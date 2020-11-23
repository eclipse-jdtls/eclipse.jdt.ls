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

import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IPackageFragment;
import org.eclipse.jdt.core.IPackageFragmentRoot;
import org.eclipse.jdt.core.compiler.IProblem;
import org.eclipse.jdt.ls.core.internal.CodeActionUtil;
import org.eclipse.jdt.ls.core.internal.JDTUtils;
import org.eclipse.jdt.ls.core.internal.JavaClientConnection;
import org.eclipse.jdt.ls.core.internal.JavaCodeActionKind;
import org.eclipse.jdt.ls.core.internal.JavaLanguageServerPlugin;
import org.eclipse.jdt.ls.core.internal.LanguageServerWorkingCopyOwner;
import org.eclipse.jdt.ls.core.internal.correction.AbstractQuickFixTest;
import org.eclipse.jdt.ls.core.internal.correction.TestOptions;
import org.eclipse.lsp4j.CodeAction;
import org.eclipse.lsp4j.CodeActionContext;
import org.eclipse.lsp4j.CodeActionKind;
import org.eclipse.lsp4j.CodeActionParams;
import org.eclipse.lsp4j.Command;
import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.DiagnosticSeverity;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class CodeActionResolveHandlerTest extends AbstractCompilationUnitBasedTest {
	private IPackageFragment defaultPackage;
	@Mock
	private JavaClientConnection connection;

	@Override
	@Before
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
		Assert.assertNotNull(quickfixActions);
		Assert.assertFalse("No quickfix actions were found", quickfixActions.isEmpty());
		for (Either<Command, CodeAction> codeAction : quickfixActions) {
			Assert.assertNull("Should defer the edit property to the resolveCodeAction request", codeAction.getRight().getEdit());
			Assert.assertNotNull("Should preserve the data property for the unresolved code action", codeAction.getRight().getData());
		}

		Optional<Either<Command, CodeAction>> removeUnusedResponse = quickfixActions.stream().filter(codeAction -> {
			return "Remove 'bar' and all assignments".equals(codeAction.getRight().getTitle());
		}).findFirst();
		Assert.assertTrue("Should return the quickfix \"Remove 'bar' and all assignments\"", removeUnusedResponse.isPresent());
		CodeAction unresolvedCodeAction = removeUnusedResponse.get().getRight();

		CodeAction resolvedCodeAction = server.resolveCodeAction(unresolvedCodeAction).join();
		Assert.assertNotNull("Should resolve the edit property in the resolveCodeAction request", resolvedCodeAction.getEdit());
		String actual = AbstractQuickFixTest.evaluateWorkspaceEdit(resolvedCodeAction.getEdit());
		buf = new StringBuilder();
		buf.append("public class Foo {\n");
		buf.append("    void foo() {    }\n");
		buf.append("}\n");
		Assert.assertEquals(buf.toString(), actual);
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
		Assert.assertNotNull(codeActions);
		Assert.assertFalse("No refactor actions were found", codeActions.isEmpty());
		for (Either<Command, CodeAction> codeAction : codeActions) {
			Assert.assertNull("Should defer the edit property to the resolveCodeAction request", codeAction.getRight().getEdit());
			Assert.assertNotNull("Should preserve the data property for the unresolved code action", codeAction.getRight().getData());
		}

		Optional<Either<Command, CodeAction>> convertToLambdaResponse = codeActions.stream().filter(codeAction -> {
			return "Convert to lambda expression".equals(codeAction.getRight().getTitle());
		}).findFirst();
		Assert.assertTrue("Should return the refactor action 'Convert to lambda expression'", convertToLambdaResponse.isPresent());
		CodeAction unresolvedCodeAction = convertToLambdaResponse.get().getRight();

		CodeAction resolvedCodeAction = server.resolveCodeAction(unresolvedCodeAction).join();
		Assert.assertNotNull("Should resolve the edit property in the resolveCodeAction request", resolvedCodeAction.getEdit());
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
		Assert.assertEquals(buf.toString(), actual);
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
		Assert.assertNotNull(codeActions);
		Assert.assertFalse("No quickassist actions were found", codeActions.isEmpty());
		for (Either<Command, CodeAction> codeAction : codeActions) {
			Assert.assertNull("Should defer the edit property to the resolveCodeAction request", codeAction.getRight().getEdit());
			Assert.assertNotNull("Should preserve the data property for the unresolved code action", codeAction.getRight().getData());
		}

		Optional<Either<Command, CodeAction>> assignToNewFieldResponse = codeActions.stream().filter(codeAction -> {
			return "Assign parameter to new field".equals(codeAction.getRight().getTitle());
		}).findFirst();
		Assert.assertTrue("Should return the quick assist 'Convert to method reference'", assignToNewFieldResponse.isPresent());
		CodeAction unresolvedCodeAction = assignToNewFieldResponse.get().getRight();

		CodeAction resolvedCodeAction = server.resolveCodeAction(unresolvedCodeAction).join();
		Assert.assertNotNull("Should resolve the edit property in the resolveCodeAction request", resolvedCodeAction.getEdit());
		String actual = AbstractQuickFixTest.evaluateWorkspaceEdit(resolvedCodeAction.getEdit());
		buf = new StringBuilder();
		buf.append("public class E {\n");
		buf.append("    private int count;\n");
		buf.append("\n");
		buf.append("    public  E(int count) {\n");
		buf.append("        this.count = count;\n");
		buf.append("    }\n");
		buf.append("}\n");
		Assert.assertEquals(buf.toString(), actual);
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
		Assert.assertNotNull(codeActions);
		Assert.assertFalse("No source actions were found", codeActions.isEmpty());
		for (Either<Command, CodeAction> codeAction : codeActions) {
			Assert.assertNull("Should defer the edit property to the resolveCodeAction request", codeAction.getRight().getEdit());
		}

		Optional<Either<Command, CodeAction>> generateConstructorResponse = codeActions.stream().filter(codeAction -> {
			return "Generate Constructors".equals(codeAction.getRight().getTitle());
		}).findFirst();
		Assert.assertTrue("Should return the quick assist 'Convert to lambda expression'", generateConstructorResponse.isPresent());
		CodeAction unresolvedCodeAction = generateConstructorResponse.get().getRight();
		Assert.assertNotNull("Should preserve the data property for the unresolved code action", unresolvedCodeAction.getData());

		CodeAction resolvedCodeAction = server.resolveCodeAction(unresolvedCodeAction).join();
		Assert.assertNotNull("Should resolve the edit property in the resolveCodeAction request", resolvedCodeAction.getEdit());
		String actual = AbstractQuickFixTest.evaluateWorkspaceEdit(resolvedCodeAction.getEdit());
		buf = new StringBuilder();
		buf.append("public class E {\n");
		buf.append("    private void hello() {\n");
		buf.append("    }\n");
		buf.append("\n");
		buf.append("    /**\n");
		buf.append("     * \n");
		buf.append("     */\n");
		buf.append("    public E() {\n");
		buf.append("    }\n");
		buf.append("}\n");
		Assert.assertEquals(buf.toString(), actual);
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
