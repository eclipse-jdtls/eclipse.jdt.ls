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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.manipulation.CoreASTProvider;
import org.eclipse.jdt.ls.core.internal.JDTUtils;
import org.eclipse.jdt.ls.core.internal.JsonMessageHelper;
import org.eclipse.lsp4j.CompletionItem;
import org.eclipse.lsp4j.CompletionItemKind;
import org.eclipse.lsp4j.CompletionList;
import org.eclipse.lsp4j.InsertTextFormat;
import org.eclipse.lsp4j.InsertTextMode;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.TextEdit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
public class PostfixCompletionTest extends AbstractCompilationUnitBasedTest {

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

	@BeforeEach
	public void setUp() {
		mockLSP3Client();
		CoreASTProvider sharedASTProvider = CoreASTProvider.getInstance();
		sharedASTProvider.disposeAST();
		preferences.setPostfixCompletionEnabled(true);
	}

	@AfterEach
	public void tearDown() throws Exception {
		preferences.setPostfixCompletionEnabled(false);
	}

	@Test
	public void testCastLazyResolve() throws JavaModelException {
		try {
			preferences.setCompletionLazyResolveTextEditEnabled(true);
			when(preferenceManager.getClientPreferences().isResolveAdditionalTextEditsSupport()).thenReturn(true);
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
			assertEquals(item.getInsertTextFormat(), InsertTextFormat.Snippet);
			Range range = new Range(new Position(3, 2), new Position(3, 8));
			assertPostfixTextEdit(item, "((${1})${inner_expression})${0}", range);

			CompletionItem resolved = server.resolveCompletionItem(item).join();
			assertPostfixTextEdit(resolved, "((${1})a)${0}", range);
		} finally {
			preferences.setCompletionLazyResolveTextEditEnabled(false);
		}
	}

	@Test
	public void test_assert() throws JavaModelException {
		//@formatter:off
		ICompilationUnit unit = getWorkingCopy(
			"src/org/sample/Test.java",
			"package org.sample;\n" +
			"public class Test {\n" +
			"	public void testMethod(Boolean identifier) {\n" +
			"		identifier.assert" +
			"	}\n" +
			"}"
		);
		//@formatter:on
		CompletionList list = requestCompletions(unit, "identifier.assert");

		assertNotNull(list);

		List<CompletionItem> items = new ArrayList<>(list.getItems());
		CompletionItem item = items.get(0);
		assertEquals("assert", item.getLabel());
		assertEquals(item.getInsertTextFormat(), InsertTextFormat.Snippet);
		assertPostfixTextEdit(item, "assert identifier;", new Range(new Position(3, 2), new Position(3, 19)));
	}

	@Test
	public void test_cast() throws JavaModelException {
		when(preferenceManager.getClientPreferences().isCompletionItemLabelDetailsSupport()).thenReturn(true);
		when(preferenceManager.getClientPreferences().getCompletionItemInsertTextModeDefault()).thenReturn(InsertTextMode.AsIs);
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
		assertNull(item.getLabelDetails().getDetail());
		assertEquals("Casts the expression to a new type", item.getLabelDetails().getDescription());
		assertEquals(item.getInsertTextFormat(), InsertTextFormat.Snippet);
		assertEquals(item.getInsertTextMode(), InsertTextMode.AdjustIndentation);
		assertPostfixTextEdit(item, "((${1})a)${0}", new Range(new Position(3, 2), new Position(3, 8)));
	}

	@Test
	public void test_if() throws JavaModelException {
		when(preferenceManager.getClientPreferences().getCompletionItemInsertTextModeDefault()).thenReturn(InsertTextMode.AdjustIndentation);
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
		assertEquals(item.getInsertTextFormat(), InsertTextFormat.Snippet);
		assertNull(item.getInsertTextMode());
		assertPostfixTextEdit(item, "if (a) {\n\t${0}\n}", new Range(new Position(3, 2), new Position(3, 6)));
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
		assertEquals(item.getInsertTextFormat(), InsertTextFormat.Snippet);
		assertPostfixTextEdit(item, "if (!a) {\n\t${0}\n}", new Range(new Position(3, 2), new Position(3, 8)));
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
		assertEquals(item.getInsertTextFormat(), InsertTextFormat.Snippet);
		assertPostfixTextEdit(item, "for (String ${1:a2} : a) {\n\t${0}\n}", new Range(new Position(3, 2), new Position(3, 7)));
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
		assertEquals(item.getInsertTextFormat(), InsertTextFormat.Snippet);
		assertPostfixTextEdit(item, "for (int ${1:a2} = 0; ${1:a2} < a.length; ${1:a2}++) {\n\t${0}\n}", new Range(new Position(3, 2), new Position(3, 8)));
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
		assertEquals(item.getInsertTextFormat(), InsertTextFormat.Snippet);
		assertPostfixTextEdit(item, "for (int ${1:a2} = a.length - 1; ${1:a2} >= 0; ${1:a2}--) {\n\t${0}\n}", new Range(new Position(3, 2), new Position(3, 8)));
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
		assertEquals(item.getInsertTextFormat(), InsertTextFormat.Snippet);
		assertPostfixTextEdit(item, "if (a != null) {\n\t${0}\n}", new Range(new Position(3, 2), new Position(3, 9)));
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
		assertEquals(item.getInsertTextFormat(), InsertTextFormat.Snippet);
		assertPostfixTextEdit(item, "if (a == null) {\n\t${0}\n}", new Range(new Position(3, 2), new Position(3, 8)));
	}

