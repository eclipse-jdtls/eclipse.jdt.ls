/*******************************************************************************
 * Copyright (c) 2016-2022 Red Hat Inc. and others.
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
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;

import org.eclipse.buildship.core.BuildConfiguration;
import org.eclipse.buildship.core.FixedVersionGradleDistribution;
import org.eclipse.buildship.core.GradleDistribution;
import org.eclipse.buildship.core.LocalGradleDistribution;
import org.eclipse.buildship.core.WrapperGradleDistribution;
import org.eclipse.buildship.core.internal.CorePlugin;
import org.eclipse.buildship.core.internal.configuration.ProjectConfiguration;
import org.eclipse.core.resources.ICommand;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IWorkspaceRoot;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.jdt.apt.core.util.AptConfig;
import org.eclipse.jdt.core.IClasspathEntry;
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
import org.eclipse.jdt.ls.core.internal.ResourceUtils;
import org.eclipse.jdt.ls.core.internal.TestVMType;
import org.eclipse.jdt.ls.core.internal.WorkspaceHelper;
import org.eclipse.jdt.ls.core.internal.preferences.Preferences;
import org.eclipse.jdt.ls.core.internal.preferences.Preferences.FeatureStatus;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * @author Fred Bricon
 *
 */
@ExtendWith(MockitoExtension.class)
public class GradleProjectImporterTest extends AbstractGradleBasedTest{

	private static final String GRADLE1_PATTERN = "**/gradle1";
	private String gradleJavaHome;

	@BeforeEach
	public void setUp() {
		gradleJavaHome = JavaLanguageServerPlugin.getPreferencesManager().getPreferences().getGradleJavaHome();
	}

	@Test
	public void importSimpleGradleProject() throws Exception {
		importSimpleJavaProject();
		assertTaskCompleted(GradleProjectImporter.IMPORTING_GRADLE_PROJECTS);
	}

	/* (non-Javadoc)
	 * @see org.eclipse.jdt.ls.core.internal.managers.AbstractProjectsManagerBasedTest#cleanUp()
	 */
	@Override
	@AfterEach
	public void cleanUp() throws Exception {
		super.cleanUp();
		Job.getJobManager().join(CorePlugin.GRADLE_JOB_FAMILY, new NullProgressMonitor());
		JavaLanguageServerPlugin.getPreferencesManager().getPreferences().setGradleJavaHome(gradleJavaHome);
	}

