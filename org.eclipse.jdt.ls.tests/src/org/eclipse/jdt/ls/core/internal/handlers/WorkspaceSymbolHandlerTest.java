/*******************************************************************************
 * Copyright (c) 2017 Red Hat Inc. and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *     Red Hat Inc. - initial API and implementation
 *******************************************************************************/
package org.eclipse.jdt.ls.core.internal.handlers;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.when;

import java.util.List;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.jdt.ls.core.internal.JDTUtils;
import org.eclipse.jdt.ls.core.internal.managers.AbstractProjectsManagerBasedTest;
import org.eclipse.lsp4j.Location;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.SymbolInformation;
import org.eclipse.lsp4j.SymbolKind;
import org.eclipse.lsp4j.SymbolTag;
import org.junit.Before;
import org.junit.Test;

/**
 * @author Fred Bricon
 */
public class WorkspaceSymbolHandlerTest extends AbstractProjectsManagerBasedTest {

	@Before
	public void setup() throws Exception {
		importProjects("eclipse/hello");//We need at least 1 project
	}

	@Test
	public void testSearchWithEmptyResults() {
		List<SymbolInformation> results = WorkspaceSymbolHandler.search(null, monitor);
		assertNotNull(results);
		assertEquals(0, results.size());

		results = WorkspaceSymbolHandler.search("  ", monitor);
		assertNotNull(results);
		assertEquals(0, results.size());

		results = WorkspaceSymbolHandler.search("Abracadabra", monitor);
		assertNotNull(results);
		assertEquals(0, results.size());
	}


	@Test
	public void testWorkspaceSearchNoClassContentSupport() {
		when(preferenceManager.isClientSupportsClassFileContent()).thenReturn(false);
		//No classes from binaries can be found
		List<SymbolInformation> results = WorkspaceSymbolHandler.search("Array", monitor);
		assertNotNull(results);
		assertEquals("Unexpected results", 0, results.size());

		//... but workspace classes can still be found
		testWorkspaceSearchOnFileInWorkspace();
	}

	@Test
	public void testWorkspaceSearch() {
		String query = "Array";
		List<SymbolInformation> results = WorkspaceSymbolHandler.search(query, monitor);
		assertNotNull(results);
		assertEquals("Unexpected results", 22, results.size());
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
		List<SymbolInformation> results = WorkspaceSymbolHandler.search(query, monitor);
		assertNotNull(results);
		Range defaultRange = JDTUtils.newRange();
		assertEquals("Unexpected results", 2, results.size());
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
		List<SymbolInformation> results = WorkspaceSymbolHandler.search(query, monitor);
		assertNotNull(results);
		assertEquals("Found " + results.size() + " results", 2, results.size());
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
		List<SymbolInformation> results = WorkspaceSymbolHandler.search("NPE", monitor);
		assertNotNull(results);
		assertEquals("NullPointerException", results.get(0).getName());

		results = WorkspaceSymbolHandler.search("HaMa", monitor);
		String className = "HashMap";
		boolean foundClass = results.stream().filter(s -> className.equals(s.getName())).findFirst().isPresent();
		assertTrue("Did not find "+className, foundClass);
	}

	@Test
	public void testSearchSourceOnly() {
		String query = "B*";
		List<SymbolInformation> results = WorkspaceSymbolHandler.search(query, "hello", true, monitor);
		assertNotNull(results);
		assertEquals("Found " + results.size() + "result", 6, results.size());
		String className = "BaseTest";
		boolean foundClass = results.stream().filter(s -> className.equals(s.getName())).findFirst().isPresent();
		assertTrue("Did not find " + className, foundClass);
	}

	@Test
	public void testSearchReturnMaxResults() {
		String query = "B*";
		List<SymbolInformation> results = WorkspaceSymbolHandler.search(query, 2, "hello", true, monitor);
		assertNotNull(results);
		assertEquals("Found " + results.size() + "result", 2, results.size());
	}

	@Test
	public void testEmptyNames() throws Exception {
		importProjects("maven/reactor");
		IProject project = ResourcesPlugin.getWorkspace().getRoot().getProject("reactor");
		assertIsJavaProject(project);
		String query = "Mono";
		List<SymbolInformation> results = WorkspaceSymbolHandler.search(query, 0, "reactor", false, monitor);
		assertNotNull(results);
		assertEquals("Found ", 119, results.size());
		boolean hasEmptyName = results.stream().filter(s -> (s.getName() == null || s.getName().isEmpty())).findFirst().isPresent();
		assertFalse("Found empty name", hasEmptyName);
	}

