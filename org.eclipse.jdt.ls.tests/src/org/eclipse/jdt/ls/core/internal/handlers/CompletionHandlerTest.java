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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.ls.core.internal.JDTUtils;
import org.eclipse.jdt.ls.core.internal.JsonMessageHelper;
import org.eclipse.jdt.ls.core.internal.WorkspaceHelper;
import org.eclipse.jdt.ls.core.internal.preferences.ClientPreferences;
import org.eclipse.lsp4j.CompletionItem;
import org.eclipse.lsp4j.CompletionItemKind;
import org.eclipse.lsp4j.CompletionList;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.TextEdit;
import org.junit.Before;
import org.junit.ComparisonFailure;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;
import org.mockito.runners.MockitoJUnitRunner;

/**
 * @author Gorkem Ercan
 *
 */
@RunWith(MockitoJUnitRunner.class)
public class CompletionHandlerTest extends AbstractCompilationUnitBasedTest {

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

	@Before
	public void setUp() {
		mockLSP3Client();
	}

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
			assertTrue(isNotBlank(item.getInsertText()));
			assertNotNull(item.getFilterText());
			assertFalse(item.getFilterText().contains(" "));
			assertTrue(item.getLabel().startsWith(item.getFilterText()));
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
		assertEquals("Object", ctor.getInsertText());

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
	public void testCompletion_import_package() throws JavaModelException{
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
		assertEquals("999999215", item.getSortText());
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
	public void testCompletion_method_withLSPV2() throws JavaModelException{
		mockLSP2Client();

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

		assertEquals("put", ci.getInsertText());
		assertEquals(CompletionItemKind.Function, ci.getKind());
		assertEquals("999999019", ci.getSortText());
		assertNull(ci.getTextEdit());

		CompletionItem resolvedItem = server.resolveCompletionItem(ci).join();
		assertNotNull(resolvedItem.getTextEdit());
		assertTextEdit(5, 4, 6, "put", resolvedItem.getTextEdit());
		assertNotNull(resolvedItem.getAdditionalTextEdits());
		List<TextEdit> edits = resolvedItem.getAdditionalTextEdits();
		assertEquals(3, edits.size());
	}

	@Test
	public void testCompletion_method_withLSPV3() throws JavaModelException{
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

		assertEquals("put", ci.getInsertText());
		assertEquals(CompletionItemKind.Function, ci.getKind());
		assertEquals("999999019", ci.getSortText());
		assertNull(ci.getTextEdit());

		CompletionItem resolvedItem = server.resolveCompletionItem(ci).join();
		assertNotNull(resolvedItem.getTextEdit());
		try {
			assertTextEdit(5, 4, 6, "put(${1:key}, ${2:value})", resolvedItem.getTextEdit());
		} catch (ComparisonFailure e) {
			//In case the JDK has no sources
			assertTextEdit(5, 4, 6, "put(${1:arg0}, ${2:arg1})", resolvedItem.getTextEdit());
		}
		assertNotNull(resolvedItem.getAdditionalTextEdits());
		List<TextEdit> edits = resolvedItem.getAdditionalTextEdits();
		assertEquals(3, edits.size());
	}

	private ClientPreferences mockClientPreferences(boolean supportCompletionSnippets, boolean supportSignatureHelp) {
		ClientPreferences mockCapabilies = Mockito.mock(ClientPreferences.class);
		Mockito.when(preferenceManager.getClientPreferences()).thenReturn(mockCapabilies);
		Mockito.when(mockCapabilies.isCompletionSnippetsSupported()).thenReturn(supportCompletionSnippets);
		Mockito.when(mockCapabilies.isSignatureHelpSupported()).thenReturn(supportSignatureHelp);
		return mockCapabilies;
	}

	@Test
	public void testCompletion_field() throws JavaModelException{
		ICompilationUnit unit = getWorkingCopy(
				"src/java/Foo.java",
				"import java.sq \n" +
						"public class Foo {\n"+
						"private String myTestString;\n"+
						"	void foo() {\n"+
						"   this.myTestS\n"+
						"	}\n"+
				"}\n");

		int[] loc = findCompletionLocation(unit, "this.myTestS");

		CompletionList list = server.completion(JsonMessageHelper.getParams(createCompletionRequest(unit, loc[0], loc[1]))).join().getRight();

		assertNotNull(list);
		assertEquals(1, list.getItems().size());
		CompletionItem item = list.getItems().get(0);
		assertEquals(CompletionItemKind.Field, item.getKind());
		assertEquals("myTestString", item.getInsertText());
		assertNull(item.getAdditionalTextEdits());
		assertNull(item.getTextEdit());

		CompletionItem resolvedItem = server.resolveCompletionItem(item).join();
		assertNotNull(resolvedItem.getTextEdit());
		assertTextEdit(4,8,15,"myTestString",resolvedItem.getTextEdit());
		//Not checking the range end character
	}

	@Test
	public void testCompletion_import_type() throws JavaModelException{
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
		assertEquals("Map", item.getInsertText());
		assertNull(item.getAdditionalTextEdits());
		assertNull(item.getTextEdit());

		CompletionItem resolvedItem = server.resolveCompletionItem(item).join();
		assertNotNull(resolvedItem.getTextEdit());
		assertTextEdit(3,3,15,"java.util.Map",resolvedItem.getTextEdit());
		//Not checking the range end character
	}

	@Test
	public void testCompletion_noPackage() throws Exception{
		ICompilationUnit unit = getWorkingCopy(
				"src/NoPackage.java",
				"public class NoPackage {\n"
						+ "    NoP"
						+"}\n");
		int[] loc = findCompletionLocation(unit, "    NoP");
		CompletionList list = server.completion(JsonMessageHelper.getParams(createCompletionRequest(unit, loc[0], loc[1]))).join().getRight();
		assertNotNull(list);
		assertFalse("No proposals were found", list.getItems().isEmpty());
		assertEquals("NoPackage", list.getItems().get(0).getLabel());
	}

	@Test
	public void testCompletion_package() throws JavaModelException{
		ClientPreferences mockCapabilies = Mockito.mock(ClientPreferences.class);
		Mockito.when(preferenceManager.getClientPreferences()).thenReturn(mockCapabilies);

		ICompilationUnit unit = getWorkingCopy(
				"src/org/sample/Baz.java",
				"package o"+
						"public class Baz {\n"+
				"}\n");

		int[] loc = findCompletionLocation(unit, "package o");

		CompletionList list = server.completion(JsonMessageHelper.getParams(createCompletionRequest(unit, loc[0], loc[1]))).join().getRight();

		assertNotNull(list);
		List<CompletionItem> items = new ArrayList<>(list.getItems());
		assertTrue(items.size() > 1);
		items.sort((i1, i2) -> i1.getSortText().compareTo(i2.getSortText()));

		CompletionItem item = items.get(0);
		// current package should appear 1st
		assertEquals("org.sample",item.getLabel());

		CompletionItem resolvedItem = server.resolveCompletionItem(item).join();
		assertNotNull(resolvedItem);
		TextEdit te = item.getTextEdit();
		assertNotNull(te);
		assertEquals("org.sample", te.getNewText());
		assertNotNull(te.getRange());
		Range range = te.getRange();
		assertEquals(0, range.getStart().getLine());
		assertEquals(8, range.getStart().getCharacter());
		assertEquals(0, range.getEnd().getLine());
		assertEquals(15, range.getEnd().getCharacter());
	}

	@Test
	public void testCompletion_methodOverride() throws Exception{
		testCompletion_classMethodOverride("hello", true, true);
	}

	@Test
	public void testCompletion_interfaceMethodOverride() throws Exception {
		testCompletion_interfaceMethodOverride("hello", true, true);
	}

	@Test
	public void testCompletion_classMethodOverrideNoSnippet() throws Exception {
		testCompletion_classMethodOverride("hello", false, true);
	}

	@Test
	public void testCompletion_interfaceMethodOverrideNoSnippet() throws Exception {
		testCompletion_interfaceMethodOverride("hello", false, true);
	}

	@Test
	public void testCompletion_classMethodOverrideJava4() throws Exception {
		testCompletion_classMethodOverride("java4", true, false);
	}

	@Test
	public void testCompletion_interfaceMethodOverrideJava4() throws Exception {
		testCompletion_interfaceMethodOverride("java4", true, false);
	}

	@Test
	public void testCompletion_classMethodOverrideJava5() throws Exception {
		testCompletion_classMethodOverride("java5", true, true);
	}

	@Test
	public void testCompletion_interfaceMethodOverrideJava5() throws Exception {
		testCompletion_interfaceMethodOverride("java5", true, false);
	}

	private void testCompletion_classMethodOverride(String projectName, boolean supportSnippets,
			boolean overridesSuperClass) throws Exception {
		if (project == null || !projectName.equals(project.getName())) {
			importProjects("eclipse/"+projectName);
			project = WorkspaceHelper.getProject(projectName);
		}
		mockClientPreferences(supportSnippets, true);

		ICompilationUnit unit = getWorkingCopy(
				"src/java/Foo.java",
				"public class Foo {\n"
						+ "    toStr"
						+"}\n");
		int[] loc = findCompletionLocation(unit, " toStr");
		CompletionList list = server.completion(JsonMessageHelper.getParams(createCompletionRequest(unit, loc[0], loc[1]))).join().getRight();
		assertNotNull(list);
		List<CompletionItem> filtered = list.getItems().stream().filter((item)->{
			return item.getDetail() != null && item.getDetail().startsWith("Override method in");
		}).collect(Collectors.toList());
		assertFalse("No override proposals", filtered.isEmpty());
		CompletionItem oride = filtered.get(0);
		assertEquals("toString", oride.getInsertText());
		assertNull(oride.getTextEdit());
		oride = server.resolveCompletionItem(oride).join();
		assertNotNull(oride.getTextEdit());
		String text = oride.getTextEdit().getNewText();
		StringBuilder expectedText = new StringBuilder();
		if (overridesSuperClass) {
			expectedText.append("@Override\n");
		}
		expectedText.append("public String toString() {\n\t");
		if (supportSnippets) {
			expectedText.append("${0:");
		}
		expectedText.append("return super.toString();");
		if (supportSnippets) {
			expectedText.append("}");
		}
		expectedText.append("\n}");

		assertEquals(expectedText.toString(), text);
	}

	private void testCompletion_interfaceMethodOverride(String projectName, boolean supportSnippets,
			boolean overridesInterface) throws Exception {
		if (project == null || !projectName.equals(project.getName())) {
			importProjects("eclipse/" + projectName);
			project = WorkspaceHelper.getProject(projectName);
		}
		mockClientPreferences(supportSnippets, true);

		ICompilationUnit unit = getWorkingCopy(
				"src/java/Foo.java",
				"public class Foo implements Runnable{\n"
						+ "    ru"
						+"}\n");
		int[] loc = findCompletionLocation(unit, " ru");
		CompletionList list = server
				.completion(JsonMessageHelper.getParams(createCompletionRequest(unit, loc[0], loc[1]))).join()
				.getRight();
		assertNotNull(list);
		List<CompletionItem> filtered = list.getItems().stream().filter((item) -> {
			return item.getDetail() != null && item.getDetail().startsWith("Override method in");
		}).collect(Collectors.toList());
		assertFalse("No override proposals", filtered.isEmpty());
		CompletionItem oride = filtered.get(0);
		assertEquals("run", oride.getInsertText());
		assertNull(oride.getTextEdit());
		oride = server.resolveCompletionItem(oride).join();
		assertNotNull(oride.getTextEdit());
		String text = oride.getTextEdit().getNewText();
		StringBuilder expectedText = new StringBuilder();
		if (overridesInterface) {
			expectedText.append("@Override\n");
		}
		expectedText.append("public void run() {\n\t");
		if (supportSnippets) {
			expectedText.append("${0}");
		}
		expectedText.append("\n}");

		assertEquals(expectedText.toString(), text);

	}

	@Test
	public void testCompletion_methodOverrideWithParams() throws Exception {
		ICompilationUnit unit = getWorkingCopy(
				//@formatter:off
				"src/org/sample/Test.java",
				"package org.sample;\n\n"+
				"public class Test extends Baz {\n"+
				"    getP" +
				"}\n");
				//@formatter:on
		int[] loc = findCompletionLocation(unit, " getP");
		CompletionList list = server.completion(JsonMessageHelper.getParams(createCompletionRequest(unit, loc[0], loc[1]))).join().getRight();
		assertNotNull(list);
		List<CompletionItem> filtered = list.getItems().stream().filter((item)->{
			return item.getDetail() != null && item.getDetail().startsWith("Override method in");
		}).collect(Collectors.toList());
		assertEquals("No override proposals", filtered.size(), 1);
		CompletionItem oride = filtered.get(0);
		assertEquals("getParent", oride.getInsertText());
		assertNull(oride.getTextEdit());
		oride = server.resolveCompletionItem(oride).join();
		assertNotNull(oride.getTextEdit());
		String text = oride.getTextEdit().getNewText();

		String expectedText = "@Override\n"+
				"protected File getParent(File file, int depth) {\n" +
				"\t${0:return super.getParent(file, depth);}\n"+
				"}";

		assertEquals(expectedText, text);
		assertEquals("Missing required imports", 4, oride.getAdditionalTextEdits().size());

		assertTextEdit(0, 19, 19, "\n\n", oride.getAdditionalTextEdits().get(0));
		assertTextEdit(0, 19, 19, "import java.io.File;", oride.getAdditionalTextEdits().get(1));
		assertTextEdit(0, 19, 19, "\n\n", oride.getAdditionalTextEdits().get(2));
	}

	@Test
	public void testCompletion_methodOverrideWithException() throws Exception {
		ICompilationUnit unit = getWorkingCopy(
				//@formatter:off
				"src/org/sample/Test.java",
				"package org.sample;\n\n"+
				"public class Test extends Baz {\n"+
				"    dele"+
				"}\n");
				//@formatter:on
		int[] loc = findCompletionLocation(unit, " dele");
		CompletionList list = server.completion(JsonMessageHelper.getParams(createCompletionRequest(unit, loc[0], loc[1]))).join().getRight();
		assertNotNull(list);
		List<CompletionItem> filtered = list.getItems().stream().filter((item)->{
			return item.getDetail() != null && item.getDetail().startsWith("Override method in");
		}).collect(Collectors.toList());
		assertEquals("No override proposals", filtered.size(), 1);
		CompletionItem oride = filtered.get(0);
		assertEquals("deleteSomething", oride.getInsertText());
		assertNull(oride.getTextEdit());
		oride = server.resolveCompletionItem(oride).join();
		assertNotNull(oride.getTextEdit());
		String text = oride.getTextEdit().getNewText();

		String expectedText = "@Override\n"+
				"protected void deleteSomething() throws IOException {\n" +
				"\t${0:super.deleteSomething();}\n"+
				"}";

		assertEquals(expectedText, text);
		assertEquals("Missing required imports", 4, oride.getAdditionalTextEdits().size());

		assertTextEdit(0, 19, 19, "\n\n", oride.getAdditionalTextEdits().get(0));
		assertTextEdit(0, 19, 19, "import java.io.IOException;", oride.getAdditionalTextEdits().get(1));
		assertTextEdit(0, 19, 19, "\n\n", oride.getAdditionalTextEdits().get(2));
	}

	public void testCompletion_plainTextDoc() throws Exception{
		ICompilationUnit unit = getWorkingCopy(
				//@formatter:off
				"src/java/Foo.java",
				"import java.sq \n" +
				"public class Foo {\n"+
				"	void foo() {\n"+
				"      zz\n"+
				"	}\n\"	}\\n\"+"+
				"\n"+
				"/** This should be <bold>plain</bold>.*/\n" +
				"	void zzz() {}\n"+
				"}\n");
				//@formatter:off
		int[] loc = findCompletionLocation(unit, "   zz");
		CompletionList list = server.completion(JsonMessageHelper.getParams(createCompletionRequest(unit, loc[0], loc[1]))).join().getRight();
		assertNotNull(list);
		assertFalse("No proposals were found", list.getItems().isEmpty());
		CompletionItem item = list.getItems().get(0);
		assertEquals("zzz() : void", item.getLabel());

		CompletionItem resolvedItem = server.resolveCompletionItem(item).join();
		assertEquals("This should be plain.", resolvedItem.getDocumentation());
	}

	private String createCompletionRequest(ICompilationUnit unit, int line, int kar) {
		return COMPLETION_TEMPLATE.replace("${file}", JDTUtils.getFileURI(unit))
				.replace("${line}", String.valueOf(line))
				.replace("${char}", String.valueOf(kar));
	}

	private void mockLSP3Client() {
		mockLSPClient(true, true);
	}

	private void mockLSP2Client() {
		mockLSPClient(false, false);
	}

	private void mockLSPClient(boolean isSnippetSupported, boolean isSignatureHelpSuported) {
		reset(preferenceManager);
		ClientPreferences mockCapabilies = mock(ClientPreferences.class);
		// Mock the preference manager to use LSP v3 support.
		when(preferenceManager.getClientPreferences()).thenReturn(mockCapabilies);
		when(mockCapabilies.isCompletionSnippetsSupported()).thenReturn(isSnippetSupported);
		when(mockCapabilies.isSignatureHelpSupported()).thenReturn(isSignatureHelpSuported);
		when(preferenceManager.getClientPreferences()).thenReturn(mockCapabilies);
	}
}
