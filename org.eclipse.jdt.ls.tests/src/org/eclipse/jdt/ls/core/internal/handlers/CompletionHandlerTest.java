/*******************************************************************************
 * Copyright (c) 2016-2018 Red Hat Inc. and others.
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
import static org.eclipse.jdt.ls.core.internal.Lsp4jAssertions.assertPosition;
import static org.eclipse.jdt.ls.core.internal.Lsp4jAssertions.assertTextEdit;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.core.runtime.Path;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.manipulation.CoreASTProvider;
import org.eclipse.jdt.ls.core.internal.JDTUtils;
import org.eclipse.jdt.ls.core.internal.JavaClientConnection;
import org.eclipse.jdt.ls.core.internal.JavaLanguageServerPlugin;
import org.eclipse.jdt.ls.core.internal.JsonMessageHelper;
import org.eclipse.jdt.ls.core.internal.TextEditUtil;
import org.eclipse.jdt.ls.core.internal.WorkspaceHelper;
import org.eclipse.jdt.ls.core.internal.contentassist.JavadocCompletionProposal;
import org.eclipse.jdt.ls.core.internal.preferences.ClientPreferences;
import org.eclipse.jdt.ls.core.internal.preferences.PreferenceManager;
import org.eclipse.lsp4j.CompletionItem;
import org.eclipse.lsp4j.CompletionItemKind;
import org.eclipse.lsp4j.CompletionList;
import org.eclipse.lsp4j.CompletionParams;
import org.eclipse.lsp4j.DidChangeTextDocumentParams;
import org.eclipse.lsp4j.InsertTextFormat;
import org.eclipse.lsp4j.MarkupContent;
import org.eclipse.lsp4j.MarkupKind;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.TextDocumentContentChangeEvent;
import org.eclipse.lsp4j.TextEdit;
import org.eclipse.lsp4j.VersionedTextDocumentIdentifier;
import org.junit.After;
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

	private DocumentLifeCycleHandler lifeCycleHandler;
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
		//		sharedASTProvider.clearASTCreationCount();
		javaClient = new JavaClientConnection(client);
		lifeCycleHandler = new DocumentLifeCycleHandler(javaClient, preferenceManager, projectsManager, true);
	}

	@After
	public void tearDown() throws Exception {
		javaClient.disconnect();
	}

	@Test
	public void testCompletion_javadoc() throws Exception {
		IJavaProject javaProject = JavaCore.create(project);
		ICompilationUnit unit = (ICompilationUnit) javaProject.findElement(new Path("org/sample/TestJavadoc.java"));
		unit.becomeWorkingCopy(null);
		String joinOnCompletion = System.getProperty(JDTLanguageServer.JAVA_LSP_JOIN_ON_COMPLETION);
		try {
			System.setProperty(JDTLanguageServer.JAVA_LSP_JOIN_ON_COMPLETION, "true");
			int[] loc = findCompletionLocation(unit, "inner.");
			CompletionParams position = JsonMessageHelper.getParams(createCompletionRequest(unit, loc[0], loc[1]));
			String source = unit.getSource();
			changeDocument(unit, source, 3);
			Job.getJobManager().join(DocumentLifeCycleHandler.DOCUMENT_LIFE_CYCLE_JOBS, new NullProgressMonitor());
			changeDocument(unit, source, 4);
			CompletionList list = server.completion(position).join().getRight();
			CompletionItem resolved = server.resolveCompletionItem(list.getItems().get(0)).join();
			assertEquals("Test ", resolved.getDocumentation().getLeft());
		} finally {
			unit.discardWorkingCopy();
			if (joinOnCompletion == null) {
				System.clearProperty(JDTLanguageServer.JAVA_LSP_JOIN_ON_COMPLETION);
			} else {
				System.setProperty(JDTLanguageServer.JAVA_LSP_JOIN_ON_COMPLETION, joinOnCompletion);
			}
		}
	}

	@Test
	public void testCompletion_javadocMarkdown() throws Exception {
		IJavaProject javaProject = JavaCore.create(project);
		ClientPreferences mockCapabilies = Mockito.mock(ClientPreferences.class);
		Mockito.when(preferenceManager.getClientPreferences()).thenReturn(mockCapabilies);
		Mockito.when(mockCapabilies.isSupportsCompletionDocumentationMarkdown()).thenReturn(true);
		ICompilationUnit unit = (ICompilationUnit) javaProject.findElement(new Path("org/sample/TestJavadoc.java"));
		unit.becomeWorkingCopy(null);
		String joinOnCompletion = System.getProperty(JDTLanguageServer.JAVA_LSP_JOIN_ON_COMPLETION);
		try {
			System.setProperty(JDTLanguageServer.JAVA_LSP_JOIN_ON_COMPLETION, "true");
			int[] loc = findCompletionLocation(unit, "inner.");
			CompletionParams position = JsonMessageHelper.getParams(createCompletionRequest(unit, loc[0], loc[1]));
			String source = unit.getSource();
			changeDocument(unit, source, 3);
			Job.getJobManager().join(DocumentLifeCycleHandler.DOCUMENT_LIFE_CYCLE_JOBS, new NullProgressMonitor());
			changeDocument(unit, source, 4);
			CompletionList list = server.completion(position).join().getRight();
			CompletionItem resolved = server.resolveCompletionItem(list.getItems().get(0)).join();
			MarkupContent markup = resolved.getDocumentation().getRight();
			assertNotNull(markup);
			assertEquals(MarkupKind.MARKDOWN, markup.getKind());
			assertEquals("Test", markup.getValue());
		} finally {
			unit.discardWorkingCopy();
			if (joinOnCompletion == null) {
				System.clearProperty(JDTLanguageServer.JAVA_LSP_JOIN_ON_COMPLETION);
			} else {
				System.setProperty(JDTLanguageServer.JAVA_LSP_JOIN_ON_COMPLETION, joinOnCompletion);
			}
		}
	}

	private void changeDocument(ICompilationUnit unit, String content, int version) throws JavaModelException {
		DidChangeTextDocumentParams changeParms = new DidChangeTextDocumentParams();
		VersionedTextDocumentIdentifier textDocument = new VersionedTextDocumentIdentifier();
		textDocument.setUri(JDTUtils.toURI(unit));
		textDocument.setVersion(version);
		changeParms.setTextDocument(textDocument);
		TextDocumentContentChangeEvent event = new TextDocumentContentChangeEvent();
		event.setText(content);
		List<TextDocumentContentChangeEvent> contentChanges = new ArrayList<>();
		contentChanges.add(event);
		changeParms.setContentChanges(contentChanges);
		lifeCycleHandler.didChange(changeParms);
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
	public void testCompletion_dataFieldURI() throws Exception {
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

		List<CompletionItem> items = new ArrayList<>(list.getItems());
		for ( CompletionItem item : items) {
			@SuppressWarnings("unchecked")
			Map<String,String> data = (Map<String, String>) item.getData();
			assertNotNull(data);
			String uri = data.get(CompletionResolveHandler.DATA_FIELD_URI);
			assertTrue(isNotBlank(uri));
			assertTrue("unexpected URI prefix: " + uri, uri.matches("file://.*/src/java/Foo\\.java"));
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
	public void testCompletion_javadocComment() throws JavaModelException {
		ICompilationUnit unit = getWorkingCopy(
		//@formatter:off
		"src/java/Foo.java",
		"public class Foo {\n"+
		"	/** */ \n"+
		"	void foo(int i, String s) {\n"+
		"	}\n"+
		"}\n");
		//@formatter:on
		int[] loc = findCompletionLocation(unit, "/**");
		CompletionList list = server.completion(JsonMessageHelper.getParams(createCompletionRequest(unit, loc[0], loc[1]))).join().getRight();
		assertNotNull(list);
		assertEquals(1, list.getItems().size());
		CompletionItem item = list.getItems().get(0);
		assertNull(item.getInsertText());
		assertEquals(JavadocCompletionProposal.JAVA_DOC_COMMENT, item.getLabel());
		assertEquals(CompletionItemKind.Snippet, item.getKind());
		assertEquals("999999999", item.getSortText());
		assertEquals(item.getInsertTextFormat(), InsertTextFormat.Snippet);
		assertNotNull(item.getTextEdit());
		assertEquals("\n * ${0}\n * @param i\n * @param s\n", item.getTextEdit().getNewText());
		Range range = item.getTextEdit().getRange();
		assertEquals(1, range.getStart().getLine());
		assertEquals(4, range.getStart().getCharacter());
		assertEquals(1, range.getEnd().getLine());
		assertEquals(" * @param i\n * @param s\n", item.getDocumentation().getLeft());
	}

	@Test
	public void testCompletion_javadocCommentNoSnippet() throws JavaModelException {
		ClientPreferences mockCapabilies = Mockito.mock(ClientPreferences.class);
		Mockito.when(preferenceManager.getClientPreferences()).thenReturn(mockCapabilies);
		Mockito.when(mockCapabilies.isCompletionSnippetsSupported()).thenReturn(false);
		ICompilationUnit unit = getWorkingCopy(
		//@formatter:off
		"src/java/Foo.java",
		"public class Foo {\n"+
		"	/** */ \n"+
		"	void foo(int i, String s) {\n"+
		"	}\n"+
		"}\n");
		//@formatter:on
		int[] loc = findCompletionLocation(unit, "/**");
		CompletionList list = server.completion(JsonMessageHelper.getParams(createCompletionRequest(unit, loc[0], loc[1]))).join().getRight();
		assertNotNull(list);
		assertEquals(1, list.getItems().size());
		CompletionItem item = list.getItems().get(0);
		assertNull(item.getInsertText());
		assertEquals(JavadocCompletionProposal.JAVA_DOC_COMMENT, item.getLabel());
		assertEquals(CompletionItemKind.Snippet, item.getKind());
		assertEquals("999999999", item.getSortText());
		assertEquals(item.getInsertTextFormat(), InsertTextFormat.PlainText);
		assertNotNull(item.getTextEdit());
		assertEquals("\n * @param i\n * @param s\n", item.getTextEdit().getNewText());
		Range range = item.getTextEdit().getRange();
		assertEquals(1, range.getStart().getLine());
		assertEquals(4, range.getStart().getCharacter());
		assertEquals(1, range.getEnd().getLine());
		assertEquals(" * @param i\n * @param s\n", item.getDocumentation().getLeft());
	}

	@Test
	public void testCompletion_javadocCommentPartial() throws JavaModelException {
		ICompilationUnit unit = getWorkingCopy(
		//@formatter:off
		"src/java/Foo.java",
		"public class Foo {\n"+
		"	/** \n"+
		"	 * @int \n"+
		"	*/ \n"+
		"	void foo(int i, String s) {\n"+
		"	}\n"+
		"}\n");
		//@formatter:on
		int[] loc = findCompletionLocation(unit, "/**");
		CompletionList list = server.completion(JsonMessageHelper.getParams(createCompletionRequest(unit, loc[0], loc[1]))).join().getRight();
		assertNotNull(list);
		assertEquals(0, list.getItems().size());
	}

	@Test
	public void testCompletion_javadocCommentRegular() throws JavaModelException {
		ICompilationUnit unit = getWorkingCopy(
		//@formatter:off
		"src/java/Foo.java",
		"public class Foo {\n"+
		"	/* */ \n"+
		"	void foo(int i, String s) {\n"+
		"	}\n"+
		"}\n");
		//@formatter:on
		int[] loc = findCompletionLocation(unit, "/*");
		CompletionList list = server.completion(JsonMessageHelper.getParams(createCompletionRequest(unit, loc[0], loc[1]))).join().getRight();
		assertNotNull(list);
		assertEquals(0, list.getItems().size());
	}

	@Test
	public void testCompletion_javadocCommentNoParam() throws JavaModelException {
		ICompilationUnit unit = getWorkingCopy(
		//@formatter:off
		"src/java/Foo.java",
		"public class Foo {\n"+
		"	/** */ \n"+
		"	void foo() {\n"+
		"	}\n"+
		"}\n");
		//@formatter:on
		int[] loc = findCompletionLocation(unit, "/**");
		CompletionList list = server.completion(JsonMessageHelper.getParams(createCompletionRequest(unit, loc[0], loc[1]))).join().getRight();
		assertNotNull(list);
		assertEquals(0, list.getItems().size());
	}

	@Test
	public void testCompletion_import_static() throws JavaModelException{
		ICompilationUnit unit = getWorkingCopy(
				"src/java/Foo.java",
				"import static java.util.concurrent.TimeUnit. \n" +
						"public class Foo {\n"+
						"	void foo() {\n"+
						"	}\n"+
				"}\n");

		int[] loc = findCompletionLocation(unit, "java.util.concurrent.TimeUnit.");

		CompletionList list = server.completion(JsonMessageHelper.getParams(createCompletionRequest(unit, loc[0], loc[1]))).join().getRight();

		assertNotNull(list);
		assertEquals(9, list.getItems().size());

		//// .SECONDS - enum value
		CompletionItem secondsFieldItem = list.getItems().get(0);
		// Check completion item
		assertEquals("SECONDS", secondsFieldItem.getInsertText());
		assertEquals("SECONDS : TimeUnit", secondsFieldItem.getLabel());
		assertEquals(CompletionItemKind.Field, secondsFieldItem.getKind());
		assertEquals("999999210", secondsFieldItem.getSortText());
		assertNull(secondsFieldItem.getTextEdit());

		assertNotNull(server.resolveCompletionItem(secondsFieldItem).join());
		TextEdit teSeconds = secondsFieldItem.getTextEdit();
		assertNotNull(teSeconds);
		assertEquals("SECONDS;", teSeconds.getNewText());
		assertNotNull(teSeconds.getRange());
		Range secondsRange = teSeconds.getRange();
		assertEquals(0, secondsRange.getStart().getLine());
		assertEquals(44, secondsRange.getStart().getCharacter());
		assertEquals(0, secondsRange.getEnd().getLine());

		//// .values() - static method
		CompletionItem valuesMethodItem = list.getItems().get(7);
		// Check completion item
		assertEquals("values", valuesMethodItem.getInsertText());
		assertEquals("values() : TimeUnit[]", valuesMethodItem.getLabel());
		assertEquals(CompletionItemKind.Module, valuesMethodItem.getKind());
		assertEquals("999999211", valuesMethodItem.getSortText());
		assertNull(valuesMethodItem.getTextEdit());


		assertNotNull(server.resolveCompletionItem(valuesMethodItem).join());
		TextEdit teValues = valuesMethodItem.getTextEdit();
		assertNotNull(teValues);
		assertEquals("values;", teValues.getNewText());
		assertNotNull(teValues.getRange());
		Range valuesRange = teValues.getRange();
		assertEquals(0, valuesRange.getStart().getLine());
		assertEquals(44, valuesRange.getStart().getCharacter());
		assertEquals(0, valuesRange.getEnd().getLine());

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
		assertEquals(2, edits.size());
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
		assertEquals(2, edits.size());
	}

	@Test
	public void testCompletion_method_guessMethodArgumentsFalse() throws JavaModelException {
		testCompletion_method_guessMethodArguments(false, "test(${1:name}, ${2:i});");
	}

	@Test
	public void testCompletion_method_guessMethodArgumentsTrue() throws JavaModelException {
		testCompletion_method_guessMethodArguments(true, "test(${1:str}, ${2:x});");
	}

	private void testCompletion_method_guessMethodArguments(boolean guessMethodArguments, String expected) throws JavaModelException {
		ICompilationUnit unit = getWorkingCopy(
		//@formatter:off
				"src/java/Foo.java",
				"public class Foo {\n" +
				"	static void test(String name, int i) {}\n" +
				"	public static void main(String[] args) {\n" +
				"		String str = \"x\";\n" +
				"		int  x = 0;\n" +
				"		tes\n" +
				"	}\n\n" +
				"}\n");
		//@formatter:on
		int[] loc = findCompletionLocation(unit, "tes");
		boolean oldGuessMethodArguments = JavaLanguageServerPlugin.getPreferencesManager().getPreferences().isGuessMethodArguments();
		try {
			JavaLanguageServerPlugin.getPreferencesManager().getPreferences().setGuessMethodArguments(guessMethodArguments);
			CompletionList list = server.completion(JsonMessageHelper.getParams(createCompletionRequest(unit, loc[0], loc[1]))).join().getRight();
			assertNotNull(list);
			CompletionItem ci = list.getItems().stream().filter(item -> item.getLabel().equals("test(String name, int i) : void")).findFirst().orElse(null);
			assertNotNull(ci);

			assertEquals("test", ci.getInsertText());
			assertEquals(CompletionItemKind.Function, ci.getKind());
			assertEquals("999999163", ci.getSortText());
			assertNull(ci.getTextEdit());

			CompletionItem resolvedItem = server.resolveCompletionItem(ci).join();
			assertNotNull(resolvedItem.getTextEdit());
			assertTextEdit(5, 2, 5, expected, resolvedItem.getTextEdit());
		} finally {
			JavaLanguageServerPlugin.getPreferencesManager().getPreferences().setGuessMethodArguments(oldGuessMethodArguments);
		}
	}

	@Test
	public void testCompletion_method_guessMethodArguments2() throws JavaModelException {
		ICompilationUnit unit = getWorkingCopy(
		//@formatter:off
				"src/java/Foo.java",
				"public class Foo {\n" +
				"	static void test(String name, int i) {}\n" +
				"	public static void main(String[] args) {\n" +
				"		String str = \"x\";\n" +
				"		tes\n" +
				"	}\n\n" +
				"}\n");
		//@formatter:on
		int[] loc = findCompletionLocation(unit, "tes");
		boolean oldGuessMethodArguments = JavaLanguageServerPlugin.getPreferencesManager().getPreferences().isGuessMethodArguments();
		try {
			JavaLanguageServerPlugin.getPreferencesManager().getPreferences().setGuessMethodArguments(true);
			CompletionList list = server.completion(JsonMessageHelper.getParams(createCompletionRequest(unit, loc[0], loc[1]))).join().getRight();
			assertNotNull(list);
			CompletionItem ci = list.getItems().stream().filter(item -> item.getLabel().equals("test(String name, int i) : void")).findFirst().orElse(null);
			assertNotNull(ci);

			assertEquals("test", ci.getInsertText());
			assertEquals(CompletionItemKind.Function, ci.getKind());
			assertEquals("999999163", ci.getSortText());
			assertNull(ci.getTextEdit());

			CompletionItem resolvedItem = server.resolveCompletionItem(ci).join();
			assertNotNull(resolvedItem.getTextEdit());
			assertTextEdit(4, 2, 5, "test(${1:str}, ${2:0});", resolvedItem.getTextEdit());
		} finally {
			JavaLanguageServerPlugin.getPreferencesManager().getPreferences().setGuessMethodArguments(oldGuessMethodArguments);
		}
	}

	@Test
	public void testCompletion_method_guessMethodArguments3() throws JavaModelException {
		ICompilationUnit unit = getWorkingCopy(
		//@formatter:off
				"src/java/Foo.java",
				"public class Foo {\n" +
				"	static void test(int i, int j) {}\n" +
				"	public static void main(String[] args) {\n" +
				"		int one=1;\n" +
				"		int two=2;\n" +
				"		tes\n" +
				"	}\n\n" +
				"}\n");
		//@formatter:on
		int[] loc = findCompletionLocation(unit, "tes");
		boolean oldGuessMethodArguments = JavaLanguageServerPlugin.getPreferencesManager().getPreferences().isGuessMethodArguments();
		try {
			JavaLanguageServerPlugin.getPreferencesManager().getPreferences().setGuessMethodArguments(true);
			CompletionList list = server.completion(JsonMessageHelper.getParams(createCompletionRequest(unit, loc[0], loc[1]))).join().getRight();
			assertNotNull(list);
			CompletionItem ci = list.getItems().stream().filter(item -> item.getLabel().equals("test(int i, int j) : void")).findFirst().orElse(null);
			assertNotNull(ci);

			assertEquals("test", ci.getInsertText());
			assertEquals(CompletionItemKind.Function, ci.getKind());
			assertEquals("999999163", ci.getSortText());
			assertNull(ci.getTextEdit());

			CompletionItem resolvedItem = server.resolveCompletionItem(ci).join();
			assertNotNull(resolvedItem.getTextEdit());
			assertTextEdit(5, 2, 5, "test(${1:one}, ${2:two});", resolvedItem.getTextEdit());
		} finally {
			JavaLanguageServerPlugin.getPreferencesManager().getPreferences().setGuessMethodArguments(oldGuessMethodArguments);
		}
	}

	@Test
	public void testCompletion_method_guessMethodArgumentsConstructor() throws JavaModelException {
		ICompilationUnit unit = getWorkingCopy(
		//@formatter:off
				"src/java/Foo.java",
				"public class Foo {\n" +
				"	public static void main(String[] args) {\n" +
				"		String str = \"x\";\n" +
				"		new A\n" +
				"	}\n" +
				"	private static class A { public A(String name){} }\n" +
				"}\n");
		//@formatter:on
		int[] loc = findCompletionLocation(unit, "new A");
		boolean oldGuessMethodArguments = JavaLanguageServerPlugin.getPreferencesManager().getPreferences().isGuessMethodArguments();
		try {
			JavaLanguageServerPlugin.getPreferencesManager().getPreferences().setGuessMethodArguments(true);
			CompletionList list = server.completion(JsonMessageHelper.getParams(createCompletionRequest(unit, loc[0], loc[1]))).join().getRight();
			assertNotNull(list);
			CompletionItem ci = list.getItems().stream().filter(item -> item.getLabel().equals("A(String name)")).findFirst().orElse(null);
			assertNotNull(ci);

			assertEquals("A", ci.getInsertText());
			assertEquals(CompletionItemKind.Constructor, ci.getKind());
			assertEquals("999999051", ci.getSortText());
			assertNull(ci.getTextEdit());

			CompletionItem resolvedItem = server.resolveCompletionItem(ci).join();
			assertNotNull(resolvedItem.getTextEdit());
			assertTextEdit(3, 6, 7, "A(${1:str})", resolvedItem.getTextEdit());
		} finally {
			JavaLanguageServerPlugin.getPreferencesManager().getPreferences().setGuessMethodArguments(oldGuessMethodArguments);
		}
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
		assertFalse(items.isEmpty());
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
	public void testSnippet_interface() throws JavaModelException {
		ICompilationUnit unit = getWorkingCopy("src/org/sample/Test.java", "");
		int[] loc = findCompletionLocation(unit, "");
		CompletionList list = server.completion(JsonMessageHelper.getParams(createCompletionRequest(unit, loc[0], loc[1]))).join().getRight();

		assertNotNull(list);
		List<CompletionItem> items = new ArrayList<>(list.getItems());
		assertFalse(items.isEmpty());
		items.sort((i1, i2) -> (i1.getSortText().compareTo(i2.getSortText())));

		CompletionItem item = items.get(9);
		assertEquals("interface", item.getLabel());
		String te = item.getInsertText();
		assertEquals("package org.sample;\n\n/**\n * Test\n */\npublic interface Test {\n\n\t${0}\n}", te);

		//check resolution doesn't blow up (https://github.com/eclipse/eclipse.jdt.ls/issues/675)
		assertSame(item, server.resolveCompletionItem(item).join());
	}

	@Test
	public void testSnippet_interface_with_package() throws JavaModelException {
		ICompilationUnit unit = getWorkingCopy("src/org/sample/Test.java", "package org.sample;\n");
		int[] loc = findCompletionLocation(unit, "package org.sample;\n");
		CompletionList list = server.completion(JsonMessageHelper.getParams(createCompletionRequest(unit, loc[0], loc[1]))).join().getRight();

		assertNotNull(list);
		List<CompletionItem> items = new ArrayList<>(list.getItems());
		assertFalse(items.isEmpty());
		items.sort((i1, i2) -> (i1.getSortText().compareTo(i2.getSortText())));

		CompletionItem item = items.get(8);
		assertEquals("interface", item.getLabel());
		String te = item.getInsertText();
		assertEquals("/**\n * Test\n */\npublic interface Test {\n\n\t${0}\n}", te);
	}

	@Test
	public void testSnippet_inner_interface() throws JavaModelException {
		ICompilationUnit unit = getWorkingCopy("src/org/sample/Test.java", "package org.sample;\npublic interface Test {}\n");
		int[] loc = findCompletionLocation(unit, "package org.sample;\npublic interface Test {}\n");
		CompletionList list = server.completion(JsonMessageHelper.getParams(createCompletionRequest(unit, loc[0], loc[1]))).join().getRight();

		assertNotNull(list);
		List<CompletionItem> items = new ArrayList<>(list.getItems());
		assertFalse(items.isEmpty());
		items.sort((i1, i2) -> (i1.getSortText().compareTo(i2.getSortText())));

		CompletionItem item = items.get(6);
		assertEquals("interface", item.getLabel());
		String te = item.getInsertText();
		assertEquals("/**\n * ${1:InnerTest}\n */\npublic interface ${1:InnerTest} {\n\n\t${0}\n}", te);
	}

	@Test
	public void testSnippet_sibling_inner_interface() throws JavaModelException {
		ICompilationUnit unit = getWorkingCopy("src/org/sample/Test.java", "package org.sample;\npublic interface Test {}\npublic interface InnerTest{}\n");
		int[] loc = findCompletionLocation(unit, "package org.sample;\npublic interface Test {}\npublic interface InnerTest{}\n");
		CompletionList list = server.completion(JsonMessageHelper.getParams(createCompletionRequest(unit, loc[0], loc[1]))).join().getRight();

		assertNotNull(list);
		List<CompletionItem> items = new ArrayList<>(list.getItems());
		assertFalse(items.isEmpty());
		items.sort((i1, i2) -> (i1.getSortText().compareTo(i2.getSortText())));

		CompletionItem item = items.get(6);
		assertEquals("interface", item.getLabel());
		String te = item.getInsertText();
		assertEquals("/**\n * ${1:InnerTest_1}\n */\npublic interface ${1:InnerTest_1} {\n\n\t${0}\n}", te);
	}

	@Test
	public void testSnippet_nested_inner_interface() throws JavaModelException {
		ICompilationUnit unit = getWorkingCopy("src/org/sample/Test.java", "package org.sample;\npublic interface Test {}\npublic interface InnerTest{\n");
		int[] loc = findCompletionLocation(unit, "package org.sample;\npublic interface Test {}\npublic interface InnerTest{\n");
		CompletionList list = server.completion(JsonMessageHelper.getParams(createCompletionRequest(unit, loc[0], loc[1]))).join().getRight();

		assertNotNull(list);
		List<CompletionItem> items = new ArrayList<>(list.getItems());
		assertFalse(items.isEmpty());
		items.sort((i1, i2) -> (i1.getSortText().compareTo(i2.getSortText())));

		CompletionItem item = items.get(23);
		assertEquals("interface", item.getLabel());
		String te = item.getInsertText();
		assertEquals("/**\n * ${1:InnerTest_1}\n */\npublic interface ${1:InnerTest_1} {\n\n\t${0}\n}", te);
	}

	@Test
	public void testSnippet_nested_inner_interface_nosnippet() throws JavaModelException {
		mockLSP2Client();
		ICompilationUnit unit = getWorkingCopy("src/org/sample/Test.java", "package org.sample;\npublic interface Test {}\npublic interface InnerTest{\n");
		int[] loc = findCompletionLocation(unit, "package org.sample;\npublic interface Test {}\npublic interface InnerTest{\n");
		CompletionList list = server.completion(JsonMessageHelper.getParams(createCompletionRequest(unit, loc[0], loc[1]))).join().getRight();

		assertNotNull(list);
		assertFalse("No snippets should be returned", list.getItems().stream().anyMatch(ci -> ci.getKind() == CompletionItemKind.Snippet));
	}

	@Test
	public void testSnippet_class() throws JavaModelException {
		ICompilationUnit unit = getWorkingCopy("src/org/sample/Test.java", "");
		int[] loc = findCompletionLocation(unit, "");
		CompletionList list = server.completion(JsonMessageHelper.getParams(createCompletionRequest(unit, loc[0], loc[1]))).join().getRight();

		assertNotNull(list);
		List<CompletionItem> items = new ArrayList<>(list.getItems());
		assertFalse(items.isEmpty());
		items.sort((i1, i2) -> (i1.getSortText().compareTo(i2.getSortText())));

		CompletionItem item = items.get(8);
		assertEquals("class", item.getLabel());
		String te = item.getInsertText();
		assertEquals("package org.sample;\n\n/**\n * Test\n */\npublic class Test {\n\n\t${0}\n}", te);
	}

	@Test
	public void testSnippet_class_with_package() throws JavaModelException {
		ICompilationUnit unit = getWorkingCopy("src/org/sample/Test.java", "package org.sample;\n");
		int[] loc = findCompletionLocation(unit, "package org.sample;\n");
		CompletionList list = server.completion(JsonMessageHelper.getParams(createCompletionRequest(unit, loc[0], loc[1]))).join().getRight();

		assertNotNull(list);
		List<CompletionItem> items = new ArrayList<>(list.getItems());
		assertFalse(items.isEmpty());
		items.sort((i1, i2) -> (i1.getSortText().compareTo(i2.getSortText())));

		CompletionItem item = items.get(7);
		assertEquals("class", item.getLabel());
		String te = item.getInsertText();
		assertEquals("/**\n * Test\n */\npublic class Test {\n\n\t${0}\n}", te);
	}

	@Test
	public void testSnippet_inner_class() throws JavaModelException {
		ICompilationUnit unit = getWorkingCopy("src/org/sample/Test.java", "package org.sample;\npublic class Test {}\n");
		int[] loc = findCompletionLocation(unit, "");
		CompletionList list = server.completion(JsonMessageHelper.getParams(createCompletionRequest(unit, loc[0], loc[1]))).join().getRight();

		assertNotNull(list);
		List<CompletionItem> items = new ArrayList<>(list.getItems());
		assertFalse(items.isEmpty());
		items.sort((i1, i2) -> (i1.getSortText().compareTo(i2.getSortText())));

		CompletionItem item = items.get(5);
		assertEquals("class", item.getLabel());
		String te = item.getInsertText();
		assertEquals("/**\n * ${1:InnerTest}\n */\npublic class ${1:InnerTest} {\n\n\t${0}\n}", te);
	}

	@Test
	public void testSnippet_sibling_inner_class() throws JavaModelException {
		ICompilationUnit unit = getWorkingCopy("src/org/sample/Test.java", "package org.sample;\npublic class Test {}\npublic class InnerTest{}\n");
		int[] loc = findCompletionLocation(unit, "package org.sample;\npublic class Test {}\npublic class InnerTest{}\n");
		CompletionList list = server.completion(JsonMessageHelper.getParams(createCompletionRequest(unit, loc[0], loc[1]))).join().getRight();

		assertNotNull(list);
		List<CompletionItem> items = new ArrayList<>(list.getItems());
		assertFalse(items.isEmpty());
		items.sort((i1, i2) -> (i1.getSortText().compareTo(i2.getSortText())));

		CompletionItem item = items.get(5);
		assertEquals("class", item.getLabel());
		String te = item.getInsertText();
		assertEquals("/**\n * ${1:InnerTest_1}\n */\npublic class ${1:InnerTest_1} {\n\n\t${0}\n}", te);
	}

	@Test
	public void testSnippet_sibling_inner_class_nosnippets() throws JavaModelException {
		mockLSP2Client();
		ICompilationUnit unit = getWorkingCopy("src/org/sample/Test.java", "package org.sample;\npublic class Test {}\npublic class InnerTest{}\n");
		int[] loc = findCompletionLocation(unit, "package org.sample;\npublic class Test {}\npublic class InnerTest{}\n");
		CompletionList list = server.completion(JsonMessageHelper.getParams(createCompletionRequest(unit, loc[0], loc[1]))).join().getRight();

		assertNotNull(list);
		assertFalse("No snippets should be returned", list.getItems().stream().anyMatch(ci -> ci.getKind() == CompletionItemKind.Snippet));
	}

	@Test
	public void testSnippet_nested_inner_class() throws JavaModelException {
		ICompilationUnit unit = getWorkingCopy("src/org/sample/Test.java", "package org.sample;\npublic class Test {}\npublic class InnerTest{\n");
		int[] loc = findCompletionLocation(unit, "package org.sample;\npublic class Test {}\npublic class InnerTest{\n");
		CompletionList list = server.completion(JsonMessageHelper.getParams(createCompletionRequest(unit, loc[0], loc[1]))).join().getRight();

		assertNotNull(list);
		List<CompletionItem> items = new ArrayList<>(list.getItems());
		assertFalse(items.isEmpty());
		items.sort((i1, i2) -> (i1.getSortText().compareTo(i2.getSortText())));

		CompletionItem item = items.get(21);
		assertEquals("class", item.getLabel());
		String te = item.getInsertText();
		assertNotNull(te);
		assertEquals("/**\n * ${1:InnerTest_1}\n */\npublic class ${1:InnerTest_1} {\n\n\t${0}\n}", te);
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
		assertEquals("Missing required imports", 1, oride.getAdditionalTextEdits().size());

		assertEquals("\n\nimport java.io.File;\n\n", oride.getAdditionalTextEdits().get(0).getNewText());
		assertPosition(0, 19, oride.getAdditionalTextEdits().get(0).getRange().getStart());
		assertPosition(2, 0, oride.getAdditionalTextEdits().get(0).getRange().getEnd());
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
		assertEquals("Missing required imports", 1, oride.getAdditionalTextEdits().size());
		assertEquals("\n\nimport java.io.IOException;\n\n", oride.getAdditionalTextEdits().get(0).getNewText());
		assertPosition(0, 19, oride.getAdditionalTextEdits().get(0).getRange().getStart());
		assertPosition(2, 0, oride.getAdditionalTextEdits().get(0).getRange().getEnd());
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

	@Test
	public void testCompletion_getter() throws Exception {

		ICompilationUnit unit = getWorkingCopy(
				"src/java/Foo.java",
				"public class Foo {\n"+
						"    private String strField;\n" +
						"    get" +
				"}\n");

		int[] loc = findCompletionLocation(unit, "get");


		CompletionList list = server.completion(JsonMessageHelper.getParams(createCompletionRequest(unit, loc[0], loc[1]))).join().getRight();
		assertNotNull(list);
		CompletionItem ci = list.getItems().stream()
				.filter(item -> item.getLabel().startsWith("getStrField() : String"))
				.findFirst().orElse(null);
		assertNotNull(ci);

		assertEquals("getStrField", ci.getInsertText());
		assertEquals(CompletionItemKind.Function, ci.getKind());
		assertEquals("999999979", ci.getSortText());
		assertNull(ci.getTextEdit());

		CompletionItem resolvedItem = server.resolveCompletionItem(ci).join();
		assertNotNull(resolvedItem.getTextEdit());
		assertTextEdit(2, 4, 7, "/**\n" +
				 " * @return the strField\n" +
				 " */\n" +
				"public String getStrField() {\n" +
				"	return strField;\n" +
				"}", resolvedItem.getTextEdit());
	}

	@Test
	public void testCompletion_booleangetter() throws Exception {

		ICompilationUnit unit = getWorkingCopy(
				"src/java/Foo.java",
				"public class Foo {\n"+
						"    private boolean boolField;\n" +
						"    is\n" +
				"}\n");

		int[] loc = findCompletionLocation(unit, "is");


		CompletionList list = server.completion(JsonMessageHelper.getParams(createCompletionRequest(unit, loc[0], loc[1]))).join().getRight();
		assertNotNull(list);
		CompletionItem ci = list.getItems().stream()
				.filter(item -> item.getLabel().startsWith("isBoolField() : boolean"))
				.findFirst().orElse(null);
		assertNotNull(ci);

		assertEquals("isBoolField", ci.getInsertText());
		assertEquals(CompletionItemKind.Function, ci.getKind());
		assertEquals("999999979", ci.getSortText());
		assertNull(ci.getTextEdit());

		CompletionItem resolvedItem = server.resolveCompletionItem(ci).join();
		assertNotNull(resolvedItem.getTextEdit());
		assertTextEdit(2, 4, 6, "/**\n" +
				 " * @return the boolField\n" +
				 " */\n" +
				"public boolean isBoolField() {\n" +
				"	return boolField;\n" +
				"}", resolvedItem.getTextEdit());
	}

	@Test
	public void testCompletion_setter() throws Exception {
		ICompilationUnit unit = getWorkingCopy(
				"src/java/Foo.java",
				"public class Foo {\n"+
						"    private String strField;\n" +
						"    set" +
				"}\n");

		int[] loc = findCompletionLocation(unit, "set");


		CompletionList list = server.completion(JsonMessageHelper.getParams(createCompletionRequest(unit, loc[0], loc[1]))).join().getRight();
		assertNotNull(list);
		CompletionItem ci = list.getItems().stream()
				.filter(item -> item.getLabel().startsWith("setStrField(String strField) : void"))
				.findFirst().orElse(null);
		assertNotNull(ci);

		assertEquals("setStrField", ci.getInsertText());
		assertEquals(CompletionItemKind.Function, ci.getKind());
		assertEquals("999999979", ci.getSortText());
		assertNull(ci.getTextEdit());

		CompletionItem resolvedItem = server.resolveCompletionItem(ci).join();
		assertNotNull(resolvedItem.getTextEdit());
		assertTextEdit(2, 4, 7, "/**\n" +
				" * @param strField the strField to set\n" +
				 " */\n" +
				"public void setStrField(String strField) {\n" +
				"	this.strField = strField;\n" +
				"}", resolvedItem.getTextEdit());
	}

	@Test
	public void testCompletion_AnonymousType() throws Exception {
		ICompilationUnit unit = getWorkingCopy(
				"src/java/Foo.java",
				"public class Foo {\n"+
						"    public static void main(String[] args) {\n" +
						"        IFoo foo = new \n" +
						"    } \n" +
						"    interface IFoo {\n"+
						"        String getName();\n"+
						"    }\n"+
				"}\n");
		waitForBackgroundJobs();
		int[] loc = findCompletionLocation(unit, "new ");
		CompletionList list = server.completion(JsonMessageHelper.getParams(createCompletionRequest(unit, loc[0], loc[1]))).join().getRight();
		assertNotNull(list);
		CompletionItem ci = list.getItems().stream()
				.filter(item -> item.getLabel().startsWith("Foo.IFoo()  Anonymous Inner Type"))
				.findFirst().orElse(null);
		assertNotNull(ci);

		assertEquals("Foo.IFoo", ci.getInsertText());
		assertEquals(CompletionItemKind.Constructor, ci.getKind());
		assertEquals("999998684", ci.getSortText());
		assertNull(ci.getTextEdit());

		CompletionItem resolvedItem = server.resolveCompletionItem(ci).join();
		assertNotNull(resolvedItem.getTextEdit());
		assertTextEdit(2, 23, 23, "IFoo(){\n" +
				"\n" +
				"		@Override\n" +
				"		public String getName() {\n" +
				"			${0:return null;}\n" +
				"		}\n" +
				"};", resolvedItem.getTextEdit());
	}

	@Test
	public void testCompletion_AnonymousTypeMoreMethods() throws Exception {
		ICompilationUnit unit = getWorkingCopy(
				"src/java/Foo.java",
				"public class Foo {\n"+
						"    public static void main(String[] args) {\n" +
						"        IFoo foo = new \n" +
						"    } \n" +
						"    interface IFoo {\n"+
						"        String getName();\n"+
						"        void setName(String name);\n"+
						"    }\n"+
				"}\n");
		waitForBackgroundJobs();
		int[] loc = findCompletionLocation(unit, "new ");
		CompletionList list = server.completion(JsonMessageHelper.getParams(createCompletionRequest(unit, loc[0], loc[1]))).join().getRight();
		assertNotNull(list);
		CompletionItem ci = list.getItems().stream()
				.filter(item -> item.getLabel().startsWith("Foo.IFoo()  Anonymous Inner Type"))
				.findFirst().orElse(null);
		assertNotNull(ci);

		assertEquals("Foo.IFoo", ci.getInsertText());
		assertEquals(CompletionItemKind.Constructor, ci.getKind());
		assertEquals("999998684", ci.getSortText());
		assertNull(ci.getTextEdit());

		CompletionItem resolvedItem = server.resolveCompletionItem(ci).join();
		assertNotNull(resolvedItem.getTextEdit());
		assertTextEdit(2, 23, 23, "IFoo(){\n" +
				"\n		@Override\n" +
				"		public void setName(String name) {\n" +
				"			${0}\n" +
				"		}\n" +
				"\n		@Override\n" +
				"		public String getName() {\n" +
				"			return null;\n" +
				"		}\n" +
				"};", resolvedItem.getTextEdit());
	}

	@Test
	public void testCompletion_AnonymousDeclarationType() throws Exception {
		ICompilationUnit unit = getWorkingCopy(
				"src/java/Foo.java",
				"public class Foo {\n"+
						"    public static void main(String[] args) {\n" +
						"        new Runnable()\n" +
						"    }\n" +
				"}\n");
		waitForBackgroundJobs();
		int[] loc = findCompletionLocation(unit, "Runnable(");
		CompletionList list = server.completion(JsonMessageHelper.getParams(createCompletionRequest(unit, loc[0], loc[1]))).join().getRight();
		assertNotNull(list);
		CompletionItem ci = list.getItems().stream()
				.filter(item -> item.getLabel().startsWith("Runnable()  Anonymous Inner Type"))
				.findFirst().orElse(null);
		assertNotNull(ci);

		assertEquals("Runnable", ci.getInsertText());
		assertEquals(CompletionItemKind.Class, ci.getKind());
		assertEquals("999999372", ci.getSortText());
		assertNull(ci.getTextEdit());

		CompletionItem resolvedItem = server.resolveCompletionItem(ci).join();
		assertNotNull(resolvedItem.getTextEdit());
		assertTextEdit(2, 20, 22, "(){\n" +
				"\n" +
				"	@Override\n" +
				"	public void run() {\n" +
				"		${0}\n" +
				"	}\n" +
				"}", resolvedItem.getTextEdit());
	}

	@Test
	public void testCompletion_AnonymousDeclarationType2() throws Exception {
		ICompilationUnit unit = getWorkingCopy(
				"src/java/Foo.java",
				"public class Foo {\n"+
						"    public static void main(String[] args) {\n" +
						"        new Runnable(  )\n" +
						"    }\n" +
				"}\n");
		waitForBackgroundJobs();
		int[] loc = findCompletionLocation(unit, "Runnable( ");
		CompletionList list = server.completion(JsonMessageHelper.getParams(createCompletionRequest(unit, loc[0], loc[1]))).join().getRight();
		assertNotNull(list);
		CompletionItem ci = list.getItems().stream()
				.filter(item -> item.getLabel().startsWith("Runnable()  Anonymous Inner Type"))
				.findFirst().orElse(null);
		assertNotNull(ci);

		assertEquals("Runnable", ci.getInsertText());
		assertEquals(CompletionItemKind.Class, ci.getKind());
		assertEquals("999999372", ci.getSortText());
		assertNull(ci.getTextEdit());

		CompletionItem resolvedItem = server.resolveCompletionItem(ci).join();
		assertNotNull(resolvedItem.getTextEdit());
		assertTextEdit(2, 20, 24, "(){\n" +
				"\n" +
				"	@Override\n" +
				"	public void run() {\n" +
				"		${0}\n" +
				"	}\n" +
				"}", resolvedItem.getTextEdit());
	}

	@Test
	public void testCompletion_AnonymousDeclarationType3() throws Exception {
		ICompilationUnit unit = getWorkingCopy(
				"src/java/Foo.java",
				"public class Foo {\n"+
						"    public static void main(String[] args) {\n" +
						"        run(\"name\", new Runnable(, 1);\n" +
						"    }\n" +
						"    void run(String name, Runnable runnable, int i) {\n" +
						"    }\n" +
				"}\n");
		waitForBackgroundJobs();
		int[] loc = findCompletionLocation(unit, "Runnable(");
		CompletionList list = server.completion(JsonMessageHelper.getParams(createCompletionRequest(unit, loc[0], loc[1]))).join().getRight();
		assertNotNull(list);
		CompletionItem ci = list.getItems().stream()
				.filter(item -> item.getLabel().startsWith("Runnable()  Anonymous Inner Type"))
				.findFirst().orElse(null);
		assertNotNull(ci);

		assertEquals("Runnable", ci.getInsertText());
		assertEquals(CompletionItemKind.Class, ci.getKind());
		assertEquals("999999372", ci.getSortText());
		assertNull(ci.getTextEdit());

		CompletionItem resolvedItem = server.resolveCompletionItem(ci).join();
		assertNotNull(resolvedItem.getTextEdit());
		assertTextEdit(2, 32, 33, "(){\n" +
				"\n" +
				"	@Override\n" +
				"	public void run() {\n" +
				"		${0}\n" +
				"	}\n" +
				"}", resolvedItem.getTextEdit());
	}

	@Test
	public void testCompletion_AnonymousDeclarationType4() throws Exception {
		ICompilationUnit unit = getWorkingCopy(
				"src/java/Foo.java",
				"public class Foo {\n"+
						"    public static void main(String[] args) {\n" +
						"        run(\"name\", new Runnable(\n" +
						"        , 1);\n" +
						"    }\n" +
						"    void run(String name, Runnable runnable, int i) {\n" +
						"    }\n" +
				"}\n");
		waitForBackgroundJobs();
		int[] loc = findCompletionLocation(unit, "Runnable(");
		CompletionList list = server.completion(JsonMessageHelper.getParams(createCompletionRequest(unit, loc[0], loc[1]))).join().getRight();
		assertNotNull(list);
		CompletionItem ci = list.getItems().stream()
				.filter(item -> item.getLabel().startsWith("Runnable()  Anonymous Inner Type"))
				.findFirst().orElse(null);
		assertNotNull(ci);

		assertEquals("Runnable", ci.getInsertText());
		assertEquals(CompletionItemKind.Class, ci.getKind());
		assertEquals("999999372", ci.getSortText());
		assertNull(ci.getTextEdit());

		CompletionItem resolvedItem = server.resolveCompletionItem(ci).join();
		assertNotNull(resolvedItem.getTextEdit());
		assertTextEdit(2, 32, 33, "(){\n" +
				"\n" +
				"	@Override\n" +
				"	public void run() {\n" +
				"		${0}\n" +
				"	}\n" +
				"}", resolvedItem.getTextEdit());
	}

	@Test
	public void testCompletion_AnonymousDeclarationType5() throws Exception {
		ICompilationUnit unit = getWorkingCopy(
				"src/java/Foo.java",
				"public class Foo {\n"+
						"    public static void main(String[] args) {\n" +
						"        run(\"name\", new Runnable(");
		waitForBackgroundJobs();
		int[] loc = findCompletionLocation(unit, "Runnable(");
		CompletionList list = server.completion(JsonMessageHelper.getParams(createCompletionRequest(unit, loc[0], loc[1]))).join().getRight();
		assertNotNull(list);
		CompletionItem ci = list.getItems().stream()
				.filter(item -> item.getLabel().startsWith("Runnable()  Anonymous Inner Type"))
				.findFirst().orElse(null);
		assertNotNull(ci);

		assertEquals("Runnable", ci.getInsertText());
		assertEquals(CompletionItemKind.Class, ci.getKind());
		assertEquals("999999372", ci.getSortText());
		assertNull(ci.getTextEdit());

		CompletionItem resolvedItem = server.resolveCompletionItem(ci).join();
		assertNotNull(resolvedItem.getTextEdit());
		assertTextEdit(2, 33, 33, "(){\n" +
				"\n" +
				"	@Override\n" +
				"	public void run() {\n" +
				"		${0}\n" +
				"	}\n" +
				"}", resolvedItem.getTextEdit());
	}

	@Test
	public void testCompletion_class_name_contains_$() throws Exception {
		ICompilationUnit unit = getWorkingCopy(
				"src/org/sample/Foo$Bar.java",
				"public class Foo$Bar {\n"+
						"    public static void main(String[] args) {\n" +
						"        new Foo\n" +
						"    }\n" +
				"}\n");
		waitForBackgroundJobs();
		int[] loc = findCompletionLocation(unit, "new Foo");
		CompletionList list = server.completion(JsonMessageHelper.getParams(createCompletionRequest(unit, loc[0], loc[1]))).join().getRight();
		assertNotNull(list);
		CompletionItem ci = list.getItems().stream()
				.filter(item -> item.getLabel().startsWith("Foo$Bar"))
				.findFirst().orElse(null);
		assertNotNull(ci);

		assertEquals("Foo$Bar", ci.getInsertText());
		assertEquals(CompletionItemKind.Constructor, ci.getKind());
		assertEquals("999999115", ci.getSortText());
		assertNull(ci.getTextEdit());

		CompletionItem resolvedItem = server.resolveCompletionItem(ci).join();
		assertNotNull(resolvedItem.getTextEdit());
		assertTextEdit(2, 12, 15, "Foo\\$Bar()", resolvedItem.getTextEdit());
	}

	@Test
	public void testCompletion_class_name_contains_$withoutSnippetSupport() throws Exception {
		mockLSPClient(false, true);
		ICompilationUnit unit = getWorkingCopy(
				"src/org/sample/Foo$Bar.java",
				"public class Foo$Bar {\n"+
						"    public static void main(String[] args) {\n" +
						"        new Foo\n" +
						"    }\n" +
				"}\n");
		waitForBackgroundJobs();
		int[] loc = findCompletionLocation(unit, "new Foo");
		CompletionList list = server.completion(JsonMessageHelper.getParams(createCompletionRequest(unit, loc[0], loc[1]))).join().getRight();
		assertNotNull(list);
		CompletionItem ci = list.getItems().stream()
				.filter(item -> item.getLabel().startsWith("Foo$Bar"))
				.findFirst().orElse(null);
		assertNotNull(ci);

		assertEquals("Foo$Bar", ci.getInsertText());
		assertEquals(CompletionItemKind.Constructor, ci.getKind());
		assertEquals("999999115", ci.getSortText());
		assertNull(ci.getTextEdit());

		CompletionItem resolvedItem = server.resolveCompletionItem(ci).join();
		assertNotNull(resolvedItem.getTextEdit());
		assertTextEdit(2, 12, 15, "Foo$Bar", resolvedItem.getTextEdit());
	}

	@Test
	public void testCompletion_testClassesDontLeakIntoMainCode() throws Exception {
		ICompilationUnit unit = getWorkingCopy(
				//@formatter:off
				"src/org/sample/Test.java",
				"package org.sample;\n\n"+
				"public class Test extends AbstractTe {\n"+
				"}\n");
				//@formatter:on
		int[] loc = findCompletionLocation(unit, " AbstractTe");
		CompletionList list = server.completion(JsonMessageHelper.getParams(createCompletionRequest(unit, loc[0], loc[1]))).join().getRight();
		assertEquals("Test proposals leaked:\n" + list.getItems(), 0, list.getItems().size());
	}

	@Test
	public void testCompletion_testMethodWithParams() throws Exception {
		ICompilationUnit unit = getWorkingCopy(
		//@formatter:off
		"src/org/sample/Test.java",
		"package org.sample;\n" +
		"public class Test {\n" +
		"	public static void main(String[] args) {\n" +
		"		fo\n" +
		"		System.out.println(\"Hello World!\");\n" +
		"	}\n\n" +
		"	/**\n" +
		"	* This method has Javadoc\n" +
		"	*/\n" +
		"	public static void foo(String bar) {\n" +
		"	}\n" +
		"	/**\n" +
		"	* Another Javadoc\n" +
		"	*/\n" +
		"	public static void foo() {\n" +
		"	}\n" +
		"}\n");
		//@formatter:on
		int[] loc = findCompletionLocation(unit, "\t\tfo");
		CompletionList list = server.completion(JsonMessageHelper.getParams(createCompletionRequest(unit, loc[0], loc[1]))).join().getRight();
		assertNotNull(list);
		CompletionItem ci = list.getItems().stream().filter(item -> item.getLabel().startsWith("foo(String bar) : void")).findFirst().orElse(null);
		assertNotNull(ci);
		CompletionItem resolvedItem = server.resolveCompletionItem(ci).join();
		assertNotNull(resolvedItem);
		assertNotNull(resolvedItem.getDocumentation());
		assertNotNull(resolvedItem.getDocumentation().getLeft());
		String javadoc = resolvedItem.getDocumentation().getLeft();
		assertEquals(javadoc, " This method has Javadoc ");
		ci = list.getItems().stream().filter(item -> item.getLabel().startsWith("foo() : void")).findFirst().orElse(null);
		assertNotNull(ci);
		resolvedItem = server.resolveCompletionItem(ci).join();
		assertNotNull(resolvedItem);
		assertNotNull(resolvedItem.getDocumentation());
		assertNotNull(resolvedItem.getDocumentation().getLeft());
		javadoc = resolvedItem.getDocumentation().getLeft();
		assertEquals(javadoc, " Another Javadoc ");
	}

	@Test
	public void testCompletion_testClassesAvailableIntoTestCode() throws Exception {
		ICompilationUnit unit = getWorkingCopy(
		//@formatter:off
				"test/foo/bar/BaseTest.java",
				"package foo.bar;\n\n"+
				"public class BaseTest extends AbstractTe {\n"+
				"}\n");
				//@formatter:on
		int[] loc = findCompletionLocation(unit, " AbstractTe");
		CompletionList list = server.completion(JsonMessageHelper.getParams(createCompletionRequest(unit, loc[0], loc[1]))).join().getRight();
		assertNotNull(list);
		assertEquals("Test proposals missing from :\n" + list, 1, list.getItems().size());
		assertEquals("AbstractTest - foo.bar", list.getItems().get(0).getLabel());
	}

	@Test
	public void testCompletion_overwrite() throws Exception {
		ICompilationUnit unit = getCompletionOverwriteReplaceUnit();
		//@formatter:on
		int[] loc = findCompletionLocation(unit, "method(t.");
		CompletionList list = server.completion(JsonMessageHelper.getParams(createCompletionRequest(unit, loc[0], loc[1]))).join().getRight();
		assertNotNull(list);
		CompletionItem ci = list.getItems().stream().filter(item -> item.getLabel().startsWith("testInt : int")).findFirst().orElse(null);
		assertNotNull(ci);
		assertEquals("testInt", ci.getInsertText());
		assertEquals(CompletionItemKind.Field, ci.getKind());
		assertEquals("999998554", ci.getSortText());
		assertNull(ci.getTextEdit());
		CompletionItem resolvedItem = server.resolveCompletionItem(ci).join();
		assertNotNull(resolvedItem.getTextEdit());
		List<TextEdit> edits = new ArrayList<>();
		edits.add(resolvedItem.getTextEdit());
		String returned = TextEditUtil.apply(unit, edits);
		//@formatter:off
			String expected =
				"package foo.bar;\n\n" +
				"public class BaseTest {\n" +
				"    public int testInt;\n\n" +
				"    public boolean method(int x, int y, int z) {\n" +
				"        return true;\n" +
				"    } \n\n" +
				"    public void update() {\n" +
				"        BaseTest t = new BaseTest();\n" +
				"        t.method(t.testInt.testInt, this.testInt);\n" +
				"    }\n" +
				"}\n";
		//@formatter:on
		assertEquals(returned, expected);
	}

	@Test
	public void testCompletion_insert() throws Exception {
		ICompilationUnit unit = getCompletionOverwriteReplaceUnit();
		int[] loc = findCompletionLocation(unit, "method(t.");
		CompletionList list = server.completion(JsonMessageHelper.getParams(createCompletionRequest(unit, loc[0], loc[1]))).join().getRight();
		assertNotNull(list);
		CompletionItem ci = list.getItems().stream().filter(item -> item.getLabel().startsWith("testInt : int")).findFirst().orElse(null);
		assertNotNull(ci);
		assertEquals("testInt", ci.getInsertText());
		assertEquals(CompletionItemKind.Field, ci.getKind());
		assertEquals("999998554", ci.getSortText());
		assertNull(ci.getTextEdit());
		try {
			JavaLanguageServerPlugin.getPreferencesManager().getPreferences().setCompletionOverwrite(false);
		CompletionItem resolvedItem = server.resolveCompletionItem(ci).join();
		assertNotNull(resolvedItem.getTextEdit());
		List<TextEdit> edits = new ArrayList<>();
		edits.add(resolvedItem.getTextEdit());
		String returned = TextEditUtil.apply(unit, edits);
		//@formatter:off
			String expected =
				"package foo.bar;\n\n" +
				"public class BaseTest {\n" +
				"    public int testInt;\n\n" +
				"    public boolean method(int x, int y, int z) {\n" +
				"        return true;\n" +
				"    } \n\n" +
				"    public void update() {\n" +
				"        BaseTest t = new BaseTest();\n" +
				"        t.method(t.testIntthis.testInt, this.testInt);\n" +
				"    }\n" +
				"}\n";
		//@formatter:on
		assertEquals(returned, expected);
		} finally {
			JavaLanguageServerPlugin.getPreferencesManager().getPreferences().setCompletionOverwrite(true);
		}
	}

	private ICompilationUnit getCompletionOverwriteReplaceUnit() throws JavaModelException {
		ICompilationUnit unit = getWorkingCopy(
		//@formatter:off
				"test/foo/bar/BaseTest.java",
				"package foo.bar;\n\n" +
				"public class BaseTest {\n" +
				"    public int testInt;\n\n" +
				"    public boolean method(int x, int y, int z) {\n" +
				"        return true;\n" +
				"    } \n\n" +
				"    public void update() {\n" +
				"        BaseTest t = new BaseTest();\n" +
				"        t.method(t.this.testInt, this.testInt);\n" +
				"    }\n" +
				"}\n");
		//@formatter:on
		return unit;
	}

	@Test
	public void testSnippet_with_public() throws JavaModelException {
		ICompilationUnit unit = getWorkingCopy("src/org/sample/Test.java",
		//@formatter:off
			"package org.sample;\n" +
			"public ");
			//@formatter:off
		int[] loc = findCompletionLocation(unit, "public ");
		CompletionList list = server.completion(JsonMessageHelper.getParams(createCompletionRequest(unit, loc[0], loc[1]))).join().getRight();
		assertNotNull(list);
		CompletionItem ci = list.getItems().stream()
				.filter( item->  (item.getLabel().matches("class")
						&& item.getKind() == CompletionItemKind.Snippet))
				.findFirst().orElse(null);
		assertNotNull(ci);
		String text = ci.getInsertText();
		assertEquals("class Test {\n\n\t${0}\n}", text);
		ci = list.getItems().stream()
				.filter( item->  (item.getLabel().matches("interface") && item.getKind() == CompletionItemKind.Snippet))
				.findFirst().orElse(null);
		assertNotNull(ci);
		text = ci.getInsertText();
		assertEquals("interface Test {\n\n\t${0}\n}", text);
	}

	@Test
	public void testSnippet_context_javadoc() throws JavaModelException {
		ICompilationUnit unit = getWorkingCopy("src/org/sample/Test.java",
		//@formatter:off
			"package org.sample;\n" +
			"/**\n */");
			//@formatter:off
		int[] loc = findCompletionLocation(unit, "/**");
		CompletionList list = server.completion(JsonMessageHelper.getParams(createCompletionRequest(unit, loc[0], loc[1]))).join().getRight();
		assertNotNull(list);
		CompletionItem ci = list.getItems().stream()
				.filter( item->  (item.getLabel().matches("class") && item.getKind() == CompletionItemKind.Snippet))
				.findFirst().orElse(null);
		assertNull(ci);
		ci = list.getItems().stream()
				.filter( item->  (item.getLabel().matches("interface") && item.getKind() == CompletionItemKind.Snippet))
				.findFirst().orElse(null);
		assertNull(ci);
	}

	@Test
	public void testSnippet_context_package() throws JavaModelException {
		ICompilationUnit unit = getWorkingCopy("src/org/sample/Test.java",
		//@formatter:off
			"package org.sample;\n");
			//@formatter:off
		int[] loc = findCompletionLocation(unit, "package ");
		CompletionList list = server.completion(JsonMessageHelper.getParams(createCompletionRequest(unit, loc[0], loc[1]))).join().getRight();
		assertNotNull(list);
		CompletionItem ci = list.getItems().stream()
				.filter( item->  (item.getLabel().matches("class") && item.getKind() == CompletionItemKind.Snippet))
				.findFirst().orElse(null);
		assertNull(ci);
		ci = list.getItems().stream()
				.filter( item->  (item.getLabel().matches("interface") && item.getKind() == CompletionItemKind.Snippet))
				.findFirst().orElse(null);
		assertNull(ci);
	}

	@Test
	public void testSnippet_context_method1() throws JavaModelException {
		ICompilationUnit unit = getWorkingCopy("src/org/sample/Test.java",
		//@formatter:off
				"package org.sample;\n"
			+	"public class Test {\n\n"
			+	"	void test() {\n\n"
			+	"	}\n"
			+	"}\n");
			//@formatter:off
		int[] loc = findCompletionLocation(unit, "{\n\n");
		CompletionList list = server.completion(JsonMessageHelper.getParams(createCompletionRequest(unit, loc[0], loc[1]))).join().getRight();
		assertNotNull(list);
		CompletionItem ci = list.getItems().stream()
				.filter( item->  (item.getLabel().matches("class") && item.getKind() == CompletionItemKind.Snippet))
				.findFirst().orElse(null);
		assertNotNull(ci);
		String text = ci.getInsertText();
		assertEquals("class ${1:InnerTest} {\n\n\t${0}\n}", text);
		ci = list.getItems().stream()
				.filter( item->  (item.getLabel().matches("interface") && item.getKind() == CompletionItemKind.Snippet))
				.findFirst().orElse(null);
		assertNull(ci);
	}

	@Test
	public void testSnippet_context_method2() throws JavaModelException {
		ICompilationUnit unit = getWorkingCopy("src/org/sample/Test.java",
		//@formatter:off
				"package org.sample;\n"
			+	"public class Test {\n\n"
			+	"	void test() {\n\n"
			+	"		if (c\n"
			+	"	}\n"
			+	"}\n");
			//@formatter:off
		int[] loc = findCompletionLocation(unit, "if (c");
		CompletionList list = server.completion(JsonMessageHelper.getParams(createCompletionRequest(unit, loc[0], loc[1]))).join().getRight();
		assertNotNull(list);
		CompletionItem ci = list.getItems().stream()
				.filter( item->  (item.getLabel().matches("class") && item.getKind() == CompletionItemKind.Snippet))
				.findFirst().orElse(null);
		assertNull(ci);
		ci = list.getItems().stream()
				.filter( item->  (item.getLabel().matches("interface") && item.getKind() == CompletionItemKind.Snippet))
				.findFirst().orElse(null);
		assertNull(ci);
	}

	@Test
	public void testSnippet_context_method3() throws JavaModelException {
		ICompilationUnit unit = getWorkingCopy("src/org/sample/Test.java",
		//@formatter:off
				"package org.sample;\n"
			+	"public class Test {\n\n"
			+	"	void test() {\n\n"
			+	"		int \n"
			+	"	}\n"
			+	"}\n");
			//@formatter:off
		int[] loc = findCompletionLocation(unit, "int ");
		CompletionList list = server.completion(JsonMessageHelper.getParams(createCompletionRequest(unit, loc[0], loc[1]))).join().getRight();
		assertNotNull(list);
		CompletionItem ci = list.getItems().stream()
				.filter( item->  (item.getLabel().matches("class") && item.getKind() == CompletionItemKind.Snippet))
				.findFirst().orElse(null);
		assertNull(ci);
		ci = list.getItems().stream()
				.filter( item->  (item.getLabel().matches("interface") && item.getKind() == CompletionItemKind.Snippet))
				.findFirst().orElse(null);
		assertNull(ci);
	}

	@Test
	public void testSnippet_context_static() throws JavaModelException {
		ICompilationUnit unit = getWorkingCopy("src/org/sample/Test.java",
		//@formatter:off
				"package org.sample;\n"
			+	"public class Test {\n\n"
			+	"	static {\n"
			+	"	}\n"
			+	"}\n");
			//@formatter:off
		int[] loc = findCompletionLocation(unit, "static {\n");
		CompletionList list = server.completion(JsonMessageHelper.getParams(createCompletionRequest(unit, loc[0], loc[1]))).join().getRight();
		assertNotNull(list);
		CompletionItem ci = list.getItems().stream()
				.filter( item->  (item.getLabel().matches("class") && item.getKind() == CompletionItemKind.Snippet))
				.findFirst().orElse(null);
		assertNotNull(ci);
		String text = ci.getInsertText();
		assertEquals("class ${1:InnerTest} {\n\n\t${0}\n}", text);
		ci = list.getItems().stream()
				.filter( item->  (item.getLabel().matches("interface") && item.getKind() == CompletionItemKind.Snippet))
				.findFirst().orElse(null);
		assertNull(ci);
	}

	@Test
	public void testStaticImports1() throws Exception {
		List<String> favorites = new ArrayList<>();
		favorites.add("test1.A.foo");
		PreferenceManager.getPrefs(null).setJavaCompletionFavoriteMembers(favorites);
		try {
			ICompilationUnit unit = getWorkingCopy("src/test1/B.java",
			//@formatter:off
				"package test1;\n" +
				"\n" +
				"public class B {\n" +
				"    public void bar() {\n" +
				"        fo\n" +
				"    }\n" +
				"}\n");
			//@formatter:on

			int[] loc = findCompletionLocation(unit, "fo");
			CompletionList list = server.completion(JsonMessageHelper.getParams(createCompletionRequest(unit, loc[0], loc[1]))).join().getRight();
			assertNotNull(list);
			assertTrue(list.getItems().size() > 0);
			assertTrue("no proposal for foo()", "foo() : void".equals(list.getItems().get(0).getLabel()));
		} finally {
			PreferenceManager.getPrefs(null).setJavaCompletionFavoriteMembers(Collections.emptyList());
		}
	}

	@Test
	public void testStaticImports2() throws Exception {
		PreferenceManager.getPrefs(null).setJavaCompletionFavoriteMembers(Collections.emptyList());
		ICompilationUnit unit = getWorkingCopy("src/test1/B.java",
		//@formatter:off
					"package test1;\n" +
					"\n" +
					"public class B {\n" +
					"    public void bar() {\n" +
					"        /* */fo\n" +
					"    }\n" +
					"    public void foo(int x) {\n" + // conflicting method, no static import possible
					"    }\n" +
					"}\n");
				//@formatter:on

		int[] loc = findCompletionLocation(unit, "/* */fo");
		CompletionList list = server.completion(JsonMessageHelper.getParams(createCompletionRequest(unit, loc[0], loc[1]))).join().getRight();
		assertNotNull(list);
		assertTrue(list.getItems().size() > 0);
		for (CompletionItem it : list.getItems()) {
			if ("foo() : void".equals(it.getLabel())) {
				fail("there is a proposal for foo()");
			}
		}
	}

	@Test
	public void testCompletion_linksInMarkdown() throws JavaModelException{
		ClientPreferences mockCapabilies = Mockito.mock(ClientPreferences.class);
		Mockito.when(preferenceManager.getClientPreferences()).thenReturn(mockCapabilies);
		Mockito.when(mockCapabilies.isSupportsCompletionDocumentationMarkdown()).thenReturn(true);
		Mockito.when(mockCapabilies.isClassFileContentSupported()).thenReturn(true);

		//@formatter:off
		ICompilationUnit unit = getWorkingCopy(
				"src/org/sample/Test.java",
				"package org.sample;\n"+
				"public class Test {\n"+
				"    public void foo(){\n"+
				"      this.zz \n"+
				"    }\n"+
				"    \n"+
				"	/**\n"+
				"	 * @see Baz\n"+
				"	 */\n"+
				"    public Baz zzzzzzz(){ \n"+
				"      return null;\n"+
				"    }\n" +
				"}\n");
		//@formatter:on
		int[] loc = findCompletionLocation(unit, "this.zz");

		CompletionList list = server.completion(JsonMessageHelper.getParams(createCompletionRequest(unit, loc[0], loc[1]))).join().getRight();
		assertNotNull(list);
		assertEquals(1, list.getItems().size());
		CompletionItem ci = list.getItems().get(0);
		assertEquals("zzzzzzz() : Baz", ci.getLabel());

		CompletionItem resolvedItem = server.resolveCompletionItem(ci).join();
		assertNotNull(resolvedItem.getDocumentation().getRight());
		String doc = resolvedItem.getDocumentation().getRight().getValue();
		assertTrue("Unexpected documentation content in " + doc, doc.contains("*  [Baz](file:/"));
	}

	@Test
	public void testCompletion_additionalTextEdit() throws Exception {
		//@formatter:off
		ICompilationUnit unit = getWorkingCopy(
				"src/java/Foo.java",
				"public class Foo {\n"+
						"	private Object o;\n"+
						"	void foo() {\n"+
						"		o.toStr\n"+
						"	}\n"+
				"}\n");
		//@formatter:on
		int[] loc = findCompletionLocation(unit, "o.toStr");
		CompletionList list = server.completion(JsonMessageHelper.getParams(createCompletionRequest(unit, loc[0], loc[1]))).join().getRight();
		assertNotNull(list);
		assertFalse("No proposals were found",list.getItems().isEmpty());
		CompletionItem ci = list.getItems().get(0);
		assertNull(ci.getAdditionalTextEdits());
		assertEquals("toString() : String", ci.getLabel());
		CompletionItem resolvedItem = server.resolveCompletionItem(ci).join();
		assertNull(resolvedItem.getAdditionalTextEdits());
	}

	private String createCompletionRequest(ICompilationUnit unit, int line, int kar) {
		return COMPLETION_TEMPLATE.replace("${file}", JDTUtils.toURI(unit))
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
		initPreferenceManager(true);
		ClientPreferences mockCapabilies = mock(ClientPreferences.class);
		// Mock the preference manager to use LSP v3 support.
		when(mockCapabilies.isCompletionSnippetsSupported()).thenReturn(isSnippetSupported);
		when(mockCapabilies.isSignatureHelpSupported()).thenReturn(isSignatureHelpSuported);
		when(preferenceManager.getClientPreferences()).thenReturn(mockCapabilies);
	}
}
