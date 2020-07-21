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

package org.eclipse.jdt.ls.core.internal.commands;

import java.util.Collections;

import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.jdt.core.ITypeRoot;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.manipulation.CoreASTProvider;
import org.eclipse.jdt.ls.core.internal.JDTUtils;
import org.eclipse.jdt.ls.core.internal.JavaLanguageServerPlugin;
import org.eclipse.jdt.ls.core.internal.JobHelpers;
import org.eclipse.jdt.ls.core.internal.handlers.DocumentLifeCycleHandler;
import org.eclipse.jdt.ls.core.internal.handlers.JsonRpcHelpers;
import org.eclipse.jdt.ls.core.internal.semantictokens.SemanticTokenManager;
import org.eclipse.jdt.ls.core.internal.semantictokens.SemanticTokens;
import org.eclipse.jdt.ls.core.internal.semantictokens.SemanticTokensLegend;
import org.eclipse.jdt.ls.core.internal.semantictokens.SemanticTokensVisitor;
import org.eclipse.jface.text.IDocument;

public class SemanticTokensCommand {
	public static SemanticTokens provide(String uri) {
		JobHelpers.waitForJobs(DocumentLifeCycleHandler.DOCUMENT_LIFE_CYCLE_JOBS, null);
		return doProvide(uri);
	}

	private static SemanticTokens doProvide(String uri) {
		IDocument document = null;

		ITypeRoot typeRoot = JDTUtils.resolveTypeRoot(uri);
		if (typeRoot != null) {
			try {
				document = JsonRpcHelpers.toDocument(typeRoot.getBuffer());
			} catch (JavaModelException e) {
				JavaLanguageServerPlugin.logException("Failed to provide semantic tokens for " + uri, e);
			}
		}
		if (document == null) {
			return new SemanticTokens(Collections.emptyList());
		}

		SemanticTokensVisitor collector = new SemanticTokensVisitor(document, SemanticTokenManager.getInstance());
		CompilationUnit root = CoreASTProvider.getInstance().getAST(typeRoot, CoreASTProvider.WAIT_YES, new NullProgressMonitor());
		root.accept(collector);
		return collector.getSemanticTokens();
	}

	public static SemanticTokensLegend getLegend() {
		return SemanticTokenManager.getInstance().getLegend();
	}
}
