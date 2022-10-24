/*******************************************************************************
 * Copyright (c) 2021 Red Hat Inc. and others.
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
package org.eclipse.jdt.ls.core.internal.correction;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Hashtable;
import java.util.List;

import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IPackageFragment;
import org.eclipse.jdt.core.IPackageFragmentRoot;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.compiler.IProblem;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.manipulation.CoreASTProvider;
import org.eclipse.jdt.ls.core.internal.JDTUtils;
import org.eclipse.jdt.ls.core.internal.JavaClientConnection;
import org.eclipse.jdt.ls.core.internal.JavaLanguageServerPlugin;
import org.eclipse.jdt.ls.core.internal.handlers.CodeActionHandler;
import org.eclipse.jdt.ls.core.internal.handlers.DiagnosticsHandler;
import org.eclipse.jdt.ls.core.internal.handlers.JDTLanguageServer;
import org.eclipse.jdt.ls.core.internal.preferences.Preferences;
import org.eclipse.lsp4j.CodeAction;
import org.eclipse.lsp4j.CodeActionContext;
import org.eclipse.lsp4j.CodeActionKind;
import org.eclipse.lsp4j.CodeActionParams;
import org.eclipse.lsp4j.Command;
import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

public class ShowAllQuickFixTest extends AbstractQuickFixTest {
	private IJavaProject fJProject1;
	private IPackageFragmentRoot fSourceFolder;
	private JavaClientConnection javaClient;

	@Before
	public void setup() throws Exception {
		javaClient = new JavaClientConnection(client);
		mockJDTLanguageServer();
		JavaLanguageServerPlugin.getInstance().getProtocol().connectClient(client);
		fJProject1 = newEmptyProject();
		Hashtable<String, String> options = TestOptions.getDefaultOptions();
		options.put(JavaCore.COMPILER_PB_UNUSED_PRIVATE_MEMBER, JavaCore.ERROR);
		options.put(JavaCore.COMPILER_PB_DEAD_CODE, JavaCore.WARNING);
		fJProject1.setOptions(options);
		fSourceFolder = fJProject1.getPackageFragmentRoot(fJProject1.getProject().getFolder("src"));
		preferenceManager.getPreferences().setJavaQuickFixShowAt("problem");
	}

	private void mockJDTLanguageServer() {
		JDTLanguageServer server = Mockito.mock(JDTLanguageServer.class);
		Mockito.when(server.getClientConnection()).thenReturn(this.javaClient);
		JavaLanguageServerPlugin.getInstance().setProtocol(server);
	}

	@After
	@Override
	public void cleanUp() throws Exception {
		for (ICompilationUnit cu : JavaCore.getWorkingCopies(null)) {
			cu.discardWorkingCopy();
		}
		super.cleanUp();
		javaClient.disconnect();
		Job.getJobManager().setProgressProvider(null);
		JavaLanguageServerPlugin.getInstance().setProtocol(null);
	}

	// preferenceManager.getPreferences().setJavaQuickFixShowAt(Preferences.LINE);
	@Test
	public void testHandledProblems() throws Exception {
		String showQuickFixes = preferenceManager.getPreferences().getJavaQuickFixShowAt();
		try {
			preferenceManager.getPreferences().setJavaQuickFixShowAt(Preferences.LINE);
			IPackageFragment pack1 = fSourceFolder.createPackageFragment("test1", false, null);
			StringBuilder buf = new StringBuilder();
			buf.append("package test1;\n");
			buf.append("import java.util.List;\n");
			buf.append("public class F implements List {\n");
			buf.append("}\n");
			ICompilationUnit cu = pack1.createCompilationUnit("F.java", buf.toString(), true, null);
			CodeActionParams codeActionParams = new CodeActionParams();
			TextDocumentIdentifier textDocument = new TextDocumentIdentifier();
			textDocument.setUri(JDTUtils.toURI(cu));
			codeActionParams.setTextDocument(textDocument);
			codeActionParams.setRange(new Range(new Position(2, 13), new Position(2, 16)));
			CodeActionContext context = new CodeActionContext();
			CompilationUnit astRoot = CoreASTProvider.getInstance().getAST(cu, CoreASTProvider.WAIT_YES, null);
			List<Diagnostic> diagnostics = getDiagnostics(cu, astRoot, 3);
			context.setDiagnostics(diagnostics);
			context.setOnly(Arrays.asList(CodeActionKind.QuickFix));
			codeActionParams.setContext(context);
			List<Either<Command, CodeAction>> codeActions = new CodeActionHandler(this.preferenceManager).getCodeActionCommands(codeActionParams, new NullProgressMonitor());
			assertEquals(1, codeActions.size());
		} finally {
			preferenceManager.getPreferences().setJavaQuickFixShowAt(showQuickFixes);
		}
	}

	@Test
	public void testShowAt() throws Exception {
		String showQuickFixes = preferenceManager.getPreferences().getJavaQuickFixShowAt();
		try {
			IPackageFragment pack1 = fSourceFolder.createPackageFragment("test1", false, null);
			StringBuilder buf = new StringBuilder();
			buf.append("package test1;\n");
			buf.append("import java.math.BigDecimal;\n");
			buf.append("public class F {\n");
			buf.append("    private int i; private BigDecimal b;private void test() {}\n");
			buf.append("    public static void main(String[] args) {\n");
			buf.append("        System.out.println(greeting());\n");
			buf.append("    }\n");
			buf.append("}\n");
			ICompilationUnit cu = pack1.createCompilationUnit("F.java", buf.toString(), true, null);
			cu.becomeWorkingCopy(null);
			CodeActionParams codeActionParams = new CodeActionParams();
			TextDocumentIdentifier textDocument = new TextDocumentIdentifier();
			textDocument.setUri(JDTUtils.toURI(cu));
			codeActionParams.setTextDocument(textDocument);
			codeActionParams.setRange(new Range(new Position(3, 9), new Position(3, 9)));
			CodeActionContext context = new CodeActionContext();
			context.setDiagnostics(Collections.emptyList());
			context.setOnly(Arrays.asList(CodeActionKind.QuickFix));
			codeActionParams.setContext(context);
			List<Either<Command, CodeAction>> codeActions = new CodeActionHandler(this.preferenceManager).getCodeActionCommands(codeActionParams, new NullProgressMonitor());
			assertEquals(0, codeActions.size());
			codeActionParams.setRange(new Range(new Position(3, 4), new Position(3, 40)));
			codeActions = new CodeActionHandler(this.preferenceManager).getCodeActionCommands(codeActionParams, new NullProgressMonitor());
			assertEquals(0, codeActions.size());
			codeActionParams.setRange(new Range(new Position(5, 1), new Position(5, 1)));
			codeActions = new CodeActionHandler(this.preferenceManager).getCodeActionCommands(codeActionParams, new NullProgressMonitor());
			assertEquals(0, codeActions.size());
			preferenceManager.getPreferences().setJavaQuickFixShowAt(Preferences.LINE);
			codeActionParams.setRange(new Range(new Position(3, 9), new Position(3, 9)));
			CompilationUnit astRoot = CoreASTProvider.getInstance().getAST(cu, CoreASTProvider.WAIT_YES, null);
			cu.makeConsistent(null);
			List<Diagnostic> diagnostics = getDiagnostics(cu, astRoot, 4);
			context.setDiagnostics(diagnostics);
			codeActions = new CodeActionHandler(this.preferenceManager).getCodeActionCommands(codeActionParams, new NullProgressMonitor());
			assertEquals(2, codeActions.size());
			codeActionParams.setRange(new Range(new Position(3, 4), new Position(3, 40)));
			codeActions = new CodeActionHandler(this.preferenceManager).getCodeActionCommands(codeActionParams, new NullProgressMonitor());
			assertEquals(2, codeActions.size());
			codeActionParams.setRange(new Range(new Position(5, 1), new Position(5, 1)));
			diagnostics = getDiagnostics(cu, astRoot, 6);
			context.setDiagnostics(diagnostics);
			codeActions = new CodeActionHandler(this.preferenceManager).getCodeActionCommands(codeActionParams, new NullProgressMonitor());
			assertEquals(1, codeActions.size());
			CodeAction greeting = codeActions.get(0).getRight();
			assertNotNull(greeting);
			assertEquals("Create method 'greeting()'", greeting.getTitle());
		} finally {
			preferenceManager.getPreferences().setJavaQuickFixShowAt(showQuickFixes);
		}
	}

	// https://github.com/redhat-developer/vscode-java/issues/2236
	@Test
	public void testDuplicateQuickFix() throws Exception {
		String showQuickFixes = preferenceManager.getPreferences().getJavaQuickFixShowAt();
		try {
			IPackageFragment pack1 = fSourceFolder.createPackageFragment("test1", false, null);
			StringBuilder buf = new StringBuilder();
			buf.append("package test1;\n");
			buf.append("public class F {\n");
			buf.append("    List list = List.of();\n");
			buf.append("}\n");
			ICompilationUnit cu = pack1.createCompilationUnit("F.java", buf.toString(), true, null);
			cu.becomeWorkingCopy(null);
			preferenceManager.getPreferences().setJavaQuickFixShowAt(Preferences.LINE);
			CodeActionParams codeActionParams = new CodeActionParams();
			TextDocumentIdentifier textDocument = new TextDocumentIdentifier();
			textDocument.setUri(JDTUtils.toURI(cu));
			codeActionParams.setTextDocument(textDocument);
			codeActionParams.setRange(new Range(new Position(2, 6), new Position(2, 6)));
			CodeActionContext context = new CodeActionContext();
			CompilationUnit astRoot = CoreASTProvider.getInstance().getAST(cu, CoreASTProvider.WAIT_YES, null);
			List<Diagnostic> diagnostics = getDiagnostics(cu, astRoot, 3);
			context.setDiagnostics(diagnostics);
			context.setOnly(Arrays.asList(CodeActionKind.QuickFix));
			codeActionParams.setContext(context);
			List<Either<Command, CodeAction>> codeActions = new CodeActionHandler(this.preferenceManager).getCodeActionCommands(codeActionParams, new NullProgressMonitor());
			assertEquals(6, codeActions.size());
			CodeAction action = codeActions.get(0).getRight();
			assertNotNull(action);
			assertEquals("Import 'List' (java.util)", action.getTitle());
			action = codeActions.get(1).getRight();
			assertNotNull(action);
			assertNotEquals("Import 'List' (java.util)", action.getTitle());
		} finally {
			preferenceManager.getPreferences().setJavaQuickFixShowAt(showQuickFixes);
		}
	}

	// https://github.com/redhat-developer/vscode-java/issues/2339
	@Test
	public void testDuplicateQuickFix2() throws Exception {
		String showQuickFixes = preferenceManager.getPreferences().getJavaQuickFixShowAt();
		try {
			IPackageFragment pack1 = fSourceFolder.createPackageFragment("test1", false, null);
			StringBuilder buf = new StringBuilder();
			buf.append("package test1;\n");
			buf.append("import java.util.List;\n");
			buf.append("public class Foo {\n");
			buf.append("    public void foo(List<Integer> list) {\n");
			buf.append("        for (element: list) {");
			buf.append("        }\n");
			buf.append("    }\n");
			buf.append("}\n");
			ICompilationUnit cu = pack1.createCompilationUnit("Foo.java", buf.toString(), true, null);
			cu.becomeWorkingCopy(null);
			preferenceManager.getPreferences().setJavaQuickFixShowAt(Preferences.LINE);
			CodeActionParams codeActionParams = new CodeActionParams();
			TextDocumentIdentifier textDocument = new TextDocumentIdentifier();
			textDocument.setUri(JDTUtils.toURI(cu));
			codeActionParams.setTextDocument(textDocument);
			codeActionParams.setRange(new Range(new Position(4, 13), new Position(4, 20)));
			CodeActionContext context = new CodeActionContext();
			CompilationUnit astRoot = CoreASTProvider.getInstance().getAST(cu, CoreASTProvider.WAIT_YES, null);
			List<Diagnostic> diagnostics = getDiagnostics(cu, astRoot, 5);
			context.setDiagnostics(diagnostics);
			context.setOnly(Arrays.asList(CodeActionKind.QuickFix));
			codeActionParams.setContext(context);
			List<Either<Command, CodeAction>> codeActions = new CodeActionHandler(this.preferenceManager).getCodeActionCommands(codeActionParams, new NullProgressMonitor());
			assertEquals(10, codeActions.size());
			CodeAction action = codeActions.get(0).getRight();
			assertNotNull(action);
			assertEquals("Create loop variable 'element'", action.getTitle());
			action = codeActions.get(1).getRight();
			assertNotNull(action);
			assertNotEquals("Create loop variable 'element'", action.getTitle());
		} finally {
			preferenceManager.getPreferences().setJavaQuickFixShowAt(showQuickFixes);
		}
	}

	private List<Diagnostic> getDiagnostics(ICompilationUnit cu, CompilationUnit astRoot, int line) {
		List<IProblem> problems = new ArrayList<>();
		for (IProblem problem : astRoot.getProblems()) {
			if (problem.getSourceLineNumber() == line) {
				problems.add(problem);
			}
		}
		List<Diagnostic> diagnostics = DiagnosticsHandler.toDiagnosticsArray(cu, problems, true);
		return diagnostics;
	}

}
