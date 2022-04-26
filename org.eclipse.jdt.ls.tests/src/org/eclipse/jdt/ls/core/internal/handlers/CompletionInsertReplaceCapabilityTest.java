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

import static org.eclipse.jdt.ls.core.internal.Lsp4jAssertions.assertPosition;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.when;

import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.manipulation.CoreASTProvider;
import org.eclipse.jdt.ls.core.internal.JDTUtils;
import org.eclipse.jdt.ls.core.internal.JsonMessageHelper;
import org.eclipse.lsp4j.CompletionItem;
import org.eclipse.lsp4j.CompletionList;
import org.eclipse.lsp4j.InsertReplaceEdit;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.runners.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class CompletionInsertReplaceCapabilityTest extends AbstractCompilationUnitBasedTest {

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
		mockClient();
		CoreASTProvider sharedASTProvider = CoreASTProvider.getInstance();
		sharedASTProvider.disposeAST();
	}

	@Test
	public void testCompletion_InsertReplaceEdit() throws Exception {
		when(preferenceManager.getClientPreferences().isCompletionInsertReplaceSupport()).thenReturn(true);
		ICompilationUnit unit = getWorkingCopy("src/org/sample/Test.java", String.join("\n",
			"public class Test {",
			"	public static void main(String[] args) {",
			"		if (\"foo\".equSystem.getProperty(\"bar\")) {}",
			"	}",
			"}"
		));
		CompletionItem item = requestCompletions(unit, ".equ").getItems().get(0);
		InsertReplaceEdit edit = item.getTextEdit().getRight();
		assertNotNull(edit);
		assertTrue(edit.getNewText().startsWith("equals("));
		// check insert range
		assertPosition(2, 12, edit.getInsert().getStart());
		assertPosition(2, 15, edit.getInsert().getEnd());
		// check replace range
		assertPosition(2, 12, edit.getReplace().getStart());
		assertPosition(2, 21, edit.getReplace().getEnd());
	}

	private CompletionList requestCompletions(ICompilationUnit unit, String completeBehind) throws JavaModelException {
		int[] loc = findCompletionLocation(unit, completeBehind);
		return server.completion(JsonMessageHelper.getParams(createCompletionRequest(unit, loc[0], loc[1]))).join().getRight();
	}

	private String createCompletionRequest(ICompilationUnit unit, int line, int kar) {
		return COMPLETION_TEMPLATE.replace("${file}", JDTUtils.toURI(unit))
				.replace("${line}", String.valueOf(line))
				.replace("${char}", String.valueOf(kar));
	}

	private void mockClient() {
		// Mock the preference manager to use LSP v3 support.
		when(preferenceManager.getClientPreferences().isCompletionSnippetsSupported()).thenReturn(true);
		when(preferenceManager.getClientPreferences().isSignatureHelpSupported()).thenReturn(true);
		when(preferenceManager.getClientPreferences().isCompletionInsertReplaceSupport()).thenReturn(true);
	}
}
