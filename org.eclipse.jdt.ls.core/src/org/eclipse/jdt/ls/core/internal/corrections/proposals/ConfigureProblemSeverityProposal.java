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
package org.eclipse.jdt.ls.core.internal.corrections.proposals;

import java.util.List;
import java.util.Map;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.internal.corext.util.Messages;
import org.eclipse.jdt.ls.core.internal.corrections.CorrectionMessages;
import org.eclipse.jdt.ls.core.internal.text.correction.CUCorrectionCommandProposal;

/**
 * Offers to set a single compiler problem's severity option (e.g. "ignore")
 * for the enclosing project. Implemented as a command proposal - rather than
 * a Change/text-edit proposal - so that the underlying
 * "java.project.configureProblemSeverity" command (which writes to the
 * project's .settings/org.eclipse.jdt.core.prefs, the same file a user could
 * edit by hand, and then revalidates the current file so a stale diagnostic
 * doesn't linger when autobuild is disabled) only runs when the user
 * actually selects this quick fix, not merely when code actions are listed
 * or resolved for display.
 */
public final class ConfigureProblemSeverityProposal extends CUCorrectionCommandProposal {

	private final String fProblemMessage;

	public ConfigureProblemSeverityProposal(String name, ICompilationUnit cu, String projectUri, String optionId, String severity, String fileUri, String problemMessage, int relevance) {
		super(name, cu, relevance, "java.project.configureProblemSeverity", List.of(projectUri, Map.of(optionId, severity), fileUri));
		fProblemMessage = problemMessage;
	}

	@Override
	public Object getAdditionalProposalInfo(IProgressMonitor monitor) {
		return Messages.format(CorrectionMessages.ConfigureProblemSeveritySubProcessor_ignore_project_info, fProblemMessage);
	}

}
