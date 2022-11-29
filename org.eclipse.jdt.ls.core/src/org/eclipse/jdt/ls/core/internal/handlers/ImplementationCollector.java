/*******************************************************************************
 * Copyright (c) 2009, 2018 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *     Red Hat, Inc. - decouple implementation search from jdt.ui
 *******************************************************************************/
package org.eclipse.jdt.ls.core.internal.handlers;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import org.eclipse.core.runtime.Assert;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.core.runtime.OperationCanceledException;
import org.eclipse.core.runtime.SubProgressMonitor;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IMember;
import org.eclipse.jdt.core.IMethod;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.ITypeHierarchy;
import org.eclipse.jdt.core.ITypeRoot;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.NodeFinder;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.core.dom.SuperMethodInvocation;
import org.eclipse.jdt.core.manipulation.CoreASTProvider;
import org.eclipse.jdt.core.search.IJavaSearchConstants;
import org.eclipse.jdt.core.search.IJavaSearchScope;
import org.eclipse.jdt.core.search.SearchEngine;
import org.eclipse.jdt.core.search.SearchMatch;
import org.eclipse.jdt.core.search.SearchParticipant;
import org.eclipse.jdt.core.search.SearchPattern;
import org.eclipse.jdt.core.search.SearchRequestor;
import org.eclipse.jdt.internal.core.manipulation.JavaElementLabelsCore;
import org.eclipse.jdt.internal.corext.dom.Bindings;
import org.eclipse.jdt.internal.corext.util.JdtFlags;
import org.eclipse.jdt.internal.corext.util.MethodOverrideTester;
import org.eclipse.jdt.ls.core.internal.JavaLanguageServerPlugin;
import org.eclipse.jdt.ls.core.internal.Messages;
import org.eclipse.jface.text.IRegion;


/**
 * Searches for {@link IJavaElement} implementations.
 *
 * Parts of the search logic was borrowed from
 * org.eclipse.jdt.internal.ui.javaeditor.JavaElementImplementationHyperlink.java
 *
 */
public class ImplementationCollector<T> {


	/**
	 * Maps {@link IJavaElement} and position coordinates results to a type
	 *
	 * @param <T>
	 */
	public static interface ResultMapper<T> {

		T convert(IJavaElement element, int offset, int position);
	}

	private static final String JavaElementImplementationHyperlink_search_implementors = "Searching for implementors...";
	private static final String JavaElementImplementationHyperlink_search_method_implementors = "Searching for implementors of ''{0}''...";
	private final IRegion region;
	private final IJavaElement javaElement;
	private ResultMapper<T> mapper;
	private ITypeRoot typeRoot;

	/**
	 * @param typeRoot
	 *            the root where the find implementation is triggered
	 * @param region
	 *            the region of the selection
	 * @param javaElement
	 *            the element (type or method) to open
	 */
	public ImplementationCollector(ITypeRoot typeRoot, IRegion region, IJavaElement javaElement, ResultMapper<T> mapper) {
		Assert.isNotNull(typeRoot);
		Assert.isNotNull(region);
		Assert.isNotNull(javaElement);
		Assert.isNotNull(mapper);
		Assert.isTrue(javaElement instanceof IType || javaElement instanceof IMethod);
		this.typeRoot = typeRoot;
		this.region = region;
		this.javaElement = javaElement;
		this.mapper = mapper;
	}

	/**
	 * Finds the implementations for the method or type.
	 *
	 * @return an unmodifiable {@link List} of T, never <code>null</code>.
	 */
	public List<T> findImplementations(IProgressMonitor monitor) throws CoreException {
		if (monitor == null) {
			monitor = new NullProgressMonitor();
		}
		monitor.setTaskName(JavaElementImplementationHyperlink_search_implementors);
		List<T> implementations = null;
		if (javaElement instanceof IMethod) {
			implementations = findMethodImplementations(monitor);
		} else if (javaElement instanceof IType) {
			implementations = findTypeImplementations(monitor);
		}
		return implementations == null ? Collections.emptyList() : Collections.unmodifiableList(implementations);
	}

	private List<T> findTypeImplementations(IProgressMonitor monitor) throws JavaModelException {
		IType type = (IType) javaElement;
		List<T> results = null;
		try {
			String typeLabel = JavaElementLabelsCore.getElementLabel(type, JavaElementLabelsCore.DEFAULT_QUALIFIED);
			monitor.beginTask(Messages.format(JavaElementImplementationHyperlink_search_method_implementors, typeLabel), 10);
			IType[] allTypes = type.newTypeHierarchy(monitor).getAllSubtypes(type);
			results = Arrays.stream(allTypes).map(el -> mapper.convert(el, 0, 0)).filter(Objects::nonNull).collect(Collectors.toList());
			if (monitor.isCanceled()) {
				throw new OperationCanceledException();
			}
		} finally {
			monitor.done();
		}
		return results;
	}