	@Test
	public void test_opt() throws JavaModelException {
		//@formatter:off
		ICompilationUnit unit = getWorkingCopy(
			"src/org/sample/Test.java",
			"package org.sample;\n" +
			"public class Test {\n" +
			"	public void testMethod(Object identifier) {\n" +
			"		identifier.opt" +
			"	}\n" +
			"}"
		);
		//@formatter:on
		CompletionList list = requestCompletions(unit, "identifier.opt");

		assertNotNull(list);

		List<CompletionItem> items = new ArrayList<>(list.getItems());
		CompletionItem item = items.get(0);
		assertEquals("opt", item.getLabel());
		assertEquals(item.getInsertTextFormat(), InsertTextFormat.Snippet);
		assertPostfixTextEdit(item, "Optional.ofNullable(identifier)", new Range(new Position(3, 2), new Position(3, 16)));
	}

	@Test
	public void test_not() throws JavaModelException {
		//@formatter:off
		ICompilationUnit unit = getWorkingCopy(
			"src/org/sample/Test.java",
			"package org.sample;\n" +
			"public class Test {\n" +
			"	public void testMethod(boolean a) {\n" +
			"		a.not" +
			"	}\n" +
			"}"
		);
		//@formatter:on
		CompletionList list = requestCompletions(unit, "a.not");

		assertNotNull(list);

		List<CompletionItem> items = new ArrayList<>(list.getItems());
		CompletionItem item = items.get(0);
		assertEquals("not", item.getLabel());
		assertEquals(item.getInsertTextFormat(), InsertTextFormat.Snippet);
		assertPostfixTextEdit(item, "!a", new Range(new Position(3, 2), new Position(3, 7)));
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
		assertEquals(item.getInsertTextFormat(), InsertTextFormat.Snippet);
		assertPostfixTextEdit(item, "System.out.println(a);${0}", new Range(new Position(3, 2), new Position(3, 10)));
	}

	@Test
	public void test_sysout_itemDefaults_enabled() throws Exception {
		//@formatter:off
		ICompilationUnit unit = getWorkingCopy(
				"src/org/sample/Test.java",
				"package org.sample;\n" +
				"public class Test {\n" +
				"	void sysop(){}\n" +
				"	public void testMethod(Object args) {\n" +
				"		new Test().syso\n" +
				"	}\n" +
				"}\n");
		CompletionList list = requestCompletions(unit, "new Test().syso");
		//@formatter:on
		assertNotNull(list);
		assertNotNull(list.getItemDefaults().getEditRange());
		assertEquals(InsertTextFormat.Snippet, list.getItemDefaults().getInsertTextFormat());
		assertEquals(InsertTextMode.AdjustIndentation, list.getItemDefaults().getInsertTextMode());

		CompletionItem ci = list.getItems().stream().filter(item -> item.getLabel().startsWith("sysout")).findFirst().orElse(null);
		assertNotNull(ci);

		assertPostfixTextEdit(ci, "System.out.println(new Test());${0}", new Range(new Position(4, 2), new Position(4, 17)));
		// The full postfix range differs from the shared item default, so this item must override it.
		assertNull(ci.getTextEditText());
		assertNull(ci.getInsertTextFormat());
		assertNull(ci.getInsertTextMode());
		assertEquals(CompletionItemKind.Snippet, ci.getKind());
	}

