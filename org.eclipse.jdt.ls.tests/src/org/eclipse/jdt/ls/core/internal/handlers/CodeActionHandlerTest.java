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

import java.util.Arrays;
import java.util.List;

import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.compiler.IProblem;
import org.eclipse.jdt.ls.core.internal.JDTUtils;
import org.eclipse.jdt.ls.core.internal.JavaClientConnection;
import org.eclipse.jdt.ls.core.internal.LanguageServerWorkingCopyOwner;
import org.eclipse.jdt.ls.core.internal.WorkspaceHelper;
import org.eclipse.lsp4j.CodeActionContext;
import org.eclipse.lsp4j.CodeActionParams;
import org.eclipse.lsp4j.Command;
import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.DiagnosticSeverity;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

/**
 * @author Gorkem Ercan
 *
 */
@RunWith(MockitoJUnitRunner.class)
public class CodeActionHandlerTest extends AbstractCompletionBasedTest {

	@Mock
	private JavaClientConnection connection;


	@Override
	@Before
	public void setup() throws Exception{
		importProjects("eclipse/hello");
		project = WorkspaceHelper.getProject("hello");
		wcOwner = new LanguageServerWorkingCopyOwner(connection);
		server= new JDTLanguageServer(projectsManager, null);
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
		params.setTextDocument(new TextDocumentIdentifier(JDTUtils.getFileURI(unit)));
		final Range range = getRange(unit, "java.sql");
		params.setRange(range);
		params.setContext(new CodeActionContext(Arrays.asList(getDiagnostic(Integer.toString(IProblem.UnusedImport), range))));
		List<? extends Command> commands = server.codeAction(params).join();
		Assert.assertNotNull(commands);
		Assert.assertEquals(1, commands.size());
		Command c = commands.get(0);
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
		params.setTextDocument(new TextDocumentIdentifier(JDTUtils.getFileURI(unit)));
		final Range range = getRange(unit, "some str");
		params.setRange(range);
		params.setContext(new CodeActionContext(Arrays.asList(getDiagnostic(Integer.toString(IProblem.UnterminatedString), range))));
		List<? extends Command> commands = server.codeAction(params).join();
		Assert.assertNotNull(commands);
		Assert.assertEquals(1, commands.size());
		Command c = commands.get(0);
		Assert.assertEquals(CodeActionHandler.COMMAND_ID_APPLY_EDIT, c.getCommand());
	}

	private Range getRange(ICompilationUnit unit, String search) throws JavaModelException {
		String str= unit.getSource();
		int start = str.lastIndexOf(search);
		return JDTUtils.toRange(unit, start, search.length());
	}

	private Diagnostic getDiagnostic(String code, Range range){
		Diagnostic $ = new Diagnostic();
		$.setCode(code);
		$.setRange(range);
		$.setSeverity(DiagnosticSeverity.Error);
		$.setMessage("Test Diagnostic");
		return $;
	}

}
