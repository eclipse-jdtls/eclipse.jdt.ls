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
import java.util.LinkedHashMap;
import java.util.Map;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.manipulation.ChangeCorrectionProposalCore;
import org.eclipse.jdt.internal.corext.util.Messages;
import org.eclipse.jdt.ls.core.internal.commands.ProjectCommand;
import org.eclipse.jdt.ls.core.internal.corrections.CorrectionMessages;
import org.eclipse.ltk.core.refactoring.Change;

public final class ChangeToRequiredCompilerCompliance extends ChangeCorrectionProposalCore {

	private final IJavaProject fProject;
	private final String fRequiredVersion;
	private final boolean fEnablePreviews;

	public ChangeToRequiredCompilerCompliance(String name, IJavaProject project, String requiredVersion, int relevance) {
		this(name, project, requiredVersion, false, relevance);
	}

	public ChangeToRequiredCompilerCompliance(String name, IJavaProject project, String requiredVersion, boolean enablePreviews, int relevance) {
		super(name, null, relevance);
		fProject = project;
		fRequiredVersion = requiredVersion;
		fEnablePreviews = enablePreviews;
	}


	@Override
	public Object getAdditionalProposalInfo(IProgressMonitor monitor) {
		StringBuilder message = new StringBuilder();
		message.append(Messages.format(CorrectionMessages.ReorgCorrectionsSubProcessor_required_compliance_changeproject_description, fRequiredVersion));

		if (fEnablePreviews) {
			message.append(CorrectionMessages.ReorgCorrectionsSubProcessor_enable_preview_features_info);
		}
		return message.toString();
	}

	/* (non-Javadoc)
	 * @see org.eclipse.jdt.core.manipulation.ChangeCorrectionProposalCore#createChange()
	 */
	@Override
	protected Change createChange() throws CoreException {
		Map<String, Object> options = new LinkedHashMap<>();
		options.put(JavaCore.COMPILER_COMPLIANCE, fRequiredVersion);
		options.put(JavaCore.COMPILER_SOURCE, fRequiredVersion);
		options.put(JavaCore.COMPILER_CODEGEN_TARGET_PLATFORM, fRequiredVersion);
		options.put(JavaCore.COMPILER_PB_ASSERT_IDENTIFIER, JavaCore.ERROR);
		options.put(JavaCore.COMPILER_PB_ENUM_IDENTIFIER, JavaCore.ERROR);
		options.put(JavaCore.COMPILER_RELEASE, JavaCore.ENABLED);
		if (fEnablePreviews) {
			options.put(JavaCore.COMPILER_PB_ENABLE_PREVIEW_FEATURES, JavaCore.ENABLED);
			options.put(JavaCore.COMPILER_PB_REPORT_PREVIEW_FEATURES, JavaCore.IGNORE);
		} else {
			options.put(JavaCore.COMPILER_PB_ENABLE_PREVIEW_FEATURES, JavaCore.DISABLED);
			options.put(JavaCore.COMPILER_PB_REPORT_PREVIEW_FEATURES, JavaCore.WARNING);
		}
		try {
			URI projectUri = fProject.getProject().getLocationURI();
			ProjectCommand.updateProjectSettings(projectUri.toString(), options);
		} catch (Exception e) {
			// continue
		}
		return null;
	}

}