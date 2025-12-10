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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.jdt.ls.core.internal.JDTUtils;
import org.eclipse.jdt.ls.core.internal.managers.AbstractProjectsManagerBasedTest;
import org.eclipse.lsp4j.Location;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.SymbolInformation;
import org.eclipse.lsp4j.SymbolKind;
import org.eclipse.lsp4j.SymbolTag;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * @author Fred Bricon
 */
public class WorkspaceSymbolHandlerTest extends AbstractProjectsManagerBasedTest {

	@BeforeEach
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
		assertEquals(0, results.size(), "Unexpected results");

		//... but workspace classes can still be found
		testWorkspaceSearchOnFileInWorkspace();
	}

	@Test
	public void testWorkspaceSearch() {
		String query = "Array";
		List<SymbolInformation> results = WorkspaceSymbolHandler.search(query, monitor);
		assertNotNull(results);
		assertEquals(11, results.size(), "Unexpected results");
		Range defaultRange = JDTUtils.newRange();
		for (SymbolInformation symbol : results) {
			assertNotNull(symbol.getKind(), "Kind is missing");
			assertNotNull(symbol.getContainerName(), "ContainerName is missing");
			assertTrue(symbol.getName().startsWith(query));
			Location location = symbol.getLocation();
			assertEquals(defaultRange, location.getRange());
			//No class in the workspace project starts with Array, so everything comes from the JDK
			assertTrue(location.getUri().startsWith("jdt://"), "Unexpected uri "+ location.getUri());
		}
	}

	@Test
	public void testWorkspaceSearchOnFileInWorkspace() {
		String query = "Baz";
		List<SymbolInformation> results = WorkspaceSymbolHandler.search(query, monitor);
		assertNotNull(results);
		Range defaultRange = JDTUtils.newRange();
		assertEquals(2, results.size(), "Unexpected results");
		for (SymbolInformation symbol : results) {
			assertNotNull(symbol.getKind(), "Kind is missing");
			assertNotNull(symbol.getContainerName(), "ContainerName is missing");
			assertTrue(symbol.getName().startsWith(query));
			Location location = symbol.getLocation();
			assertNotEquals(defaultRange, location.getRange(), "Range should not equal the default range");
			assertTrue(location.getUri().startsWith("file://"), "Unexpected uri " + location.getUri());
		}
	}

	@Test
	public void testProjectSearch() {
		String query = "IFoo";
		List<SymbolInformation> results = WorkspaceSymbolHandler.search(query, monitor);
		assertNotNull(results);
		assertEquals(2, results.size(), "Found " + results.size() + " results");
		assertTrue(results.stream().anyMatch(s -> "org.sample".equals(s.getContainerName())));
		assertTrue(results.stream().anyMatch(s -> "java".equals(s.getContainerName())));
		SymbolInformation symbol = results.get(0);
		assertEquals(SymbolKind.Interface, symbol.getKind());
		assertEquals(query, symbol.getName());
		Location location = symbol.getLocation();
		assertNotEquals(JDTUtils.newRange(), location.getRange(), "Range should not equal the default range");
		assertTrue(location.getUri().endsWith("Foo.java"), "Unexpected uri "+ location.getUri());
	}

	@Test
	public void testCamelCaseSearch() {
		List<SymbolInformation> results = WorkspaceSymbolHandler.search("NPE", monitor);
		assertTrue(results.size() > 0);
		assertTrue(results.stream().anyMatch(s -> s.getName().equals("NullPointerException")));

		results = WorkspaceSymbolHandler.search("HaMa", monitor);
		String className = "HashMap";
		boolean foundClass = results.stream().filter(s -> className.equals(s.getName())).findFirst().isPresent();
		assertTrue(foundClass, "Did not find " + className);
	}

	@Test
	public void testCamelCaseFuzzySearch() {
		Set<String> expected = new HashSet<>(Arrays.asList("BufferedInputStream", "BufferedOutputStream", "StringBufferInputStream"));
		List<SymbolInformation> results = WorkspaceSymbolHandler.search("BuffStream", monitor);
		assertTrue(results.size() > 0);
		assertTrue(results.stream().allMatch(s -> expected.contains(s.getName())));

		results = WorkspaceSymbolHandler.search("inkSet", monitor);
		String className = "LinkedHashSet";
		boolean foundClass = results.stream().filter(s -> className.equals(s.getName())).findFirst().isPresent();
		assertTrue(foundClass, "Did not find "+className);
	}

	@Test
	public void testSearchSourceOnly() {
		String query = "B*";
		List<SymbolInformation> results = WorkspaceSymbolHandler.search(query, "hello", true, monitor);
		assertNotNull(results);
		assertEquals(6, results.size(), "Found " + results.size() + "result");
		String className = "BaseTest";
		boolean foundClass = results.stream().filter(s -> className.equals(s.getName())).findFirst().isPresent();
		assertTrue(foundClass, "Did not find " + className);
	}

	@Test
	public void testSearchReturnMaxResults() {
		String query = "B*";
		List<SymbolInformation> results = WorkspaceSymbolHandler.search(query, 2, "hello", true, monitor);
		assertNotNull(results);
		assertEquals( 2, results.size(),"Found " + results.size() + "result");
	}

	@Test
	public void testEmptyNames() throws Exception {
		importProjects("maven/reactor");
		IProject project = ResourcesPlugin.getWorkspace().getRoot().getProject("reactor");
		assertIsJavaProject(project);
		String query = "Mono";
		List<SymbolInformation> results = WorkspaceSymbolHandler.search(query, 0, "reactor", false, monitor);
		assertNotNull(results);
		assertEquals(119, results.size(), "Found ");
		boolean hasEmptyName = results.stream().filter(s -> (s.getName() == null || s.getName().isEmpty())).findFirst().isPresent();
		assertFalse(hasEmptyName, "Found empty name");
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
	public void testSearchPartialPackage() {
		List<SymbolInformation> results = WorkspaceSymbolHandler.search("util.Array", monitor);
		assertTrue(results.size() > 1);
		assertTrue(results.stream().anyMatch(s -> s.getName().equals("ArrayList") && s.getContainerName().equals("java.util")));

		results = WorkspaceSymbolHandler.search("util.Pattern", monitor);
		assertTrue(results.size() > 1);
		assertTrue(results.stream().anyMatch(s -> s.getName().equals("Pattern") && s.getContainerName().equals("java.util.regex")));
	}

	@Test
	public void testSearchWithoutDuplicate() {
		List<SymbolInformation> results = WorkspaceSymbolHandler.search("*", monitor);
		Set<SymbolInformation> resultsSet = new HashSet<>(results);
		assertEquals(results.size(), resultsSet.size());
	}

	@Test
	public void testSearchSourceMethodDeclarations() {
		preferences.setIncludeSourceMethodDeclarations(true);
		List<SymbolInformation> results = WorkspaceSymbolHandler.search("deleteSomething", "hello", true, monitor);
		assertNotNull(results);
		assertEquals(1, results.size(), "Found " + results.size() + " result");
		SymbolInformation res = results.get(0);
		assertEquals(SymbolKind.Method, res.getKind());
		assertEquals(res.getContainerName(), "org.sample.Baz");

		results = WorkspaceSymbolHandler.search("main", "hello", true, monitor);
		assertNotNull(results);
		assertEquals(11, results.size(), "Found " + results.size() + " result");
		boolean allMethods = results.stream().allMatch(s -> s.getKind() == SymbolKind.Method);
		assertTrue(allMethods, "Found a non-method symbol");
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
		assertTrue(deprecated.getTags().contains(SymbolTag.Deprecated), "Should have deprecated tag");

		SymbolInformation notDeprecated = results.stream()
			.filter(symbol -> symbol.getContainerName().equals("java.security.cert"))
			.findFirst().orElse(null);

		assertNotNull(notDeprecated);
		if (notDeprecated.getTags() != null) {
			assertFalse(notDeprecated.getTags().contains(SymbolTag.Deprecated), "Should not have deprecated tag");
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
		assertTrue(deprecated.getDeprecated(), "Should be deprecated");
	}

	@Test
	public void testWorkspaceSearchWithClassContentSupport() {
		when(preferenceManager.isClientSupportsClassFileContent()).thenReturn(true);
		//Classes will be found with jar container path.
		List<SymbolInformation> results = WorkspaceSymbolHandler.search("Array", monitor);
		assertNotNull(results);
		assertNotEquals(0, results.size(), "Unexpected results");
		// sample just the first symbol
		assertNotNull(results.get(0).getLocation(), "Location is null");
		assertNotNull(results.get(0).getLocation().getUri(), "Location URI is null");
		assertTrue(results.get(0).getLocation().getUri().contains("rtstubs.jar/"), "Wrong location URI");
	}
}
