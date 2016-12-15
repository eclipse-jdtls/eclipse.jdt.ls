/*******************************************************************************
 * Copyright (c) 2016 Red Hat Inc. and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Red Hat Inc. - initial API and implementation
 *******************************************************************************/
package org.jboss.tools.vscode.java.internal.handlers;


import static org.jboss.tools.vscode.java.internal.Lsp4jAssertions.assertTextEdit;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import java.util.List;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.Path;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.WorkingCopyOwner;
import org.eclipse.lsp4j.CompletionItem;
import org.eclipse.lsp4j.CompletionItemKind;
import org.eclipse.lsp4j.CompletionList;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.TextEdit;
import org.jboss.tools.vscode.java.internal.JDTUtils;
import org.jboss.tools.vscode.java.internal.JavaClientConnection;
import org.jboss.tools.vscode.java.internal.JsonMessageHelper;
import org.jboss.tools.vscode.java.internal.LanguageServerWorkingCopyOwner;
import org.jboss.tools.vscode.java.internal.WorkspaceHelper;
import org.jboss.tools.vscode.java.internal.managers.AbstractProjectsManagerBasedTest;
import org.junit.Before;
import org.junit.ComparisonFailure;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

/**
 * @author Gorkem Ercan
 *
 */
@RunWith(MockitoJUnitRunner.class)
public class CompletionHandlerTest extends AbstractProjectsManagerBasedTest{

	private static String COMPLETION_TEMPLATE =
			"{\n" +
					"    \"id\": \"1\",\n" +
					"    \"method\": \"textDocument/hover\",\n" +
					"    \"params\": {\n" +
					"        \"textDocument\": {\n" +
					"            \"uri\": \"${file}\"\n" +
					"        },\n" +
					"        \"position\": {\n" +
					"            \"line\": ${line},\n" +
					"            \"character\": ${char}\n" +
					"        }\n" +
					"    },\n" +
					"    \"jsonrpc\": \"2.0\"\n" +
					"}";

	@Mock
	private JavaClientConnection connection;

	private JDTLanguageServer server;
	private WorkingCopyOwner wcOwner ;
	private IProject project;



	@Before
	public void setup() throws Exception{
		importProjects("eclipse/hello");
		project = WorkspaceHelper.getProject("hello");
		wcOwner = new LanguageServerWorkingCopyOwner(connection);
		server= new JDTLanguageServer(projectsManager);

	}

	@Test
	public void testCompletion_1() throws JavaModelException{
		ICompilationUnit unit = getWorkingCopy(
				"src/java/Foo.java",
				"public class Foo {\n"+
						"	void foo() {\n"+
						"		Objec\n"+
						"	}\n"+
				"}\n");
		int[] loc = findCompletionLocation(unit, "Objec");

		CompletionList list = server.completion(JsonMessageHelper.getParams(createCompletionRequest(unit, loc[0], loc[1]))).join();
		assertNotNull(list);
	}


	@Test
	public void testCompletion_2() throws JavaModelException{
		ICompilationUnit unit = getWorkingCopy(
				"src/java/Foo.java",
				"import java.sq \n" +
						"public class Foo {\n"+
						"	void foo() {\n"+
						"	}\n"+
				"}\n");

		int[] loc = findCompletionLocation(unit, "java.sq");


		CompletionList list = server.completion(JsonMessageHelper.getParams(createCompletionRequest(unit, loc[0], loc[1]))).join();

		assertNotNull(list);
		assertEquals(1, list.getItems().size());
		CompletionItem item = list.getItems().get(0);
		// Check completion item
		assertNull(item.getInsertText());
		assertEquals("java.sql",item.getLabel());
		assertEquals(CompletionItemKind.Module, item.getKind() );
		assertEquals("cbf", item.getSortText());
		assertNotNull(item.getTextEdit());
		TextEdit te = item.getTextEdit();
		assertEquals("java.sql.*;",te.getNewText());
		assertNotNull(te.getRange());
		Range range = te.getRange();
		assertEquals(0, range.getStart().getLine());
		assertEquals(7, range.getStart().getCharacter());
		assertEquals(0, range.getEnd().getLine());
		//Not checking the range end character
	}

