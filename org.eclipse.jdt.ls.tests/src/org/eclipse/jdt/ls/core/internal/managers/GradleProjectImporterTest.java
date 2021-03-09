/*******************************************************************************
 * Copyright (c) 2016-2020 Red Hat Inc. and others.
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

import static org.eclipse.jdt.ls.core.internal.ProjectUtils.getJavaSourceLevel;
import static org.eclipse.jdt.ls.core.internal.WorkspaceHelper.getProject;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import org.eclipse.buildship.core.BuildConfiguration;
import org.eclipse.buildship.core.FixedVersionGradleDistribution;
import org.eclipse.buildship.core.GradleDistribution;
import org.eclipse.buildship.core.LocalGradleDistribution;
import org.eclipse.buildship.core.WrapperGradleDistribution;
import org.eclipse.buildship.core.internal.CorePlugin;
import org.eclipse.buildship.core.internal.configuration.ProjectConfiguration;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IWorkspaceRoot;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.internal.launching.StandardVMType;
import org.eclipse.jdt.launching.IVMInstall;
import org.eclipse.jdt.launching.IVMInstallType;
import org.eclipse.jdt.launching.JavaRuntime;
import org.eclipse.jdt.ls.core.internal.JVMConfigurator;
import org.eclipse.jdt.ls.core.internal.JavaLanguageServerPlugin;
import org.eclipse.jdt.ls.core.internal.JobHelpers;
import org.eclipse.jdt.ls.core.internal.ProjectUtils;
import org.eclipse.jdt.ls.core.internal.TestVMType;
import org.eclipse.jdt.ls.core.internal.WorkspaceHelper;
import org.eclipse.jdt.ls.core.internal.managers.ProjectsManager.CHANGE_TYPE;
import org.eclipse.jdt.ls.core.internal.preferences.Preferences;
import org.eclipse.jdt.ls.core.internal.preferences.Preferences.FeatureStatus;
import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.runners.MockitoJUnitRunner;

import com.google.common.collect.ImmutableList;
import com.google.common.io.Files;

/**
 * @author Fred Bricon
 *
 */
@RunWith(MockitoJUnitRunner.class)
public class GradleProjectImporterTest extends AbstractGradleBasedTest{

	private static final String GRADLE1_PATTERN = "**/gradle1";

	@Test
	public void importSimpleGradleProject() throws Exception {
		importSimpleJavaProject();
		assertTaskCompleted(GradleProjectImporter.IMPORTING_GRADLE_PROJECTS);
	}

	/* (non-Javadoc)
	 * @see org.eclipse.jdt.ls.core.internal.managers.AbstractProjectsManagerBasedTest#cleanUp()
	 */
	@Override
	@After
	public void cleanUp() throws Exception {
		super.cleanUp();
		Job.getJobManager().join(CorePlugin.GRADLE_JOB_FAMILY, new NullProgressMonitor());
	}

	@Test
	public void importNestedGradleProject() throws Exception {
		List<IProject> projects = importProjects("gradle/nested");
		assertEquals(4, projects.size());//default + 3 gradle projects
		IProject gradle1 = WorkspaceHelper.getProject("gradle1");
		assertIsGradleProject(gradle1);
		IProject gradle2 = WorkspaceHelper.getProject("gradle2");
		assertIsGradleProject(gradle2);
		IProject gradle3 = WorkspaceHelper.getProject("gradle3");
		assertIsGradleProject(gradle3);
		assertFalse(ProjectUtils.isJavaProject(gradle3));
	}

	@Test
	public void testDeleteInvalidProjects() throws Exception {
		List<IProject> projects = importProjects(Arrays.asList("gradle/nested/gradle1", "gradle/nested/gradle2"));
		assertEquals(3, projects.size());//default + 2 gradle projects
		IProject gradle1 = WorkspaceHelper.getProject("gradle1");
		assertIsGradleProject(gradle1);
		IProject gradle2 = WorkspaceHelper.getProject("gradle2");
		assertIsGradleProject(gradle2);

		projects = importProjects("gradle/nested/gradle1");
		assertEquals(2, projects.size());
		gradle1 = WorkspaceHelper.getProject("gradle1");
		assertNotNull(gradle1);
		gradle2 = WorkspaceHelper.getProject("gradle2");
		assertNull(gradle2);
	}

