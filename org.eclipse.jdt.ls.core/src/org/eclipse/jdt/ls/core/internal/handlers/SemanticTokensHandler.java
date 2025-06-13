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
import org.eclipse.jdt.core.ITypeRoot;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.ls.core.internal.JDTUtils;
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

		CompilationUnit root = JDTUtils.getAst(typeRoot, monitor);
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
}
