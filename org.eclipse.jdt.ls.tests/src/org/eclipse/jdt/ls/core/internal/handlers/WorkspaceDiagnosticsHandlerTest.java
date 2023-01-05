/*******************************************************************************
 * Copyright (c) 2016-2021 Red Hat Inc. and others.
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
package org.eclipse.jdt.ls.core.internal.handlers;

import static org.eclipse.jdt.ls.core.internal.WorkspaceHelper.getProject;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.atMost;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.commons.io.FileUtils;
import org.eclipse.core.internal.resources.ValidateProjectEncoding;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IFolder;
import org.eclipse.core.resources.IMarker;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IncrementalProjectBuilder;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaModelMarker;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.compiler.IProblem;
import org.eclipse.jdt.ls.core.internal.JavaClientConnection;
import org.eclipse.jdt.ls.core.internal.ResourceUtils;
import org.eclipse.jdt.ls.core.internal.WorkspaceHelper;
import org.eclipse.jdt.ls.core.internal.managers.AbstractProjectsManagerBasedTest;
import org.eclipse.jdt.ls.tests.Unstable;
import org.eclipse.jface.text.Document;
import org.eclipse.jface.text.IDocument;
import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.DiagnosticSeverity;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.PublishDiagnosticsParams;
import org.eclipse.lsp4j.Range;
import org.eclipse.m2e.core.internal.IMavenConstants;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;

/**
 * @author Fred Bricon
 *
 */
@SuppressWarnings("restriction")
@RunWith(MockitoJUnitRunner.class)
public class WorkspaceDiagnosticsHandlerTest extends AbstractProjectsManagerBasedTest {
	@Mock
	private JavaClientConnection connection;

	private WorkspaceDiagnosticsHandler handler;

	private static final Comparator<Diagnostic> DIAGNOSTICS_COMPARATOR = (Diagnostic d1, Diagnostic d2) -> {
		int diff = d1.getRange().getStart().getLine() - d2.getRange().getStart().getLine();
		if (diff == 0) {
			diff = d1.getMessage().compareTo(d2.getMessage());
		}
		return diff;
	};

	@Before
	public void setup() throws Exception {
		handler = new WorkspaceDiagnosticsHandler(connection, projectsManager, preferenceManager.getClientPreferences());
		handler.addResourceChangeListener();
	}

	@After
	@Override
	public void cleanUp() throws Exception {
		super.cleanUp();
		handler.removeResourceChangeListener();
		connection.disconnect();
	}

	@Test
	public void testToDiagnosticsArray() throws Exception {
		String msg1 = "Something's wrong Jim";
		IMarker m1 = createMarker(IMarker.SEVERITY_WARNING, msg1, 2, 95, 100);

		String msg2 = "He's dead";
		IMarker m2 = createMarker(IMarker.SEVERITY_ERROR, msg2, 10, 1015, 1025);

		String msg3 = "It's probably time to panic";
		IMarker m3 = createMarker(42, msg3, 100, 10000, 10005);

		String msg4 = "ArrayList cannot be resolved to a type";
		IMarker m4 = createUndefinedTypeMarker(IMarker.SEVERITY_ERROR, msg4, 14, 215, 228);

		IDocument d = mock(IDocument.class);
		Mockito.lenient().when(d.getLineOffset(1)).thenReturn(90);
		Mockito.lenient().when(d.getLineOffset(9)).thenReturn(1000);
		Mockito.lenient().when(d.getLineOffset(14)).thenReturn(210);
		Mockito.lenient().when(d.getLineOffset(99)).thenReturn(10000);

		List<Diagnostic> diags = WorkspaceDiagnosticsHandler.toDiagnosticsArray(d, new IMarker[] { m1, m2, m3, m4 }, true);
		assertEquals(4, diags.size());

		Range r;
		Diagnostic d1 = diags.get(0);
		assertEquals(msg1, d1.getMessage());
		assertEquals(DiagnosticSeverity.Warning, d1.getSeverity());
		r = d1.getRange();
		assertEquals(1, r.getStart().getLine());
		assertEquals(5, r.getStart().getCharacter());
		assertEquals(1, r.getEnd().getLine());
		assertEquals(10, r.getEnd().getCharacter());

		Diagnostic d2 = diags.get(1);
		assertEquals(msg2, d2.getMessage());
		assertEquals(DiagnosticSeverity.Error, d2.getSeverity());
		r = d2.getRange();
		assertEquals(9, r.getStart().getLine());
		assertEquals(15, r.getStart().getCharacter());
		assertEquals(9, r.getEnd().getLine());
		assertEquals(25, r.getEnd().getCharacter());

		Diagnostic d3 = diags.get(2);
		assertEquals(msg3, d3.getMessage());
		assertEquals(DiagnosticSeverity.Information, d3.getSeverity());
		r = d3.getRange();
		assertEquals(99, r.getStart().getLine());
		assertEquals(0, r.getStart().getCharacter());
		assertEquals(99, r.getEnd().getLine());
		assertEquals(5, r.getEnd().getCharacter());

		Diagnostic d4 = diags.get(3);
		assertEquals(msg4, d4.getMessage());
		assertEquals(DiagnosticSeverity.Error, d4.getSeverity());
		r = d4.getRange();
		assertEquals(13, r.getStart().getLine());
		assertEquals(215, r.getStart().getCharacter());
		assertEquals(13, r.getEnd().getLine());
		assertEquals(228, r.getEnd().getCharacter());
	}

