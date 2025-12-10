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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IPackageFragment;
import org.eclipse.jdt.core.IPackageFragmentRoot;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.compiler.IProblem;
import org.eclipse.jdt.core.formatter.DefaultCodeFormatterConstants;
import org.eclipse.jdt.ls.core.internal.CodeActionUtil;
import org.eclipse.jdt.ls.core.internal.JDTUtils;
import org.eclipse.jdt.ls.core.internal.JavaClientConnection;
import org.eclipse.jdt.ls.core.internal.JavaCodeActionKind;
import org.eclipse.jdt.ls.core.internal.JavaLanguageServerPlugin;
import org.eclipse.jdt.ls.core.internal.LanguageServerWorkingCopyOwner;
import org.eclipse.jdt.ls.core.internal.ProjectUtils;
import org.eclipse.jdt.ls.core.internal.ResourceUtils;
import org.eclipse.jdt.ls.core.internal.WorkspaceHelper;
import org.eclipse.jdt.ls.core.internal.codemanipulation.AbstractSourceTestCase;
import org.eclipse.jdt.ls.core.internal.correction.AbstractQuickFixTest;
import org.eclipse.jdt.ls.core.internal.corrections.CorrectionMessages;
import org.eclipse.jdt.ls.core.internal.preferences.ClientPreferences;
import org.eclipse.jdt.ls.core.internal.preferences.PreferenceManager;
import org.eclipse.jdt.ls.core.internal.preferences.Preferences;
import org.eclipse.lsp4j.CodeAction;
import org.eclipse.lsp4j.CodeActionContext;
import org.eclipse.lsp4j.CodeActionKind;
import org.eclipse.lsp4j.CodeActionParams;
import org.eclipse.lsp4j.Command;
import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.DiagnosticSeverity;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.TextDocumentEdit;
import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.eclipse.lsp4j.WorkspaceEdit;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * @author Gorkem Ercan
 *
 */
@ExtendWith(MockitoExtension.class)
public class CodeActionHandlerTest extends AbstractCompilationUnitBasedTest {

	@Mock
	private JavaClientConnection connection;
	private ClientPreferences clientPreferences;
	private File javaCompilerSettings;

	@Override
	@BeforeEach
	public void setup() throws Exception{
		importProjects("eclipse/hello");
		project = WorkspaceHelper.getProject("hello");
		wcOwner = new LanguageServerWorkingCopyOwner(connection);
		server = new JDTLanguageServer(projectsManager, this.preferenceManager);
	}

	@Override
	protected ClientPreferences initPreferenceManager(boolean supportClassFileContents) {
		clientPreferences = super.initPreferenceManager(supportClassFileContents);
		return clientPreferences;
	}

	@Override
	protected void initPreferences(Preferences preferences) throws IOException {
		super.initPreferences(preferences);
		javaCompilerSettings = new File(System.getProperty("java.io.tmpdir"), "settings.prefs");
		javaCompilerSettings.createNewFile();
		preferences.setSettingsUrl(javaCompilerSettings.toURI().toString());
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
		assertNotNull(codeActions);
		assertTrue(codeActions.size() >= 3);
		List<Either<Command, CodeAction>> quickAssistActions = findActions(codeActions, CodeActionKind.QuickFix);
		assertNotNull(quickAssistActions);
		assertTrue(quickAssistActions.size() >= 1);
		List<Either<Command, CodeAction>> organizeImportActions = findActions(codeActions, CodeActionKind.SourceOrganizeImports);
		assertNotNull(organizeImportActions);
		assertEquals(1, organizeImportActions.size());
		WorkspaceEdit e = codeActions.get(0).getRight().getEdit();
		assertNotNull(e);
	}