	@Test
	public void test_sysout_object() throws JavaModelException {
		//@formatter:off
		ICompilationUnit unit = getWorkingCopy(
			"src/org/sample/Test.java",
			"package org.sample;\n" +
			"public class Test {\n" +
			"	public void testMethod(String a) {\n" +
			"		Boolean foo = true;\n" +
			"		foo.sysout" +
			"	}\n" +
			"}"
		);
		//@formatter:on
		CompletionList list = requestCompletions(unit, "foo.sysout");

		assertNotNull(list);

		List<CompletionItem> items = new ArrayList<>(list.getItems());
		CompletionItem item = items.get(0);
		assertEquals("sysout", item.getLabel());
		assertEquals(item.getInsertTextFormat(), InsertTextFormat.Snippet);
		assertPostfixTextEdit(item, "System.out.println(foo);${0}", new Range(new Position(4, 2), new Position(4, 12)));
	}

	@Test
	public void test_sysoutv_object() throws JavaModelException {
		//@formatter:off
		ICompilationUnit unit = getWorkingCopy(
			"src/org/sample/Test.java",
			"package org.sample;\n" +
			"public class Test {\n" +
			"	public void testMethod(String a) {\n" +
			"		Boolean foo = true;\n" +
			"		foo.sysoutv" +
			"	}\n" +
			"}"
		);
		//@formatter:on
		CompletionList list = requestCompletions(unit, "foo.sysoutv");

		assertNotNull(list);

		List<CompletionItem> items = new ArrayList<>(list.getItems());
		CompletionItem item = items.get(0);
		assertEquals("sysoutv", item.getLabel());
		assertEquals(item.getInsertTextFormat(), InsertTextFormat.Snippet);
		assertPostfixTextEdit(item, "System.out.println(\"foo = \" + foo);${0}", new Range(new Position(4, 2), new Position(4, 13)));
	}

	@Test
	public void test_sysouf_object() throws JavaModelException {
		//@formatter:off
		ICompilationUnit unit = getWorkingCopy(
			"src/org/sample/Test.java",
			"package org.sample;\n" +
			"public class Test {\n" +
			"	public void testMethod(String a) {\n" +
			"		Boolean foo = true;\n" +
			"		foo.sysouf" +
			"	}\n" +
			"}"
		);
		//@formatter:on
		CompletionList list = requestCompletions(unit, "foo.sysouf");

		assertNotNull(list);

		List<CompletionItem> items = new ArrayList<>(list.getItems());
		CompletionItem item = items.get(0);
		assertEquals("sysouf", item.getLabel());
		assertEquals(item.getInsertTextFormat(), InsertTextFormat.Snippet);
		assertPostfixTextEdit(item, "System.out.printf(\"\", foo);${0}", new Range(new Position(4, 2), new Position(4, 12)));
	}

	@Test
	public void test_syserr_object() throws JavaModelException {
		//@formatter:off
		ICompilationUnit unit = getWorkingCopy(
			"src/org/sample/Test.java",
			"package org.sample;\n" +
			"public class Test {\n" +
			"	public void testMethod(String a) {\n" +
			"		Boolean foo = true;\n" +
			"		foo.syserr" +
			"	}\n" +
			"}"
		);
		//@formatter:on
		CompletionList list = requestCompletions(unit, "foo.syserr");

		assertNotNull(list);

		List<CompletionItem> items = new ArrayList<>(list.getItems());
		CompletionItem item = items.get(0);
		assertEquals("syserr", item.getLabel());
		assertEquals(item.getInsertTextFormat(), InsertTextFormat.Snippet);
		assertPostfixTextEdit(item, "System.err.println(foo);${0}", new Range(new Position(4, 2), new Position(4, 12)));
	}

	@Test
	public void test_format() throws JavaModelException {
		//@formatter:off
		ICompilationUnit unit = getWorkingCopy(
			"src/org/sample/Test.java",
			"package org.sample;\n" +
			"public class Test {\n" +
			"	public void testMethod(String a) {\n" +
			"		a.format" +
			"	}\n" +
			"}"
		);
		//@formatter:on
		CompletionList list = requestCompletions(unit, "a.format");

		assertNotNull(list);

		CompletionItem item = list.getItems().stream().filter(i -> i.getKind() == CompletionItemKind.Snippet).findFirst().orElse(null);
		assertEquals("format", item.getLabel());
		assertPostfixTextEdit(item, "String.format(a, ${0});", new Range(new Position(3, 2), new Position(3, 10)));
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
		assertEquals(item.getInsertTextFormat(), InsertTextFormat.Snippet);
		assertPostfixTextEdit(item, "throw e;", new Range(new Position(4, 2), new Position(4, 9)));
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
		assertEquals(item.getInsertTextFormat(), InsertTextFormat.Snippet);
		assertPostfixTextEdit(item, "String ${1:a2} = a;${0}", new Range(new Position(3, 2), new Position(3, 7)));
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
		assertEquals(item.getInsertTextFormat(), InsertTextFormat.Snippet);
		assertPrimaryTextEdit(item, "List<Object> ${1:emptyList} = Collections.emptyList();${0}",
				new Range(new Position(4, 2), new Position(4, 29)));
		List<TextEdit> additionalTextEdits = item.getAdditionalTextEdits();
		assertNotNull(additionalTextEdits);
		assertTrue(additionalTextEdits.stream().anyMatch(e -> e.getNewText().contains("import java.util.List;")));
		assertAdditionalTextEditsDoNotOverlapPrimary(item);
	}

