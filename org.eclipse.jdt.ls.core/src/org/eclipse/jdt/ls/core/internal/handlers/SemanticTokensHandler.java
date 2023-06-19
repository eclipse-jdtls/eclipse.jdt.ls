/*******************************************************************************
 * Copyright (c) 2020 Microsoft Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *     Microsoft Corporation - initial API and implementation
 *******************************************************************************/

package org.eclipse.jdt.ls.core.internal.handlers;

import java.util.Arrays;
import java.util.Collections;
import java.util.stream.Collectors;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.ITypeRoot;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.manipulation.CoreASTProvider;
import org.eclipse.jdt.ls.core.internal.JDTUtils;
import org.eclipse.jdt.ls.core.internal.JavaLanguageServerPlugin;
import org.eclipse.jdt.ls.core.internal.JobHelpers;
import org.eclipse.jdt.ls.core.internal.handlers.BaseDocumentLifeCycleHandler.DocumentMonitor;
import org.eclipse.jdt.ls.core.internal.semantictokens.SemanticTokensVisitor;
import org.eclipse.jdt.ls.core.internal.semantictokens.TokenModifier;
import org.eclipse.jdt.ls.core.internal.semantictokens.TokenType;
import org.eclipse.lsp4j.SemanticTokens;
import org.eclipse.lsp4j.SemanticTokensLegend;
import org.eclipse.lsp4j.SemanticTokensParams;

public class SemanticTokensHandler {

	public static SemanticTokens full(IProgressMonitor monitor, SemanticTokensParams params, DocumentMonitor documentMonitor) {
		ITypeRoot typeRoot = JDTUtils.resolveTypeRoot(params.getTextDocument().getUri());
		documentMonitor.checkChanged();
		if (typeRoot == null || monitor.isCanceled()) {
			return new SemanticTokens(Collections.emptyList());
		}

		JobHelpers.waitForJobs(DocumentLifeCycleHandler.DOCUMENT_LIFE_CYCLE_JOBS, monitor);
		documentMonitor.checkChanged();

		CompilationUnit root = getAst(typeRoot, monitor);
		documentMonitor.checkChanged();
		if (root == null || monitor.isCanceled()) {
			return new SemanticTokens(Collections.emptyList());
		}

		SemanticTokensVisitor collector = new SemanticTokensVisitor(root);
		root.accept(collector);
		return collector.getSemanticTokens();
	}

	public static SemanticTokensLegend legend() {
		return new SemanticTokensLegend(
			Arrays.stream(TokenType.values()).map(TokenType::toString).collect(Collectors.toList()),
			Arrays.stream(TokenModifier.values()).map(TokenModifier::toString).collect(Collectors.toList())
		);
	}

	/**
	 * Get the AST from CoreASTProvider. After getting the AST, it will check if the buffer size is equal to
	 * the AST's length. If it's not - indicating that the AST is out-of-date. The AST will be disposed and
	 * request CoreASTProvider to get a new one.
	 *
	 * <p>
	 * Such inconsistency will happen when a thread is calling getAST(), at the meantime, the
	 * document has been changed. Though the disposeAST() will be called when document change event
	 * comes, there is a chance when disposeAST() finishes before getAST(). In that case, an out-of-date
	 * AST will be cached and be used by other threads.
	 * </p>
	 *
	 * TODO: Consider to extract it to a utility and used by other handlers that need AST.
	 */
	private static CompilationUnit getAst(ITypeRoot typeRoot, IProgressMonitor monitor) {
		CompilationUnit root = CoreASTProvider.getInstance().getAST(typeRoot, CoreASTProvider.WAIT_YES, monitor);
		IJavaElement element = root.getJavaElement();
		if (element instanceof ICompilationUnit cu) {
			try {
				if (cu.getBuffer().getLength() != root.getLength()) {
					CoreASTProvider.getInstance().disposeAST();
					root = CoreASTProvider.getInstance().getAST(typeRoot, CoreASTProvider.WAIT_YES, monitor);
				}
			} catch (JavaModelException e) {
				JavaLanguageServerPlugin.log(e);
			}
		}
		return root;
	}
}
