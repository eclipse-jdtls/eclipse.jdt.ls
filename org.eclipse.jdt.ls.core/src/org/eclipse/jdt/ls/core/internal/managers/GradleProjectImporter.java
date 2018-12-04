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
import java.io.FilenameFilter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import org.eclipse.buildship.core.BuildConfiguration;
import org.eclipse.buildship.core.GradleCore;
import org.eclipse.buildship.core.GradleDistribution;
import org.eclipse.buildship.core.SynchronizationResult;
import org.eclipse.buildship.core.internal.CorePlugin;
import org.eclipse.buildship.core.internal.preferences.PersistentModel;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.SubMonitor;
import org.eclipse.jdt.ls.core.internal.AbstractProjectImporter;
import org.eclipse.jdt.ls.core.internal.JavaLanguageServerPlugin;
import org.eclipse.jdt.ls.core.internal.ProjectUtils;
import org.eclipse.jdt.ls.core.internal.preferences.PreferenceManager;

/**
 * @author Fred Bricon
 *
 */
public class GradleProjectImporter extends AbstractProjectImporter {

	public static final String GRADLE_HOME = "GRADLE_HOME";

	private static final String BUILD_GRADLE_DESCRIPTOR = "build.gradle";

	protected static final GradleDistribution DEFAULT_DISTRIBUTION = GradleDistribution.fromBuild();

	public static final String IMPORTING_GRADLE_PROJECTS = "Importing Gradle project(s)";

	private Collection<Path> directories;

	/* (non-Javadoc)
	 * @see org.eclipse.jdt.ls.core.internal.managers.IProjectImporter#applies(org.eclipse.core.runtime.IProgressMonitor)
	 */
	@Override
	public boolean applies(IProgressMonitor monitor) throws CoreException {
		if (rootFolder == null) {
			return false;
		}
		PreferenceManager preferencesManager = JavaLanguageServerPlugin.getPreferencesManager();
		if (preferencesManager != null && !preferencesManager.getPreferences().isImportGradleEnabled()) {
			return false;
		}
		if (directories == null) {
			BasicFileDetector gradleDetector = new BasicFileDetector(rootFolder.toPath(), BUILD_GRADLE_DESCRIPTOR)
					.includeNested(false)
					.addExclusions("**/build");//default gradle build dir
			directories = gradleDetector.scan(monitor);
		}
		return !directories.isEmpty();
	}

	/* (non-Javadoc)
	 * @see org.eclipse.jdt.ls.core.internal.managers.IProjectImporter#importToWorkspace(org.eclipse.core.runtime.IProgressMonitor)
	 */
	@Override
	public void importToWorkspace(IProgressMonitor monitor) throws CoreException {
		if (!applies(monitor)) {
			return;
		}
		int projectSize = directories.size();
		SubMonitor subMonitor = SubMonitor.convert(monitor, projectSize + 1);
		subMonitor.setTaskName(IMPORTING_GRADLE_PROJECTS);
		JavaLanguageServerPlugin.logInfo(IMPORTING_GRADLE_PROJECTS);
		subMonitor.worked(1);
		directories.forEach(d -> importDir(d, subMonitor.newChild(1)));
		subMonitor.done();
	}

	private void importDir(Path rootFolder, IProgressMonitor monitor) {
		if (monitor.isCanceled()) {
			return;
		}
		startSynchronization(rootFolder, monitor);
	}

	public static GradleDistribution getGradleDistribution(Path rootFolder) {
		GradleDistribution distribution = DEFAULT_DISTRIBUTION;
		if (Files.exists(rootFolder.resolve("gradlew"))) {
			distribution = GradleDistribution.fromBuild();
		} else {
			File gradleHomeFile = getGradleHomeFile();
			if (gradleHomeFile != null) {
				distribution = GradleDistribution.forLocalInstallation(gradleHomeFile);
			}
		}
		return distribution;
	}

	public static File getGradleHomeFile() {
		Map<String, String> env = System.getenv();
		Properties sysprops = System.getProperties();
		return getGradleHomeFile(env, sysprops);
	}

	public static File getGradleHomeFile(Map<String, String> env, Properties sysprops) {
		String gradleHome = env.get(GRADLE_HOME);
		if (gradleHome == null || !new File(gradleHome).isDirectory()) {
			gradleHome = sysprops.getProperty(GRADLE_HOME);
		}
		if (gradleHome != null) {
			File gradleHomeFile = new File(gradleHome);
			if (gradleHomeFile.isDirectory()) {
				return gradleHomeFile;
			}
		}
		return null;
	}

	protected void startSynchronization(Path rootFolder, IProgressMonitor monitor) {
		File location = rootFolder.toFile();
		boolean shouldSynchronize = shouldSynchronize(location);
		List<IProject> projects = ProjectUtils.getGradleProjects();
		for (IProject project : projects) {
			File projectDir = project.getLocation() == null ? null : project.getLocation().toFile();
			if (location.equals(projectDir)) {
				//
				shouldSynchronize = checkGradlePersistence(shouldSynchronize, project, projectDir);
				break;
			}
		}
		if (shouldSynchronize) {
			File gradleUserHome = getGradleHomeFile();
			boolean overrideWorkspaceConfiguration = gradleUserHome != null;
			GradleDistribution distribution = getGradleDistribution(rootFolder);
			String javaHomeStr = JavaLanguageServerPlugin.getPreferencesManager().getPreferences().getJavaHome();
			File javaHome = javaHomeStr == null ? null : new File(javaHomeStr);
			// @formatter:off
			BuildConfiguration build = BuildConfiguration.forRootProjectDirectory(location)
					.overrideWorkspaceConfiguration(overrideWorkspaceConfiguration)
					.gradleDistribution(distribution)
					.javaHome(javaHome)
					.gradleUserHome(gradleUserHome)
					.build();
			// @formatter:on
			SynchronizationResult result = GradleCore.getWorkspace().createBuild(build).synchronize(monitor);
			if (!result.getStatus().isOK()) {
				JavaLanguageServerPlugin.log(result.getStatus());
			}
		}
	}

	public static boolean shouldSynchronize(File location) {
		boolean shouldSynchronize = true;
		List<IProject> projects = ProjectUtils.getGradleProjects();
		for (IProject project : projects) {
			File projectDir = project.getLocation() == null ? null : project.getLocation().toFile();
			if (location.equals(projectDir)) {
				shouldSynchronize = checkGradlePersistence(shouldSynchronize, project, projectDir);
				break;
			}
		}
		return shouldSynchronize;
	}

	private static boolean checkGradlePersistence(boolean shouldSynchronize, IProject project, File projectDir) {
		PersistentModel model = CorePlugin.modelPersistence().loadModel(project);
		if (model.isPresent()) {
			File persistentFile = CorePlugin.getInstance().getStateLocation().append("project-preferences").append(project.getName()).toFile();
			if (persistentFile.exists()) {
				long modified = persistentFile.lastModified();
				if (projectDir.exists()) {
					File[] files = projectDir.listFiles(new FilenameFilter() {

						@Override
						public boolean accept(File dir, String name) {
							if (name != null && name.endsWith(GradleBuildSupport.GRADLE_SUFFIX)) {
								return new File(dir, name).lastModified() > modified;
							}
							return false;
						}
					});
					shouldSynchronize = files != null && files.length > 0;
				}
			}
		}
		return shouldSynchronize;
	}

	@Override
	public void reset() {
	}

}
