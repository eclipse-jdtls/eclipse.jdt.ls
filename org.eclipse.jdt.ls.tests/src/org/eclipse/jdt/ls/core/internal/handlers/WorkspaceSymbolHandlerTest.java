/*******************************************************************************
 * Copyright (c) 2017 Red Hat Inc. and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Red Hat Inc. - initial API and implementation
 *******************************************************************************/
package org.eclipse.jdt.ls.core.internal.handlers;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.util.List;

import org.eclipse.jdt.ls.core.internal.JDTUtils;
import org.eclipse.jdt.ls.core.internal.managers.AbstractProjectsManagerBasedTest;
import org.eclipse.lsp4j.Location;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.SymbolInformation;
import org.eclipse.lsp4j.SymbolKind;
import org.junit.Before;
import org.junit.Test;

/**
 * @author Fred Bricon
 */
public class WorkspaceSymbolHandlerTest extends AbstractProjectsManagerBasedTest {

	private WorkspaceSymbolHandler handler;

	@Before
	public void setup() throws Exception {
		importProjects("eclipse/hello");//We need at least 1 project
		handler = new WorkspaceSymbolHandler();
	}

	@Test
	public void testSearchWithEmptyResults() {
		List<SymbolInformation> results = handler.search(null, monitor);
		assertNotNull(results);
		assertEquals(0, results.size());

		results = handler.search("  ", monitor);
		assertNotNull(results);
		assertEquals(0, results.size());

		results = handler.search("Abracadabra", monitor);
		assertNotNull(results);
		assertEquals(0, results.size());
	}


	@Test
	public void testWorkspaceSearch() {
		String query = "Array";
		List<SymbolInformation> results = handler.search(query, monitor);
		assertNotNull(results);
		assertEquals("Unexpected results", 11, results.size());
		Range defaultRange = JDTUtils.newRange();
		for (SymbolInformation symbol : results) {
			assertNotNull("Kind is missing", symbol.getKind());
			assertNotNull("ContainerName is missing", symbol.getContainerName());
			assertTrue(symbol.getName().startsWith(query));
			Location location = symbol.getLocation();
			assertEquals(defaultRange, location.getRange());
			//No class in the workspace project starts with Array, so everything comes from the JDK
			assertTrue("Unexpected uri "+ location.getUri(), location.getUri().startsWith("jdt://"));
		}
	}

	@Test
	public void testWorkspaceSearchOnFileInWorkspace() {
		String query = "Baz";
		List<SymbolInformation> results = handler.search(query, monitor);
		assertNotNull(results);
		assertEquals("Unexpected results", 2, results.size());
		Range defaultRange = JDTUtils.newRange();
		for (SymbolInformation symbol : results) {
			assertNotNull("Kind is missing", symbol.getKind());
			assertNotNull("ContainerName is missing", symbol.getContainerName());
			assertTrue(symbol.getName().startsWith(query));
			Location location = symbol.getLocation();
			assertNotEquals("Range should not equal the default range", defaultRange, location.getRange());
			assertTrue("Unexpected uri " + location.getUri(), location.getUri().startsWith("file://"));
		}
	}

	@Test
	public void testProjectSearch() {
		String query = "IFoo";
		List<SymbolInformation> results = handler.search(query, monitor);
		assertNotNull(results);
		assertEquals("Found "+ results.size() + " results", 1, results.size());
		SymbolInformation symbol = results.get(0);
		assertEquals(SymbolKind.Interface, symbol.getKind());
		assertEquals("java", symbol.getContainerName());
		assertEquals(query, symbol.getName());
		Location location = symbol.getLocation();
		assertNotEquals("Range should not equal the default range", JDTUtils.newRange(), location.getRange());
		assertTrue("Unexpected uri "+ location.getUri(), location.getUri().endsWith("Foo.java"));
	}

	@Test
	public void testCamelCaseSearch() {
		List<SymbolInformation> results = handler.search("NPE", monitor);
		assertNotNull(results);
		assertEquals("NullPointerException", results.get(0).getName());

		results = handler.search("HaMa", monitor);
		String className = "HashMap";
		boolean foundClass = results.stream().filter(s -> className.equals(s.getName())).findFirst().isPresent();
		assertTrue("Did not find "+className, foundClass);
	}

}
