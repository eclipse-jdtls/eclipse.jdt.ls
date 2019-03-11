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

import java.net.URI;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.compiler.IProblem;
import org.eclipse.jdt.ls.core.internal.JDTUtils;
import org.eclipse.jdt.ls.core.internal.JavaClientConnection;
import org.eclipse.jdt.ls.core.internal.JavaCodeActionKind;
import org.eclipse.jdt.ls.core.internal.LanguageServerWorkingCopyOwner;
import org.eclipse.jdt.ls.core.internal.WorkspaceHelper;
import org.eclipse.lsp4j.CodeAction;
import org.eclipse.lsp4j.CodeActionContext;
import org.eclipse.lsp4j.CodeActionKind;
import org.eclipse.lsp4j.CodeActionParams;
import org.eclipse.lsp4j.Command;
import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.DiagnosticSeverity;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.eclipse.lsp4j.WorkspaceEdit;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

/**
 * @author Gorkem Ercan
 *
 */
@RunWith(MockitoJUnitRunner.class)
public class CodeActionHandlerTest extends AbstractCompilationUnitBasedTest {

	@Mock
	private JavaClientConnection connection;


	@Override
	@Before
	public void setup() throws Exception{
		importProjects("eclipse/hello");
		project = WorkspaceHelper.getProject("hello");
		wcOwner = new LanguageServerWorkingCopyOwner(connection);
		server = new JDTLanguageServer(projectsManager, this.preferenceManager);
	}

	@Test
	public void testCodeAction_removeUnusedImport() throws Exception{
		ICompilationUnit unit = getWorkingCopy(
				"src/java/Foo.java",
				"import java.sql.*; \n" +
						"public class Foo {\n"+
						"	void foo() {\n"+
						"	}\n"+
				"}\n");

		CodeActionParams params = new CodeActionParams();
		params.setTextDocument(new TextDocumentIdentifier(JDTUtils.toURI(unit)));
		final Range range = getRange(unit, "java.sql");
		params.setRange(range);
		params.setContext(new CodeActionContext(Arrays.asList(getDiagnostic(Integer.toString(IProblem.UnusedImport), range))));
		List<Either<Command, CodeAction>> codeActions = getCodeActions(params);
		Assert.assertNotNull(codeActions);
		Assert.assertEquals(4, codeActions.size());
		Assert.assertEquals(codeActions.get(0).getRight().getKind(), CodeActionKind.QuickFix);
		Assert.assertEquals(codeActions.get(1).getRight().getKind(), CodeActionKind.QuickFix);
		Assert.assertEquals(codeActions.get(2).getRight().getKind(), CodeActionKind.SourceOrganizeImports);
		Assert.assertEquals(codeActions.get(3).getRight().getKind(), JavaCodeActionKind.SOURCE_OVERRIDE_METHODS);
		Command c = codeActions.get(0).getRight().getCommand();
		Assert.assertEquals(CodeActionHandler.COMMAND_ID_APPLY_EDIT, c.getCommand());
	}

	@Test
	public void testCodeAction_removeUnterminatedString() throws Exception{
		ICompilationUnit unit = getWorkingCopy(
				"src/java/Foo.java",
				"public class Foo {\n"+
						"	void foo() {\n"+
						"String s = \"some str\n" +
						"	}\n"+
				"}\n");

		CodeActionParams params = new CodeActionParams();
		params.setTextDocument(new TextDocumentIdentifier(JDTUtils.toURI(unit)));
		final Range range = getRange(unit, "some str");
		params.setRange(range);
		params.setContext(new CodeActionContext(Arrays.asList(getDiagnostic(Integer.toString(IProblem.UnterminatedString), range))));
		List<Either<Command, CodeAction>> codeActions = getCodeActions(params);
		Assert.assertNotNull(codeActions);
		Assert.assertFalse(codeActions.isEmpty());
		Assert.assertEquals(codeActions.get(0).getRight().getKind(), CodeActionKind.QuickFix);
		Command c = codeActions.get(0).getRight().getCommand();
		Assert.assertEquals(CodeActionHandler.COMMAND_ID_APPLY_EDIT, c.getCommand());
	}

