/*******************************************************************************
 * Copyright (c) 2021 Microsoft Corporation and others.
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

package org.eclipse.jdt.ls.core.internal.managers;

import java.util.List;
import java.util.Objects;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IFolder;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.jdt.core.IClasspathEntry;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.manipulation.CoreASTProvider;
import org.eclipse.jdt.ls.core.internal.JDTUtils;
import org.eclipse.jdt.ls.core.internal.JavaLanguageServerPlugin;
import org.eclipse.jdt.ls.core.internal.ProjectUtils;
import org.eclipse.jdt.ls.core.internal.commands.DiagnosticsCommand;
import org.eclipse.jdt.ls.core.internal.handlers.DiagnosticsHandler;
import org.eclipse.jdt.ls.core.internal.preferences.IPreferencesChangeListener;
import org.eclipse.jdt.ls.core.internal.preferences.Preferences;
import org.eclipse.lsp4j.MessageParams;
import org.eclipse.lsp4j.MessageType;

public class InvisibleProjectPreferenceChangeListener implements IPreferencesChangeListener {

	@Override
	public void preferencesChange(Preferences oldPreferences, Preferences newPreferences) {
		try {
			if (!Objects.equals(oldPreferences.getInvisibleProjectSourcePaths(), newPreferences.getInvisibleProjectSourcePaths())) {
				for (IJavaProject javaProject : ProjectUtils.getJavaProjects()) {
					IProject project = javaProject.getProject();
					if (ProjectUtils.isVisibleProject(project)) {
						continue;
					}
					if (project.equals(ProjectsManager.getDefaultProject())) {
						continue;
					}

					IFolder workspaceLinkFolder = javaProject.getProject().getFolder(ProjectUtils.WORKSPACE_LINK);
					IPath rootPath = ProjectUtils.findBelongedWorkspaceRoot(workspaceLinkFolder.getLocation());
					if (rootPath == null) {
						continue;
					}
					List<IPath> sourcePaths = InvisibleProjectImporter.getSourcePaths(newPreferences.getInvisibleProjectSourcePaths(), workspaceLinkFolder);
					List<IPath> excludingPaths = InvisibleProjectImporter.getExcludingPath(javaProject, rootPath, workspaceLinkFolder);
					IPath outputPath = InvisibleProjectImporter.getOutputPath(javaProject, newPreferences.getInvisibleProjectOutputPath(), true /*isUpdate*/);
					IClasspathEntry[] classpathEntries = InvisibleProjectImporter.resolveClassPathEntries(javaProject, sourcePaths, excludingPaths, outputPath);
					javaProject.setRawClasspath(classpathEntries, outputPath, new NullProgressMonitor());
					if (JavaLanguageServerPlugin.getInstance().getProtocol() != null && JavaLanguageServerPlugin.getInstance().getProtocol().getClientConnection() != null) {
						for (ICompilationUnit unit : JavaCore.getWorkingCopies(null)) {
							IPath path = unit.getPath();
							IFile file = ResourcesPlugin.getWorkspace().getRoot().getFile(path);
							if (file.exists()) {
								String contents = null;
								try {
									if (unit.hasUnsavedChanges()) {
										contents = unit.getSource();
									}
								} catch (Exception e) {
									JavaLanguageServerPlugin.logException(e.getMessage(), e);
								}
								unit.discardWorkingCopy();
								if (unit.equals(CoreASTProvider.getInstance().getActiveJavaElement())) {
									CoreASTProvider.getInstance().disposeAST();
								}
								unit = JavaCore.createCompilationUnitFrom(file);
								unit.becomeWorkingCopy(null);
								if (contents != null) {
									unit.getBuffer().setContents(contents);
								}
							}
							DiagnosticsHandler diagnosticHandler = new DiagnosticsHandler(JavaLanguageServerPlugin.getInstance().getProtocol().getClientConnection(), unit);
							diagnosticHandler.clearDiagnostics();
							DiagnosticsCommand.refreshDiagnostics(JDTUtils.toURI(unit), "thisFile", JDTUtils.isDefaultProject(unit) || !JDTUtils.isOnClassPath(unit));
						}
					}
				}
			} else if (!Objects.equals(oldPreferences.getInvisibleProjectOutputPath(), newPreferences.getInvisibleProjectOutputPath())) {
				for (IJavaProject javaProject : ProjectUtils.getJavaProjects()) {
					IProject project = javaProject.getProject();
					if (ProjectUtils.isVisibleProject(project)) {
						continue;
					}
					if (project.equals(ProjectsManager.getDefaultProject())) {
						continue;
					}

					IPath outputPath = InvisibleProjectImporter.getOutputPath(javaProject, newPreferences.getInvisibleProjectOutputPath(), true /*isUpdate*/);
					javaProject.setOutputLocation(outputPath, new NullProgressMonitor());
				}
			}
		} catch (CoreException e) {
			JavaLanguageServerPlugin.getProjectsManager().getConnection().showMessage(new MessageParams(MessageType.Error, e.getMessage()));
		}
	}
}
