/*******************************************************************************
 * Copyright (c) 2017 Red Hat Inc. and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Red Hat Inc. - initial API and implementation
 *******************************************************************************/
package org.eclipse.jdt.ls.core.internal.handlers;

import static org.eclipse.jdt.ls.core.internal.Lsp4jAssertions.assertRange;
import static org.junit.Assert.assertEquals;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IPackageFragment;
import org.eclipse.jdt.core.IPackageFragmentRoot;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.ls.core.internal.JDTUtils;
import org.eclipse.jdt.ls.core.internal.JavaClientConnection;
import org.eclipse.jdt.ls.core.internal.JavaClientConnection.JavaLanguageClient;
import org.eclipse.jdt.ls.core.internal.SharedASTProvider;
import org.eclipse.jdt.ls.core.internal.managers.AbstractProjectsManagerBasedTest;
import org.eclipse.jdt.ls.core.internal.preferences.Preferences;
import org.eclipse.jdt.ls.core.internal.preferences.Preferences.Severity;
import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.DidChangeTextDocumentParams;
import org.eclipse.lsp4j.DidCloseTextDocumentParams;
import org.eclipse.lsp4j.DidOpenTextDocumentParams;
import org.eclipse.lsp4j.DidSaveTextDocumentParams;
import org.eclipse.lsp4j.PublishDiagnosticsParams;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.TextDocumentContentChangeEvent;
import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.eclipse.lsp4j.TextDocumentItem;
import org.eclipse.lsp4j.VersionedTextDocumentIdentifier;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;
import org.mockito.runners.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class DocumentLifeCycleHandlerTest extends AbstractProjectsManagerBasedTest {

	private SharedASTProvider sharedASTProvider;
	private Map<String, List<Object>> clientRequests = new HashMap<>();
	private JavaLanguageClient client=(JavaLanguageClient)Proxy.newProxyInstance(getClass().getClassLoader(),new Class[]{JavaLanguageClient.class},new InvocationHandler(){

		@Override
		public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
			if (args.length == 1) {
				String name = method.getName();
				List<Object> params = clientRequests.get(name);
				if (params == null) {
					params = new ArrayList<>();
					clientRequests.put(name, params);
				}
				params.add(args[0]);
			}
			return null;
		}
	});

	private DocumentLifeCycleHandler lifeCycleHandler;

	@Before
	public void setup() throws Exception {
		mockPreferences();
		clientRequests.clear();

		sharedASTProvider = SharedASTProvider.getInstance();
		sharedASTProvider.invalidateAll();
		sharedASTProvider.clearASTCreationCount();

		lifeCycleHandler = new DocumentLifeCycleHandler(new JavaClientConnection(client), preferenceManager, projectsManager, false);
	}

	@After
	public void tearDown() throws Exception {
		for (ICompilationUnit cu : JavaCore.getWorkingCopies(null)) {
			cu.discardWorkingCopy();
		}
	}

	private Preferences mockPreferences() {
		Preferences mockPreferences = Mockito.mock(Preferences.class);
		Mockito.when(preferenceManager.getPreferences()).thenReturn(mockPreferences);
		Mockito.when(preferenceManager.getPreferences(Mockito.any())).thenReturn(mockPreferences);
		Mockito.when(mockPreferences.getIncompleteClasspathSeverity()).thenReturn(Severity.ignore);
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
		assertEquals(0, sharedASTProvider.getCacheSize());
		assertNewASTsCreated(0);

		openDocument(cu1, cu1.getSource(), 1);

		assertEquals(true, cu1.isWorkingCopy());
		assertEquals(false, cu1.hasUnsavedChanges());
		assertNewProblemReported(new ExpectedProblemReport(cu1, 0));
		assertEquals(1, sharedASTProvider.getCacheSize());
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
		assertEquals(1, sharedASTProvider.getCacheSize());
		assertNewASTsCreated(1);

		saveDocument(cu1);

		assertEquals(true, cu1.isWorkingCopy());
		assertEquals(false, cu1.hasUnsavedChanges());
		assertNewProblemReported();
		assertEquals(1, sharedASTProvider.getCacheSize());
		assertNewASTsCreated(0);

		closeDocument(cu1);

		assertEquals(false, cu1.isWorkingCopy());
		assertEquals(false, cu1.hasUnsavedChanges());
		assertNewProblemReported();
		assertEquals(0, sharedASTProvider.getCacheSize());
		assertNewASTsCreated(0);
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
		assertEquals(0, sharedASTProvider.getCacheSize());
		assertNewASTsCreated(0);

		openDocument(cu1, cu1.getSource(), 1);

		assertEquals(true, cu1.isWorkingCopy());
		assertEquals(false, cu1.hasUnsavedChanges());
		assertNewProblemReported(new ExpectedProblemReport(cu1, 0));
		assertEquals(1, sharedASTProvider.getCacheSize());
		assertNewASTsCreated(1);

		buf = new StringBuilder();
		buf.append(TO_BE_CHANGED_PART);
		buf.append("  X x;\n");

		changeDocumentIncrementally(cu1, buf.toString(), 2, BEGIN_PART.length(), TO_BE_CHANGED_PART.length());

		assertEquals(true, cu1.isWorkingCopy());
		assertEquals(true, cu1.hasUnsavedChanges());
		assertNewProblemReported(new ExpectedProblemReport(cu1, 1));
		assertEquals(1, sharedASTProvider.getCacheSize());
		assertNewASTsCreated(1);

		saveDocument(cu1);

		assertEquals(true, cu1.isWorkingCopy());
		assertEquals(false, cu1.hasUnsavedChanges());
		assertNewProblemReported();
		assertEquals(1, sharedASTProvider.getCacheSize());
		assertNewASTsCreated(0);

		closeDocument(cu1);

		assertEquals(false, cu1.isWorkingCopy());
		assertEquals(false, cu1.hasUnsavedChanges());
		assertNewProblemReported();
		assertEquals(0, sharedASTProvider.getCacheSize());
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
		assertEquals(0, sharedASTProvider.getCacheSize());
		assertNewASTsCreated(0);

		openDocument(cu2, cu2.getSource(), 1);

		assertEquals(false, cu1.isWorkingCopy());
		assertEquals(false, cu1.hasUnsavedChanges());
		assertEquals(true, cu2.isWorkingCopy());
		assertEquals(false, cu2.hasUnsavedChanges());
		assertNewProblemReported(new ExpectedProblemReport(cu2, 1));
		assertEquals(1, sharedASTProvider.getCacheSize());
		assertNewASTsCreated(1);

		openDocument(cu1, cu1.getSource(), 1);

		assertEquals(true, cu1.isWorkingCopy());
		assertEquals(false, cu1.hasUnsavedChanges());
		assertEquals(true, cu2.isWorkingCopy());
		assertEquals(false, cu2.hasUnsavedChanges());
		assertNewProblemReported(new ExpectedProblemReport(cu2, 1), new ExpectedProblemReport(cu1, 0));
		assertEquals(2, sharedASTProvider.getCacheSize());
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
		assertNewProblemReported(new ExpectedProblemReport(cu2, 0), new ExpectedProblemReport(cu1, 0));
		assertEquals(2, sharedASTProvider.getCacheSize());
		assertNewASTsCreated(2);

		saveDocument(cu1);

		assertEquals(true, cu1.isWorkingCopy());
		assertEquals(false, cu1.hasUnsavedChanges());
		assertEquals(true, cu2.isWorkingCopy());
		assertEquals(false, cu2.hasUnsavedChanges());
		assertNewProblemReported();
		assertEquals(2, sharedASTProvider.getCacheSize());
		assertNewASTsCreated(0);

		closeDocument(cu1);

		assertEquals(false, cu1.isWorkingCopy());
		assertEquals(false, cu1.hasUnsavedChanges());
		assertEquals(true, cu2.isWorkingCopy());
		assertEquals(false, cu2.hasUnsavedChanges());
		assertNewProblemReported();
		assertEquals(1, sharedASTProvider.getCacheSize());
		assertNewASTsCreated(0);

		closeDocument(cu2);

		assertEquals(false, cu1.isWorkingCopy());
		assertEquals(false, cu1.hasUnsavedChanges());
		assertEquals(false, cu2.isWorkingCopy());
		assertEquals(false, cu2.hasUnsavedChanges());
		assertNewProblemReported();
		assertEquals(0, sharedASTProvider.getCacheSize());
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
		assertEquals("Unexpected number of errors " + diagParam.getDiagnostics(), 1, diagParam.getDiagnostics().size());
		Diagnostic d = diagParam.getDiagnostics().get(0);
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

		assertEquals("Unexpected number of errors " + diagParam.getDiagnostics(), 3, diagParam.getDiagnostics().size());
		Diagnostic d = diagParam.getDiagnostics().get(0);
		assertEquals("Cannot use this in a static context", d.getMessage());
		assertRange(3, 21, 25, d.getRange());

		d = diagParam.getDiagnostics().get(1);
		assertEquals("Duplicate method method1() in type Foo", d.getMessage());
		assertRange(5, 13, 22, d.getRange());

		d = diagParam.getDiagnostics().get(2);
		assertEquals("Duplicate method method1() in type Foo", d.getMessage());
		assertRange(7, 13, 22, d.getRange());
	}

	@SuppressWarnings("unchecked")
	private <T> List<T> getClientRequests(String name) {
		List<?> requests = clientRequests.get(name);
		return requests != null ? (List<T>) requests : Collections.emptyList();
	}

	private void openDocument(ICompilationUnit cu, String content, int version) {
		DidOpenTextDocumentParams openParms = new DidOpenTextDocumentParams();
		TextDocumentItem textDocument = new TextDocumentItem();
		textDocument.setLanguageId("java");
		textDocument.setText(content);
		textDocument.setUri(JDTUtils.getFileURI(cu));
		textDocument.setVersion(version);
		openParms.setTextDocument(textDocument);
		lifeCycleHandler.didOpen(openParms);
	}

	private void changeDocumentIncrementally(ICompilationUnit cu, String content, int version, int offset, int length) throws JavaModelException {
		Range range = JDTUtils.toRange(cu, offset, length);
		changeDocument(cu, content, version, range, length);
	}

	private void changeDocumentFull(ICompilationUnit cu, String content, int version) throws JavaModelException {
		changeDocument(cu, content, version, null, 0);
	}

	private void changeDocument(ICompilationUnit cu, String content, int version, Range range, int length) throws JavaModelException {
		DidChangeTextDocumentParams changeParms = new DidChangeTextDocumentParams();
		VersionedTextDocumentIdentifier textDocument = new VersionedTextDocumentIdentifier();
		textDocument.setUri(JDTUtils.getFileURI(cu));
		textDocument.setVersion(version);
		changeParms.setTextDocument(textDocument);
		TextDocumentContentChangeEvent event = new TextDocumentContentChangeEvent();
		if (range != null) {
			event.setRange(range);
			event.setRangeLength(length);
		}
		event.setText(content);
		List<TextDocumentContentChangeEvent> contentChanges = new ArrayList<>();
		contentChanges.add(event);
		changeParms.setContentChanges(contentChanges);
		lifeCycleHandler.didChange(changeParms);
	}

	private void saveDocument(ICompilationUnit cu) throws JavaModelException {
		DidSaveTextDocumentParams saveParms = new DidSaveTextDocumentParams();
		TextDocumentIdentifier textDocument = new TextDocumentIdentifier();
		textDocument.setUri(JDTUtils.getFileURI(cu));
		saveParms.setTextDocument(textDocument);
		saveParms.setText(cu.getSource());
		lifeCycleHandler.didSave(saveParms);
	}

	private void closeDocument(ICompilationUnit cu) {
		DidCloseTextDocumentParams closeParms = new DidCloseTextDocumentParams();
		TextDocumentIdentifier textDocument = new TextDocumentIdentifier();
		textDocument.setUri(JDTUtils.getFileURI(cu));
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

		for (int i = 0; i < expectedReports.length; i++) {
			PublishDiagnosticsParams diag = diags.get(i);
			ExpectedProblemReport expected = expectedReports[i];
			assertEquals(JDTUtils.getFileURI(expected.cu), diag.getUri());
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
		assertEquals(expected, sharedASTProvider.getASTCreationCount());
		sharedASTProvider.clearASTCreationCount();
	}
}
