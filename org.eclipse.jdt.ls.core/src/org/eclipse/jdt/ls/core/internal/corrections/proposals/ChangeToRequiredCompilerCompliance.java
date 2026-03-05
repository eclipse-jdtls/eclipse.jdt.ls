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

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.URIUtil;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.manipulation.ChangeCorrectionProposalCore;
import org.eclipse.jdt.internal.corext.util.Messages;
import org.eclipse.jdt.ls.core.internal.ExternalFileChange;
import org.eclipse.jdt.ls.core.internal.JavaLanguageServerPlugin;
import org.eclipse.jdt.ls.core.internal.ResourceUtils;
import org.eclipse.jdt.ls.core.internal.commands.ProjectCommand;
import org.eclipse.jdt.ls.core.internal.corrections.CorrectionMessages;
import org.eclipse.jface.text.Document;
import org.eclipse.jface.text.IDocument;
import org.eclipse.ltk.core.refactoring.Change;
import org.eclipse.text.edits.ReplaceEdit;

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
		URI settingsURI = JavaLanguageServerPlugin.getPreferencesManager().getPreferences().getSettingsAsURI();
		File settingsFile = null;
		boolean projectSettings = false;
		try {
			if (settingsURI != null && URIUtil.isFileURI(settingsURI)) {
				settingsFile = ResourceUtils.toFile(settingsURI);
			} else {
				String settingsUrl = JavaLanguageServerPlugin.getPreferencesManager().getPreferences().getSettingsUrl();
				settingsURI = new URI("file://" + fProject.getProject().getRawLocation() + "/" + settingsUrl); //$NON-NLS-1$ //$NON-NLS-2$
				if (settingsURI != null && URIUtil.isFileURI(settingsURI)) {
					settingsFile = ResourceUtils.toFile(settingsURI);
					projectSettings = true;
				}
			}
			if (settingsFile != null && settingsFile.exists()) {
				Map<String, Object> options = new LinkedHashMap<>();
				if (!projectSettings) {
					options = readOptions(settingsFile);
				}
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
				if (projectSettings) {
					try {
						URI projectUri = fProject.getProject().getLocationURI();
						ProjectCommand.updateProjectSettings(projectUri.toString(), options);
					} catch (Exception e) {
						// continue
					}
					return null;
				} else {
					String content = ResourceUtils.getContent(settingsURI);
					IDocument doc = new Document(content);
					ExternalFileChange editChange = new ExternalFileChange(fName, doc, settingsURI);
					ReplaceEdit edit = new ReplaceEdit(0, content.length(), options.entrySet().stream().map(entry -> entry.getKey() + "=" + entry.getValue() + "\n").collect(Collectors.joining()));
					editChange.setEdit(edit);
					return editChange;
				}
			}
		} catch (IOException | URISyntaxException e) {
			throw new CoreException(Status.error(CorrectionMessages.UnexpectedError, e));
		}
		return super.createChange();
	}

	private Map<String, Object> readOptions(File file) throws FileNotFoundException, IOException {
		Map<String, Object> options = new LinkedHashMap<>();
		try (FileReader reader = new FileReader(file); BufferedReader bf = new BufferedReader(reader);) {
			String line = null;
			while ((line = bf.readLine()) != null) {
				String[] parts = line.split("="); //$NON-NLS-1$
				if (parts.length == 2) {
					options.put(parts[0], parts[1]);
				}
			}
		}
		return options;
	}

}