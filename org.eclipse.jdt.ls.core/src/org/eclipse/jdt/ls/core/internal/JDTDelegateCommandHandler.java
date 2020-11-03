/*******************************************************************************
 * Copyright (c) 2017,2020 Microsoft Corporation and others.
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
package org.eclipse.jdt.ls.core.internal;

import java.util.ArrayList;
import java.util.List;

import org.apache.commons.lang3.StringUtils;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.jdt.ls.core.internal.commands.BuildPathCommand;
import org.eclipse.jdt.ls.core.internal.commands.DiagnosticsCommand;
import org.eclipse.jdt.ls.core.internal.commands.OrganizeImportsCommand;
import org.eclipse.jdt.ls.core.internal.commands.ProjectCommand;
import org.eclipse.jdt.ls.core.internal.commands.ProjectCommand.ClasspathOptions;
import org.eclipse.jdt.ls.core.internal.handlers.ResolveSourceMappingHandler;
import org.eclipse.jdt.ls.core.internal.commands.SemanticTokensCommand;
import org.eclipse.jdt.ls.core.internal.commands.SourceAttachmentCommand;
import org.eclipse.jdt.ls.core.internal.semantictokens.SemanticTokensLegend;
import org.eclipse.lsp4j.WorkspaceEdit;

public class JDTDelegateCommandHandler implements IDelegateCommandHandler {

	/* (non-Javadoc)
	 * @see org.eclipse.jdt.ls.core.internal.IDelegateCommandHandler#executeCommand(java.lang.String, java.util.List, org.eclipse.core.runtime.IProgressMonitor)
	 */
	@Override
	public Object executeCommand(String commandId, List<Object> arguments, IProgressMonitor monitor) throws Exception {
		if (!StringUtils.isBlank(commandId)) {
			switch (commandId) {
				case "java.edit.organizeImports":
					final OrganizeImportsCommand c = new OrganizeImportsCommand();
					final Object result = c.organizeImports(arguments);
					final boolean applyNow = JavaLanguageServerPlugin.getPreferencesManager().getClientPreferences().isWorkspaceApplyEditSupported();
					if (applyNow) {
						JavaLanguageServerPlugin.getInstance().getClientConnection().applyWorkspaceEdit((WorkspaceEdit) result);
						// return an empty object to avoid errors on client
						return new Object();
					} else {
						// we are returning a workspace edit here in order to accomodate the clients that
						// did not implement workspace/applyEdit from LSP. This still allows them to implement applying
						// workspaceEdit on the custom command.
						return result;
					}
				case "java.project.resolveSourceAttachment":
					return SourceAttachmentCommand.resolveSourceAttachment(arguments, monitor);
				case "java.project.updateSourceAttachment":
					return SourceAttachmentCommand.updateSourceAttachment(arguments, monitor);
				case "java.project.addToSourcePath":
					String sourceFolder = (String) arguments.get(0);
					return BuildPathCommand.addToSourcePath(sourceFolder);
				case "java.project.removeFromSourcePath":
					String sourceFolder1 = (String) arguments.get(0);
					return BuildPathCommand.removeFromSourcePath(sourceFolder1);
				case "java.project.listSourcePaths":
					return BuildPathCommand.listSourcePaths();
				case "java.project.getSettings":
					return ProjectCommand.getProjectSettings((String) arguments.get(0), (ArrayList<String>) arguments.get(1));
				case "java.project.getClasspaths":
					return ProjectCommand.getClasspaths((String) arguments.get(0), JSONUtility.toModel(arguments.get(1), ClasspathOptions.class));
				case "java.project.isTestFile":
					return ProjectCommand.isTestFile((String) arguments.get(0));
				case "java.project.getAll":
					return ProjectCommand.getAllJavaProjects();
				case "java.project.refreshDiagnostics":
					return DiagnosticsCommand.refreshDiagnostics((String) arguments.get(0), (String) arguments.get(1), (boolean) arguments.get(2));
				case "java.project.provideSemanticTokens":
					return SemanticTokensCommand.provide((String) arguments.get(0));
				case "java.project.getSemanticTokensLegend":
					return new SemanticTokensLegend();
				case "java.project.import":
					ProjectCommand.importProject(monitor);
					return null;
				case "java.project.resolveStackTraceLocation":
					List<String> projectNames = null;
					if (arguments.size() > 1) {
						projectNames = (ArrayList<String>) arguments.get(1);
					}
					return ResolveSourceMappingHandler.resolveStackTraceLocation((String) arguments.get(0), projectNames);
				default:
					break;
			}
		}
		throw new UnsupportedOperationException(String.format("Java language server doesn't support the command '%s'.", commandId));
	}

}
