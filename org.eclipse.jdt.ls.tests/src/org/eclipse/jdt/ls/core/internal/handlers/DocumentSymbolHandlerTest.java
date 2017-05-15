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
		importProjects("maven/salut");
		project = WorkspaceHelper.getProject("salut");
		handler = new DocumentSymbolHandler();
	}

	@Test
	public void testDocumentSymbolHandler() throws Exception {
		testClass("org.apache.commons.lang3.text.WordUtils");
	}

	@Test
	public void testSyntheticMember() throws Exception {
		String className = "org.apache.commons.lang3.text.StrTokenizer";
		List<? extends SymbolInformation> symbols = getSymbols(className);
		boolean overloadedMethod1Found = false;
		boolean overloadedMethod2Found = false;
		String overloadedMethod1 = "getCSVInstance(String)";
		String overloadedMethod2 = "reset()";
		for (SymbolInformation symbol : symbols) {
			Location loc = symbol.getLocation();
			assertTrue("Class: " + className + ", Symbol:" + symbol.getName() + " - invalid location.",
					loc != null && isValid(loc.getRange()));
			assertFalse("Class: " + className + ", Symbol:" + symbol.getName() + " - invalid name",
					symbol.getName().startsWith("access$"));
			assertFalse("Class: " + className + ", Symbol:" + symbol.getName() + "- invalid name",
					symbol.getName().equals("<clinit>"));
			if (overloadedMethod1.equals(symbol.getName())) {
				overloadedMethod1Found = true;
			}
			if (overloadedMethod2.equals(symbol.getName())) {
				overloadedMethod2Found = true;
			}
		}
		assertTrue("The " + overloadedMethod1 + " method hasn't been found", overloadedMethod1Found);
		assertTrue("The " + overloadedMethod2 + " method hasn't been found", overloadedMethod2Found);
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
