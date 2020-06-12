/*******************************************************************************
 * Copyright (c) 2016-2017 Red Hat Inc. and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *     Red Hat Inc. - initial API and implementation
 *******************************************************************************/
package org.eclipse.jdt.ls.core.internal.managers;

import java.io.File;
import java.nio.file.FileSystems;
import java.nio.file.PathMatcher;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import org.eclipse.core.resources.IContainer;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IWorkspaceRoot;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.OperationCanceledException;
import org.eclipse.core.runtime.Path;
import org.eclipse.core.runtime.SubMonitor;
import org.eclipse.jdt.ls.core.internal.AbstractProjectImporter;
import org.eclipse.jdt.ls.core.internal.JavaLanguageServerPlugin;
import org.eclipse.jdt.ls.core.internal.ProjectUtils;
import org.eclipse.jdt.ls.core.internal.preferences.PreferenceManager;
import org.eclipse.m2e.core.MavenPlugin;
import org.eclipse.m2e.core.embedder.MavenModelManager;
import org.eclipse.m2e.core.internal.preferences.MavenConfigurationImpl;
import org.eclipse.m2e.core.internal.preferences.ProblemSeverity;
import org.eclipse.m2e.core.project.IMavenProjectImportResult;
import org.eclipse.m2e.core.project.IProjectConfigurationManager;
import org.eclipse.m2e.core.project.LocalProjectScanner;
import org.eclipse.m2e.core.project.MavenProjectInfo;
import org.eclipse.m2e.core.project.ProjectImportConfiguration;

@SuppressWarnings("restriction")
public class MavenProjectImporter extends AbstractProjectImporter {

	private static final int MAX_PROJECTS_TO_IMPORT = 50;

	private static final long MAX_MEMORY = 1536 * 1024 * 1024; // 1.5g

	public static final String IMPORTING_MAVEN_PROJECTS = "Importing Maven project(s)";

	public static final String POM_FILE = "pom.xml";

	private Set<MavenProjectInfo> projectInfos = null;

	private IProjectConfigurationManager configurationManager;
	private DigestStore digestStore;

	public MavenProjectImporter() {
		this.configurationManager = MavenPlugin.getProjectConfigurationManager();
		this.digestStore = JavaLanguageServerPlugin.getDigestStore();
	}


	@Override
	public boolean applies(IProgressMonitor monitor) throws OperationCanceledException, CoreException {
		PreferenceManager preferencesManager = JavaLanguageServerPlugin.getPreferencesManager();
		if (preferencesManager != null && !preferencesManager.getPreferences().isImportMavenEnabled()) {
			return false;
		}

		Set<File> existingProjectFolders = new HashSet<>();
		for (IProject project : ProjectUtils.getMavenProjects()) {
			existingProjectFolders.add(new File(project.getLocationURI()));
		}

		Set<MavenProjectInfo> files = getMavenProjectInfo(monitor);
		if (files != null) {
			Iterator<MavenProjectInfo> iter = files.iterator();
			while (iter.hasNext()) {
				MavenProjectInfo projectInfo = iter.next();
				File dir = projectInfo.getPomFile() == null ? null : projectInfo.getPomFile().getParentFile();
				if (dir == null) {
					continue;
				}
				if (exclude(dir.toPath()) || existingProjectFolders.contains(dir)) {
					iter.remove();
				}
			}
		}
		return files != null && !files.isEmpty();
	}

	private boolean exclude(java.nio.file.Path path) {
		List<String> javaImportExclusions = JavaLanguageServerPlugin.getPreferencesManager().getPreferences().getJavaImportExclusions();
		boolean excluded = false;
		if (javaImportExclusions != null) {
			for (String pattern : javaImportExclusions) {
				boolean includePattern = false;
				if (pattern.startsWith("!")) {
					includePattern = true;
					pattern = pattern.substring(1);
				}
				PathMatcher matcher = FileSystems.getDefault().getPathMatcher("glob:" + pattern);
				if (matcher.matches(path)) {
					excluded = includePattern ? false : true;
				}
			}
		}
		return excluded;
	}

	synchronized Set<MavenProjectInfo> getMavenProjectInfo(IProgressMonitor monitor) throws OperationCanceledException {
		if (projectInfos == null) {
			projectInfos = collectMavenProjectInfo(monitor);
		}
		return projectInfos;
	}

	Set<MavenProjectInfo> collectMavenProjectInfo(IProgressMonitor monitor) throws OperationCanceledException {
		MavenModelManager modelManager = MavenPlugin.getMavenModelManager();
		return getMavenProjects(getProjectDirectory(), modelManager, monitor);
	}

	@Override
	public void reset() {
		projectInfos = null;
	}

