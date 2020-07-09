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

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.compiler.IProblem;
import org.eclipse.jdt.ls.core.internal.CodeActionUtil;
import org.eclipse.jdt.ls.core.internal.JDTUtils;
import org.eclipse.jdt.ls.core.internal.JavaClientConnection;
import org.eclipse.jdt.ls.core.internal.JavaCodeActionKind;
import org.eclipse.jdt.ls.core.internal.LanguageServerWorkingCopyOwner;
import org.eclipse.jdt.ls.core.internal.WorkspaceHelper;
import org.eclipse.jdt.ls.core.internal.preferences.PreferenceManager;
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
		final Range range = CodeActionUtil.getRange(unit, "java.sql");
		params.setRange(range);
		params.setContext(new CodeActionContext(Arrays.asList(getDiagnostic(Integer.toString(IProblem.UnusedImport), range))));
		List<Either<Command, CodeAction>> codeActions = getCodeActions(params);
		Assert.assertNotNull(codeActions);
		Assert.assertTrue(codeActions.size() >= 3);
		Assert.assertEquals(codeActions.get(0).getRight().getKind(), CodeActionKind.QuickFix);
		Assert.assertEquals(codeActions.get(1).getRight().getKind(), CodeActionKind.QuickFix);
		Assert.assertEquals(codeActions.get(2).getRight().getKind(), CodeActionKind.SourceOrganizeImports);
		Command c = codeActions.get(0).getRight().getCommand();
		Assert.assertEquals(CodeActionHandler.COMMAND_ID_APPLY_EDIT, c.getCommand());
	}

	@Test
	public void testCodeAction_sourceActionsOnly() throws Exception {
		//@formatter:off
		ICompilationUnit unit = getWorkingCopy(
				"src/java/Foo.java",
				"import java.sql.*; \n" +
				"public class Foo {\n"+
				"	void foo() {\n"+
				"	}\n"+
				"}\n");
		//@formatter:on
		CodeActionParams params = new CodeActionParams();
		params.setTextDocument(new TextDocumentIdentifier(JDTUtils.toURI(unit)));
		final Range range = CodeActionUtil.getRange(unit, "foo()");
		params.setRange(range);
		params.setContext(new CodeActionContext(Collections.emptyList(), Collections.singletonList(CodeActionKind.Source)));
		List<Either<Command, CodeAction>> sourceActions = getCodeActions(params);

		Assert.assertNotNull(sourceActions);
		Assert.assertFalse("No source actions were found", sourceActions.isEmpty());
		for (Either<Command, CodeAction> codeAction : sourceActions) {
			Assert.assertTrue("Unexpected kind:" + codeAction.getRight().getKind(), codeAction.getRight().getKind().startsWith(CodeActionKind.Source));
		}
	}

	@Test
	public void testCodeAction_organizeImportsSourceActionOnly() throws Exception {
		ICompilationUnit unit = getWorkingCopy(
				"src/java/Foo.java",
				"import java.util.List;\n"+
				"public class Foo {\n"+
				"	void foo() {\n"+
				"		String bar = \"astring\";"+
				"	}\n"+
				"}\n");
		CodeActionParams params = new CodeActionParams();
		params.setTextDocument(new TextDocumentIdentifier(JDTUtils.toURI(unit)));
		final Range range = CodeActionUtil.getRange(unit, "bar");
		params.setRange(range);
		CodeActionContext context = new CodeActionContext(
			Arrays.asList(getDiagnostic(Integer.toString(IProblem.LocalVariableIsNeverUsed), range)),
			Collections.singletonList(CodeActionKind.SourceOrganizeImports)
		);
		params.setContext(context);
		List<Either<Command, CodeAction>> codeActions = getCodeActions(params);

		Assert.assertNotNull(codeActions);
		Assert.assertFalse("No organize imports actions were found", codeActions.isEmpty());
		for (Either<Command, CodeAction> codeAction : codeActions) {
			Assert.assertTrue("Unexpected kind:" + codeAction.getRight().getKind(), codeAction.getRight().getKind().startsWith(CodeActionKind.SourceOrganizeImports));
		}
	}

	@Test
	public void testCodeAction_refactorActionsOnly() throws Exception {
		ICompilationUnit unit = getWorkingCopy(
				"src/java/Foo.java",
				"public class Foo {\n"+
				"	void foo() {\n"+
				"		String bar = \"astring\";"+
				"	}\n"+
				"}\n");
		CodeActionParams params = new CodeActionParams();
		params.setTextDocument(new TextDocumentIdentifier(JDTUtils.toURI(unit)));
		final Range range = CodeActionUtil.getRange(unit, "bar");
		params.setRange(range);
		CodeActionContext context = new CodeActionContext(
			Arrays.asList(getDiagnostic(Integer.toString(IProblem.LocalVariableIsNeverUsed), range)),
			Collections.singletonList(CodeActionKind.Refactor)
		);
		params.setContext(context);
		List<Either<Command, CodeAction>> refactorActions = getCodeActions(params);

		Assert.assertNotNull(refactorActions);
		Assert.assertFalse("No refactor actions were found", refactorActions.isEmpty());
		for (Either<Command, CodeAction> codeAction : refactorActions) {
			Assert.assertTrue("Unexpected kind:" + codeAction.getRight().getKind(), codeAction.getRight().getKind().startsWith(CodeActionKind.Refactor));
		}
	}

	@Test
	public void testCodeAction_quickfixActionsOnly() throws Exception {
		ICompilationUnit unit = getWorkingCopy(
				"src/java/Foo.java",
				"public class Foo {\n"+
				"	void foo() {\n"+
				"		String bar = \"astring\";"+
				"	}\n"+
				"}\n");
		CodeActionParams params = new CodeActionParams();
		params.setTextDocument(new TextDocumentIdentifier(JDTUtils.toURI(unit)));
		final Range range = CodeActionUtil.getRange(unit, "bar");
		params.setRange(range);
		CodeActionContext context = new CodeActionContext(
			Arrays.asList(getDiagnostic(Integer.toString(IProblem.LocalVariableIsNeverUsed), range)),
			Collections.singletonList(CodeActionKind.QuickFix)
		);
		params.setContext(context);
		List<Either<Command, CodeAction>> quickfixActions = getCodeActions(params);

		Assert.assertNotNull(quickfixActions);
		Assert.assertFalse("No quickfix actions were found", quickfixActions.isEmpty());
		for (Either<Command, CodeAction> codeAction : quickfixActions) {
			Assert.assertTrue("Unexpected kind:" + codeAction.getRight().getKind(), codeAction.getRight().getKind().startsWith(CodeActionKind.QuickFix));
		}
	}

	@Test
	public void testCodeAction_allKindsOfActions() throws Exception {
		ICompilationUnit unit = getWorkingCopy(
				"src/java/Foo.java",
				"public class Foo {\n"+
				"	void foo() {\n"+
				"		String bar = \"astring\";"+
				"	}\n"+
				"}\n");
		CodeActionParams params = new CodeActionParams();
		params.setTextDocument(new TextDocumentIdentifier(JDTUtils.toURI(unit)));
		final Range range = CodeActionUtil.getRange(unit, "bar");
		params.setRange(range);
		CodeActionContext context = new CodeActionContext(
			Arrays.asList(getDiagnostic(Integer.toString(IProblem.LocalVariableIsNeverUsed), range))
		);
		params.setContext(context);
		List<Either<Command, CodeAction>> codeActions = getCodeActions(params);

		Assert.assertNotNull(codeActions);
		Assert.assertFalse("No code actions were found", codeActions.isEmpty());
		boolean hasQuickFix = codeActions.stream().anyMatch(codeAction -> codeAction.getRight().getKind().startsWith(CodeActionKind.QuickFix));
		assertTrue("No quickfix actions were found", hasQuickFix);
		boolean hasRefactor = codeActions.stream().anyMatch(codeAction -> codeAction.getRight().getKind().startsWith(CodeActionKind.Refactor));
		assertTrue("No refactor actions were found", hasRefactor);
		boolean hasSource = codeActions.stream().anyMatch(codeAction -> codeAction.getRight().getKind().startsWith(CodeActionKind.Source));
		assertTrue("No source actions were found", hasSource);

		List<String> baseKinds = codeActions.stream().map(codeAction -> getBaseKind(codeAction.getRight().getKind())).collect(Collectors.toList());
		assertTrue("quickfix actions should be ahead of refactor actions",  baseKinds.lastIndexOf(CodeActionKind.QuickFix) < baseKinds.indexOf(CodeActionKind.Refactor));
		assertTrue("refactor actions should be ahead of source actions",  baseKinds.lastIndexOf(CodeActionKind.Refactor) < baseKinds.indexOf(CodeActionKind.Source));
	}

	private static String getBaseKind(String codeActionKind) {
		if (codeActionKind.contains(".")) {
			return codeActionKind.substring(0, codeActionKind.indexOf('.'));
		} else {
			return codeActionKind;
		}
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
		final Range range = CodeActionUtil.getRange(unit, "some str");
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
		final Range range = CodeActionUtil.getRange(unit, ";");
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
		final Range range = CodeActionUtil.getRange(unit, "String foo;");
		params.setRange(range);
		params.setContext(new CodeActionContext(Collections.emptyList()));
		List<Either<Command, CodeAction>> codeActions = getCodeActions(params);
		Assert.assertNotNull(codeActions);
		Assert.assertFalse("No need for organize imports action", containsKind(codeActions, CodeActionKind.SourceOrganizeImports));
		Assert.assertFalse("No need for generate getter and setter action", containsKind(codeActions, JavaCodeActionKind.SOURCE_GENERATE_ACCESSORS));
	}

	@Test
	public void test_filterTypes() throws Exception {
		//@formatter:off
		ICompilationUnit unit = getWorkingCopy(
				"src/org/sample/Foo.java",
				"package org.sample;\n"+
				"\n"+
				"public class Foo {\n"+
				"	List foo;\n"+
				"}\n");
		//@formatter:on
		CodeActionParams params = new CodeActionParams();
		params.setTextDocument(new TextDocumentIdentifier(JDTUtils.toURI(unit)));
		final Range range = CodeActionUtil.getRange(unit, "List");
		params.setRange(range);
		params.setContext(new CodeActionContext(Collections.emptyList()));
		List<Either<Command, CodeAction>> codeActions = getCodeActions(params);
		Assert.assertNotNull(codeActions);
		Assert.assertTrue("No organize imports action", containsKind(codeActions, CodeActionKind.SourceOrganizeImports));
		try {
			List<String> filteredTypes = new ArrayList<>();
			filteredTypes.add("java.util.*");
			PreferenceManager.getPrefs(null).setFilteredTypes(filteredTypes);
			codeActions = getCodeActions(params);
			assertNotNull(codeActions);
			Assert.assertFalse("No need for organize imports action", containsKind(codeActions, CodeActionKind.SourceOrganizeImports));
		} finally {
			PreferenceManager.getPrefs(null).setFilteredTypes(Collections.emptyList());
		}
	}

	@Test
	public void testCodeAction_ignoringOtherDiagnosticWithoutCode() throws Exception {
		ICompilationUnit unit = getWorkingCopy("src/java/Foo.java", "import java.sql.*; \n" + "public class Foo {\n" + "	void foo() {\n" + "	}\n" + "}\n");

		CodeActionParams params = new CodeActionParams();
		params.setTextDocument(new TextDocumentIdentifier(JDTUtils.toURI(unit)));
		final Range range = CodeActionUtil.getRange(unit, "java.sql");
		params.setRange(range);

		Diagnostic diagnosticWithoutCode = new Diagnostic(new Range(new Position(0, 0), new Position(0, 1)), "fake dignostic without code");

		params.setContext(new CodeActionContext(Arrays.asList(diagnosticWithoutCode, getDiagnostic(Integer.toString(IProblem.UnusedImport), range))));
		List<Either<Command, CodeAction>> codeActions = getCodeActions(params);
		Assert.assertNotNull(codeActions);
		Assert.assertTrue(codeActions.size() >= 3);
		Assert.assertEquals(codeActions.get(0).getRight().getKind(), CodeActionKind.QuickFix);
		Assert.assertEquals(codeActions.get(1).getRight().getKind(), CodeActionKind.QuickFix);
		Assert.assertEquals(codeActions.get(2).getRight().getKind(), CodeActionKind.SourceOrganizeImports);
		Command c = codeActions.get(0).getRight().getCommand();
		Assert.assertEquals(CodeActionHandler.COMMAND_ID_APPLY_EDIT, c.getCommand());
	}

	private List<Either<Command, CodeAction>> getCodeActions(CodeActionParams params) {
		return server.codeAction(params).join();
	}

	public static Command getCommand(Either<Command, CodeAction> codeAction) {
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

	public static Either<Command, CodeAction> findAction(List<Either<Command, CodeAction>> codeActions, String kind) {
		Optional<Either<Command, CodeAction>> any = codeActions.stream().filter((action) -> Objects.equals(kind, action.getLeft() == null ? action.getRight().getKind() : action.getLeft().getCommand())).findFirst();
		return any.isPresent() ? any.get() : null;
	}
}
