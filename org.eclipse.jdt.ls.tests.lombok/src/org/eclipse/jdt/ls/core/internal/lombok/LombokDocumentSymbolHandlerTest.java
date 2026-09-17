/*******************************************************************************
 * Copyright (c) 2026 IBM Corp. and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *     IBM Corp. - initial API and implementation
 *******************************************************************************/
package org.eclipse.jdt.ls.core.internal.lombok;

import static java.util.stream.Collectors.toList;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import java.io.UnsupportedEncodingException;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutionException;

import org.eclipse.core.resources.IProject;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.ls.core.internal.ClassFileUtil;
import org.eclipse.jdt.ls.core.internal.WorkspaceHelper;
import org.eclipse.jdt.ls.core.internal.handlers.DocumentSymbolHandler;
import org.eclipse.jdt.ls.core.internal.managers.AbstractProjectsManagerBasedTest;
import org.eclipse.lsp4j.DocumentSymbolParams;
import org.eclipse.lsp4j.SymbolInformation;
import org.eclipse.lsp4j.SymbolKind;
import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class LombokDocumentSymbolHandlerTest extends AbstractProjectsManagerBasedTest {

	private IProject project;

	@BeforeEach
	public void setup() throws Exception {
		importProjects("maven/mavenlombok");
		project = WorkspaceHelper.getProject("mavenlombok");
	}

	@Test
	public void testLombok() throws Exception {
		String className = "org.sample.Test";
		List<? extends SymbolInformation> symbols = getSymbols(className);
		//@formatter:on
		assertFalse(symbols.isEmpty(), "No symbols found for " + className);
		assertHasSymbol("Test", "Test.java", SymbolKind.Class, symbols);
		Optional<? extends SymbolInformation> method = symbols.stream().filter(s -> (s.getKind() == SymbolKind.Method)).findAny();
		assertFalse(method.isPresent());
	}

	@Test
	public void testLombok_showGeneratedCodeSymbols() throws Exception {
		preferences.setShowGeneratedCodeSymbols(true);
		try {
			String className = "org.sample.Test";
			List<? extends SymbolInformation> symbols = getSymbols(className);
			assertFalse(symbols.isEmpty(), "No symbols found for " + className);
			assertHasSymbol("Test", "Test.java", SymbolKind.Class, symbols);
			Optional<? extends SymbolInformation> method = symbols.stream().filter(s -> (s.getKind() == SymbolKind.Method)).findAny();
			assertTrue(method.isPresent(), "Generated methods should appear when java.symbols.includeGeneratedCode is true");
		} finally {
			preferences.setShowGeneratedCodeSymbols(false);
		}
	}

	private void assertHasSymbol(String expectedType, String expectedParent, SymbolKind expectedKind, Collection<? extends SymbolInformation> symbols) {
		Optional<? extends SymbolInformation> symbol = symbols.stream()
															.filter(s -> expectedType.equals(s.getName()) && expectedParent.equals(s.getContainerName()))
															.findFirst();
		assertTrue(symbol.isPresent(), expectedType + " (" + expectedParent + ")" + " is missing from " + symbols);
		assertKind(expectedKind, symbol.get());
	}

	private void assertKind(SymbolKind expectedKind, SymbolInformation symbol) {
		assertSame(expectedKind, symbol.getKind(), "Unexpected SymbolKind in " + symbol.getName());
	}

	private List<? extends SymbolInformation> getSymbols(String className)
			throws JavaModelException, UnsupportedEncodingException, InterruptedException, ExecutionException {
		String uri = ClassFileUtil.getURI(project, className);
		TextDocumentIdentifier identifier = new TextDocumentIdentifier(uri);
		DocumentSymbolParams params = new DocumentSymbolParams();
		params.setTextDocument(identifier);
		when(preferenceManager.getClientPreferences().isHierarchicalDocumentSymbolSupported()).thenReturn(false);
		//@formatter:off
		List<SymbolInformation> symbols = new DocumentSymbolHandler(preferenceManager)
				.documentSymbol(params, monitor).stream()
				.map(Either::getLeft).collect(toList());
		//@formatter:on
		assertFalse(symbols.isEmpty(), "No symbols found for " + className);
		return symbols;
	}

}