	@Override
	public void importToWorkspace(IProgressMonitor monitor) throws CoreException, OperationCanceledException {
		JavaLanguageServerPlugin.logInfo(IMPORTING_MAVEN_PROJECTS);
		MavenConfigurationImpl configurationImpl = (MavenConfigurationImpl)MavenPlugin.getMavenConfiguration();
		configurationImpl.setDownloadSources(JavaLanguageServerPlugin.getPreferencesManager().getPreferences().isMavenDownloadSources());
		configurationImpl.setNotCoveredMojoExecutionSeverity(ProblemSeverity.ignore.toString());
		SubMonitor subMonitor = SubMonitor.convert(monitor, 105);
		subMonitor.setTaskName(IMPORTING_MAVEN_PROJECTS);
		Set<MavenProjectInfo> files = getMavenProjectInfo(subMonitor.split(5));
		IWorkspaceRoot root = ResourcesPlugin.getWorkspace().getRoot();
		Collection<MavenProjectInfo> toImport = new LinkedHashSet<>();
		//Separate existing projects from new ones
		for (MavenProjectInfo projectInfo : files) {
			File pom = projectInfo.getPomFile();
			IContainer container = root.getContainerForLocation(new Path(pom.getAbsolutePath()));
			// getContainerForLocation() will return the nearest container for the given path,
			// if the project has been imported, container.getProject() will return the imported IProject
			// otherwise, container.getProject() will return the root level project
			if (container != null && projectInfo.getParent() != null) {
				MavenProjectInfo parentInfo = projectInfo.getParent();
				File parentPom = parentInfo.getPomFile();
				IContainer parentContainer = root.getContainerForLocation(new Path(parentPom.getAbsolutePath()));
				if (parentContainer != null && Objects.equals(container.getProject(), parentContainer.getProject())) {
					container = null;
				}
			}
			if (container == null) {
				digestStore.updateDigest(pom.toPath());
				toImport.add(projectInfo);
			} else {
				IProject project = container.getProject();
				if (project != null) {
					//Project doesn't have the Maven nature, so we (re)import it
					digestStore.updateDigest(pom.toPath());
					// need to delete project due to m2e failing to create if linked and not the same name
					project.delete(IProject.FORCE | IProject.NEVER_DELETE_PROJECT_CONTENT, subMonitor.split(5));
					toImport.add(projectInfo);
				}
			}
		}
		if (!toImport.isEmpty()) {
			if (toImport.size() > MAX_PROJECTS_TO_IMPORT && Runtime.getRuntime().maxMemory() <= MAX_MEMORY) {
				JavaLanguageServerPlugin.logInfo("Projects size:" + toImport.size());
				Iterator<MavenProjectInfo> iter = toImport.iterator();
				List<IMavenProjectImportResult> results = new ArrayList<>(MAX_PROJECTS_TO_IMPORT);
				SubMonitor monitor2 = SubMonitor.convert(monitor, toImport.size() * 2);
				int it = 1;
				while (iter.hasNext()) {
					int percent = Math.min(100, it++ * 100 * MAX_PROJECTS_TO_IMPORT / (toImport.size() + 1));
					monitor2.setTaskName(percent + "% " + IMPORTING_MAVEN_PROJECTS);
					List<MavenProjectInfo> importPartial = new ArrayList<>();
					int i = 0;
					while (i++ < MAX_PROJECTS_TO_IMPORT && iter.hasNext()) {
						importPartial.add(iter.next());
					}
					ProjectImportConfiguration importConfig = new ProjectImportConfiguration();
					List<IMavenProjectImportResult> result = configurationManager.importProjects(importPartial, importConfig, monitor2.split(MAX_PROJECTS_TO_IMPORT));
					results.addAll(result);
					monitor2.setWorkRemaining(toImport.size() * 2 - it * MAX_PROJECTS_TO_IMPORT);
				}
				List<IProject> imported = new ArrayList<>(results.size());
				for (IMavenProjectImportResult result : results) {
					imported.add(result.getProject());
				}
				monitor2.setTaskName("Updating Maven project(s)");
				monitor2.done();
			} else {
				ProjectImportConfiguration importConfig = new ProjectImportConfiguration();
				configurationManager.importProjects(toImport, importConfig, subMonitor.split(75));
			}
		}
		subMonitor.setWorkRemaining(20);
		subMonitor.done();
	}

	private File getProjectDirectory() {
		return rootFolder;
	}

	private Set<MavenProjectInfo> getMavenProjects(File directory, MavenModelManager modelManager, IProgressMonitor monitor) throws OperationCanceledException {
		if (directory == null) {
			return Collections.emptySet();
		}
		try {
			LocalProjectScanner scanner = new LocalProjectScanner(directory.getParentFile(), directory.toString(), false, modelManager);
			scanner.run(monitor);
			return collectProjects(scanner.getProjects());
		} catch (InterruptedException e) {
			throw new OperationCanceledException();
		}
	}

	public boolean isMavenProject() {
		return isMavenProject(getProjectDirectory());
	}

	private boolean isMavenProject(File dir) {
		if (!isReadable(dir)
				|| !dir.isDirectory()) {
			return false;
		}
		return isReadable(new File(dir, POM_FILE));
	}

	private boolean isReadable(File destination) {
		return destination != null
				&& destination.canRead();
	}

	public Set<MavenProjectInfo> collectProjects(
			Collection<MavenProjectInfo> projects) {
		return new LinkedHashSet<MavenProjectInfo>() {
			private static final long serialVersionUID = 1L;

			public Set<MavenProjectInfo> collectProjects(
					Collection<MavenProjectInfo> projects) {
				for (MavenProjectInfo projectInfo : projects) {
					add(projectInfo);
					collectProjects(projectInfo.getProjects());
				}
				return this;
			}
		}.collectProjects(projects);
	}
}
