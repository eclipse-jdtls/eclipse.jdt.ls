/*******************************************************************************
 * Copyright (c) 2022 Red Hat Inc. and others.
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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.jdt.core.CompletionProposal;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.ls.core.contentassist.CompletionRanking;
import org.eclipse.jdt.ls.core.contentassist.ICompletionRankingProvider;
import org.eclipse.jdt.ls.core.internal.JDTUtils;
import org.eclipse.jdt.ls.core.internal.JavaLanguageServerPlugin;
import org.eclipse.jdt.ls.core.internal.JsonMessageHelper;
import org.eclipse.lsp4j.CompletionItem;
import org.eclipse.lsp4j.CompletionList;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class CompletionRankingProviderTest extends AbstractCompilationUnitBasedTest {

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

	@Mock
	private TestRankingProvider provider;

	@Before
	public void setUp() {
		when(provider.rank(any(), any(), any(), any())).thenCallRealMethod();
		doNothing().when(provider).onDidCompletionItemSelect(any());
		JavaLanguageServerPlugin.getCompletionContributionService().registerRankingProvider(provider);
	}

	@After
	public void tearDown() {
		JavaLanguageServerPlugin.getCompletionContributionService().unregisterRankingProvider(provider);
		reset(provider);
	}

	@Test
	public void testRank() throws Exception {
		ICompilationUnit unit = getWorkingCopy(
				"src/java/Foo.java",
				"public class Foo {\n"+
						"	void foo() {\n"+
						" 		Integer.\n" +
						"	}\n"+
				"}\n");

		CompletionList list = requestCompletions(unit, "Integer.");
		assertNotNull(list);
		assertFalse("No proposals were found",list.getItems().isEmpty());

		CompletionItem recommended = list.getItems().get(0);
		assertTrue(recommended.getLabel().startsWith("★"));
		assertEquals(recommended.getFilterText(), recommended.getInsertText());
	}

	@Test
	public void testOnDidCompletionItemSelect() throws Exception {ICompilationUnit unit = getWorkingCopy(
				"src/java/Foo.java",
				"public class Foo {\n"+
						"	void foo() {\n"+
						" 		Integer.\n" +
						"	}\n"+
				"}\n");

		requestCompletions(unit, "Integer.");
		CompletionHandler handler = new CompletionHandler(JavaLanguageServerPlugin.getPreferencesManager());

		ArgumentCaptor<CompletionItem> argument = ArgumentCaptor.forClass(CompletionItem.class);
		handler.onDidCompletionItemSelect(String.valueOf((new CompletionResponse()).getId() - 1), "0");

		verify(provider, times(1)).onDidCompletionItemSelect(argument.capture());
		Map<String, String> data = (Map<String, String>) argument.getValue().getData();
		assertEquals("bar", data.get("foo"));
		assertTrue(data.containsKey(CompletionRanking.COMPLETION_EXECUTION_TIME));
	}

	class TestRankingProvider implements ICompletionRankingProvider {

		@Override
		public CompletionRanking[] rank(List<CompletionProposal> proposals, org.eclipse.jdt.core.CompletionContext context, ICompilationUnit unit, IProgressMonitor monitor) {
			CompletionRanking[] rankings = new CompletionRanking[proposals.size()];
			rankings[0] = new CompletionRanking();
			rankings[0].setScore(CompletionRanking.MAX_SCORE);
			rankings[0].setDecorator('★');
			Map<String, String> data = new HashMap<>();
			data.put("foo", "bar");
			rankings[0].setData(data);
			return rankings;
		}

		@Override
		public void onDidCompletionItemSelect(CompletionItem item) {
		}
	}

	protected CompletionList requestCompletions(ICompilationUnit unit, String completeBehind) throws JavaModelException {
		return requestCompletions(unit, completeBehind, 0);
	}

	protected CompletionList requestCompletions(ICompilationUnit unit, String completeBehind, int fromIndex) throws JavaModelException {
		int[] loc = findCompletionLocation(unit, completeBehind, fromIndex);
		return server.completion(JsonMessageHelper.getParams(createCompletionRequest(unit, loc[0], loc[1]))).join().getRight();
	}

	private String createCompletionRequest(ICompilationUnit unit, int line, int kar) {
		return COMPLETION_TEMPLATE.replace("${file}", JDTUtils.toURI(unit))
				.replace("${line}", String.valueOf(line))
				.replace("${char}", String.valueOf(kar));
	}
}