	@Test
	public void testJavaImportExclusions() throws Exception {
		List<String> javaImportExclusions = JavaLanguageServerPlugin.getPreferencesManager().getPreferences().getJavaImportExclusions();
		try {
			javaImportExclusions.add(GRADLE1_PATTERN);
			List<IProject> projects = importProjects("gradle/nested");
			assertEquals(3, projects.size());//default + 2 gradle projects
			IProject gradle1 = WorkspaceHelper.getProject("gradle1");
			assertNull(gradle1);
			IProject gradle2 = WorkspaceHelper.getProject("gradle2");
			assertIsGradleProject(gradle2);
			IProject gradle3 = WorkspaceHelper.getProject("gradle3");
			assertIsGradleProject(gradle3);
		} finally {
			javaImportExclusions.remove(GRADLE1_PATTERN);
		}
	}

	@Test
	public void testDisableGradleWrapper() throws Exception {
		boolean enabled = JavaLanguageServerPlugin.getPreferencesManager().getPreferences().isGradleWrapperEnabled();
		String gradleVersion = JavaLanguageServerPlugin.getPreferencesManager().getPreferences().getGradleVersion();
		File file = new File(getSourceProjectDirectory(), "gradle/simple-gradle");
		assertTrue(file.isDirectory());
		try {
			GradleDistribution distribution = GradleProjectImporter.getGradleDistribution(file.toPath());
			assertTrue(distribution instanceof WrapperGradleDistribution);
			JavaLanguageServerPlugin.getPreferencesManager().getPreferences().setGradleWrapperEnabled(false);
			distribution = GradleProjectImporter.getGradleDistribution(file.toPath());
			if (GradleProjectImporter.getGradleHomeFile() != null) {
				assertEquals(distribution.getClass(), LocalGradleDistribution.class);
			} else {
				assertSame(distribution, GradleProjectImporter.DEFAULT_DISTRIBUTION);
			}
			String requiredVersion = "5.2.1";
			JavaLanguageServerPlugin.getPreferencesManager().getPreferences().setGradleVersion(requiredVersion);
			distribution = GradleProjectImporter.getGradleDistribution(file.toPath());
			assertEquals(distribution.getClass(), FixedVersionGradleDistribution.class);
			assertEquals(((FixedVersionGradleDistribution) distribution).getVersion(), requiredVersion);
			List<IProject> projects = importProjects("eclipse/eclipsegradle");
			assertEquals(2, projects.size());//default + 1 eclipse projects
			IProject eclipse = WorkspaceHelper.getProject("eclipsegradle");
			assertNotNull(eclipse);
			assertTrue(eclipse.getName() + " does not have the Gradle nature", ProjectUtils.isGradleProject(eclipse));
		} finally {
			JavaLanguageServerPlugin.getPreferencesManager().getPreferences().setGradleWrapperEnabled(enabled);
			JavaLanguageServerPlugin.getPreferencesManager().getPreferences().setGradleVersion(gradleVersion);
		}
	}

