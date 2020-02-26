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

package org.eclipse.jdt.ls.core.internal.text.correction;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.eclipse.jdt.internal.ui.text.correction.IProblemLocationCore;
import org.eclipse.jdt.ls.core.internal.DiagnosticsState;
import org.eclipse.jdt.ls.core.internal.JDTUtils;
import org.eclipse.jdt.ls.core.internal.JavaLanguageServerPlugin;
import org.eclipse.jdt.ls.core.internal.corrections.IInvocationContext;
import org.eclipse.jdt.ls.core.internal.handlers.DiagnosticsHandler;
import org.eclipse.jdt.ls.core.internal.preferences.PreferenceManager;
import org.eclipse.lsp4j.CodeAction;
import org.eclipse.lsp4j.CodeActionKind;
import org.eclipse.lsp4j.CodeActionParams;
import org.eclipse.lsp4j.Command;
import org.eclipse.lsp4j.jsonrpc.messages.Either;

public class NonProjectFixProcessor {
	public static final String REFRESH_DIAGNOSTICS_COMMAND = "java.project.refreshDiagnostics";

	private PreferenceManager preferenceManager;
	private DiagnosticsState nonProjectDiagnosticsState;

	public NonProjectFixProcessor(PreferenceManager preferenceManager) {
		this.preferenceManager = preferenceManager;
		this.nonProjectDiagnosticsState = JavaLanguageServerPlugin.getNonProjectDiagnosticsState();
	}

	public List<Either<Command, CodeAction>> getCorrections(CodeActionParams params, IInvocationContext context, IProblemLocationCore[] locations) {
		if (locations == null || locations.length == 0) {
			return Collections.emptyList();
		}

		List<Either<Command, CodeAction>> $ = new ArrayList<>();
		String uri = JDTUtils.toURI(context.getCompilationUnit());
		for (int i = 0; i < locations.length; i++) {
			IProblemLocationCore curr = locations[i];
			Integer id = Integer.valueOf(curr.getProblemId());
			if (id == DiagnosticsHandler.NON_PROJECT_JAVA_FILE
				|| id == DiagnosticsHandler.NOT_ON_CLASSPATH) {
				if (this.nonProjectDiagnosticsState.isOnlySyntaxReported(uri)) {
					$.add(getDiagnosticsFixes(ActionMessages.ReportAllErrorsForThisFile, uri, "thisFile", false));
					$.add(getDiagnosticsFixes(ActionMessages.ReportAllErrorsForAnyNonProjectFile, uri, "anyNonProjectFile", false));
				} else {
					$.add(getDiagnosticsFixes(ActionMessages.ReportSyntaxErrorsForThisFile, uri, "thisFile", true));
					$.add(getDiagnosticsFixes(ActionMessages.ReportSyntaxErrorsForAnyNonProjectFile, uri, "anyNonProjectFile", true));
				}
			}
		}

		return $;
	}

	private Either<Command, CodeAction> getDiagnosticsFixes(String message, String uri, String scope, boolean syntaxOnly) {
		Command command = new Command(message, REFRESH_DIAGNOSTICS_COMMAND, Arrays.asList(uri, scope, syntaxOnly));
		if (preferenceManager.getClientPreferences().isSupportedCodeActionKind(CodeActionKind.QuickFix)) {
			CodeAction codeAction = new CodeAction(message);
			codeAction.setKind(CodeActionKind.QuickFix);
			codeAction.setCommand(command);
			codeAction.setDiagnostics(Collections.EMPTY_LIST);
			return Either.forRight(codeAction);
		} else {
			return Either.forLeft(command);
		}
	}
}
