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

import org.eclipse.core.resources.IFolder;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.jdt.core.IClasspathEntry;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.ls.core.internal.JavaLanguageServerPlugin;
import org.eclipse.jdt.ls.core.internal.ProjectUtils;
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
					ProjectUtils.refreshDiagnostics(new NullProgressMonitor());
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