	@Test
	public void testGradleUserHome() throws Exception {
		String gradleUserHomePreference = JavaLanguageServerPlugin.getPreferencesManager().getPreferences().getGradleUserHome();
		File gradleUserHome = null;
		try {
			gradleUserHome = Files.createTempDir();
			gradleUserHome.deleteOnExit();
			JavaLanguageServerPlugin.getPreferencesManager().getPreferences().setGradleUserHome(gradleUserHome.getAbsolutePath());
			List<IProject> projects = importProjects("gradle/simple-gradle");
			assertEquals(2, projects.size());//default + 1 eclipse projects
			IProject project = WorkspaceHelper.getProject("simple-gradle");
			assertNotNull(project);
			assertTrue(project.getName() + " does not have the Gradle nature", ProjectUtils.isGradleProject(project));
			assertTrue(gradleUserHome.exists());
			ProjectConfiguration projectConfiguration = CorePlugin.configurationManager().loadProjectConfiguration(project);
			assertEquals(gradleUserHome, projectConfiguration.getBuildConfiguration().getGradleUserHome());
		} finally {
			JavaLanguageServerPlugin.getPreferencesManager().getPreferences().setGradleUserHome(gradleUserHomePreference);
		}
	}

	@Test
	public void testJavaHome() throws Exception {
		Preferences prefs = JavaLanguageServerPlugin.getPreferencesManager().getPreferences();
		String javaHomePreference = prefs.getJavaHome();
		IVMInstall defaultVM = JavaRuntime.getDefaultVMInstall();
		try {
			IVMInstallType installType = JavaRuntime.getVMInstallType(StandardVMType.ID_STANDARD_VM_TYPE);
			if (installType == null || installType.getVMInstalls().length == 0) {
				// https://github.com/eclipse/eclipse.jdt.ls/issues/1646
				installType = JavaRuntime.getVMInstallType(JVMConfigurator.MAC_OSX_VM_TYPE);
			}
			IVMInstall[] vms = installType.getVMInstalls();
			IVMInstall vm = vms[0];
			JavaRuntime.setDefaultVMInstall(vm, new NullProgressMonitor());
			String javaHome = new File(TestVMType.getFakeJDKsLocation(), "11").getAbsolutePath();
			prefs.setJavaHome(javaHome);
			IWorkspaceRoot root = ResourcesPlugin.getWorkspace().getRoot();
			IPath rootFolder = root.getLocation().append("/projects/gradle/simple-gradle");
			BuildConfiguration build = GradleProjectImporter.getBuildConfiguration(rootFolder.toFile().toPath());
			assertEquals(vm.getInstallLocation().getAbsolutePath(), build.getJavaHome().get().getAbsolutePath());
		} finally {
			prefs.setJavaHome(javaHomePreference);
			if (defaultVM != null) {
				JavaRuntime.setDefaultVMInstall(defaultVM, new NullProgressMonitor());
			}
		}
	}

	@Test
	public void testGradleJavaHome() throws Exception {
		Preferences prefs = JavaLanguageServerPlugin.getPreferencesManager().getPreferences();
		String gradleJavaHomePreference = prefs.getGradleJavaHome();
		IVMInstall defaultVM = JavaRuntime.getDefaultVMInstall();
		try {
			IVMInstallType installType = JavaRuntime.getVMInstallType(StandardVMType.ID_STANDARD_VM_TYPE);
			if (installType == null || installType.getVMInstalls().length == 0) {
				// https://github.com/eclipse/eclipse.jdt.ls/issues/1646
				installType = JavaRuntime.getVMInstallType(JVMConfigurator.MAC_OSX_VM_TYPE);
			}
			IVMInstall[] vms = installType.getVMInstalls();
			IVMInstall vm = vms[0];
			JavaRuntime.setDefaultVMInstall(vm, new NullProgressMonitor());
			String javaHome = new File(TestVMType.getFakeJDKsLocation(), "1.8").getAbsolutePath();
			prefs.setGradleJavaHome(javaHome);
			IWorkspaceRoot root = ResourcesPlugin.getWorkspace().getRoot();
			IPath rootFolder = root.getLocation().append("/projects/gradle/simple-gradle");
			BuildConfiguration build = GradleProjectImporter.getBuildConfiguration(rootFolder.toFile().toPath());
			assertEquals(javaHome, build.getJavaHome().get().getAbsolutePath());
		} finally {
			prefs.setJavaHome(gradleJavaHomePreference);
			if (defaultVM != null) {
				JavaRuntime.setDefaultVMInstall(defaultVM, new NullProgressMonitor());
			}
		}
	}

