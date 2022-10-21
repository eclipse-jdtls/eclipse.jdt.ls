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

import static org.eclipse.jdt.ls.core.internal.ProjectUtils.getJavaSourceLevel;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.apache.commons.io.FileUtils;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IProjectDescription;
import org.eclipse.core.resources.IWorkspace;
import org.eclipse.core.resources.IWorkspaceRoot;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.core.runtime.jobs.IJobChangeEvent;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.core.runtime.jobs.JobChangeAdapter;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.ls.core.internal.JavaLanguageServerPlugin;
import org.eclipse.jdt.ls.core.internal.ProjectUtils;
import org.eclipse.jdt.ls.core.internal.ResourceUtils;
import org.eclipse.jdt.ls.core.internal.WorkspaceHelper;
import org.eclipse.jdt.ls.core.internal.handlers.BuildWorkspaceHandler;
import org.eclipse.jdt.ls.core.internal.handlers.ProgressReporterManager;
import org.eclipse.jdt.ls.core.internal.preferences.Preferences.FeatureStatus;
import org.eclipse.lsp4j.jsonrpc.CancelChecker;
import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;

import com.google.common.collect.ImmutableList;

/**
 * @author Fred Bricon
 */
@RunWith(MockitoJUnitRunner.class)
public class MavenProjectImporterTest extends AbstractMavenBasedTest {

	private static final String PROJECT1_PATTERN = "**/project1";

	private MavenUpdateProjectJobSpy jobSpy;

	private void attachJobSpy() {
		jobSpy = new MavenUpdateProjectJobSpy();
		Job.getJobManager().addJobChangeListener(jobSpy);
	}

	@After
	public void removeJobSpy() {
		if (jobSpy != null) {
			Job.getJobManager().removeJobChangeListener(jobSpy);
		}
	}

	@Test
	public void testImportSimpleJavaProject() throws Exception {
		attachJobSpy();
		importSimpleJavaProject();
		assertEquals("New Projects should not be updated", 0, jobSpy.updateProjectJobCalled);
		assertTaskCompleted(MavenProjectImporter.IMPORTING_MAVEN_PROJECTS);
	}

	@Test
	public void testJavaImportExclusions() throws Exception {
		List<String> javaImportExclusions = JavaLanguageServerPlugin.getPreferencesManager().getPreferences().getJavaImportExclusions();
		try {
			javaImportExclusions.add(PROJECT1_PATTERN);
			List<IProject> projects = importProjects("maven/multi");
			assertEquals(2, projects.size());//default + project 2
			IProject project1 = WorkspaceHelper.getProject("project1");
			assertNull(project1);
			IProject project2 = WorkspaceHelper.getProject("project2");
			assertIsMavenProject(project2);
		} finally {
			javaImportExclusions.remove(PROJECT1_PATTERN);
		}
	}

	@Test
	public void testNodeModules() throws Exception {
		ProgressReporter progressReporter = new ProgressReporter();
		ProgressReporterManager progressManager = new ProgressReporterManager(this.client, preferenceManager) {

			@Override
			public IProgressMonitor getDefaultMonitor() {
				return progressReporter;
			}

			@Override
			public IProgressMonitor createMonitor(Job job) {
				return progressReporter;
			}

			@Override
			public IProgressMonitor getProgressReporter(CancelChecker checker) {
				return progressReporter;
			}

		};
		Job.getJobManager().setProgressProvider(progressManager);
		monitor = progressManager.getDefaultMonitor();
		importProjects("maven/salut5");
		IProject proj = WorkspaceHelper.getProject("proj");
		assertIsMavenProject(proj);
		assertFalse("node_modules has been scanned", progressReporter.isScanned());
	}

	@Test
	public void testUnzippedSourceImportExclusions() throws Exception {
		List<IProject> projects = importProjects("maven/unzipped-sources");
		assertEquals(Arrays.asList(ProjectsManager.getDefaultProject()), projects);
	}

	@Test
	public void testDisableMaven() throws Exception {
		boolean enabled = JavaLanguageServerPlugin.getPreferencesManager().getPreferences().isImportMavenEnabled();
		try {
			JavaLanguageServerPlugin.getPreferencesManager().getPreferences().setImportMavenEnabled(false);
			List<IProject> projects = importProjects("eclipse/eclipsemaven");
			assertEquals(2, projects.size());//default + 1 eclipse projects
			IProject eclipse = WorkspaceHelper.getProject("eclipse");
			assertNotNull(eclipse);
			assertFalse(eclipse.getName() + " has the Maven nature", ProjectUtils.isMavenProject(eclipse));
		} finally {
			JavaLanguageServerPlugin.getPreferencesManager().getPreferences().setImportMavenEnabled(enabled);
		}
	}

