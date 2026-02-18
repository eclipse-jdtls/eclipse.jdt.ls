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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.eclipse.core.resources.IProject;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IPackageFragment;
import org.eclipse.jdt.core.IPackageFragmentRoot;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.manipulation.CoreASTProvider;
import org.eclipse.jdt.ls.core.internal.JDTUtils;
import org.eclipse.jdt.ls.core.internal.JsonMessageHelper;
import org.eclipse.jdt.ls.core.internal.WorkspaceHelper;
import org.eclipse.lsp4j.CompletionItem;
import org.eclipse.lsp4j.CompletionItemKind;
import org.eclipse.lsp4j.CompletionList;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class CompletionResolveHandlerTest extends AbstractCompilationUnitBasedTest {

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
		CoreASTProvider sharedASTProvider = CoreASTProvider.getInstance();
		sharedASTProvider.disposeAST();
		preferences.setPostfixCompletionEnabled(false);
		when(preferenceManager.getClientPreferences().isCompletionResolveDocumentSupport()).thenReturn(true);
	}

	@AfterEach
	public void tearDown() throws Exception {
	}

	@Test
	public void testSnippet_while_itemDefaults_enabled_generic_snippets() throws JavaModelException {
		//@formatter:off
		ICompilationUnit unit = getWorkingCopy("src/org/sample/Test.java", """
				package org.sample;
				public class Test {
					/**
					 * This is a test.
					 */
					private int a;
					public void test(int a) {
						a
					}
				}"""
		);
		//@formatter:on
		CompletionList list = requestCompletions(unit, "\ta");

		assertNotNull(list);

		List<CompletionItem> items = new ArrayList<>(list.getItems());
		CompletionItem item = items.stream().filter(i -> "this.a".equals(i.getInsertText())).findFirst().get();

		CompletionItem resolved = server.resolveCompletionItem(item).join();
		assertEquals("This is a test.", resolved.getDocumentation().getLeft().trim());
	}

	@Disabled("Requires a real JDK, instead of stubbed JRE with no module info")
	@Test
	public void testModuleCompletion_resolve_showsDocumentation() throws Exception {
		importProjects("eclipse/java25");
		IProject java25Project = WorkspaceHelper.getProject("java25");
		assertNotNull(java25Project, "java25 project should be loaded");
		IJavaProject javaProject = JavaCore.create(java25Project);
		IPackageFragmentRoot root = javaProject.getPackageFragmentRoot(java25Project.getFolder("src/main/java"));
		IPackageFragment pack = root.getPackageFragment("org.sample");
		String source = """
				package org.sample;

				import module java.

				public class ImportModule {}
				""";
		ICompilationUnit unit = pack.createCompilationUnit("ImportModule.java", source, false, null);
		unit.becomeWorkingCopy(monitor);
		unit.makeConsistent(monitor);

		CompletionList list = requestCompletions(unit, "java.");
		assertNotNull(list);
		assertTrue(list.getItems().size() > 0, "Expected module completions after 'import module java.'");

		Optional<CompletionItem> javaSqlItem = list.getItems().stream()
				.filter(i -> CompletionItemKind.Module.equals(i.getKind()))
				.filter(i -> "java.base".equals(i.getLabel()))
				.findFirst();
		assertTrue(javaSqlItem.isPresent(), "Expected 'java.base' module in completion list");

		CompletionItem resolved = server.resolveCompletionItem(javaSqlItem.get()).join();
		assertNotNull(resolved.getDocumentation(), "Resolved module completion should have documentation");
		Either<String, org.eclipse.lsp4j.MarkupContent> doc = resolved.getDocumentation();
		String docText = doc.getLeft() != null ? doc.getLeft() : (doc.getRight() != null ? doc.getRight().getValue() : null);
		assertNotNull(docText, "Documentation content should not be null");
		assertTrue(!docText.isBlank(), "Module documentation should not be empty");
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

}
