/*******************************************************************************
 * Copyright (c) 2026 Red Hat Inc. and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *     Red Hat Inc. - initial API and implementation
 *******************************************************************************/
package org.eclipse.jdt.ls.core.internal.corrections;

import java.util.Collection;

import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.compiler.IProblem;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.ls.core.internal.corrections.proposals.ConfigureProblemSeverityProposal;
import org.eclipse.jdt.ls.core.internal.handlers.CodeActionHandler;
import org.eclipse.jdt.ui.text.java.IInvocationContext;
import org.eclipse.jdt.ui.text.java.IProblemLocation;
import org.eclipse.lsp4j.CodeActionKind;

/**
 * jdt.ls variant of org.eclipse.jdt.internal.ui.text.correction.ConfigureProblemSeveritySubProcessor.
 * Unlike the desktop implementation, which opens a preferences dialog and relies on the user to
 * manually change the severity, this directly offers to set the problem's severity to "ignore"
 * for the current project - the same .settings/org.eclipse.jdt.core.prefs change a user could make
 * by hand, applied immediately as a single quick fix.
 */
public class ConfigureProblemSeveritySubProcessor {

	private static final int CONFIGURE_PROBLEM_SEVERITY_RELEVANCE = -11;

	public static boolean hasConfigureProblemSeverityProposal(int problemId) {
		return JavaCore.getOptionForConfigurableSeverity(problemId) != null;
	}

	public static void addConfigureProblemSeverityProposal(IInvocationContext context, IProblemLocation problem, Collection<ProposalKindWrapper> proposals) {
		int problemId = problem.getProblemId();
		String optionId = JavaCore.getOptionForConfigurableSeverity(problemId);
		if (optionId == null) {
			return;
		}

		String problemMessage = getProblemMessage(context, problem);
		ConfigureProblemSeverityProposal proposal = new ConfigureProblemSeverityProposal(
				CorrectionMessages.ConfigureProblemSeveritySubProcessor_ignore_project_name,
				context.getCompilationUnit().getJavaProject(),
				optionId,
				JavaCore.IGNORE,
				problemMessage,
				CONFIGURE_PROBLEM_SEVERITY_RELEVANCE);
		proposals.add(CodeActionHandler.wrap(proposal, CodeActionKind.QuickFix));
	}

	private static String getProblemMessage(IInvocationContext context, IProblemLocation problem) {
		CompilationUnit astRoot = context.getASTRoot();
		if (astRoot != null) {
			for (IProblem iProblem : astRoot.getProblems()) {
				if (problem.getProblemId() == iProblem.getID()
						&& problem.getOffset() == iProblem.getSourceStart()
						&& problem.getLength() == iProblem.getSourceEnd() - iProblem.getSourceStart() + 1) {
					return iProblem.getMessage();
				}
			}
		}
		return ""; //$NON-NLS-1$
	}

	private ConfigureProblemSeveritySubProcessor() {
	}

}