	@Test
	public void testUnchangedProjectShouldNotBeUpdated() throws Exception {
		attachJobSpy();
		String name = "salut";
		importMavenProject(name);
		assertEquals("New Project should not be updated", 0, jobSpy.updateProjectJobCalled);
		importExistingMavenProject(name);
		assertEquals("Unchanged Project should not be updated", 0, jobSpy.updateProjectJobCalled);
	}

	@Test
	public void testChangedProjectShouldBeUpdated() throws Exception {
		attachJobSpy();
		String name = "salut";
		IProject salut = importMavenProject(name);
		assertEquals("New Project should not be updated", 0, jobSpy.updateProjectJobCalled);
		File pom = salut.getFile(MavenProjectImporter.POM_FILE).getRawLocation().toFile();
		pom.setLastModified(System.currentTimeMillis() + 1000);
		importExistingMavenProject(name);
		assertEquals("Changed Project should be updated", 1, jobSpy.updateProjectJobCalled);
	}

	@Test
	public void testPreexistingIProjectDifferentName() throws Exception {
		File from = new File(getSourceProjectDirectory(), "maven/salut");
		Path projectDir = Files.createTempDirectory("testImportDifferentName");

		IWorkspaceRoot wsRoot = WorkspaceHelper.getWorkspaceRoot();
		IWorkspace workspace = wsRoot.getWorkspace();
		String projectName = projectDir.getFileName().toString();
		IProject salut = wsRoot.getProject("salut");
		salut.delete(true, monitor);
		IProjectDescription description = workspace.newProjectDescription(projectName);
		description.setLocation(new org.eclipse.core.runtime.Path(projectDir.toFile().getAbsolutePath()));

		IProject project = wsRoot.getProject(projectName);
		project.create(description, monitor);


		assertTrue(WorkspaceHelper.getAllProjects().contains(project));

		FileUtils.copyDirectory(from, projectDir.toFile());

		assertTrue(project.exists());
		Job updateJob = projectsManager.updateWorkspaceFolders(Collections.singleton(new org.eclipse.core.runtime.Path(projectDir.toString())), Collections.emptySet());
		updateJob.join(20000, monitor);
		assertTrue("Failed to import preexistingProjectTest:\n" + updateJob.getResult().getException(), updateJob.getResult().isOK());
	}

	@Test
	public void testPreexistingIProjectSameName() throws Exception {
		File from = new File(getSourceProjectDirectory(), "maven/salut");
		Path workspaceDir = Files.createTempDirectory("preexistingProjectTest");
		Path projectDir = Files.createDirectory(workspaceDir.resolve("TheSalutProject"));
		FileUtils.copyDirectory(from, projectDir.toFile());

		projectsManager.initializeProjects(Collections.singleton(new org.eclipse.core.runtime.Path(workspaceDir.toString())), monitor);

		Job updateJob = projectsManager.updateWorkspaceFolders(Collections.singleton(new org.eclipse.core.runtime.Path(workspaceDir.toString())), Collections.emptySet());
		updateJob.join(20000, monitor);
		assertTrue("Failed to import testImportDifferentName:\n" + updateJob.getResult().getException(), updateJob.getResult().isOK());
	}

	@Test
	public void testJava9Project() throws Exception {
		IProject project = importMavenProject("salut-java9");
		assertIsJavaProject(project);
		assertEquals("9", getJavaSourceLevel(project));
		assertNoErrors(project);
	}

	@Test
	public void testJava110Project() throws Exception {
		IProject project = importMavenProject("salut-java110");
		assertIsJavaProject(project);
		assertEquals("10", getJavaSourceLevel(project));
		assertNoErrors(project);
	}

	@Test
	public void testJava10Project() throws Exception {
		IProject project = importMavenProject("salut-java10");
		assertIsJavaProject(project);
		assertEquals("10", getJavaSourceLevel(project));
		assertNoErrors(project);
	}

	@Test
	public void testJava11Project() throws Exception {
		IProject project = importMavenProject("salut-java11");
		assertIsJavaProject(project);
		assertEquals("11", getJavaSourceLevel(project));
		assertNoErrors(project);
	}

