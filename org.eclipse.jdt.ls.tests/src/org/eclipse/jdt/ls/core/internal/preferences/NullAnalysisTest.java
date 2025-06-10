/*******************************************************************************
 * Copyright (c) 2022 Microsoft Corporation and others.
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
package org.eclipse.jdt.ls.core.internal.preferences;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayInputStream;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IMarker;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.ls.core.internal.JavaLanguageServerPlugin;
import org.eclipse.jdt.ls.core.internal.ProjectUtils;
import org.eclipse.jdt.ls.core.internal.ResourceUtils;
import org.eclipse.jdt.ls.core.internal.handlers.BuildWorkspaceHandler;
import org.eclipse.jdt.ls.core.internal.managers.AbstractGradleBasedTest;
import org.eclipse.jdt.ls.core.internal.preferences.Preferences.FeatureStatus;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class NullAnalysisTest extends AbstractGradleBasedTest {

	@Test
	public void testNullAnalysisWithJavax() throws Exception {
		try {
			this.preferenceManager.getPreferences().setNonnullTypes(List.of("javax.annotation.Nonnull", "org.eclipse.jdt.annotation.NonNull"));
			this.preferenceManager.getPreferences().setNullableTypes(List.of("javax.annotation.Nullable", "org.eclipse.jdt.annotation.Nullable"));
			this.preferenceManager.getPreferences().setNonnullbydefaultTypes(List.of("org.eclipse.jdt.annotation.NonNullByDefault", "javax.annotation.ParametersAreNonnullByDefault"));
			this.preferenceManager.getPreferences().setNullAnalysisMode(FeatureStatus.automatic);
			IProject project = importGradleProject("null-analysis");
			assertIsJavaProject(project);
			if (this.preferenceManager.getPreferences().updateAnnotationNullAnalysisOptions()) {
				BuildWorkspaceHandler buildWorkspaceHandler = new BuildWorkspaceHandler(JavaLanguageServerPlugin.getProjectsManager());
				buildWorkspaceHandler.buildWorkspace(true, new NullProgressMonitor());
			}
			IMarker marker = getWarningMarker(project, "Null type mismatch: required '@Nonnull String' but the provided value is null");
			assertNotNull(marker);
			assertTrue(marker.getResource() instanceof IFile);
			assertEquals("TestJavax.java", ((IFile) marker.getResource()).getFullPath().lastSegment());
			IMarker marker1 = getWarningMarker(project, "Potential null pointer access: The method nullable() may return null");
			assertNotNull(marker);
			assertTrue(marker.getResource() instanceof IFile);
			assertEquals("TestJavax.java", ((IFile) marker1.getResource()).getFullPath().lastSegment());
			IMarker marker2 = getWarningMarker(project, "The return type is incompatible with '@Nonnull String' returned from TestJavax.A.nonnullMethod() (mismatching null constraints)");
			assertNotNull(marker);
			assertTrue(marker.getResource() instanceof IFile);
			assertEquals("TestJavax.java", ((IFile) marker2.getResource()).getFullPath().lastSegment());
			IMarker marker3 = getWarningMarker(project, "Null type mismatch: required '@Nonnull List<String>' but the provided value is specified as @Nullable");
			assertNotNull(marker);
			assertTrue(marker.getResource() instanceof IFile);
			assertEquals("TestJavax.java", ((IFile) marker3.getResource()).getFullPath().lastSegment());
			// See https://github.com/redhat-developer/vscode-java/issues/3255
			IMarker marker4 = getWarningMarker(project, "Potential null pointer access: The field obj is specified as @Nullable");
			assertNull(marker4);
			assertNoErrors(project);
		} finally {
			this.preferenceManager.getPreferences().setNonnullTypes(Collections.emptyList());
			this.preferenceManager.getPreferences().setNullableTypes(Collections.emptyList());
			this.preferenceManager.getPreferences().setNonnullbydefaultTypes(Collections.emptyList());
			this.preferenceManager.getPreferences().updateAnnotationNullAnalysisOptions();
			this.preferenceManager.getPreferences().setNullAnalysisMode(FeatureStatus.disabled);
		}
	}

	@Test
	public void testMixedNullAnalysis() throws Exception {
		try {
			this.preferenceManager.getPreferences().setNonnullTypes(List.of("javax.annotation.Nonnull", "org.eclipse.jdt.annotation.NonNull"));
			this.preferenceManager.getPreferences().setNullableTypes(List.of("org.eclipse.jdt.annotation.Nullable", "javax.annotation.Nonnull"));
			this.preferenceManager.getPreferences().setNonnullbydefaultTypes(List.of("org.eclipse.jdt.annotation.NonNullByDefault", "javax.annotation.ParametersAreNonnullByDefault"));
			this.preferenceManager.getPreferences().setNullAnalysisMode(FeatureStatus.automatic);
			IProject project = importGradleProject("null-analysis");
			assertIsJavaProject(project);
			if (this.preferenceManager.getPreferences().updateAnnotationNullAnalysisOptions()) {
				BuildWorkspaceHandler buildWorkspaceHandler = new BuildWorkspaceHandler(JavaLanguageServerPlugin.getProjectsManager());
				buildWorkspaceHandler.buildWorkspace(true, new NullProgressMonitor());
			}
			IMarker marker = getWarningMarker(project, "Null type mismatch: required '@Nonnull String' but the provided value is null");
			assertNotNull(marker);
			assertTrue(marker.getResource() instanceof IFile);
			assertEquals("TestJavax.java", ((IFile) marker.getResource()).getFullPath().lastSegment());
			IMarker marker1 = getWarningMarker(project, "Potential null pointer access: The method nullable() may return null");
			assertNotNull(marker);
			assertTrue(marker.getResource() instanceof IFile);
			assertEquals("TestJDT.java", ((IFile) marker1.getResource()).getFullPath().lastSegment());
			IMarker marker2 = getWarningMarker(project, "The return type is incompatible with '@Nonnull String' returned from TestJavax.A.nonnullMethod() (mismatching null constraints)");
			assertNotNull(marker);
			assertTrue(marker.getResource() instanceof IFile);
			assertEquals("TestJavax.java", ((IFile) marker2.getResource()).getFullPath().lastSegment());
			IMarker marker3 = getWarningMarker(project, "Null type safety: The expression of type 'List<String>' needs unchecked conversion to conform to '@Nonnull List<String>'");
			assertNotNull(marker);
			assertTrue(marker.getResource() instanceof IFile);
			assertEquals("TestJavax.java", ((IFile) marker3.getResource()).getFullPath().lastSegment());
			assertNoErrors(project);
		} finally {
			this.preferenceManager.getPreferences().setNonnullTypes(Collections.emptyList());
			this.preferenceManager.getPreferences().setNullableTypes(Collections.emptyList());
			this.preferenceManager.getPreferences().setNonnullbydefaultTypes(Collections.emptyList());
			this.preferenceManager.getPreferences().setNullAnalysisMode(FeatureStatus.disabled);
			this.preferenceManager.getPreferences().updateAnnotationNullAnalysisOptions();
		}
	}

	@Test
	public void testNullAnalysisDisabled() throws Exception {
		try {
			this.preferenceManager.getPreferences().setNonnullTypes(List.of("javax.annotation.Nonnull", "org.eclipse.jdt.annotation.NonNull"));
			this.preferenceManager.getPreferences().setNullableTypes(List.of("javax.annotation.Nullable", "org.eclipse.jdt.annotation.Nullable"));
			this.preferenceManager.getPreferences().setNonnullbydefaultTypes(List.of("org.eclipse.jdt.annotation.NonNullByDefault", "javax.annotation.ParametersAreNonnullByDefault"));
			this.preferenceManager.getPreferences().setNullAnalysisMode(FeatureStatus.disabled);
			IProject project = importGradleProject("null-analysis");
			assertIsJavaProject(project);
			if (this.preferenceManager.getPreferences().updateAnnotationNullAnalysisOptions()) {
				BuildWorkspaceHandler buildWorkspaceHandler = new BuildWorkspaceHandler(JavaLanguageServerPlugin.getProjectsManager());
				buildWorkspaceHandler.buildWorkspace(true, new NullProgressMonitor());
			}
			List<IMarker> warningMarkers = ResourceUtils.getWarningMarkers(project);
			assertEquals(3, warningMarkers.size());
			assertNoErrors(project);
		} finally {
			this.preferenceManager.getPreferences().setNonnullTypes(Collections.emptyList());
			this.preferenceManager.getPreferences().setNullableTypes(Collections.emptyList());
			this.preferenceManager.getPreferences().setNonnullbydefaultTypes(Collections.emptyList());
			this.preferenceManager.getPreferences().updateAnnotationNullAnalysisOptions();
		}
	}

	@Test
	public void testKeepExistingProjectOptions() throws Exception {
		try {
			this.preferenceManager.getPreferences().setNonnullTypes(List.of("javax.annotation.Nonnull", "org.eclipse.jdt.annotation.NonNull"));
			this.preferenceManager.getPreferences().setNullableTypes(List.of("javax.annotation.Nullable", "org.eclipse.jdt.annotation.Nullable"));
			this.preferenceManager.getPreferences().setNonnullbydefaultTypes(List.of("org.eclipse.jdt.annotation.NonNullByDefault", "javax.annotation.ParametersAreNonnullByDefault"));

			IProject project = importGradleProject("null-analysis");
			assertIsJavaProject(project);
			if (this.preferenceManager.getPreferences().updateAnnotationNullAnalysisOptions()) {
				BuildWorkspaceHandler buildWorkspaceHandler = new BuildWorkspaceHandler(JavaLanguageServerPlugin.getProjectsManager());
				buildWorkspaceHandler.buildWorkspace(true, new NullProgressMonitor());
			}
			IJavaProject javaProject = ProjectUtils.getJavaProject(project);
			// sourceCompatibility = '11' defined in project null-analysis build.gradle
			assertEquals("11", javaProject.getOption(JavaCore.COMPILER_COMPLIANCE, false));
			assertEquals("11", javaProject.getOption(JavaCore.COMPILER_CODEGEN_TARGET_PLATFORM, false));
			assertEquals("11", javaProject.getOption(JavaCore.COMPILER_SOURCE, false));

		} finally {
			this.preferenceManager.getPreferences().setNonnullTypes(Collections.emptyList());
			this.preferenceManager.getPreferences().setNullableTypes(Collections.emptyList());
			this.preferenceManager.getPreferences().setNonnullbydefaultTypes(Collections.emptyList());
			this.preferenceManager.getPreferences().updateAnnotationNullAnalysisOptions();
		}
	}

	@Test
	public void testNonnullbyDefault() throws Exception {
		try {
			this.preferenceManager.getPreferences().setNonnullTypes(List.of("javax.annotation.Nonnull", "org.eclipse.jdt.annotation.NonNull"));
			this.preferenceManager.getPreferences().setNullableTypes(List.of("org.eclipse.jdt.annotation.Nullable", "javax.annotation.Nonnull"));
			this.preferenceManager.getPreferences().setNonnullbydefaultTypes(List.of("org.eclipse.jdt.annotation.NonNullByDefault", "javax.annotation.ParametersAreNonnullByDefault"));
			this.preferenceManager.getPreferences().setNullAnalysisMode(FeatureStatus.automatic);
			IProject project = importGradleProject("null-analysis");
			assertIsJavaProject(project);
			BuildWorkspaceHandler buildWorkspaceHandler = new BuildWorkspaceHandler(JavaLanguageServerPlugin.getProjectsManager());
			if (this.preferenceManager.getPreferences().updateAnnotationNullAnalysisOptions()) {
				buildWorkspaceHandler.buildWorkspace(true, new NullProgressMonitor());
			}
			Map<String, String> options = JavaCore.create(project).getOptions(true);
			assertEquals(options.get(JavaCore.COMPILER_NONNULL_BY_DEFAULT_ANNOTATION_NAME), "org.eclipse.jdt.annotation.NonNullByDefault");
			IFile file = project.getFile("/src/main/java/org/sample/Test.java");
			assertTrue(file.exists());
			IMarker[] markers = file.findMarkers(null, true, IResource.DEPTH_INFINITE);
			assertEquals(1, markers.length);
			IMarker marker = getWarningMarker(project, "The @Nonnull field count may not have been initialized");
			assertNotNull(marker);
			IFile packageInfo = project.getFile("/src/main/java/org/sample/package-info.java");
			assertTrue(packageInfo.exists());
			String contents = """
					package org.sample;
					""";
			ByteArrayInputStream newContent = new ByteArrayInputStream(contents.getBytes("UTF-8")); //$NON-NLS-1$
			packageInfo.setContents(newContent, IResource.FORCE, new NullProgressMonitor());
			buildWorkspaceHandler.buildWorkspace(true, new NullProgressMonitor());
			markers = file.findMarkers(null, true, IResource.DEPTH_INFINITE);
			assertEquals(0, markers.length);
			marker = getWarningMarker(project, "The @Nonnull field count may not have been initialized");
			assertNull(marker);
			assertNoErrors(project);
		} finally {
			this.preferenceManager.getPreferences().setNonnullTypes(Collections.emptyList());
			this.preferenceManager.getPreferences().setNullableTypes(Collections.emptyList());
			this.preferenceManager.getPreferences().setNonnullbydefaultTypes(Collections.emptyList());
			this.preferenceManager.getPreferences().setNullAnalysisMode(FeatureStatus.disabled);
			this.preferenceManager.getPreferences().updateAnnotationNullAnalysisOptions();
		}
	}

	// https://github.com/redhat-developer/vscode-java/issues/3387
	@Test
	public void testMissingNonNull() throws Exception {
		try {
			this.preferenceManager.getPreferences().setNonnullTypes(List.of("javax.annotation.Nonnull", "org.eclipse.jdt.annotation.NonNull"));
			this.preferenceManager.getPreferences().setNullableTypes(List.of("javax.annotation.Nullable", "org.eclipse.jdt.annotation.Nullable"));
			this.preferenceManager.getPreferences().setNonnullbydefaultTypes(List.of("org.eclipse.jdt.annotation.NonNullByDefault", "javax.annotation.ParametersAreNonnullByDefault"));
			this.preferenceManager.getPreferences().setNullAnalysisMode(FeatureStatus.automatic);
			List<IProject> projects = importProjects("eclipse/testnullable3");
			IProject project = projects.get(0);
			assertIsJavaProject(project);
			boolean updated = this.preferenceManager.getPreferences().updateAnnotationNullAnalysisOptions();
			assertFalse(updated);
			projects = importProjects("eclipse/testnullable2");
			project = projects.get(0);
			assertIsJavaProject(project);
			updated = this.preferenceManager.getPreferences().updateAnnotationNullAnalysisOptions();
			assertTrue(updated);
		} finally {
			this.preferenceManager.getPreferences().setNonnullTypes(Collections.emptyList());
			this.preferenceManager.getPreferences().setNullableTypes(Collections.emptyList());
			this.preferenceManager.getPreferences().setNonnullbydefaultTypes(Collections.emptyList());
			this.preferenceManager.getPreferences().updateAnnotationNullAnalysisOptions();
			this.preferenceManager.getPreferences().setNullAnalysisMode(FeatureStatus.disabled);
		}
	}

}