	private List<T> findMethodImplementations(IProgressMonitor monitor) throws CoreException {
		IMethod method = (IMethod) javaElement;
		try {
			if (cannotBeOverriddenMethod(method)) {
				return null;
			}
		} catch (JavaModelException e) {
			JavaLanguageServerPlugin.logException("Find method implementations failure ", e);
			return null;
		}

		CompilationUnit ast = CoreASTProvider.getInstance().getAST(typeRoot, CoreASTProvider.WAIT_YES, monitor);
		if (ast == null) {
			return null;
		}

		ASTNode node = NodeFinder.perform(ast, region.getOffset(), region.getLength());
		ITypeBinding parentTypeBinding = null;
		if (node instanceof SimpleName) {
			ASTNode parent = node.getParent();
			if (parent instanceof MethodInvocation methodInvocation) {
				Expression expression = methodInvocation.getExpression();
				if (expression == null) {
					parentTypeBinding= Bindings.getBindingOfParentType(node);
				} else {
					parentTypeBinding = expression.resolveTypeBinding();
				}
			} else if (parent instanceof SuperMethodInvocation) {
				// Directly go to the super method definition
				return Collections.singletonList(mapper.convert(method, 0, 0));
			} else if (parent instanceof MethodDeclaration) {
				parentTypeBinding = Bindings.getBindingOfParentType(node);
			}
		}
		final IType receiverType = getType(parentTypeBinding);
		if (receiverType == null) {
			return null;
		}

		final List<T> results = new ArrayList<>();
		try {
			String methodLabel = JavaElementLabelsCore.getElementLabel(method, JavaElementLabelsCore.DEFAULT_QUALIFIED);
			monitor.beginTask(Messages.format(JavaElementImplementationHyperlink_search_method_implementors, methodLabel), 10);
			SearchRequestor requestor = new SearchRequestor() {
				@Override
				public void acceptSearchMatch(SearchMatch match) throws CoreException {
					if (match.getAccuracy() == SearchMatch.A_ACCURATE) {
						Object element = match.getElement();
						if (element instanceof IMethod methodFound) {
							if (!JdtFlags.isAbstract(methodFound)) {
								T result = mapper.convert(methodFound, match.getOffset(), match.getLength());
								if (result != null) {
									results.add(result);
								}
							}
						}
					}
				}
			};

			IJavaSearchScope hierarchyScope;
			if (receiverType.isInterface()) {
				hierarchyScope = SearchEngine.createHierarchyScope(method.getDeclaringType());
			} else {
				if (isFullHierarchyNeeded(new SubProgressMonitor(monitor, 3), method, receiverType)) {
					hierarchyScope = SearchEngine.createHierarchyScope(receiverType);
				} else {
					boolean isMethodAbstract = JdtFlags.isAbstract(method);
					hierarchyScope = SearchEngine.createStrictHierarchyScope(null, receiverType, true, isMethodAbstract, null);
				}
			}

			int limitTo = IJavaSearchConstants.DECLARATIONS | IJavaSearchConstants.IGNORE_DECLARING_TYPE | IJavaSearchConstants.IGNORE_RETURN_TYPE;
			SearchPattern pattern = SearchPattern.createPattern(method, limitTo);
			Assert.isNotNull(pattern);
			SearchParticipant[] participants = new SearchParticipant[] { SearchEngine.getDefaultSearchParticipant() };
			SearchEngine engine = new SearchEngine();
			engine.search(pattern, participants, hierarchyScope, requestor, new SubProgressMonitor(monitor, 7));
			if (monitor.isCanceled()) {
				throw new OperationCanceledException();
			}
		} finally {
			monitor.done();
		}
		return results;
	}


	private static IType getType(ITypeBinding typeBinding) {
		if (typeBinding == null) {
			return null;
		}
		if (typeBinding.isTypeVariable()) {
			ITypeBinding[] typeBounds= typeBinding.getTypeBounds();
			if (typeBounds.length > 0) {
				typeBinding= typeBounds[0].getTypeDeclaration();
			} else {
				return null;
			}
		}
		return (IType) typeBinding.getJavaElement();
	}

	/**
	 * Checks whether or not a method can be overridden.
	 *
	 * @param method the method
	 * @return <code>true</code> if the method cannot be overridden, <code>false</code> otherwise
	 * @throws JavaModelException if this element does not exist or if an exception occurs while
	 *             accessing its corresponding resource
	 * @since 3.7
	 */
	private static boolean cannotBeOverriddenMethod(IMethod method) throws JavaModelException {
		return JdtFlags.isPrivate(method) || JdtFlags.isFinal(method) || JdtFlags.isStatic(method) || method.isConstructor()
				|| JdtFlags.isFinal((IMember)method.getParent());
	}

	/**
	 * Checks whether a full type hierarchy is needed to search for implementors.
	 *
	 * @param monitor the progress monitor
	 * @param method the method
	 * @param receiverType the receiver type
	 * @return <code>true</code> if a full type hierarchy is needed, <code>false</code> otherwise
	 * @throws JavaModelException if the java element does not exist or if an exception occurs while
	 *             accessing its corresponding resource
	 * @since 3.6
	 */
	private static boolean isFullHierarchyNeeded(IProgressMonitor monitor, IMethod method, IType receiverType) throws JavaModelException {
		ITypeHierarchy superTypeHierarchy= receiverType.newSupertypeHierarchy(monitor);
		MethodOverrideTester methodOverrideTester= new MethodOverrideTester(receiverType, superTypeHierarchy);
		return methodOverrideTester.findOverriddenMethodInType(receiverType, method) == null;
	}
}