	@Test
	public void testJava12Project() throws Exception {
		IProject project = importMavenProject("salut-java12");
		assertIsJavaProject(project);
		assertEquals("12", getJavaSourceLevel(project));
		IJavaProject javaProject = JavaCore.create(project);
		assertEquals(JavaCore.ENABLED, javaProject.getOption(JavaCore.COMPILER_PB_ENABLE_PREVIEW_FEATURES, false));
		assertEquals(JavaCore.IGNORE, javaProject.getOption(JavaCore.COMPILER_PB_REPORT_PREVIEW_FEATURES, false));
		// https://bugs.eclipse.org/bugs/show_bug.cgi?id=549258#c9
		assertHasErrors(project, "Preview features enabled at an invalid source release level");
	}

	@Test
	public void testJava13Project() throws Exception {
		IProject project = importMavenProject("salut-java13");
		assertIsJavaProject(project);
		assertEquals("13", getJavaSourceLevel(project));
		IJavaProject javaProject = JavaCore.create(project);
		assertEquals(JavaCore.ENABLED, javaProject.getOption(JavaCore.COMPILER_PB_ENABLE_PREVIEW_FEATURES, false));
		assertEquals(JavaCore.IGNORE, javaProject.getOption(JavaCore.COMPILER_PB_REPORT_PREVIEW_FEATURES, false));
		// https://bugs.eclipse.org/bugs/show_bug.cgi?id=549258#c9
		assertHasErrors(project, "Preview features enabled at an invalid source release level");
	}

	@Test
	public void testJava14Project() throws Exception {
		IProject project = importMavenProject("salut-java14");
		assertIsJavaProject(project);
		assertEquals("14", getJavaSourceLevel(project));
		IJavaProject javaProject = JavaCore.create(project);
		assertEquals(JavaCore.ENABLED, javaProject.getOption(JavaCore.COMPILER_PB_ENABLE_PREVIEW_FEATURES, false));
		assertEquals(JavaCore.IGNORE, javaProject.getOption(JavaCore.COMPILER_PB_REPORT_PREVIEW_FEATURES, false));
		assertHasErrors(project, "Preview features enabled at an invalid source release level");
	}

	@Test
	public void testJava15Project() throws Exception {
		IProject project = importMavenProject("salut-java15");
		assertIsJavaProject(project);
		assertEquals("15", getJavaSourceLevel(project));
		IJavaProject javaProject = JavaCore.create(project);
		assertEquals(JavaCore.ENABLED, javaProject.getOption(JavaCore.COMPILER_PB_ENABLE_PREVIEW_FEATURES, false));
		assertEquals(JavaCore.IGNORE, javaProject.getOption(JavaCore.COMPILER_PB_REPORT_PREVIEW_FEATURES, false));
		assertHasErrors(project, "Preview features enabled at an invalid source release level");
	}

	@Test
	public void testJava16Project() throws Exception {
		IProject project = importMavenProject("salut-java16");
		assertIsJavaProject(project);
		assertEquals("16", getJavaSourceLevel(project));
		IJavaProject javaProject = JavaCore.create(project);
		assertEquals(JavaCore.ENABLED, javaProject.getOption(JavaCore.COMPILER_PB_ENABLE_PREVIEW_FEATURES, false));
		assertEquals(JavaCore.IGNORE, javaProject.getOption(JavaCore.COMPILER_PB_REPORT_PREVIEW_FEATURES, false));
		assertHasErrors(project, "Preview features enabled at an invalid source release level");
		// assertNoErrors(project);
	}

	@Test
	public void testJava17Project() throws Exception {
		IProject project = importMavenProject("salut-java17");
		assertIsJavaProject(project);
		assertEquals("17", getJavaSourceLevel(project));
		IJavaProject javaProject = JavaCore.create(project);
		assertEquals(JavaCore.ENABLED, javaProject.getOption(JavaCore.COMPILER_PB_ENABLE_PREVIEW_FEATURES, false));
		assertEquals(JavaCore.IGNORE, javaProject.getOption(JavaCore.COMPILER_PB_REPORT_PREVIEW_FEATURES, false));
		assertHasErrors(project, "Preview features enabled at an invalid source release level");
		// assertNoErrors(project);
	}

	@Test
	public void testJava18Project() throws Exception {
		IProject project = importMavenProject("salut-java18");
		assertIsJavaProject(project);
		assertEquals("18", getJavaSourceLevel(project));
		IJavaProject javaProject = JavaCore.create(project);
		assertEquals(JavaCore.ENABLED, javaProject.getOption(JavaCore.COMPILER_PB_ENABLE_PREVIEW_FEATURES, false));
		assertEquals(JavaCore.IGNORE, javaProject.getOption(JavaCore.COMPILER_PB_REPORT_PREVIEW_FEATURES, false));
		// assertNoErrors(project);
	}