	@Test
	public void testDisableImportGradle() throws Exception {
		boolean enabled = JavaLanguageServerPlugin.getPreferencesManager().getPreferences().isImportGradleEnabled();
		try {
			JavaLanguageServerPlugin.getPreferencesManager().getPreferences().setImportGradleEnabled(false);
			List<IProject> projects = importProjects("eclipse/eclipsegradle");
			assertEquals(2, projects.size());//default + 1 eclipse projects
			IProject eclipse = WorkspaceHelper.getProject("eclipse");
			assertNotNull(eclipse);
			assertFalse(eclipse.getName() + " has the Gradle nature", ProjectUtils.isGradleProject(eclipse));
		} finally {
			JavaLanguageServerPlugin.getPreferencesManager().getPreferences().setImportGradleEnabled(enabled);
		}
	}

	@Test
	public void testGradlePersistence() throws Exception {
		importProjects("gradle/nested");
		List<IProject> projects = ProjectUtils.getGradleProjects();
		for (IProject project : projects) {
			assertTrue(project.getName() + " should synchronize", GradleProjectImporter.shouldSynchronize(project.getLocation().toFile()));
		}
		Job.getJobManager().join(CorePlugin.GRADLE_JOB_FAMILY, new NullProgressMonitor());
		GradleBuildSupport.saveModels();
		for (IProject project : projects) {
			assertFalse(project.getName() + " should not synchronize", GradleProjectImporter.shouldSynchronize(project.getLocation().toFile()));
		}
		IProject project = WorkspaceHelper.getProject("gradle1");
		File gradleBuild = new File(project.getLocation().toFile(), "build.gradle");
		gradleBuild.setLastModified(System.currentTimeMillis() + 1000);
		assertTrue(GradleProjectImporter.shouldSynchronize(project.getLocation().toFile()));
	}

	@Test
	public void testWorkspaceSettings() throws Exception {
		Map<String, String> env = new HashMap<>();
		Properties sysprops = new Properties();
		File file = null;
		try {
			IWorkspaceRoot root = ResourcesPlugin.getWorkspace().getRoot();
			File rootFile = root.getLocation().toFile();
			file = new File(rootFile, "fakeGradleHome");
			sysprops.setProperty(GradleProjectImporter.GRADLE_HOME, file.getAbsolutePath());
			boolean overrideWorkspaceSettings = GradleProjectImporter.getGradleHomeFile(env, sysprops) != null;
			assertFalse(overrideWorkspaceSettings);
			file.mkdir();
			file.deleteOnExit();
			overrideWorkspaceSettings = GradleProjectImporter.getGradleHomeFile(env, sysprops) != null;
			assertTrue(overrideWorkspaceSettings);
		} finally {
			if (file != null) {
				file.delete();
			}
		}
	}

	@Test
	public void testGradleHome() {
		Map<String, String> env = new HashMap<>();
		Properties sysprops = new Properties();
		File file = null;
		File projectFile = null;
		try {
			IWorkspaceRoot root = ResourcesPlugin.getWorkspace().getRoot();
			File rootFile = root.getLocation().toFile();
			file = new File(rootFile, "fakeGradleHome");
			sysprops.setProperty(GradleProjectImporter.GRADLE_HOME, file.getAbsolutePath());
			boolean overrideWorkspaceSettings = GradleProjectImporter.getGradleHomeFile(env, sysprops) != null;
			assertFalse(overrideWorkspaceSettings);
			file.mkdir();
			file.deleteOnExit();
			overrideWorkspaceSettings = GradleProjectImporter.getGradleHomeFile(env, sysprops) != null;
			assertTrue(overrideWorkspaceSettings);
			projectFile = new File(rootFile, "fakeProject");
			projectFile.mkdir();
			projectFile.deleteOnExit();
			BuildConfiguration build = GradleProjectImporter.getBuildConfiguration(file.toPath());
			assertFalse(build.getGradleUserHome().isPresent());
		} finally {
			if (file != null) {
				file.delete();
			}
			if (projectFile != null) {
				projectFile.delete();
			}
		}
	}

