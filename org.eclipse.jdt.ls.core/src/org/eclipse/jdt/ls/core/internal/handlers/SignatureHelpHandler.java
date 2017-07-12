/*******************************************************************************
 * Copyright (c) 2017 Microsoft Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Microsoft Corporation - initial API and implementation
 *******************************************************************************/

package org.eclipse.jdt.ls.core.internal.handlers;

import java.util.Arrays;
import java.util.List;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.jdt.core.IBuffer;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.ls.core.internal.JDTUtils;
import org.eclipse.jdt.ls.core.internal.JavaLanguageServerPlugin;
import org.eclipse.jdt.ls.core.internal.contentassist.SignatureHelpRequestor;
import org.eclipse.jdt.ls.core.internal.preferences.PreferenceManager;
import org.eclipse.lsp4j.SignatureHelp;
import org.eclipse.lsp4j.SignatureHelpOptions;
import org.eclipse.lsp4j.SignatureInformation;
import org.eclipse.lsp4j.TextDocumentPositionParams;

public class SignatureHelpHandler {

	public static SignatureHelpOptions createOptions() {
		return new SignatureHelpOptions(Arrays.asList("("));
	}

	private static final int SEARCH_BOUND = 2000;

	private PreferenceManager preferenceManager;

	public SignatureHelpHandler(PreferenceManager preferenceManager) {
		this.preferenceManager = preferenceManager;
	}

	public SignatureHelp signatureHelp(TextDocumentPositionParams position, IProgressMonitor monitor) {

		SignatureHelp help = new SignatureHelp();

		if (!preferenceManager.getPreferences(null).isSignatureHelpEnabled()) {
			return help;
		}

		try {
			ICompilationUnit unit = JDTUtils.resolveCompilationUnit(position.getTextDocument().getUri());
			final int offset = JsonRpcHelpers.toOffset(unit.getBuffer(), position.getPosition().getLine(), position.getPosition().getCharacter());
			int[] contextInfomation = getContextInfomation(unit.getBuffer(), offset);
			if (contextInfomation[0] == -1) {
				return help;
			}
			SignatureHelpRequestor collector = new SignatureHelpRequestor(unit, contextInfomation[0] + 1);

			if (offset > -1 && !monitor.isCanceled()) {
				unit.codeComplete(contextInfomation[0] + 1, collector, monitor);
				help = collector.getSignatureHelp(monitor);

				if (help != null) {
					int currentParameter = contextInfomation[1];
					List<SignatureInformation> infos = help.getSignatures();
					for (int i = 0; i < infos.size(); i++) {
						if (infos.get(i).getParameters().size() >= currentParameter + 1) {
							help.setActiveSignature(i);
							help.setActiveParameter(currentParameter);
							break;
						}
					}
				}
			}
		} catch (CoreException ex) {
			JavaLanguageServerPlugin.logException("Find signatureHelp failure ", ex);
		}
		return help;
	}

	/*
	 * Calculate the heuristic information about the start offset of method and current parameter position. The parameter position is 0-based.
	 * If cannot find the methods start offset after max search bound, -1 will be return.
	 *
	 * @return array of 2 integer2: the first one is starting offset of method and the second one is the current parameter position.
	 */
	private int[] getContextInfomation(IBuffer buffer, int offset) {
		int[] result = new int[2];
		result[0] = result[1] = -1;
		int depth = 1;

		for (int i = offset - 1; i >= 0 && ((offset - i) < SEARCH_BOUND); i--) {
			char c = buffer.getChar(i);
			if (c == ')') {
				depth++;
			}
			if (c == '(') {
				depth--;
			}
			if (c == ',' && depth == 1) {
				result[1]++;
			}
			if (depth == 0) {
				result[0] = i;
				break;
			}
		}
		// Assuming user are typing current parameter:
		if (result[0] + 1 != offset) {
			result[1]++;
		}
		return result;
	}
}