	@Test
	public void testJava19Project() throws Exception {
		IProject project = importMavenProject("salut-java19");
		assertIsJavaProject(project);
		assertEquals("19", getJavaSourceLevel(project));
		IJavaProject javaProject = JavaCore.create(project);
		assertEquals(JavaCore.ENABLED, javaProject.getOption(JavaCore.COMPILER_PB_ENABLE_PREVIEW_FEATURES, false));
		assertEquals(JavaCore.IGNORE, javaProject.getOption(JavaCore.COMPILER_PB_REPORT_PREVIEW_FEATURES, false));
		assertNoErrors(project);
	}

	@Test
	public void testAnnotationProcessing() throws Exception {
		IProject project = importMavenProject("autovalued");
		assertIsJavaProject(project);
		IFile autovalueFoo = project.getFile("target/generated-sources/annotations/foo/bar/AutoValue_Foo.java");
		assertTrue(autovalueFoo.getRawLocation() + " was not generated", autovalueFoo.exists());
		assertNoErrors(project);
	}

	@Test
	public void avoidImportDuplicatedProjects() throws Exception {
		try {
			this.preferences.setImportMavenEnabled(false);
			importProjects("multi-buildtools");
			MavenProjectImporter importer = new MavenProjectImporter();
			File root = new File(getWorkingProjectDirectory(), "multi-buildtools");
			importer.initialize(root);

			Collection<IPath> configurationPaths = new ArrayList<>();
			configurationPaths.add(ResourceUtils.canonicalFilePathFromURI(root.toPath().resolve("pom.xml").toUri().toString()));
			this.preferences.setImportMavenEnabled(true);
			assertFalse(importer.applies(configurationPaths, null));
		} finally {
			this.preferences.setImportMavenEnabled(true);
		}
	}

	// https://github.com/redhat-developer/vscode-java/issues/2712
	@Test
	public void testNullAnalysisDisabled() throws Exception {
		this.preferenceManager.getPreferences().setNonnullTypes(ImmutableList.of("javax.annotation.Nonnull", "org.eclipse.jdt.annotation.NonNull"));
		this.preferenceManager.getPreferences().setNullableTypes(ImmutableList.of("org.eclipse.jdt.annotation.Nullable", "javax.annotation.Nonnull"));
		this.preferenceManager.getPreferences().setNullAnalysisMode(FeatureStatus.automatic);
		try {
			IProject project = importMavenProject("null-analysis");
			assertIsJavaProject(project);
			if (this.preferenceManager.getPreferences().updateAnnotationNullAnalysisOptions()) {
				BuildWorkspaceHandler buildWorkspaceHandler = new BuildWorkspaceHandler(JavaLanguageServerPlugin.getProjectsManager());
				buildWorkspaceHandler.buildWorkspace(true, new NullProgressMonitor());
			}
			IJavaProject javaProject = JavaCore.create(project);
			Map<String, String> options = javaProject.getOptions(true);
			assertEquals(JavaCore.DISABLED, options.get(JavaCore.COMPILER_ANNOTATION_NULL_ANALYSIS));
		} finally {
			this.preferenceManager.getPreferences().setNonnullTypes(Collections.emptyList());
			this.preferenceManager.getPreferences().setNullableTypes(Collections.emptyList());
			this.preferenceManager.getPreferences().setNullAnalysisMode(FeatureStatus.disabled);
			this.preferenceManager.getPreferences().updateAnnotationNullAnalysisOptions();
		}
	}

	private static class MavenUpdateProjectJobSpy extends JobChangeAdapter {

		int updateProjectJobCalled;

		@Override
		public void scheduled(IJobChangeEvent event) {
			String jobName = event.getJob().getName();
			if ("Update Maven project configuration".equals(jobName)) {
				updateProjectJobCalled++;
			}
		}

	}

	private static class ProgressReporter extends NullProgressMonitor {
		private boolean scanned = false;

		public boolean isScanned() {
			return scanned;
		}

		public ProgressReporter() {
			super();
		}

		@Override
		public void subTask(String name) {
			if (name != null && name.endsWith("node_modules/sub")) {
				scanned = true;
			}
		}

		@Override
		public boolean isCanceled() {
			return false;
		}
	}

}