	@Test
	public void testBuildFile() throws Exception {
		IProject project = importSimpleJavaProject();
		IFile file = project.getFile("/target-default/build.gradle");
		assertFalse(projectsManager.isBuildFile(file));
		importProjects("gradle/gradle-withoutjava");
		project = getProject("gradle-withoutjava");
		file = project.getFile("/build.gradle");
		assertTrue(projectsManager.isBuildFile(file));
	}

	@Test
	public void testGradlePropertiesFile() throws Exception {
		IProject project = importSimpleJavaProject();
		IFile file = project.getFile("/target-default/gradle.properties");
		assertFalse(projectsManager.isBuildFile(file));
		importProjects("gradle/gradle-withoutjava");
		project = getProject("gradle-withoutjava");
		file = project.getFile("/gradle.properties");
		assertTrue(projectsManager.isBuildFile(file));
	}

	@Test
	public void testGradleHomePreference() {
		String home = JavaLanguageServerPlugin.getPreferencesManager().getPreferences().getGradleHome();
		Map<String, String> env = new HashMap<>();
		Properties sysprops = new Properties();
		try {
			JavaLanguageServerPlugin.getPreferencesManager().getPreferences().setGradleHome(null);
			assertNull(GradleProjectImporter.getGradleHomeFile(env, sysprops));

			JavaLanguageServerPlugin.getPreferencesManager().getPreferences().setGradleHome("/gradle/home");
			assertEquals(new File("/gradle/home"), GradleProjectImporter.getGradleHomeFile(env, sysprops));
		} finally {
			JavaLanguageServerPlugin.getPreferencesManager().getPreferences().setGradleHome(home);
		}
	}

	@Test
	public void testGradleArguments() {
		List<String> arguments = JavaLanguageServerPlugin.getPreferencesManager().getPreferences().getGradleArguments();
		try {
			Path rootPath = ResourcesPlugin.getWorkspace().getRoot().getLocation().toFile().toPath();
			BuildConfiguration build = GradleProjectImporter.getBuildConfiguration(rootPath);
			assertTrue(build.getArguments().isEmpty());

			JavaLanguageServerPlugin.getPreferencesManager().getPreferences()
					.setGradleArguments(ImmutableList.of("-Pproperty=value", "--stacktrace"));
			build = GradleProjectImporter.getBuildConfiguration(rootPath);
			assertEquals(2, build.getArguments().size());
			assertTrue(build.getArguments().contains("-Pproperty=value"));
			assertTrue(build.getArguments().contains("--stacktrace"));
		} finally {
			JavaLanguageServerPlugin.getPreferencesManager().getPreferences().setGradleArguments(arguments);
		}
	}

	@Test
	public void testGradleOfflineMode() {
		boolean offlineMode = JavaLanguageServerPlugin.getPreferencesManager().getPreferences().isImportGradleOfflineEnabled();
		try {
			Path rootPath = ResourcesPlugin.getWorkspace().getRoot().getLocation().toFile().toPath();
			BuildConfiguration build = GradleProjectImporter.getBuildConfiguration(rootPath);
			assertFalse(build.isOfflineMode());
			JavaLanguageServerPlugin.getPreferencesManager().getPreferences().setImportGradleOfflineEnabled(true);
			build = GradleProjectImporter.getBuildConfiguration(rootPath);
			assertTrue(build.isOfflineMode());
		} finally {
			JavaLanguageServerPlugin.getPreferencesManager().getPreferences().setImportGradleOfflineEnabled(offlineMode);
		}
	}

