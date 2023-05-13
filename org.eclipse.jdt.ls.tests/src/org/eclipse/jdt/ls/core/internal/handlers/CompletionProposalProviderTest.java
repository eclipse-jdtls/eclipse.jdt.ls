/*******************************************************************************
 * Copyright (c) 2023 Gayan Perera and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *     Gayan Perera - initial API and implementation
 *******************************************************************************/
package org.eclipse.jdt.ls.core.internal.handlers;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.jdt.core.CompletionContext;
import org.eclipse.jdt.core.CompletionProposal;
import org.eclipse.jdt.core.IAnnotatable;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.ls.core.contentassist.ICompletionProposalProvider;
import org.eclipse.jdt.ls.core.internal.JDTUtils;
import org.eclipse.jdt.ls.core.internal.JavaLanguageServerPlugin;
import org.eclipse.jdt.ls.core.internal.JsonMessageHelper;
import org.eclipse.lsp4j.CompletionItem;
import org.eclipse.lsp4j.CompletionList;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class CompletionProposalProviderTest extends AbstractCompilationUnitBasedTest {

	private static String COMPLETION_TEMPLATE = "{\n" + "    \"id\": \"1\",\n" + "    \"method\": \"textDocument/completion\",\n" + "    \"params\": {\n" + "        \"textDocument\": {\n" + "            \"uri\": \"${file}\"\n"
			+ "        },\n" + "        \"position\": {\n" + "            \"line\": ${line},\n" + "            \"character\": ${char}\n" + "        }\n" + "    },\n" + "    \"jsonrpc\": \"2.0\"\n" + "}";

	private TestProposalProvider provider;

	@Before
	public void setUp() {
		provider = new TestProposalProvider();
		JavaLanguageServerPlugin.getCompletionContributionService().registerProposalProvider(provider);
	}

	@After
	public void tearDown() {
		JavaLanguageServerPlugin.getCompletionContributionService().unregisterProposalProvider(provider);
		provider = null;
	}

	@Test
	public void testCompletionsFromExternalCompletionProvider() throws Exception {
		ICompilationUnit unit = getWorkingCopy("src/java/Foo.java", """
				public class Foo {
					@SuppressWarnings()
					void foo() {
					}
				}
				""");

		CompletionList list = requestCompletions(unit, "@SuppressWarnings(");
		assertNotNull(list);
		assertFalse("No proposals were found", list.getItems().isEmpty());

		Optional<CompletionItem> item = list.getItems().stream().filter(i -> "rawtypes".equals(i.getLabel())).findFirst();
		assertTrue("No expected completion item from provider", item.isPresent());
	}

	class TestProposalProvider implements ICompletionProposalProvider {
		@Override
		public List<CompletionProposal> compute(int offset, ICompilationUnit unit, CompletionContext context, IProgressMonitor monitor) {
			try {
				if (unit.getElementAt(offset) instanceof IAnnotatable ann && ann.getAnnotation("SuppressWarnings") != null) {
					CompletionProposal completion = CompletionProposal.create(CompletionProposal.KEYWORD, offset);
					completion.setCompletion("rawtypes".toCharArray());
					return List.of(completion);
				}
			} catch (JavaModelException e) {
			}
			return Collections.emptyList();
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
		return COMPLETION_TEMPLATE.replace("${file}", JDTUtils.toURI(unit)).replace("${line}", String.valueOf(line)).replace("${char}", String.valueOf(kar));
	}
}
