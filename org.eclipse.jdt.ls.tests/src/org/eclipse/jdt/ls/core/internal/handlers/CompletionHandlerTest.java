/*******************************************************************************
 * Copyright (c) 2016-2023 Red Hat Inc. and others.
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
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.core.runtime.Path;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.jdt.core.CompletionProposal;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.manipulation.CoreASTProvider;
import org.eclipse.jdt.ls.core.contentassist.CompletionRanking;
import org.eclipse.jdt.ls.core.internal.JDTUtils;
import org.eclipse.jdt.ls.core.internal.JSONUtility;
import org.eclipse.jdt.ls.core.internal.JavaClientConnection;
import org.eclipse.jdt.ls.core.internal.JavaLanguageServerPlugin;
import org.eclipse.jdt.ls.core.internal.JsonMessageHelper;
import org.eclipse.jdt.ls.core.internal.ProjectUtils;
import org.eclipse.jdt.ls.core.internal.ResourceUtils;
import org.eclipse.jdt.ls.core.internal.TextEditUtil;
import org.eclipse.jdt.ls.core.internal.WorkspaceHelper;
import org.eclipse.jdt.ls.core.internal.contentassist.JavadocCompletionProposal;
import org.eclipse.jdt.ls.core.internal.preferences.ClientPreferences;
import org.eclipse.jdt.ls.core.internal.preferences.PreferenceManager;
import org.eclipse.jdt.ls.core.internal.preferences.Preferences;
import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.IDocument;
import org.eclipse.lsp4j.CompletionContext;
import org.eclipse.lsp4j.CompletionItem;
import org.eclipse.lsp4j.CompletionItemDefaults;
import org.eclipse.lsp4j.CompletionItemKind;
import org.eclipse.lsp4j.CompletionItemTag;
import org.eclipse.lsp4j.CompletionList;
import org.eclipse.lsp4j.CompletionParams;
import org.eclipse.lsp4j.CompletionTriggerKind;
import org.eclipse.lsp4j.DidChangeTextDocumentParams;
import org.eclipse.lsp4j.InsertReplaceEdit;
import org.eclipse.lsp4j.InsertTextFormat;
import org.eclipse.lsp4j.InsertTextMode;
import org.eclipse.lsp4j.MarkupContent;
import org.eclipse.lsp4j.MarkupKind;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.TextDocumentContentChangeEvent;
import org.eclipse.lsp4j.TextEdit;
import org.eclipse.lsp4j.VersionedTextDocumentIdentifier;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.eclipse.text.edits.InsertEdit;
import org.eclipse.text.edits.MalformedTreeException;
import org.junit.After;
import org.junit.Before;
import org.junit.ComparisonFailure;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;

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
		preferences.setPostfixCompletionEnabled(false);
		preferences.setCompletionLazyResolveTextEditEnabled(false);
		Preferences.DISCOVERED_STATIC_IMPORTS.clear();
	}

	@After
	public void tearDown() throws Exception {
	}

	@Test
	public void testCompletion_javadoc() throws Exception {
		ClientPreferences mockCapabilies = Mockito.mock(ClientPreferences.class);
		Mockito.when(preferenceManager.getClientPreferences()).thenReturn(mockCapabilies);
		Mockito.when(mockCapabilies.isCompletionResolveDocumentSupport()).thenReturn(true);
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
			assertEquals("Test", resolved.getDocumentation().getLeft());
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
		Mockito.when(mockCapabilies.isCompletionResolveDocumentSupport()).thenReturn(true);
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

	@Test
	public void testCompletion_nojavadoc() throws Exception {
		IJavaProject javaProject = JavaCore.create(project);
		ClientPreferences mockCapabilies = Mockito.mock(ClientPreferences.class);
		Mockito.when(preferenceManager.getClientPreferences()).thenReturn(mockCapabilies);
		Mockito.lenient().when(mockCapabilies.isSupportsCompletionDocumentationMarkdown()).thenReturn(true);
		ICompilationUnit unit = (ICompilationUnit) javaProject.findElement(new Path("org/sample/Foo5.java"));
		unit.becomeWorkingCopy(null);
		try {
			int[] loc = findCompletionLocation(unit, "nam");
			CompletionParams position = JsonMessageHelper.getParams(createCompletionRequest(unit, loc[0], loc[1]));
			CompletionList list = server.completion(position).join().getRight();
			CompletionItem resolved = server.resolveCompletionItem(list.getItems().get(0)).join();
			assertNull(resolved.getDocumentation());
		} catch (Exception e) {
			fail("Unexpected exception " + e);
		} finally {
			unit.discardWorkingCopy();
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
		CompletionList list = requestCompletions(unit, "Objec");
		assertNotNull(list);
		assertFalse("No proposals were found",list.getItems().isEmpty());

		List<CompletionItem> items = list.getItems();
		for ( CompletionItem item : items) {
			assertTrue(isNotBlank(item.getLabel()));
			assertNotNull(item.getKind() );
			assertTrue(isNotBlank(item.getSortText()));
			//text edits are set during calls to "completion"
			assertNotNull(item.getTextEdit());
			assertTrue(isNotBlank(item.getInsertText()));
			assertNotNull(item.getFilterText());
			assertFalse(item.getFilterText().contains(" "));
			assertTrue(item.getLabel().startsWith(item.getInsertText()));
			assertTrue(item.getFilterText().contains("Objec"));
			//Check contains data used for completionItem resolution
			@SuppressWarnings("unchecked")
			Map<String,String> data = (Map<String, String>) item.getData();
			assertNotNull(data);
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
		CompletionList list = requestCompletions(unit, "Objec");
		assertNotNull(list);
		assertFalse("No proposals were found",list.getItems().isEmpty());

		Map<String,String> data = (Map<String, String>) list.getItems().get(0).getData();
		long requestId = Long.parseLong(data.get(CompletionResolveHandler.DATA_FIELD_REQUEST_ID));
		CompletionResponse completionResponse = CompletionResponses.get(requestId);
		assertNotNull(completionResponse);
		String uri = completionResponse.getCommonData(CompletionResolveHandler.DATA_FIELD_URI);
		assertNotNull(uri);
		assertTrue("unexpected URI prefix: " + uri, uri.matches("file://.*/src/java/Foo\\.java"));
	}

	@Test
	public void testCompletion_dataFieldExecutionTime() throws Exception {
		ICompilationUnit unit = getWorkingCopy(
			"src/java/Foo.java",
			"public class Foo {\n"+
				"	void foo() {\n"+
				"		Objec\n"+
				"	}\n"+
				"}\n");
		CompletionList list = requestCompletions(unit, "Objec");
		assertNotNull(list);
		assertFalse("No proposals were found",list.getItems().isEmpty());

		Map<String,String> data = (Map<String, String>) list.getItems().get(0).getData();
		long requestId = Long.parseLong(data.get(CompletionResolveHandler.DATA_FIELD_REQUEST_ID));
		CompletionResponse completionResponse = CompletionResponses.get(requestId);
		assertNotNull(completionResponse);
		String time = completionResponse.getCommonData(CompletionRanking.COMPLETION_EXECUTION_TIME);
		assertNotNull(time);
	}


	@Test
	public void testCompletion_constructor() throws Exception{
		when(preferenceManager.getClientPreferences().isCompletionItemLabelDetailsSupport()).thenReturn(true);
		ICompilationUnit unit = getWorkingCopy(
				"src/java/Foo.java",
				"public class Foo {\n"+
						"	void foo() {\n"+
						"		Object o = new O\n"+
						"	}\n"+
				"}\n");
		CompletionList list = requestCompletions(unit, "new O");
		assertNotNull(list);
		assertFalse("No proposals were found",list.getItems().isEmpty());

		List<CompletionItem> items = new ArrayList<>(list.getItems());
		Comparator<CompletionItem> comparator = (CompletionItem a, CompletionItem b) -> a.getSortText().compareTo(b.getSortText());
		Collections.sort(items, comparator);
		CompletionItem ctor = items.get(0);
		assertEquals("Object", ctor.getLabel());
		// createMethodProposalLabel
		assertEquals("()", ctor.getLabelDetails().getDetail());
		assertNull(ctor.getLabelDetails().getDescription());
		assertEquals("java.lang.Object.Object()", ctor.getDetail());
		assertEquals("Object", ctor.getInsertText());

		CompletionItem resolvedItem = server.resolveCompletionItem(ctor).join();
		assertNotNull(resolvedItem);
		TextEdit te = resolvedItem.getTextEdit().getLeft();
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
		when(preferenceManager.getClientPreferences().isCompletionItemLabelDetailsSupport()).thenReturn(true);
		ICompilationUnit unit = getWorkingCopy(
				"src/java/Foo.java",
				"import java.sq \n" +
						"public class Foo {\n"+
						"	void foo() {\n"+
						"	}\n"+
				"}\n");

		CompletionList list = requestCompletions(unit, "java.sq");

		assertNotNull(list);
		assertEquals(1, list.getItems().size());
		CompletionItem item = list.getItems().get(0);
		// Check completion item
		assertNull(item.getInsertText());
		assertEquals("java.sql",item.getLabel());
		// createPackageProposalLabel
		assertNull(item.getLabelDetails().getDetail());
		assertEquals("(package)",item.getLabelDetails().getDescription());
		assertEquals("(package) java.sql", item.getDetail());
		assertEquals(CompletionItemKind.Module, item.getKind() );
		assertEquals("999999215", item.getSortText());
		assertNotNull(item.getTextEdit().getLeft());
		TextEdit te = item.getTextEdit().getLeft();
		assertNotNull(te);
		assertEquals("java.sql.${0:*};", te.getNewText());
		assertNotNull(te.getRange());
		Range range = te.getRange();
		assertEquals(0, range.getStart().getLine());
		assertEquals(7, range.getStart().getCharacter());
		assertEquals(0, range.getEnd().getLine());
		//Not checking the range end character
	}

	@Test
	public void testCompletion_javadocComment() throws JavaModelException {
		mockClientPreferences(true, true, true);
		when(preferenceManager.getClientPreferences().getCompletionItemInsertTextModeDefault()).thenReturn(InsertTextMode.AdjustIndentation);
		ICompilationUnit unit = getWorkingCopy(
		//@formatter:off
		"src/java/Foo.java",
		"public class Foo {\n"+
		"	/** */ \n"+
		"	void foo(int i, String s) {\n"+
		"	}\n"+
		"}\n");
		//@formatter:on
		CompletionList list = requestCompletions(unit, "/**");
		assertNotNull(list);
		assertEquals(1, list.getItems().size());
		CompletionItem item = list.getItems().get(0);
		assertNull(item.getInsertText());
		assertEquals(JavadocCompletionProposal.JAVA_DOC_COMMENT, item.getLabel());
		assertEquals(CompletionItemKind.Snippet, item.getKind());
		assertEquals("999999999", item.getSortText());
		assertEquals(item.getInsertTextFormat(), InsertTextFormat.Snippet);
		assertNull(item.getInsertTextMode());
		assertNotNull(item.getTextEdit());
		assertEquals("\n * ${0}\n * @param i\n * @param s\n", item.getTextEdit().getLeft().getNewText());
		Range range = item.getTextEdit().getLeft().getRange();
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
		Mockito.lenient().when(mockCapabilies.isCompletionItemInsertTextModeSupport(InsertTextMode.AdjustIndentation)).thenReturn(true);
		Mockito.when(mockCapabilies.getCompletionItemInsertTextModeDefault()).thenReturn(InsertTextMode.AsIs);

		ICompilationUnit unit = getWorkingCopy(
		//@formatter:off
		"src/java/Foo.java",
		"public class Foo {\n"+
		"	/** */ \n"+
		"	void foo(int i, String s) {\n"+
		"	}\n"+
		"}\n");
		//@formatter:on
		CompletionList list = requestCompletions(unit, "/**");
		assertNotNull(list);
		assertEquals(1, list.getItems().size());
		CompletionItem item = list.getItems().get(0);
		assertNull(item.getInsertText());
		assertEquals(JavadocCompletionProposal.JAVA_DOC_COMMENT, item.getLabel());
		assertEquals(CompletionItemKind.Snippet, item.getKind());
		assertEquals("999999999", item.getSortText());
		assertEquals(item.getInsertTextFormat(), InsertTextFormat.PlainText);
		assertEquals(item.getInsertTextMode(), InsertTextMode.AdjustIndentation);
		assertNotNull(item.getTextEdit());
		assertEquals("\n * @param i\n * @param s\n", item.getTextEdit().getLeft().getNewText());
		Range range = item.getTextEdit().getLeft().getRange();
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
		CompletionList list = requestCompletions(unit, "/**");
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
		CompletionList list = requestCompletions(unit, "/*");
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
		CompletionList list = requestCompletions(unit, "/**");
		assertNotNull(list);
		assertEquals(0, list.getItems().size());
	}

	@Test
	public void testCompletion_javadocCommentRecord() throws Exception {
		importProjects("eclipse/java16");
		IProject proj = WorkspaceHelper.getProject("java16");
		IJavaProject javaProject = JavaCore.create(proj);
		ICompilationUnit unit = null;
		try {
			unit = (ICompilationUnit) javaProject.findElement(new Path("foo/bar/Foo.java"));
			unit.becomeWorkingCopy(null);
			String source =
			//@formatter:off
				"package foo.bar;\n"+
				"/** */ \n"+
				"public record Foo(String name, int age) {\n"+
				"}\n";
			//@formatter:on
			changeDocument(unit, source, 1);
			Job.getJobManager().join(DocumentLifeCycleHandler.DOCUMENT_LIFE_CYCLE_JOBS, new NullProgressMonitor());
			CompletionList list = requestCompletions(unit, "/**");
			assertNotNull(list);
			assertEquals(1, list.getItems().size());
			CompletionItem item = list.getItems().get(0);
			assertNull(item.getInsertText());
			assertEquals(JavadocCompletionProposal.JAVA_DOC_COMMENT, item.getLabel());
			assertEquals(CompletionItemKind.Snippet, item.getKind());
			assertEquals("999999999", item.getSortText());
			assertEquals(item.getInsertTextFormat(), InsertTextFormat.Snippet);
			assertNotNull(item.getTextEdit());
			assertEquals("\n * ${0}\n * Foo\n * @param name\n * @param age\n", item.getTextEdit().getLeft().getNewText());
			Range range = item.getTextEdit().getLeft().getRange();
			assertEquals(1, range.getStart().getLine());
			assertEquals(3, range.getStart().getCharacter());
			assertEquals(1, range.getEnd().getLine());
			assertEquals(" * Foo\n * @param name\n * @param age\n", item.getDocumentation().getLeft());
		} finally {
			unit.discardWorkingCopy();
			proj.delete(true, monitor);
		}
	}

	@Test
	public void testCompletion_javadocCommentRecordNoSnippet() throws Exception {
		ClientPreferences mockCapabilies = Mockito.mock(ClientPreferences.class);
		Mockito.when(preferenceManager.getClientPreferences()).thenReturn(mockCapabilies);
		Mockito.when(mockCapabilies.isCompletionSnippetsSupported()).thenReturn(false);
		importProjects("eclipse/java16");
		IProject proj = WorkspaceHelper.getProject("java16");
		IJavaProject javaProject = JavaCore.create(proj);
		ICompilationUnit unit = null;
		try {
			unit = (ICompilationUnit) javaProject.findElement(new Path("foo/bar/Foo.java"));
			unit.becomeWorkingCopy(null);
			String source =
			//@formatter:off
				"package foo.bar;\n"+
				"/** */ \n"+
				"public record Foo(String name, int age) {\n"+
				"}\n";
			//@formatter:on
			changeDocument(unit, source, 1);
			Job.getJobManager().join(DocumentLifeCycleHandler.DOCUMENT_LIFE_CYCLE_JOBS, new NullProgressMonitor());
			CompletionList list = requestCompletions(unit, "/**");
			assertNotNull(list);
			assertEquals(1, list.getItems().size());
			CompletionItem item = list.getItems().get(0);
			assertNull(item.getInsertText());
			assertEquals(JavadocCompletionProposal.JAVA_DOC_COMMENT, item.getLabel());
			assertEquals(CompletionItemKind.Snippet, item.getKind());
			assertEquals("999999999", item.getSortText());
			assertEquals(item.getInsertTextFormat(), InsertTextFormat.PlainText);
			assertNotNull(item.getTextEdit());
			assertEquals("\n * Foo\n * @param name\n * @param age\n", item.getTextEdit().getLeft().getNewText());
			Range range = item.getTextEdit().getLeft().getRange();
			assertEquals(1, range.getStart().getLine());
			assertEquals(3, range.getStart().getCharacter());
			assertEquals(1, range.getEnd().getLine());
			assertEquals(" * Foo\n * @param name\n * @param age\n", item.getDocumentation().getLeft());
		} finally {
			unit.discardWorkingCopy();
			proj.delete(true, monitor);
		}
	}

	@Test
	public void testCompletion_import_static() throws JavaModelException{
		when(preferenceManager.getClientPreferences().isCompletionItemLabelDetailsSupport()).thenReturn(true);

		ICompilationUnit unit = getWorkingCopy(
				"src/java/Foo.java",
				"import static java.util.concurrent.TimeUnit. \n" +
						"public class Foo {\n"+
						"	void foo() {\n"+
						"	}\n"+
				"}\n");

		CompletionList list = requestCompletions(unit, "java.util.concurrent.TimeUnit.");

		assertNotNull(list);
		assertEquals(9, list.getItems().size());

		//// .DAYS - enum value
		CompletionItem daysFieldItem = list.getItems().get(0);
		// Check completion item
		assertEquals("DAYS", daysFieldItem.getInsertText());
		// createLabelWithTypeAndDeclaration
		assertEquals("DAYS", daysFieldItem.getLabel());
		assertEquals("TimeUnit", daysFieldItem.getLabelDetails().getDescription());
		assertNull(daysFieldItem.getLabelDetails().getDetail());

		//
		assertEquals(CompletionItemKind.EnumMember, daysFieldItem.getKind());
		assertEquals("999999210", daysFieldItem.getSortText());

		TextEdit teDays = daysFieldItem.getTextEdit().getLeft();
		assertNotNull(teDays);
		assertEquals("DAYS;", teDays.getNewText());
		assertNotNull(teDays.getRange());
		Range secondsRange = teDays.getRange();
		assertEquals(0, secondsRange.getStart().getLine());
		assertEquals(44, secondsRange.getStart().getCharacter());
		assertEquals(0, secondsRange.getEnd().getLine());

		//Check other fields are listed alphabetically
		assertEquals("HOURS;", list.getItems().get(1).getTextEdit().getLeft().getNewText());
		assertEquals("MICROSECONDS;", list.getItems().get(2).getTextEdit().getLeft().getNewText());
		assertEquals("MILLISECONDS;", list.getItems().get(3).getTextEdit().getLeft().getNewText());
		assertEquals("MINUTES;", list.getItems().get(4).getTextEdit().getLeft().getNewText());
		assertEquals("NANOSECONDS;", list.getItems().get(5).getTextEdit().getLeft().getNewText());
		assertEquals("SECONDS;", list.getItems().get(6).getTextEdit().getLeft().getNewText());

		//// .values() - static method
		CompletionItem valuesMethodItem = list.getItems().get(7);
		// Check completion item
		assertEquals("valueOf", valuesMethodItem.getInsertText());
		assertEquals("valueOf", valuesMethodItem.getLabel());
		assertEquals("(String)", valuesMethodItem.getLabelDetails().getDetail());
		assertEquals("TimeUnit", valuesMethodItem.getLabelDetails().getDescription());
		assertEquals(CompletionItemKind.Method, valuesMethodItem.getKind());
		assertEquals("999999211", valuesMethodItem.getSortText());
		TextEdit teValues = valuesMethodItem.getTextEdit().getLeft();
		assertNotNull(teValues);
		assertEquals("valueOf;", teValues.getNewText());
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

		CompletionList list = requestCompletions(unit, "map.pu");
		assertNotNull(list);
		CompletionItem ci = list.getItems().stream()
				.filter( item->  item.getLabel().matches("put\\(String \\w+, String \\w+\\) : String"))
				.findFirst().orElse(null);
		assertNotNull(ci);

		assertEquals("put", ci.getInsertText());
		assertEquals(CompletionItemKind.Method, ci.getKind());
		assertEquals("999999019", ci.getSortText());

		assertNotNull(ci.getTextEdit().getLeft());
		assertTextEdit(5, 4, 6, "put", ci.getTextEdit().getLeft());
		assertNotNull(ci.getAdditionalTextEdits());
		List<TextEdit> edits = ci.getAdditionalTextEdits();
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

		CompletionList list = requestCompletions(unit, "map.pu");
		assertNotNull(list);
		CompletionItem ci = list.getItems().stream()
				.filter( item->  item.getLabel().matches("put\\(String \\w+, String \\w+\\) : String"))
				.findFirst().orElse(null);
		assertNotNull(ci);

		assertEquals("put", ci.getInsertText());
		assertTrue(ci.getDetail().matches("java.util.HashMap.put\\(String \\w+, String \\w+\\) : String"));
		assertEquals(CompletionItemKind.Method, ci.getKind());
		assertEquals("999999019", ci.getSortText());
		assertNotNull(ci.getTextEdit().getLeft());
		try {
			assertTextEdit(5, 4, 6, "put(${1:key}, ${2:value})", ci.getTextEdit().getLeft());
		} catch (ComparisonFailure e) {
			//In case the JDK has no sources
			assertTextEdit(5, 4, 6, "put(${1:arg0}, ${2:arg1})", ci.getTextEdit().getLeft());
		}
		assertNotNull(ci.getAdditionalTextEdits());
		List<TextEdit> edits = ci.getAdditionalTextEdits();
		assertEquals(2, edits.size());
	}

	@Test
	public void testCompletion_method_insertParameterNames() throws JavaModelException {
		testCompletion_method_guessMethodArguments(CompletionGuessMethodArgumentsMode.INSERT_PARAMETER_NAMES, "test(${1:name}, ${2:i});");
	}

	@Test
	public void testCompletion_method_insertBestGuessedArguments() throws JavaModelException {
		testCompletion_method_guessMethodArguments(CompletionGuessMethodArgumentsMode.INSERT_BEST_GUESSED_ARGUMENTS, "test(${1:str}, ${2:x});");
	}

	private void testCompletion_method_guessMethodArguments(CompletionGuessMethodArgumentsMode guessMethodArguments, String expected) throws JavaModelException {
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
		CompletionGuessMethodArgumentsMode oldGuessMethodArguments = JavaLanguageServerPlugin.getPreferencesManager().getPreferences().getGuessMethodArgumentsMode();
		try {
			JavaLanguageServerPlugin.getPreferencesManager().getPreferences().setGuessMethodArgumentsMode(guessMethodArguments);
			CompletionList list = requestCompletions(unit, "tes");
			assertNotNull(list);
			CompletionItem ci = list.getItems().stream().filter(item -> item.getLabel().equals("test(String name, int i) : void")).findFirst().orElse(null);
			assertNotNull(ci);

			assertEquals("test", ci.getInsertText());
			assertEquals(CompletionItemKind.Method, ci.getKind());
			assertEquals("999999163", ci.getSortText());
			assertNotNull(ci.getTextEdit().getLeft());
			assertTextEdit(5, 2, 5, expected, ci.getTextEdit().getLeft());
		} finally {
			JavaLanguageServerPlugin.getPreferencesManager().getPreferences().setGuessMethodArgumentsMode(oldGuessMethodArguments);
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
		CompletionGuessMethodArgumentsMode oldGuessMethodArguments = JavaLanguageServerPlugin.getPreferencesManager().getPreferences().getGuessMethodArgumentsMode();
		try {
			JavaLanguageServerPlugin.getPreferencesManager().getPreferences().setGuessMethodArgumentsMode(CompletionGuessMethodArgumentsMode.INSERT_BEST_GUESSED_ARGUMENTS);
			CompletionList list = requestCompletions(unit, "tes");
			assertNotNull(list);
			CompletionItem ci = list.getItems().stream().filter(item -> item.getLabel().equals("test(String name, int i) : void")).findFirst().orElse(null);
			assertNotNull(ci);

			assertEquals("test", ci.getInsertText());
			assertEquals(CompletionItemKind.Method, ci.getKind());
			assertEquals("999999163", ci.getSortText());
			assertNotNull(ci.getTextEdit().getLeft());
			assertTextEdit(4, 2, 5, "test(${1:str}, ${2:0});", ci.getTextEdit().getLeft());
		} finally {
			JavaLanguageServerPlugin.getPreferencesManager().getPreferences().setGuessMethodArgumentsMode(oldGuessMethodArguments);
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
		CompletionGuessMethodArgumentsMode oldGuessMethodArguments = JavaLanguageServerPlugin.getPreferencesManager().getPreferences().getGuessMethodArgumentsMode();
		try {
			JavaLanguageServerPlugin.getPreferencesManager().getPreferences().setGuessMethodArgumentsMode(CompletionGuessMethodArgumentsMode.INSERT_BEST_GUESSED_ARGUMENTS);
			CompletionList list = requestCompletions(unit, "tes");
			assertNotNull(list);
			CompletionItem ci = list.getItems().stream().filter(item -> item.getLabel().equals("test(int i, int j) : void")).findFirst().orElse(null);
			assertNotNull(ci);

			assertEquals("test", ci.getInsertText());
			assertEquals(CompletionItemKind.Method, ci.getKind());
			assertEquals("999999163", ci.getSortText());
			assertNotNull(ci.getTextEdit().getLeft());
			assertTextEdit(5, 2, 5, "test(${1:one}, ${2:two});", ci.getTextEdit().getLeft());
		} finally {
			JavaLanguageServerPlugin.getPreferencesManager().getPreferences().setGuessMethodArgumentsMode(oldGuessMethodArguments);
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
		CompletionGuessMethodArgumentsMode oldGuessMethodArguments = JavaLanguageServerPlugin.getPreferencesManager().getPreferences().getGuessMethodArgumentsMode();
		try {
			JavaLanguageServerPlugin.getPreferencesManager().getPreferences().setGuessMethodArgumentsMode(CompletionGuessMethodArgumentsMode.INSERT_BEST_GUESSED_ARGUMENTS);
			CompletionList list = requestCompletions(unit, "new A");
			assertNotNull(list);
			CompletionItem ci = list.getItems().stream().filter(item -> item.getLabel().equals("A(String name)")).findFirst().orElse(null);
			assertNotNull(ci);

			assertEquals("A", ci.getInsertText());
			assertEquals(CompletionItemKind.Constructor, ci.getKind());
			assertEquals("999999051", ci.getSortText());
			assertNotNull(ci.getTextEdit().getLeft());
			assertTextEdit(3, 6, 7, "A(${1:str})", ci.getTextEdit().getLeft());
		} finally {
			JavaLanguageServerPlugin.getPreferencesManager().getPreferences().setGuessMethodArgumentsMode(oldGuessMethodArguments);
		}
	}

	@Test
	public void testCompletion_method_turnOffGuessMethodArguments() throws JavaModelException {
		ICompilationUnit unit = getWorkingCopy(
				"src/java/Foo.java",
				"""
				public class Foo {
					static void test(int i, int j) {}
					public static void main(String[] args) {
						int one=1;
						int two=2;
						tes
					}
				}
				"""
		);
		CompletionGuessMethodArgumentsMode oldGuessMethodArguments = JavaLanguageServerPlugin.getPreferencesManager().getPreferences().getGuessMethodArgumentsMode();
		try {
			JavaLanguageServerPlugin.getPreferencesManager().getPreferences().setGuessMethodArgumentsMode(CompletionGuessMethodArgumentsMode.OFF);
			CompletionList list = requestCompletions(unit, "tes");
			assertNotNull(list);
			CompletionItem ci = list.getItems().stream().filter(item -> item.getLabel().equals("test(int i, int j) : void")).findFirst().orElse(null);
			assertNotNull(ci);
			assertTextEdit(5, 2, 5, "test(${0});", ci.getTextEdit().getLeft());
		} finally {
			JavaLanguageServerPlugin.getPreferencesManager().getPreferences().setGuessMethodArgumentsMode(oldGuessMethodArguments);
		}
	}

	@Test
	public void testCompletion_constructor_turnOffGuessMethodArguments() throws JavaModelException {
		ICompilationUnit unit = getWorkingCopy(
				"src/java/Foo.java",
				"""
				public class Foo {
					public static void main(String[] args) {
						String s = new String
					}
				}
				"""
		);
		CompletionGuessMethodArgumentsMode oldGuessMethodArguments = JavaLanguageServerPlugin.getPreferencesManager().getPreferences().getGuessMethodArgumentsMode();
		try {
			JavaLanguageServerPlugin.getPreferencesManager().getPreferences().setGuessMethodArgumentsMode(CompletionGuessMethodArgumentsMode.OFF);
			CompletionList list = requestCompletions(unit, "new String");
			assertNotNull(list);
			CompletionItem ci = list.getItems().stream().filter(item -> item.getLabel().equals("String - java.lang")).findFirst().orElse(null);
			assertNotNull(ci);
			assertEquals("String(${0})", ci.getTextEdit().getLeft().getNewText());
		} finally {
			JavaLanguageServerPlugin.getPreferencesManager().getPreferences().setGuessMethodArgumentsMode(oldGuessMethodArguments);
		}
	}

	@Test
	public void testCompletion_constructor_innerClass() throws JavaModelException {
		ICompilationUnit unit = getWorkingCopy(
				"src/java/Foo.java",
				"""
				import java.util.List;
				import java.util.ArrayList;
				public class Foo {
					public void test() {
						List<String> a = new MyC
					}
					public class MyClass {
						static class MyList<E> extends ArrayList<E> { }
					}
				}
				"""
		);
		CompletionList list = requestCompletions(unit, "new MyC");
		assertNotNull(list);
		CompletionItem ci = list.getItems().stream().filter(item -> item.getLabel().equals("MyClass - java.Foo")).findFirst().orElse(null);
		assertNotNull(ci);
		assertEquals("java.Foo.MyClass", ci.getDetail());
	}

	private ClientPreferences mockClientPreferences(boolean supportCompletionSnippets, boolean supportSignatureHelp, boolean isCompletionListItemDefaultsSupport) {
		ClientPreferences mockCapabilies = Mockito.mock(ClientPreferences.class);
		Mockito.when(preferenceManager.getClientPreferences()).thenReturn(mockCapabilies);
		Mockito.when(mockCapabilies.isCompletionSnippetsSupported()).thenReturn(supportCompletionSnippets);
		Mockito.lenient().when(mockCapabilies.isSignatureHelpSupported()).thenReturn(supportSignatureHelp);
		when(preferenceManager.getClientPreferences().isCompletionListItemDefaultsSupport()).thenReturn(isCompletionListItemDefaultsSupport);
		when(preferenceManager.getClientPreferences().isCompletionListItemDefaultsPropertySupport("editRange")).thenReturn(isCompletionListItemDefaultsSupport);
		when(preferenceManager.getClientPreferences().isCompletionListItemDefaultsPropertySupport("insertTextFormat")).thenReturn(isCompletionListItemDefaultsSupport);
		when(preferenceManager.getClientPreferences().isCompletionItemInsertTextModeSupport(InsertTextMode.AdjustIndentation)).thenReturn(true);
		when(preferenceManager.getClientPreferences().isCompletionListItemDefaultsPropertySupport("insertTextMode")).thenReturn(isCompletionListItemDefaultsSupport);
		when(preferenceManager.getClientPreferences().isCompletionItemLabelDetailsSupport()).thenReturn(false);
		return mockCapabilies;
	}

	@Test
	public void testCompletion_field() throws JavaModelException{
		when(preferenceManager.getClientPreferences().getCompletionItemInsertTextModeDefault()).thenReturn(InsertTextMode.AsIs);
		ICompilationUnit unit = getWorkingCopy(
				"src/java/Foo.java",
				"import java.sq \n" +
						"public class Foo {\n"+
						"private String myTestString;\n"+
						"	void foo() {\n"+
						"   this.myTestS\n"+
						"	}\n"+
				"}\n");

		CompletionList list = requestCompletions(unit, "this.myTestS");

		assertNotNull(list);
		assertEquals(1, list.getItems().size());
		CompletionItem item = list.getItems().get(0);
		assertEquals(CompletionItemKind.Field, item.getKind());
		assertEquals("myTestString", item.getInsertText());
		assertEquals("Foo.myTestString : String", item.getDetail());
		assertNotNull(item.getTextEdit());
		assertTextEdit(4, 8, 15, "myTestString", item.getTextEdit().getLeft());
		assertEquals(item.getInsertTextMode(), InsertTextMode.AdjustIndentation);
		//Not checking the range end character
	}

	@Test
	public void testCompletion_field_itemDefaults_enabled() throws JavaModelException{
		mockClientPreferences(true, true, true);
		when(preferenceManager.getClientPreferences().getCompletionItemInsertTextModeDefault()).thenReturn(InsertTextMode.AsIs);
		//@formatter:off
		ICompilationUnit unit = getWorkingCopy(
				"src/java/Foo.java",
				"import java.sq \n" +
						"public class Foo {\n"+
						"private String myTestString;\n"+
						"	void foo() {\n"+
						"   this.myTestS\n"+
						"	}\n"+
				"}\n");
		//@formatter:on

		CompletionList list = requestCompletions(unit, "this.myTestS");

		assertNotNull(list);
		assertNotNull(list.getItemDefaults().getEditRange());
		assertEquals(InsertTextFormat.Snippet, list.getItemDefaults().getInsertTextFormat());
		assertEquals(InsertTextMode.AdjustIndentation, list.getItemDefaults().getInsertTextMode());
		assertNotNull(list.getItemDefaults().getData());
		Map<String, Object> data = (Map)list.getItemDefaults().getData();
		Set<Integer> completionKinds = (Set)data.get("completionKinds");
		assertNotNull(completionKinds);
		assertTrue(completionKinds.contains(CompletionProposal.FIELD_REF));

		assertEquals(1, list.getItems().size());
		CompletionItem item = list.getItems().get(0);
		assertEquals(CompletionItemKind.Field, item.getKind());
		assertEquals("myTestString", item.getInsertText());
		assertEquals("Foo.myTestString : String", item.getDetail());
		//check that the fields covered by itemDefaults are set to null
		assertNull(item.getTextEdit());
		assertNull(item.getInsertTextFormat());
		assertNull(item.getInsertTextMode());
		//Not checking the range end character
	}

	@Test
	public void testCompletion_field_itemDefaults_enabled_AdjustIndentation() throws JavaModelException{
		mockClientPreferences(true, true, true);
		when(preferenceManager.getClientPreferences().getCompletionItemInsertTextModeDefault()).thenReturn(InsertTextMode.AdjustIndentation);
		//@formatter:off
		ICompilationUnit unit = getWorkingCopy(
				"src/java/Foo.java",
				"import java.sq \n" +
						"public class Foo {\n"+
						"private String myTestString;\n"+
						"	void foo() {\n"+
						"   this.myTestS\n"+
						"	}\n"+
				"}\n");
		//@formatter:on

		CompletionList list = requestCompletions(unit, "this.myTestS");

		assertNotNull(list);
		assertNull(list.getItemDefaults().getInsertTextMode());

		assertEquals(1, list.getItems().size());
		CompletionItem item = list.getItems().get(0);
		assertNull(item.getInsertTextMode());
	}

	@Test
	public void testCompletion_import_type() throws JavaModelException{
		when(preferenceManager.getClientPreferences().getCompletionItemInsertTextModeDefault()).thenReturn(InsertTextMode.AdjustIndentation);
		ICompilationUnit unit = getWorkingCopy(
				"src/java/Foo.java",
				"import java.sq \n" +
						"public class Foo {\n"+
						"	void foo() {\n"+
						"   java.util.Ma\n"+
						"	}\n"+
				"}\n");

		CompletionList list = requestCompletions(unit, "java.util.Ma");

		assertNotNull(list);
		assertFalse(list.getItems().isEmpty());
		CompletionItem item = list.getItems().get(0);
		assertEquals(CompletionItemKind.Interface, item.getKind());
		assertEquals("Map", item.getInsertText());
		assertNotNull(item.getTextEdit());
		assertTextEdit(3, 3, 15, "java.util.Map", item.getTextEdit().getLeft());
		assertTrue(item.getFilterText().startsWith("java.util.Ma"));
		assertNull(item.getInsertTextMode());
		//Not checking the range end character
	}

	@Test
	public void testCompletion_noPackage() throws Exception{
		ICompilationUnit unit = getWorkingCopy(
				"src/NoPackage.java",
				"public class NoPackage {\n"
						+ "    NoP"
						+"}\n");
		CompletionList list = requestCompletions(unit, "    NoP");
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

		CompletionList list = requestCompletions(unit, "package o");

		assertNotNull(list);
		List<CompletionItem> items = new ArrayList<>(list.getItems());
		assertFalse(items.isEmpty());
		items.sort((i1, i2) -> i1.getSortText().compareTo(i2.getSortText()));

		CompletionItem item = items.get(0);
		// current package should appear 1st
		assertEquals("org.sample",item.getLabel());

		CompletionItem resolvedItem = server.resolveCompletionItem(item).join();
		assertNotNull(resolvedItem);
		TextEdit te = item.getTextEdit().getLeft();
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
	public void testSkipAdditionalEditForImport() throws JavaModelException, MalformedTreeException, BadLocationException {
		//@formatter:off
		ICompilationUnit unit = getWorkingCopy(
			"src/org/sample/Test.java",
			"package org.sample;\n" +
			"import " +
			"public class Test {\n" +
			"}"
		);
		//@formatter:on

		// mock the user's input behavior
		int startOffset = JsonRpcHelpers.toOffset(unit.getBuffer(), 1, 7);
		InsertEdit edit = new InsertEdit(startOffset, "j");
		IDocument document = JsonRpcHelpers.toDocument(unit.getBuffer());
		edit.apply(document, org.eclipse.text.edits.TextEdit.NONE);
		CompletionList list = requestCompletions(unit, "import j");

		assertNotNull(list);

		List<CompletionItem> items = new ArrayList<>(list.getItems());
		CompletionItem item = items.get(0);
		CompletionItem resolved = server.resolveCompletionItem(item).join();
		assertNull(resolved.getAdditionalTextEdits());
	}

	@Test
	public void testSkipAdditionalEditForImport2() throws JavaModelException, MalformedTreeException, BadLocationException {
		//@formatter:off
		ICompilationUnit unit = getWorkingCopy(
			"src/org/sample/Test.java",
			"package org.sample;\n" +
			"import " +
			"public class Test {\n" +
			"}"
		);
		//@formatter:on

		// mock the user's input behavior
		int startOffset = JsonRpcHelpers.toOffset(unit.getBuffer(), 1, 7);
		InsertEdit edit = new InsertEdit(startOffset, "java.util.Arr");
		IDocument document = JsonRpcHelpers.toDocument(unit.getBuffer());
		edit.apply(document, org.eclipse.text.edits.TextEdit.NONE);
		CompletionList list = requestCompletions(unit, "java.util.Arr");

		assertNotNull(list);

		List<CompletionItem> items = new ArrayList<>(list.getItems());
		CompletionItem item = items.get(0);
		CompletionItem resolved = server.resolveCompletionItem(item).join();
		assertNull(resolved.getAdditionalTextEdits());
	}

	@Test
	public void testSnippet_NonLazyResolve() throws JavaModelException {
		try {
			preferences.setCompletionLazyResolveTextEditEnabled(false);
			//@formatter:off
			ICompilationUnit unit = getWorkingCopy(
				"src/org/sample/Test.java",
				"package org.sample;\n" +
				"public class Test {\n" +
				"	public void testMethod() {\n" +
				"		sysout" +
				"	}\n" +
				"}"
			);
			//@formatter:on
			CompletionList list = requestCompletions(unit, "sysout");

			assertNotNull(list);

			List<CompletionItem> items = new ArrayList<>(list.getItems());
			CompletionItem item = items.get(0);
			assertEquals("sysout", item.getLabel());
			String newText = item.getTextEdit().getLeft().getNewText();
			assertEquals("System.out.println(${0});", newText);
		} finally {
			preferences.setCompletionLazyResolveTextEditEnabled(true);
		}
	}

	// https://github.com/eclipse/eclipse.jdt.ls/issues/1800
	@Test
	public void testSnippet_ifelse2() throws JavaModelException {
		//@formatter:off
		ICompilationUnit unit = getWorkingCopy(
			"src/org/sample/Test.java",
			"package org.sample;\n" +
			"public class Test {\n" +
			"	private void test(String s, int i) {\n"
			+ "  if (i > 2) {\n"
			+ "  } else {\n"
			+ "    s.\n"
			+ "    System.out.println(\"b\");\n"
			+ "}\n"
			+ "}"
		);
		//@formatter:on
		int[] loc = findCompletionLocation(unit, "s.");
		CompletionList list = server.completion(JsonMessageHelper.getParams(createCompletionRequest(unit, loc[0], loc[1]))).join().getRight();
		assertNotNull(list);
		List<CompletionItem> items = new ArrayList<>(list.getItems());
		assertTrue(items.size() > 0);
	}

	// https://github.com/eclipse/eclipse.jdt.ls/issues/1800
	@Test
	public void testSnippet_if2() throws JavaModelException {
		//@formatter:off
		ICompilationUnit unit = getWorkingCopy(
				"src/org/sample/Test.java",
				"public class Test {\n"
				+ "  private boolean flag;\n"
				+ "  private void test(List<String> c) {\n"
				+ "    if (flag) {\n"
				+ "      \n"
				+ "      List<String> scs = c.subList(0, 1);\n"
				+ "    }\n"
				+ "  }\n"
				+ "  String test() {\n"
				+ "    return null;\n"
				+ "  } \n"
				+ "}"
				);
		//@formatter:on
		int[] loc = findCompletionLocation(unit, "      ");
		CompletionList list = server.completion(JsonMessageHelper.getParams(createCompletionRequest(unit, loc[0], loc[1]))).join().getRight();
		assertNotNull(list);
		List<CompletionItem> items = new ArrayList<>(list.getItems());
		assertTrue(items.size() > 1);
	}

	// https://github.com/eclipse/eclipse.jdt.ls/issues/1811
	@Test
	public void testSnippet_multiline_string() throws JavaModelException {
		//@formatter:off
			ICompilationUnit unit = getWorkingCopy(
				"src/org/sample/Test.java",
				"package org.sample;\n" +
				"public class Test {\n"
				+ "  public void test () {\n"
				+ "    String foo = \"\"\"\n"
				+ "    test1\n"
				+ "    test2\n"
				+ "    test3\n"
				+ "    \"\"\".;\n"
				+ "  }\n"
				+ "}"
			);
			//@formatter:on
		int[] loc = findCompletionLocation(unit, "\".");
		CompletionList list = server.completion(JsonMessageHelper.getParams(createCompletionRequest(unit, loc[0], loc[1]))).join().getRight();
		assertNotNull(list);
		List<CompletionItem> items = new ArrayList<>(list.getItems());
		assertTrue(items.size() > 0);
	}

	@Test
	public void testSnippet_ctor() throws JavaModelException {
		//@formatter:off
			ICompilationUnit unit = getWorkingCopy(
				"src/org/sample/Test.java",
				"package org.sample;\n" +
				"public class MainClass {\n"
				+ "}" +
				"class AnotherClass {\n"
				+ "ctor\n"
				+ "}"
			);
			//@formatter:on
		CompletionList list = requestCompletions(unit, "ctor");
		assertNotNull(list);
		List<CompletionItem> items = new ArrayList<>(list.getItems());
		CompletionItem item = items.get(0);
		assertEquals("ctor", item.getLabel());
		String newText = item.getTextEdit().getLeft().getNewText();
		assertEquals("${1|public,protected,private|} AnotherClass(${2}) {\n" + "\t${3:super();}${0}\n" + "}", newText);
	}

	@Test
	public void testSnippet_interface() throws JavaModelException {
		ICompilationUnit unit = getWorkingCopy("src/org/sample/Test.java", "");
		CompletionList list = requestCompletions(unit, "");

		assertNotNull(list);
		List<CompletionItem> items = new ArrayList<>(list.getItems());
		assertFalse(items.isEmpty());
		items.sort((i1, i2) -> (i1.getSortText().compareTo(i2.getSortText())));

		CompletionItem item = items.get(1);
		assertEquals("interface", item.getLabel());
		String te = item.getInsertText();
		assertEquals("package org.sample;\n\n/**\n * Test\n */\npublic interface Test {\n\n\t${0}\n}", ResourceUtils.dos2Unix(te));

		//check resolution doesn't blow up (https://github.com/eclipse/eclipse.jdt.ls/issues/675)
		assertSame(item, server.resolveCompletionItem(item).join());
	}

	@Test
	public void testSnippet_interface_with_package() throws JavaModelException {
		ICompilationUnit unit = getWorkingCopy("src/org/sample/Test.java", "package org.sample;\n");
		CompletionList list = requestCompletions(unit, "package org.sample;\n");

		assertNotNull(list);
		List<CompletionItem> items = new ArrayList<>(list.getItems());
		assertFalse(items.isEmpty());
		items.sort((i1, i2) -> (i1.getSortText().compareTo(i2.getSortText())));

		CompletionItem item = items.get(1);
		assertEquals("interface", item.getLabel());
		String te = item.getInsertText();
		assertEquals("/**\n * Test\n */\npublic interface Test {\n\n\t${0}\n}", te);
	}

	@Test
	public void testSnippet_inner_interface() throws JavaModelException {
		ICompilationUnit unit = getWorkingCopy("src/org/sample/Test.java", "package org.sample;\npublic interface Test {}\n");
		CompletionList list = requestCompletions(unit, "package org.sample;\npublic interface Test {}\n");

		assertNotNull(list);
		List<CompletionItem> items = new ArrayList<>(list.getItems());
		assertFalse(items.isEmpty());
		items.sort((i1, i2) -> (i1.getSortText().compareTo(i2.getSortText())));

		CompletionItem item = items.get(1);
		assertEquals("interface", item.getLabel());
		String te = item.getInsertText();
		assertEquals("/**\n * ${1:InnerTest}\n */\npublic interface ${1:InnerTest} {\n\n\t${0}\n}", te);
	}

	@Test
	public void testSnippet_sibling_inner_interface() throws JavaModelException {
		ICompilationUnit unit = getWorkingCopy("src/org/sample/Test.java", "package org.sample;\npublic interface Test {}\npublic interface InnerTest{}\n");
		CompletionList list = requestCompletions(unit, "package org.sample;\npublic interface Test {}\npublic interface InnerTest{}\n");

		assertNotNull(list);
		List<CompletionItem> items = new ArrayList<>(list.getItems());
		assertFalse(items.isEmpty());
		items.sort((i1, i2) -> (i1.getSortText().compareTo(i2.getSortText())));

		CompletionItem item = items.get(1);
		assertEquals("interface", item.getLabel());
		String te = item.getInsertText();
		assertEquals("/**\n * ${1:InnerTest_1}\n */\npublic interface ${1:InnerTest_1} {\n\n\t${0}\n}", te);
	}

	@Test
	public void testSnippet_nested_inner_interface() throws JavaModelException {
		ICompilationUnit unit = getWorkingCopy("src/org/sample/Test.java", "package org.sample;\npublic interface Test {}\npublic interface InnerTest{\n");
		CompletionList list = requestCompletions(unit, "package org.sample;\npublic interface Test {}\npublic interface InnerTest{\n");

		assertNotNull(list);
		List<CompletionItem> items = list.getItems().stream().filter(item -> item.getSortText() != null).collect(Collectors.toCollection(ArrayList::new));
		assertFalse(items.isEmpty());
		items.sort((i1, i2) -> (i1.getSortText().compareTo(i2.getSortText())));

		CompletionItem item = items.get(15);
		assertEquals("interface", item.getLabel());
		String te = item.getInsertText();
		assertEquals("/**\n * ${1:InnerTest_1}\n */\npublic interface ${1:InnerTest_1} {\n\n\t${0}\n}", te);
	}

	@Test
	public void testSnippet_nested_inner_interface_nosnippet() throws JavaModelException {
		mockLSP2Client();
		ICompilationUnit unit = getWorkingCopy("src/org/sample/Test.java", "package org.sample;\npublic interface Test {}\npublic interface InnerTest{\n");
		CompletionList list = requestCompletions(unit, "package org.sample;\npublic interface Test {}\npublic interface InnerTest{\n");

		assertNotNull(list);
		assertFalse("No snippets should be returned", list.getItems().stream().anyMatch(ci -> ci.getKind() == CompletionItemKind.Snippet));
	}

	@Test
	public void testSnippet_interface_method() throws JavaModelException {
		//@formatter:off
			ICompilationUnit unit = getWorkingCopy(
				"src/org/sample/Test.java",
				"package org.sample;\n" +
				"public interface Test {\n"
				+ "method\n"
				+ "}"
			);
			//@formatter:on
		CompletionList list = requestCompletions(unit, "method");
		assertNotNull(list);
		List<CompletionItem> items = new ArrayList<>(list.getItems());
		CompletionItem itemOne = items.get(6);
		CompletionItem itemTwo = items.get(7);
		assertEquals("method", itemOne.getLabel());
		assertEquals("static_method", itemTwo.getLabel());
		String methodText = itemOne.getTextEdit().getLeft().getNewText();
		String staticMethodText = itemTwo.getTextEdit().getLeft().getNewText();
		assertEquals("${1|public,private|} ${2:void} ${3:name}(${4});", methodText);
		assertEquals("${1|public,private|} static ${2:void} ${3:name}(${4}) {\n" + "\t${0}\n" + "}", staticMethodText);
	}

	@Test
	public void testSnippet_interface_no_ctor() throws JavaModelException {
		//@formatter:off
			ICompilationUnit unit = getWorkingCopy(
				"src/org/sample/Test.java",
				"package org.sample;\n" +
				"public interface Test {\n"
				+ "ctor\n"
				+ "}"
			);
			//@formatter:on
		CompletionList list = requestCompletions(unit, "ctor");
		assertNotNull(list);
		List<CompletionItem> items = new ArrayList<>(list.getItems());
		assertFalse("No ctor snippet should be available", items.stream().anyMatch(i -> "ctor".equals(i.getLabel())));
	}

	@Test
	public void testSnippet_class() throws JavaModelException {
		ICompilationUnit unit = getWorkingCopy("src/org/sample/Test.java", "");
		CompletionList list = requestCompletions(unit, "");

		assertNotNull(list);
		List<CompletionItem> items = new ArrayList<>(list.getItems());
		assertFalse(items.isEmpty());
		items.sort((i1, i2) -> (i1.getSortText().compareTo(i2.getSortText())));

		CompletionItem item = items.get(0);
		assertEquals("class", item.getLabel());
		String te = item.getInsertText();
		assertEquals("package org.sample;\n\n/**\n * Test\n */\npublic class Test {\n\n\t${0}\n}", ResourceUtils.dos2Unix(te));
	}

	@Test
	public void testSnippet_class_with_package() throws JavaModelException {
		ICompilationUnit unit = getWorkingCopy("src/org/sample/Test.java", "package org.sample;\n");
		CompletionList list = requestCompletions(unit, "package org.sample;\n");

		assertNotNull(list);
		List<CompletionItem> items = new ArrayList<>(list.getItems());
		assertFalse(items.isEmpty());
		items.sort((i1, i2) -> (i1.getSortText().compareTo(i2.getSortText())));

		CompletionItem item = items.get(0);
		assertEquals("class", item.getLabel());
		String te = item.getInsertText();
		assertEquals("/**\n * Test\n */\npublic class Test {\n\n\t${0}\n}", te);
	}

	@Test
	public void testSnippet_inner_class() throws JavaModelException {
		ICompilationUnit unit = getWorkingCopy("src/org/sample/Test.java", "package org.sample;\npublic class Test {}\n");
		CompletionList list = requestCompletions(unit, "");

		assertNotNull(list);
		List<CompletionItem> items = new ArrayList<>(list.getItems());
		assertFalse(items.isEmpty());
		items.sort((i1, i2) -> (i1.getSortText().compareTo(i2.getSortText())));

		CompletionItem item = items.get(0);
		assertEquals("class", item.getLabel());
		String te = item.getInsertText();
		assertEquals("/**\n * ${1:InnerTest}\n */\npublic class ${1:InnerTest} {\n\n\t${0}\n}", te);
	}

	@Test
	public void testSnippet_inner_class_itemDefaults_enabled_type_definition() throws JavaModelException {
		mockClientPreferences(true, true, true);
		when(preferenceManager.getClientPreferences().getCompletionItemInsertTextModeDefault()).thenReturn(InsertTextMode.AsIs);
		ICompilationUnit unit = getWorkingCopy("src/org/sample/Test.java", "package org.sample;\npublic class Test {}\n");
		CompletionList list = requestCompletions(unit, "");

		assertNotNull(list);
		assertEquals(InsertTextFormat.Snippet, list.getItemDefaults().getInsertTextFormat());
		assertEquals(InsertTextMode.AdjustIndentation, list.getItemDefaults().getInsertTextMode());

		List<CompletionItem> items = new ArrayList<>(list.getItems());
		assertFalse(items.isEmpty());
		items.sort((i1, i2) -> (i1.getSortText().compareTo(i2.getSortText())));

		CompletionItem item = items.get(0);
		assertEquals("class", item.getLabel());
		String te = item.getTextEditText();
		assertEquals("/**\n * ${1:InnerTest}\n */\npublic class ${1:InnerTest} {\n\n\t${0}\n}", te);
		//check that the fields covered by itemDefaults are set to null
		assertNull(item.getTextEdit());
		assertNull(item.getInsertTextFormat());
		assertNull(item.getInsertTextMode());
	}

	@Test
	public void testSnippet_sibling_inner_class() throws JavaModelException {
		ICompilationUnit unit = getWorkingCopy("src/org/sample/Test.java", "package org.sample;\npublic class Test {}\npublic class InnerTest{}\n");
		CompletionList list = requestCompletions(unit, "package org.sample;\npublic class Test {}\npublic class InnerTest{}\n");

		assertNotNull(list);
		List<CompletionItem> items = new ArrayList<>(list.getItems());
		assertFalse(items.isEmpty());
		items.sort((i1, i2) -> (i1.getSortText().compareTo(i2.getSortText())));

		CompletionItem item = items.get(0);
		assertEquals("class", item.getLabel());
		String te = item.getInsertText();
		assertEquals("/**\n * ${1:InnerTest_1}\n */\npublic class ${1:InnerTest_1} {\n\n\t${0}\n}", te);
	}

	@Test
	public void testSnippet_sibling_inner_class_nosnippets() throws JavaModelException {
		mockLSP2Client();
		ICompilationUnit unit = getWorkingCopy("src/org/sample/Test.java", "package org.sample;\npublic class Test {}\npublic class InnerTest{}\n");
		CompletionList list = requestCompletions(unit, "package org.sample;\npublic class Test {}\npublic class InnerTest{}\n");

		assertNotNull(list);
		assertFalse("No snippets should be returned", list.getItems().stream().anyMatch(ci -> ci.getKind() == CompletionItemKind.Snippet));
	}

	@Test
	public void testSnippet_nested_inner_class() throws JavaModelException {
		ICompilationUnit unit = getWorkingCopy("src/org/sample/Test.java", "package org.sample;\npublic class Test {}\npublic class InnerTest{\n");
		CompletionList list = requestCompletions(unit, "package org.sample;\npublic class Test {}\npublic class InnerTest{\n");

		assertNotNull(list);
		List<CompletionItem> items = list.getItems().stream().filter(item -> item.getSortText() != null).collect(Collectors.toCollection(ArrayList::new));
		assertFalse(items.isEmpty());
		items.sort((i1, i2) -> (i1.getSortText().compareTo(i2.getSortText())));

		CompletionItem item = items.get(14);
		assertEquals("class", item.getLabel());
		String te = item.getInsertText();
		assertNotNull(te);
		assertEquals("/**\n * ${1:InnerTest_1}\n */\npublic class ${1:InnerTest_1} {\n\n\t${0}\n}", te);
	}

	@Test
	public void testSnippet_class_no_static_method() throws JavaModelException {
		//@formatter:off
			ICompilationUnit unit = getWorkingCopy(
				"src/org/sample/Test.java",
				"package org.sample;\n" +
				"public class Test {\n"
				+ "static_method\n"
				+ "}"
			);
			//@formatter:on
		CompletionList list = requestCompletions(unit, "static_method");
		assertNotNull(list);
		List<CompletionItem> items = new ArrayList<>(list.getItems());
		assertFalse("No static_method snippet should be available", items.stream().anyMatch(i -> "static_method".equals(i.getLabel())));
	}

	@Test
	public void testSnippet_no_record() throws JavaModelException {
		ICompilationUnit unit = getWorkingCopy("src/org/sample/Test.java", "");
		CompletionList list = requestCompletions(unit, "");

		assertNotNull(list);
		List<CompletionItem> items = new ArrayList<>(list.getItems());
		//Not a Java 14 project => no snippet
		assertFalse("No record snippet should be available", items.stream().anyMatch(i -> "record".equals(i.getLabel())));
	}

	@Test
	public void testSnippet_record() throws Exception {
		importProjects("eclipse/records");
		project = WorkspaceHelper.getProject("records");
		ICompilationUnit unit = getWorkingCopy("src/main/java/org/sample/Test.java", "");
		CompletionList list = requestCompletions(unit, "");

		assertNotNull(list);
		List<CompletionItem> items = new ArrayList<>(list.getItems());
		assertFalse(items.isEmpty());
		items.sort((i1, i2) -> (i1.getSortText().compareTo(i2.getSortText())));

		CompletionItem item = items.get(2);
		assertEquals("record", item.getLabel());
		String te = item.getInsertText();
		assertEquals("package org.sample;\n\n/**\n * Test\n */\npublic record Test(${0}) {\n}", ResourceUtils.dos2Unix(te));

		//check resolution doesn't blow up (https://github.com/eclipse/eclipse.jdt.ls/issues/675)
		assertSame(item, server.resolveCompletionItem(item).join());
	}

	@Test
	public void testSnippet_record_with_package() throws Exception {
		importProjects("eclipse/records");
		project = WorkspaceHelper.getProject("records");
		ICompilationUnit unit = getWorkingCopy("src/main/java/org/sample/Test.java", "package org.sample;\n");
		CompletionList list = requestCompletions(unit, "package org.sample;\n");

		assertNotNull(list);
		List<CompletionItem> items = new ArrayList<>(list.getItems());
		assertFalse(items.isEmpty());
		items.sort((i1, i2) -> (i1.getSortText().compareTo(i2.getSortText())));

		CompletionItem item = items.get(2);
		assertEquals("record", item.getLabel());
		String te = item.getInsertText();
		assertEquals("/**\n * Test\n */\npublic record Test(${0}) {\n}", te);
	}

	@Ignore(value = "When running tests, in SnippetCompletionProposal.getSnippetContent(), cu.getAllTypes() returns en empty array, so inner record name is not computed")
	@Test
	public void testSnippet_inner_record() throws Exception {
		importProjects("eclipse/records");
		project = WorkspaceHelper.getProject("records");
		ICompilationUnit unit = getWorkingCopy("src/main/java/org/sample/Test.java", "package org.sample;\npublic record Test() {}\n");
		CompletionList list = requestCompletions(unit, "package org.sample;\npublic record Test() {");

		assertNotNull(list);
		List<CompletionItem> items = new ArrayList<>(list.getItems());
		assertFalse(items.isEmpty());
		items.sort((i1, i2) -> (i1.getSortText().compareTo(i2.getSortText())));

		CompletionItem item = items.get(7);
		assertEquals("record", item.getLabel());
		String te = item.getInsertText();
		assertEquals("/**\n * ${1:InnerTest}\n */\npublic record ${1:InnerTest}(${0}) {\n}", te);
	}

	@Ignore(value = "When running tests, in SnippetCompletionProposal.getSnippetContent(), cu.getAllTypes() returns en empty array, so inner record name is not computed")
	@Test
	public void testSnippet_sibling_inner_record() throws Exception {
		importProjects("eclipse/records");
		project = WorkspaceHelper.getProject("records");
		ICompilationUnit unit = getWorkingCopy("src/main/java/org/sample/Test.java", "package org.sample;\npublic record Test() {}\npublic record InnerTest(){}\n");
		CompletionList list = requestCompletions(unit, "package org.sample;\npublic record Test {}\npublic record InnerTest(){}\n");

		assertNotNull(list);
		List<CompletionItem> items = new ArrayList<>(list.getItems());
		assertFalse(items.isEmpty());
		items.sort((i1, i2) -> (i1.getSortText().compareTo(i2.getSortText())));

		CompletionItem item = items.get(7);
		assertEquals("record", item.getLabel());
		String te = item.getInsertText();
		assertEquals("/**\n * ${1:InnerTest_1}\n */\npublic record ${1:InnerTest_1}(${0) {\n}", te);
	}

	@Ignore(value = "When running tests, in SnippetCompletionProposal.getSnippetContent(), cu.getAllTypes() returns en empty array, so inner record name is not computed")
	@Test
	public void testSnippet_nested_inner_record() throws Exception {
		importProjects("eclipse/records");
		project = WorkspaceHelper.getProject("records");
		ICompilationUnit unit = getWorkingCopy("src/main/java/org/sample/Test.java", "package org.sample;\npublic record Test() {}\npublic record InnerTest(){\n");
		CompletionList list = requestCompletions(unit, "package org.sample;\npublic record Test() {}\npublic record InnerTest(){\n");

		assertNotNull(list);
		List<CompletionItem> items = new ArrayList<>(list.getItems());
		assertFalse(items.isEmpty());
		items.sort((i1, i2) -> (i1.getSortText().compareTo(i2.getSortText())));

		CompletionItem item = items.get(24);
		assertEquals("record", item.getLabel());
		String te = item.getInsertText();
		assertEquals("/**\n * ${1:InnerTest_1}\n */\npublic record ${1:InnerTest_1}(${0}) {\n}", te);
	}

	@Test
	public void testSnippet_nested_inner_record_nosnippet() throws Exception {
		importProjects("eclipse/records");
		project = WorkspaceHelper.getProject("records");
		mockLSP2Client();
		ICompilationUnit unit = getWorkingCopy("src/main/java/org/sample/Test.java", "package org.sample;\npublic record Test() {}\npublic record InnerTest(){\n");
		CompletionList list = requestCompletions(unit, "package org.sample;\npublic record Test() {}\npublic record InnerTest(){\n");

		assertNotNull(list);
		assertFalse("No snippets should be returned", list.getItems().stream().anyMatch(ci -> ci.getKind() == CompletionItemKind.Snippet));
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
		testCompletion_classMethodOverride("java11", true, true);
	}

	@Test
	public void testCompletion_interfaceMethodOverrideJava4() throws Exception {
		testCompletion_interfaceMethodOverride("java11", true, true);
	}

	@Test
	public void testCompletion_classMethodOverrideJava5() throws Exception {
		testCompletion_classMethodOverride("java11", true, true);
	}

	@Test
	public void testCompletion_interfaceMethodOverrideJava5() throws Exception {
		testCompletion_interfaceMethodOverride("java11", true, true);
	}

	private void testCompletion_classMethodOverride(String projectName, boolean supportSnippets,
			boolean overridesSuperClass) throws Exception {
		if (project == null || !projectName.equals(project.getName())) {
			importProjects("eclipse/"+projectName);
			project = WorkspaceHelper.getProject(projectName);
		}
		mockClientPreferences(supportSnippets, true, false);

		ICompilationUnit unit = getWorkingCopy(
				"src/java/Foo.java",
				"public class Foo {\n"
						+ "    toStr"
						+"}\n");
		CompletionList list = requestCompletions(unit, " toStr");
		assertNotNull(list);
		List<CompletionItem> filtered = list.getItems().stream().filter((item)->{
			return item.getDetail() != null && item.getDetail().startsWith("Override method in");
		}).collect(Collectors.toList());
		assertFalse("No override proposals", filtered.isEmpty());
		CompletionItem oride = filtered.get(0);
		assertEquals("toString", oride.getInsertText());
		assertNotNull(oride.getTextEdit());
		String text = oride.getTextEdit().getLeft().getNewText();
		StringBuilder expectedText = new StringBuilder();
		if (overridesSuperClass) {
			expectedText.append("@Override\n");
		}
		expectedText.append("public String toString() {\n\t");
		if (supportSnippets) {
			expectedText.append("${0:");
		}
		expectedText.append("// TODO Auto-generated method stub\n\t");
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
		mockClientPreferences(supportSnippets, true, false);

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
		assertNotNull(oride.getTextEdit());
		String text = oride.getTextEdit().getLeft().getNewText();
		StringBuilder expectedText = new StringBuilder();
		if (overridesInterface) {
			expectedText.append("@Override\n");
		}
		expectedText.append("public void run() {\n\t");
		if (supportSnippets) {
			expectedText.append("${0:");
		}
		expectedText.append("// TODO Auto-generated method stub\n\t");
		if (supportSnippets) {
			expectedText.append("}");
		}
		expectedText.append("\n}");
		assertEquals(expectedText.toString(), text);

	}

	@Test
	public void testCompletion_methodOverrideWithParams() throws Exception {
		when(preferenceManager.getClientPreferences().isCompletionItemLabelDetailsSupport()).thenReturn(true);
		ICompilationUnit unit = getWorkingCopy(
				//@formatter:off
				"src/org/sample/Test.java",
				"package org.sample;\n\n"+
				"public class Test extends Baz {\n"+
				"    getP" +
				"}\n");
				//@formatter:on
		CompletionList list = requestCompletions(unit, " getP");
		assertNotNull(list);
		List<CompletionItem> filtered = list.getItems().stream().filter((item)->{
			return item.getDetail() != null && item.getDetail().startsWith("Override method in");
		}).collect(Collectors.toList());
		assertEquals("No override proposals", filtered.size(), 1);
		CompletionItem oride = filtered.get(0);
		assertEquals("getParent", oride.getInsertText());
		assertNotNull(oride.getTextEdit());
		String text = oride.getTextEdit().getLeft().getNewText();

		String expectedText = "@Override\n"+
				"protected File getParent(File file, int depth) {\n" +
				"\t${0:// TODO Auto-generated method stub\n\treturn super.getParent(file, depth);}\n" +
				"}";

		assertEquals(expectedText, text);
		assertEquals("Missing required imports", 1, oride.getAdditionalTextEdits().size());

		assertEquals("\n\nimport java.io.File;\n\n", oride.getAdditionalTextEdits().get(0).getNewText());
		assertPosition(0, 19, oride.getAdditionalTextEdits().get(0).getRange().getStart());
		assertPosition(2, 0, oride.getAdditionalTextEdits().get(0).getRange().getEnd());
	}

	@Test
	public void testCompletion_methodOverrideWithException() throws Exception {
		when(preferenceManager.getClientPreferences().isCompletionItemLabelDetailsSupport()).thenReturn(true);
		ICompilationUnit unit = getWorkingCopy(
				//@formatter:off
				"src/org/sample/Test.java",
				"package org.sample;\n\n"+
				"public class Test extends Baz {\n"+
				"    dele"+
				"}\n");
				//@formatter:on
		CompletionList list = requestCompletions(unit, " dele");
		assertNotNull(list);
		List<CompletionItem> filtered = list.getItems().stream().filter((item)->{
			return item.getDetail() != null && item.getDetail().startsWith("Override method in");
		}).collect(Collectors.toList());
		assertEquals("No override proposals", filtered.size(), 1);
		CompletionItem oride = filtered.get(0);
		assertEquals("deleteSomething", oride.getInsertText());
		assertNotNull(oride.getTextEdit());
		String text = oride.getTextEdit().getLeft().getNewText();

		assertEquals(oride.getLabel(), "deleteSomething");
		assertEquals(oride.getLabelDetails().getDetail(), "()");
		assertEquals(oride.getLabelDetails().getDescription(), "void");

		String expectedText = "@Override\n"+
				"protected void deleteSomething() throws IOException {\n" +
				"\t${0:// TODO Auto-generated method stub\n" +
				"\tsuper.deleteSomething();}\n" +
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
		CompletionList list = requestCompletions(unit, "   zz");
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

		CompletionList list = requestCompletions(unit, "get");
		assertNotNull(list);
		CompletionItem ci = list.getItems().stream()
				.filter(item -> item.getLabel().startsWith("getStrField() : String"))
				.findFirst().orElse(null);
		assertNotNull(ci);

		assertEquals("getStrField", ci.getInsertText());
		assertEquals(CompletionItemKind.Method, ci.getKind());
		assertEquals("999999979", ci.getSortText());
		assertNotNull(ci.getTextEdit().getLeft());
		assertTextEdit(2, 4, 7, "/**\n" +
				 " * @return the strField\n" +
				 " */\n" +
				"public String getStrField() {\n" +
				"	return strField;\n" +
				"}", ci.getTextEdit().getLeft());
	}

	@Test
	public void testCompletion_getterNoJavadoc() throws Exception {
		preferences.setCodeGenerationTemplateGenerateComments(false);
		ICompilationUnit unit = getWorkingCopy(
				"src/java/Foo.java",
				"public class Foo {\n"+
						"    private String strField;\n" +
						"    get" +
				"}\n");

		CompletionList list = requestCompletions(unit, "get");
		assertNotNull(list);
		CompletionItem ci = list.getItems().stream()
				.filter(item -> item.getLabel().startsWith("getStrField() : String"))
				.findFirst().orElse(null);
		assertNotNull(ci);

		assertEquals("getStrField", ci.getInsertText());
		assertEquals(CompletionItemKind.Method, ci.getKind());
		assertEquals("999999979", ci.getSortText());
		assertNotNull(ci.getTextEdit().getLeft());
		assertTextEdit(2, 4, 7,
				"public String getStrField() {\n" +
				"	return strField;\n" +
				"}", ci.getTextEdit().getLeft());
	}

	@Test
	public void testCompletion_booleangetter() throws Exception {

		ICompilationUnit unit = getWorkingCopy(
				"src/java/Foo.java",
				"public class Foo {\n"+
						"    private boolean boolField;\n" +
						"    is\n" +
				"}\n");

		CompletionList list = requestCompletions(unit, "is");
		assertNotNull(list);
		CompletionItem ci = list.getItems().stream()
				.filter(item -> item.getLabel().startsWith("isBoolField() : boolean"))
				.findFirst().orElse(null);
		assertNotNull(ci);

		assertEquals("isBoolField", ci.getInsertText());
		assertEquals(CompletionItemKind.Method, ci.getKind());
		assertEquals("999999979", ci.getSortText());
		assertNotNull(ci.getTextEdit().getLeft());
		assertTextEdit(2, 4, 6, "/**\n" +
				 " * @return the boolField\n" +
				 " */\n" +
				"public boolean isBoolField() {\n" +
				"	return boolField;\n" +
				"}", ci.getTextEdit().getLeft());
	}

	@Test
	public void testCompletion_setter() throws Exception {
		ICompilationUnit unit = getWorkingCopy(
				"src/java/Foo.java",
				"public class Foo {\n"+
						"    private String strField;\n" +
						"    set" +
				"}\n");

		CompletionList list = requestCompletions(unit, "set");
		assertNotNull(list);
		CompletionItem ci = list.getItems().stream()
				.filter(item -> item.getLabel().startsWith("setStrField(String strField) : void"))
				.findFirst().orElse(null);
		assertNotNull(ci);

		assertEquals("setStrField", ci.getInsertText());
		assertEquals(CompletionItemKind.Method, ci.getKind());
		assertEquals("999999979", ci.getSortText());
		assertNotNull(ci.getTextEdit().getLeft());
		assertTextEdit(2, 4, 7, "/**\n" +
				" * @param strField the strField to set\n" +
				 " */\n" +
				"public void setStrField(String strField) {\n" +
				"	this.strField = strField;\n" +
				"}", ci.getTextEdit().getLeft());
	}

	@Test
	public void testCompletion_AnonymousType() throws Exception {
		when(preferenceManager.getClientPreferences().isCompletionItemLabelDetailsSupport()).thenReturn(true);
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
		CompletionList list = requestCompletions(unit, "new ");
		assertNotNull(list);
		CompletionItem ci = list.getItems().stream()
				.filter(item -> item.getLabel().startsWith("Foo.IFoo"))
				.findFirst().orElse(null);
		assertNotNull(ci);

		assertEquals("Foo.IFoo", ci.getInsertText());
		assertEquals(CompletionItemKind.Constructor, ci.getKind());
		// createAnonymousTypeLabel
		assertEquals("Foo.IFoo", ci.getLabel());
		assertEquals("()", ci.getLabelDetails().getDetail());
		assertEquals("Anonymous Inner Type", ci.getLabelDetails().getDescription());


		assertEquals("999998684", ci.getSortText());
		assertNotNull(ci.getTextEdit().getLeft());
		assertTextEdit(2, 23, 23, "IFoo() {\n" +
				"	${0}\n" +
				"};", ci.getTextEdit().getLeft());
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
		CompletionList list = requestCompletions(unit, "new ");
		assertNotNull(list);
		CompletionItem ci = list.getItems().stream()
				.filter(item -> item.getLabel().startsWith("Foo.IFoo()  Anonymous Inner Type"))
				.findFirst().orElse(null);
		assertNotNull(ci);

		assertEquals("Foo.IFoo", ci.getInsertText());
		assertEquals(CompletionItemKind.Constructor, ci.getKind());
		assertEquals("999998684", ci.getSortText());
		assertNotNull(ci.getTextEdit().getLeft());
		assertTextEdit(2, 23, 23, "IFoo() {\n" +
				"	${0}\n" +
				"};", ci.getTextEdit().getLeft());
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
		CompletionList list = requestCompletions(unit, "Runnable(");
		assertNotNull(list);
		CompletionItem ci = list.getItems().stream()
				.filter(item -> item.getLabel().startsWith("Runnable()  Anonymous Inner Type"))
				.findFirst().orElse(null);
		assertNotNull(ci);

		assertEquals("Runnable", ci.getInsertText());
		assertEquals(CompletionItemKind.Class, ci.getKind());
		assertEquals("999999372", ci.getSortText());
		assertNotNull(ci.getTextEdit().getLeft());
		assertTextEdit(2, 20, 22, "() {\n" +
				"	${0}\n" +
				"}", ci.getTextEdit().getLeft());
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
		CompletionList list = requestCompletions(unit, "Runnable( ");
		assertNotNull(list);
		CompletionItem ci = list.getItems().stream()
				.filter(item -> item.getLabel().startsWith("Runnable()  Anonymous Inner Type"))
				.findFirst().orElse(null);
		assertNotNull(ci);

		assertEquals("Runnable", ci.getInsertText());
		assertEquals(CompletionItemKind.Class, ci.getKind());
		assertEquals("999999372", ci.getSortText());
		assertNotNull(ci.getTextEdit().getLeft());
		assertTextEdit(2, 20, 24, "() {\n" +
				"	${0}\n" +
				"}", ci.getTextEdit().getLeft());
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
		CompletionList list = requestCompletions(unit, "Runnable(");
		assertNotNull(list);
		CompletionItem ci = list.getItems().stream()
				.filter(item -> item.getLabel().startsWith("Runnable()  Anonymous Inner Type"))
				.findFirst().orElse(null);
		assertNotNull(ci);

		assertEquals("Runnable", ci.getInsertText());
		assertEquals(CompletionItemKind.Class, ci.getKind());
		assertEquals("999999372", ci.getSortText());
		assertNotNull(ci.getTextEdit().getLeft());
		assertTextEdit(2, 33, 37, "() {\n" +
				"	${0}\n" +
				"}", ci.getTextEdit().getLeft());
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
		CompletionList list = requestCompletions(unit, "Runnable(");
		assertNotNull(list);
		CompletionItem ci = list.getItems().stream()
				.filter(item -> item.getLabel().startsWith("Runnable()  Anonymous Inner Type"))
				.findFirst().orElse(null);
		assertNotNull(ci);

		assertEquals("Runnable", ci.getInsertText());
		assertEquals(CompletionItemKind.Class, ci.getKind());
		assertEquals("999999372", ci.getSortText());
		assertNotNull(ci.getTextEdit().getLeft());
		assertTextEdit(3, 8, 12, "() {\n" +
				"	${0}\n" +
				"}", ci.getTextEdit().getLeft());
	}

	@Test
	public void testCompletion_AnonymousDeclarationType5() throws Exception {
		ICompilationUnit unit = getWorkingCopy(
				"src/java/Foo.java",
				"public class Foo {\n"+
						"    public static void main(String[] args) {\n" +
						"        run(\"name\", new Runnable(");
		waitForBackgroundJobs();
		CompletionList list = requestCompletions(unit, "Runnable(");
		assertNotNull(list);
		CompletionItem ci = list.getItems().stream()
				.filter(item -> item.getLabel().startsWith("Runnable()  Anonymous Inner Type"))
				.findFirst().orElse(null);
		assertNotNull(ci);

		assertEquals("Runnable", ci.getInsertText());
		assertEquals(CompletionItemKind.Class, ci.getKind());
		assertEquals("999999372", ci.getSortText());
		assertNotNull(ci.getTextEdit().getLeft());
		assertTextEdit(2, 33, 33, "() {\n" +
				"	${0}\n" +
				"}", ci.getTextEdit().getLeft());
	}

	@Test
	public void testCompletion_AnonymousDeclarationType_noSnippet() throws Exception {
		ClientPreferences mockCapabilies = mock(ClientPreferences.class);
		when(mockCapabilies.isCompletionSnippetsSupported()).thenReturn(false);
		when(preferenceManager.getClientPreferences()).thenReturn(mockCapabilies);

		ICompilationUnit unit = getWorkingCopy(
				"src/java/Foo.java",
				"public class Foo {\n"+
						"    public static void main(String[] args) {\n" +
						"        new Runnable()\n" +
						"    }\n" +
				"}\n");
		waitForBackgroundJobs();
		CompletionList list = requestCompletions(unit, "Runnable(");
		assertNotNull(list);
		CompletionItem ci = list.getItems().stream()
				.filter(item -> item.getLabel().startsWith("Runnable()  Anonymous Inner Type"))
				.findFirst().orElse(null);
		assertNotNull(ci);

		assertEquals("Runnable", ci.getInsertText());
		assertEquals(CompletionItemKind.Class, ci.getKind());
		assertEquals("999999372", ci.getSortText());
		assertNotNull(ci.getTextEdit().getLeft());
		assertTextEdit(2, 20, 22, "() {\n" +
				"\n" +
				"}", ci.getTextEdit().getLeft());
	}

	@Test
	public void testCompletion_type() throws Exception {
		ICompilationUnit unit = getWorkingCopy(
				"src/org/sample/Foo.java",
				"public class Foo {\n"+
						"    public static void main(String[] args) {\n" +
						"        ArrayList\n" +
						"    }\n" +
				"}\n");
		waitForBackgroundJobs();
		CompletionList list = requestCompletions(unit, "ArrayList");
		assertNotNull(list);
		CompletionItem ci = list.getItems().stream()
				.filter(item -> item.getLabel().startsWith("ArrayList"))
				.findFirst().orElse(null);
		assertNotNull(ci);

		assertEquals("ArrayList", ci.getInsertText());
		assertEquals(CompletionItemKind.Class, ci.getKind());
		assertEquals("ArrayList - java.util", ci.getLabel());
		assertEquals("java.util.ArrayList", ci.getDetail());
		assertEquals("999999116", ci.getSortText());
		assertNotNull(ci.getTextEdit().getLeft());
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
		CompletionList list = requestCompletions(unit, "new Foo");
		assertNotNull(list);
		CompletionItem ci = list.getItems().stream()
				.filter(item -> item.getLabel().startsWith("Foo$Bar"))
				.findFirst().orElse(null);
		assertNotNull(ci);

		assertEquals("Foo$Bar", ci.getInsertText());
		assertEquals(CompletionItemKind.Constructor, ci.getKind());
		assertEquals("999999115", ci.getSortText());
		assertNotNull(ci.getTextEdit().getLeft());
		assertTextEdit(2, 12, 15, "Foo\\$Bar()", ci.getTextEdit().getLeft());
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
		CompletionList list = requestCompletions(unit, "new Foo");
		assertNotNull(list);
		CompletionItem ci = list.getItems().stream()
				.filter(item -> item.getLabel().startsWith("Foo$Bar"))
				.findFirst().orElse(null);
		assertNotNull(ci);

		assertEquals("Foo$Bar", ci.getInsertText());
		assertEquals(CompletionItemKind.Constructor, ci.getKind());
		assertEquals("999999115", ci.getSortText());

		assertNotNull(ci.getTextEdit().getLeft());
		assertTextEdit(2, 12, 15, "Foo$Bar", ci.getTextEdit().getLeft());
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
		CompletionList list = requestCompletions(unit, " AbstractTe");
		assertEquals("Test proposals leaked:\n" + list.getItems(), 0, list.getItems().size());
	}

	@Test
	public void testCompletion_testMethodWithParams() throws Exception {
		ClientPreferences mockCapabilies = Mockito.mock(ClientPreferences.class);
		Mockito.when(preferenceManager.getClientPreferences()).thenReturn(mockCapabilies);
		Mockito.when(mockCapabilies.isCompletionResolveDocumentSupport()).thenReturn(true);
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
		CompletionList list = requestCompletions(unit, "\t\tfo");
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
		CompletionList list = requestCompletions(unit, " AbstractTe");
		assertNotNull(list);
		assertEquals("Test proposals missing from :\n" + list, 1, list.getItems().size());
		assertEquals("AbstractTest - foo.bar", list.getItems().get(0).getLabel());
	}

	@Test
	public void testCompletion_overwrite() throws Exception {
		ICompilationUnit unit = getCompletionOverwriteReplaceUnit();
		//@formatter:on
		CompletionList list = requestCompletions(unit, "method(t.");
		assertNotNull(list);
		CompletionItem ci = list.getItems().stream().filter(item -> item.getLabel().startsWith("testInt : int")).findFirst().orElse(null);
		assertNotNull(ci);
		assertEquals("testInt", ci.getInsertText());
		assertEquals(CompletionItemKind.Field, ci.getKind());
		assertEquals("999998554", ci.getSortText());
		assertNotNull(ci.getTextEdit().getLeft());
		List<TextEdit> edits = new ArrayList<>();
		edits.add(ci.getTextEdit().getLeft());
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
		try {
			JavaLanguageServerPlugin.getPreferencesManager().getPreferences().setCompletionOverwrite(false);
			CompletionList list = requestCompletions(unit, "method(t.");
			assertNotNull(list);
			CompletionItem ci = list.getItems().stream().filter(item -> item.getLabel().startsWith("testInt : int")).findFirst().orElse(null);
			assertNotNull(ci);
			assertEquals("testInt", ci.getInsertText());
			assertEquals(CompletionItemKind.Field, ci.getKind());
			assertEquals("999998554", ci.getSortText());
			assertNotNull(ci.getTextEdit().getLeft());
			List<TextEdit> edits = new ArrayList<>();
			edits.add(ci.getTextEdit().getLeft());
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
		CompletionList list = requestCompletions(unit, "public ");
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
		CompletionList list = requestCompletions(unit, "/**");
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
		CompletionList list = requestCompletions(unit, "package ");
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
		CompletionList list = requestCompletions(unit, "{\n\n");
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
		CompletionList list = requestCompletions(unit, "if (c");
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
		CompletionList list = requestCompletions(unit, "int ");
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
		CompletionList list = requestCompletions(unit, "static {\n");
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
		long timeout = Long.getLong("completion.timeout", 5000);
		try {
			System.setProperty("completion.timeout", String.valueOf(60000));
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

			CompletionList list = requestCompletions(unit, "fo");
			assertNotNull(list);
			assertFalse(list.isIncomplete());
			assertTrue(list.getItems().size() > 0);
			assertTrue("no proposal for foo()", "foo() : void".equals(list.getItems().get(0).getLabel()));
		} finally {
			PreferenceManager.getPrefs(null).setJavaCompletionFavoriteMembers(Collections.emptyList());
			System.setProperty("completion.timeout", String.valueOf(timeout));
		}
	}

	@Test
	public void testLimitCompletionResults() throws Exception {
		int maxCompletionResults = PreferenceManager.getPrefs(null).getMaxCompletionResults();
		try {
			ICompilationUnit unit = getWorkingCopy("src/test1/B.java",
			//@formatter:off
				"package test1;\n" +
				"\n" +
				"public class B {\n" +
				"    public void bar() {\n" +
				"        d\n" +
				"    }\n" +
				"}\n");
			//@formatter:on

			//Completion should limit results to maxCompletionResults (excluding snippets)
			CompletionList list = requestCompletions(unit, "d");
			assertNotNull(list);
			assertTrue(list.isIncomplete());
			List<CompletionItem> completionOnly = noSnippets(list.getItems());
			assertEquals(maxCompletionResults, completionOnly.size());
			assertTrue(completionOnly.get(0).getSortText().compareTo(completionOnly.get(completionOnly.size() - 1).getSortText()) < 0);

			//Set max results to 1 to double check
			PreferenceManager.getPrefs(null).setMaxCompletionResults(1);
			list = requestCompletions(unit, "d");
			assertNotNull(list);
			assertTrue(list.isIncomplete());
			completionOnly = noSnippets(list.getItems());
			assertEquals(1, completionOnly.size());

			//when maxCompletionResults is set to 0, limit is disabled, completion should be complete
			PreferenceManager.getPrefs(null).setMaxCompletionResults(0);
			list = requestCompletions(unit, "d");
			assertNotNull(list);
			assertFalse(list.isIncomplete());
			completionOnly = noSnippets(list.getItems());
			assertTrue("Expected way than " + completionOnly.size(), completionOnly.size() > Preferences.JAVA_COMPLETION_MAX_RESULTS_DEFAULT);
			assertTrue(completionOnly.get(0).getSortText().compareTo(completionOnly.get(completionOnly.size() - 1).getSortText()) < 0);

		} finally {
			PreferenceManager.getPrefs(null).setMaxCompletionResults(maxCompletionResults);
		}
	}

	private List<CompletionItem> noSnippets(List<CompletionItem> items) {
		return items.stream().filter(i -> !CompletionItemKind.Snippet.equals(i.getKind())).collect(Collectors.toList());
	}


	@Test
	public void testStaticImports2() throws Exception {
		PreferenceManager.getPrefs(null).setJavaCompletionFavoriteMembers(Collections.emptyList());
		long timeout = Long.getLong("completion.timeout", 5000);
		try {
			System.setProperty("completion.timeout", String.valueOf(60000));
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

			CompletionList list = requestCompletions(unit, "/* */fo");
			assertNotNull(list);
			assertTrue(list.getItems().size() > 0);
			for (CompletionItem it : list.getItems()) {
				if ("foo() : void".equals(it.getLabel())) {
					fail("there is a proposal for foo()");
				}
			}
		} finally {
			System.setProperty("completion.timeout", String.valueOf(timeout));
		}
	}

	@Test
	public void testStarImports() throws Exception {
		List<String> favorites = new ArrayList<>();
		favorites.add("java.lang.Math.*");
		Preferences prefs = PreferenceManager.getPrefs(null);
		List<String> oldFavorites = Arrays.asList(prefs.getJavaCompletionFavoriteMembers());
		int onDemandThreshold = prefs.getImportOnDemandThreshold();
		int staticOnDemandThreshold = prefs.getStaticImportOnDemandThreshold();
		prefs.setJavaCompletionFavoriteMembers(favorites);
		prefs.setImportOnDemandThreshold(2);
		prefs.setStaticImportOnDemandThreshold(2);
		long timeout = Long.getLong("completion.timeout", 5000);

		try {
			System.setProperty("completion.timeout", String.valueOf(60000));
			ICompilationUnit unit = getWorkingCopy("src/test1/B.java",
			//@formatter:off
			"package test1;\n" +
			"import static java.lang.Math.sqrt;\n" +
			"import java.util.List;\n" +
			"public class B {\n" +
			"    List<String> list = new ArrayL\n" +
			"    public static void main(String[] args) {\n" +
			"        double d1 = sqrt(4);\n" +
			"        double d2 = abs\n" +
			"    }\n" +
			"}\n");
			//@formatter:on
			CompletionList list = requestCompletions(unit, "new ArrayL");
			assertNotNull(list);
			assertTrue(list.getItems().size() > 0);
			CompletionItem item = list.getItems().stream().filter(i -> "ArrayList()".equals(i.getLabel())).collect(Collectors.toList()).get(0);
			assertNotNull(item);
			List<TextEdit> textEdits = item.getAdditionalTextEdits();
			assertEquals(1, textEdits.size());
			TextEdit textEdit = textEdits.get(0);
			assertEquals("\n\nimport java.util.*;", textEdit.getNewText());
			list = requestCompletions(unit, "= abs");
			assertNotNull(list);
			assertTrue(list.getItems().size() > 0);
			item = list.getItems().stream().filter(i -> i.getLabel().startsWith("abs(double")).collect(Collectors.toList()).get(0);
			assertNotNull(item);
			textEdits = item.getAdditionalTextEdits();
			assertEquals(1, textEdits.size());
			textEdit = textEdits.get(0);
			assertEquals("import static java.lang.Math.*;\n\n", textEdit.getNewText());
		} finally {
			prefs.setJavaCompletionFavoriteMembers(oldFavorites);
			prefs.setImportOnDemandThreshold(onDemandThreshold);
			prefs.setStaticImportOnDemandThreshold(staticOnDemandThreshold);
			System.setProperty("completion.timeout", String.valueOf(timeout));
		}
	}

	@Test
	public void testCompletion_linksInMarkdown() throws JavaModelException{
		ClientPreferences mockCapabilies = Mockito.mock(ClientPreferences.class);
		Mockito.when(preferenceManager.getClientPreferences()).thenReturn(mockCapabilies);
		Mockito.when(mockCapabilies.isSupportsCompletionDocumentationMarkdown()).thenReturn(true);
		Mockito.when(mockCapabilies.isCompletionResolveDocumentSupport()).thenReturn(true);
		Mockito.lenient().when(mockCapabilies.isClassFileContentSupported()).thenReturn(true);

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
		CompletionList list = requestCompletions(unit, "this.zz");
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
		CompletionList list = requestCompletions(unit, "o.toStr");
		assertNotNull(list);
		assertFalse("No proposals were found",list.getItems().isEmpty());
		CompletionItem ci = list.getItems().get(0);
		assertNull(ci.getAdditionalTextEdits());
		assertEquals("toString() : String", ci.getLabel());
		CompletionItem resolvedItem = server.resolveCompletionItem(ci).join();
		assertNull(resolvedItem.getAdditionalTextEdits());
	}

	@Test
	public void testCompletion_resolveAdditionalTextEdits() throws Exception {
		ClientPreferences mockCapabilies = mock(ClientPreferences.class);
		when(mockCapabilies.isResolveAdditionalTextEditsSupport()).thenReturn(true);
		when(preferenceManager.getClientPreferences()).thenReturn(mockCapabilies);
		//@formatter:off
		ICompilationUnit unit = getWorkingCopy(
				"src/java/Foo.java",
				"public class Foo {\n"+
						"	void foo() {\n"+
						"		HashMa\n"+
						"	}\n"+
				"}\n");
		//@formatter:on
		CompletionList list = requestCompletions(unit, "HashMa");
		assertNotNull(list);
		assertFalse("No proposals were found",list.getItems().isEmpty());
		CompletionItem ci = list.getItems().get(0);
		assertNull(ci.getAdditionalTextEdits());
		assertEquals("HashMap - java.util", ci.getLabel());
		CompletionItem resolvedItem = server.resolveCompletionItem(ci).join();
		List<TextEdit> additionalEdits = resolvedItem.getAdditionalTextEdits();
		assertNotNull(additionalEdits);
		assertEquals(1, additionalEdits.size());
		assertEquals("import java.util.HashMap;\n\n", additionalEdits.get(0).getNewText());
	}

	@Test
	public void testCompletion_Enum() throws JavaModelException {
		ICompilationUnit unit = getWorkingCopy("src/org/sample/Test.java",
		//@formatter:off
				"package org.sample;\n"
			+	"public class Test {\n\n"
			+   "   enum Zenum{A,B}\n"
			+	"	void test() {\n\n"
			+	"      Zenu\n"
			+	"	}\n"
			+	"}\n");
		//@formatter:on
		CompletionList list = requestCompletions(unit, "   Zenu");
		assertNotNull(list);
		assertEquals(1, list.getItems().size());
		CompletionItem item = list.getItems().get(0);
		assertEquals(CompletionItemKind.Enum, item.getKind());
		assertEquals("Zenum", item.getInsertText());
	}

	@Test
	public void testCompletion_Constant() throws JavaModelException {
		ICompilationUnit unit = getWorkingCopy("src/org/sample/Test.java",
		//@formatter:off
				"package org.sample;\n"
			+	"public class Test {\n\n"
			+	"	void test() {\n\n"
			+	"		char c = java.io.File.pathSeparatorC \n"
			+	"	}\n"
			+	"}\n");
		//@formatter:on
		CompletionList list = requestCompletions(unit, "pathSeparatorC");
		assertNotNull(list);
		assertEquals(1, list.getItems().size());
		CompletionItem item = list.getItems().get(0);
		assertEquals(CompletionItemKind.Constant, item.getKind());
		assertEquals("pathSeparatorChar", item.getInsertText());
	}

	@Test
	public void testCompletion_FilterTypes() throws JavaModelException {
		ICompilationUnit unit = getWorkingCopy("src/org/sample/Test.java",
		//@formatter:off
				"package org.sample;\n"
			+	"public class Test {\n\n"
			+	"	void test() {\n\n"
			+	"		List l; \n"
			+	"	}\n"
			+	"}\n");
		//@formatter:on
		CompletionList list = requestCompletions(unit, "List");
		assertNotNull(list);
		assertTrue(list.getItems().stream().anyMatch(i -> "java.util.List".equals(i.getDetail())));
		//@formatter:off
		boolean present = list.getItems()
				.stream()
				.filter(item -> "List - java.util".equals(item.getLabel()))
				.findFirst()
				.isPresent();
		//@formatter:off
		assertTrue("The 'List - java.util' proposal hasn't been found", present);
		try {
			List<String> filteredTypes = new ArrayList<>();
			filteredTypes.add("java.util.*");
			PreferenceManager.getPrefs(null).setFilteredTypes(filteredTypes);
			list = requestCompletions(unit, "List");
			assertNotNull(list);
			assertFalse(list.getItems().stream().anyMatch(i -> "java.util.List".equals(i.getDetail())));
		} finally {
			PreferenceManager.getPrefs(null).setFilteredTypes(Collections.emptyList());
		}
	}

	@Test
	public void testCompletion_FilterPackages() throws JavaModelException {
		ICompilationUnit unit = getWorkingCopy("src/org/sample/Test.java",
				//@formatter:off
				"package org.sample;\n\n"
				+	"public class Test {\n"
				+	"	void test() {\n"
				+	"		java.util \n"
				+	"	}\n"
				+	"}\n");
		try {
			List<String> filteredTypes = new ArrayList<>();
			filteredTypes.add("java.util.*");
			PreferenceManager.getPrefs(null).setFilteredTypes(filteredTypes);

			CompletionList list = requestCompletions(unit, "java.util");
			assertNotNull(list);
			List<String> packages = list.getItems().stream().map(i -> i.getLabel()).collect(Collectors.toList());
			assertTrue(packages.size() > 1);
			assertEquals("java.util", packages.get(0));
		} finally {
			PreferenceManager.getPrefs(null).setFilteredTypes(Collections.emptyList());
		}
	}

	@Test
	public void testCompletion_FilterTypesKeepMethods() throws JavaModelException {
		ICompilationUnit unit = getWorkingCopy("src/org/sample/Test.java",
		//@formatter:off
				"package org.sample;\n"
			+	"public class Test {\n\n"
			+	"	void test() {\n\n"
			+	"		java.util.List l; \n"
			+   "       l.clea \n"
			+	"	}\n"
			+	"}\n");
		//@formatter:on
		try {
			List<String> filteredTypes = new ArrayList<>();
			filteredTypes.add("java.util.*");
			PreferenceManager.getPrefs(null).setFilteredTypes(filteredTypes);

			CompletionList list = requestCompletions(unit, "l.clea");
			assertNotNull(list);
			assertEquals("Missing completion", 1, list.getItems().size());
			assertEquals("clear() : void", list.getItems().get(0).getLabel());
		} finally {
			PreferenceManager.getPrefs(null).setFilteredTypes(Collections.emptyList());
		}
	}

	@Test
	public void testCompletion_FilterTypesKeepMethods2() throws JavaModelException {
		ICompilationUnit unit = getWorkingCopy("src/org/sample/Test.java",
		//@formatter:off
				"package org.sample;\n"
			+	"import java.util.List;"
			+	"public class Test {\n\n"
			+	"	void test() {\n\n"
			+	"		List l; \n"
			+	"		l.clea \n"
			+	"	}\n"
			+	"}\n");
		//@formatter:on
		try {
			List<String> filteredTypes = new ArrayList<>();
			filteredTypes.add("java.util.*");
			PreferenceManager.getPrefs(null).setFilteredTypes(filteredTypes);

			CompletionList list = requestCompletions(unit, "l.clea");
			assertNotNull(list);
			assertEquals("Missing completion", 1, list.getItems().size());
			assertEquals("clear() : void", list.getItems().get(0).getLabel());
		} finally {
			PreferenceManager.getPrefs(null).setFilteredTypes(Collections.emptyList());
		}
	}

	@Test
	public void testCompletion_FilterMethodsWhenTypeIsMissing() throws JavaModelException {
		ICompilationUnit unit = getWorkingCopy("src/org/sample/Test.java",
		//@formatter:off
				"package org.sample;\n"
			+	"public class Test {\n\n"
			+	"	void test() {\n\n"
			+	"		List l; \n"
			+	"		l.clea \n"
			+	"	}\n"
			+	"}\n");
		//@formatter:on
		try {
			List<String> filteredTypes = new ArrayList<>();
			filteredTypes.add("java.util.*");
			PreferenceManager.getPrefs(null).setFilteredTypes(filteredTypes);

			CompletionList list = requestCompletions(unit, "l.clea");
			assertNotNull(list);
			assertEquals(0, list.getItems().size());
		} finally {
			PreferenceManager.getPrefs(null).setFilteredTypes(Collections.emptyList());
		}
	}

	@Test
	public void testCompletion_IgnoreTypeFilterWhenImported1() throws JavaModelException {
		ICompilationUnit unit = getWorkingCopy("src/org/sample/Test.java",
		//@formatter:off
				"package org.sample;\n"
			+	"import java.util.List;"
			+	"public class Test {\n\n"
			+	"	void test() {\n\n"
			+	"		List\n"
			+	"	}\n"
			+	"}\n");
		//@formatter:on
		try {
			List<String> filteredTypes = new ArrayList<>();
			filteredTypes.add("java.util.*");
			PreferenceManager.getPrefs(null).setFilteredTypes(filteredTypes);

			CompletionList list = requestCompletions(unit, "		List");
			assertNotNull(list);
			assertTrue(list.getItems().stream().anyMatch(t -> "java.util.List".equals(t.getDetail())));
		} finally {
			PreferenceManager.getPrefs(null).setFilteredTypes(Collections.emptyList());
		}
	}

	@Test
	public void testCompletion_IgnoreTypeFilterWhenImported2() throws JavaModelException {
		ICompilationUnit unit = getWorkingCopy("src/org/sample/Test.java",
		//@formatter:off
				"package org.sample;\n"
			+	"import java.util.*;"
			+	"public class Test {\n\n"
			+	"	void test() {\n\n"
			+	"		List\n"
			+	"	}\n"
			+	"}\n");
		//@formatter:on
		try {
			List<String> filteredTypes = new ArrayList<>();
			filteredTypes.add("java.util.*");
			PreferenceManager.getPrefs(null).setFilteredTypes(filteredTypes);

			CompletionList list = requestCompletions(unit, "		List");
			assertNotNull(list);
			assertTrue(list.getItems().stream().anyMatch(t -> "java.util.List".equals(t.getDetail())));
		} finally {
			PreferenceManager.getPrefs(null).setFilteredTypes(Collections.emptyList());
		}
	}

	@Test
	public void testCompletion_IgnoreTypeFilterWhenImported3() throws JavaModelException {
		ICompilationUnit unit = getWorkingCopy("src/org/sample/Test.java",
		//@formatter:off
				"package org.sample;\n"
			+	"import static java.util.List.*;"
			+	"public class Test {\n\n"
			+	"	void test() {\n\n"
			+	"		List\n"
			+	"	}\n"
			+	"}\n");
		//@formatter:on
		try {
			List<String> filteredTypes = new ArrayList<>();
			filteredTypes.add("java.util.*");
			PreferenceManager.getPrefs(null).setFilteredTypes(filteredTypes);

			CompletionList list = requestCompletions(unit, "		List");
			assertNotNull(list);
			assertTrue(list.getItems().stream().anyMatch(t -> "java.util.List".equals(t.getDetail())));
		} finally {
			PreferenceManager.getPrefs(null).setFilteredTypes(Collections.emptyList());
		}
	}

	@Test
	public void testCompletion_IgnoreTypeFilterWhenImported4() throws JavaModelException {
		ICompilationUnit unit = getWorkingCopy("src/org/sample/Test.java",
		//@formatter:off
				"package org.sample;\n"
			+	"import static java.util.List.DUMMY;"
			+	"public class Test {\n\n"
			+	"	void test() {\n\n"
			+	"		List\n"
			+	"	}\n"
			+	"}\n");
		//@formatter:on
		try {
			List<String> filteredTypes = new ArrayList<>();
			filteredTypes.add("java.util.*");
			PreferenceManager.getPrefs(null).setFilteredTypes(filteredTypes);

			CompletionList list = requestCompletions(unit, "		List");
			assertNotNull(list);
			assertTrue(list.getItems().stream().anyMatch(t -> "java.util.List".equals(t.getDetail())));
		} finally {
			PreferenceManager.getPrefs(null).setFilteredTypes(Collections.emptyList());
		}
	}

	@Test
	public void testCompletion_IgnoreTypeFilterWhenImported5() throws JavaModelException {
		ICompilationUnit unit = getWorkingCopy("src/org/sample/Test.java", """
				package org.sample;
				import java.util.List;
				public class Test {
				}""");
		try {
			List<String> filteredTypes = new ArrayList<>();
			filteredTypes.add("java.util.*");
			PreferenceManager.getPrefs(null).setFilteredTypes(filteredTypes);

			CompletionList list = requestCompletions(unit, "java.util.");
			assertNotNull(list);
		} finally {
			PreferenceManager.getPrefs(null).setFilteredTypes(Collections.emptyList());
		}
	}

	@Test
	public void testCompletion_IgnoreTypeFilterWhenImported6() throws JavaModelException {
		ICompilationUnit unit = getWorkingCopy("src/org/sample/Test.java", """
				package org.sample;
				import java.util.
				public class Test {
				}""");
		try {
			List<String> filteredTypes = new ArrayList<>();
			filteredTypes.add("java.util.*");
			PreferenceManager.getPrefs(null).setFilteredTypes(filteredTypes);

			CompletionList list = requestCompletions(unit, "java.util.");
			assertNotNull(list);
		} finally {
			PreferenceManager.getPrefs(null).setFilteredTypes(Collections.emptyList());
		}
	}

	@Test
	public void testCompletion_autoAddStaticImportAsFavoriteImport() throws JavaModelException {
		ICompilationUnit unit = getWorkingCopy("src/org/sample/Test.java", """
				package org.sample;
				import static java.util.Arrays.sort;
				public class Test {
					public static void main(String[] args) {
						asList
					}
				}""");
		String[] oldFavorites = JavaLanguageServerPlugin.getPreferencesManager().getPreferences().getJavaCompletionFavoriteMembers();
		Set<String> oldStaticImports = new LinkedHashSet<>(Preferences.DISCOVERED_STATIC_IMPORTS);
		try {
			JavaLanguageServerPlugin.getPreferencesManager().getPreferences().setJavaCompletionFavoriteMembers(Arrays.asList("org.junit.Assert.*"));
			Preferences.DISCOVERED_STATIC_IMPORTS.clear();
			CompletionList list = requestCompletions(unit, "asList");
			assertNotNull(list);
			assertFalse(list.getItems().isEmpty());
			CompletionItem item = list.getItems().stream().filter(i -> i.getDetail() != null && i.getDetail().startsWith("java.util.Arrays.asList(")).collect(Collectors.toList()).get(0);
			assertNotNull(item);
		} finally {
			JavaLanguageServerPlugin.getPreferencesManager().getPreferences().setJavaCompletionFavoriteMembers(Arrays.asList(oldFavorites));
			Preferences.DISCOVERED_STATIC_IMPORTS.addAll(oldStaticImports);
			PreferenceManager.getPrefs(null).setFilteredTypes(Collections.emptyList());
		}
	}

	@Test
	public void testCompletion_InvalidJavadoc() throws Exception {
		importProjects("maven/aspose");
		IProject project = null;
		ICompilationUnit unit = null;
		try {
			project = ResourcesPlugin.getWorkspace().getRoot().getProject("aspose");
			IJavaProject javaProject = JavaCore.create(project);
			unit = (ICompilationUnit) javaProject.findElement(new Path("org/sample/TestJavadoc.java"));
			unit.becomeWorkingCopy(null);
			CompletionList list = requestCompletions(unit, "doc.");
			CompletionItem ci = list.getItems().stream().filter(item -> item.getLabel().equals("accept(DocumentVisitor visitor) : boolean")).findFirst().orElse(null);
			assertNotNull(ci);
		} finally {
			if (unit != null) {
				unit.discardWorkingCopy();
			}
		}
	}

	@Test
	public void testCompletion_ConstantDefaultValue() throws JavaModelException {
		ClientPreferences mockCapabilies = Mockito.mock(ClientPreferences.class);
		Mockito.when(preferenceManager.getClientPreferences()).thenReturn(mockCapabilies);
		Mockito.when(mockCapabilies.isCompletionResolveDocumentSupport()).thenReturn(true);
		ICompilationUnit unit = getWorkingCopy("src/org/sample/Test.java",
		//@formatter:off
				"package org.sample;\n"
			+	"public class Test {\n\n"
			+	"	private int one = IConstantDefault.\n"
			+	"	@IConstantDefault()\n"
			+	"	void test() {\n"
			+	"	}\n"
			+	"}\n");
		//@formatter:on
		CompletionList list = requestCompletions(unit, "IConstantDefault.");
		assertNotNull(list);
		assertEquals(3, list.getItems().size());
		CompletionItem ci = list.getItems().get(0);
		assertEquals(CompletionItemKind.Constant, ci.getKind());
		assertEquals("ONE : int", ci.getLabel());
		CompletionItem resolvedItem = server.resolveCompletionItem(ci).join();
		assertEquals(CompletionItemKind.Constant, resolvedItem.getKind());
		String documentation = resolvedItem.getDocumentation().getLeft();
		assertEquals("Value: 1", documentation);

		ci = list.getItems().get(1);
		assertEquals(CompletionItemKind.Constant, ci.getKind());
		assertEquals("TEST : double", ci.getLabel());

		list = requestCompletions(unit, "@IConstantDefault(");
		assertNotNull(list);
		assertEquals(1, list.getItems().size());
		ci = list.getItems().get(0);
		assertEquals(CompletionItemKind.Property, ci.getKind());
		assertEquals("someMethod : String", ci.getLabel());
		resolvedItem = server.resolveCompletionItem(ci).join();
		assertEquals(CompletionItemKind.Property, resolvedItem.getKind());
		documentation = resolvedItem.getDocumentation().getLeft();
		assertEquals("Default: \"test\"", documentation);
	}

	// See https://github.com/redhat-developer/vscode-java/issues/1258
	@Test
	public void testCompletion_javadocOriginal() throws JavaModelException {
		ClientPreferences mockCapabilies = Mockito.mock(ClientPreferences.class);
		Mockito.when(preferenceManager.getClientPreferences()).thenReturn(mockCapabilies);
		Mockito.when(mockCapabilies.isCompletionResolveDocumentSupport()).thenReturn(true);
		ICompilationUnit unit = getWorkingCopy("src/org/sample/Test.java",
		//@formatter:off
				"package org.sample;\n"
			+	"import java.util.List;\n"
			+	"import java.util.LinkedList;\n"
			+	"public class Test {\n\n"
			+	"	void test() {\n"
			+	"		MyList<String> l = new LinkedList<>();\n"
			+	"		l.add\n"
			+	"	}\n"
			+	"}\n");
		//@formatter:on
		CompletionList list = requestCompletions(unit, "l.add");
		assertNotNull(list);
		assertEquals(4, list.getItems().size());
		CompletionItem ci = list.getItems().get(0);
		assertEquals(CompletionItemKind.Method, ci.getKind());
		assertEquals("add(String e) : boolean", ci.getLabel());
		CompletionItem resolvedItem = server.resolveCompletionItem(ci).join();
		assertEquals(CompletionItemKind.Method, resolvedItem.getKind());
		String documentation = resolvedItem.getDocumentation().getLeft();
		assertEquals(" Test ", documentation);
	}

	// See https://github.com/redhat-developer/vscode-java/issues/2034
	@Test
	public void testCompletion_Anonymous() throws JavaModelException {
		when(preferenceManager.getClientPreferences().isCompletionItemLabelDetailsSupport()).thenReturn(true);
		ICompilationUnit unit = getWorkingCopy("src/org/sample/Test.java",
		//@formatter:off
					"package org.sample;\n"
				+	"import java.util.Arrays;\n"
				+	"public class Test {\n\n"
				+	"	public static void main(String[] args) {\n"
				+	"		new Runnable() {\n"
				+	"			@Override\n"
				+	"			public void run() {\n"
				+	"				boolean equals = Arrays.equals(new Object[0], new Object[0]);\n"
				+	"			}\n"
				+	"		};\n"
				+	"	}\n"
				+	"}\n");
			//@formatter:on
		CompletionList list = requestCompletions(unit, "= A");
		assertNotNull(list);
		assertTrue(list.getItems().size() > 0);
		CompletionItem ci = list.getItems().stream().filter(item -> item.getLabel().equals("Arrays")).findFirst().orElse(null);
		// createTypeProposalLabel
		assertEquals("Arrays", ci.getLabel());
		assertNull(ci.getLabelDetails().getDetail());
		assertEquals("java.util", ci.getLabelDetails().getDescription());

		assertEquals(CompletionItemKind.Class, ci.getKind());
		assertEquals("java.util.Arrays", ci.getDetail());
	}

	@Test
	public void testCompletion_Nullable() throws Exception {
		importProjects("eclipse/testnullable");
		IProject proj = ProjectUtils.getProject("testnullable");
		assertTrue(ProjectUtils.isJavaProject(proj));
		IFile file = proj.getFile("/src/org/sample/Main.java");
		ICompilationUnit unit = JavaCore.createCompilationUnitFrom(file);
		CompletionList list = requestCompletions(unit, "ru");
		assertNotNull(list);
		CompletionItem ci = list.getItems().stream().filter(item -> item.getLabel().equals("run() : void")).findFirst().orElse(null);
		assertNotNull(ci);
		assertEquals("public void run() {};", ci.getTextEdit().getLeft().getNewText());
	}

	@Test
	public void testCompletion_Deprecated() throws Exception {
		when(preferenceManager.getClientPreferences().isCompletionItemTagSupported()).thenReturn(true);

		ICompilationUnit unit = getWorkingCopy("src/org/sample/Test.java", String.join("\n",
			"public class Main {",
			"	@Deprecated",
			"	public static final class DeprecatedClass {}",

			"	DeprecatedCl",

			"	/**",
			"	 * @deprecated",
			"	 */",
			"	public static void deprecatedMethod() {",
			"		deprecatedMe",
			"	}",

			"	public static void notDeprecated() {",
			"		notDepr",
			"	}",
			"}"
		));

		CompletionItem deprecatedClass = requestCompletions(unit, "\tDeprecatedCl").getItems().get(0);
		assertNotNull(deprecatedClass);
		assertEquals(CompletionItemKind.Class, deprecatedClass.getKind());
		assertNotNull(deprecatedClass.getTags());
		assertTrue("Should have deprecated tag", deprecatedClass.getTags().contains(CompletionItemTag.Deprecated));

		CompletionItem deprecatedMethod = requestCompletions(unit, "\t\tdeprecatedMe").getItems().get(0);
		assertNotNull(deprecatedMethod);
		assertEquals(CompletionItemKind.Method, deprecatedMethod.getKind());
		assertNotNull(deprecatedMethod.getTags());
		assertTrue("Should have deprecated tag", deprecatedMethod.getTags().contains(CompletionItemTag.Deprecated));

		CompletionItem notDeprecated = requestCompletions(unit, "\t\tnotDepr").getItems().get(0);
		assertNotNull(notDeprecated);
		assertEquals(CompletionItemKind.Method, notDeprecated.getKind());
		if (notDeprecated.getTags() != null) {
			assertFalse("Should not have deprecated tag", notDeprecated.getTags().contains(CompletionItemTag.Deprecated));
		}
	}

	@Test
	public void testCompletion_Deprecated_property() throws Exception {
		when(preferenceManager.getClientPreferences().isCompletionItemTagSupported()).thenReturn(false);

		ICompilationUnit unit = getWorkingCopy("src/org/sample/Test.java", String.join("\n",
			"public class Main {",
			"	@Deprecated",
			"	public static final class DeprecatedClass {}",

			"	DeprecatedCl",
			"}"
		));

		CompletionItem deprecatedClass = requestCompletions(unit, "\tDeprecatedCl").getItems().get(0);
		assertNotNull(deprecatedClass);
		assertEquals(CompletionItemKind.Class, deprecatedClass.getKind());
		assertNotNull(deprecatedClass.getDeprecated());
		assertTrue("Should be deprecated", deprecatedClass.getDeprecated());
	}

	@Test
	public void testCompletion_Lambda() throws Exception {
		when(preferenceManager.getClientPreferences().isCompletionItemLabelDetailsSupport()).thenReturn(true);
		ICompilationUnit unit = getWorkingCopy("src/org/sample/Test.java", String.join("\n",
			"import java.util.function.Consumer;",
			"public class Test {",
			"	public static void main(String[] args) {",
			"		Consumer c = ",
			"	}",
			"}"
		));

		CompletionList list = requestCompletions(unit, "c = ");
		assertNotNull(list);
		CompletionItem lambda = list.getItems().stream()
				.filter(item -> (item.getLabel().matches("\\(Object \\w+\\) ->") && item.getKind() == CompletionItemKind.Method))
				.findFirst().orElse(null);
		assertNotNull(lambda);
		assertTrue(lambda.getTextEdit().getLeft().getNewText().matches("\\$\\{1:\\w+\\} -> \\$\\{0\\}"));

		try {
			assertEquals("(Object t) ->", lambda.getLabel());
		} catch (ComparisonFailure e) {
			// In case the JDK has no sources
			assertEquals("(Object arg0) ->", lambda.getLabel());
		}
		assertNull(lambda.getLabelDetails().getDetail());
		assertEquals(lambda.getLabelDetails().getDescription(), "void");
	}

	@Test
	public void testCompletion_afterNew() throws Exception {
		ICompilationUnit unit = getWorkingCopy("src/org/sample/Test.java", String.join("\n",
			"public class Test {",
			"	public static void main(String[] args) {",
			"		String s = new ",
			"	}",
			"}"
		));
		try {
			int[] loc = findCompletionLocation(unit, "new ");
			CompletionParams params = JsonMessageHelper.getParams(createCompletionRequest(unit, loc[0], loc[1]));
			CompletionContext context = new CompletionContext(CompletionTriggerKind.TriggerCharacter, " ");
			params.setContext(context);
			CoreASTProvider.getInstance().setActiveJavaElement(unit);
			CompletionList list = server.completion(params).join().getRight();
			assertTrue(list.isIncomplete());
			assertTrue(list.getItems().get(0).getLabel().startsWith("String("));
		} catch (Exception e) {
			fail("Unexpected exception " + e);
		} finally {
			unit.discardWorkingCopy();
		}
	}

	@Test
	public void testCompletion_IgnoreSpaceWithoutNew() throws Exception {
		ICompilationUnit unit = getWorkingCopy("src/org/sample/Test.java", String.join("\n",
			"public class Test {",
			"	public static void main(String[] args) {",
			"		String s ",
			"	}",
			"}"
		));
		try {
			int[] loc = findCompletionLocation(unit, "String s ");
			CompletionParams params = JsonMessageHelper.getParams(createCompletionRequest(unit, loc[0], loc[1]));
			CompletionContext context = new CompletionContext(CompletionTriggerKind.TriggerCharacter, " ");
			params.setContext(context);
			CoreASTProvider.getInstance().setActiveJavaElement(unit);
			CompletionList list = server.completion(params).join().getRight();
			assertTrue(list.getItems().isEmpty());
		} catch (Exception e) {
			fail("Unexpected exception " + e);
		} finally {
			unit.discardWorkingCopy();
		}
	}

	@Test
	public void testCompletion_IgnoreVariableWithNewPostfix() throws Exception {
		ICompilationUnit unit = getWorkingCopy("src/org/sample/Test.java", String.join("\n",
			"public class Test {",
			"	public static void main(String[] args) {",
			"		String val_new;",
			"		new String(val_new );",
			"	}",
			"}"
		));
		try {
			int[] loc = findCompletionLocation(unit, "val_new ");
			CompletionParams params = JsonMessageHelper.getParams(createCompletionRequest(unit, loc[0], loc[1]));
			CompletionContext context = new CompletionContext(CompletionTriggerKind.TriggerCharacter, " ");
			params.setContext(context);
			CoreASTProvider.getInstance().setActiveJavaElement(unit);
			CompletionList list = server.completion(params).join().getRight();
			assertTrue(list.getItems().isEmpty());
		} catch (Exception e) {
			fail("Unexpected exception " + e);
		} finally {
			unit.discardWorkingCopy();
		}
	}

	@Test
	public void testCompletion_IgnoreStringLiteralNew() throws Exception {
		ICompilationUnit unit = getWorkingCopy("src/org/sample/Test.java", String.join("\n",
			"public class Test {",
			"	public static void main(String[] args) {",
			"		String s = \"new \";",
			"	}",
			"}"
		));
		try {
			int[] loc = findCompletionLocation(unit, "\"new ");
			CompletionParams params = JsonMessageHelper.getParams(createCompletionRequest(unit, loc[0], loc[1]));
			CompletionContext context = new CompletionContext(CompletionTriggerKind.TriggerCharacter, " ");
			params.setContext(context);
			CoreASTProvider.getInstance().setActiveJavaElement(unit);
			CompletionList list = server.completion(params).join().getRight();
			assertTrue(list.getItems().isEmpty());
		} catch (Exception e) {
			fail("Unexpected exception " + e);
		} finally {
			unit.discardWorkingCopy();
		}
	}

	// https://github.com/redhat-developer/vscode-java/issues/2534
	@Test
	public void testCompletion_QualifiedName() throws Exception {
		ICompilationUnit unit = getWorkingCopy("src/org/sample/Test.java", String.join("\n",
		//@formatter:off
				"package org.sample",
				"public class Test {",
				"	public static void main(String[] args) {",
				"		 java.util.List<String> list = new Array",
				"	}",
				"}"));
				//@formatter:on
		CompletionList list = requestCompletions(unit, "new Array");
		assertFalse(list.getItems().isEmpty());
		assertEquals("ArrayList<>()", list.getItems().get(0).getTextEdit().getLeft().getNewText());
	}

	// https://github.com/eclipse/eclipse.jdt.ls/issues/2147
	@Test
	public void testCompletion_QualifiedName2() throws Exception {
		ICompilationUnit unit = getWorkingCopy("src/org/sample/Test.java", String.join("\n",
		//@formatter:off
				"package org.sample",
				"public class Test {",
				"	public static void main(String[] args) {",
				"		  List<String> list = new java.util.ArrayL",
				"	}",
				"}"));
				//@formatter:on
		CompletionList list = requestCompletions(unit, "ArrayL");
		assertFalse(list.getItems().isEmpty());
		assertTrue(list.getItems().get(0).getFilterText().startsWith("java.util.ArrayList"));
	}

	@Test
	public void testCompletion_withConflictingTypeNames() throws Exception {
		ClientPreferences mockCapabilies = Mockito.mock(ClientPreferences.class);
		Mockito.when(preferenceManager.getClientPreferences()).thenReturn(mockCapabilies);
		Mockito.when(mockCapabilies.isResolveAdditionalTextEditsSupport()).thenReturn(true);
		getWorkingCopy("src/java/List.java",
			"package util;\n" +
			"public class List {\n" +
			"}\n");
		ICompilationUnit unit = getWorkingCopy(
				"src/java/Foo.java",
				"package util;\n" +
				"public class Foo {\n"+
						"	void foo() {\n"+
						" 		Object list = new List();\n" +
						"		List \n"+
						"	}\n"+
				"}\n");
		CoreASTProvider.getInstance().setActiveJavaElement(unit);
		CoreASTProvider.getInstance().getAST(unit, CoreASTProvider.WAIT_YES, monitor);

		CompletionList list = requestCompletions(unit, "List", unit.getSource().indexOf("List()") + 6);
		assertNotNull(list);
		assertFalse("No proposals were found",list.getItems().isEmpty());

		List<CompletionItem> items = list.getItems().stream().filter(p -> "java.util.List".equals(p.getDetail()))
			.collect(Collectors.toList());
		assertFalse("java.util.List not found",items.isEmpty());
		CompletionItem resolved = server.resolveCompletionItem(list.getItems().get(0)).join();
		assertEquals("java.util.List", resolved.getTextEdit().getLeft().getNewText());
	}

	@Test
	public void testCompletion_lambdaWithNoParam() throws Exception{
		ICompilationUnit unit = getWorkingCopy(
				"src/java/Foo.java",
				"public class Foo {\n"+
						"	void foo() {\n"+
						" 		Runnable r = \n" +
						"	}\n"+
				"}\n");

		CompletionList list = requestCompletions(unit, "= ");
		assertNotNull(list);
		assertFalse("No proposals were found",list.getItems().isEmpty());

		List<CompletionItem> items = list.getItems().stream().filter(p -> p.getLabel() != null && p.getLabel().contains("->"))
			.collect(Collectors.toList());
		assertFalse("Lambda not found",items.isEmpty());
		assertTrue(items.get(0).getTextEdit().getLeft().getNewText().matches("\\(\\) -> \\$\\{0\\}"));
	}

	@Test
	public void testCompletion_lambdaWithMultipleParams() throws Exception{
		ICompilationUnit unit = getWorkingCopy(
				"src/java/Foo.java",
				"public class Foo {\n"+
						"	void foo() {\n"+
						" 		java.util.function.BiConsumer<Integer, Long> bc = \n" +
						"	}\n"+
				"}\n");

		CompletionList list = requestCompletions(unit, "= ");
		assertNotNull(list);
		assertFalse("No proposals were found",list.getItems().isEmpty());

		List<CompletionItem> items = list.getItems().stream().filter(p -> p.getLabel() != null && p.getLabel().contains("->"))
			.collect(Collectors.toList());
		assertFalse("Lambda not found",items.isEmpty());
		assertTrue(items.get(0).getTextEdit().getLeft().getNewText().matches("\\(\\$\\{1:\\w+\\}\\, \\$\\{2:\\w+\\}\\) -> \\$\\{0\\}"));
	}

	@Test
	public void testCompletion_MatchCaseOff() throws Exception {
		ICompilationUnit unit = getWorkingCopy("src/org/sample/Test.java", String.join("\n",
			//@formatter:off
					"package org.sample",
					"public class Test {",
					"	public static void main(String[] args) {",
					"		i",
					"	}",
					"}"));
					//@formatter:on
			CompletionList list = requestCompletions(unit, "		i");
			assertFalse(list.getItems().isEmpty());
			boolean hasUpperCase = list.getItems().stream()
				.anyMatch(t -> Character.isUpperCase(t.getLabel().charAt(0)));
			assertTrue(hasUpperCase);
	}

	@Test
	public void testCompletion_MatchCaseFirstLetter() throws Exception {
		try {
			preferenceManager.getPreferences().setCompletionMatchCaseMode(CompletionMatchCaseMode.FIRSTLETTER);
			ICompilationUnit unit = getWorkingCopy("src/org/sample/Test.java", String.join("\n",
			//@formatter:off
					"package org.sample",
					"public class Test {",
					"	public static void main(String[] args) {",
					"		i",
					"	}",
					"}"));
					//@formatter:on
			CompletionList list = requestCompletions(unit, "		i");
			assertFalse(list.getItems().isEmpty());
			boolean hasUpperCase = list.getItems().stream()
				.anyMatch(t -> t.getKind() != CompletionItemKind.Snippet && Character.isUpperCase(t.getLabel().charAt(0)));
			assertFalse(hasUpperCase);
		} finally {
			preferenceManager.getPreferences().setCompletionMatchCaseMode(CompletionMatchCaseMode.OFF);
		}
	}

	@Test
	public void testCompletion_MatchCaseFirstLetterForConstructor() throws Exception {
		try {
			preferenceManager.getPreferences().setCompletionMatchCaseMode(CompletionMatchCaseMode.FIRSTLETTER);
			ICompilationUnit unit = getWorkingCopy("src/org/sample/Test.java", String.join("\n",
			//@formatter:off
					"package org.sample",
					"public class Test {",
					"	public static void main(String[] args) {",
					"		String a = new S",
					"	}",
					"}"));
					//@formatter:on
			CompletionList list = requestCompletions(unit, "new S");
			assertFalse(list.getItems().isEmpty());
			assertTrue(list.getItems().stream()
				.allMatch(t -> Character.isUpperCase(t.getLabel().charAt(0))));
			assertTrue(list.getItems().stream()
				.anyMatch(t -> t.getLabel().startsWith("String")));
		} finally {
			preferenceManager.getPreferences().setCompletionMatchCaseMode(CompletionMatchCaseMode.OFF);
		}
	}

	// https://github.com/eclipse-jdtls/eclipse.jdt.ls/issues/2884
	@Test
	public void testCompletion_MatchCaseFirstLetterForMethodOverride() throws Exception {
		preferenceManager.getPreferences().setCompletionMatchCaseMode(CompletionMatchCaseMode.FIRSTLETTER);
		ICompilationUnit unit = getWorkingCopy("src/org/sample/Test.java", String.join("\n",
		//@formatter:off
				"package org.sample",
				"public class Test {",
				"	public void testMethod(int a, int b){}",
				"	public void testMethod(int b){}",
				"}",
				"class TestOverride extends Test{",
				"	t",
				"}"));
				//@formatter:on
		CompletionList list = requestCompletions(unit, "t");
		assertFalse(list.getItems().isEmpty());
		assertTrue(list.getItems().get(0).getLabel().startsWith("testMethod(int b"));
		assertTrue(list.getItems().get(1).getLabel().startsWith("testMethod(int a"));
	}

	// https://github.com/eclipse/eclipse.jdt.ls/issues/2376
	@Test
	public void testCompletion_selectSnippetItem() throws Exception {
		ICompilationUnit unit = getWorkingCopy(
				"src/java/Foo.java",
				"public class Foo {\n"+
						"	void foo() {\n"+
						" 		sysout\n" +
						"	}\n"+
				"}\n");

		CompletionList list = requestCompletions(unit, "sysout");
		CompletionItem completionItem = list.getItems().get(0);
		Map<String, String> data = JSONUtility.toModel(completionItem.getData(), Map.class);
		long requestId = Long.parseLong(data.get("rid"));
		assertNotNull(CompletionResponses.get(requestId));
	}

	// https://github.com/eclipse/eclipse.jdt.ls/issues/2387
	@Test
	public void testCompletion_multiLineRange() throws Exception {
		when(preferenceManager.getClientPreferences().isCompletionInsertReplaceSupport()).thenReturn(true);
		//@formatter:off
			ICompilationUnit unit = getWorkingCopy(
					"src/java/Foo.java",
					"public class Foo {\n"
					+ "    public static void main(String[] args) {\n"
					+ "        if (true) {\n"
					+ "            java.util.List<String> list = new java.util.ArrayList<>();\n"
					+ "            list.add\n"
					+ "            (\"test\"\n"
					+ "            );\n"
					+ "        }\n"
					+ "    }\n"
					+ "}\n");
		//@formatter:on
		CompletionList list = requestCompletions(unit, "list.");
		List<CompletionItem> completionItems = list.getItems().stream().filter(i -> i.getLabel().startsWith("add")).collect(Collectors.toList());
		assertTrue(completionItems.size() > 0);
		for (CompletionItem completionItem: completionItems) {
			assertNotNull(completionItem);
			Either<TextEdit, InsertReplaceEdit> textEdit = completionItem.getTextEdit();
			assertNotNull(textEdit);
			Range replace = textEdit.isRight() ? textEdit.getRight().getReplace() : null;
			if (replace == null) {
				replace = textEdit.isLeft() ? textEdit.getLeft().getRange() : (textEdit.getRight().getInsert() != null ? textEdit.getRight().getInsert() : textEdit.getRight().getReplace());
			}
			assertEquals(replace.getStart().getLine(), replace.getEnd().getLine());
		}
	}

	@Test
	public void testCompletion_syserrSnipper() throws JavaModelException {
		preferenceManager.getPreferences().setCompletionLazyResolveTextEditEnabled(false);
		ICompilationUnit unit = getWorkingCopy(
		//@formatter:off
		"src/java/Foo.java",
		"""
		public class Foo {
			void f() {
				syser
			}
		};
		""");
		//@formatter:on
		CompletionList list = requestCompletions(unit, "syser");
		assertNotNull(list);
		assertEquals(1, list.getItems().size());
		CompletionItem item = list.getItems().get(0);
		assertEquals("syserr", item.getLabel());
		assertEquals(new Range(new Position(2, 2), new Position(2, 7)), item.getTextEdit().map(TextEdit::getRange, InsertReplaceEdit::getReplace));
	}

	@Test
	public void testCompletion_printSnippets() throws JavaModelException {
		preferenceManager.getPreferences().setCompletionLazyResolveTextEditEnabled(false);
		ICompilationUnit unit = getWorkingCopy(
		//@formatter:off
		"src/java/Foo.java",
		"""
		public class Foo {
			void f() {
				prin
			}
		};
		""");
		//@formatter:on
		CompletionList list = requestCompletions(unit, "prin");
		assertNotNull(list);
		CompletionItem outItem = list.getItems().get(3);
		CompletionItem errItem = list.getItems().get(4);
		assertEquals("System.out.println()", outItem.getLabel());
		assertEquals("System.err.println()", errItem.getLabel());
		assertEquals(new Range(new Position(2, 2), new Position(2, 6)), outItem.getTextEdit().map(TextEdit::getRange, InsertReplaceEdit::getReplace));
		assertEquals(new Range(new Position(2, 2), new Position(2, 6)), errItem.getTextEdit().map(TextEdit::getRange, InsertReplaceEdit::getReplace));
	}

	@Test
	public void testCompletion_publicmainSnippet() throws JavaModelException {
		preferenceManager.getPreferences().setCompletionLazyResolveTextEditEnabled(false);
		preferenceManager.getPreferences().setCompletionMatchCaseMode(CompletionMatchCaseMode.FIRSTLETTER);
		ICompilationUnit unit = getWorkingCopy(
		//@formatter:off
		"src/java/Foo.java",
		"""
		class Foo {
			public
		};
		""");
		//@formatter:on
		CompletionList list = requestCompletions(unit, "public");
		assertNotNull(list);
		assertEquals(2, list.getItems().size());
		CompletionItem item = list.getItems().get(1);
		assertEquals("public static void main(String[] args)", item.getLabel());
	}

	@Test
	public void testCompletion_forNonPrimitiveArrayTypeReceivers() throws Exception {
		ICompilationUnit unit = getWorkingCopy("src/java/Arr.java", """
				public class Arr {
					void foo() {
				 		String[] names = new S
					}
				}
				""");

		CompletionList list = requestCompletions(unit, "new ");
		CompletionItem completionItem = list.getItems().get(0);
		assertEquals("Array type completion EditText", "String[]", completionItem.getInsertText());
		assertEquals("Array type completion Label", "String[] - java.lang", completionItem.getLabel());
	}

	@Test
	public void testCompletion_forPrimitiveArrayTypeReceivers() throws Exception {
		ICompilationUnit unit = getWorkingCopy("src/java/Arr.java", """
				public class Arr {
					void foo() {
				 		int[] ages = new i
					}
				}
				""");

		CompletionList list = requestCompletions(unit, "new ");
		CompletionItem completionItem = list.getItems().get(0);
		assertEquals("Array type completion EditText", "int[]", completionItem.getInsertText());
		assertEquals("Array type completion Label", "int[]", completionItem.getLabel());
	}

	@Test
	public void testCompletion_forEnclosingTypeArrayTypeReceivers() throws Exception {
		ICompilationUnit unit = getWorkingCopy("src/java/Arr.java", """
				public class Arr {
					void foo() {
						Arr[] ages = new A
					}
				}
				""");

		CompletionList list = requestCompletions(unit, "new ");
		CompletionItem completionItem = list.getItems().get(0);
		assertEquals("Array type completion EditText", "Arr[]", completionItem.getInsertText());
		assertEquals("Array type completion Label", "Arr[] - java", completionItem.getLabel());
	}

	// this test should pass when starting with -javaagent:<lombok_jar> (-javagent:~/.m2/repository/org/projectlombok/lombok/1.18.28/lombok-1.18.28.jar)
	// https://github.com/eclipse/eclipse.jdt.ls/issues/2669
	@Test
	public void testCompletion_lombok() throws Exception {
		boolean lombokDisabled = "true".equals(System.getProperty("jdt.ls.lombok.disabled"));
		if (lombokDisabled) {
			return;
		}
		when(preferenceManager.getClientPreferences().isCompletionInsertReplaceSupport()).thenReturn(true);
		when(preferenceManager.getClientPreferences().isCompletionListItemDefaultsSupport()).thenReturn(true);
		when(preferenceManager.getClientPreferences().isCompletionListItemDefaultsPropertySupport("editRange")).thenReturn(true);
		when(preferenceManager.getClientPreferences().isCompletionListItemDefaultsPropertySupport("insertTextFormat")).thenReturn(true);
		when(preferenceManager.getClientPreferences().isCompletionItemInsertTextModeSupport(InsertTextMode.AdjustIndentation)).thenReturn(true);
		when(preferenceManager.getClientPreferences().isCompletionListItemDefaultsPropertySupport("insertTextMode")).thenReturn(true);
		importProjects("maven/mavenlombok");
		IProject proj = WorkspaceHelper.getProject("mavenlombok");
		IJavaProject javaProject = JavaCore.create(proj);
		ICompilationUnit unit = null;
		try {
			unit = (ICompilationUnit) javaProject.findElement(new Path("org/sample/Test.java"));
			unit.becomeWorkingCopy(null);
			String source =
			//@formatter:off
				"package org.sample;\n"
				+ "import lombok.Builder;\n"
				+ "import lombok.Data;\n"
				+ "import lombok.Builder.Default;\n"
				+ "@Data\n"
				+ "@Builder\n"
				+ "public class Test {\n"
				+ "      @Default\n"
				+ "      private Integer offset = ;\n"
				+ "}\n";
			//@formatter:on
			changeDocument(unit, source, 1);
			Job.getJobManager().join(DocumentLifeCycleHandler.DOCUMENT_LIFE_CYCLE_JOBS, new NullProgressMonitor());
			CompletionList list = requestCompletions(unit, " = ");
			assertNotNull(list);
			assertEquals(6, list.getItems().size());
			CompletionItemDefaults itemDefaults = list.getItemDefaults();
			assertNotNull(itemDefaults);
			assertNull(itemDefaults.getInsertTextFormat());
			assertNull(itemDefaults.getEditRange());
		} finally {
			unit.discardWorkingCopy();
			proj.delete(true, monitor);
		}
	}

	// this test should pass when starting with -javaagent:<lombok_jar> (-javagent:~/.m2/repository/org/projectlombok/lombok/1.18.28/lombok-1.18.28.jar)
	// https://github.com/eclipse/eclipse.jdt.ls/issues/2669
	@Test
	public void testCompletion_lombok2() throws Exception {
		boolean lombokDisabled = "true".equals(System.getProperty("jdt.ls.lombok.disabled"));
		if (lombokDisabled) {
			return;
		}
		when(preferenceManager.getClientPreferences().isCompletionInsertReplaceSupport()).thenReturn(true);
		when(preferenceManager.getClientPreferences().isCompletionListItemDefaultsSupport()).thenReturn(true);
		when(preferenceManager.getClientPreferences().isCompletionListItemDefaultsPropertySupport("editRange")).thenReturn(true);
		when(preferenceManager.getClientPreferences().isCompletionListItemDefaultsPropertySupport("insertTextFormat")).thenReturn(true);
		when(preferenceManager.getClientPreferences().isCompletionItemInsertTextModeSupport(InsertTextMode.AdjustIndentation)).thenReturn(true);
		when(preferenceManager.getClientPreferences().isCompletionListItemDefaultsPropertySupport("insertTextMode")).thenReturn(true);
		importProjects("maven/mavenlombok");
		IProject proj = WorkspaceHelper.getProject("mavenlombok");
		IJavaProject javaProject = JavaCore.create(proj);
		ICompilationUnit unit = null;
		try {
			unit = (ICompilationUnit) javaProject.findElement(new Path("org/sample/Test.java"));
			unit.becomeWorkingCopy(null);
			String source =
			//@formatter:off
					"package org.sample;\n"
					+ "import lombok.Builder;\n"
					+ "import lombok.Data;\n"
					+ "import lombok.Builder.Default;\n"
					+ "@Data\n"
					+ "@Builder\n"
					+ "public class Test {\n"
					+ "      private Integer offset = ;\n"
					+ "}\n";
				//@formatter:on
			changeDocument(unit, source, 1);
			Job.getJobManager().join(DocumentLifeCycleHandler.DOCUMENT_LIFE_CYCLE_JOBS, new NullProgressMonitor());
			CompletionList list = requestCompletions(unit, " = ");
			assertNotNull(list);
			assertEquals(19, list.getItems().size());
			CompletionItemDefaults itemDefaults = list.getItemDefaults();
			assertNotNull(itemDefaults);
			assertNotNull(itemDefaults.getEditRange());
		} finally {
			unit.discardWorkingCopy();
			proj.delete(true, monitor);
		}
	}


	@Test
	public void testCompletion_record() throws Exception{
		importProjects("eclipse/java17");
		project = WorkspaceHelper.getProject("java17");
		ICompilationUnit unit = getWorkingCopy("src/foo/bar/Foo.java", """
				package foo.bar;

				public class Foo() {

					static record MyRecordKind(int i){}

					private MyRecordKin
				}
				""");

		CompletionList list = requestCompletions(unit, "private MyRecordKin");

		assertNotNull(list);
		assertFalse(list.getItems().isEmpty());
		CompletionItem item = list.getItems().get(0);
		assertEquals(CompletionItemKind.Struct, item.getKind());
		assertEquals("MyRecordKind", item.getInsertText());
	}

	@Test
	public void testCompletion_annotationParam() throws Exception {
		importProjects("eclipse/java17");
		project = WorkspaceHelper.getProject("java17");
		ICompilationUnit unit = getWorkingCopy("src/foo/bar/Foo.java", """
				package foo.bar;

				@Deprecated()
				public class Foo() {
				}
				""");

		CompletionList list = requestCompletions(unit, "@Deprecated(");

		assertNotNull(list);
		assertFalse(list.getItems().isEmpty());
		for (CompletionItem item : list.getItems()) {
			assertEquals(CompletionItemKind.Property, item.getKind());
		}
	}

	@Test
	public void testCompletion_order() throws Exception {
		when(preferenceManager.getClientPreferences().isCompletionItemLabelDetailsSupport()).thenReturn(true);
		ICompilationUnit unit = getWorkingCopy("src/org/sample/Test.java", String.join("\n",
		//@formatter:off
				"package org.sample",
				"public class Test {",
				"	public void test(String x){}",
				"	public void test(String x, int y){}",
				"	public void test(String x, int y, boolean z){}",
				"	public static void main(String[] args) {",
				"		  Test obj = new Test();",
				"		  obj.test",
				"	}",
				"}"));
				//@formatter:on
		CompletionList list = requestCompletions(unit, "obj.test");
		assertFalse(list.getItems().isEmpty());
		assertTrue(list.getItems().get(0).getFilterText().startsWith("test(String x)"));
		assertTrue(list.getItems().get(1).getFilterText().startsWith("test(String x, int y)"));
		assertTrue(list.getItems().get(2).getFilterText().startsWith("test(String x, int y, boolean z)"));
	}

	@Test
	public void testCompletion_collapse() throws Exception {
		when(preferenceManager.getClientPreferences().isCompletionItemLabelDetailsSupport()).thenReturn(true);
		preferenceManager.getPreferences().setCollapseCompletionItemsEnabled(true);
		ICompilationUnit unit = getWorkingCopy("src/org/sample/Test.java", String.join("\n",
		//@formatter:off
				"package org.sample",
				"public class Test {",
				"	public void test(String x){}",
				"	public void test(String x, int y){}",
				"	public void test(String x, int y, boolean z){}",
				"	public static void main(String[] args) {",
				"		  Test obj = new Test();",
				"		  obj.test",
				"	}",
				"}"));
				//@formatter:on
		CompletionList list = requestCompletions(unit, "obj.test");
		assertFalse(list.getItems().isEmpty());
		assertTrue(list.getItems().get(0).getLabelDetails().getDetail().startsWith("(...)"));
		assertTrue(list.getItems().get(0).getLabelDetails().getDescription().startsWith("3 overloads"));
	}

	@Test
	public void testCompletion_collapse_extends() throws Exception {
		when(preferenceManager.getClientPreferences().isCompletionItemLabelDetailsSupport()).thenReturn(true);
		preferenceManager.getPreferences().setCollapseCompletionItemsEnabled(true);
		ICompilationUnit unit = getWorkingCopy("src/org/sample/Test.java", String.join("\n",
		//@formatter:off
				"package org.sample",
				"class Test extends TestSuper {",
				"	public void test(String x){}",
				"	public static void main(String[] args) {",
				"		  Test obj = new Test();",
				"		  obj.test",
				"	}",
				"}",
				"public class TestSuper {",
				"	public void test(String x, int y){}",
				"}"));
				//@formatter:on
		CompletionList list = requestCompletions(unit, "obj.test");
		assertFalse(list.getItems().isEmpty());
		assertTrue(list.getItems().get(0).getLabelDetails().getDetail().startsWith("(...)"));
		assertTrue(list.getItems().get(0).getLabelDetails().getDescription().startsWith("2 overloads"));
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

	private void mockLSP2Client() {
		mockLSPClient(false, false);
	}

	private void mockLSPClient(boolean isSnippetSupported, boolean isSignatureHelpSupported) {
		// Mock the preference manager to use LSP v3 support.
		when(preferenceManager.getClientPreferences().isCompletionSnippetsSupported()).thenReturn(isSnippetSupported);
		when(preferenceManager.getClientPreferences().isSignatureHelpSupported()).thenReturn(isSignatureHelpSupported);
	}
}
