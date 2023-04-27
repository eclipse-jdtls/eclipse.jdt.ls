/*******************************************************************************
 * Copyright (c) 2023 Microsoft Corporation and others.
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
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.manipulation.CoreASTProvider;
import org.eclipse.jdt.internal.codeassist.impl.AssistOptions;
import org.eclipse.jdt.ls.core.internal.JDTUtils;
import org.eclipse.jdt.ls.core.internal.JavaClientConnection;
import org.eclipse.jdt.ls.core.internal.JsonMessageHelper;
import org.eclipse.jdt.ls.core.internal.preferences.ClientPreferences;
import org.eclipse.lsp4j.CompletionItem;
import org.eclipse.lsp4j.CompletionItemKind;
import org.eclipse.lsp4j.CompletionList;
import org.eclipse.lsp4j.InsertTextFormat;
import org.eclipse.lsp4j.InsertTextMode;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class CompletionHandlerLazyResolveTest extends AbstractCompilationUnitBasedTest {

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
		preferences.setPostfixCompletionEnabled(false);
		preferences.setCompletionLazyResolveTextEditEnabled(true);
	}

	@After
	public void tearDown() throws Exception {
		javaClient.disconnect();
	}

	@Test
	public void testSnippet_sysout() throws JavaModelException {
		when(preferenceManager.getClientPreferences().getCompletionItemInsertTextModeDefault()).thenReturn(InsertTextMode.AdjustIndentation);
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
		String insertText = item.getInsertText();
		assertEquals("System.out.println(${0});", insertText);
		assertNull(item.getInsertTextMode());
	}

	@Test
	public void testSnippet_sout() throws JavaModelException {
		//@formatter:off
		ICompilationUnit unit = getWorkingCopy(
			"src/org/sample/Test.java",
			"package org.sample;\n" +
			"public class Test {\n" +
			"	public void testMethod() {\n" +
			"		sout" +
			"	}\n" +
			"}"
		);
		//@formatter:on
		CompletionList list = requestCompletions(unit, "sout");

		assertNotNull(list);

		List<CompletionItem> items = new ArrayList<>(list.getItems());
		for (CompletionItem item : items) {
			if (CompletionItemKind.Snippet.equals(item.getKind()) && "sout".equals(item.getLabel())) {
				String insertText = item.getInsertText();
				assertEquals("System.out.println(${0});", insertText);
				return;
			}
		}
		fail("Failed to find snippet: 'sout'.");
	}

	@Test
	public void testSnippet_syserr() throws JavaModelException {
		//@formatter:off
		ICompilationUnit unit = getWorkingCopy(
			"src/org/sample/Test.java",
			"package org.sample;\n" +
			"public class Test {\n" +
			"	public void testMethod() {\n" +
			"		syserr" +
			"	}\n" +
			"}"
		);
		//@formatter:on
		CompletionList list = requestCompletions(unit, "syserr");

		assertNotNull(list);

		List<CompletionItem> items = new ArrayList<>(list.getItems());
		CompletionItem item = items.get(0);
		assertEquals("syserr", item.getLabel());
		String insertText = item.getInsertText();
		assertEquals("System.err.println(${0});", insertText);
	}

	@Test
	public void testSnippet_serr() throws JavaModelException {
		//@formatter:off
		ICompilationUnit unit = getWorkingCopy(
			"src/org/sample/Test.java",
			"package org.sample;\n" +
			"public class Test {\n" +
			"	public void testMethod() {\n" +
			"		serr" +
			"	}\n" +
			"}"
		);
		//@formatter:on
		CompletionList list = requestCompletions(unit, "serr");

		assertNotNull(list);

		List<CompletionItem> items = new ArrayList<>(list.getItems());
		for (CompletionItem item : items) {
			if (CompletionItemKind.Snippet.equals(item.getKind()) && "serr".equals(item.getLabel())) {
				String insertText = item.getInsertText();
				assertEquals("System.err.println(${0});", insertText);
				return;
			}
		}
		fail("Failed to find snippet: 'serr'.");
	}

	@Test
	public void testSnippet_systrace() throws JavaModelException {
		//@formatter:off
		ICompilationUnit unit = getWorkingCopy(
			"src/org/sample/Test.java",
			"package org.sample;\n" +
			"public class Test {\n" +
			"	public void testMethod() {\n" +
			"		systrace" +
			"	}\n" +
			"}"
		);
		//@formatter:on
		CompletionList list = requestCompletions(unit, "systrace");

		assertNotNull(list);

		List<CompletionItem> items = new ArrayList<>(list.getItems());
		CompletionItem item = items.get(0);
		assertEquals("systrace", item.getLabel());
		String insertText = item.getInsertText();
		assertEquals("System.out.println(\"${enclosing_type}.${enclosing_method}()\");", insertText);
		CompletionItem resolved = server.resolveCompletionItem(item).join();
		assertNotNull(resolved.getTextEdit());
		assertEquals("System.out.println(\"Test.testMethod()\");", resolved.getTextEdit().getLeft().getNewText());
	}

	@Test
	public void testSnippet_soutm() throws JavaModelException {
		//@formatter:off
		ICompilationUnit unit = getWorkingCopy(
			"src/org/sample/Test.java",
			"package org.sample;\n" +
			"public class Test {\n" +
			"	public void testMethod() {\n" +
			"		soutm" +
			"	}\n" +
			"}"
		);
		//@formatter:on
		CompletionList list = requestCompletions(unit, "soutm");

		assertNotNull(list);

		List<CompletionItem> items = new ArrayList<>(list.getItems());
		CompletionItem item = items.get(0);
		assertEquals("soutm", item.getLabel());
		String insertText = item.getInsertText();
		assertEquals("System.out.println(\"${enclosing_type}.${enclosing_method}()\");", insertText);
		CompletionItem resolved = server.resolveCompletionItem(item).join();
		assertNotNull(resolved.getTextEdit());
		assertEquals("System.out.println(\"Test.testMethod()\");", resolved.getTextEdit().getLeft().getNewText());
	}

	@Test
	public void testSnippet_array_foreach() throws JavaModelException {
		//@formatter:off
		ICompilationUnit unit = getWorkingCopy(
			"src/org/sample/Test.java",
			"package org.sample;\n" +
			"public class Test {\n" +
			"	public void testMethod(String[] args) {\n" +
			"		foreach" +
			"	}\n" +
			"}"
		);
		//@formatter:on
		CompletionList list = requestCompletions(unit, "foreach");

		assertNotNull(list);

		List<CompletionItem> items = new ArrayList<>(list.getItems());
		CompletionItem item = items.get(0);
		assertEquals("foreach", item.getLabel());
		String insertText = item.getInsertText();
		assertEquals("for (${1:iterable_type} ${2:iterable_element} : ${3:iterable}) {\n\t$TM_SELECTED_TEXT${0}\n}", insertText);
		CompletionItem resolved = server.resolveCompletionItem(item).join();
		assertNotNull(resolved.getTextEdit());
		assertEquals("for (${1:String} ${2:string} : ${3:args}) {\n\t$TM_SELECTED_TEXT${0}\n}", resolved.getTextEdit().getLeft().getNewText());
	}

	@Test
	public void testSnippet_list_foreach() throws JavaModelException {
		//@formatter:off
		ICompilationUnit unit = getWorkingCopy(
			"src/org/sample/Test.java",
			"package org.sample;\n" +
			"import java.util.List;\n" +
			"public class Test {\n" +
			"	public void testMethod(List<String> args) {\n" +
			"		foreach" +
			"	}\n" +
			"}"
		);
		//@formatter:on
		CompletionList list = requestCompletions(unit, "foreach");

		assertNotNull(list);

		List<CompletionItem> items = new ArrayList<>(list.getItems());
		CompletionItem item = items.get(0);
		assertEquals("foreach", item.getLabel());
		String insertText = item.getInsertText();
		assertEquals("for (${1:iterable_type} ${2:iterable_element} : ${3:iterable}) {\n\t$TM_SELECTED_TEXT${0}\n}", insertText);
		CompletionItem resolved = server.resolveCompletionItem(item).join();
		assertNotNull(resolved.getTextEdit());
		assertEquals("for (${1:String} ${2:string} : ${3:args}) {\n\t$TM_SELECTED_TEXT${0}\n}", resolved.getTextEdit().getLeft().getNewText());
	}

	@Test
	public void testSnippet_list_iter() throws JavaModelException {
		//@formatter:off
		ICompilationUnit unit = getWorkingCopy(
			"src/org/sample/Test.java",
			"package org.sample;\n" +
			"import java.util.List;\n" +
			"public class Test {\n" +
			"	public void testMethod(List<String> args) {\n" +
			"		iter" +
			"	}\n" +
			"}"
		);
		//@formatter:on
		CompletionList list = requestCompletions(unit, "iter");

		assertNotNull(list);

		List<CompletionItem> items = new ArrayList<>(list.getItems());
		for (CompletionItem item : items) {
			if (CompletionItemKind.Snippet.equals(item.getKind()) && "iter".equals(item.getLabel())) {
				String insertText = item.getInsertText();
				assertEquals("for (${1:iterable_type} ${2:iterable_element} : ${3:iterable}) {\n\t$TM_SELECTED_TEXT${0}\n}", insertText);
				CompletionItem resolved = server.resolveCompletionItem(item).join();
				assertNotNull(resolved.getTextEdit());
				assertEquals("for (${1:String} ${2:string} : ${3:args}) {\n\t$TM_SELECTED_TEXT${0}\n}", resolved.getTextEdit().getLeft().getNewText());
				return;
			}
		}
		fail("Failed to find snippet: 'iter'.");
	}

	@Test
	public void testSnippet_array_fori() throws JavaModelException {
		//@formatter:off
		ICompilationUnit unit = getWorkingCopy(
			"src/org/sample/Test.java",
			"package org.sample;\n" +
			"public class Test {\n" +
			"	public void testMethod(String[] args) {\n" +
			"		fori" +
			"	}\n" +
			"}"
		);
		//@formatter:on
		CompletionList list = requestCompletions(unit, "fori");

		assertNotNull(list);

		List<CompletionItem> items = new ArrayList<>(list.getItems());
		CompletionItem item = items.get(0);
		assertEquals("fori", item.getLabel());
		String insertText = item.getInsertText();
		assertEquals("for (${1:int} ${2:index} = ${3:0}; ${2:index} < ${4:array.length}; ${2:index}++) {\n\t$TM_SELECTED_TEXT${0}\n}", insertText);
		CompletionItem resolved = server.resolveCompletionItem(item).join();
		assertNotNull(resolved.getTextEdit());
		assertEquals("for (${1:int} ${2:i} = ${3:0}; ${2:i} < ${4:args.length}; ${2:i}++) {\n\t$TM_SELECTED_TEXT${0}\n}", resolved.getTextEdit().getLeft().getNewText());
	}

	@Test
	public void testSnippet_while() throws JavaModelException {
		when(preferenceManager.getClientPreferences().isCompletionItemLabelDetailsSupport()).thenReturn(true);
		//@formatter:off
		ICompilationUnit unit = getWorkingCopy(
			"src/org/sample/Test.java",
			"package org.sample;\n" +
			"public class Test {\n" +
			"	public void testMethod(boolean con) {\n" +
			"		while" +
			"	}\n" +
			"}"
		);
		//@formatter:on
		CompletionList list = requestCompletions(unit, "while");

		assertNotNull(list);

		List<CompletionItem> items = new ArrayList<>(list.getItems());
		CompletionItem item = items.get(1);
		assertEquals("while", item.getLabel());
		assertNull(item.getLabelDetails().getDetail());
		assertEquals("while statement", item.getLabelDetails().getDescription());

		String insertText = item.getInsertText();
		assertEquals("while (${1:condition:var(boolean)}) {\n\t$TM_SELECTED_TEXT${0}\n}", insertText);
		CompletionItem resolved = server.resolveCompletionItem(item).join();
		assertNotNull(resolved.getTextEdit());
		assertEquals("while (${1:con}) {\n\t$TM_SELECTED_TEXT${0}\n}", resolved.getTextEdit().getLeft().getNewText());
	}

	@Test
	public void testSnippet_dowhile() throws JavaModelException {
		//@formatter:off
		ICompilationUnit unit = getWorkingCopy(
			"src/org/sample/Test.java",
			"package org.sample;\n" +
			"public class Test {\n" +
			"	public void testMethod(boolean con) {\n" +
			"		dowhile" +
			"	}\n" +
			"}"
		);
		//@formatter:on
		CompletionList list = requestCompletions(unit, "dowhile");

		assertNotNull(list);

		List<CompletionItem> items = new ArrayList<>(list.getItems());
		CompletionItem item = items.get(0);
		assertEquals("dowhile", item.getLabel());
		String insertText = item.getInsertText();
		assertEquals("do {\n\t$TM_SELECTED_TEXT${0}\n} while (${1:condition:var(boolean)});", insertText);
		CompletionItem resolved = server.resolveCompletionItem(item).join();
		assertNotNull(resolved.getTextEdit());
		assertEquals("do {\n\t$TM_SELECTED_TEXT${0}\n} while (${1:con});", resolved.getTextEdit().getLeft().getNewText());
	}

	@Test
	public void testSnippet_if() throws JavaModelException {
		//@formatter:off
		ICompilationUnit unit = getWorkingCopy(
			"src/org/sample/Test.java",
			"package org.sample;\n" +
			"public class Test {\n" +
			"	public void testMethod(boolean con) {\n" +
			"		if" +
			"	}\n" +
			"}"
		);
		//@formatter:on
		String substringMatch = System.getProperty(AssistOptions.PROPERTY_SubstringMatch);
		try {
			System.setProperty(AssistOptions.PROPERTY_SubstringMatch, "true");
			CompletionList list = requestCompletions(unit, "if");
			assertNotNull(list);
			List<CompletionItem> items = new ArrayList<>(list.getItems());
			boolean hasIfSnippet = false;
			for (CompletionItem item : items) {
				if (!Objects.equals(item.getLabel(), "if")) {
					continue;
				}
				if (Objects.equals(item.getInsertText(), "if (${1:condition:var(boolean)}) {\n\t$TM_SELECTED_TEXT${0}\n}")) {
					hasIfSnippet = true;
					break;
				}
			}
			assertTrue(hasIfSnippet);
		} finally {
			System.setProperty(AssistOptions.PROPERTY_SubstringMatch, substringMatch);
		}
	}

	@Test
	public void testSnippet_ifelse() throws JavaModelException {
		//@formatter:off
		ICompilationUnit unit = getWorkingCopy(
			"src/org/sample/Test.java",
			"package org.sample;\n" +
			"public class Test {\n" +
			"	public void testMethod(boolean con) {\n" +
			"		ifelse" +
			"	}\n" +
			"}"
		);
		//@formatter:on
		CompletionList list = requestCompletions(unit, "ifelse");

		assertNotNull(list);

		List<CompletionItem> items = new ArrayList<>(list.getItems());
		CompletionItem item = items.get(0);
		assertEquals("ifelse", item.getLabel());
		String insertText = item.getInsertText();
		assertEquals("if (${1:condition:var(boolean)}) {\n\t${2}\n} else {\n\t${0}\n}", insertText);
		CompletionItem resolved = server.resolveCompletionItem(item).join();
		assertNotNull(resolved.getTextEdit());
		assertEquals("if (${1:con}) {\n\t${2}\n} else {\n\t${0}\n}", resolved.getTextEdit().getLeft().getNewText());
	}

	@Test
	public void testSnippet_ifnull() throws JavaModelException {
		//@formatter:off
		ICompilationUnit unit = getWorkingCopy(
			"src/org/sample/Test.java",
			"package org.sample;\n" +
			"public class Test {\n" +
			"	public void testMethod(Object obj) {\n" +
			"		ifnull" +
			"	}\n" +
			"}"
		);
		//@formatter:on
		CompletionList list = requestCompletions(unit, "ifnull");

		assertNotNull(list);

		List<CompletionItem> items = new ArrayList<>(list.getItems());
		CompletionItem item = items.get(0);
		assertEquals("ifnull", item.getLabel());
		String insertText = item.getInsertText();
		assertEquals("if (${1:name:var} == null) {\n\t$TM_SELECTED_TEXT${0}\n}", insertText);
		CompletionItem resolved = server.resolveCompletionItem(item).join();
		assertNotNull(resolved.getTextEdit());
		assertEquals("if (${1:obj} == null) {\n\t$TM_SELECTED_TEXT${0}\n}", resolved.getTextEdit().getLeft().getNewText());
	}

	@Test
	public void testSnippet_ifnotnull() throws JavaModelException {
		//@formatter:off
		ICompilationUnit unit = getWorkingCopy(
			"src/org/sample/Test.java",
			"package org.sample;\n" +
			"public class Test {\n" +
			"	public void testMethod(Object obj) {\n" +
			"		ifnotnull" +
			"	}\n" +
			"}"
		);
		//@formatter:on
		CompletionList list = requestCompletions(unit, "ifnotnull");

		assertNotNull(list);

		List<CompletionItem> items = new ArrayList<>(list.getItems());
		CompletionItem item = items.get(0);
		assertEquals("ifnotnull", item.getLabel());
		String insertText = item.getInsertText();
		assertEquals("if (${1:name:var} != null) {\n\t$TM_SELECTED_TEXT${0}\n}", insertText);
		CompletionItem resolved = server.resolveCompletionItem(item).join();
		assertNotNull(resolved.getTextEdit());
		assertEquals("if (${1:obj} != null) {\n\t$TM_SELECTED_TEXT${0}\n}", resolved.getTextEdit().getLeft().getNewText());
	}

	@Test
	public void testSnippet_while_itemDefaults_enabled_generic_snippets() throws JavaModelException {
		mockClientPreferences(true, true, true);
		when(preferenceManager.getClientPreferences().getCompletionItemInsertTextModeDefault()).thenReturn(InsertTextMode.AsIs);
		//@formatter:off
		ICompilationUnit unit = getWorkingCopy(
			"src/org/sample/Test.java",
			"package org.sample;\n" +
			"public class Test {\n" +
			"	public void testMethod(boolean con) {\n" +
			"		while" +
			"	}\n" +
			"}"
		);
		//@formatter:on
		CompletionList list = requestCompletions(unit, "while");

		assertNotNull(list);
		assertNotNull(list.getItemDefaults().getEditRange());
		assertEquals(InsertTextFormat.Snippet, list.getItemDefaults().getInsertTextFormat());
		assertEquals(InsertTextMode.AdjustIndentation, list.getItemDefaults().getInsertTextMode());

		List<CompletionItem> items = new ArrayList<>(list.getItems());
		CompletionItem item = items.get(1);
		assertEquals("while", item.getLabel());
		String insertText = item.getTextEditText();
		assertEquals("while (${1:condition:var(boolean)}) {\n\t$TM_SELECTED_TEXT${0}\n}", insertText);
		//check that the fields covered by itemDefaults are set to null
		assertNull(item.getTextEdit());
		assertNull(item.getInsertTextFormat());
		assertNull(item.getInsertTextMode());

		CompletionItem resolved = server.resolveCompletionItem(item).join();
		assertNotNull(resolved.getTextEdit());
		assertEquals("while (${1:con}) {\n\t$TM_SELECTED_TEXT${0}\n}", resolved.getTextEdit().getLeft().getNewText());
	}

	@Test
	public void testConstructorCompletion() throws JavaModelException {
		//@formatter:off
		ICompilationUnit unit = getWorkingCopy(
			"src/org/sample/Test.java",
			"package org.sample;\n" +
			"public class Test {\n" +
			"	public void testMethod(String[] args) {\n" +
			"		new String" +
			"	}\n" +
			"}"
		);
		//@formatter:on
		CompletionList list = requestCompletions(unit, "new String");

		assertNotNull(list);

		List<CompletionItem> items = new ArrayList<>(list.getItems());
		CompletionItem item = items.get(0);
		assertEquals("String", item.getTextEdit().getLeft().getNewText());
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
		mockLSPClient(true);
	}

	private void mockLSPClient(boolean isSnippetSupported) {
		// Mock the preference manager to use LSP v3 support.
		when(preferenceManager.getClientPreferences().isCompletionSnippetsSupported()).thenReturn(isSnippetSupported);
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
}
