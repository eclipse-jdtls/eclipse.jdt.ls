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

import static org.junit.Assert.assertTrue;

import java.io.UnsupportedEncodingException;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import org.eclipse.core.resources.IProject;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.ls.core.internal.ResourceUtils;
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

	/**
	 * @param handler
	 * @throws JavaModelException
	 * @throws UnsupportedEncodingException
	 * @throws InterruptedException
	 * @throws ExecutionException
	 */
	private void testClass(String className)
			throws JavaModelException, UnsupportedEncodingException, InterruptedException, ExecutionException {
		String uri = ResourceUtils.getURI(project, className);
		TextDocumentIdentifier identifier = new TextDocumentIdentifier(uri);
		DocumentSymbolParams params = new DocumentSymbolParams();
		params.setTextDocument(identifier);
		CompletableFuture<List<? extends SymbolInformation>> future = handler.documentSymbol(params);
		List<? extends SymbolInformation> symbols = future.get();
		assertTrue(symbols.size() > 0);
		for (SymbolInformation symbol : symbols) {
			Location loc = symbol.getLocation();
			assertTrue("Class: " + className + ", Symbol:" + symbol.getName() + " - invalid location.",
					loc != null && isValid(loc.getRange()));
		}
	}

	/**
	 * @param range
	 * @return
	 */
	private boolean isValid(Range range) {
		return range != null && isValid(range.getStart()) && isValid(range.getEnd());
	}

	/**
	 * @param position
	 */
	private boolean isValid(Position position) {
		return position != null && position.getLine() >= 0 && position.getCharacter() >= 0;
	}

}