	@Test
	public void testTaskMarkers() throws Exception {
		//import project
		importProjects("eclipse/hello");
		ArgumentCaptor<PublishDiagnosticsParams> captor = ArgumentCaptor.forClass(PublishDiagnosticsParams.class);
		verify(connection, atLeastOnce()).publishDiagnostics(captor.capture());

		List<PublishDiagnosticsParams> allCalls = captor.getAllValues();
		Collections.reverse(allCalls);
		projectsManager.setConnection(client);

		Optional<PublishDiagnosticsParams> taskDiags = allCalls.stream().filter(p -> p.getUri().endsWith("TaskMarkerTest.java")).findFirst();
		assertTrue("No TaskMarkerTest.java markers were found", taskDiags.isPresent());
		List<Diagnostic> diags = taskDiags.get().getDiagnostics();
		assertEquals("Some marker is missing", 3, diags.size());
		long todoMarkers = diags.stream().filter(p -> p.getMessage().startsWith("TODO")).count();
		assertEquals("A TODO marker is missing", todoMarkers, 2);
		Collections.sort(diags, new Comparator<Diagnostic>() {

			@Override
			public int compare(Diagnostic o1, Diagnostic o2) {
				return o1.getMessage().compareTo(o2.getMessage());
			}
		});
		Range r;
		Diagnostic d = diags.get(1);
		assertEquals("TODO task 2", d.getMessage());
		assertEquals(DiagnosticSeverity.Information, d.getSeverity());
		r = d.getRange();
		assertEquals(11, r.getStart().getLine());
		assertEquals(11, r.getStart().getCharacter());
		assertEquals(11, r.getEnd().getLine());
		assertEquals(22, r.getEnd().getCharacter());
		d = diags.get(0);
		assertEquals("TODO task 1", d.getMessage());
		assertEquals(DiagnosticSeverity.Information, d.getSeverity());
		r = d.getRange();
		assertEquals(9, r.getStart().getLine());
		assertEquals(11, r.getStart().getCharacter());
		assertEquals(9, r.getEnd().getLine());
		assertEquals(22, r.getEnd().getCharacter());
	}

	@Test
	public void testMavenMarkers() throws Exception {
		String msg1 = "Some dependency is missing";
		IMarker m1 = createMavenMarker(IMarker.SEVERITY_ERROR, msg1, 2, 95, 100);

		IDocument d = mock(IDocument.class);

		List<Diagnostic> diags = WorkspaceDiagnosticsHandler.toDiagnosticsArray(d, new IMarker[] { m1, null }, true);
		assertEquals(1, diags.size());

		Range r;
		Diagnostic d1 = diags.get(0);
		assertEquals(msg1, d1.getMessage());
		assertEquals(DiagnosticSeverity.Error, d1.getSeverity());
		r = d1.getRange();
		assertEquals(1, r.getStart().getLine());
		assertEquals(95, r.getStart().getCharacter());
		assertEquals(1, r.getEnd().getLine());
		assertEquals(100, r.getEnd().getCharacter());
	}

