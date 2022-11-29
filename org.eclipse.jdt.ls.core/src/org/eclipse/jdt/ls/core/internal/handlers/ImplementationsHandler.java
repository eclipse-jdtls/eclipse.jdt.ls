/*******************************************************************************
 * Copyright (c) 2018 Red Hat Inc. and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *     Red Hat Inc. - initial API and implementation
 *******************************************************************************/
package org.eclipse.jdt.ls.core.internal.handlers;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IMethod;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.ITypeRoot;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.AbstractTypeDeclaration;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.NodeFinder;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.core.dom.SuperMethodInvocation;
import org.eclipse.jdt.core.manipulation.CoreASTProvider;
import org.eclipse.jdt.internal.corext.util.JdtFlags;
import org.eclipse.jdt.ls.core.internal.JDTUtils;
import org.eclipse.jdt.ls.core.internal.JavaLanguageServerPlugin;
import org.eclipse.jdt.ls.core.internal.preferences.PreferenceManager;
import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.IRegion;
import org.eclipse.jface.text.Region;
import org.eclipse.lsp4j.Location;
import org.eclipse.lsp4j.TextDocumentPositionParams;

/**
 * Implementations handler
 *
 * @author Fred Bricon
 */
public class ImplementationsHandler {

	private PreferenceManager preferenceManager;

	public ImplementationsHandler(PreferenceManager preferenceManager) {
		this.preferenceManager = preferenceManager;
	}

	public List<? extends Location> findImplementations(TextDocumentPositionParams param, IProgressMonitor monitor) {
		List<Location> locations = null;
		IJavaElement elementToSearch = null;
		ITypeRoot typeRoot = null;
		IRegion region = null;
		try {
			boolean returnCompilationUnit = preferenceManager == null ? false : preferenceManager.isClientSupportsClassFileContent() && (preferenceManager.getPreferences().isIncludeDecompiledSources());
			typeRoot = JDTUtils.resolveTypeRoot(param.getTextDocument().getUri(), returnCompilationUnit, monitor);
			if (typeRoot != null) {
				elementToSearch = JDTUtils.findElementAtSelection(typeRoot, param.getPosition().getLine(), param.getPosition().getCharacter(), this.preferenceManager, monitor);
			}
			if (!(elementToSearch instanceof IType || elementToSearch instanceof IMethod)) {
				return Collections.emptyList();
			}
			int offset = getOffset(param, typeRoot);
			region = new Region(offset, 0);
			IType primaryType = typeRoot.findPrimaryType();
			//java.lang.Object is a special case. We need to minimize heavy cost of I/O,
			// by avoiding opening all files from the Object hierarchy
			boolean useDefaultLocation = primaryType == null ? false : "java.lang.Object".equals(primaryType.getFullyQualifiedName());
			ImplementationToLocationMapper mapper = new ImplementationToLocationMapper(preferenceManager.isClientSupportsClassFileContent(), useDefaultLocation);
			ImplementationCollector<Location> collector = new ImplementationCollector<>(typeRoot, region, elementToSearch, mapper);
			locations = collector.findImplementations(monitor);
			if (shouldIncludeDefinition(typeRoot, region, elementToSearch, locations)) {
				Location definition = NavigateToDefinitionHandler.computeDefinitionNavigation(elementToSearch, typeRoot.getJavaProject());
				if (definition != null) {
					locations = locations == null ? new ArrayList<>() : new ArrayList<>(locations);
					locations.add(0, definition);
				}
			}
		} catch (CoreException e) {
			JavaLanguageServerPlugin.logException("Problem computing definition for" + typeRoot.getElementName(), e);
			return Collections.emptyList();
		} finally {
			JDTUtils.discardClassFileWorkingCopy(typeRoot);
		}
		return locations;
	}

	private int getOffset(TextDocumentPositionParams param, ITypeRoot typeRoot) {
		int offset = 0;
		try {
			IDocument document = JsonRpcHelpers.toDocument(typeRoot.getBuffer());
			offset = document.getLineOffset(param.getPosition().getLine()) + param.getPosition().getCharacter();
		} catch (JavaModelException | BadLocationException e) {
			JavaLanguageServerPlugin.logException(e.getMessage(), e);
		}
		return offset;
	}

	private boolean shouldIncludeDefinition(ITypeRoot typeRoot, IRegion region, IJavaElement elementToSearch, List<Location> implementations) {
		boolean isUnimplemented = false;
		try {
			isUnimplemented = isUnimplementedMember(elementToSearch);
		} catch (JavaModelException e) {
			// do nothing.
		}

		if (isUnimplemented && implementations != null && !implementations.isEmpty()) {
			return false;
		}

		CompilationUnit ast = CoreASTProvider.getInstance().getAST(typeRoot, CoreASTProvider.WAIT_YES, new NullProgressMonitor());
		if (ast == null) {
			return false;
		}

		ASTNode node = NodeFinder.perform(ast, region.getOffset(), region.getLength());
		if (node instanceof SimpleName && !(node.getParent() instanceof MethodDeclaration || node.getParent() instanceof SuperMethodInvocation || node.getParent() instanceof AbstractTypeDeclaration)) {
			return true;
		}

		return false;
	}

	private boolean isUnimplementedMember(IJavaElement element) throws JavaModelException {
		if (element instanceof IMethod method) {
			return isUnimplementedMethod(method);
		} else if (element instanceof IType type) {
			return isUnimplementedType(type);
		}

		return false;
	}

	private boolean isUnimplementedMethod(IMethod method) throws JavaModelException {
		if (JdtFlags.isAbstract(method) || (method.getDeclaringType() != null && method.getDeclaringType().isInterface())) {
			return true;
		}

		return false;
	}

	private boolean isUnimplementedType(IType type) throws JavaModelException {
		if (JdtFlags.isAbstract(type) || type.isInterface()) {
			return true;
		}

		return false;
	}
}