	@Test
	public void test_par() throws JavaModelException {
		//@formatter:off
		ICompilationUnit unit = getWorkingCopy(
			"src/org/sample/Test.java",
			"package org.sample;\n" +
			"public class Test {\n" +
			"	public void testMethod(String a) {\n" +
			"		a.par" +
			"	}\n" +
			"}"
		);
		//@formatter:on
		CompletionList list = requestCompletions(unit, "a.par");

		assertNotNull(list);

		List<CompletionItem> items = new ArrayList<>(list.getItems());
		CompletionItem item = items.get(0);
		assertEquals("par", item.getLabel());
		assertEquals(item.getInsertTextFormat(), InsertTextFormat.Snippet);
		assertPostfixTextEdit(item, "(a)", new Range(new Position(3, 2), new Position(3, 7)));
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
		assertEquals(item.getInsertTextFormat(), InsertTextFormat.Snippet);
		assertPostfixTextEdit(item, "while (a) {\n\t${0}\n}", new Range(new Position(3, 2), new Position(3, 9)));
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

	@Test
	public void testPostfixCompletion_NotInImportDeclaration() throws JavaModelException {
		ICompilationUnit unit = getWorkingCopy("src/org/sample/Test.java", """
				package org.sample;

				import static java.util.ArrayList.;
				public class Test {}
				""");
		CompletionList list = requestCompletions(unit, "import static java.util.ArrayList.");

		assertEquals(0, list.getItems().size(), "Postfix completion should not be triggered in import declarations:" + list.getItems());
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

	private void assertPostfixTextEdit(CompletionItem item, String newText, Range range) {
		assertPrimaryTextEdit(item, newText, range);
		assertAdditionalTextEditsDoNotOverlapPrimary(item);
	}

	private void assertPrimaryTextEdit(CompletionItem item, String newText, Range range) {
		assertNotNull(item.getTextEdit());
		assertTrue(item.getTextEdit().isLeft());
		assertEquals(new TextEdit(range, newText), item.getTextEdit().getLeft());
	}

	private void assertAdditionalTextEditsDoNotOverlapPrimary(CompletionItem item) {
		if (item.getAdditionalTextEdits() == null) {
			return;
		}
		Range primaryRange = item.getTextEdit().getLeft().getRange();
		for (TextEdit additionalEdit : item.getAdditionalTextEdits()) {
			assertFalse(rangesOverlap(primaryRange, additionalEdit.getRange()));
		}
	}

	private boolean rangesOverlap(Range left, Range right) {
		return compare(left.getStart(), right.getEnd()) < 0 && compare(right.getStart(), left.getEnd()) < 0;
	}

	private int compare(Position left, Position right) {
		int lineComparison = Integer.compare(left.getLine(), right.getLine());
		return lineComparison != 0 ? lineComparison : Integer.compare(left.getCharacter(), right.getCharacter());
	}

	private void mockLSP3Client() {
		mockLSPClient(true, true, true);
	}

	private void mockLSPClient(boolean isSnippetSupported, boolean isSignatureHelpSuported, boolean isCompletionListItemDefaultsSupport) {
		// Mock the preference manager to use LSP v3 support.
		when(preferenceManager.getClientPreferences().isCompletionSnippetsSupported()).thenReturn(isSnippetSupported);
		when(preferenceManager.getClientPreferences().isCompletionListItemDefaultsSupport()).thenReturn(isCompletionListItemDefaultsSupport);
		when(preferenceManager.getClientPreferences().isCompletionListItemDefaultsPropertySupport("editRange")).thenReturn(isCompletionListItemDefaultsSupport);
		when(preferenceManager.getClientPreferences().isCompletionListItemDefaultsPropertySupport("insertTextFormat")).thenReturn(isCompletionListItemDefaultsSupport);
		when(preferenceManager.getClientPreferences().isCompletionItemInsertTextModeSupport(InsertTextMode.AdjustIndentation)).thenReturn(true);
		when(preferenceManager.getClientPreferences().isCompletionListItemDefaultsPropertySupport("insertTextMode")).thenReturn(isCompletionListItemDefaultsSupport);
		when(preferenceManager.getClientPreferences().isCompletionItemLabelDetailsSupport()).thenReturn(false);
	}
}