	@Test
	public void testMarkerListening() throws Exception {
		//import project
		importProjects("maven/broken");

		ArgumentCaptor<PublishDiagnosticsParams> captor = ArgumentCaptor.forClass(PublishDiagnosticsParams.class);
		verify(connection, atLeastOnce()).publishDiagnostics(captor.capture());

		List<PublishDiagnosticsParams> allCalls = captor.getAllValues();
		Collections.reverse(allCalls);
		projectsManager.setConnection(client);

		/* With Maven 3.6.2 (m2e 1.14), source folders are no longer configured if dependencies are malformed (missing version tag here)
		Optional<PublishDiagnosticsParams> fooDiags = allCalls.stream().filter(p -> p.getUri().endsWith("Foo.java")).findFirst();
		assertTrue("No Foo.java errors were found", fooDiags.isPresent());
		List<Diagnostic> diags = fooDiags.get().getDiagnostics();
		Collections.sort(diags, DIAGNOSTICS_COMPARATOR );
		assertEquals(diags.toString(), 2, diags.size());
		assertEquals("The import org cannot be resolved", diags.get(0).getMessage());
		assertEquals("StringUtils cannot be resolved", diags.get(1).getMessage());
		*/

		Optional<PublishDiagnosticsParams> pomDiags = allCalls.stream().filter(p -> p.getUri().endsWith("pom.xml")).findFirst();
		assertTrue("No pom.xml errors were found", pomDiags.isPresent());
		List<Diagnostic> diags = pomDiags.get().getDiagnostics();
		// https://github.com/redhat-developer/vscode-java/issues/2857
		// m2e 2.2.0 returns 3 markers
		assertEquals(diags.toString(), 3, diags.size());
		Diagnostic diag = diags.stream().filter(d -> d.getMessage().startsWith("Project build error")).findFirst().get();
		assertEquals("Project build error: 'dependencies.dependency.version' for org.apache.commons:commons-lang3:jar is missing.", diag.getMessage());
	}