	@Test
	public void testCodeAction_exception() throws JavaModelException {
		URI uri = project.getFile("nopackage/Test.java").getRawLocationURI();
		ICompilationUnit cu = JDTUtils.resolveCompilationUnit(uri);
		try {
			cu.becomeWorkingCopy(new NullProgressMonitor());
			CodeActionParams params = new CodeActionParams();
			params.setTextDocument(new TextDocumentIdentifier(uri.toString()));
			final Range range = new Range();
			range.setStart(new Position(0, 17));
			range.setEnd(new Position(0, 17));
			params.setRange(range);
			CodeActionContext context = new CodeActionContext();
			context.setDiagnostics(Collections.emptyList());
			params.setContext(context);
			List<Either<Command, CodeAction>> codeActions = getCodeActions(params);
			Assert.assertNotNull(codeActions);
		} finally {
			cu.discardWorkingCopy();
		}
	}

	@Test
	@Ignore
	public void testCodeAction_superfluousSemicolon() throws Exception{
		ICompilationUnit unit = getWorkingCopy(
				"src/java/Foo.java",
				"public class Foo {\n"+
						"	void foo() {\n"+
						";" +
						"	}\n"+
				"}\n");

		CodeActionParams params = new CodeActionParams();
		params.setTextDocument(new TextDocumentIdentifier(JDTUtils.toURI(unit)));
		final Range range = getRange(unit, ";");
		params.setRange(range);
		params.setContext(new CodeActionContext(Arrays.asList(getDiagnostic(Integer.toString(IProblem.SuperfluousSemicolon), range))));
		List<Either<Command, CodeAction>> codeActions = getCodeActions(params);
		Assert.assertNotNull(codeActions);
		Assert.assertEquals(1, codeActions.size());
		Assert.assertEquals(codeActions.get(0), CodeActionKind.QuickFix);
		Command c = getCommand(codeActions.get(0));
		Assert.assertEquals(CodeActionHandler.COMMAND_ID_APPLY_EDIT, c.getCommand());
		Assert.assertNotNull(c.getArguments());
		Assert.assertTrue(c.getArguments().get(0) instanceof WorkspaceEdit);
		WorkspaceEdit we = (WorkspaceEdit) c.getArguments().get(0);
		List<org.eclipse.lsp4j.TextEdit> edits = we.getChanges().get(JDTUtils.toURI(unit));
		Assert.assertEquals(1, edits.size());
		Assert.assertEquals("", edits.get(0).getNewText());
		Assert.assertEquals(range, edits.get(0).getRange());
	}

	@Test
	public void test_noUnnecessaryCodeActions() throws Exception{
		//@formatter:off
		ICompilationUnit unit = getWorkingCopy(
				"src/org/sample/Foo.java",
				"package org.sample;\n"+
				"\n"+
				"public class Foo {\n"+
				"	private String foo;\n"+
				"	public String getFoo() {\n"+
				"	  return foo;\n"+
				"	}\n"+
				"   \n"+
				"	public void setFoo(String newFoo) {\n"+
				"	  foo = newFoo;\n"+
				"	}\n"+
				"}\n");
		//@formatter:on
		CodeActionParams params = new CodeActionParams();
		params.setTextDocument(new TextDocumentIdentifier(JDTUtils.toURI(unit)));
		final Range range = getRange(unit, "String foo;");
		params.setRange(range);
		params.setContext(new CodeActionContext(Collections.emptyList()));
		List<Either<Command, CodeAction>> codeActions = getCodeActions(params);
		Assert.assertNotNull(codeActions);
		Assert.assertFalse("No need for organize imports action", containsKind(codeActions, CodeActionKind.SourceOrganizeImports));
		Assert.assertFalse("No need for generate getter and setter action", containsKind(codeActions, JavaCodeActionKind.SOURCE_GENERATE_ACCESSORS));
	}

	public static Range getRange(ICompilationUnit unit, String search) throws JavaModelException {
		String str= unit.getSource();
		int start = str.lastIndexOf(search);
		return JDTUtils.toRange(unit, start, search.length());
	}

	private List<Either<Command, CodeAction>> getCodeActions(CodeActionParams params) {
		return server.codeAction(params).join();
	}

	public Command getCommand(Either<Command, CodeAction> codeAction) {
		return codeAction.isLeft() ? codeAction.getLeft() : codeAction.getRight().getCommand();
	}

	private Diagnostic getDiagnostic(String code, Range range){
		Diagnostic $ = new Diagnostic();
		$.setCode(code);
		$.setRange(range);
		$.setSeverity(DiagnosticSeverity.Error);
		$.setMessage("Test Diagnostic");
		return $;
	}

	public static boolean containsKind(List<Either<Command, CodeAction>> codeActions, String kind) {
		for (Either<Command, CodeAction> action : codeActions) {
			String actionKind = action.getLeft() == null ? action.getRight().getKind() : action.getLeft().getCommand();
			if (Objects.equals(actionKind, kind)) {
				return true;
			}
		}

		return false;
	}
}
