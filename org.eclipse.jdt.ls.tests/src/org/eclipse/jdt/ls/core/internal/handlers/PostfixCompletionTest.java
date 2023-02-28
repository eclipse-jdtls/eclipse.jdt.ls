/*******************************************************************************
* Copyright (c) 2022 Microsoft Corporation and others.
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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.manipulation.CoreASTProvider;
import org.eclipse.jdt.ls.core.internal.JDTUtils;
import org.eclipse.jdt.ls.core.internal.JavaClientConnection;
import org.eclipse.jdt.ls.core.internal.JsonMessageHelper;
import org.eclipse.lsp4j.CompletionItem;
import org.eclipse.lsp4j.CompletionItemKind;
import org.eclipse.lsp4j.CompletionList;
import org.eclipse.lsp4j.InsertTextFormat;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.TextEdit;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class PostfixCompletionTest extends AbstractCompilationUnitBasedTest {

	private JavaClientConnection javaClient;
	private static String COMPLETION_TEMPLATE =
			"{\n" +
					"    \"id\": \"1\",\n" +
					"    \"method\": \"textDocument/completion\",\n" +
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

	@Before
	public void setUp() {
		mockLSP3Client();
		CoreASTProvider sharedASTProvider = CoreASTProvider.getInstance();
		sharedASTProvider.disposeAST();
		javaClient = new JavaClientConnection(client);
		preferences.setPostfixCompletionEnabled(true);
	}

	@After
	public void tearDown() throws Exception {
		javaClient.disconnect();
		preferences.setPostfixCompletionEnabled(false);
	}

	@Test
	public void test_cast() throws JavaModelException {
		//@formatter:off
		ICompilationUnit unit = getWorkingCopy(
			"src/org/sample/Test.java",
			"package org.sample;\n" +
			"public class Test {\n" +
			"	public void testMethod(String a) {\n" +
			"		a.cast" +
			"	}\n" +
			"}"
		);
		//@formatter:on
		CompletionList list = requestCompletions(unit, "a.cast");

		assertNotNull(list);

		List<CompletionItem> items = new ArrayList<>(list.getItems());
		CompletionItem item = items.get(0);
		assertEquals("cast", item.getLabel());
		assertEquals(item.getInsertText(), "((${1})a)${0}");
		assertEquals(item.getInsertTextFormat(), InsertTextFormat.Snippet);
		Range range = item.getAdditionalTextEdits().get(0).getRange();
		assertEquals(new Range(new Position(3, 2), new Position(3, 8)), range);
	}

	@Test
	public void test_if() throws JavaModelException {
		//@formatter:off
		ICompilationUnit unit = getWorkingCopy(
			"src/org/sample/Test.java",
			"package org.sample;\n" +
			"public class Test {\n" +
			"	public void testMethod(boolean a) {\n" +
			"		a.if" +
			"	}\n" +
			"}"
		);
		//@formatter:on
		CompletionList list = requestCompletions(unit, "a.if");

		assertNotNull(list);

		List<CompletionItem> items = new ArrayList<>(list.getItems());
		CompletionItem item = items.get(0);
		assertEquals("if", item.getLabel());
		assertEquals(item.getInsertText(), "if (a) {\n\t${0}\n}");
		assertEquals(item.getInsertTextFormat(), InsertTextFormat.Snippet);
		Range range = item.getAdditionalTextEdits().get(0).getRange();
		assertEquals(new Range(new Position(3, 2), new Position(3, 6)), range);
	}

	@Test
	public void test_else() throws JavaModelException {
		//@formatter:off
		ICompilationUnit unit = getWorkingCopy(
			"src/org/sample/Test.java",
			"package org.sample;\n" +
			"public class Test {\n" +
			"	public void testMethod(boolean a) {\n" +
			"		a.else" +
			"	}\n" +
			"}"
		);
		//@formatter:on
		CompletionList list = requestCompletions(unit, "a.else");

		assertNotNull(list);

		List<CompletionItem> items = new ArrayList<>(list.getItems());
		CompletionItem item = items.get(0);
		assertEquals("else", item.getLabel());
		assertEquals(item.getInsertText(), "if (!a) {\n\t${0}\n}");
		assertEquals(item.getInsertTextFormat(), InsertTextFormat.Snippet);
		Range range = item.getAdditionalTextEdits().get(0).getRange();
		assertEquals(new Range(new Position(3, 2), new Position(3, 8)), range);
	}

	@Test
	public void test_for() throws JavaModelException {
		//@formatter:off
		ICompilationUnit unit = getWorkingCopy(
			"src/org/sample/Test.java",
			"package org.sample;\n" +
			"public class Test {\n" +
			"	public void testMethod(String[] a) {\n" +
			"		a.for" +
			"	}\n" +
			"}"
		);
		//@formatter:on
		CompletionList list = requestCompletions(unit, "a.for");

		assertNotNull(list);

		List<CompletionItem> items = new ArrayList<>(list.getItems());
		CompletionItem item = items.get(0);
		assertEquals("for", item.getLabel());
		assertEquals(item.getInsertText(), "for (String ${1:a2} : a) {\n\t${0}\n}");
		assertEquals(item.getInsertTextFormat(), InsertTextFormat.Snippet);
		Range range = item.getAdditionalTextEdits().get(0).getRange();
		assertEquals(new Range(new Position(3, 2), new Position(3, 7)), range);
	}

	@Test
	public void test_fori() throws JavaModelException {
		//@formatter:off
		ICompilationUnit unit = getWorkingCopy(
			"src/org/sample/Test.java",
			"package org.sample;\n" +
			"public class Test {\n" +
			"	public void testMethod(String[] a) {\n" +
			"		a.fori" +
			"	}\n" +
			"}"
		);
		//@formatter:on
		CompletionList list = requestCompletions(unit, "a.fori");

		assertNotNull(list);

		List<CompletionItem> items = new ArrayList<>(list.getItems());
		CompletionItem item = items.get(0);
		assertEquals("fori", item.getLabel());
		assertEquals(item.getInsertText(), "for (int ${1:a2} = 0; ${1:a2} < a.length; ${1:a2}++) {\n\t${0}\n}");
		assertEquals(item.getInsertTextFormat(), InsertTextFormat.Snippet);
		Range range = item.getAdditionalTextEdits().get(0).getRange();
		assertEquals(new Range(new Position(3, 2), new Position(3, 8)), range);
	}

	@Test
	public void test_forr() throws JavaModelException {
		//@formatter:off
		ICompilationUnit unit = getWorkingCopy(
			"src/org/sample/Test.java",
			"package org.sample;\n" +
			"public class Test {\n" +
			"	public void testMethod(String[] a) {\n" +
			"		a.forr" +
			"	}\n" +
			"}"
		);
		//@formatter:on
		CompletionList list = requestCompletions(unit, "a.forr");

		assertNotNull(list);

		List<CompletionItem> items = new ArrayList<>(list.getItems());
		CompletionItem item = items.get(0);
		assertEquals("forr", item.getLabel());
		assertEquals(item.getInsertText(), "for (int ${1:a2} = a.length - 1; ${1:a2} >= 0; ${1:a2}--) {\n\t${0}\n}");
		assertEquals(item.getInsertTextFormat(), InsertTextFormat.Snippet);
		Range range = item.getAdditionalTextEdits().get(0).getRange();
		assertEquals(new Range(new Position(3, 2), new Position(3, 8)), range);
	}

	@Test
	public void test_nnull() throws JavaModelException {
		//@formatter:off
		ICompilationUnit unit = getWorkingCopy(
			"src/org/sample/Test.java",
			"package org.sample;\n" +
			"public class Test {\n" +
			"	public void testMethod(String a) {\n" +
			"		a.nnull" +
			"	}\n" +
			"}"
		);
		//@formatter:on
		CompletionList list = requestCompletions(unit, "a.nnull");

		assertNotNull(list);

		List<CompletionItem> items = new ArrayList<>(list.getItems());
		CompletionItem item = items.get(0);
		assertEquals("nnull", item.getLabel());
		assertEquals(item.getInsertText(), "if (a != null) {\n\t${0}\n}");
		assertEquals(item.getInsertTextFormat(), InsertTextFormat.Snippet);
		Range range = item.getAdditionalTextEdits().get(0).getRange();
		assertEquals(new Range(new Position(3, 2), new Position(3, 9)), range);
	}

	@Test
	public void test_null() throws JavaModelException {
		//@formatter:off
		ICompilationUnit unit = getWorkingCopy(
			"src/org/sample/Test.java",
			"package org.sample;\n" +
			"public class Test {\n" +
			"	public void testMethod(String a) {\n" +
			"		a.null" +
			"	}\n" +
			"}"
		);
		//@formatter:on
		CompletionList list = requestCompletions(unit, "a.null");

		assertNotNull(list);

		List<CompletionItem> items = new ArrayList<>(list.getItems());
		CompletionItem item = items.get(0);
		assertEquals("null", item.getLabel());
		assertEquals(item.getInsertText(), "if (a == null) {\n\t${0}\n}");
		assertEquals(item.getInsertTextFormat(), InsertTextFormat.Snippet);
		Range range = item.getAdditionalTextEdits().get(0).getRange();
		assertEquals(new Range(new Position(3, 2), new Position(3, 8)), range);
	}

	@Test
	public void test_sysout() throws JavaModelException {
		//@formatter:off
		ICompilationUnit unit = getWorkingCopy(
			"src/org/sample/Test.java",
			"package org.sample;\n" +
			"public class Test {\n" +
			"	public void testMethod(String a) {\n" +
			"		a.sysout" +
			"	}\n" +
			"}"
		);
		//@formatter:on
		CompletionList list = requestCompletions(unit, "a.sysout");

		assertNotNull(list);

		List<CompletionItem> items = new ArrayList<>(list.getItems());
		CompletionItem item = items.get(0);
		assertEquals("sysout", item.getLabel());
		assertEquals(item.getInsertText(), "System.out.println(a);${0}");
		assertEquals(item.getInsertTextFormat(), InsertTextFormat.Snippet);
		Range range = item.getAdditionalTextEdits().get(0).getRange();
		assertEquals(new Range(new Position(3, 2), new Position(3, 10)), range);
	}

	@Test
	public void test_throw() throws JavaModelException {
		//@formatter:off
		ICompilationUnit unit = getWorkingCopy(
			"src/org/sample/Test.java",
			"package org.sample;\n" +
			"public class Test {\n" +
			"	public void testMethod() {\n" +
			"		Exception e;\n" +
			"		e.throw" +
			"	}\n" +
			"}"
		);
		//@formatter:on
		CompletionList list = requestCompletions(unit, "e.throw");

		assertNotNull(list);

		List<CompletionItem> items = new ArrayList<>(list.getItems());
		CompletionItem item = items.get(0);
		assertEquals("throw", item.getLabel());
		assertEquals(item.getInsertText(), "throw e;");
		assertEquals(item.getInsertTextFormat(), InsertTextFormat.Snippet);
		Range range = item.getAdditionalTextEdits().get(0).getRange();
		assertEquals(new Range(new Position(4, 2), new Position(4, 9)), range);
	}

	@Test
	public void test_var() throws JavaModelException {
		//@formatter:off
		ICompilationUnit unit = getWorkingCopy(
			"src/org/sample/Test.java",
			"package org.sample;\n" +
			"public class Test {\n" +
			"	public void testMethod(String a) {\n" +
			"		a.var" +
			"	}\n" +
			"}"
		);
		//@formatter:on
		CompletionList list = requestCompletions(unit, "a.var");

		assertNotNull(list);

		List<CompletionItem> items = new ArrayList<>(list.getItems());
		CompletionItem item = items.get(0);
		assertEquals("var", item.getLabel());
		assertEquals(item.getInsertText(), "String ${1:a2} = a;${0}");
		assertEquals(item.getInsertTextFormat(), InsertTextFormat.Snippet);
		Range range = item.getAdditionalTextEdits().get(0).getRange();
		assertEquals(new Range(new Position(3, 2), new Position(3, 7)), range);
	}

	@Test
	public void test_var2() throws JavaModelException {
		//@formatter:off
		ICompilationUnit unit = getWorkingCopy(
			"src/org/sample/Test.java",
			"package org.sample;\n" +
			"import java.util.Collections;\n" +
			"public class Test {\n" +
			"	public void testMethod(String a) {\n" +
			"		Collections.emptyList().var" +
			"	}\n" +
			"}"
		);
		//@formatter:on
		CompletionList list = requestCompletions(unit, ".emptyList().var");

		assertNotNull(list);

		List<CompletionItem> items = new ArrayList<>(list.getItems());
		CompletionItem item = items.get(0);
		assertEquals("var", item.getLabel());
		assertEquals(item.getInsertText(), "List<Object> ${1:emptyList} = Collections.emptyList();${0}");
		assertEquals(item.getInsertTextFormat(), InsertTextFormat.Snippet);
		List<TextEdit> additionalTextEdits = item.getAdditionalTextEdits();
		Range range =additionalTextEdits.get(0).getRange();
		assertEquals(new Range(new Position(4, 2), new Position(4, 29)), range);
		assertTrue(additionalTextEdits.stream().anyMatch(e -> e.getNewText().contains("import java.util.List;")));
	}

	@Test
	public void test_while() throws JavaModelException {
		//@formatter:off
		ICompilationUnit unit = getWorkingCopy(
			"src/org/sample/Test.java",
			"package org.sample;\n" +
			"public class Test {\n" +
			"	public void testMethod(boolean a) {\n" +
			"		a.while" +
			"	}\n" +
			"}"
		);
		//@formatter:on
		CompletionList list = requestCompletions(unit, "a.while");

		assertNotNull(list);

		List<CompletionItem> items = new ArrayList<>(list.getItems());
		CompletionItem item = items.get(0);
		assertEquals("while", item.getLabel());
		assertEquals(item.getInsertText(), "while (a) {\n\t${0}\n}");
		assertEquals(item.getInsertTextFormat(), InsertTextFormat.Snippet);
		Range range = item.getAdditionalTextEdits().get(0).getRange();
		assertEquals(new Range(new Position(3, 2), new Position(3, 9)), range);
	}

	@Test
	public void test_canEvaluate() throws JavaModelException {
		//@formatter:off
		ICompilationUnit unit = getWorkingCopy(
			"src/org/sample/Test.java",
			"package org.sample;\n" +
			"public class Test {\n" +
			"	public void testMethod() {\n" +
			"		System." +
			"	}\n" +
			"}"
		);
		//@formatter:on
		CompletionList list = requestCompletions(unit, "System.");

		assertNotNull(list);

		List<CompletionItem> items = new ArrayList<>(list.getItems());
		assertFalse(items.stream().anyMatch(i -> i.getKind().equals(CompletionItemKind.Snippet)));
	}

	@Test
	public void test_canEvaluate2() throws JavaModelException {
		//@formatter:off
		ICompilationUnit unit = getWorkingCopy(
			"src/org/sample/Test.java",
			"package org.sample;\n" +
			"import java.util.ArrayList;\n" +
			"import java.util.List;\n" +
			"public class Test {\n" +
			"	public void testMethod() {\n" +
			"		List<String> lines = new ArrayList<>();\n" +
			"		lines.\n" +
			"		if (lines.isEmpty()){return;}\n" +
			"	}\n" +
			"}"
		);
		//@formatter:on
		CompletionList list = requestCompletions(unit, "		lines.");
		assertTrue(list.getItems().size() > 0);
	}

	@Test
	public void test_JavaDoc() throws JavaModelException {
		//@formatter:off
		ICompilationUnit unit = getWorkingCopy(
			"src/org/sample/Test.java",
			"package org.sample;\n" +
			"public enum Test {\n" +
			"	/**\n" +
			"	* {@link ArrayList}\n" +
			"	* Match case for the first letter.\n" +
			"	*/\n" +
			"	FIRSTLETTER;\n" +
			"}"
		);
		//@formatter:on
		CompletionList list = requestCompletions(unit, "letter.");
		assertTrue(list.getItems().isEmpty());
	}

	@Test
	public void testCompletion_GenericAnonymousClass() throws Exception {
		//@formatter:off
			ICompilationUnit unit = getWorkingCopy(
				"src/org/sample/Test.java",
				"package org.sample;\n" +
				"import java.util.ArrayList;\n" +
				"public class Test {\n" +
				"	public static void main(String[] args) {\n" +
				"		 ArrayList list = new ArrayList() {}. \n" +
				"	}\n" +
				"}\n"
			);
		//@formatter:on
		CompletionList list = requestCompletions(unit, "{}.");
		assertFalse(list.getItems().isEmpty());
	}

	private CompletionList requestCompletions(ICompilationUnit unit, String completeBehind) throws JavaModelException {
		return requestCompletions(unit, completeBehind, 0);
	}

	private CompletionList requestCompletions(ICompilationUnit unit, String completeBehind, int fromIndex) throws JavaModelException {
		int[] loc = findCompletionLocation(unit, completeBehind, fromIndex);
		return server.completion(JsonMessageHelper.getParams(createCompletionRequest(unit, loc[0], loc[1]))).join().getRight();
	}

	private String createCompletionRequest(ICompilationUnit unit, int line, int kar) {
		return COMPLETION_TEMPLATE.replace("${file}", JDTUtils.toURI(unit))
				.replace("${line}", String.valueOf(line))
				.replace("${char}", String.valueOf(kar));
	}

	private void mockLSP3Client() {
		mockLSPClient(true, true);
	}

	private void mockLSPClient(boolean isSnippetSupported, boolean isSignatureHelpSuported) {
		// Mock the preference manager to use LSP v3 support.
		when(preferenceManager.getClientPreferences().isCompletionSnippetsSupported()).thenReturn(isSnippetSupported);
	}
}
