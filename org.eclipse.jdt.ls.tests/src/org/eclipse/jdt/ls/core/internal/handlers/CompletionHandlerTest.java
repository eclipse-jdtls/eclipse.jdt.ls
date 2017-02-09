/*******************************************************************************
 * Copyright (c) 2016-2017 Red Hat Inc. and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Red Hat Inc. - initial API and implementation
 *******************************************************************************/
package org.eclipse.jdt.ls.core.internal.handlers;


import static org.apache.commons.lang3.StringUtils.isNotBlank;
import static org.eclipse.jdt.ls.core.internal.Lsp4jAssertions.assertTextEdit;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.ls.core.internal.JDTUtils;
import org.eclipse.jdt.ls.core.internal.JsonMessageHelper;
import org.eclipse.jdt.ls.core.internal.handlers.CompletionResolveHandler;
import org.eclipse.lsp4j.CompletionItem;
import org.eclipse.lsp4j.CompletionItemKind;
import org.eclipse.lsp4j.CompletionList;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.TextEdit;
import org.junit.ComparisonFailure;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.runners.MockitoJUnitRunner;

/**
 * @author Gorkem Ercan
 *
 */
@RunWith(MockitoJUnitRunner.class)
public class CompletionHandlerTest extends AbstractCompletionBasedTest {

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

	//FIXME Something very fishy here: when run from command line as part of the whole test suite,
	//no completions are returned maybe 80% of the time if this method runs first in this class,
	//i.e. if this method is named testCompletion_1. It seems to fail in the IDE too but *very*
	//infrequently.
	//When running the test class only, completions are always returned.
	@Test
	public void testCompletion_object() throws Exception{
		ICompilationUnit unit = getWorkingCopy(
				"src/java/Foo.java",
				"public class Foo {\n"+
						"	void foo() {\n"+
						"		Objec\n"+
						"	}\n"+
				"}\n");
		int[] loc = findCompletionLocation(unit, "Objec");
		CompletionList list = server.completion(JsonMessageHelper.getParams(createCompletionRequest(unit, loc[0], loc[1]))).join().getRight();
		assertNotNull(list);
		assertFalse("No proposals were found",list.getItems().isEmpty());

		List<CompletionItem> items = list.getItems();
		for ( CompletionItem item : items) {
			assertTrue(isNotBlank(item.getLabel()));
			assertNotNull(item.getKind() );
			assertTrue(isNotBlank(item.getSortText()));
			//text edits are not set during calls to "completion"
			assertNull(item.getTextEdit());
			assertNull(item.getInsertText());
			//Check contains data used for completionItem resolution
			@SuppressWarnings("unchecked")
			Map<String,String> data = (Map<String, String>) item.getData();
			assertNotNull(data);
			assertTrue(isNotBlank(data.get(CompletionResolveHandler.DATA_FIELD_URI)));
			assertTrue(isNotBlank(data.get(CompletionResolveHandler.DATA_FIELD_PROPOSAL_ID)));
			assertTrue(isNotBlank(data.get(CompletionResolveHandler.DATA_FIELD_REQUEST_ID)));
		}
	}


	@Test
	public void testCompletion_constructor() throws Exception{
		ICompilationUnit unit = getWorkingCopy(
				"src/java/Foo.java",
				"public class Foo {\n"+
						"	void foo() {\n"+
						"		Object o = new O\n"+
						"	}\n"+
				"}\n");
		int[] loc = findCompletionLocation(unit, "new O");
		CompletionList list = server.completion(JsonMessageHelper.getParams(createCompletionRequest(unit, loc[0], loc[1]))).join().getRight();
		assertNotNull(list);
		assertFalse("No proposals were found",list.getItems().isEmpty());

		List<CompletionItem> items = new ArrayList<>(list.getItems());
		Comparator<CompletionItem> comparator = (CompletionItem a, CompletionItem b) -> a.getSortText().compareTo(b.getSortText());
		Collections.sort(items, comparator);
		CompletionItem ctor = items.get(0);
		assertEquals("Object()", ctor.getLabel());



		CompletionItem resolvedItem = server.resolveCompletionItem(ctor).join();
		assertNotNull(resolvedItem);
		TextEdit te = resolvedItem.getTextEdit();
		assertNotNull(te);
		assertEquals("Object()",te.getNewText());
		assertNotNull(te.getRange());
		Range range = te.getRange();
		assertEquals(2, range.getStart().getLine());
		assertEquals(17, range.getStart().getCharacter());
		assertEquals(2, range.getEnd().getLine());
		assertEquals(18, range.getEnd().getCharacter());


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

		CompletionList list = server.completion(JsonMessageHelper.getParams(createCompletionRequest(unit, loc[0], loc[1]))).join().getRight();

		assertNotNull(list);
		assertEquals(1, list.getItems().size());
		CompletionItem item = list.getItems().get(0);
		// Check completion item
		assertNull(item.getInsertText());
		assertEquals("java.sql",item.getLabel());
		assertEquals(CompletionItemKind.Module, item.getKind() );
		assertTrue("Unexpected sortText:"+ item.getSortText() , item.getSortText().endsWith("cbf"));
		assertNull(item.getTextEdit());


		CompletionItem resolvedItem = server.resolveCompletionItem(item).join();
		assertNotNull(resolvedItem);
		TextEdit te = item.getTextEdit();
		assertNotNull(te);
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

		CompletionList list = server.completion(JsonMessageHelper.getParams(createCompletionRequest(unit, loc[0], loc[1]))).join().getRight();
		assertNotNull(list);
		CompletionItem ci = list.getItems().stream()
				.filter( item->  item.getLabel().matches("put\\(String \\w+, String \\w+\\) : String"))
				.findFirst().orElse(null);
		assertNotNull(ci);

		assertNull(ci.getInsertText());
		assertEquals(CompletionItemKind.Function, ci.getKind());
		assertTrue("Unexpected sortText:"+ ci.getSortText() , ci.getSortText().endsWith("abj"));
		assertNull(ci.getTextEdit());

		CompletionItem resolvedItem = server.resolveCompletionItem(ci).join();
		assertNotNull(resolvedItem.getTextEdit());
		try {
			assertTextEdit(5, 4, 6, "put({{key}}, {{value}})", resolvedItem.getTextEdit());
		} catch (ComparisonFailure e) {
			//In case the JDK has no sources
			assertTextEdit(5, 4, 6, "put({{arg0}}, {{arg1}})", resolvedItem.getTextEdit());
		}
		assertNotNull(resolvedItem.getAdditionalTextEdits());
		List<TextEdit> edits = resolvedItem.getAdditionalTextEdits();
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

		CompletionList list = server.completion(JsonMessageHelper.getParams(createCompletionRequest(unit, loc[0], loc[1]))).join().getRight();

		assertNotNull(list);
		assertEquals(1, list.getItems().size());
		CompletionItem item = list.getItems().get(0);
		assertEquals(CompletionItemKind.Class, item.getKind());
		assertNull(item.getInsertText());
		assertNull(item.getAdditionalTextEdits());
		assertNull(item.getTextEdit());

		CompletionItem resolvedItem = server.resolveCompletionItem(item).join();
		assertNotNull(resolvedItem.getTextEdit());
		assertTextEdit(3,3,15,"java.util.Map",resolvedItem.getTextEdit());
		//Not checking the range end character
	}


	private String createCompletionRequest(ICompilationUnit unit, int line, int kar) {
		return COMPLETION_TEMPLATE.replace("${file}", JDTUtils.getFileURI(unit))
				.replace("${line}", String.valueOf(line))
				.replace("${char}", String.valueOf(kar));
	}
}
