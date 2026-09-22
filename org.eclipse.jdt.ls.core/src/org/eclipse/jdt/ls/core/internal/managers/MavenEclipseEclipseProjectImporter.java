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
package org.eclipse.jdt.ls.core.internal.managers;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.maven.Maven;
import org.apache.maven.execution.DefaultMavenExecutionRequest;
import org.apache.maven.execution.MavenExecutionRequestPopulator;
import org.apache.maven.execution.MavenExecutionResult;
import org.apache.maven.project.MavenProject;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.OperationCanceledException;
import org.eclipse.jdt.ls.core.internal.JavaLanguageServerPlugin;
import org.eclipse.jdt.ls.core.internal.ProjectUtils;
import org.eclipse.jdt.ls.core.internal.preferences.PreferenceManager;
import org.eclipse.m2e.core.MavenPlugin;
import org.eclipse.m2e.core.embedder.IMaven;
import org.eclipse.m2e.core.internal.IMavenConstants;
import org.osgi.service.component.annotations.Component;

/**
 * Imports a Maven project by using mvn eclipse:eclipse.
 *
 * @author David Thompson
 */
@Component
public class MavenEclipseEclipseProjectImporter extends EclipseProjectImporter {

	/* (non-Javadoc)
	 * @see org.eclipse.jdt.ls.core.internal.AbstractProjectImporter#applies(org.eclipse.core.runtime.IProgressMonitor)
	 */
	@Override
	public boolean applies(IProgressMonitor monitor) throws OperationCanceledException, CoreException {
		PreferenceManager preferencesManager = JavaLanguageServerPlugin.getPreferencesManager();
		if (preferencesManager != null && !preferencesManager.getPreferences().isImportMavenEnabled()) {
			return false;
		}
		if (directories == null) {
			//@formatter:off
			BasicFileDetector mavenDetector = new BasicFileDetector(rootFolder.toPath(), IMavenConstants.POM_FILE_NAME)
					.includeNested(false)
					.addExclusions("**/target"); //default maven build dir
			//@formatter:on
			for (IProject project : ProjectUtils.getAllProjects()) {
				if (!ProjectUtils.isMavenProject(project)) {
					String path = project.getLocation().toOSString();
					mavenDetector.addExclusions(path.replace("\\", "\\\\"));
				}
			}
			directories = mavenDetector.scan(monitor);
		}
		return !directories.isEmpty();
	}

	/* (non-Javadoc)
	 * @see org.eclipse.jdt.ls.core.internal.AbstractProjectImporter#applies(Collection<IPath>, org.eclipse.core.runtime.IProgressMonitor)
	 */
	@Override
	public boolean applies(Collection<IPath> buildFiles, IProgressMonitor monitor) {
		if (!getPreferences().isImportMavenEnabled()) {
			return false;
		}

		Collection<java.nio.file.Path> configurationDirs = findProjectPathByConfigurationName(buildFiles, Arrays.asList(IMavenConstants.POM_FILE_NAME), true /*includeNested*/);
		if (configurationDirs == null || configurationDirs.isEmpty()) {
			return false;
		}

		Set<java.nio.file.Path> noneMavenProjectPaths = new HashSet<>();
		for (IProject project : ProjectUtils.getAllProjects()) {
			if (!ProjectUtils.isMavenProject(project)) {
				noneMavenProjectPaths.add(project.getLocation().toFile().toPath());
			}
		}

		this.directories = configurationDirs.stream().filter(d -> {
			boolean folderIsImported = noneMavenProjectPaths.stream().anyMatch(path -> {
				return path.compareTo(d) == 0;
			});
			return !folderIsImported;
		}).collect(Collectors.toList());

		return !this.directories.isEmpty();
	}

	/* (non-Javadoc)
	 * @see org.eclipse.jdt.ls.core.internal.AbstractProjectImporter#importToWorkspace(org.eclipse.core.runtime.IProgressMonitor)
	 */
	@Override
	public void importToWorkspace(IProgressMonitor monitor) throws OperationCanceledException, CoreException {
		try {
			IMaven eyeMaven = MavenPlugin.getMaven();
			Maven maven = eyeMaven.lookup(Maven.class);
			for (Path directory : directories) {
				MavenProject project = eyeMaven.readProject(directory.resolve(IMavenConstants.POM_FILE_NAME).toFile(), monitor);
				DefaultMavenExecutionRequest req = new DefaultMavenExecutionRequest();
				eyeMaven.lookup(MavenExecutionRequestPopulator.class).populateDefaults(req);
				req.setSystemProperties(System.getProperties());
				req.setGoals(List.of("eclipse:eclipse"));
				req.setPom(project.getFile());
				req.setBaseDirectory(directory.toFile());
				MavenExecutionResult res = maven.execute(req);
				if (res.hasExceptions()) {
					if (res.getExceptions().get(0) instanceof Exception) {
						throw (Exception) res.getExceptions().get(0);
					}
				}
				JavaLanguageServerPlugin.logInfo("Imported " + directory.toString() + "using mvn eclipse:eclipse");
			}
		} catch (Exception e) {
			JavaLanguageServerPlugin.logException(e);
		}

		super.importToWorkspace(monitor);
	}

	/* (non-Javadoc)
	 * @see org.eclipse.jdt.ls.core.internal.AbstractProjectImporter#reset()
	 */
	@Override
	public void reset() {
		// TODO Auto-generated method stub
		super.reset();
	}

}
