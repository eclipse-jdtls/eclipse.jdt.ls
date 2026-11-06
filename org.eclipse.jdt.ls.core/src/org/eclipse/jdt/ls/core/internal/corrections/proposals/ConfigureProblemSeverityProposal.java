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

import java.net.URI;
import java.util.Collections;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.manipulation.ChangeCorrectionProposalCore;
import org.eclipse.jdt.internal.corext.util.Messages;
import org.eclipse.jdt.ls.core.internal.commands.ProjectCommand;
import org.eclipse.jdt.ls.core.internal.corrections.CorrectionMessages;
import org.eclipse.ltk.core.refactoring.Change;

/**
 * Sets a single compiler problem's severity option (e.g. "ignore") for the
 * enclosing project directly, by writing to the project's JDT preferences
 * (.settings/org.eclipse.jdt.core.prefs) - the same file a user could edit
 * by hand. Unlike the desktop Eclipse equivalent, this does not open a
 * preferences dialog: the chosen severity is applied immediately.
 */
public final class ConfigureProblemSeverityProposal extends ChangeCorrectionProposalCore {

	private final IJavaProject fProject;
	private final String fOptionId;
	private final String fSeverity;
	private final String fProblemMessage;

	public ConfigureProblemSeverityProposal(String name, IJavaProject project, String optionId, String severity, String problemMessage, int relevance) {
		super(name, null, relevance);
		fProject = project;
		fOptionId = optionId;
		fSeverity = severity;
		fProblemMessage = problemMessage;
	}

	@Override
	public Object getAdditionalProposalInfo(IProgressMonitor monitor) {
		return Messages.format(CorrectionMessages.ConfigureProblemSeveritySubProcessor_ignore_project_info, fProblemMessage);
	}

	@Override
	protected Change createChange() throws CoreException {
		try {
			URI projectUri = fProject.getProject().getLocationURI();
			ProjectCommand.updateProjectSettings(projectUri.toString(), Collections.singletonMap(fOptionId, fSeverity));
		} catch (Exception e) {
			// continue
		}
		return null;
	}

}
