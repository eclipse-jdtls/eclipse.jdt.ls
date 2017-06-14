/*******************************************************************************
 * Copyright (c) 2017 Red Hat Inc. and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Red Hat Inc. - initial API and implementation
 *******************************************************************************/
package org.eclipse.jdt.ls.core.internal;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.ISafeRunnable;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.OperationCanceledException;
import org.eclipse.core.runtime.SafeRunner;
import org.eclipse.core.runtime.Status;
import org.eclipse.jdt.core.ElementChangedEvent;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IElementChangedListener;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IJavaElementDelta;
import org.eclipse.jdt.core.ITypeRoot;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.internal.corext.dom.ASTNodes;
import org.eclipse.jdt.internal.corext.dom.IASTSharedValues;

/**
 * AST provider that caches the AST generated for
 * files that are known to be open.
 *
 * @author Gorkem Ercan
 *
 */
@SuppressWarnings("restriction")
public final class SharedASTProvider {

	private Map<String, CompilationUnit> cache = Collections.synchronizedMap(new HashMap<>());
	private IElementChangedListener fJavaListener;
	private static SharedASTProvider instance = new SharedASTProvider();

	private SharedASTProvider(){
		fJavaListener= new IElementChangedListener() {

			@Override
			public void elementChanged(ElementChangedEvent event) {
				hasCUChange(event.getDelta());
			}

			private boolean hasCUChange(IJavaElementDelta delta) {
				IJavaElement elem= delta.getElement();
				boolean isAddedOrRemoved= (delta.getKind() != IJavaElementDelta.CHANGED);
				switch (elem.getElementType()) {

				case IJavaElement.JAVA_MODEL: case IJavaElement.JAVA_PROJECT:
				case IJavaElement.PACKAGE_FRAGMENT_ROOT: case IJavaElement.PACKAGE_FRAGMENT:
					if (isAddedOrRemoved) {
						return true;
					}
					return processChildrenDelta(delta.getAffectedChildren());

				case IJavaElement.COMPILATION_UNIT:
					ICompilationUnit cu= (ICompilationUnit) elem;
					if (!cu.getPrimary().equals(cu)) {
						return false;
					}
					invalidate(cu);
					return false;

				default:
					return false;
				}
			}

			private boolean processChildrenDelta(IJavaElementDelta[] children) {
				for (int i= 0; i < children.length; i++) {
					if (hasCUChange(children[i])) {
						return true;
					}
				}
				return false;
			}
		};
		JavaCore.addElementChangedListener(fJavaListener);
	}

	public void dispose() {
		if (fJavaListener != null) {
			JavaCore.removeElementChangedListener(fJavaListener);
			fJavaListener = null;
		}
	}


	public static SharedASTProvider getInstance(){
		return instance;
	}

	public CompilationUnit getAST(final ITypeRoot input,  IProgressMonitor progressMonitor) {
		if (progressMonitor != null && progressMonitor.isCanceled()) {
			return null;
		}

		final String identifier = input.getHandleIdentifier();
		CompilationUnit unit = cache.get(identifier);
		if(unit == null){
			unit = createAST(input, progressMonitor);
			if (shouldCache(input)) {
				cache.put(identifier, unit);
			}
		}
		return unit;
	}

	private boolean shouldCache(ITypeRoot input) {
		if (input.getElementType() != IJavaElement.COMPILATION_UNIT) {
			return false;
		}
		ICompilationUnit cu = (ICompilationUnit) input;
		return cu.getOwner() == null && cu.isWorkingCopy();
	}

	public void invalidate(ITypeRoot root){
		if(root != null){
			cache.remove(root.getHandleIdentifier());
		}
	}


	/**
	 * Creates a new compilation unit AST.
	 *
	 * @param input the Java element for which to create the AST
	 * @param progressMonitor the progress monitor
	 * @return AST
	 */
	private static CompilationUnit createAST(final ITypeRoot input, final IProgressMonitor progressMonitor) {
		if (!hasSource(input)) {
			return null;
		}

		if (progressMonitor != null && progressMonitor.isCanceled()) {
			return null;
		}

		final ASTParser parser = ASTParser.newParser(IASTSharedValues.SHARED_AST_LEVEL);
		parser.setResolveBindings(true);
		parser.setStatementsRecovery(IASTSharedValues.SHARED_AST_STATEMENT_RECOVERY);
		parser.setBindingsRecovery(IASTSharedValues.SHARED_BINDING_RECOVERY);
		parser.setSource(input);

		if (progressMonitor != null && progressMonitor.isCanceled()) {
			return null;
		}

		final CompilationUnit root[]= new CompilationUnit[1];

		SafeRunner.run(new ISafeRunnable() {
			@Override
			public void run() {
				try {
					if (progressMonitor != null && progressMonitor.isCanceled()) {
						return;
					}
					root[0]= (CompilationUnit)parser.createAST(progressMonitor);

					//mark as unmodifiable
					ASTNodes.setFlagsToAST(root[0], ASTNode.PROTECT);
				} catch (OperationCanceledException ex) {
					return;
				}
			}
			@Override
			public void handleException(Throwable ex) {
				IStatus status= new Status(IStatus.ERROR, JavaLanguageServerPlugin.PLUGIN_ID, IStatus.OK, "Error in JDT Core during AST creation", ex);  //$NON-NLS-1$
				JavaLanguageServerPlugin.log(status);
			}
		});
		return root[0];
	}

	/**
	 * Checks whether the given Java element has accessible source.
	 *
	 * @param je the Java element to test
	 * @return <code>true</code> if the element has source
	 */
	private static boolean hasSource(ITypeRoot je) {
		if (je == null || !je.exists()) {
			return false;
		}

		try {
			return je.getBuffer() != null;
		} catch (JavaModelException ex) {
			IStatus status= new Status(IStatus.ERROR, JavaLanguageServerPlugin.PLUGIN_ID, IStatus.OK, "Error in JDT Core during AST creation", ex);  //$NON-NLS-1$
			JavaLanguageServerPlugin.log(status);
		}
		return false;
	}


}
