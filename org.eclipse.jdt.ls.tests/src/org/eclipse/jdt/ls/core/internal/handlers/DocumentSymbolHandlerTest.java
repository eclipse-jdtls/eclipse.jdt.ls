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

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.UnsupportedEncodingException;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import org.eclipse.core.resources.IProject;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.ls.core.internal.ClassFileUtil;
import org.eclipse.jdt.ls.core.internal.WorkspaceHelper;
import org.eclipse.jdt.ls.core.internal.managers.AbstractProjectsManagerBasedTest;
import org.eclipse.lsp4j.DocumentSymbolParams;
import org.eclipse.lsp4j.Location;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.SymbolInformation;
import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.junit.Before;
import org.junit.Test;

/**
 * @author snjeza
 */
public class DocumentSymbolHandlerTest extends AbstractProjectsManagerBasedTest {
	private IProject project;
	private DocumentSymbolHandler handler;

	@Before
	public void setup() throws Exception {
		importProjects("eclipse/hello");
		project = WorkspaceHelper.getProject("hello");
		handler = new DocumentSymbolHandler();
	}

	@Test
	public void testDocumentSymbolHandler() throws Exception {
		testClass("java.util.LinkedHashMap");
		testClass("java.util.HashMap");
		testClass("java.util.Set");
	}

	@Test
	public void testSyntheticMember() throws Exception {
		String className = "java.util.zip.ZipFile";
		List<? extends SymbolInformation> symbols = getSymbols(className);
		boolean getEntryFound = false;
		boolean getNameFound = false;
		String getEntry = "getEntry(String)";
		String getName = "getName()";
		for (SymbolInformation symbol : symbols) {
			Location loc = symbol.getLocation();
			assertTrue("Class: " + className + ", Symbol:" + symbol.getName() + " - invalid location.",
					loc != null && isValid(loc.getRange()));
			assertFalse("Class: " + className + ", Symbol:" + symbol.getName() + " - invalid name",
					symbol.getName().startsWith("access$"));
			assertFalse("Class: " + className + ", Symbol:" + symbol.getName() + "- invalid name",
					symbol.getName().equals("<clinit>"));
			if (getEntry.equals(symbol.getName())) {
				getEntryFound = true;
			}
			if (getName.equals(symbol.getName())) {
				getNameFound = true;
			}
		}
		assertTrue("The " + getEntry + " method hasn't been found", getEntryFound);
		assertTrue("The " + getName + " method hasn't been found", getNameFound);
	}

	private void testClass(String className)
			throws JavaModelException, UnsupportedEncodingException, InterruptedException, ExecutionException {
		List<? extends SymbolInformation> symbols = getSymbols(className);
		for (SymbolInformation symbol : symbols) {
			Location loc = symbol.getLocation();
			assertTrue("Class: " + className + ", Symbol:" + symbol.getName() + " - invalid location.",
					loc != null && isValid(loc.getRange()));
		}
	}

	private List<? extends SymbolInformation> getSymbols(String className)
			throws JavaModelException, UnsupportedEncodingException, InterruptedException, ExecutionException {
		String uri = ClassFileUtil.getURI(project, className);
		TextDocumentIdentifier identifier = new TextDocumentIdentifier(uri);
		DocumentSymbolParams params = new DocumentSymbolParams();
		params.setTextDocument(identifier);
		CompletableFuture<List<? extends SymbolInformation>> future = handler.documentSymbol(params);
		List<? extends SymbolInformation> symbols = future.get();
		assertTrue(symbols.size() > 0);
		return symbols;
	}

	private boolean isValid(Range range) {
		return range != null && isValid(range.getStart()) && isValid(range.getEnd());
	}

	private boolean isValid(Position position) {
		return position != null && position.getLine() >= 0 && position.getCharacter() >= 0;
	}


}
