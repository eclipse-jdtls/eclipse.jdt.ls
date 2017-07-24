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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.ISafeRunnable;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.OperationCanceledException;
import org.eclipse.core.runtime.SafeRunner;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.SubMonitor;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.ITypeRoot;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.internal.corext.dom.ASTNodes;
import org.eclipse.jdt.internal.corext.dom.IASTSharedValues;

/**
 * AST provider that caches the AST generated for files that are known to be
 * open.
 */
@SuppressWarnings("restriction")
public final class SharedASTProvider {

	private static SharedASTProvider instance = new SharedASTProvider();

	private ConcurrentMap<String, CompilationUnit> cache = new ConcurrentHashMap<>();
	private int astCreationCount; // to testing purposes


	private SharedASTProvider() {
		astCreationCount = 0;
	}

	public void dispose() {
		invalidateAll();
	}

	public static SharedASTProvider getInstance(){
		return instance;
	}

	public CompilationUnit getAST(final ITypeRoot input, IProgressMonitor progressMonitor) {
		if (progressMonitor != null && progressMonitor.isCanceled()) {
			return null;
		}
		if (!shouldCache(input)) {
			JavaLanguageServerPlugin.logInfo("Creating uncached AST for " + input.getPath().toString());
			return createAST(input, progressMonitor);
		}

		final String identifier = input.getHandleIdentifier();
		return cache.computeIfAbsent(identifier, k -> {
			JavaLanguageServerPlugin.logInfo("Caching AST for " + input.getPath().toString());
			CompilationUnit astRoot = createAST(input, progressMonitor);
			astCreationCount++;
			return astRoot;
		});
	}

	public List<CompilationUnit> getASTs(List<ICompilationUnit> inputs, IProgressMonitor progressMonitor) {
		if (progressMonitor != null && progressMonitor.isCanceled() || inputs.isEmpty()) {
			return Collections.emptyList();
		}
		List<CompilationUnit> result = new ArrayList<>();
		SubMonitor subMonitor = SubMonitor.convert(progressMonitor, inputs.size());

		for (ICompilationUnit input : inputs) {
			result.add(getAST(input, subMonitor.split(1)));
		}
		return result;
	}

	public void setAST(CompilationUnit astRoot) {
		ITypeRoot typeRoot = astRoot.getTypeRoot();
		if (shouldCache(typeRoot)) {
			cache.put(typeRoot.getHandleIdentifier(), astRoot);
		}
	}

	/**
	 * Only cache ASTs for compilation units in working copy mode (open in a
	 * buffer)
	 */
	private boolean shouldCache(ITypeRoot input) {
		if (input.getElementType() != IJavaElement.COMPILATION_UNIT) {
			return false;
		}
		ICompilationUnit cu = (ICompilationUnit) input;
		return cu.getOwner() == null && cu.isWorkingCopy();
	}

	public void invalidate(ITypeRoot root){
		if(root != null){
			CompilationUnit removed = cache.remove(root.getHandleIdentifier());
			if (removed != null) {
				JavaLanguageServerPlugin.logInfo("Releasing AST for " + root.getPath().toString());
			}
		}
	}

	public void invalidateAll() {
		cache.clear();
		JavaLanguageServerPlugin.logInfo("Releasing all ASTs");
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

		final ASTParser parser = newASTParser();
		parser.setSource(input);

		final CompilationUnit root[]= new CompilationUnit[1];

		SafeRunner.run(new ISafeRunnable() {
			@Override
			public void run() {
				try {
					if (progressMonitor != null && progressMonitor.isCanceled()) {
						return;
					}
					root[0] = (CompilationUnit) parser.createAST(progressMonitor);

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

	public static ASTParser newASTParser() {
		final ASTParser parser = ASTParser.newParser(IASTSharedValues.SHARED_AST_LEVEL);
		parser.setResolveBindings(true);
		parser.setStatementsRecovery(IASTSharedValues.SHARED_AST_STATEMENT_RECOVERY);
		parser.setBindingsRecovery(IASTSharedValues.SHARED_BINDING_RECOVERY);
		return parser;
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

	/**
	 * For testing purposes
	 *
	 * @return the number of elements currently in the cache
	 */
	public int getCacheSize() {
		return cache.size();
	}

	/**
	 * For testing purposes
	 *
	 * @return the number of ASTs created
	 */
	public int getASTCreationCount() {
		return astCreationCount;
	}

	/**
	 * For testing purposes
	 *
	 * Sets the counter for ASTs created to 0
	 */
	public void clearASTCreationCount() {
		astCreationCount = 0;
	}

}