	@Test
	public void importNestedGradleProject() throws Exception {
		List<IProject> projects = importProjects("gradle/nested");
		assertEquals(3, projects.size()); // 3 gradle projects
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
		assertEquals(2, projects.size()); // 2 gradle projects
		IProject gradle1 = WorkspaceHelper.getProject("gradle1");
		assertIsGradleProject(gradle1);
		IProject gradle2 = WorkspaceHelper.getProject("gradle2");
		assertIsGradleProject(gradle2);

		projects = importProjects("gradle/nested/gradle1");
		assertEquals(1, projects.size());
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
			assertEquals(2, projects.size()); // 2 gradle projects
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
			String requiredVersion = "8.5";
			JavaLanguageServerPlugin.getPreferencesManager().getPreferences().setGradleVersion(requiredVersion);
			distribution = GradleProjectImporter.getGradleDistribution(file.toPath());
			assertEquals(distribution.getClass(), FixedVersionGradleDistribution.class);
			assertEquals(((FixedVersionGradleDistribution) distribution).getVersion(), requiredVersion);
			List<IProject> projects = importProjects("eclipse/eclipsegradle");
			assertEquals(1, projects.size()); // 1 eclipse project
			IProject eclipse = WorkspaceHelper.getProject("eclipsegradle");
			assertNotNull(eclipse);
			assertTrue(ProjectUtils.isGradleProject(eclipse), eclipse.getName() + " does not have the Gradle nature");
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
			gradleUserHome = Files.createTempDirectory("gradleUserHome").toFile();
			gradleUserHome.deleteOnExit();
			JavaLanguageServerPlugin.getPreferencesManager().getPreferences().setGradleUserHome(gradleUserHome.getAbsolutePath());
			List<IProject> projects = importProjects("gradle/simple-gradle");
			assertEquals(1, projects.size()); // 1 eclipse project
			IProject project = WorkspaceHelper.getProject("simple-gradle");
			assertNotNull(project);
			assertTrue(ProjectUtils.isGradleProject(project), project.getName() + " does not have the Gradle nature");
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
			assertEquals(1, projects.size()); // 1 eclipse projects
			IProject eclipse = WorkspaceHelper.getProject("eclipse");
			assertNotNull(eclipse);
			assertFalse(ProjectUtils.isGradleProject(eclipse), eclipse.getName() + " has the Gradle nature");
		} finally {
			JavaLanguageServerPlugin.getPreferencesManager().getPreferences().setImportGradleEnabled(enabled);
		}
	}

	@Test
	public void testGradlePersistence() throws Exception {
		importProjects("gradle/nested");
		List<IProject> projects = ProjectUtils.getGradleProjects();
		for (IProject project : projects) {
			assertTrue(GradleProjectImporter.shouldSynchronize(project.getLocation().toFile()), project.getName() + " should synchronize");
		}
		Job.getJobManager().join(CorePlugin.GRADLE_JOB_FAMILY, new NullProgressMonitor());
		GradleBuildSupport.saveModels();
		for (IProject project : projects) {
			assertFalse(GradleProjectImporter.shouldSynchronize(project.getLocation().toFile()), project.getName() + " should not synchronize");
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
			assertFalse(build.getArguments().isEmpty());
			assertEquals(2, build.getArguments().size());
			assertTrue(build.getArguments().contains("--init-script"));

			JavaLanguageServerPlugin.getPreferencesManager().getPreferences()
					.setGradleArguments(List.of("-Pproperty=value", "--stacktrace"));
			build = GradleProjectImporter.getBuildConfiguration(rootPath);
			assertEquals(4, build.getArguments().size());
			assertTrue(build.getArguments().contains("-Pproperty=value"));
			assertTrue(build.getArguments().contains("--stacktrace"));
			assertTrue(build.getArguments().contains("--init-script"));
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
	public void testGradleAutoSync() {
		FeatureStatus status = JavaLanguageServerPlugin.getPreferencesManager().getPreferences().getUpdateBuildConfigurationStatus();
		try {
			Path rootPath = ResourcesPlugin.getWorkspace().getRoot().getLocation().toFile().toPath();
			BuildConfiguration build = GradleProjectImporter.getBuildConfiguration(rootPath);
			assertFalse(build.isAutoSync());
			JavaLanguageServerPlugin.getPreferencesManager().getPreferences().setUpdateBuildConfigurationStatus(FeatureStatus.automatic);
			build = GradleProjectImporter.getBuildConfiguration(rootPath);
			assertTrue(build.isAutoSync());
		} finally {
			JavaLanguageServerPlugin.getPreferencesManager().getPreferences().setUpdateBuildConfigurationStatus(status);
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
					.setGradleJvmArguments(List.of("-Djavax.net.ssl.trustStore=truststore.jks"));
			build = GradleProjectImporter.getBuildConfiguration(rootPath);
			assertEquals(1, build.getJvmArguments().size());
			assertTrue(build.getJvmArguments().contains("-Djavax.net.ssl.trustStore=truststore.jks"));
		} finally {
			JavaLanguageServerPlugin.getPreferencesManager().getPreferences().setGradleJvmArguments(jvmArguments);
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
			JavaLanguageServerPlugin.getPreferencesManager().getPreferences().setGradleArguments(List.of("--stacktrace"));
			List<IProject> projects = importProjects("gradle/subprojects");
			assertEquals(3, projects.size()); // 3 gradle projects
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
			assertEquals(3, configuration.getBuildConfiguration().getArguments().size());
			configuration = getProjectConfiguration(project1);
			assertFalse(configuration.getBuildConfiguration().isOverrideWorkspaceSettings());
			configuration = getProjectConfiguration(project2);
			assertFalse(configuration.getBuildConfiguration().isOverrideWorkspaceSettings());
			JavaLanguageServerPlugin.getPreferencesManager().getPreferences().setGradleArguments(List.of("--stacktrace"));
			configuration = CorePlugin.configurationManager().loadProjectConfiguration(project1);
			assertTrue(configuration.getBuildConfiguration().isOverrideWorkspaceSettings());
			assertEquals(3, configuration.getBuildConfiguration().getArguments().size());
			configuration = CorePlugin.configurationManager().loadProjectConfiguration(project2);
			assertTrue(configuration.getBuildConfiguration().isOverrideWorkspaceSettings());
			assertEquals(3, configuration.getBuildConfiguration().getArguments().size());
			JavaLanguageServerPlugin.getPreferencesManager().getPreferences().setGradleArguments(arguments);
			projectsManager.updateProject(root, true);
			JobHelpers.waitForJobsToComplete();
			Job.getJobManager().join(CorePlugin.GRADLE_JOB_FAMILY, new NullProgressMonitor());
			configuration = CorePlugin.configurationManager().loadProjectConfiguration(root);
			// the configuration contains two arguments about jdt.ls init script
			assertTrue(configuration.getBuildConfiguration().isOverrideWorkspaceSettings());
			assertEquals(2, configuration.getBuildConfiguration().getArguments().size());
			// check that the children are updated
			configuration = CorePlugin.configurationManager().loadProjectConfiguration(project1);
			assertTrue(configuration.getBuildConfiguration().isOverrideWorkspaceSettings());
			assertEquals(2, configuration.getBuildConfiguration().getArguments().size());
			configuration = CorePlugin.configurationManager().loadProjectConfiguration(project2);
			assertTrue(configuration.getBuildConfiguration().isOverrideWorkspaceSettings());
			assertEquals(2, configuration.getBuildConfiguration().getArguments().size());
		} finally {
			JavaLanguageServerPlugin.getPreferencesManager().getPreferences().setGradleArguments(arguments);
		}
	}

	@Test
	public void importGradleKtsProject() throws Exception {
		List<IProject> projects = importProjects("gradle/kradle");
		assertEquals(1, projects.size()); // gradle kts projects
		IProject kradle = WorkspaceHelper.getProject("kradle");
		assertIsGradleProject(kradle);
		assertNoErrors(kradle);
		IJavaProject javaProject = JavaCore.create(kradle);
		IType app = javaProject.findType("org.sample.App");
		assertTrue(app.exists());
		IType appTest = javaProject.findType("org.sample.AppTest");
		assertTrue(appTest.exists());
	}

	@Test
	public void avoidImportDuplicatedProjects() throws Exception {
		try {
			this.preferences.setImportGradleEnabled(false);
			importProjects("multi-buildtools");
			GradleProjectImporter importer = new GradleProjectImporter();
			File root = new File(getWorkingProjectDirectory(), "multi-buildtools");
			importer.initialize(root);

			Collection<IPath> configurationPaths = new ArrayList<>();
			configurationPaths.add(ResourceUtils.canonicalFilePathFromURI(root.toPath().resolve("build.gradle").toUri().toString()));
			this.preferences.setImportGradleEnabled(true);
			assertFalse(importer.applies(configurationPaths, null));
		} finally {
			this.preferences.setImportGradleEnabled(true);
		}
	}

	@Test
	public void avoidImportDuplicatedProjects2() throws Exception {
		try {
			this.preferences.setImportGradleEnabled(false);
			importProjects("multi-buildtools");
			IProject project = getProject("multi-build-tools");
			assertIsJavaProject(project);
			GradleProjectImporter importer = new GradleProjectImporter();
			importer.initialize(project.getLocation().toFile());

			this.preferences.setImportGradleEnabled(true);
			assertFalse(importer.applies(null));
		} finally {
			this.preferences.setImportGradleEnabled(true);
		}
	}

	@Test
	public void testProtoBufSupport() throws Exception {
		try {
			this.preferences.setProtobufSupportEnabled(true);
			IProject project = importGradleProject("protobuf");
			IJavaProject javaProject = JavaCore.create(project);
			IClasspathEntry[] classpathEntries = javaProject.getRawClasspath();
			assertTrue(Arrays.stream(classpathEntries).anyMatch(cpe -> {
				return "/protobuf/build/generated/source/proto/main/java".equals(cpe.getPath().toString());
			}));
			assertTrue(Arrays.stream(classpathEntries).anyMatch(cpe -> {
				return "/protobuf/build/generated/source/proto/test/java".equals(cpe.getPath().toString());
			}));
		} finally {
			this.preferences.setProtobufSupportEnabled(false);
		}
	}

	@Test
	public void testProtoBufSupportChanged() throws Exception {
		try {
			this.preferences.setProtobufSupportEnabled(true);
			IProject project = importGradleProject("protobuf");
			IJavaProject javaProject = JavaCore.create(project);
			IClasspathEntry[] classpathEntries = javaProject.getRawClasspath();
			assertEquals(5, classpathEntries.length);
			assertTrue(Arrays.stream(classpathEntries).anyMatch(cpe -> {
				return "/protobuf/build/generated/source/proto/main/java".equals(cpe.getPath().toString());
			}));
			assertTrue(Arrays.stream(classpathEntries).anyMatch(cpe -> {
				return "/protobuf/build/generated/source/proto/test/java".equals(cpe.getPath().toString());
			}));

			this.preferences.setProtobufSupportEnabled(false);
			projectsManager.updateProject(project, true);

			waitForBackgroundJobs();

			assertEquals(3, javaProject.getRawClasspath().length);
		} finally {
			this.preferences.setProtobufSupportEnabled(false);
		}
	}

	@Test
	public void testNameConflictProject() throws Exception {
		List<IProject> projects = importProjects("gradle/nameConflict");
		assertEquals(2, projects.size());
		IProject root = WorkspaceHelper.getProject("nameConflict");
		assertIsGradleProject(root);
		IProject subProject = WorkspaceHelper.getProject("nameConflict-nameconflict");
		assertIsGradleProject(subProject);
	}

	// https://github.com/eclipse-jdtls/eclipse.jdt.ls/issues/1743
	@Test
	public void testNameConflictProject2() throws Exception {
		List<IProject> projects = importProjects("gradle/nameconflict2");
		assertEquals(2, projects.size());
		IProject project1 = WorkspaceHelper.getProject("rest-service-initial");
		assertIsGradleProject(project1);
		IProject project2 = WorkspaceHelper.getProject("rest-service-complete");
		assertIsGradleProject(project2);
	}

	@Test
	public void testAndroidProjectSupport() throws Exception {
		try {
			this.preferences.setAndroidSupportEnabled(true);
			List<IProject> projects = importProjects("gradle/android");
			assertEquals(2, projects.size());
			IProject androidAppProject = WorkspaceHelper.getProject("app");
			assertNotNull(androidAppProject);
			IJavaProject javaProject = JavaCore.create(androidAppProject);
			IClasspathEntry[] classpathEntries = javaProject.getRawClasspath();
			if (!isAndroidSdkInstalled()) {
				// android SDK is not detected, plugin will do nothing
				assertEquals(2, classpathEntries.length);
			} else {
				// android SDK is detected, android project should be imported successfully
				assertEquals(6, classpathEntries.length);
				// main sourceSet are added to classpath correctly
				assertTrue(Arrays.stream(classpathEntries).anyMatch(cpe -> {
					return "/app/src/main/java".equals(cpe.getPath().toString());
				}));
				// test sourceSet are added to classpath correctly
				assertTrue(Arrays.stream(classpathEntries).anyMatch(cpe -> {
					return "/app/src/test/java".equals(cpe.getPath().toString());
				}));
				// androidTest sourceSet are added to classpath correctly
				assertTrue(Arrays.stream(classpathEntries).anyMatch(cpe -> {
					return "/app/src/androidTest/java".equals(cpe.getPath().toString());
				}));
				// buildConfig files are added to classpath correctly
				assertTrue(Arrays.stream(classpathEntries).anyMatch(cpe -> {
					return "/app/build/generated/source/buildConfig/standard/debug".equals(cpe.getPath().toString());
				}));
				// dataBinding files are added to classpath correctly
				assertTrue(Arrays.stream(classpathEntries).anyMatch(cpe -> {
					return "/app/build/generated/data_binding_base_class_source_out/standardDebug/out".equals(cpe.getPath().toString());
				}));
			}
		} finally {
			this.preferences.setAndroidSupportEnabled(false);
		}
	}

	@Test
	public void testAndroidProjectSupportChanged() throws Exception {
		try {
			this.preferences.setAndroidSupportEnabled(true);
			List<IProject> projects = importProjects("gradle/android");
			assertEquals(2, projects.size());
			IProject androidAppProject = WorkspaceHelper.getProject("app");
			assertNotNull(androidAppProject);
			IJavaProject javaProject = JavaCore.create(androidAppProject);
			IClasspathEntry[] classpathEntries = javaProject.getRawClasspath();
			if (!isAndroidSdkInstalled()) {
				// android SDK is not detected, plugin will do nothing
				assertEquals(2, classpathEntries.length);
			} else {
				// android SDK is detected, android project should be imported successfully
				assertEquals(6, classpathEntries.length);
			}
			this.preferences.setAndroidSupportEnabled(false);
			for (IProject project : projects) {
				projectsManager.updateProject(project, true);
			}
			waitForBackgroundJobs();
			// regardless of ANDROID_HOME, the number of cpe is 2 since android support is disabled
			assertEquals(2, javaProject.getRawClasspath().length);
		} finally {
			this.preferences.setAndroidSupportEnabled(false);
		}
	}

	@Test
	public void testNeedReplaceContent() throws Exception {
		File f = null;
		try {
			f = File.createTempFile("test", ".txt");
			MessageDigest md = MessageDigest.getInstance("sha-256");
			md.update(java.nio.file.Files.readAllBytes(f.toPath()));
			byte[] digest = md.digest();
			assertTrue( GradleUtils.needReplaceContent(f, digest));

			java.nio.file.Files.write(f.toPath(), "modification".getBytes());
			assertTrue(GradleUtils.needReplaceContent(f, digest));
		} finally {
			if (f != null && f.exists()) {
				if (!f.delete()) {
					f.deleteOnExit();
				}
			}
		}
	}

	@Test
	public void testAnnotationProcessing() throws Exception {
		IProject project = importGradleProject("apt");
		IJavaProject javaProject = JavaCore.create(project);

		assertNotNull(javaProject);
		assertTrue(AptConfig.isEnabled(javaProject));
		assertEquals("true", AptConfig.getRawProcessorOptions(javaProject).get("mapstruct.suppressGeneratorTimestamp"));
		assertEquals("apt", AptConfig.getRawProcessorOptions(javaProject).get("test.arg"));
	}

	@Test
	public void testGetGradleDistribution() {
		File projectRoot = new File(getSourceProjectDirectory(), "gradle/no-gradlew");
		GradleDistribution distribution = GradleProjectImporter.getGradleDistribution(projectRoot.toPath());
		assertTrue(distribution instanceof WrapperGradleDistribution);
	}

	@Test
	public void testAspectSupportDisabled() throws Exception {
		boolean oldAspectSupported = this.preferences.isAspectjSupportEnabled();
		try {
			this.preferences.setAspectjSupportEnabled(false);
			IProject project = importGradleProject("aspect");
			assertTrue(ProjectUtils.isGradleProject(project));
			assertHasErrors(project);

		} finally {
			this.preferences.setAspectjSupportEnabled(oldAspectSupported);
		}
	}

	@Test
	public void testAspectSupportEnabled() throws Exception {
		boolean oldAspectSupported = this.preferences.isAspectjSupportEnabled();
		try {
			this.preferences.setAspectjSupportEnabled(true);
			IProject project = importGradleProject("aspect");
			assertTrue(ProjectUtils.isGradleProject(project));
			assertNoErrors(project);
			IJavaProject javaProject = JavaCore.create(project);
			IType demoAspect = javaProject.findType("io.freefair.DemoAspect");
			assertNotNull(demoAspect);
		} finally {
			this.preferences.setAspectjSupportEnabled(oldAspectSupported);
		}
	}

	@Test
	public void testCleanUpBuildServerFootprint() throws Exception {
		IProject project = importGradleProject("gradle-build-server");
		assertFalse(ProjectUtils.hasNature(project, GradleProjectImporter.GRADLE_BUILD_SERVER_NATURE));
		for (ICommand command : project.getDescription().getBuildSpec()) {
			if (Objects.equals(command.getBuilderName(), GradleProjectImporter.GRADLE_BUILD_SERVER_BUILDER_ID) ||
					Objects.equals(command.getBuilderName(), GradleProjectImporter.JAVA_PROBLEM_CHECKER_ID)) {
				fail("Build server builders should have been removed");
			}
		}
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

	private boolean isAndroidSdkInstalled() {
		String androidHome = System.getenv("ANDROID_HOME");
		String androidSdkRoot = System.getenv("ANDROID_SDK_ROOT");
		return androidHome != null || androidSdkRoot != null;
	}
}
