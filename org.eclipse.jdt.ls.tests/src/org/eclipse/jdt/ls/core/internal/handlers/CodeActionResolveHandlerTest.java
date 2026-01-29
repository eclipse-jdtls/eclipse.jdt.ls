/*******************************************************************************
* Copyright (c) 2021 Microsoft Corporation and others.
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

package org.eclipse.jdt.ls.core.internal.handlers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import org.eclipse.core.resources.IProject;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IPackageFragment;
import org.eclipse.jdt.core.IPackageFragmentRoot;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.compiler.IProblem;
import org.eclipse.jdt.ls.core.internal.CodeActionUtil;
import org.eclipse.jdt.ls.core.internal.JDTUtils;
import org.eclipse.jdt.ls.core.internal.JavaClientConnection;
import org.eclipse.jdt.ls.core.internal.JavaCodeActionKind;
import org.eclipse.jdt.ls.core.internal.JavaLanguageServerPlugin;
import org.eclipse.jdt.ls.core.internal.LanguageServerWorkingCopyOwner;
import org.eclipse.jdt.ls.core.internal.WorkspaceHelper;
import org.eclipse.jdt.ls.core.internal.correction.AbstractQuickFixTest;
import org.eclipse.jdt.ls.core.internal.correction.TestOptions;
import org.eclipse.jdt.ls.core.internal.preferences.ClientPreferences;
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
import org.eclipse.lsp4j.TextEdit;
import org.eclipse.lsp4j.extended.SnippetTextEdit;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class CodeActionResolveHandlerTest extends AbstractCompilationUnitBasedTest {
	private IPackageFragment defaultPackage;
	@Mock
	private JavaClientConnection connection;

	@Override
	@BeforeEach
	public void setup() throws Exception{
		IJavaProject fJProject = newEmptyProject();
		fJProject.setOptions(TestOptions.getDefaultOptions());
		IPackageFragmentRoot fSourceFolder = fJProject.getPackageFragmentRoot(fJProject.getProject().getFolder("src"));
		defaultPackage = fSourceFolder.getPackageFragment("");
		wcOwner = new LanguageServerWorkingCopyOwner(connection);
		server = new JDTLanguageServer(projectsManager, this.preferenceManager);
	}

	@Test
	public void testResolveCodeAction_QuickFixes() throws Exception {
		when(preferenceManager.getClientPreferences().isResolveCodeActionSupported()).thenReturn(true);

		StringBuilder buf = new StringBuilder();
		buf.append("public class Foo {\n");
		buf.append("    void foo() {\n");
		buf.append("        String bar = \"astring\";");
		buf.append("    }\n");
		buf.append("}\n");
		ICompilationUnit unit = defaultPackage.createCompilationUnit("Foo.java", buf.toString(), false, null);
		CodeActionParams params = new CodeActionParams();
		params.setTextDocument(new TextDocumentIdentifier(JDTUtils.toURI(unit)));
		final Range range = CodeActionUtil.getRange(unit, "bar");
		params.setRange(range);
		CodeActionContext context = new CodeActionContext(
			Arrays.asList(getDiagnostic(Integer.toString(IProblem.LocalVariableIsNeverUsed), range)),
			Collections.singletonList(CodeActionKind.QuickFix)
		);
		params.setContext(context);

		List<Either<Command, CodeAction>> quickfixActions = server.codeAction(params).join();
		assertNotNull(quickfixActions);
		assertFalse(quickfixActions.isEmpty(), "No quickfix actions were found");
		for (Either<Command, CodeAction> codeAction : quickfixActions) {
			assertNull(codeAction.getRight().getEdit(), "Should defer the edit property to the resolveCodeAction request");
			assertNotNull(codeAction.getRight().getData(), "Should preserve the data property for the unresolved code action");
		}

		Optional<Either<Command, CodeAction>> removeUnusedResponse = quickfixActions.stream().filter(codeAction -> {
			return "Remove 'bar' and all assignments".equals(codeAction.getRight().getTitle());
		}).findFirst();
		assertTrue(removeUnusedResponse.isPresent(), "Should return the quickfix \"Remove 'bar' and all assignments\"");
		CodeAction unresolvedCodeAction = removeUnusedResponse.get().getRight();

		CodeAction resolvedCodeAction = server.resolveCodeAction(unresolvedCodeAction).join();
		assertNotNull(resolvedCodeAction.getEdit(), "Should resolve the edit property in the resolveCodeAction request");
		String actual = AbstractQuickFixTest.evaluateWorkspaceEdit(resolvedCodeAction.getEdit());
		buf = new StringBuilder();
		buf.append("public class Foo {\n");
		buf.append("    void foo() {    }\n");
		buf.append("}\n");
		assertEquals(buf.toString(), actual);
	}

	// See https://github.com/redhat-developer/vscode-java/issues/1992
	@Test
	public void testResolveCodeAction_AnnotationQuickFixes() throws Exception {
		when(preferenceManager.getClientPreferences().isResolveCodeActionSupported()).thenReturn(true);
		importProjects("maven/salut4");
		IProject proj = WorkspaceHelper.getProject("salut4");
		IJavaProject javaProject = JavaCore.create(proj);
		assertTrue(javaProject.exists());
		IType type = javaProject.findType("org.sample.MyTest");
		ICompilationUnit unit = type.getCompilationUnit();
		assertFalse(unit.getSource().contains("import org.junit.jupiter.api.Test"));
		CodeActionParams params = new CodeActionParams();
		params.setTextDocument(new TextDocumentIdentifier(JDTUtils.toURI(unit)));
		Position position = new Position(3, 5);
		final Range range = new Range(position, position);
		params.setRange(range);
		CodeActionContext context = new CodeActionContext(Arrays.asList(getDiagnostic(Integer.toString(IProblem.UndefinedType), range)), Collections.singletonList(CodeActionKind.QuickFix));
		params.setContext(context);
		List<Either<Command, CodeAction>> quickfixActions = server.codeAction(params).join();
		assertNotNull(quickfixActions);
		assertFalse(quickfixActions.isEmpty(), "No quickfix actions were found");
		Optional<Either<Command, CodeAction>> importTest = quickfixActions.stream().filter(codeAction -> {
			return "Import 'Test' (org.junit.jupiter.api)".equals(codeAction.getRight().getTitle());
		}).findFirst();
		CodeAction codeAction = importTest.get().getRight();
		assertEquals(1, codeAction.getDiagnostics().size());
		CodeAction resolvedCodeAction = server.resolveCodeAction(codeAction).join();
		assertNotNull(resolvedCodeAction.getEdit(), "Should resolve the edit property in the resolveCodeAction request");
		String actual = AbstractQuickFixTest.evaluateWorkspaceEdit(resolvedCodeAction.getEdit());
		assertTrue(actual.contains("import org.junit.jupiter.api.Test"));
	}

	@Test
	public void testResolveCodeAction_Refactors() throws Exception {
		when(preferenceManager.getClientPreferences().isResolveCodeActionSupported()).thenReturn(true);

		StringBuilder buf = new StringBuilder();
		buf.append("interface I {\n");
		buf.append("    void method();\n");
		buf.append("}\n");
		buf.append("public class E {\n");
		buf.append("    public void bar(I i) {}\n");
		buf.append("    public void foo() {\n");
		buf.append("        bar(new I() {\n");
		buf.append("            @Override\n");
		buf.append("            /*[*/public void method() {\n");
		buf.append("                System.out.println();\n");
		buf.append("            }/*]*/\n");
		buf.append("        });\n");
		buf.append("    }\n");
		buf.append("}\n");
		ICompilationUnit unit = defaultPackage.createCompilationUnit("E.java", buf.toString(), false, null);
		CodeActionParams params = new CodeActionParams();
		params.setTextDocument(new TextDocumentIdentifier(JDTUtils.toURI(unit)));
		final Range range = CodeActionUtil.getRange(unit);
		params.setRange(range);

		CodeActionContext context = new CodeActionContext(
			Collections.emptyList(),
			Collections.singletonList(CodeActionKind.Refactor)
		);
		params.setContext(context);

		List<Either<Command, CodeAction>> codeActions = server.codeAction(params).join();
		assertNotNull(codeActions);
		assertFalse(codeActions.isEmpty(), "No refactor actions were found");
		for (Either<Command, CodeAction> codeAction : codeActions) {
			assertNull(codeAction.getRight().getEdit(), "Should defer the edit property to the resolveCodeAction request");
			assertNotNull(codeAction.getRight().getData(), "Should preserve the data property for the unresolved code action");
		}

		Optional<Either<Command, CodeAction>> convertToLambdaResponse = codeActions.stream().filter(codeAction -> {
			return "Convert to lambda expression".equals(codeAction.getRight().getTitle());
		}).findFirst();
		assertTrue(convertToLambdaResponse.isPresent(), "Should return the refactor action 'Convert to lambda expression'");
		CodeAction unresolvedCodeAction = convertToLambdaResponse.get().getRight();

		CodeAction resolvedCodeAction = server.resolveCodeAction(unresolvedCodeAction).join();
		assertNotNull(resolvedCodeAction.getEdit(), "Should resolve the edit property in the resolveCodeAction request");
		String actual = AbstractQuickFixTest.evaluateWorkspaceEdit(resolvedCodeAction.getEdit());
		buf = new StringBuilder();
		buf.append("interface I {\n");
		buf.append("    void method();\n");
		buf.append("}\n");
		buf.append("public class E {\n");
		buf.append("    public void bar(I i) {}\n");
		buf.append("    public void foo() {\n");
		buf.append("        bar(() -> System.out.println());\n");
		buf.append("    }\n");
		buf.append("}\n");
		assertEquals(buf.toString(), actual);
	}

	@Test
	public void testResolveCodeAction_QuickAssists() throws Exception {
		when(preferenceManager.getClientPreferences().isResolveCodeActionSupported()).thenReturn(true);

		StringBuilder buf = new StringBuilder();
		buf.append("public class E {\n");
		buf.append("    public  E(int count) {\n");
		buf.append("    }\n");
		buf.append("}\n");
		ICompilationUnit unit = defaultPackage.createCompilationUnit("E.java", buf.toString(), false, null);
		CodeActionParams params = new CodeActionParams();
		params.setTextDocument(new TextDocumentIdentifier(JDTUtils.toURI(unit)));
		Range range = CodeActionUtil.getRange(unit, "count");
		params.setRange(range);
		CodeActionContext context = new CodeActionContext(
			Collections.emptyList(),
			Collections.singletonList(JavaCodeActionKind.QUICK_ASSIST)
		);
		params.setContext(context);

		List<Either<Command, CodeAction>> codeActions = server.codeAction(params).join();
		assertNotNull(codeActions);
		assertFalse(codeActions.isEmpty(), "No quickassist actions were found");
		for (Either<Command, CodeAction> codeAction : codeActions) {
			assertNull(codeAction.getRight().getEdit(), "Should defer the edit property to the resolveCodeAction request");
			assertNotNull(codeAction.getRight().getData(), "Should preserve the data property for the unresolved code action");
		}

		Optional<Either<Command, CodeAction>> assignToNewFieldResponse = codeActions.stream().filter(codeAction -> {
			return "Assign parameter to new field".equals(codeAction.getRight().getTitle());
		}).findFirst();
		assertTrue(assignToNewFieldResponse.isPresent(), "Should return the quick assist 'Convert to method reference'");
		CodeAction unresolvedCodeAction = assignToNewFieldResponse.get().getRight();

		CodeAction resolvedCodeAction = server.resolveCodeAction(unresolvedCodeAction).join();
		assertNotNull(resolvedCodeAction.getEdit(), "Should resolve the edit property in the resolveCodeAction request");
		String actual = AbstractQuickFixTest.evaluateWorkspaceEdit(resolvedCodeAction.getEdit());
		buf = new StringBuilder();
		buf.append("public class E {\n");
		buf.append("    private int count;\n");
		buf.append("\n");
		buf.append("    public  E(int count) {\n");
		buf.append("        this.count = count;\n");
		buf.append("    }\n");
		buf.append("}\n");
		assertEquals(buf.toString(), actual);
	}

	@Test
	public void testAssignAllParamsToFields() throws Exception {
		when(preferenceManager.getClientPreferences().isResolveCodeActionSupported()).thenReturn(true);

		StringBuilder buf = new StringBuilder();
		buf.append("public class App {\n");
		buf.append("    private String s;\n");
		buf.append("\n");
		buf.append("    public App(String s, String s3, String s2) {\n");
		buf.append("    }\n");
		buf.append("}\n");
		ICompilationUnit unit = defaultPackage.createCompilationUnit("App.java", buf.toString(), false, null);
		CodeActionParams params = new CodeActionParams();
		params.setTextDocument(new TextDocumentIdentifier(JDTUtils.toURI(unit)));
		Range range = CodeActionUtil.getRange(unit, "s3");
		params.setRange(range);
		CodeActionContext context = new CodeActionContext(Collections.emptyList(), Collections.singletonList(JavaCodeActionKind.QUICK_ASSIST));
		params.setContext(context);

		List<Either<Command, CodeAction>> codeActions = server.codeAction(params).join();
		assertNotNull(codeActions);
		assertFalse(codeActions.isEmpty(), "No quickassist actions were found");

		Optional<Either<Command, CodeAction>> assignAllToNewFieldsResponse = codeActions.stream().filter(codeAction -> {
			return "Assign all parameters to new fields".equals(codeAction.getRight().getTitle());
		}).findFirst();
		assertTrue(assignAllToNewFieldsResponse.isPresent(), "Should return the quick assist 'Assign all parameters to new fields'");
		CodeAction unresolvedCodeAction = assignAllToNewFieldsResponse.get().getRight();

		CodeAction resolvedCodeAction = server.resolveCodeAction(unresolvedCodeAction).join();
		assertNotNull(resolvedCodeAction.getEdit(), "Should resolve the edit property in the resolveCodeAction request");
		String actual = AbstractQuickFixTest.evaluateWorkspaceEdit(resolvedCodeAction.getEdit());
		buf = new StringBuilder();
		buf.append("public class App {\n");
		buf.append("    private String s;\n");
		buf.append("    private String s4;\n");
		buf.append("    private String s3;\n");
		buf.append("    private String s2;\n");
		buf.append("\n");
		buf.append("    public App(String s, String s3, String s2) {\n");
		buf.append("        s4 = s;\n");
		buf.append("        this.s3 = s3;\n");
		buf.append("        this.s2 = s2;\n");
		buf.append("    }\n");
		buf.append("}\n");
		assertEquals(buf.toString(), actual);
	}

	@Test
	public void testResolveCodeAction_SourceActions() throws Exception {
		when(preferenceManager.getClientPreferences().isResolveCodeActionSupported()).thenReturn(true);

		StringBuilder buf = new StringBuilder();
		buf.append("public class E {\n");
		buf.append("    private void hello() {\n");
		buf.append("    }\n");
		buf.append("}\n");
		ICompilationUnit unit = defaultPackage.createCompilationUnit("E.java", buf.toString(), false, null);
		CodeActionParams params = new CodeActionParams();
		params.setTextDocument(new TextDocumentIdentifier(JDTUtils.toURI(unit)));
		final Range range = CodeActionUtil.getRange(unit, "hello");
		params.setRange(range);
		CodeActionContext context = new CodeActionContext(
			Collections.emptyList(),
			Collections.singletonList(CodeActionKind.Source)
		);
		params.setContext(context);

		List<Either<Command, CodeAction>> codeActions = server.codeAction(params).join();
		assertNotNull(codeActions);
		assertFalse(codeActions.isEmpty(), "No source actions were found");
		for (Either<Command, CodeAction> codeAction : codeActions) {
			assertNull(codeAction.getRight().getEdit(), "Should defer the edit property to the resolveCodeAction request");
		}

		Optional<Either<Command, CodeAction>> generateToStringResponse = codeActions.stream().filter(codeAction -> {
			return "Generate toString()".equals(codeAction.getRight().getTitle());
		}).findFirst();
		assertTrue(generateToStringResponse.isPresent(), "Should return the quick assist 'Generate toString()'");
		CodeAction unresolvedCodeAction = generateToStringResponse.get().getRight();
		assertNotNull(unresolvedCodeAction.getData(), "Should preserve the data property for the unresolved code action");

		CodeAction resolvedCodeAction = server.resolveCodeAction(unresolvedCodeAction).join();
		assertNotNull(resolvedCodeAction.getEdit(), "Should resolve the edit property in the resolveCodeAction request");
		String actual = AbstractQuickFixTest.evaluateWorkspaceEdit(resolvedCodeAction.getEdit());
		buf = new StringBuilder();
		buf.append("public class E {\n");
		buf.append("    private void hello() {\n");
		buf.append("    }\n");
		buf.append("\n");
		buf.append("    @Override\n");
		buf.append("    public String toString() {\n");
		buf.append("        return \"E []\";\n");
		buf.append("    }\n");
		buf.append("}\n");
		assertEquals(buf.toString(), actual);
	}

	@Test
	public void testResolveCodeAction_LinkedProposals() throws Exception {
		when(preferenceManager.getClientPreferences().isResolveCodeActionSupported()).thenReturn(true);
		when(preferenceManager.getClientPreferences().isWorkspaceSnippetEditSupported()).thenReturn(true);
		when(preferenceManager.getClientPreferences().isResourceOperationSupported()).thenReturn(true);

		StringBuilder buf = new StringBuilder();
		buf.append("import java.util.HashMap;\n");
		buf.append("import java.util.List;\n");
		buf.append("public class E {\n");
		buf.append("    private void hello() {\n");
		buf.append("        List<String> test = new HashMap();\n");
		buf.append("    }\n");
		buf.append("}\n");
		ICompilationUnit unit = defaultPackage.createCompilationUnit("E.java", buf.toString(), false, null);
		CodeActionParams params = new CodeActionParams();
		params.setTextDocument(new TextDocumentIdentifier(JDTUtils.toURI(unit)));
		final Range range = CodeActionUtil.getRange(unit, "new HashMap()");
		params.setRange(range);
		CodeActionContext context = new CodeActionContext(Arrays.asList(getDiagnostic(Integer.toString(IProblem.TypeMismatch), range)), Collections.singletonList(CodeActionKind.QuickFix));
		params.setContext(context);

		List<Either<Command, CodeAction>> quickfixActions = server.codeAction(params).join();
		assertFalse(quickfixActions.isEmpty(), "No quickfix actions were found");
		for (Either<Command, CodeAction> codeAction : quickfixActions) {
			assertNull(codeAction.getRight().getEdit(), "Should defer the edit property to the resolveCodeAction request");
			assertNotNull(codeAction.getRight().getData(), "Should preserve the data property for the unresolved code action");
		}

		Optional<Either<Command, CodeAction>> removeUnusedResponse = quickfixActions.stream().filter(codeAction -> {
			return "Change type of 'test' to 'HashMap'".equals(codeAction.getRight().getTitle());
		}).findFirst();
		assertTrue(removeUnusedResponse.isPresent(), "Should return the quickfix \"Change type of 'test' to 'HashMap'\"");
		CodeAction unresolvedCodeAction = removeUnusedResponse.get().getRight();

		CodeAction resolvedCodeAction = server.resolveCodeAction(unresolvedCodeAction).join();
		assertNotNull(resolvedCodeAction.getEdit(), "Should resolve the edit property in the resolveCodeAction request");
		List<TextEdit> edits = resolvedCodeAction.getEdit().getDocumentChanges().get(0).getLeft().getEdits();
		assertTrue(edits.get(0) instanceof SnippetTextEdit);
		assertEquals(((SnippetTextEdit) edits.get(0)).getSnippet().getValue(), "${1|HashMap,Map,Cloneable,Serializable,AbstractMap,Object|}");
	}

	// https://github.com/redhat-developer/vscode-java/issues/3905
	@Test
	public void testGH3905() throws Exception {
		when(preferenceManager.getClientPreferences().isResolveCodeActionSupported()).thenReturn(true);
		when(preferenceManager.getClientPreferences().isWorkspaceSnippetEditSupported()).thenReturn(true);
		when(preferenceManager.getClientPreferences().isResourceOperationSupported()).thenReturn(true);

		StringBuilder buf = new StringBuilder();
		buf.append("public class E {\n");
		buf.append("    public class SuperType {\n");
		buf.append("        public SuperType(String name) {}\n");
		buf.append("    }\n");
		buf.append("    public class SubType extends SuperType {\n");
		buf.append("    }\n");
		buf.append("}");
		ICompilationUnit unit = defaultPackage.createCompilationUnit("E.java", buf.toString(), false, null);
		CodeActionParams params = new CodeActionParams();
		params.setTextDocument(new TextDocumentIdentifier(JDTUtils.toURI(unit)));
		final Range range = CodeActionUtil.getRange(unit, "SubType");
		params.setRange(range);
		CodeActionContext context = new CodeActionContext(Arrays.asList(getDiagnostic(Integer.toString(IProblem.UndefinedConstructorInDefaultConstructor), range)), Collections.singletonList(CodeActionKind.QuickFix));
		params.setContext(context);

		List<Either<Command, CodeAction>> quickfixActions = server.codeAction(params).join();
		assertFalse(quickfixActions.isEmpty(), "No quickfix actions were found");
		for (Either<Command, CodeAction> codeAction : quickfixActions) {
			assertNull(codeAction.getRight().getEdit(), "Should defer the edit property to the resolveCodeAction request");
			assertNotNull(codeAction.getRight().getData(), "Should preserve the data property for the unresolved code action");
		}

		Optional<Either<Command, CodeAction>> unresolvedResponse = quickfixActions.stream().filter(codeAction -> {
			return "Add constructor 'SubType(String)'".equals(codeAction.getRight().getTitle());
		}).findFirst();
		assertTrue(unresolvedResponse.isPresent(), "Should return the quickfix \"Add constructor 'SubType(String)'\"");
		CodeAction unresolvedCodeAction = unresolvedResponse.get().getRight();

		CodeAction resolvedCodeAction = server.resolveCodeAction(unresolvedCodeAction).join();
		assertNotNull(resolvedCodeAction.getEdit(), "Should resolve the edit property in the resolveCodeAction request");
		List<TextEdit> edits = resolvedCodeAction.getEdit().getDocumentChanges().get(0).getLeft().getEdits();
		assertTrue(edits.get(0) instanceof SnippetTextEdit);
		assertEquals(((SnippetTextEdit) edits.get(0)).getSnippet().getValue(), "\n\n        public SubType(${1:String} ${2:name}) {\n            super(name);\n            //TODO Auto-generated constructor stub\n        }");
	}

	// https://github.com/redhat-developer/vscode-java/issues/4138
	@Test
	public void testCreateMethod() throws Exception {
		testCreateMethod(true);
	}

	//https://github.com/redhat-developer/vscode-java/issues/4320
	@Test
	public void testCreateMethodNoBufferValidation() throws Exception {
		testCreateMethod(false);
	}

	private void testCreateMethod(boolean validateAllOpenBuffersOnChanges) throws Exception {
		ClientPreferences clientPreferences = preferenceManager.getClientPreferences();
		when(clientPreferences.isWorkspaceSnippetEditSupported()).thenReturn(true);
		when(clientPreferences.isResolveCodeActionSupported()).thenReturn(true);
		when(clientPreferences.isResourceOperationSupported()).thenReturn(true);
		preferenceManager.getPreferences().setValidateAllOpenBuffersOnChanges(validateAllOpenBuffersOnChanges);
		String content = """
				package test1;
				import java.util.HashMap;
				import java.util.Map;

				public class E {
					private Test test = new Test();
					private void get() {
						Map<String,String> user = new HashMap<>();
						this.test.create(user);
					}
					class Test {

					}
				}
				""";
		ICompilationUnit unit = defaultPackage.createCompilationUnit("E.java", content, true, null);
		CodeActionParams params = new CodeActionParams();
		params.setTextDocument(new TextDocumentIdentifier(JDTUtils.toURI(unit)));
		final Range range = CodeActionUtil.getRange(unit, "create");
		params.setRange(range);
		CodeActionContext context = new CodeActionContext(Arrays.asList(getDiagnostic(Integer.toString(IProblem.UndefinedMethod), range)), Collections.singletonList(CodeActionKind.QuickFix));
		params.setContext(context);

		List<Either<Command, CodeAction>> quickfixActions = server.codeAction(params).join();
		assertFalse(quickfixActions.isEmpty(), "No quickfix actions were found");
		for (Either<Command, CodeAction> codeAction : quickfixActions) {
			assertNull(codeAction.getRight().getEdit(), "Should defer the edit property to the resolveCodeAction request");
			assertNotNull(codeAction.getRight().getData(), "Should preserve the data property for the unresolved code action");
		}

		Optional<Either<Command, CodeAction>> unresolvedResponse = quickfixActions.stream().filter(codeAction -> {
			return "Create method 'create(Map<String, String>)' in type 'Test'".equals(codeAction.getRight().getTitle());
		}).findFirst();
		assertTrue(unresolvedResponse.isPresent(), "Should return the quickfix \"Create method 'create(Map<String, String>)' in type 'Test'\"");
		CodeAction unresolvedCodeAction = unresolvedResponse.get().getRight();

		CodeAction resolvedCodeAction = server.resolveCodeAction(unresolvedCodeAction).join();
		assertNotNull(resolvedCodeAction.getEdit(), "Should resolve the edit property in the resolveCodeAction request");
		List<TextEdit> edits = resolvedCodeAction.getEdit().getDocumentChanges().get(0).getLeft().getEdits();
		assertTrue(edits.get(0) instanceof SnippetTextEdit);
		SnippetTextEdit snippetTextEdit = (SnippetTextEdit) edits.get(0);
		String snippet = snippetTextEdit.getSnippet().getValue();
		assertTrue(snippet.contains("${1:create}(${4|Map<String\\,String>|}"));
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
}