	@Test
	public void testGradleJvmArguments() {
		List<String> jvmArguments = JavaLanguageServerPlugin.getPreferencesManager().getPreferences().getGradleJvmArguments();
		try {
			Path rootPath = ResourcesPlugin.getWorkspace().getRoot().getLocation().toFile().toPath();
			BuildConfiguration build = GradleProjectImporter.getBuildConfiguration(rootPath);
			assertTrue(build.getJvmArguments().isEmpty());

			JavaLanguageServerPlugin.getPreferencesManager().getPreferences()
					.setGradleJvmArguments(ImmutableList.of("-Djavax.net.ssl.trustStore=truststore.jks"));
			build = GradleProjectImporter.getBuildConfiguration(rootPath);
			assertEquals(1, build.getJvmArguments().size());
			assertTrue(build.getJvmArguments().contains("-Djavax.net.ssl.trustStore=truststore.jks"));
		} finally {
			JavaLanguageServerPlugin.getPreferencesManager().getPreferences().setGradleJvmArguments(jvmArguments);
		}
	}

	@Test
	public void testDeleteClasspath() throws Exception {
		FeatureStatus status = preferenceManager.getPreferences().getUpdateBuildConfigurationStatus();
		try {
			preferenceManager.getPreferences().setUpdateBuildConfigurationStatus(FeatureStatus.automatic);
			IProject project = importSimpleJavaProject();
			assertIsJavaProject(project);
			assertIsGradleProject(project);
			IFile dotClasspath = project.getFile(IJavaProject.CLASSPATH_FILE_NAME);
			File file = dotClasspath.getRawLocation().toFile();
			assertTrue(file.exists());
			file.delete();
			projectsManager.fileChanged(file.toPath().toUri().toString(), CHANGE_TYPE.DELETED);
			waitForBackgroundJobs();
			Job.getJobManager().join(CorePlugin.GRADLE_JOB_FAMILY, new NullProgressMonitor());
			project = getProject("simple-gradle");
			assertIsGradleProject(project);
			assertIsJavaProject(project);
			IFile bin = project.getFile("bin");
			assertFalse(bin.getRawLocation().toFile().exists());
			assertTrue(dotClasspath.exists());
		} finally {
			preferenceManager.getPreferences().setUpdateBuildConfigurationStatus(status);
		}
	}

	@Test
	public void testJava11Project() throws Exception {
		IProject project = importGradleProject("gradle-11");
		assertIsJavaProject(project);
		assertEquals("11", getJavaSourceLevel(project));
		assertNoErrors(project);
	}

	@Test
	public void testJava12Project() throws Exception {
		testJavaProjectWithPreviewFeatures("12", false, JavaCore.WARNING);
	}

	@Test
	public void testJava13Project() throws Exception {
		testJavaProjectWithPreviewFeatures("13", true /* The project has enabled preview features in the jdt setting*/, JavaCore.IGNORE);
	}

	@Test
	public void testJava14Project() throws Exception {
		testJavaProjectWithPreviewFeatures("14", true /* The project has enabled preview features in the jdt setting*/, JavaCore.IGNORE);
	}

