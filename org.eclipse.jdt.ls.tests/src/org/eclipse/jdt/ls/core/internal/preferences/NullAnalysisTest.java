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
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.util.Collections;
import java.util.List;

import javax.xml.catalog.CatalogFeatures.Feature;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IMarker;
import org.eclipse.core.resources.IProject;
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

import com.google.common.collect.ImmutableList;

@RunWith(MockitoJUnitRunner.class)
public class NullAnalysisTest extends AbstractGradleBasedTest {

	@Test
	public void testNullAnalysisWithJavax() throws Exception {
		try {
			this.preferenceManager.getPreferences().setNonnullTypes(ImmutableList.of("javax.annotation.Nonnull", "org.eclipse.jdt.annotation.NonNull"));
			this.preferenceManager.getPreferences().setNullableTypes(ImmutableList.of("javax.annotation.Nullable", "org.eclipse.jdt.annotation.Nullable"));
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
			assertNoErrors(project);
		} finally {
			this.preferenceManager.getPreferences().setNonnullTypes(Collections.emptyList());
			this.preferenceManager.getPreferences().setNullableTypes(Collections.emptyList());
			this.preferenceManager.getPreferences().updateAnnotationNullAnalysisOptions();
			this.preferenceManager.getPreferences().setNullAnalysisMode(FeatureStatus.disabled);
		}
	}

	@Test
	public void testMixedNullAnalysis() throws Exception {
		try {
			this.preferenceManager.getPreferences().setNonnullTypes(ImmutableList.of("javax.annotation.Nonnull", "org.eclipse.jdt.annotation.NonNull"));
			this.preferenceManager.getPreferences().setNullableTypes(ImmutableList.of("org.eclipse.jdt.annotation.Nullable", "javax.annotation.Nonnull"));
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
			this.preferenceManager.getPreferences().setNullAnalysisMode(FeatureStatus.disabled);
			this.preferenceManager.getPreferences().updateAnnotationNullAnalysisOptions();
		}
	}

	@Test
	public void testNullAnalysisDisabled() throws Exception {
		this.preferenceManager.getPreferences().setNonnullTypes(ImmutableList.of("javax.annotation.Nonnull", "org.eclipse.jdt.annotation.NonNull"));
		this.preferenceManager.getPreferences().setNullableTypes(ImmutableList.of("javax.annotation.Nullable", "org.eclipse.jdt.annotation.Nullable"));
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
	}

	@Test
	public void testKeepExistingProjectOptions() throws Exception {
		try {
			this.preferenceManager.getPreferences().setNonnullTypes(ImmutableList.of("javax.annotation.Nonnull", "org.eclipse.jdt.annotation.NonNull"));
			this.preferenceManager.getPreferences().setNullableTypes(ImmutableList.of("javax.annotation.Nullable", "org.eclipse.jdt.annotation.Nullable"));
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
			this.preferenceManager.getPreferences().updateAnnotationNullAnalysisOptions();
		}
	}
}