	@Test
	public void testCompletion_3() throws JavaModelException{
		ICompilationUnit unit = getWorkingCopy(
				"src/java/Foo.java",
				"public class Foo {\n"+
						"	void foo() {\n"+
						"System.out.print(\"Hello\");\n" +
						"System.out.println(\" World!\");\n"+
						"HashMap<String, String> map = new HashMap<>();\n"+
						"map.pu\n" +
						"	}\n"+
				"}\n");

		int[] loc = findCompletionLocation(unit, "map.pu");

		CompletionList list = server.completion(JsonMessageHelper.getParams(createCompletionRequest(unit, loc[0], loc[1]))).join();
		System.out.println(list.toString());
		assertNotNull(list);
		CompletionItem ci = list.getItems().stream()
				.filter( item->  item.getLabel().matches("put\\(String \\w+, String \\w+\\) : String"))
				.findFirst().orElse(null);
		assertNotNull(ci);

		assertNull(ci.getInsertText());
		assertEquals(CompletionItemKind.Function, ci.getKind());
		assertEquals("abj", ci.getSortText());
		try {
			assertTextEdit(5, 4, 6, "put({{key}}, {{value}})", ci.getTextEdit());
		} catch (ComparisonFailure e) {
			//In case the JDK has no sources
			assertTextEdit(5, 4, 6, "put({{arg0}}, {{arg1}})", ci.getTextEdit());
		}
		assertNotNull(ci.getAdditionalTextEdits());
		List<TextEdit> edits = ci.getAdditionalTextEdits();
		assertEquals(3, edits.size());

	}

	@Test
	public void testCompletion_4() throws JavaModelException{
		ICompilationUnit unit = getWorkingCopy(
				"src/java/Foo.java",
				"import java.sq \n" +
						"public class Foo {\n"+
						"	void foo() {\n"+
						"   java.util.Ma\n"+
						"	}\n"+
				"}\n");

		int[] loc = findCompletionLocation(unit, "java.util.Ma");

		CompletionList list = server.completion(JsonMessageHelper.getParams(createCompletionRequest(unit, loc[0], loc[1]))).join();

		System.out.println(list.toString());
		assertNotNull(list);
		assertEquals(1, list.getItems().size());
		CompletionItem item = list.getItems().get(0);
		assertEquals(CompletionItemKind.Class, item.getKind());
		assertNull(item.getInsertText());
		assertNull(item.getAdditionalTextEdits());
		assertTextEdit(3,3,15,"java.util.Map",item.getTextEdit());


		//Not checking the range end character
	}



	/**
	 * @param unit
	 * @param str
	 * @return
	 * @throws JavaModelException
	 */
	private int[] findCompletionLocation(ICompilationUnit unit, String completeBehind) throws JavaModelException {
		String str= unit.getSource();
		int cursorLocation = str.lastIndexOf(completeBehind) + completeBehind.length();
		return JsonRpcHelpers.toLine(unit.getBuffer(), cursorLocation);
	}




	public ICompilationUnit getWorkingCopy(String path, String source) throws JavaModelException {
		ICompilationUnit workingCopy = getCompilationUnit(path);
		workingCopy.getWorkingCopy(wcOwner,null/*no progress monitor*/);
		workingCopy.getBuffer().setContents(source);
		workingCopy.makeConsistent(null/*no progress monitor*/);
		return workingCopy;
	}

	protected ICompilationUnit getCompilationUnit(String path) {
		return (ICompilationUnit)JavaCore.create(getFile(path));
	}

	protected IFile getFile(String path) {
		return project.getFile(new Path(path));
	}

	private String createCompletionRequest(ICompilationUnit unit, int line, int kar) {
		return COMPLETION_TEMPLATE.replace("${file}", JDTUtils.getFileURI(unit))
				.replace("${line}", String.valueOf(line))
				.replace("${char}", String.valueOf(kar));
	}

}
