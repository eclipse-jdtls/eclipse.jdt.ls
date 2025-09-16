/*******************************************************************************
 * Copyright (c) 2017-2023 Microsoft Corporation and others.
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

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.eclipse.buildship.core.internal.util.gradle.GradleVersion;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.jdt.ls.core.internal.commands.BuildPathCommand;
import org.eclipse.jdt.ls.core.internal.commands.DiagnosticsCommand;
import org.eclipse.jdt.ls.core.internal.commands.OrganizeImportsCommand;
import org.eclipse.jdt.ls.core.internal.commands.ProjectClasspathEntries;
import org.eclipse.jdt.ls.core.internal.commands.ProjectCommand;
import org.eclipse.jdt.ls.core.internal.commands.ProjectCommand.ClasspathOptions;
import org.eclipse.jdt.ls.core.internal.commands.ProjectCommand.GetAllProjectOptions;
import org.eclipse.jdt.ls.core.internal.commands.SourceAttachmentCommand;
import org.eclipse.jdt.ls.core.internal.commands.TroubleshootingInfoCommand;
import org.eclipse.jdt.ls.core.internal.commands.TypeHierarchyCommand;
import org.eclipse.jdt.ls.core.internal.commands.VmCommand;
import org.eclipse.jdt.ls.core.internal.framework.protobuf.ProtobufSupport;
import org.eclipse.jdt.ls.core.internal.handlers.BundleUtils;
import org.eclipse.jdt.ls.core.internal.handlers.CompletionHandler;
import org.eclipse.jdt.ls.core.internal.handlers.CreateModuleInfoHandler;
import org.eclipse.jdt.ls.core.internal.handlers.FormatterHandler;
import org.eclipse.jdt.ls.core.internal.handlers.PasteEventHandler;
import org.eclipse.jdt.ls.core.internal.handlers.PasteEventHandler.PasteEventParams;
import org.eclipse.jdt.ls.core.internal.handlers.ResolveSourceMappingHandler;
import org.eclipse.jdt.ls.core.internal.handlers.SmartDetectionHandler;
import org.eclipse.jdt.ls.core.internal.handlers.SmartDetectionParams;
import org.eclipse.jdt.ls.core.internal.managers.ContentProviderManager;
import org.eclipse.jdt.ls.core.internal.managers.GradleProjectImporter;
import org.eclipse.lsp4j.SymbolInformation;
import org.eclipse.lsp4j.TextDocumentPositionParams;
import org.eclipse.lsp4j.WorkspaceEdit;
import org.eclipse.lsp4j.legacy.typeHierarchy.ResolveTypeHierarchyItemParams;
import org.eclipse.lsp4j.legacy.typeHierarchy.TypeHierarchyDirection;
import org.eclipse.lsp4j.legacy.typeHierarchy.TypeHierarchyItem;
import org.eclipse.lsp4j.legacy.typeHierarchy.TypeHierarchyParams;

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
				case "java.edit.stringFormatting":
					FormatterHandler handler = new FormatterHandler(JavaLanguageServerPlugin.getPreferencesManager());
					return handler.stringFormatting((String) arguments.get(0), JSONUtility.toModel(arguments.get(1), Map.class), Integer.parseInt((String) arguments.get(2)), monitor);
				case JAVA_EDIT_HANDLE_PASTE_EVENT:
					return PasteEventHandler.handlePasteEvent(JSONUtility.toLsp4jModel(arguments.get(0), PasteEventParams.class), monitor);
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
				case "java.project.updateClassPaths": {
					String projectUri = (String) arguments.get(0);
					ProjectClasspathEntries entries = (JSONUtility.toModel(arguments.get(1), ProjectClasspathEntries.class));
					ProjectCommand.updateClasspaths(projectUri, entries.getClasspathEntries(), monitor);
					return null;
				}
				case "java.project.updateSettings": {
					String projectUri = (String) arguments.get(0);
					Map<String, Object> options = JSONUtility.toModel(arguments.get(1), Map.class);
					ProjectCommand.updateProjectSettings(projectUri, options);
					return null;
				}
				case "java.project.isTestFile":
					return ProjectCommand.isTestFile((String) arguments.get(0));
				case "java.project.getAll":
					if (!arguments.isEmpty()) {
						GetAllProjectOptions option = JSONUtility.toModel(arguments.get(0), GetAllProjectOptions.class);
						if (option.includeNonJava) {
							return ProjectCommand.getAllProjects();
						}
					}
					return ProjectCommand.getAllJavaProjects();
				case "java.project.refreshDiagnostics":
					if (arguments.size() < 4) {
						return DiagnosticsCommand.refreshDiagnostics((String) arguments.get(0), (String) arguments.get(1), (boolean) arguments.get(2));
					}
					return DiagnosticsCommand.refreshDiagnostics((String) arguments.get(0), (String) arguments.get(1), (boolean) arguments.get(2), (boolean) arguments.get(3));
				case "java.project.import":
					ProjectCommand.importProject(monitor);
					return null;
				case "java.project.changeImportedProjects":
					ProjectCommand.changeImportedProjects((ArrayList<String>) arguments.get(0),
							(ArrayList<String>) arguments.get(1), (ArrayList<String>) arguments.get(2), monitor);
					return null;
				case "java.project.resolveStackTraceLocation":
					List<String> projectNames = null;
					if (arguments.size() > 1) {
						projectNames = (ArrayList<String>) arguments.get(1);
					}
					return ResolveSourceMappingHandler.resolveStackTraceLocation((String) arguments.get(0), projectNames);
				case "java.navigate.resolveTypeHierarchy":
					TypeHierarchyCommand resolveTypeHierarchyCommand = new TypeHierarchyCommand();
					TypeHierarchyItem toResolve = JSONUtility.toModel(arguments.get(0), TypeHierarchyItem.class);
					TypeHierarchyDirection resolveDirection = TypeHierarchyDirection.forValue(JSONUtility.toModel(arguments.get(1), Integer.class));
					int resolveDepth = JSONUtility.toModel(arguments.get(2), Integer.class);
					ResolveTypeHierarchyItemParams resolveParams = new ResolveTypeHierarchyItemParams();
					resolveParams.setItem(toResolve);
					resolveParams.setDirection(resolveDirection);
					resolveParams.setResolve(resolveDepth);
					TypeHierarchyItem resolvedItem = resolveTypeHierarchyCommand.resolveTypeHierarchy(resolveParams, monitor);
					return resolvedItem;
				case "java.navigate.openTypeHierarchy":
					TypeHierarchyCommand typeHierarchyCommand = new TypeHierarchyCommand();
					TypeHierarchyParams params = new TypeHierarchyParams();
					TextDocumentPositionParams textParams = JSONUtility.toModel(arguments.get(0), TextDocumentPositionParams.class);
					TypeHierarchyDirection direction = TypeHierarchyDirection.forValue(JSONUtility.toModel(arguments.get(1), Integer.class));
					int resolve = JSONUtility.toModel(arguments.get(2), Integer.class);
					params.setResolve(resolve);
					params.setDirection(direction);
					params.setTextDocument(textParams.getTextDocument());
					params.setPosition(textParams.getPosition());
					TypeHierarchyItem typeHierarchyItem = typeHierarchyCommand.typeHierarchy(params, monitor);
					return typeHierarchyItem;
				case "java.project.upgradeGradle": {
					String projectUri = (String) arguments.get(0);
					String gradleVersion = arguments.size() > 1 ? (String) arguments.get(1) : null;
					if (gradleVersion == null) {
						gradleVersion = GradleVersion.current().getVersion();
					}
					return GradleProjectImporter.upgradeGradleVersion(projectUri, gradleVersion, monitor);
				}
				case "java.project.resolveWorkspaceSymbol":
					SymbolInformation si = JSONUtility.toModel(arguments.get(0), SymbolInformation.class);
					return ProjectCommand.resolveWorkspaceSymbol(si);
				case "java.project.updateJdk": {
					String projectUri = (String) arguments.get(0);
					String jdkPath = (String) arguments.get(1);
					return ProjectCommand.updateProjectJdk(projectUri, jdkPath, monitor);
				}
				case "java.protobuf.generateSources":
					ProtobufSupport.generateProtobufSources((ArrayList<String>) arguments.get(0), monitor);
					return null;
				case "java.project.createModuleInfo":
					return CreateModuleInfoHandler.createModuleInfo((String) arguments.get(0), monitor);
				case "java.reloadBundles":
					try {
						BundleUtils.loadBundles((ArrayList<String>) arguments.get(0));
						return true;
					} catch (CoreException e) {
						JavaLanguageServerPlugin.log(e);
						return false;
					}
				case "java.completion.onDidSelect":
					CompletionHandler completionHandler = new CompletionHandler(JavaLanguageServerPlugin.getPreferencesManager());
					String requestId = (String) arguments.get(0);
					String proposalId = (String) arguments.get(1);
					completionHandler.onDidCompletionItemSelect(requestId, proposalId);
					return new Object();
				case "java.decompile":
					String uri = (String) arguments.get(0);
					try {
						ContentProviderManager contentProvider = JavaLanguageServerPlugin.getContentProviderManager();
						return contentProvider.getContent(new URI(uri), monitor);
					} catch (URISyntaxException e) {
						return false;
					}
				case JAVA_EDIT_SMART_SEMICOLON_DETECTION:
					if (!JavaLanguageServerPlugin.getPreferencesManager().getPreferences().isSmartSemicolonDetection()) {
						return null;
					}
					SmartDetectionParams smartDetectionParams = JSONUtility.toModel(arguments.get(0), SmartDetectionParams.class);
					return new SmartDetectionHandler(smartDetectionParams).getLocation(monitor);
				case VmCommand.GET_ALL_INSTALL_COMMAND_ID:
					return VmCommand.getAllVmInstalls();
				case TroubleshootingInfoCommand.GET_TROUBLESHOOTING_INFO_COMMAND:
					return TroubleshootingInfoCommand.getTroubleshootingInfo();
				case "java.project.resolveText":
					return PasteEventHandler.handleFilePasteEvent((String) arguments.get(0), (String) arguments.get(1), monitor);
				default:
					break;
			}
		}
		throw new UnsupportedOperationException(String.format("Java language server doesn't support the command '%s'.", commandId));
	}

}
