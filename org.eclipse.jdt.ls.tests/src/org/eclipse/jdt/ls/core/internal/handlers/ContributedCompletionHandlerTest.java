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
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.util.List;
import java.util.stream.Collectors;

import org.eclipse.core.internal.registry.ExtensionRegistry;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.ContributorFactoryOSGi;
import org.eclipse.core.runtime.IContributor;
import org.eclipse.core.runtime.IExtension;
import org.eclipse.core.runtime.IExtensionRegistry;
import org.eclipse.core.runtime.Platform;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.manipulation.CoreASTProvider;
import org.eclipse.jdt.ls.core.internal.JsonMessageHelper;
import org.eclipse.lsp4j.CompletionItem;
import org.eclipse.lsp4j.CompletionList;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.osgi.framework.Bundle;
import org.osgi.framework.FrameworkUtil;

/**
 * @author Simeon Andreev
 *
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
public class ContributedCompletionHandlerTest extends AbstractCompilationUnitBasedTest {

	@BeforeEach
	public void setUp() {
		mockLSP3Client();
		CoreASTProvider sharedASTProvider = CoreASTProvider.getInstance();
		sharedASTProvider.disposeAST();
	}

	@Test
	public void testCompletion_contributedHandler() throws Exception {
		String xml =
				"""
				<plugin>
				   <extension
				      id="testCompletionHandler"
				      point="org.eclipse.jdt.ls.core.completionHandler">
				      <completionHandler
				          id="testHandler"
				          class="org.eclipse.jdt.ls.core.internal.handlers.ContributedTestCompletionHandler" />
				   </extension>
				</plugin>
				""";
		ByteArrayInputStream is = new ByteArrayInputStream(xml.getBytes());
		IExtensionRegistry registry = Platform.getExtensionRegistry();
		Bundle bundle = FrameworkUtil.getBundle(getClass());
		String bundleName = bundle.getSymbolicName();
		IContributor contributor = ContributorFactoryOSGi.createContributor(bundle);
		Object userKey = ((ExtensionRegistry) registry).getTemporaryUserToken();
		IFile file = project.getFile("test.txt");
		try {
			file.create(new String("test").getBytes(), IResource.FORCE, monitor);
			boolean result = registry.addContribution(is, contributor, false, "testContributedHandler", null, userKey);
			assertTrue(result, "Failed to register completion handler");
			when(preferenceManager.getClientPreferences().isCompletionItemLabelDetailsSupport()).thenReturn(true);
			CompletionList list = requestCompletions(file.getLocationURI().toString(), 0, 0);
			assertNotNull(list);
			List<CompletionItem> filtered = list.getItems().stream().filter((item) -> {
				return item.getDetail() != null && item.getDetail().startsWith(ContributedTestCompletionHandler.TEST_DETAIL);
			}).collect(Collectors.toList());
			assertEquals(1, filtered.size(), "No test proposals");
			CompletionItem oride = filtered.get(0);
			assertEquals(ContributedTestCompletionHandler.TEST_CONTENT, oride.getInsertText());
		} finally {
			file.delete(true, monitor);
			IExtension extension = registry.getExtension(bundleName + ".testCompletionHandler");
			assertNotNull(extension, "Failed to find completion finder extension");
			boolean result = registry.removeExtension(extension, userKey);
			assertTrue(result, "Failed to unregister completion handler");
		}
	}

	private CompletionList requestCompletions(String uri, int line, int offset) throws JavaModelException {
		return server.completion(JsonMessageHelper.getParams(CompletionHandlerTest.createCompletionRequest(uri, line, offset))).join().getRight();
	}

	private void mockLSP3Client() {
		CompletionHandlerTest.mockLSPClient(preferenceManager, true, true);
	}
}