	@Test
	public void testCodeActionLiteral_removeUnusedImport() throws Exception{
		when(preferenceManager.getClientPreferences().isSupportedCodeActionKind(CodeActionKind.QuickFix)).thenReturn(true);
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
		assertNotNull(codeActions);
		assertTrue(codeActions.size() >= 4);
		assertEquals(CodeActionKind.QuickFix, codeActions.get(0).getRight().getKind());
		assertEquals(CodeActionKind.QuickFix, codeActions.get(1).getRight().getKind());
		assertEquals(JavaCodeActionKind.QUICK_ASSIST, codeActions.get(2).getRight().getKind());
		assertEquals(JavaCodeActionKind.SOURCE_GENERATE_CONSTRUCTORS, codeActions.get(3).getRight().getKind());
		assertEquals(CodeActionKind.SourceOrganizeImports, codeActions.get(4).getRight().getKind());
		WorkspaceEdit w = codeActions.get(0).getRight().getEdit();
		assertNotNull(w);
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

		assertNotNull(sourceActions);
		assertFalse(sourceActions.isEmpty(), "No source actions were found");
		for (Either<Command, CodeAction> codeAction : sourceActions) {
			assertTrue(codeAction.getRight().getKind().startsWith(CodeActionKind.Source), "Unexpected kind:" + codeAction.getRight().getKind());
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

		assertNotNull(codeActions);
		assertFalse(codeActions.isEmpty(), "No organize imports actions were found");
		for (Either<Command, CodeAction> codeAction : codeActions) {
			assertTrue(codeAction.getRight().getKind().startsWith(CodeActionKind.SourceOrganizeImports), "Unexpected kind:" + codeAction.getRight().getKind());
		}
	}

	@Test
	public void testCodeAction_organizeImportsQuickFix() throws Exception {
		ICompilationUnit unit = getWorkingCopy(
				"src/java/Foo.java",
				"import java.util.List;\n"+
				"public class Foo {\n"+
				"	void foo() {\n"+
				"		String bar = \"astring\";"+
				"	}\n"+
				"}\n");
		CodeActionParams params = CodeActionUtil.constructCodeActionParams(unit, "util");
		List<Either<Command, CodeAction>> codeActions = server.codeAction(params).join();

		assertNotNull(codeActions);
		List<Either<Command, CodeAction>> quickAssistActions = findActions(codeActions, JavaCodeActionKind.QUICK_ASSIST);
		assertTrue(CodeActionHandlerTest.titleExists(quickAssistActions, CorrectionMessages.ReorgCorrectionsSubProcessor_organizeimports_description));
		// Test if the quick assist exists only for type declaration
		params = CodeActionUtil.constructCodeActionParams(unit, "String bar");
		codeActions = server.codeAction(params).join();
		assertNotNull(codeActions);
		quickAssistActions = CodeActionHandlerTest.findActions(codeActions, JavaCodeActionKind.QUICK_ASSIST);
		assertFalse(CodeActionHandlerTest.titleExists(quickAssistActions, CorrectionMessages.ReorgCorrectionsSubProcessor_organizeimports_description));
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

		assertNotNull(refactorActions);
		assertFalse(refactorActions.isEmpty(), "No refactor actions were found");
		for (Either<Command, CodeAction> codeAction : refactorActions) {
			assertTrue(codeAction.getRight().getKind().startsWith(CodeActionKind.Refactor), "Unexpected kind:" + codeAction.getRight().getKind());
		}
	}

	@Test
	public void testCodeAction_errorFromOtherSources() throws Exception {
		ICompilationUnit unit = getWorkingCopy(
				"src/java/Foo.java",
				"public class Foo {\n"+
				"	void foo() {\n"+
				"		Integer bar = 2000;"+
				"	}\n"+
				"}\n");
		CodeActionParams params = new CodeActionParams();
		params.setTextDocument(new TextDocumentIdentifier(JDTUtils.toURI(unit)));
		final Range range = CodeActionUtil.getRange(unit, "bar");
		params.setRange(range);
		Diagnostic diagnostic = new Diagnostic();
		diagnostic.setCode("MagicNumberCheck");
		diagnostic.setRange(range);
		diagnostic.setSeverity(DiagnosticSeverity.Error);
		diagnostic.setMessage("'2000' is a magic number.");
		diagnostic.setSource("Checkstyle");
		CodeActionContext context = new CodeActionContext(
			Arrays.asList(diagnostic),
			Collections.singletonList(CodeActionKind.Refactor)
		);
		params.setContext(context);
		List<Either<Command, CodeAction>> refactorActions = getCodeActions(params);

		assertNotNull(refactorActions);
		assertFalse(refactorActions.isEmpty(), "No refactor actions were found");
		for (Either<Command, CodeAction> codeAction : refactorActions) {
			assertTrue(codeAction.getRight().getKind().startsWith(CodeActionKind.Refactor), "Unexpected kind:" + codeAction.getRight().getKind());
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

		assertNotNull(quickfixActions);
		assertFalse(quickfixActions.isEmpty(), "No quickfix actions were found");
		for (Either<Command, CodeAction> codeAction : quickfixActions) {
			assertTrue(codeAction.getRight().getKind().startsWith(CodeActionKind.QuickFix), "Unexpected kind:" + codeAction.getRight().getKind());
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

		assertNotNull(codeActions);
		assertFalse(codeActions.isEmpty(), "No code actions were found");
		boolean hasQuickFix = codeActions.stream().anyMatch(codeAction -> codeAction.getRight().getKind().startsWith(CodeActionKind.QuickFix));
		assertTrue(hasQuickFix, "No quickfix actions were found");
		boolean hasRefactor = codeActions.stream().anyMatch(codeAction -> codeAction.getRight().getKind().startsWith(CodeActionKind.Refactor));
		assertTrue(hasRefactor, "No refactor actions were found");
		boolean hasSource = codeActions.stream().anyMatch(codeAction -> codeAction.getRight().getKind().startsWith(CodeActionKind.Source));
		assertTrue(hasSource, "No source actions were found");

		List<String> baseKinds = codeActions.stream().map(codeAction -> getBaseKind(codeAction.getRight().getKind())).collect(Collectors.toList());
		assertTrue(baseKinds.lastIndexOf(CodeActionKind.QuickFix) < baseKinds.indexOf(CodeActionKind.Refactor), "quickfix actions should be ahead of refactor actions");
		assertTrue(baseKinds.lastIndexOf(CodeActionKind.Refactor) < baseKinds.indexOf(CodeActionKind.Source), "refactor actions should be ahead of source actions");
	}

	@Test
	public void testCodeAction_NPEInNewCUProposal() throws Exception {
		ICompilationUnit unit = getWorkingCopy(
		//@formatter:off
				"src/org/sample/Foo.java",
				"package org.sample;\n"+
				"public class Foo {\n"+
				"	public static void main(String[] args) {\n"+
				"		new javax.activity\n"+
				"	}\n"+
				"}\n");
		//@formatter:off
		CodeActionParams params = new CodeActionParams();
		params.setTextDocument(new TextDocumentIdentifier(JDTUtils.toURI(unit)));
		final Range range = CodeActionUtil.getRange(unit, "javax.activity");
		params.setRange(range);
		CodeActionContext context = new CodeActionContext(
			Arrays.asList(getDiagnostic(Integer.toString(IProblem.UndefinedType), range))
		);
		params.setContext(context);
		List<Either<Command, CodeAction>> codeActions = getCodeActions(params);
		assertNotNull(codeActions);
	}

	@Test
	public void testCodeAction_NPEOnInvalidMethodCall() throws Exception {
		ICompilationUnit unit = getWorkingCopy(
				"src/App.java",
				"""
				public class App {
					String foo = App.test(); // compilation error, test() doesn't exist
				}
				"""
				);
		CodeActionParams params = new CodeActionParams();
		params.setTextDocument(new TextDocumentIdentifier(JDTUtils.toURI(unit)));
		String source = unit.getSource();
		int appIndex = source.indexOf("App.test()");
		final Range range = JDTUtils.toRange(unit, appIndex, "App".length());
		params.setRange(range);
		CodeActionContext context = new CodeActionContext(Collections.emptyList());
		params.setContext(context);
		// This should not throw NPE
		List<Either<Command, CodeAction>> codeActions = getCodeActions(params);
		assertNotNull(codeActions, "Code actions should not be null");
		// Code actions may be empty, but should not throw an exception
	}

	@Test
	public void testCodeAction_refreshDiagnosticsCommandInNewCUProposal() throws Exception {
		when(clientPreferences.isResolveCodeActionSupported()).thenReturn(true);
		preferences.setValidateAllOpenBuffersOnChanges(false);
		ICompilationUnit unit = getWorkingCopy(
		//@formatter:off
				"src/org/sample/Foo.java",
				"package org.sample;\n"+
				"public class Foo {\n"+
				"	public static void main(String[] args) {\n"+
				"		CU obj;\n"+
				"	}\n"+
				"}\n");
		//@formatter:off
		CodeActionParams params = new CodeActionParams();
		params.setTextDocument(new TextDocumentIdentifier(JDTUtils.toURI(unit)));
		final Range range = CodeActionUtil.getRange(unit, "CU");
		params.setRange(range);
		CodeActionContext context = new CodeActionContext(
			Arrays.asList(getDiagnostic(Integer.toString(IProblem.UndefinedType), range))
		);
		params.setContext(context);
		List<Either<Command, CodeAction>> codeActions = getCodeActions(params);
		assertNotNull(codeActions);
		Optional<Either<Command, CodeAction>> newCUProposal = codeActions.stream().filter(action -> action.isRight() && Objects.equals(action.getRight().getTitle(), "Create class 'CU'")).findFirst();
		assertTrue(newCUProposal.isPresent());
		assertNotNull(newCUProposal.get().getRight());
		assertNotNull(newCUProposal.get().getRight().getCommand());
		assertEquals("java.project.refreshDiagnostics", newCUProposal.get().getRight().getCommand().getCommand());
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
		assertNotNull(codeActions);
		assertFalse(codeActions.isEmpty());
		assertEquals(CodeActionKind.QuickFix, codeActions.get(0).getRight().getKind());
		WorkspaceEdit e = codeActions.get(0).getRight().getEdit();
		assertNotNull(e);
	}

	@Test
	public void testCodeActionLiteral_removeUnterminatedString() throws Exception{
		when(preferenceManager.getClientPreferences().isSupportedCodeActionKind(CodeActionKind.QuickFix)).thenReturn(true);
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
		assertNotNull(codeActions);
		assertFalse(codeActions.isEmpty());
		assertEquals(CodeActionKind.QuickFix, codeActions.get(0).getRight().getKind());
		WorkspaceEdit w = codeActions.get(0).getRight().getEdit();
		assertNotNull(w);
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
			assertNotNull(codeActions);
		} finally {
			cu.discardWorkingCopy();
		}
	}

	@Test
	@Disabled
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
		assertNotNull(codeActions);
		assertEquals(1, codeActions.size());
		assertEquals(CodeActionKind.QuickFix, codeActions.get(0).getRight().getKind());
		WorkspaceEdit we = codeActions.get(0).getRight().getEdit();
		List<org.eclipse.lsp4j.TextEdit> edits = we.getChanges().get(JDTUtils.toURI(unit));
		assertEquals(1, edits.size());
		assertEquals("", edits.get(0).getNewText());
		assertEquals(range, edits.get(0).getRange());
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
		assertNotNull(codeActions);
		assertFalse(containsKind(codeActions, CodeActionKind.SourceOrganizeImports), "No need for organize imports action");
		assertFalse(containsKind(codeActions, JavaCodeActionKind.SOURCE_GENERATE_ACCESSORS), "No need for generate getter and setter action");
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
		assertNotNull(codeActions);
		assertTrue(containsKind(codeActions, CodeActionKind.SourceOrganizeImports), "No organize imports action");
		try {
			List<String> filteredTypes = new ArrayList<>();
			filteredTypes.add("java.util.*");
			PreferenceManager.getPrefs(null).setFilteredTypes(filteredTypes);
			codeActions = getCodeActions(params);
			assertNotNull(codeActions);
			assertFalse(containsKind(codeActions, CodeActionKind.SourceOrganizeImports), "No need for organize imports action");
		} finally {
			PreferenceManager.getPrefs(null).setFilteredTypes(Collections.emptyList());
		}
	}

	@Test
	public void testCodeAction_ignoringOtherDiagnosticWithoutCode() throws Exception {
		ICompilationUnit unit = getWorkingCopy("src/java/Foo.java", "import java.sql.*; \n" + "public class Foo {\n" + "	void foo() {\n" + "	}\n" + "}\n");
		//unit.save(new NullProgressMonitor(), true);
		CodeActionParams params = new CodeActionParams();
		params.setTextDocument(new TextDocumentIdentifier(JDTUtils.toURI(unit)));
		final Range range = CodeActionUtil.getRange(unit, "java.sql");
		params.setRange(range);

		Diagnostic diagnosticWithoutCode = new Diagnostic(new Range(new Position(0, 0), new Position(0, 1)), "fake dignostic without code");

		params.setContext(new CodeActionContext(Arrays.asList(diagnosticWithoutCode, getDiagnostic(Integer.toString(IProblem.UnusedImport), range))));
		List<Either<Command, CodeAction>> codeActions = getCodeActions(params);
		assertNotNull(codeActions);
		assertTrue(codeActions.size() >= 3);
		List<Either<Command, CodeAction>> quickAssistActions = findActions(codeActions, CodeActionKind.QuickFix);
		assertNotNull(quickAssistActions);
		assertTrue(quickAssistActions.size() >= 1);
		List<Either<Command, CodeAction>> organizeImportActions = findActions(codeActions, CodeActionKind.SourceOrganizeImports);
		assertNotNull(organizeImportActions);
		assertEquals(1, organizeImportActions.size());
		WorkspaceEdit edit = codeActions.get(0).getRight().getEdit();
		assertNotNull(edit);
	}

	@Test
	public void testCodeAction_customFileFormattingOptions() throws Exception {
		when(clientPreferences.isWorkspaceConfigurationSupported()).thenReturn(true);
		when(connection.configuration(Mockito.any())).thenReturn(Arrays.asList(4, true/*Indent using Spaces*/));
		server.setClientConnection(connection);
		JavaLanguageServerPlugin.getInstance().setProtocol(server);
		IJavaProject javaProject = ProjectUtils.getJavaProject(project);
		Map<String, String> projectOptions = javaProject.getOptions(false);
		projectOptions.put(DefaultCodeFormatterConstants.FORMATTER_TAB_CHAR, JavaCore.TAB); // Indent using Tabs
		projectOptions.put(DefaultCodeFormatterConstants.FORMATTER_TAB_SIZE, "4");
		javaProject.setOptions(projectOptions);

		IPackageFragmentRoot sourceFolder = javaProject.getPackageFragmentRoot(project.getFolder("src"));
		IPackageFragment pack1 = sourceFolder.createPackageFragment("test1", false, null);
		StringBuilder builder = new StringBuilder();
		builder.append("package test1;\n");
		builder.append("interface I {\n");
		builder.append("	void method();\n");
		builder.append("}\n");
		builder.append("public class E {\n");
		builder.append("	void bar(I i) {\n");
		builder.append("	}\n");
		builder.append("	void foo() {\n");
		builder.append("		bar(() /*[*//*]*/-> {\n");
		builder.append("		});\n");
		builder.append("	}\n");
		builder.append("}\n");
		ICompilationUnit cu = pack1.createCompilationUnit("E.java", builder.toString(), false, null);

		CodeActionParams params = new CodeActionParams();
		params.setTextDocument(new TextDocumentIdentifier(JDTUtils.toURI(cu)));
		final Range range = CodeActionUtil.getRange(cu, "/*[*//*]*/");
		params.setRange(range);
		params.setContext(new CodeActionContext(Collections.emptyList(), Arrays.asList(CodeActionKind.Refactor)));
		List<Either<Command, CodeAction>> codeActions = getCodeActions(params);
		assertNotNull(codeActions);
		Optional<Either<Command, CodeAction>> found = codeActions.stream().filter((codeAction) -> {
			return codeAction.isRight() && Objects.equals("Convert to anonymous class creation", codeAction.getRight().getTitle());
		}).findAny();
		assertTrue(found.isPresent());

		Either<Command, CodeAction> codeAction = found.get();
		WorkspaceEdit edit = codeAction.getRight().getEdit();
		String actual = AbstractQuickFixTest.evaluateWorkspaceEdit(edit);
		builder = new StringBuilder();
		builder.append("package test1;\n");
		builder.append("interface I {\n");
		builder.append("	void method();\n");
		builder.append("}\n");
		builder.append("public class E {\n");
		builder.append("	void bar(I i) {\n");
		builder.append("	}\n");
		builder.append("	void foo() {\n");
		builder.append("		bar(new I() {\n");
		builder.append("            @Override\n");
		builder.append("            public void method() {\n");
		builder.append("            }\n");
		builder.append("        });\n");
		builder.append("	}\n");
		builder.append("}\n");
		AbstractSourceTestCase.compareSource(builder.toString(), actual);
	}

	@Test
	public void testCodeAction_unimplementedMethodReference() throws Exception {
		ICompilationUnit unit = getWorkingCopy(
				"src/java/Foo.java",
				"package test1;\n"
				+ "import java.util.Comparator;\n"
						+ "class Foo {\n"
				+ "    void foo(Comparator<String> c) {\n"
				+ "    }\n"
				+ "    void bar() {\n"
				+ "        foo(this::action);\n"
				+ "    }\n"
				+ "}");
		CodeActionParams params = new CodeActionParams();
		params.setTextDocument(new TextDocumentIdentifier(JDTUtils.toURI(unit)));
		final Range range = CodeActionUtil.getRange(unit, "action");
		params.setRange(range);
		CodeActionContext context = new CodeActionContext(
				Arrays.asList(getDiagnostic(Integer.toString(IProblem.DanglingReference), range)), Collections.singletonList(JavaCodeActionKind.QUICK_ASSIST));
		params.setContext(context);
		List<Either<Command, CodeAction>> codeActions = getCodeActions(params);

		assertNotNull(codeActions);
		CodeAction action = codeActions.get(1).getRight();
		assertEquals("Add missing method 'action' to class 'Foo'", action.getTitle());
    }

	@Test
	public void testCodeAction_ignoreCompilerIssue() throws Exception{
		when(clientPreferences.isResourceOperationSupported()).thenReturn(true);
		ICompilationUnit unit = getWorkingCopy(
				"src/java/Foo.java",
				"package java;\n"
				+ "public class Foo {\n"
				+ "  @SuppressWarnings(\"deprecation\")\n"
				+ "  public void test () {\n"
				+ "  }\n"
				+ "}");

		CodeActionParams params = new CodeActionParams();
		params.setTextDocument(new TextDocumentIdentifier(JDTUtils.toURI(unit)));
		final Range range = CodeActionUtil.getRange(unit, "deprecation");
		params.setRange(range);
		params.setContext(new CodeActionContext(Arrays.asList(getDiagnostic(Integer.toString(IProblem.UnusedWarningToken), range))));
		List<Either<Command, CodeAction>> codeActions = getCodeActions(params);

		assertNotNull(codeActions);
		assertFalse(codeActions.isEmpty());

		Either<Command, CodeAction> codeAction = findAction(codeActions, CodeActionKind.QuickFix, "Ignore compiler problem(s)");
		WorkspaceEdit we = getEdit(codeAction);
		assertEquals(1, we.getDocumentChanges().size());

		TextDocumentEdit textDocEdit = we.getDocumentChanges().get(0).getLeft();
		assertEquals("org.eclipse.jdt.core.compiler.problem.unusedWarningToken=ignore\n", textDocEdit.getEdits().get(0).getNewText());
	}

	@Test
	public void testQuickAssistForImportDeclarationOrder() throws JavaModelException {
		//@formatter:off
		ICompilationUnit unit = getWorkingCopy(
				"src/java/Foo.java",
				"import java.util.List;\n"+
				"public class Foo {\n"+
				"	void foo() {\n"+
				"		String bar = \"astring\";"+
				"	}\n"+
				"}\n");
		//@formatter:on
		CodeActionParams params = CodeActionUtil.constructCodeActionParams(unit, "util");
		List<Either<Command, CodeAction>> codeActions = server.codeAction(params).join();
		assertNotNull(codeActions);
		List<Either<Command, CodeAction>> quickAssistActions = CodeActionHandlerTest.findActions(codeActions, JavaCodeActionKind.QUICK_ASSIST);
		assertEquals("Organize imports", CodeActionHandlerTest.getTitle(quickAssistActions.get(0)));
	}

	@Test
	public void testQuickAssistForFieldDeclarationOrder() throws JavaModelException {
		//@formatter:off
		ICompilationUnit unit = getWorkingCopy("A.java", "package p;\r\n" +
				"\r\n" +
				"public class A {\r\n" +
				"	public String name = \"name\";\r\n" +
				"	public String pet = \"pet\";\r\n" +
				"}");
		//@formatter:on
		CodeActionParams params = CodeActionUtil.constructCodeActionParams(unit, "String name");
		List<Either<Command, CodeAction>> codeActions = server.codeAction(params).join();
		assertNotNull(codeActions);
		List<Either<Command, CodeAction>> quickAssistActions = CodeActionHandlerTest.findActions(codeActions, JavaCodeActionKind.QUICK_ASSIST);
		assertEquals("Generate Getter and Setter for 'name'", CodeActionHandlerTest.getTitle(quickAssistActions.get(0)));
		assertEquals("Generate Getter for 'name'", CodeActionHandlerTest.getTitle(quickAssistActions.get(1)));
		assertEquals("Generate Setter for 'name'", CodeActionHandlerTest.getTitle(quickAssistActions.get(2)));
		assertEquals("Generate Constructors...", CodeActionHandlerTest.getTitle(quickAssistActions.get(3)));
	}

	@Test
	public void testQuickAssistForTypeDeclarationOrder() throws JavaModelException {
		//@formatter:off
		ICompilationUnit unit = getWorkingCopy("A.java", "package p;\r\n" +
				"\r\n" +
				"public class A {\r\n" +
				"	public String name = \"name\";\r\n" +
				"	public String pet = \"pet\";\r\n" +
				"}");
		//@formatter:on
		CodeActionParams params = CodeActionUtil.constructCodeActionParams(unit, "A");
		List<Either<Command, CodeAction>> codeActions = server.codeAction(params).join();
		assertNotNull(codeActions);
		List<Either<Command, CodeAction>> quickAssistActions = CodeActionHandlerTest.findActions(codeActions, JavaCodeActionKind.QUICK_ASSIST);
		assertEquals("Generate Getters and Setters", CodeActionHandlerTest.getTitle(quickAssistActions.get(0)));
		assertEquals("Generate Getters", CodeActionHandlerTest.getTitle(quickAssistActions.get(1)));
		assertEquals("Generate Setters", CodeActionHandlerTest.getTitle(quickAssistActions.get(2)));
		assertEquals("Generate Constructors...", CodeActionHandlerTest.getTitle(quickAssistActions.get(3)));
		assertEquals("Generate hashCode() and equals()...", CodeActionHandlerTest.getTitle(quickAssistActions.get(4)));
		assertEquals("Generate toString()...", CodeActionHandlerTest.getTitle(quickAssistActions.get(5)));
		assertEquals("Override/Implement Methods...", CodeActionHandlerTest.getTitle(quickAssistActions.get(6)));
	}

	private List<Either<Command, CodeAction>> getCodeActions(CodeActionParams params) {
		return server.codeAction(params).join();
	}

	public static Command getCommand(Either<Command, CodeAction> codeAction) {
		return codeAction.isLeft() ? codeAction.getLeft() : codeAction.getRight().getCommand();
	}

	public static String getTitle(Either<Command, CodeAction> codeAction) {
		Command command = getCommand(codeAction);
		return ResourceUtils.dos2Unix(command != null ? command.getTitle() : codeAction.getRight().getTitle());
	}

	public static WorkspaceEdit getEdit(Either<Command, CodeAction> codeAction) {
		assertTrue(codeAction.isRight());
		assertNotNull(codeAction.getRight().getEdit());
		return codeAction.getRight().getEdit();
	}

	private Diagnostic getDiagnostic(String code, Range range){
		Diagnostic $ = new Diagnostic();
		$.setCode(code);
		$.setRange(range);
		$.setSeverity(DiagnosticSeverity.Error);
		$.setMessage("Test Diagnostic");
		$.setSource(JavaLanguageServerPlugin.SERVER_SOURCE_ID);
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

	public static Either<Command, CodeAction> findAction(List<Either<Command, CodeAction>> codeActions, String kind, String title) {
		Optional<Either<Command, CodeAction>> any = codeActions.stream().filter((action) -> Objects.equals(kind, action.getLeft() == null ? action.getRight().getKind() : action.getLeft().getCommand()) &&  Objects.equals(title, action.getLeft() == null ? action.getRight().getTitle() : action.getLeft().getTitle())).findFirst();
		return any.isPresent() ? any.get() : null;
	}

	public static List<Either<Command, CodeAction>> findActions(List<Either<Command, CodeAction>> codeActions, String kind) {
		return codeActions.stream().filter((action) -> Objects.equals(kind, action.getLeft() == null ? action.getRight().getKind() : action.getLeft().getCommand())).collect(Collectors.toList());
	}

	public static boolean commandExists(List<Either<Command, CodeAction>> codeActions, String command) {
		if (codeActions == null || codeActions.isEmpty()) {
			return false;
		}
		for (Either<Command, CodeAction> codeAction : codeActions) {
			Command actionCommand = getCommand(codeAction);
			if (actionCommand != null && actionCommand.getCommand().equals(command)) {
				return true;
			}
		}
		return false;
	}

	public static boolean commandExists(List<Either<Command, CodeAction>> codeActions, String command, String title) {
		if (codeActions.isEmpty()) {
			return false;
		}
		for (Either<Command, CodeAction> codeAction : codeActions) {
			Command actionCommand = getCommand(codeAction);
			if (actionCommand != null && actionCommand.getCommand().equals(command) && actionCommand.getTitle().equals(title)) {
				return true;
			}
		}
		return false;
	}

	public static boolean titleExists(List<Either<Command, CodeAction>> codeActions, String title) {
		if (codeActions.isEmpty()) {
			return false;
		}
		for (Either<Command, CodeAction> codeAction : codeActions) {
			if (title.equals(getTitle(codeAction))) {
				return true;
			}
		}
		return false;
	}
}
