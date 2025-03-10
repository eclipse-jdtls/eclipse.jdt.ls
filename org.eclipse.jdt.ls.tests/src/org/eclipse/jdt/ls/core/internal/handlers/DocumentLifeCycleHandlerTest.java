/*******************************************************************************
 * Copyright (c) 2017 Red Hat Inc. and others.
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

import static java.util.Map.entry;
import static org.eclipse.jdt.ls.core.internal.Lsp4jAssertions.assertRange;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.when;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.apache.commons.io.FileUtils;
import org.eclipse.core.resources.IFolder;
import org.eclipse.core.resources.IMarker;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IPackageFragment;
import org.eclipse.jdt.core.IPackageFragmentRoot;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.compiler.IProblem;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.manipulation.CoreASTProvider;
import org.eclipse.jdt.ls.core.internal.JDTUtils;
import org.eclipse.jdt.ls.core.internal.JavaLanguageServerPlugin;
import org.eclipse.jdt.ls.core.internal.ProjectUtils;
import org.eclipse.jdt.ls.core.internal.ResourceUtils;
import org.eclipse.jdt.ls.core.internal.WorkspaceHelper;
import org.eclipse.jdt.ls.core.internal.handlers.BaseDocumentLifeCycleHandler.DocumentMonitor;
import org.eclipse.jdt.ls.core.internal.managers.AbstractProjectsManagerBasedTest;
import org.eclipse.jdt.ls.core.internal.preferences.ClientPreferences;
import org.eclipse.jdt.ls.core.internal.preferences.Preferences;
import org.eclipse.jdt.ls.core.internal.preferences.Preferences.Severity;
import org.eclipse.lsp4j.CodeAction;
import org.eclipse.lsp4j.CodeActionContext;
import org.eclipse.lsp4j.CodeActionKind;
import org.eclipse.lsp4j.CodeActionParams;
import org.eclipse.lsp4j.Command;
import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.DidChangeTextDocumentParams;
import org.eclipse.lsp4j.DidCloseTextDocumentParams;
import org.eclipse.lsp4j.DidOpenTextDocumentParams;
import org.eclipse.lsp4j.DidSaveTextDocumentParams;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.PublishDiagnosticsParams;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.TextDocumentContentChangeEvent;
import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.eclipse.lsp4j.TextDocumentItem;
import org.eclipse.lsp4j.VersionedTextDocumentIdentifier;
import org.eclipse.lsp4j.jsonrpc.ResponseErrorException;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class DocumentLifeCycleHandlerTest extends AbstractProjectsManagerBasedTest {

	private CoreASTProvider sharedASTProvider;

	private DocumentLifeCycleHandler lifeCycleHandler;

	private File temp;

	@Mock
	private ClientPreferences clientPreferences;

	@Before
	public void setup() throws Exception {
		mockPreferences();

		sharedASTProvider = CoreASTProvider.getInstance();
		sharedASTProvider.disposeAST();
		//		sharedASTProvider.clearASTCreationCount();
		JDTLanguageServer server = new JDTLanguageServer(projectsManager, preferenceManager);
		server.connectClient(client);
		lifeCycleHandler = new DocumentLifeCycleHandler(server.getClientConnection(), preferenceManager, projectsManager, false);
		JavaLanguageServerPlugin.getNonProjectDiagnosticsState().setGlobalErrorLevel(true);
		JavaLanguageServerPlugin.getInstance().setProtocol(server);
	}

	@After
	public void tearDown() throws Exception {
		JavaLanguageServerPlugin.getNonProjectDiagnosticsState().setGlobalErrorLevel(true);
		for (ICompilationUnit cu : JavaCore.getWorkingCopies(null)) {
			cu.discardWorkingCopy();
		}
		FileUtils.deleteQuietly(temp);
	}

	@Test
	public void testUnimplementedMethods() throws Exception {
		IJavaProject javaProject = newEmptyProject();
		IPackageFragmentRoot sourceFolder = javaProject.getPackageFragmentRoot(javaProject.getProject().getFolder("src"));
		IPackageFragment pack1 = sourceFolder.createPackageFragment("test1", false, null);

		StringBuilder buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("public interface E {\n");
		buf.append("    void foo();\n");
		buf.append("}\n");
		pack1.createCompilationUnit("E.java", buf.toString(), false, null);

		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("public class F implements E {\n");
		buf.append("}\n");
		ICompilationUnit cu = pack1.createCompilationUnit("F.java", buf.toString(), false, null);
		openDocument(cu, cu.getSource(), 1);

		List<Either<Command, CodeAction>> codeActions = getCodeActions(cu);
		assertEquals(codeActions.size(), 1);
		assertEquals(codeActions.get(0).getRight().getKind(), CodeActionKind.QuickFix);
	}

	@Test
	public void testRemoveDeadCodeAfterIf() throws Exception {
		IJavaProject javaProject = newEmptyProject();
		IPackageFragmentRoot sourceFolder = javaProject.getPackageFragmentRoot(javaProject.getProject().getFolder("src"));
		IPackageFragment pack1 = sourceFolder.createPackageFragment("test1", false, null);

		StringBuilder buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("public class E {\n");
		buf.append("    public boolean foo(boolean b1) {\n");
		buf.append("        if (false) {\n");
		buf.append("            return true;\n");
		buf.append("        }\n");
		buf.append("        return false;\n");
		buf.append("    }\n");
		buf.append("}\n");
		ICompilationUnit cu = pack1.createCompilationUnit("E.java", buf.toString(), false, null);

		List<Either<Command, CodeAction>> codeActions = getCodeActions(cu);
		boolean hasCodeAction = codeActions.stream().anyMatch(codeAction -> "Remove (including condition)".equals(codeAction.getRight().getTitle()));
		assertTrue("Code action to remove dead code not found", hasCodeAction);
	}

	protected List<Either<Command, CodeAction>> getCodeActions(ICompilationUnit cu) throws JavaModelException {

		CompilationUnit astRoot = CoreASTProvider.getInstance().getAST(cu, CoreASTProvider.WAIT_YES, null);
		IProblem[] problems = astRoot.getProblems();

		Range range = getRange(cu, problems);

		CodeActionParams parms = new CodeActionParams();

		TextDocumentIdentifier textDocument = new TextDocumentIdentifier();
		textDocument.setUri(JDTUtils.toURI(cu));
		parms.setTextDocument(textDocument);
		parms.setRange(range);
		CodeActionContext context = new CodeActionContext();
		context.setDiagnostics(DiagnosticsHandler.toDiagnosticsArray(cu, Arrays.asList(problems), true));
		context.setOnly(Arrays.asList(CodeActionKind.QuickFix));
		parms.setContext(context);

		return new CodeActionHandler(this.preferenceManager).getCodeActionCommands(parms, new NullProgressMonitor());
	}

	private Range getRange(ICompilationUnit cu, IProblem[] problems) throws JavaModelException {
		IProblem problem = problems[0];
		return JDTUtils.toRange(cu, problem.getSourceStart(), 0);
	}

	private Preferences mockPreferences() {
		Preferences mockPreferences = Mockito.mock(Preferences.class);
		Mockito.lenient().when(mockPreferences.getProjectConfigurations()).thenReturn(null);
		Mockito.lenient().when(preferenceManager.getPreferences()).thenReturn(mockPreferences);
		Mockito.lenient().when(preferenceManager.getPreferences(Mockito.any())).thenReturn(mockPreferences);
		Mockito.lenient().when(mockPreferences.getIncompleteClasspathSeverity()).thenReturn(Severity.ignore);
		Mockito.lenient().when(this.preferenceManager.getClientPreferences()).thenReturn(clientPreferences);
		Mockito.lenient().when(clientPreferences.isSupportedCodeActionKind(CodeActionKind.QuickFix)).thenReturn(true);
		return mockPreferences;
	}

	@Test
	public void testBasicBufferLifeCycle() throws Exception {
		IJavaProject javaProject = newEmptyProject();
		IPackageFragmentRoot sourceFolder = javaProject.getPackageFragmentRoot(javaProject.getProject().getFolder("src"));
		IPackageFragment pack1 = sourceFolder.createPackageFragment("test1", false, null);

		StringBuilder buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("public class E123 {\n");
		buf.append("}\n");
		ICompilationUnit cu1 = pack1.createCompilationUnit("E123.java", buf.toString(), false, null);

		assertEquals(false, cu1.isWorkingCopy());
		assertEquals(false, cu1.hasUnsavedChanges());
		assertNewProblemReported();
		assertEquals(0, getCacheSize());
		assertNewASTsCreated(0);

		openDocument(cu1, cu1.getSource(), 1);

		assertEquals(true, cu1.isWorkingCopy());
		assertEquals(false, cu1.hasUnsavedChanges());
		assertNewProblemReported(new ExpectedProblemReport(cu1, 0));
		// https://github.com/eclipse/eclipse.jdt.ls/pull/2535
		assertEquals(0, getCacheSize());
		assertNewASTsCreated(1);

		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("public class E123 {\n");
		buf.append("  X x;\n");
		buf.append("}\n");

		changeDocumentFull(cu1, buf.toString(), 2);

		assertEquals(true, cu1.isWorkingCopy());
		assertEquals(true, cu1.hasUnsavedChanges());
		assertNewProblemReported(new ExpectedProblemReport(cu1, 1));
		assertEquals(0, getCacheSize());
		assertNewASTsCreated(1);

		saveDocument(cu1);

		assertEquals(true, cu1.isWorkingCopy());
		assertEquals(false, cu1.hasUnsavedChanges());
		assertNewProblemReported();
		assertEquals(1, getCacheSize());
		assertNewASTsCreated(0);

		closeDocument(cu1);

		assertEquals(false, cu1.isWorkingCopy());
		assertEquals(false, cu1.hasUnsavedChanges());
		assertNewProblemReported();
		assertEquals(0, getCacheSize());
		assertNewASTsCreated(0);
	}

	@Test
	public void testBasicBufferLifeCycleWithoutSave() throws Exception {
		IJavaProject javaProject = newEmptyProject();
		IPackageFragmentRoot sourceFolder = javaProject.getPackageFragmentRoot(javaProject.getProject().getFolder("src"));
		IPackageFragment pack1 = sourceFolder.createPackageFragment("test1", false, null);

		StringBuilder buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("public class E123 {\n");
		buf.append("    public boolean foo() {\n");
		buf.append("        return x;\n");
		buf.append("    }\n");
		buf.append("}\n");
		ICompilationUnit cu1 = pack1.createCompilationUnit("E123.java", buf.toString(), false, null);

		openDocument(cu1, cu1.getSource(), 1);

		assertEquals(true, cu1.isWorkingCopy());
		assertEquals(false, cu1.hasUnsavedChanges());
		assertNewProblemReported(new ExpectedProblemReport(cu1, 1));
		assertEquals(0, getCacheSize());
		assertNewASTsCreated(1);

		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("public class E123 {\n");
		buf.append("    public boolean foo() {\n");
		buf.append("        return true;\n");
		buf.append("    }\n");
		buf.append("}\n");

		changeDocumentFull(cu1, buf.toString(), 2);

		assertEquals(true, cu1.isWorkingCopy());
		assertEquals(true, cu1.hasUnsavedChanges());
		assertNewProblemReported(new ExpectedProblemReport(cu1, 0));
		assertEquals(0, getCacheSize());
		assertNewASTsCreated(1);

		closeDocument(cu1);

		assertEquals(false, cu1.isWorkingCopy());
		assertEquals(false, cu1.hasUnsavedChanges());
		assertNewProblemReported(new ExpectedProblemReport(cu1, 1));
		assertEquals(0, getCacheSize());
		assertNewASTsCreated(0);
	}

	@Test
	public void testReconcile() throws Exception {
		IJavaProject javaProject = newEmptyProject();
		IPackageFragmentRoot sourceFolder = javaProject.getPackageFragmentRoot(javaProject.getProject().getFolder("src"));
		IPackageFragment pack1 = sourceFolder.createPackageFragment("test1", false, null);
		StringBuilder buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("public class E123 {\n");
		buf.append("    public void testing() {\n");
		buf.append("        int someIntegerChanged = 5;\n");
		buf.append("        int i = someInteger + 5\n");
		buf.append("    }\n");
		buf.append("}\n");
		ICompilationUnit cu1 = pack1.createCompilationUnit("E123.java", buf.toString(), false, null);
		openDocument(cu1, cu1.getSource(), 1);
		assertEquals(true, cu1.isWorkingCopy());
		assertEquals(false, cu1.hasUnsavedChanges());
		List<PublishDiagnosticsParams> diagnosticsParams = getClientRequests("publishDiagnostics");
		assertEquals(1, diagnosticsParams.size());
		PublishDiagnosticsParams diagnosticsParam = diagnosticsParams.get(0);
		List<Diagnostic> diagnostics = diagnosticsParam.getDiagnostics();
		assertEquals(2, diagnostics.size());
		diagnosticsParams.clear();
		closeDocument(cu1);
	}

	@Test
	public void testWorkingCopies() throws Exception {
		importProjects("eclipse/hello");
		IProject project = WorkspaceHelper.getProject("hello");
		URI uri = project.getFile("/src/org/sample/Foo.java").getRawLocationURI();
		ICompilationUnit cu = JDTUtils.resolveCompilationUnit(uri);
		String source = Files.readString(FileUtils.toFile(uri.toURL()).toPath());
		openDocument(cu, source, 1);
		Job.getJobManager().join(DocumentLifeCycleHandler.DOCUMENT_LIFE_CYCLE_JOBS, monitor);
		ICompilationUnit cu1 = JDTUtils.resolveCompilationUnit(uri);
		ICompilationUnit cu2 = JDTUtils.resolveCompilationUnit(uri);
		assertSame(cu1, cu2);
		closeDocument(cu);
		Job.getJobManager().join(DocumentLifeCycleHandler.DOCUMENT_LIFE_CYCLE_JOBS, monitor);
		cu1 = JDTUtils.resolveCompilationUnit(uri);
		cu2 = JDTUtils.resolveCompilationUnit(uri);
		assertNotSame(cu1, cu2);
	}

	@Test
	public void testNonJdtError() throws Exception {
		importProjects("eclipse/hello");
		IProject project = WorkspaceHelper.getProject("hello");
		URI uri = project.getFile("/src/org/sample/Foo.java").getRawLocationURI();
		ICompilationUnit cu = JDTUtils.resolveCompilationUnit(uri);
		IResource resource = cu.getCorrespondingResource();
		// @formatter:off
		resource.createMarker("testNonJdtError", Map.ofEntries(
				entry(IMarker.MESSAGE, "Non-JDT errors."),
				entry(IMarker.SEVERITY, IMarker.SEVERITY_ERROR)));
		// @formatter:off
		String source = Files.readString(FileUtils.toFile(uri.toURL()).toPath());
		openDocument(cu, source, 1);
		Job.getJobManager().join(DocumentLifeCycleHandler.DOCUMENT_LIFE_CYCLE_JOBS, monitor);
		assertEquals(project, cu.getJavaProject().getProject());
		assertEquals(source, cu.getSource());
		List<PublishDiagnosticsParams> diagnosticReports = getClientRequests("publishDiagnostics");
		assertEquals(1, diagnosticReports.size());
		PublishDiagnosticsParams diagParam = diagnosticReports.get(0);
		assertEquals(1, diagParam.getDiagnostics().size());
		closeDocument(cu);
		Job.getJobManager().join(DocumentLifeCycleHandler.DOCUMENT_LIFE_CYCLE_JOBS, monitor);
		diagnosticReports = getClientRequests("publishDiagnostics");
		assertEquals(1, diagnosticReports.size());
		diagParam = diagnosticReports.get(0);
		assertEquals(1, diagParam.getDiagnostics().size());
		Diagnostic diagnostic = diagParam.getDiagnostics().get(0);
		assertEquals("Non-JDT errors.", diagnostic.getMessage());
	}

	@Test
	public void testIncrementalChangeDocument() throws Exception {
		IJavaProject javaProject = newEmptyProject();
		IPackageFragmentRoot sourceFolder = javaProject.getPackageFragmentRoot(javaProject.getProject().getFolder("src"));
		IPackageFragment pack1 = sourceFolder.createPackageFragment("test1", false, null);

		StringBuilder buf = new StringBuilder();
		String BEGIN_PART = "package test1;\n";
		String TO_BE_CHANGED_PART = "public class E123 {\n";
		String END_PART = "}\n";
		buf.append(BEGIN_PART);
		buf.append(TO_BE_CHANGED_PART);
		buf.append(END_PART);
		ICompilationUnit cu1 = pack1.createCompilationUnit("E123.java", buf.toString(), false, null);

		assertEquals(false, cu1.isWorkingCopy());
		assertEquals(false, cu1.hasUnsavedChanges());
		assertNewProblemReported();
		assertEquals(0, getCacheSize());
		assertNewASTsCreated(0);

		openDocument(cu1, cu1.getSource(), 1);

		assertEquals(true, cu1.isWorkingCopy());
		assertEquals(false, cu1.hasUnsavedChanges());
		assertNewProblemReported(new ExpectedProblemReport(cu1, 0));
		assertEquals(0, getCacheSize());
		assertNewASTsCreated(1);

		buf = new StringBuilder();
		buf.append(TO_BE_CHANGED_PART);
		buf.append("  X x;\n");

		changeDocumentIncrementally(cu1, buf.toString(), 2, BEGIN_PART.length(), TO_BE_CHANGED_PART.length());

		assertEquals(true, cu1.isWorkingCopy());
		assertEquals(true, cu1.hasUnsavedChanges());
		assertNewProblemReported(new ExpectedProblemReport(cu1, 1));
		assertEquals(0, getCacheSize());
		assertNewASTsCreated(1);

		saveDocument(cu1);

		assertEquals(true, cu1.isWorkingCopy());
		assertEquals(false, cu1.hasUnsavedChanges());
		assertNewProblemReported();
		assertEquals(1, getCacheSize());
		assertNewASTsCreated(0);

		closeDocument(cu1);

		assertEquals(false, cu1.isWorkingCopy());
		assertEquals(false, cu1.hasUnsavedChanges());
		assertNewProblemReported();
		assertEquals(0, getCacheSize());
		assertNewASTsCreated(0);
	}

	@Test
	public void testFixInDependencyScenario() throws Exception {
		IJavaProject javaProject = newEmptyProject();
		IPackageFragmentRoot sourceFolder = javaProject.getPackageFragmentRoot(javaProject.getProject().getFolder("src"));
		IPackageFragment pack1 = sourceFolder.createPackageFragment("test1", false, null);

		StringBuilder buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("public class F123 {\n");
		buf.append("}\n");
		ICompilationUnit cu1 = pack1.createCompilationUnit("F123.java", buf.toString(), false, null);

		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("public class F456 {\n");
		buf.append("  { F123.foo(); }\n");
		buf.append("}\n");
		ICompilationUnit cu2 = pack1.createCompilationUnit("F456.java", buf.toString(), false, null);

		assertEquals(false, cu1.isWorkingCopy());
		assertEquals(false, cu1.hasUnsavedChanges());
		assertEquals(false, cu2.isWorkingCopy());
		assertEquals(false, cu2.hasUnsavedChanges());
		assertNewProblemReported();
		assertEquals(0, getCacheSize());
		assertNewASTsCreated(0);

		openDocument(cu2, cu2.getSource(), 1);

		assertEquals(false, cu1.isWorkingCopy());
		assertEquals(false, cu1.hasUnsavedChanges());
		assertEquals(true, cu2.isWorkingCopy());
		assertEquals(false, cu2.hasUnsavedChanges());
		assertNewProblemReported(new ExpectedProblemReport(cu2, 1));
		assertEquals(0, getCacheSize());
		assertNewASTsCreated(1);

		openDocument(cu1, cu1.getSource(), 1);

		assertEquals(true, cu1.isWorkingCopy());
		assertEquals(false, cu1.hasUnsavedChanges());
		assertEquals(true, cu2.isWorkingCopy());
		assertEquals(false, cu2.hasUnsavedChanges());
		assertNewProblemReported(new ExpectedProblemReport(cu1, 0));
		assertEquals(0, getCacheSize());
		assertNewASTsCreated(2);

		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("public class F123 {\n");
		buf.append("  public static void foo() {}\n");
		buf.append("}\n");

		changeDocumentFull(cu1, buf.toString(), 2);

		assertEquals(true, cu1.isWorkingCopy());
		assertEquals(true, cu1.hasUnsavedChanges());
		assertEquals(true, cu2.isWorkingCopy());
		assertEquals(false, cu2.hasUnsavedChanges());
		assertNewProblemReported(new ExpectedProblemReport(cu1, 0));
		assertEquals(0, getCacheSize());
		assertNewASTsCreated(2);

		closeDocument(cu2);
		openDocument(cu2, cu2.getSource(), 1);
		assertNewProblemReported(new ExpectedProblemReport(cu2, 0));

		saveDocument(cu1);

		assertEquals(true, cu1.isWorkingCopy());
		assertEquals(false, cu1.hasUnsavedChanges());
		assertEquals(true, cu2.isWorkingCopy());
		assertEquals(false, cu2.hasUnsavedChanges());
		assertNewProblemReported();
		assertEquals(0, getCacheSize());
		assertNewASTsCreated(0);

		closeDocument(cu1);

		assertEquals(false, cu1.isWorkingCopy());
		assertEquals(false, cu1.hasUnsavedChanges());
		assertEquals(true, cu2.isWorkingCopy());
		assertEquals(false, cu2.hasUnsavedChanges());
		assertNewProblemReported();
		assertEquals(0, getCacheSize());
		assertNewASTsCreated(0);

		closeDocument(cu2);

		assertEquals(false, cu1.isWorkingCopy());
		assertEquals(false, cu1.hasUnsavedChanges());
		assertEquals(false, cu2.isWorkingCopy());
		assertEquals(false, cu2.hasUnsavedChanges());
		assertNewProblemReported();
		assertEquals(0, getCacheSize());
		assertNewASTsCreated(0);
	}

	@Test
	public void testDidOpenStandaloneFile() throws Exception {
		IJavaProject javaProject = newDefaultProject();
		IPackageFragmentRoot sourceFolder = javaProject.getPackageFragmentRoot(javaProject.getProject().getFolder("src"));
		IPackageFragment pack1 = sourceFolder.createPackageFragment("java", false, null);

		// @formatter:off
		String standaloneFileContent =
				"package java;\n"+
				"public class Foo extends UnknownType {"+
				"	public void method1(){\n"+
				"		super.whatever();"+
				"	}\n"+
				"}";
		// @formatter:on
		ICompilationUnit cu1 = pack1.createCompilationUnit("Foo.java", standaloneFileContent, false, null);

		openDocument(cu1, cu1.getSource(), 1);

		List<PublishDiagnosticsParams> diagnosticReports = getClientRequests("publishDiagnostics");
		assertEquals(1, diagnosticReports.size());
		PublishDiagnosticsParams diagParam = diagnosticReports.get(0);
		assertEquals(1, diagParam.getDiagnostics().size());
		Diagnostic d = diagParam.getDiagnostics().get(0);
		assertEquals("Foo.java is a non-project file, only syntax errors are reported", d.getMessage());
	}

	@Test
	public void testDidOpenNotOnClasspath() throws Exception {
		importProjects("eclipse/hello");
		IProject project = WorkspaceHelper.getProject("hello");
		URI uri = project.getFile("nopackage/Test2.java").getRawLocationURI();
		ICompilationUnit cu = JDTUtils.resolveCompilationUnit(uri);
		String source = Files.readString(FileUtils.toFile(uri.toURL()).toPath());
		openDocument(cu, source, 1);
		Job.getJobManager().join(DocumentLifeCycleHandler.DOCUMENT_LIFE_CYCLE_JOBS, monitor);
		assertEquals(project, cu.getJavaProject().getProject());
		assertEquals(source, cu.getSource());
		List<PublishDiagnosticsParams> diagnosticReports = getClientRequests("publishDiagnostics");
		assertEquals(1, diagnosticReports.size());
		PublishDiagnosticsParams diagParam = diagnosticReports.get(0);
		assertEquals(2, diagParam.getDiagnostics().size());
		closeDocument(cu);
		Job.getJobManager().join(DocumentLifeCycleHandler.DOCUMENT_LIFE_CYCLE_JOBS, monitor);
		diagnosticReports = getClientRequests("publishDiagnostics");
		assertEquals(2, diagnosticReports.size());
		diagParam = diagnosticReports.get(1);
		assertEquals(0, diagParam.getDiagnostics().size());
	}

	@Test
	public void testDidOpenStandaloneFileWithSyntaxError() throws Exception {
		IJavaProject javaProject = newDefaultProject();
		IPackageFragmentRoot sourceFolder = javaProject.getPackageFragmentRoot(javaProject.getProject().getFolder("src"));
		IPackageFragment pack1 = sourceFolder.createPackageFragment("java", false, null);

		// @formatter:off
		String standaloneFileContent =
				"package java;\n"+
				"public class Foo extends UnknownType {\n"+
				"	public void method1(){\n"+
				"		super.whatever()\n"+
				"	}\n"+
				"}";
		// @formatter:on

		ICompilationUnit cu1 = pack1.createCompilationUnit("Foo.java", standaloneFileContent, false, null);

		openDocument(cu1, cu1.getSource(), 1);

		List<PublishDiagnosticsParams> diagnosticReports = getClientRequests("publishDiagnostics");
		assertEquals(1, diagnosticReports.size());
		PublishDiagnosticsParams diagParam = diagnosticReports.get(0);
		assertEquals("Unexpected number of errors " + diagParam.getDiagnostics(), 2, diagParam.getDiagnostics().size());
		Diagnostic d = diagParam.getDiagnostics().get(0);
		assertEquals("Foo.java is a non-project file, only syntax errors are reported", d.getMessage());
		assertRange(0, 0, 1, d.getRange());
		d = diagParam.getDiagnostics().get(1);
		assertEquals("Syntax error, insert \";\" to complete BlockStatements", d.getMessage());
		assertRange(3, 17, 18, d.getRange());
	}

	@Test
	public void testDidOpenStandaloneFileWithNonSyntaxErrors() throws Exception {
		IJavaProject javaProject = newDefaultProject();
		IPackageFragmentRoot sourceFolder = javaProject.getPackageFragmentRoot(javaProject.getProject().getFolder("src"));
		IPackageFragment pack1 = sourceFolder.createPackageFragment("java", false, null);

		// @formatter:off
		String standaloneFileContent =
				"package java;\n"+
				"public class Foo {\n"+
				"	public static void notThis(){\n"+
				"		System.out.println(this);\n"+
				"	}\n"+
				"	public void method1(){\n"+
				"	}\n"+
				"	public void method1(){\n"+
				"	}\n"+
				"}";
		// @formatter:on

		ICompilationUnit cu1 = pack1.createCompilationUnit("Foo.java", standaloneFileContent, false, null);

		openDocument(cu1, cu1.getSource(), 1);

		List<PublishDiagnosticsParams> diagnosticReports = getClientRequests("publishDiagnostics");
		assertEquals(1, diagnosticReports.size());
		PublishDiagnosticsParams diagParam = diagnosticReports.get(0);

		assertEquals("Unexpected number of errors " + diagParam.getDiagnostics(), 4, diagParam.getDiagnostics().size());
		Diagnostic d = diagParam.getDiagnostics().get(0);
		assertEquals("Foo.java is a non-project file, only syntax errors are reported", d.getMessage());
		assertRange(0, 0, 1, d.getRange());

		d = diagParam.getDiagnostics().get(1);
		assertEquals("Cannot use this in a static context", d.getMessage());
		assertRange(3, 21, 25, d.getRange());

		d = diagParam.getDiagnostics().get(2);
		assertEquals("Duplicate method method1() in type Foo", d.getMessage());
		assertRange(5, 13, 22, d.getRange());

		d = diagParam.getDiagnostics().get(3);
		assertEquals("Duplicate method method1() in type Foo", d.getMessage());
		assertRange(7, 13, 22, d.getRange());
	}

	@Test
	public void testDidOpenLazyLoadingInvisibleProject() throws Exception {
		File standaloneFolder = copyFiles("singlefile/lesson1", true);
		IPath rootPath = org.eclipse.core.runtime.Path.fromOSString(standaloneFolder.getAbsolutePath());
		preferences.setRootPaths(Collections.singletonList(rootPath));
		when(preferenceManager.getPreferences()).thenReturn(preferences);
		IPath triggerFile = rootPath.append("src/org/samples/HelloWorld.java");
		URI fileURI = triggerFile.toFile().toURI();
		String projectName = ProjectUtils.getWorkspaceInvisibleProjectName(rootPath);

		assertFalse(ProjectUtils.getProject(projectName).exists());

		openDocument(fileURI.toString(), ResourceUtils.getContent(fileURI), 1);
		Job.getJobManager().join(DocumentLifeCycleHandler.DOCUMENT_LIFE_CYCLE_JOBS, monitor);

		assertTrue(ProjectUtils.getProject(projectName).exists());
		ICompilationUnit cu = JDTUtils.resolveCompilationUnit(fileURI);
		assertNotNull(cu);
		assertEquals(projectName, cu.getJavaProject().getProject().getName());
	}

	@Test
	public void testNotExpectedPackage() throws Exception {
		newDefaultProject();
		// @formatter:off
		String content =
				"package org;\n"+
				"public class Foo {"+
				"}";
		// @formatter:on
		temp = createTempFolder();
		File file = createTempFile(temp, "Foo.java", content);
		URI uri = file.toURI();
		ICompilationUnit cu = JDTUtils.resolveCompilationUnit(uri);
		openDocument(cu, cu.getSource(), 1);
		CompilationUnit astRoot = CoreASTProvider.getInstance().getAST(cu, CoreASTProvider.WAIT_YES, new NullProgressMonitor());
		IProblem[] problems = astRoot.getProblems();
		assertEquals("Unexpected number of errors", 0, problems.length);
		String source = cu.getSource();
		int length = source.length();
		source = source.replace("org", "org.eclipse");
		changeDocument(cu, source, 2, JDTUtils.toRange(cu, 0, length));
		Files.writeString(file.toPath(), source);
		saveDocument(cu);
		cu = JDTUtils.resolveCompilationUnit(uri);
		astRoot = CoreASTProvider.getInstance().getAST(cu, CoreASTProvider.WAIT_YES, new NullProgressMonitor());
		problems = astRoot.getProblems();
		assertEquals("Unexpected number of errors", 0, problems.length);
	}

	@Test
	public void testCreateCompilationUnit() throws Exception {
		IJavaProject javaProject = newEmptyProject();
		// @formatter:off
		String fooContent =
				"package org;\n"+
				"public class Foo {"+
				"}\n";
		String barContent =
				"package org;\n"+
				"public class Bar {\n"+
				"  Foo test() { return null; }\n" +
				"}\n";
		// @formatter:on
		IFolder src = javaProject.getProject().getFolder("src");
		javaProject.getPackageFragmentRoot(src);
		File sourceDirectory = src.getRawLocation().makeAbsolute().toFile();
		File org = new File(sourceDirectory, "org");
		org.mkdir();
		File file = new File(org, "Bar.java");
		file.createNewFile();
		Files.writeString(file.toPath(), barContent);
		ICompilationUnit bar = JDTUtils.resolveCompilationUnit(file.toURI());
		bar.getResource().refreshLocal(IResource.DEPTH_ONE, null);
		assertNotNull("Bar doesn't exist", javaProject.findType("org.Bar"));
		file = new File(org, "Foo.java");
		file.createNewFile();
		URI uri = file.toURI();
		ICompilationUnit unit = JDTUtils.resolveCompilationUnit(uri);
		openDocument(unit, "", 1);
		Files.writeString(file.toPath(), fooContent);
		changeDocumentFull(unit, fooContent, 1);
		saveDocument(unit);
		closeDocument(unit);
		CompilationUnit astRoot = sharedASTProvider.getAST(bar, CoreASTProvider.WAIT_YES, null);
		IProblem[] problems = astRoot.getProblems();
		assertEquals("Unexpected number of errors", 0, problems.length);
	}

	@Test
	public void testNotExpectedPackage2() throws Exception {
		newDefaultProject();
		// @formatter:off
		String content =
				"package org;\n"+
				"public class Foo {"+
				"}";
		// @formatter:on
		temp = createTempFolder();
		Path path = Paths.get(temp.getAbsolutePath(), "org", "eclipse");
		File file = createTempFile(path.toFile(), "Foo.java", content);
		URI uri = file.toURI();
		ICompilationUnit cu = JDTUtils.resolveCompilationUnit(uri);
		openDocument(cu, cu.getSource(), 1);
		CompilationUnit astRoot = CoreASTProvider.getInstance().getAST(cu, CoreASTProvider.WAIT_YES, new NullProgressMonitor());
		IProblem[] problems = astRoot.getProblems();
		assertEquals("Unexpected number of errors", 0, problems.length);

		String source = cu.getSource();
		int length = source.length();
		source = source.replace("org", "org.eclipse");
		changeDocument(cu, source, 2, JDTUtils.toRange(cu, 0, length));
		Files.writeString(file.toPath(), source);
		saveDocument(cu);
		cu = JDTUtils.resolveCompilationUnit(uri);
		astRoot = CoreASTProvider.getInstance().getAST(cu, CoreASTProvider.WAIT_YES, new NullProgressMonitor());
		problems = astRoot.getProblems();
		assertEquals("Unexpected number of errors", 0, problems.length);

		source = cu.getSource();
		length = source.length();
		source = source.replace("org.eclipse", "org.eclipse.toto");
		changeDocument(cu, source, 3, JDTUtils.toRange(cu, 0, length));
		Files.writeString(file.toPath(), source);
		saveDocument(cu);
		cu = JDTUtils.resolveCompilationUnit(uri);
		astRoot = CoreASTProvider.getInstance().getAST(cu, CoreASTProvider.WAIT_YES, new NullProgressMonitor());
		problems = astRoot.getProblems();
		assertEquals("Unexpected number of errors", 1, problems.length);
	}

	@Test
	public void testCloseMissingResource() throws Exception {
		IJavaProject javaProject = newEmptyProject();
		IPackageFragmentRoot sourceFolder = javaProject.getPackageFragmentRoot(javaProject.getProject().getFolder("src"));
		IPackageFragment pack1 = sourceFolder.createPackageFragment("test1", false, null);

		StringBuilder buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("public class E123 {\n");
		buf.append("    public boolean foo() {\n");
		buf.append("        return x;\n");
		buf.append("    }\n");
		buf.append("}\n");
		ICompilationUnit cu1 = pack1.createCompilationUnit("E123.java", buf.toString(), false, null);

		openDocument(cu1, cu1.getSource(), 1);

		assertEquals(true, cu1.isWorkingCopy());
		assertEquals(false, cu1.hasUnsavedChanges());
		assertNewProblemReported(new ExpectedProblemReport(cu1, 1));
		assertEquals(0, getCacheSize());
		assertNewASTsCreated(1);

		StringBuilder buf2 = new StringBuilder();
		buf2.append("package test1;\n");
		buf2.append("public class E123 {\n");
		buf2.append("    public boolean foo() {\n");
		buf2.append("        return true;\n");
		buf2.append("    }\n");
		buf2.append("}\n");
		changeDocumentFull(cu1, buf2.toString(), 2);
		File file = cu1.getResource().getRawLocation().toFile();
		boolean deleted = file.delete();
		assertTrue(file.getAbsolutePath() + " hasn't been deleted", deleted);
		closeDocument(cu1);

		assertEquals(false, cu1.isWorkingCopy());
		assertEquals(false, cu1.hasUnsavedChanges());
		assertNewProblemReported(new ExpectedProblemReport(cu1, 0));
		assertEquals(0, getCacheSize());
		assertNewASTsCreated(0);
	}

	@Test
	public void testDocumentMonitor() throws Exception {
		IJavaProject javaProject = newEmptyProject();
		IPackageFragmentRoot sourceFolder = javaProject.getPackageFragmentRoot(javaProject.getProject().getFolder("src"));
		IPackageFragment fooPackage = sourceFolder.createPackageFragment("foo", false, null);

		String content = "package foo;\n";
		ICompilationUnit cu = fooPackage.createCompilationUnit("Foo.java", content, false, null);

		openDocument(cu, content, 1);
		DocumentMonitor documentMonitor = lifeCycleHandler.new DocumentMonitor(JDTUtils.toURI(cu));
		documentMonitor.checkChanged();
		changeDocumentFull(cu, content, 2);
		assertChanged(documentMonitor);
		closeDocument(cu);
	}

	@Test
	public void testDocumentMonitorClosedDocument() throws Exception {
		IJavaProject javaProject = newEmptyProject();
		IPackageFragmentRoot sourceFolder = javaProject.getPackageFragmentRoot(javaProject.getProject().getFolder("src"));
		IPackageFragment fooPackage = sourceFolder.createPackageFragment("foo", false, null);

		String content = "package foo;\n";
		ICompilationUnit cu = fooPackage.createCompilationUnit("Foo.java", content, false, null);

		DocumentMonitor documentMonitorBeforeOpen = lifeCycleHandler.new DocumentMonitor(JDTUtils.toURI(cu));
		openDocument(cu, content, 1);
		changeDocumentFull(cu, content, 2);
		assertChanged(documentMonitorBeforeOpen); // Version changed (null -> 2)
		closeDocument(cu);

		DocumentMonitor documentMonitorAfterClose = lifeCycleHandler.new DocumentMonitor(JDTUtils.toURI(cu));
		documentMonitorAfterClose.checkChanged(); // Version not changed (null -> null)
	}

	@Test
	public void testFileRenameForTypeDeclaration() throws Exception {
		Mockito.lenient().when(clientPreferences.isWorkspaceApplyEditSupported()).thenReturn(true);
		IJavaProject javaProject = newEmptyProject();
		IPackageFragmentRoot sourceFolder = javaProject.getPackageFragmentRoot(javaProject.getProject().getFolder("src"));
		IPackageFragment pack1 = sourceFolder.createPackageFragment("test1", false, null);

		String content = "package test1;\n"
				+ "\n"
				+ "class Foo {\n"
				+ "}";

		ICompilationUnit cu1 = pack1.createCompilationUnit("Other.java", content, false, null);

		openDocument(cu1, cu1.getSource(), 1);
		saveDocument(cu1);

		assertEquals(0, getClientRequests("applyEdit").size());
		clientRequests.clear();
		closeDocument(cu1);

		content = "package test1;\n"
				+ "\n"
				+ "public class Foo {\n"
				+ "}";

		cu1 = pack1.createCompilationUnit("Other.java", content, true, null);

		openDocument(cu1, cu1.getSource(), 1);
		saveDocument(cu1);

		// RenameFile operation through 'workspace/applyEdit'
		assertEquals(1, getClientRequests("applyEdit").size());
		clientRequests.clear();
		closeDocument(cu1);

		content = "package test1;\n"
				+ "\n"
				+ "public interface Foo {\n"
				+ "}\n"
				+ "\n"
				+ "class Bar {\n"
				+ "}\n"
				+ "\n"
				+ "class Biz {\n"
				+ "}";

		cu1 = pack1.createCompilationUnit("Other.java", content, true, null);

		openDocument(cu1, cu1.getSource(), 1);
		saveDocument(cu1);

		// RenameFile operation through 'workspace/applyEdit'
		assertEquals(1, getClientRequests("applyEdit").size());
		clientRequests.clear();
		closeDocument(cu1);

		content = "public class Foo {\n"
				+ "}\n"
				+ "\n"
				+ "public class Bar {\n"
				+ "}\n"
				+ "\n"
				+ "public class Biz {\n"
				+ "}";

		cu1 = pack1.createCompilationUnit("Other.java", content, true, null);

		openDocument(cu1, cu1.getSource(), 1);
		saveDocument(cu1);

		assertEquals(0, getClientRequests("applyEdit").size());
		clientRequests.clear();
		closeDocument(cu1);
	}

	private void assertChanged(DocumentMonitor documentMonitor) {
		try {
			documentMonitor.checkChanged();
			fail("Should have thrown ResponseErrorException");
		}
		catch (ResponseErrorException e) {
			assertEquals(e.getResponseError().getCode(), -32801); // ContentModified error code
		}
	}

	private File createTempFile(File parent, String fileName, String content) throws IOException {
		parent.mkdirs();
		File file = new File(parent, fileName);
		file.deleteOnExit();
		file.createNewFile();
		Files.writeString(file.toPath(), content);
		return file;
	}

	private File createTempFolder() throws IOException {
		File temp;
		temp = File.createTempFile("temp", Long.toString(System.nanoTime()));
		temp.delete();
		temp.mkdirs();
		temp.deleteOnExit();
		return temp;
	}

	@SuppressWarnings("unchecked")
	private <T> List<T> getClientRequests(String name) {
		List<?> requests = clientRequests.get(name);
		return requests != null ? (List<T>) requests : Collections.emptyList();
	}

	private void openDocument(ICompilationUnit cu, String content, int version) {
		openDocument(JDTUtils.toURI(cu), content, version);
	}

	private void openDocument(String uri, String content, int version) {
		DidOpenTextDocumentParams openParms = new DidOpenTextDocumentParams();
		TextDocumentItem textDocument = new TextDocumentItem();
		textDocument.setLanguageId("java");
		textDocument.setText(content);
		textDocument.setUri(uri);
		textDocument.setVersion(version);
		openParms.setTextDocument(textDocument);
		lifeCycleHandler.didOpen(openParms);
	}

	private void changeDocumentIncrementally(ICompilationUnit cu, String content, int version, int offset, int length) throws JavaModelException {
		Range range = JDTUtils.toRange(cu, offset, length);
		changeDocument(cu, content, version, range);
	}

	private void changeDocumentFull(ICompilationUnit cu, String content, int version) throws JavaModelException {
		changeDocument(cu, content, version, null);
	}

	private void changeDocument(ICompilationUnit cu, String content, int version, Range range) throws JavaModelException {
		DidChangeTextDocumentParams changeParms = new DidChangeTextDocumentParams();
		VersionedTextDocumentIdentifier textDocument = new VersionedTextDocumentIdentifier();
		textDocument.setUri(JDTUtils.toURI(cu));
		textDocument.setVersion(version);
		changeParms.setTextDocument(textDocument);
		TextDocumentContentChangeEvent event = new TextDocumentContentChangeEvent();
		if (range != null) {
			event.setRange(range);
		}
		event.setText(content);
		List<TextDocumentContentChangeEvent> contentChanges = new ArrayList<>();
		contentChanges.add(event);
		changeParms.setContentChanges(contentChanges);
		lifeCycleHandler.didChange(changeParms);
	}

	private void saveDocument(ICompilationUnit cu) throws Exception {
		DidSaveTextDocumentParams saveParms = new DidSaveTextDocumentParams();
		TextDocumentIdentifier textDocument = new TextDocumentIdentifier();
		textDocument.setUri(JDTUtils.toURI(cu));
		saveParms.setTextDocument(textDocument);
		saveParms.setText(cu.getSource());
		lifeCycleHandler.didSave(saveParms);
		DocumentLifeCycleHandler.handleFileRenameForTypeDeclaration(JDTUtils.toURI(cu));
		waitForBackgroundJobs();
	}

	private void closeDocument(ICompilationUnit cu) {
		DidCloseTextDocumentParams closeParms = new DidCloseTextDocumentParams();
		TextDocumentIdentifier textDocument = new TextDocumentIdentifier();
		textDocument.setUri(JDTUtils.toURI(cu));
		closeParms.setTextDocument(textDocument);
		lifeCycleHandler.didClose(closeParms);
	}

	class ExpectedProblemReport {
		ICompilationUnit cu;
		int problemCount;

		ExpectedProblemReport(ICompilationUnit cu, int problemCount) {
			this.cu = cu;
			this.problemCount = problemCount;
		}

	}

	private void assertNewProblemReported(ExpectedProblemReport... expectedReports) {
		List<PublishDiagnosticsParams> diags = getClientRequests("publishDiagnostics");
		assertEquals(expectedReports.length, diags.size());

		for (ExpectedProblemReport expected : expectedReports) {
			String uri = JDTUtils.toURI(expected.cu);
			List<PublishDiagnosticsParams> filteredList =
					diags.stream().filter(d -> d.getUri().equals(uri)).collect(Collectors.toList());
			assertTrue(filteredList.size() == 1);
			PublishDiagnosticsParams diag = filteredList.get(0);
			if (expected.problemCount != diag.getDiagnostics().size()) {
				String message = "";
				for (Diagnostic d : diag.getDiagnostics()) {
					message += d.getMessage() + ", ";
				}
				assertEquals(message, expected.problemCount, diag.getDiagnostics().size());
			}
		}
		diags.clear();
	}

	private void assertNewASTsCreated(int expected) {
		//		assertEquals(expected, sharedASTProvider.getASTCreationCount());
		//		sharedASTProvider.clearASTCreationCount();
	}

	private int getCacheSize() {
		return (sharedASTProvider.getCachedAST() != null) ? 1 : 0;
	}

	@Test
	public void testDiagnosticsOnExternalFileWithInternalProject() throws Exception {
		Mockito.lenient().when(clientPreferences.skipProjectConfiguration()).thenReturn(true);
		preferences.setValidateAllOpenBuffersOnChanges(true);
		when(preferenceManager.getPreferences()).thenReturn(preferences);
		Path filePath = Files.createTempDirectory("testDiagnosticsOnExternalFileWithInternalProject").resolve("A.java");
		try {
			String content = "error public class A { }";
			Files.writeString(filePath, content);
			lifeCycleHandler.didOpen(new DidOpenTextDocumentParams(new TextDocumentItem(filePath.toUri().toString(), "java", 0, content)));
			List<PublishDiagnosticsParams> diagnosticReports = getClientRequests("publishDiagnostics");
			assertFalse("No diagnostics sent on open", diagnosticReports.isEmpty());
			List<Diagnostic> diagnostics = diagnosticReports.get(0).getDiagnostics();
			assertTrue("First diagnostics not sent", diagnostics.stream().map(Diagnostic::getMessage).anyMatch(message -> message.contains("Syntax error")));
			lifeCycleHandler.didChange(new DidChangeTextDocumentParams(new VersionedTextDocumentIdentifier(filePath.toUri().toString(), 0), List.of(new TextDocumentContentChangeEvent(new Range(new Position(0, 0), new Position(0, 0)), "another"))));
			diagnosticReports = getClientRequests("publishDiagnostics");
			assertEquals("No diagnostics sent on change", 2, diagnosticReports.size());
			diagnostics = diagnosticReports.get(1).getDiagnostics();
			assertTrue("diagnostics not updated upon edit", diagnostics.stream().map(Diagnostic::getMessage).anyMatch(message -> message.contains("Syntax error")));
		} finally {
			Files.delete(filePath);
			Files.delete(filePath.getParent());
		}
	}
}