	@Test
	public void testSubprojects() throws Exception {
		List<String> arguments = JavaLanguageServerPlugin.getPreferencesManager().getPreferences().getGradleArguments();
		try {
			// force overrideWorkspace
			JavaLanguageServerPlugin.getPreferencesManager().getPreferences().setGradleArguments(ImmutableList.of("--stacktrace"));
			List<IProject> projects = importProjects("gradle/subprojects");
			assertEquals(4, projects.size());//default + 3 gradle projects
			IProject root = WorkspaceHelper.getProject("subprojects");
			assertIsGradleProject(root);
			IProject project1 = WorkspaceHelper.getProject("project1");
			assertIsGradleProject(project1);
			IProject project2 = WorkspaceHelper.getProject("project2");
			assertIsGradleProject(project2);
			projectsManager.updateProject(root, true);
			projectsManager.updateProject(project1, true);
			projectsManager.updateProject(project2, true);
			waitForBackgroundJobs();
			ProjectConfiguration configuration = getProjectConfiguration(root);
			// check the children .settings/org.eclipse.buildship.core.prefs
			assertTrue(configuration.getBuildConfiguration().isOverrideWorkspaceSettings());
			configuration = getProjectConfiguration(project1);
			assertFalse(configuration.getBuildConfiguration().isOverrideWorkspaceSettings());
			configuration = getProjectConfiguration(project2);
			assertFalse(configuration.getBuildConfiguration().isOverrideWorkspaceSettings());
			JavaLanguageServerPlugin.getPreferencesManager().getPreferences().setGradleArguments(ImmutableList.of("--stacktrace"));
			configuration = CorePlugin.configurationManager().loadProjectConfiguration(project1);
			assertTrue(configuration.getBuildConfiguration().isOverrideWorkspaceSettings());
			configuration = CorePlugin.configurationManager().loadProjectConfiguration(project2);
			assertTrue(configuration.getBuildConfiguration().isOverrideWorkspaceSettings());
			JavaLanguageServerPlugin.getPreferencesManager().getPreferences().setGradleArguments(arguments);
			projectsManager.updateProject(root, true);
			JobHelpers.waitForJobsToComplete();
			Job.getJobManager().join(CorePlugin.GRADLE_JOB_FAMILY, new NullProgressMonitor());
			configuration = CorePlugin.configurationManager().loadProjectConfiguration(root);
			assertFalse(configuration.getBuildConfiguration().isOverrideWorkspaceSettings());
			// check that the children are updated
			configuration = CorePlugin.configurationManager().loadProjectConfiguration(project1);
			assertFalse(configuration.getBuildConfiguration().isOverrideWorkspaceSettings());
			configuration = CorePlugin.configurationManager().loadProjectConfiguration(project2);
			assertFalse(configuration.getBuildConfiguration().isOverrideWorkspaceSettings());
		} finally {
			JavaLanguageServerPlugin.getPreferencesManager().getPreferences().setGradleArguments(arguments);
		}
	}

	@Test
	public void testSettingsGradle() throws Exception {
			List<IProject> projects = importProjects("gradle/sample");
			assertEquals(3, projects.size());//default, app, sample
			IProject root = WorkspaceHelper.getProject("sample");
			assertIsGradleProject(root);
			IProject project = WorkspaceHelper.getProject("app");
			assertIsGradleProject(project);
			assertIsJavaProject(project);
			IJavaProject javaProject = JavaCore.create(project);
			IType type = javaProject.findType("org.apache.commons.lang3.StringUtils");
			assertNull(type);
			IFile build2 = project.getFile("/build.gradle2");
			InputStream contents = build2.getContents();
			IFile build = project.getFile("/build.gradle");
			build.setContents(contents, true, false, null);
			projectsManager.updateProject(project, false);
			waitForBackgroundJobs();
			type = javaProject.findType("org.apache.commons.lang3.StringUtils");
			assertNotNull(type);
	}

	private ProjectConfiguration getProjectConfiguration(IProject project) {
		org.eclipse.buildship.core.internal.configuration.BuildConfiguration buildConfig = CorePlugin.configurationManager().loadBuildConfiguration(project.getLocation().toFile());
		return CorePlugin.configurationManager().createProjectConfiguration(buildConfig, project.getLocation().toFile());
	}

	private void testJavaProjectWithPreviewFeatures(String javaVersion, boolean enabled, String severity) throws Exception {
		IProject project = importGradleProject("gradle-" + javaVersion);
		assertIsJavaProject(project);
		assertEquals(javaVersion, getJavaSourceLevel(project));
		IJavaProject javaProject = JavaCore.create(project);
		assertEquals((enabled) ? JavaCore.ENABLED : JavaCore.DISABLED, javaProject.getOption(JavaCore.COMPILER_PB_ENABLE_PREVIEW_FEATURES, true));
		assertEquals(severity, javaProject.getOption(JavaCore.COMPILER_PB_REPORT_PREVIEW_FEATURES, true));
	}
}
