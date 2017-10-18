/*******************************************************************************
 * Copyright (c) 2016-2017 Red Hat Inc. and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Red Hat Inc. - initial API and implementation
 *******************************************************************************/
package org.eclipse.jdt.ls.core.internal.handlers;

import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.UnsupportedEncodingException;
import java.util.List;
import java.util.concurrent.ExecutionException;

import org.eclipse.core.resources.IProject;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.ls.core.internal.ClassFileUtil;
import org.eclipse.jdt.ls.core.internal.WorkspaceHelper;
import org.eclipse.jdt.ls.core.internal.managers.AbstractProjectsManagerBasedTest;
import org.eclipse.jdt.ls.core.internal.preferences.PreferenceManager;
import org.eclipse.jdt.ls.core.internal.preferences.Preferences;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.SymbolInformation;
import org.eclipse.lsp4j.SymbolKind;
import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.eclipse.lsp4j.TextDocumentPositionParams;
import org.junit.Before;
import org.junit.Test;

/**
 * @author snjeza
 */
public class ImplementorsHandlerTest extends AbstractProjectsManagerBasedTest {
	private IProject project;
	private ImplementorsHandler handler;

	@Before
	public void setup() throws Exception {
		importProjects("eclipse/hello");
		project = WorkspaceHelper.getProject("hello");
		preferenceManager = mock(PreferenceManager.class);
		when(preferenceManager.getPreferences()).thenReturn(new Preferences());
		handler = new ImplementorsHandler();
	}

	@Test
	public void testInterface() throws Exception {
		String className = "org.sample.I";
		Position position = new Position(2, 18);
		List<? extends SymbolInformation> symbols = getSymbols(className, position);
		assertTrue(symbols.size() > 0);
		for (SymbolInformation symbol : symbols) {
			assertKind(SymbolKind.Class, symbol);
			assertTrue(isValid(symbol.getLocation().getRange()));
		}
		className = "org.sample.I2";
		position = new Position(2, 19);
		symbols = getSymbols(className, position);
		assertTrue(symbols.size() > 0);
		for (SymbolInformation symbol : symbols) {
			assertKind(SymbolKind.Interface, symbol);
			assertTrue(isValid(symbol.getLocation().getRange()));
		}
	}

	@Test
	public void testClass() throws Exception {
		String className = "org.sample.A";
		Position position = new Position(2, 23);
		List<? extends SymbolInformation> symbols = getSymbols(className, position);
		assertTrue(symbols.size() > 0);
		for (SymbolInformation symbol : symbols) {
			assertKind(SymbolKind.Class, symbol);
			assertTrue(isValid(symbol.getLocation().getRange()));
		}
	}

	private boolean isValid(Range range) {
		return range != null && isValid(range.getStart()) && isValid(range.getEnd());
	}

	private boolean isValid(Position position) {
		return position != null && position.getLine() >= 0 && position.getCharacter() >= 0;
	}

	private void assertKind(SymbolKind expectedKind, SymbolInformation symbol) {
		assertSame("Unexpected SymbolKind in " + symbol.getName(), expectedKind, symbol.getKind());
	}

	private List<? extends SymbolInformation> getSymbols(String className, Position position)
			throws JavaModelException, UnsupportedEncodingException, InterruptedException, ExecutionException {
		String uri = ClassFileUtil.getURI(project, className);
		TextDocumentIdentifier textDocument = new TextDocumentIdentifier(uri);
		TextDocumentPositionParams params = new TextDocumentPositionParams(textDocument, position);
		List<? extends SymbolInformation> symbols = handler.implementors(params, monitor);
		return symbols;
	}

}