	@Test
	public void testSearchQualifiedTypeNoWildcards() {
		List<SymbolInformation> results = WorkspaceSymbolHandler.search("java.io.file", monitor);
		assertTrue(results.size() > 1);
		assertTrue(results.stream().anyMatch(s -> s.getName().startsWith("File") && "java.io".equals(s.getContainerName())));

		results = WorkspaceSymbolHandler.search("java.util.array", monitor);
		assertTrue(results.size() > 1);
		assertTrue(results.stream().anyMatch(s -> s.getName().startsWith("Array") && "java.util".equals(s.getContainerName())));
	}

	@Test
	public void testSearchQualifiedTypeWithWildcards() {
		List<SymbolInformation> results = WorkspaceSymbolHandler.search("java.util.*list*", monitor);
		assertTrue(results.size() > 1);
		assertTrue(results.stream().anyMatch(s -> "List".equals(s.getName()) && "java.util".equals(s.getContainerName())));

		results = WorkspaceSymbolHandler.search("*.lang*.*exception", monitor);
		assertTrue(results.size() > 1);
		assertTrue(results.stream().allMatch(s -> s.getName().endsWith("Exception") && s.getContainerName().contains(".lang")));
	}

	@Test
	public void testSearchAllTypesOfPackage() {
		List<SymbolInformation> results = WorkspaceSymbolHandler.search("java.io", monitor);
		assertTrue(results.size() > 1);
		assertTrue(results.stream().anyMatch(s -> "File".equals(s.getName()) && "java.io".equals(s.getContainerName())));

		results = WorkspaceSymbolHandler.search("java.lang", monitor);
		assertTrue(results.size() > 1);
		assertTrue(results.stream().anyMatch(s -> "Exception".equals(s.getName()) && "java.lang".equals(s.getContainerName())));
	}

	@Test
	public void testSearchSourceMethodDeclarations() {
		preferences.setIncludeSourceMethodDeclarations(true);
		List<SymbolInformation> results = WorkspaceSymbolHandler.search("deleteSomething", "hello", true, monitor);
		assertNotNull(results);
		assertEquals("Found " + results.size() + " result", 1, results.size());
		SymbolInformation res = results.get(0);
		assertEquals(SymbolKind.Method, res.getKind());
		assertEquals(res.getContainerName(), "org.sample.Baz");

		results = WorkspaceSymbolHandler.search("main", "hello", true, monitor);
		assertNotNull(results);
		assertEquals("Found " + results.size() + " result", 11, results.size());
		boolean allMethods = results.stream().allMatch(s -> s.getKind() == SymbolKind.Method);
		assertTrue("Found a non-method symbol", allMethods);
		preferences.setIncludeSourceMethodDeclarations(false);
	}

	@Test
	public void testDeprecated() {
		when(preferenceManager.getClientPreferences().isSymbolTagSupported()).thenReturn(true);

		List<SymbolInformation> results = WorkspaceSymbolHandler.search("Certificate", monitor);
		assertNotNull(results);

		SymbolInformation deprecated = results.stream()
			.filter(symbol -> symbol.getContainerName().equals("java.security"))
			.findFirst().orElse(null);

		assertNotNull(deprecated);
		assertNotNull(deprecated.getTags());
		assertTrue("Should have deprecated tag", deprecated.getTags().contains(SymbolTag.Deprecated));

		SymbolInformation notDeprecated = results.stream()
			.filter(symbol -> symbol.getContainerName().equals("java.security.cert"))
			.findFirst().orElse(null);

		assertNotNull(notDeprecated);
		if (notDeprecated.getTags() != null) {
			assertFalse("Should not have deprecated tag", deprecated.getTags().contains(SymbolTag.Deprecated));
		}
	}

	@Test
	public void testDeprecated_property() {
		when(preferenceManager.getClientPreferences().isSymbolTagSupported()).thenReturn(false);

		List<SymbolInformation> results = WorkspaceSymbolHandler.search("Certificate", monitor);
		assertNotNull(results);

		SymbolInformation deprecated = results.stream()
			.filter(symbol -> symbol.getContainerName().equals("java.security"))
			.findFirst().orElse(null);

		assertNotNull(deprecated);
		assertNotNull(deprecated.getDeprecated());
		assertTrue("Should be deprecated", deprecated.getDeprecated());
	}

	@Test
	public void testWorkspaceSearchWithClassContentSupport() {
		when(preferenceManager.isClientSupportsClassFileContent()).thenReturn(true);
		//Classes will be found with jar container path.
		List<SymbolInformation> results = WorkspaceSymbolHandler.search("Array", monitor);
		assertNotNull(results);
		assertNotEquals("Unexpected results", 0, results.size());
		// sample just the first symbol
		assertNotNull("Location is null", results.get(0).getLocation());
		assertNotNull("Location URI is null", results.get(0).getLocation().getUri());
		assertTrue("Wrong location URI", results.get(0).getLocation().getUri().contains("rtstubs.jar/"));
	}
}
