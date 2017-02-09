/*******************************************************************************
 * Copyright (c) 2016-2017 Red Hat Inc. and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Red Hat Inc. - initial API and implementation
 *******************************************************************************/
package org.eclipse.jdt.ls.core.internal.managers;

import java.io.File;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.eclipse.core.resources.IContainer;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IWorkspaceRoot;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.Path;
import org.eclipse.core.runtime.SubMonitor;
import org.eclipse.jdt.ls.core.internal.JavaLanguageServerPlugin;
import org.eclipse.m2e.core.MavenPlugin;
import org.eclipse.m2e.core.embedder.MavenModelManager;
import org.eclipse.m2e.core.internal.preferences.MavenConfigurationImpl;
import org.eclipse.m2e.core.project.IMavenProjectImportResult;
import org.eclipse.m2e.core.project.IProjectConfigurationManager;
import org.eclipse.m2e.core.project.LocalProjectScanner;
import org.eclipse.m2e.core.project.MavenProjectInfo;
import org.eclipse.m2e.core.project.MavenUpdateRequest;
import org.eclipse.m2e.core.project.ProjectImportConfiguration;

public class MavenProjectImporter extends AbstractProjectImporter {

	private static final String POM_FILE = "pom.xml";

	private Set<MavenProjectInfo> projectInfos = null;

	private IProjectConfigurationManager configurationManager;

	public MavenProjectImporter() {
		this(MavenPlugin.getProjectConfigurationManager());
	}

	public MavenProjectImporter(IProjectConfigurationManager configurationManager) {
		this.configurationManager = configurationManager;
	}


	@Override
	public boolean applies(IProgressMonitor monitor) throws InterruptedException, CoreException {
		Set<MavenProjectInfo> files = getMavenProjectInfo(monitor);
		return files != null && !files.isEmpty();
	}

	synchronized Set<MavenProjectInfo> getMavenProjectInfo(IProgressMonitor monitor) throws InterruptedException {
		if (projectInfos == null) {
			projectInfos = collectMavenProjectInfo(monitor);
		}
		return projectInfos;
	}

	Set<MavenProjectInfo> collectMavenProjectInfo(IProgressMonitor monitor) throws InterruptedException {
		MavenModelManager modelManager = MavenPlugin.getMavenModelManager();
		return getMavenProjects(getProjectDirectory(), modelManager, monitor);
	}

	@Override
	public void reset() {
		projectInfos = null;
	}

	@Override
	@SuppressWarnings("restriction")
	public void importToWorkspace(IProgressMonitor monitor) throws CoreException, InterruptedException {
		JavaLanguageServerPlugin.logInfo("Importing Maven project(s)");
		MavenConfigurationImpl configurationImpl = (MavenConfigurationImpl)MavenPlugin.getMavenConfiguration();
		configurationImpl.setDownloadSources(true);
		SubMonitor subMonitor = SubMonitor.convert(monitor, 100);
		Set<MavenProjectInfo> files = getMavenProjectInfo(subMonitor.split(5));
		ProjectImportConfiguration importConfig = new ProjectImportConfiguration();
		List<IMavenProjectImportResult> importResults = configurationManager.importProjects(files, importConfig, subMonitor.split(95));
		updateProjects(importConfig, importResults, monitor);
	}

	private File getProjectDirectory() {
		return rootFolder;
	}

	private void updateProjects(ProjectImportConfiguration importConfig, List<IMavenProjectImportResult> importResults, IProgressMonitor monitor) throws CoreException {
		IWorkspaceRoot root = ResourcesPlugin.getWorkspace().getRoot();
		for (IMavenProjectImportResult importResult : importResults) {
			IProject project = importResult.getProject();
			if (project == null) {
				//project already existed?
				File projectDir = importResult.getMavenProjectInfo().getPomFile().getParentFile();
				IContainer container = root.getContainerForLocation(new Path(projectDir.getAbsolutePath()));
				if (container instanceof IProject) {
					project = (IProject)container;
					project.open(monitor);
					project.refreshLocal(IResource.DEPTH_INFINITE, monitor);
					configurationManager.updateProjectConfiguration(new MavenUpdateRequest(project, MavenPlugin.getMavenConfiguration().isOffline(), true), monitor);
				}
			}
		}
	}

	private Set<MavenProjectInfo> getMavenProjects(File directory, MavenModelManager modelManager, IProgressMonitor monitor) throws InterruptedException {
		if (directory == null) {
			return Collections.emptySet();
		}
		LocalProjectScanner scanner = new LocalProjectScanner(directory.getParentFile(), directory.toString(), false, modelManager);
		scanner.run(monitor);
		return collectProjects(scanner.getProjects());
	}

	public boolean isMavenProject() {
		return  isMavenProject(getProjectDirectory());
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
