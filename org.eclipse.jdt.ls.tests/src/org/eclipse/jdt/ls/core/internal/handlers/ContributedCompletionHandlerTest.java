/*******************************************************************************
 * Copyright (c) Simeon Andreev and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *     Simeon Andreev - initial API and implementation
 *******************************************************************************/
package org.eclipse.jdt.ls.core.internal.handlers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.stream.Collectors;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.manipulation.CoreASTProvider;
import org.eclipse.jdt.ls.core.internal.JsonMessageHelper;
import org.eclipse.lsp4j.CompletionItem;
import org.eclipse.lsp4j.CompletionList;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

/**
 * @author Simeon Andreev
 *
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
public class ContributedCompletionHandlerTest extends AbstractCompilationUnitBasedTest {

	private IFile file;

	@BeforeEach
	public void setUp() throws Exception {
		ContributedTestCompletionHandler.item = ContributedTestCompletionHandler.testItem();
		mockLSP3Client();
		CoreASTProvider sharedASTProvider = CoreASTProvider.getInstance();
		sharedASTProvider.disposeAST();
		file = project.getFile("test.txt");
		file.create(new String("test").getBytes(), IResource.FORCE, monitor);
	}

	@AfterEach
	public void removeExtension() throws CoreException {
		ContributedTestCompletionHandler.item = null;
		file.delete(true, monitor);
	}

	@Test
	public void testCompletion_contributedHandler() throws Exception {
		when(preferenceManager.getClientPreferences().isCompletionItemLabelDetailsSupport()).thenReturn(true);
		CompletionList list = requestCompletions(file.getLocationURI().toString(), 0, 0);
		assertNotNull(list);
		List<CompletionItem> filtered = list.getItems().stream().filter((item) -> {
			return item.getDetail() != null && item.getDetail().startsWith(ContributedTestCompletionHandler.TEST_DETAIL);
		}).collect(Collectors.toList());
		assertEquals(1, filtered.size(), "No test proposals");
		CompletionItem oride = filtered.get(0);
		assertEquals(ContributedTestCompletionHandler.TEST_CONTENT, oride.getInsertText());
	}

	private CompletionList requestCompletions(String uri, int line, int offset) throws JavaModelException {
		return server.completion(JsonMessageHelper.getParams(CompletionHandlerTest.createCompletionRequest(uri, line, offset))).join().getRight();
	}

	private void mockLSP3Client() {
		CompletionHandlerTest.mockLSPClient(preferenceManager, true, true);
	}
}