	@Test
	public void testProjectLevelMarkers() throws Exception {
		//import project
		importProjects("maven/broken");
		ArgumentCaptor<PublishDiagnosticsParams> captor = ArgumentCaptor.forClass(PublishDiagnosticsParams.class);
		verify(connection, atLeastOnce()).publishDiagnostics(captor.capture());
		List<PublishDiagnosticsParams> allCalls = captor.getAllValues();
		Collections.reverse(allCalls);
		projectsManager.setConnection(client);
		Optional<PublishDiagnosticsParams>projectDiags=allCalls.stream().filter(p->(p.getUri().endsWith("maven/broken"))||p.getUri().endsWith("maven/broken/")))).findFirst();
		assertTrue("No maven/broken errors were found", projectDiags.isPresent());
		List<Diagnostic> diags = projectDiags.get().getDiagnostics();
		Collections.sort(diags, DIAGNOSTICS_COMPARATOR);
		assertEquals(diags.toString(), 3, diags.size());
		assertTrue(diags.get(2).getMessage().startsWith("The compiler compliance specified is 1.7 but a JRE 1.8 is used"));
		Optional<PublishDiagnosticsParams> pomDiags = allCalls.stream().filter(p -> p.getUri().endsWith("pom.xml")).findFirst();
		assertTrue("No pom.xml errors were found", pomDiags.isPresent());
		diags = pomDiags.get().getDiagnostics();
		Collections.sort(diags, DIAGNOSTICS_COMPARATOR);
		assertEquals(diags.toString(), 1, diags.size());
		assertTrue(diags.get(0).getMessage().startsWith("Project build error: "));
	}

	@Test
	public void testBadLocationException() throws Exception {
		//import project
		importProjects("eclipse/hello");
		IProject project = getProject("hello");
		IFile iFile = project.getFile("/src/test1/A.java");
		File file = iFile.getRawLocation().toFile();
		assertTrue(file.exists());
		iFile = project.getFile("/src/test1/A1.java");
		File destFile = iFile.getRawLocation().toFile();
		assertFalse(destFile.exists());
		FileUtils.copyFile(file, destFile, false);
		String uri = destFile.toPath().toUri().toString();
		project.refreshLocal(IResource.DEPTH_INFINITE, null);
		waitForBackgroundJobs();
		ArgumentCaptor<PublishDiagnosticsParams> captor = ArgumentCaptor.forClass(PublishDiagnosticsParams.class);
		verify(connection, atLeastOnce()).publishDiagnostics(captor.capture());
		List<PublishDiagnosticsParams> allCalls = captor.getAllValues();
		Collections.reverse(allCalls);
		projectsManager.setConnection(client);
		Optional<PublishDiagnosticsParams> param = allCalls.stream().filter(p -> p.getUri().equals(uri)).findFirst();
		assertTrue(param.isPresent());
		List<Diagnostic> diags = param.get().getDiagnostics();
		assertEquals(diags.toString(), 2, diags.size());
		Optional<Diagnostic> d = diags.stream().filter(p -> p.getMessage().equals("The type A is already defined")).findFirst();
		assertTrue(d.isPresent());
		Diagnostic diag = d.get();
		assertTrue(diag.getRange().getStart().getLine() >= 0);
		assertTrue(diag.getRange().getStart().getCharacter() >= 0);
		assertTrue(diag.getRange().getEnd().getLine() >= 0);
		assertTrue(diag.getRange().getEnd().getCharacter() >= 0);
	}

	// https://github.com/eclipse/eclipse.jdt.ls/issues/1920
	@Test
	public void testWorkingCopy() throws Exception {
		//import project
		importProjects("eclipse/hello");
		IProject project = getProject("hello");
		IFile iFile = project.getFile("/src/test1/A.java");
		ICompilationUnit cu = null;
		try {
			cu = JavaCore.createCompilationUnitFrom(iFile);
			cu.becomeWorkingCopy(null);
			reset(connection);
			ResourceUtils.setContent(iFile, "package test1;\npublic class A() {}\n");
			waitForBackgroundJobs();
			ArgumentCaptor<PublishDiagnosticsParams> captor = ArgumentCaptor.forClass(PublishDiagnosticsParams.class);
			verify(connection, atMost(2)).publishDiagnostics(captor.capture());
		} finally {
			if (cu != null) {
				cu.discardWorkingCopy();
			}
		}
	}

	// https://github.com/eclipse/eclipse.jdt.ls/issues/1963
	@Test
	public void testWorkingCopy2() throws Exception {
		ICompilationUnit cu = null;
		try {
			DocumentLifeCycleHandler lifeCycleHandler = new DocumentLifeCycleHandler(connection, preferenceManager, projectsManager, false);
			handler.removeResourceChangeListener();
			handler = new WorkspaceDiagnosticsHandler(connection, projectsManager, preferenceManager.getClientPreferences(), lifeCycleHandler);
			handler.addResourceChangeListener();
			//import project
			importProjects("eclipse/hello");
			IProject project = getProject("hello");
			IFile iFile = project.getFile("/src/test1/A.java");

			cu = JavaCore.createCompilationUnitFrom(iFile);
			cu.becomeWorkingCopy(null);
			reset(connection);
			ResourceUtils.setContent(iFile, "package test1;\npublic class A() {}\n");
			waitForBackgroundJobs();
			ArgumentCaptor<PublishDiagnosticsParams> captor = ArgumentCaptor.forClass(PublishDiagnosticsParams.class);
			verify(connection, atMost(3)).publishDiagnostics(captor.capture());
		} finally {
			if (cu != null) {
				cu.discardWorkingCopy();
			}
		}
	}

	// https://github.com/eclipse/eclipse.jdt.ls/issues/1920
	@Test
	public void testWithoutWorkingCopy() throws Exception {
		//import project
		importProjects("eclipse/hello");
		IProject project = getProject("hello");
		IFile iFile = project.getFile("/src/test1/A.java");
		reset(connection);
		ResourceUtils.setContent(iFile, "package test1;\npublic class A() {}\n");
		waitForBackgroundJobs();
		ArgumentCaptor<PublishDiagnosticsParams> captor = ArgumentCaptor.forClass(PublishDiagnosticsParams.class);
		verify(connection, atLeast(3)).publishDiagnostics(captor.capture());
	}

	@Test
	public void testMissingNatures() throws Exception {
		//import project
		importProjects("eclipse/wtpproject");
		waitForBackgroundJobs();
		ArgumentCaptor<PublishDiagnosticsParams> captor = ArgumentCaptor.forClass(PublishDiagnosticsParams.class);
		verify(connection, atLeastOnce()).publishDiagnostics(captor.capture());
		List<PublishDiagnosticsParams> allCalls = captor.getAllValues();
		Collections.reverse(allCalls);
		projectsManager.setConnection(client);
		// https://github.com/eclipse/eclipse.jdt.ls/issues/2331
		boolean hasMissingNature = false;
		for (PublishDiagnosticsParams projectDiags : allCalls) {
			if (hasMissingNature = projectDiags.getDiagnostics().stream().filter(p -> (p.getMessage().startsWith("Unknown referenced nature"))).findFirst().isPresent()) {
				break;
			}
		}
		assertTrue(hasMissingNature);
	}

	@Test
	public void testProjectConfigurationIsNotUpToDate() throws Exception {
		//import project
		importProjects("maven/salut");
		IProject project = ResourcesPlugin.getWorkspace().getRoot().getProject("salut");
		IFile pom = project.getFile("/pom.xml");
		assertTrue(pom.exists());
		ResourceUtils.setContent(pom, ResourceUtils.getContent(pom).replaceAll("1.7", "1.8"));

		ResourcesPlugin.getWorkspace().build(IncrementalProjectBuilder.FULL_BUILD, monitor);
		assertNoErrors(project);
		List<IMarker> warnings = ResourceUtils.getWarningMarkers(project);

		Optional<IMarker> outOfDateWarning = warnings.stream().filter(w -> Messages.ProjectConfigurationUpdateRequired.equals(ResourceUtils.getMessage(w))).findFirst();
		assertTrue("No out-of-date warning found", outOfDateWarning.isPresent());

		ArgumentCaptor<PublishDiagnosticsParams> captor = ArgumentCaptor.forClass(PublishDiagnosticsParams.class);
		verify(connection, atLeastOnce()).publishDiagnostics(captor.capture());
		List<PublishDiagnosticsParams> allCalls = captor.getAllValues();
		Collections.reverse(allCalls);
		projectsManager.setConnection(client);
		Optional<PublishDiagnosticsParams>projectDiags=allCalls.stream().filter(p->(p.getUri().endsWith("maven/salut"))||p.getUri().endsWith("maven/salut/")))).findFirst();
		assertTrue("No maven/salut errors were found", projectDiags.isPresent());
		List<Diagnostic> diags = projectDiags.get().getDiagnostics();
		assertEquals(diags.toString(), 2, diags.size());
		Optional<PublishDiagnosticsParams> pomDiags = allCalls.stream().filter(p -> p.getUri().endsWith("pom.xml")).findFirst();
		assertTrue("No pom.xml errors were found", pomDiags.isPresent());
		diags = pomDiags.get().getDiagnostics();
		assertEquals(diags.toString(), 1, diags.size());
		Diagnostic diag = diags.get(0);
		assertTrue(diag.getMessage().equals(WorkspaceDiagnosticsHandler.PROJECT_CONFIGURATION_IS_NOT_UP_TO_DATE_WITH_POM_XML));
		assertEquals(diag.getSeverity(), DiagnosticSeverity.Warning);
	}

	@Test
	public void testAnnotation() throws Exception {
		importProjects("maven/salut4");
		IProject proj = WorkspaceHelper.getProject("salut4");
		IJavaProject javaProject = JavaCore.create(proj);
		assertTrue(javaProject.exists());
		IType type = javaProject.findType("org.sample.MyTest");
		ICompilationUnit unit = type.getCompilationUnit();
		IResource resource = unit.getUnderlyingResource();
		IMarker[] markers = resource.findMarkers(IJavaModelMarker.JAVA_MODEL_PROBLEM_MARKER, false, IResource.DEPTH_ZERO);
		IDocument document = new Document(unit.getSource());
		List<Diagnostic> diagnostics = WorkspaceDiagnosticsHandler.toDiagnosticsArray(document, markers, false);
		assertEquals(4, diagnostics.size());
		Optional<Diagnostic> diag = diagnostics.stream().filter(p -> "Test cannot be resolved to a type".equals(p.getMessage())).findFirst();
		Diagnostic diagnostic = diag.get();
		assertEquals(4, diagnostic.getRange().getStart().getCharacter());
	}

	@Test
	public void testMissingDependencies() throws Exception {
		importProjects("maven/salut");
		IProject project = ResourcesPlugin.getWorkspace().getRoot().getProject("salut");
		IFile pom = project.getFile("/pom.xml");
		assertTrue(pom.exists());
		ResourcesPlugin.getWorkspace().build(IncrementalProjectBuilder.CLEAN_BUILD, monitor);
		assertNoErrors(project);
		// edit pom.xml
		ResourceUtils.setContent(pom, ResourceUtils.getContent(pom).replaceAll("<version>3.5</version>", "<version>3.5xx</version>"));
		waitForBackgroundJobs();
		ArgumentCaptor<PublishDiagnosticsParams> captor = ArgumentCaptor.forClass(PublishDiagnosticsParams.class);
		verify(connection, atLeastOnce()).publishDiagnostics(captor.capture());
		List<PublishDiagnosticsParams> allCalls = captor.getAllValues();
		Collections.reverse(allCalls);
		projectsManager.setConnection(client);
		testDiagnostic(allCalls);
		// update project
		reset(connection);
		captor = ArgumentCaptor.forClass(PublishDiagnosticsParams.class);
		projectsManager.updateProject(project, true);
		waitForBackgroundJobs();
		verify(connection, atLeastOnce()).publishDiagnostics(captor.capture());
		allCalls = captor.getAllValues();
		Collections.reverse(allCalls);
		testDiagnostic(allCalls);
		// build workspace
		reset(connection);
		captor = ArgumentCaptor.forClass(PublishDiagnosticsParams.class);
		BuildWorkspaceHandler bwh = new BuildWorkspaceHandler(projectsManager);
		bwh.buildWorkspace(true, new NullProgressMonitor());
		verify(connection, atLeastOnce()).publishDiagnostics(captor.capture());
		allCalls = captor.getAllValues();
		Collections.reverse(allCalls);
		testDiagnostic(allCalls);
		// publish diagnostics
		reset(connection);
		captor = ArgumentCaptor.forClass(PublishDiagnosticsParams.class);
		handler.publishDiagnostics(new NullProgressMonitor());
		verify(connection, atLeastOnce()).publishDiagnostics(captor.capture());
		allCalls = captor.getAllValues();
		Collections.reverse(allCalls);
		testDiagnostic(allCalls);
	}

	private void testDiagnostic(List<PublishDiagnosticsParams> allCalls) {
		List<Diagnostic> projectDiags = new ArrayList<>();
		List<Diagnostic> pomDiags = new ArrayList<>();
		for (PublishDiagnosticsParams diag : allCalls) {
			if (diag.getUri().endsWith("maven/salut") || diag.getUri().endsWith("maven/salut/")) {
				projectDiags.addAll(diag.getDiagnostics());
			} else if (diag.getUri().endsWith("pom.xml")) {
				pomDiags.addAll(diag.getDiagnostics());
			}
		}
		assertTrue("No maven/salut errors were found", projectDiags.size() > 0);
		Optional<Diagnostic> projectDiag = projectDiags.stream().filter(p -> p.getMessage().contains("references non existing library")).findFirst();
		assertTrue("No 'references non existing library' diagnostic", projectDiag.isPresent());
		assertEquals(projectDiag.get().getSeverity(), DiagnosticSeverity.Error);
		assertTrue("No pom.xml errors were found", pomDiags.size() > 0);
		Optional<Diagnostic> pomDiag = pomDiags.stream().filter(p -> p.getMessage().startsWith("Missing artifact")).findFirst();
		assertTrue("No 'missing artifact' diagnostic", pomDiag.isPresent());
		assertTrue(pomDiag.get().getMessage().startsWith("Missing artifact"));
		assertEquals(pomDiag.get().getRange().getStart().getLine(), 19);
		assertEquals(pomDiag.get().getRange().getStart().getCharacter(), 3);
		assertEquals(pomDiag.get().getRange().getEnd().getLine(), 19);
		assertEquals(pomDiag.get().getRange().getEnd().getCharacter(), 14);
		assertEquals(pomDiag.get().getSeverity(), DiagnosticSeverity.Error);
	}

	@Test
	@Category(Unstable.class)
	public void testResetPomDiagnostics() throws Exception {
		//import project
		importProjects("maven/multimodule");
		IProject project = ResourcesPlugin.getWorkspace().getRoot().getProject("multimodule");
		IFile pom = project.getFile("/pom.xml");
		assertTrue(pom.exists());
		//@formatter:off
		String compilerPlugin = "\n<build>\n" +
				"    <pluginManagement>\n" +
				"        <plugins>\n" +
				"            <plugin>\n" +
				"                <artifactId>maven-compiler-plugin</artifactId>\n" +
				"                <version>3.8.0</version>\n" +
				"                <configuration>\n" +
				"                    <release>9</release>\n" +
				"                </configuration>\n" +
				"            </plugin>\n" +
				"        </plugins>\n" +
				"    </pluginManagement>\n" +
				"</build>\n";
		//@formatter:on

		ResourceUtils.setContent(pom, ResourceUtils.getContent(pom).replaceAll("<profiles>", compilerPlugin + "\n<profiles>"));

		waitForBackgroundJobs();

		ArgumentCaptor<PublishDiagnosticsParams> captor = ArgumentCaptor.forClass(PublishDiagnosticsParams.class);
		verify(connection, atLeastOnce()).publishDiagnostics(captor.capture());
		List<PublishDiagnosticsParams> allCalls = captor.getAllValues();

		List<PublishDiagnosticsParams> pomDiags = allCalls.stream().filter(p -> p.getUri().endsWith("pom.xml") && !p.getDiagnostics().isEmpty()).collect(Collectors.toList());
		assertEquals("No pom.xml errors were found", 3, pomDiags.size());
		assertTrue(pomDiags.get(0).getUri(), pomDiags.get(0).getUri().endsWith("childmodule/pom.xml"));
		assertEquals(1, pomDiags.get(0).getDiagnostics().size());
		assertEquals(pomDiags.get(0).getDiagnostics().get(0).getMessage(), WorkspaceDiagnosticsHandler.PROJECT_CONFIGURATION_IS_NOT_UP_TO_DATE_WITH_POM_XML);
		assertTrue(pomDiags.get(1).getUri().endsWith("module2/pom.xml"));
		assertEquals(1, pomDiags.get(1).getDiagnostics().size());
		assertEquals(pomDiags.get(1).getDiagnostics().get(0).getMessage(), WorkspaceDiagnosticsHandler.PROJECT_CONFIGURATION_IS_NOT_UP_TO_DATE_WITH_POM_XML);
		assertTrue(pomDiags.get(2).getUri().endsWith("module3/pom.xml"));
		assertEquals(1, pomDiags.get(2).getDiagnostics().size());
		assertEquals(pomDiags.get(2).getDiagnostics().get(0).getMessage(), WorkspaceDiagnosticsHandler.PROJECT_CONFIGURATION_IS_NOT_UP_TO_DATE_WITH_POM_XML);

		reset(connection);
		captor = ArgumentCaptor.forClass(PublishDiagnosticsParams.class);
		projectsManager.updateProject(project, true);
		waitForBackgroundJobs();

		verify(connection, atLeastOnce()).publishDiagnostics(captor.capture());
		allCalls = captor.getAllValues();

		pomDiags = allCalls.stream().filter(p -> p.getUri().endsWith("pom.xml")).collect(Collectors.toList());
		boolean reset1 = false;
		boolean reset2 = false;
		boolean reset3 = true;
		for (PublishDiagnosticsParams diag : pomDiags) {
			String uri = diag.getUri();
			if (uri.endsWith("childmodule/pom.xml")) {
				assertEquals("Unexpected diagnostics:\n" + diag.getDiagnostics(), 0, diag.getDiagnostics().size());
				reset1 = true;
			} else if (uri.endsWith("module2/pom.xml")) {
				assertEquals("Unexpected diagnostics:\n" + diag.getDiagnostics(), 0, diag.getDiagnostics().size());
				reset2 = true;
			} else if (uri.endsWith("module3/pom.xml")) {//not a active module so was not updated. But this is actually a dubious behavior. Need to change that
				assertEquals("Unexpected diagnostics:\n" + diag.getDiagnostics(), 1, diag.getDiagnostics().size());
				reset3 = false;
			}
		}
		assertTrue("childmodule/pom.xml diagnostics were not reset", reset1);
		assertTrue("module2/pom.xml diagnostics were not reset", reset2);
		assertFalse("module3/pom.xml diagnostics were reset", reset3);

	}

	@Test
	public void testDeletePackage() throws Exception {
		importProjects("eclipse/unresolvedtype");
		IProject project = ResourcesPlugin.getWorkspace().getRoot().getProject("unresolvedtype");
		List<IMarker> markers = ResourceUtils.getErrorMarkers(project);
		assertTrue("unresolved type in Foo.java", markers.stream().anyMatch((marker) -> marker.getResource() != null && ((IFile) marker.getResource()).getName().endsWith("Foo.java")));

		reset(connection);
		ArgumentCaptor<PublishDiagnosticsParams> captor = ArgumentCaptor.forClass(PublishDiagnosticsParams.class);
		IFolder folder = project.getFolder("/src/pckg");
		assertTrue(folder.exists());
		folder.delete(true, new NullProgressMonitor());
		waitForBackgroundJobs();

		verify(connection, atLeastOnce()).publishDiagnostics(captor.capture());
		List<PublishDiagnosticsParams> allCalls = captor.getAllValues();
		List<PublishDiagnosticsParams> errors = allCalls.stream().filter((p) -> p.getUri().endsWith("Foo.java")).collect(Collectors.toList());
		assertTrue("Should update the children's diagnostics of the deleted package", errors.size() == 1);
		assertTrue("Should clean up the children's diagnostics of the deleted package", errors.get(0).getDiagnostics().isEmpty());
	}

	@Test
	public void testEncoding() throws Exception {
		ProjectEncodingMode projectEncoding = preferenceManager.getPreferences().getProjectEncoding();
		try {
			importProjects("eclipse/hello");
			IProject proj = WorkspaceHelper.getProject("hello");
			IJavaProject javaProject = JavaCore.create(proj);
			assertTrue(javaProject.exists());
			IMarker[] markers = proj.findMarkers(ValidateProjectEncoding.MARKER_TYPE, false, IResource.DEPTH_ZERO);
			assertEquals(1, markers.length);
			Range range = new Range(new Position(0, 0), new Position(0, 0));
			List<IMarker> encodingMarkers = Stream.of(markers).collect(Collectors.toList());
			List<Diagnostic> diagnostics = WorkspaceDiagnosticsHandler.toDiagnosticArray(range, encodingMarkers, false);
			assertEquals(0, diagnostics.size());
			preferenceManager.getPreferences().setProjectEncoding(ProjectEncodingMode.WARNING);
			diagnostics = WorkspaceDiagnosticsHandler.toDiagnosticArray(range, encodingMarkers, false);
			assertEquals(1, diagnostics.size());
			preferenceManager.getPreferences().setProjectEncoding(ProjectEncodingMode.SETDEFAULT);
			proj.delete(true, monitor);
			importProjects("eclipse/hello");
			proj = WorkspaceHelper.getProject("hello");
			waitForBackgroundJobs();
			markers = proj.findMarkers(ValidateProjectEncoding.MARKER_TYPE, false, IResource.DEPTH_ZERO);
			assertEquals(0, markers.length);
		} finally {
			preferenceManager.getPreferences().setProjectEncoding(projectEncoding);
		}
	}

	private IMarker createMarker(int severity, String msg, int line, int start, int end) {
		IMarker m = mock(IMarker.class);
		when(m.exists()).thenReturn(true);
		when(m.getAttribute(IMarker.SEVERITY, -1)).thenReturn(severity);
		when(m.getAttribute(IMarker.MESSAGE, "")).thenReturn(msg);
		when(m.getAttribute(IMarker.LINE_NUMBER, -1)).thenReturn(line);
		when(m.getAttribute(IMarker.CHAR_START, -1)).thenReturn(start);
		when(m.getAttribute(IMarker.CHAR_END, -1)).thenReturn(end);
		return m;
	}

	private IMarker createUndefinedTypeMarker(int severity, String msg, int line, int start, int end) {
		IMarker m = createMarker(severity, msg, line, start, end);
		when(m.exists()).thenReturn(true);
		when(m.getAttribute(IJavaModelMarker.ID, -1)).thenReturn(IProblem.UndefinedType);
		return m;
	}

	private IMarker createMavenMarker(int severity, String msg, int line, int start, int end) throws Exception {
		IMarker m = createMarker(severity, msg, line, start, end);
		when(m.isSubtypeOf(IMavenConstants.MARKER_ID)).thenReturn(true);
		when(m.getAttribute(IMavenConstants.MARKER_COLUMN_START, -1)).thenReturn(start);
		when(m.getAttribute(IMavenConstants.MARKER_COLUMN_END, -1)).thenReturn(end);
		return m;
	}

}
