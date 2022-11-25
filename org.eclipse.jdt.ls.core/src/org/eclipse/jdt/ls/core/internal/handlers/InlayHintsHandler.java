/*******************************************************************************
 * Copyright (c) 2022 Microsoft Corporation and others.
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

import java.util.Collections;
import java.util.List;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.jdt.core.IBuffer;
import org.eclipse.jdt.core.ITypeRoot;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.manipulation.CoreASTProvider;
import org.eclipse.jdt.ls.core.internal.JDTUtils;
import org.eclipse.jdt.ls.core.internal.JavaLanguageServerPlugin;
import org.eclipse.jdt.ls.core.internal.JobHelpers;
import org.eclipse.jdt.ls.core.internal.preferences.PreferenceManager;
import org.eclipse.lsp4j.InlayHint;
import org.eclipse.lsp4j.InlayHintParams;

public class InlayHintsHandler {

	private final PreferenceManager preferenceManager;

	public InlayHintsHandler(PreferenceManager preferenceManager) {
		this.preferenceManager = preferenceManager;
	}

	/**
	 * provide inlay hints for the given range.
	 * @param params
	 * @param monitor
	 * @return inlay hints.
	 */
	public List<InlayHint> inlayHint(InlayHintParams params, IProgressMonitor monitor) {
		if (InlayHintsParameterMode.NONE.equals(preferenceManager.getPreferences().getInlayHintsParameterMode())) {
			return Collections.emptyList();
		}

		JobHelpers.waitForJobs(DocumentLifeCycleHandler.DOCUMENT_LIFE_CYCLE_JOBS, monitor);

		String uri = params.getTextDocument().getUri();

		ITypeRoot typeRoot = JDTUtils.resolveTypeRoot(uri);
		if (typeRoot == null) {
			return Collections.emptyList();
		}
		CompilationUnit root = CoreASTProvider.getInstance().getAST(typeRoot, CoreASTProvider.WAIT_YES, monitor);
		if (root == null || monitor.isCanceled()) {
			return Collections.emptyList();
		}

		IBuffer buffer;
		try {
			buffer = typeRoot.getBuffer();
		} catch (JavaModelException e) {
			JavaLanguageServerPlugin.logException(e.getMessage(), e);
			return Collections.emptyList();
		}

		int startLine = params.getRange().getStart().getLine();
		int startCharacter = params.getRange().getStart().getCharacter();
		int startOffset = JsonRpcHelpers.toOffset(buffer, startLine, startCharacter);

		int endLine = params.getRange().getEnd().getLine();
		int endCharacter = params.getRange().getEnd().getCharacter();
		int endOffset = JsonRpcHelpers.toOffset(buffer, endLine, endCharacter);

		InlayHintVisitor inlayHintVisitor = new InlayHintVisitor(startOffset, endOffset, typeRoot, preferenceManager);
		root.accept(inlayHintVisitor);
		return inlayHintVisitor.getInlayHints();
	}
}
