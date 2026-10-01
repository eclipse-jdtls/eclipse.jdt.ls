/*******************************************************************************
 * Copyright (c) 2026 Red Hat Inc. and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *     Arcadiy Ivanov - initial API and implementation
 *******************************************************************************/
package org.eclipse.jdt.ls.core.internal.handlers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.net.URI;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.search.DerivedSourceSearchParticipant;
import org.eclipse.jdt.core.search.SearchEngine;
import org.eclipse.jdt.core.search.SearchParticipant;
import org.eclipse.jdt.ls.core.internal.HoverInfoProvider;
import org.eclipse.jdt.ls.core.internal.JDTUtils;
import org.eclipse.jdt.ls.core.internal.ResourceUtils;
import org.eclipse.jdt.ls.core.internal.WorkspaceHelper;
import org.eclipse.jdt.ls.core.internal.managers.AbstractProjectsManagerBasedTest;
import org.eclipse.jdt.ls.core.internal.preferences.PreferenceManager;
import org.eclipse.lsp4j.Location;
import org.eclipse.lsp4j.MarkedString;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.ReferenceContext;
import org.eclipse.lsp4j.ReferenceParams;
import org.eclipse.lsp4j.SymbolInformation;
import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.eclipse.lsp4j.TextDocumentPositionParams;
import org.eclipse.lsp4j.TypeHierarchyItem;
import org.eclipse.lsp4j.TypeHierarchyPrepareParams;
import org.eclipse.lsp4j.TypeHierarchySubtypesParams;
import org.eclipse.lsp4j.TypeHierarchySupertypesParams;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Tests for search participant integration in jdtls.
 * Validates both the infrastructure (API consistency) and behavioral
 * paths (no duplicates, correct language ID, fallback behavior).
 */
public class DerivedSourceSearchParticipantsTest extends AbstractProjectsManagerBasedTest {

	@BeforeEach
	public void resetParticipantCounters() {
		TestDerivedSourceSearchParticipant.reset();
	}

	// --- Infrastructure tests ---

	@Test
	public void testGetSearchParticipantsIncludesDefault() {
		SearchParticipant[] participants = SearchEngine.getSearchParticipants();
		assertNotNull(participants);
		assertTrue(participants.length >= 1, "Should contain at least the default participant");
		assertNotNull(participants[0]);
	}

	@Test
	public void testGetSearchParticipantsDefaultIsJavaParticipant() {
		SearchParticipant[] participants = SearchEngine.getSearchParticipants();
		SearchParticipant defaultParticipant = SearchEngine.getDefaultSearchParticipant();
		assertEquals(defaultParticipant.getClass(), participants[0].getClass(),
				"First participant should be the default Java search participant");
	}

	@Test
	public void testGetSearchParticipantsConsistentResults() {
		SearchParticipant[] first = SearchEngine.getSearchParticipants();
		SearchParticipant[] second = SearchEngine.getSearchParticipants();
		assertEquals(first.length, second.length,
				"Consecutive calls should return same number of participants");
		for (int i = 0; i < first.length; i++) {
			assertEquals(first[i].getClass(), second[i].getClass(),
					"Participant class at index " + i + " should be consistent");
		}
	}

	@Test
	public void testDefaultParticipantIsNotDerivedSourceSearchParticipant() throws Exception {
		SearchParticipant defaultParticipant = SearchEngine.getDefaultSearchParticipant();
		assertNotNull(defaultParticipant);
		assertTrue(!(defaultParticipant instanceof DerivedSourceSearchParticipant),
				"Default search participant should not be a DerivedSourceSearchParticipant");
	}

	@Test
	public void testResolveCompilationUnitNonJavaFileReturnsNull() throws Exception {
		importProjects("eclipse/hello");
		IProject project = WorkspaceHelper.getProject("hello");
		IFile nonJavaFile = project.getFile("src/java/Foo.kt");
		ICompilationUnit cu = JDTUtils.resolveCompilationUnit(nonJavaFile);
		assertNull(cu, "Non-Java file with no contributing participant should resolve to null");
	}

	@Test
	public void testResolveCompilationUnitJavaFileStillWorks() throws Exception {
		importProjects("eclipse/hello");
		IProject project = WorkspaceHelper.getProject("hello");
		IFile javaFile = project.getFile("src/java/Foo.java");
		assertTrue(javaFile.exists(), "Test file should exist");
		ICompilationUnit cu = JDTUtils.resolveCompilationUnit(javaFile);
		assertNotNull(cu, "Java file should still resolve via the normal path");
	}

	// --- Behavioral: workspace symbol search produces no duplicates ---

	@Test
	public void testWorkspaceSymbolSearchNoDuplicatesWithContributedParticipants() throws Exception {
		importProjects("eclipse/hello");
		List<SymbolInformation> results = WorkspaceSymbolHandler.search("*", monitor);
		Set<SymbolInformation> deduped = new HashSet<>(results);
		assertEquals(results.size(), deduped.size(),
				"Workspace symbol search should not produce duplicate entries");
	}

	@Test
	public void testWorkspaceSymbolSearchExactMatchNoDuplicates() throws Exception {
		importProjects("eclipse/hello");
		List<SymbolInformation> results = WorkspaceSymbolHandler.search("Foo", monitor);
		Set<SymbolInformation> deduped = new HashSet<>(results);
		assertEquals(results.size(), deduped.size(),
				"Exact match workspace symbol search should not produce duplicates");
	}

	// --- Behavioral: hover language ID for Java elements ---

	@Test
	public void testHoverLanguageIdIsJavaForJavaElements() throws Exception {
		importProjects("eclipse/hello");
		IProject project = WorkspaceHelper.getProject("hello");
		String uri = project.getFile("src/java/Foo.java").getLocationURI().toString();
		ICompilationUnit cu = JDTUtils.resolveCompilationUnit(uri);
		assertNotNull(cu);
		IType type = cu.findPrimaryType();
		assertNotNull(type, "Should find primary type Foo");
		MarkedString signature = HoverInfoProvider.computeSignature(type);
		assertNotNull(signature);
		assertEquals("java", signature.getLanguage(),
				"Java element hover should have language ID 'java'");
	}

	// --- Behavioral: findElementsAtSelection fallback ---

	@Test
	public void testFindElementsAtSelectionReturnsJavaElement() throws Exception {
		importProjects("eclipse/hello");
		IProject project = WorkspaceHelper.getProject("hello");
		String uri = project.getFile("src/java/Foo.java").getLocationURI().toString();
		ICompilationUnit cu = JDTUtils.resolveCompilationUnit(uri);
		assertNotNull(cu);
		// Find the class declaration — "Foo" is on line 5 (0-indexed)
		// codeSelect should resolve this directly without the fallback
		IJavaElement[] elements = JDTUtils.findElementsAtSelection(
				cu, 5, 13, preferenceManager, monitor);
		assertNotNull(elements);
		assertTrue(elements.length > 0, "Should find Foo class at selection");
		assertEquals("Foo", elements[0].getElementName());
	}

	@Test
	public void testFindElementsAtSelectionOnWhitespaceReturnsEmpty() throws Exception {
		importProjects("eclipse/hello");
		IProject project = WorkspaceHelper.getProject("hello");
		String uri = project.getFile("src/java/Foo.java").getLocationURI().toString();
		ICompilationUnit cu = JDTUtils.resolveCompilationUnit(uri);
		assertNotNull(cu);
		// Line 0, column 0 is likely a comment or whitespace — should return
		// null or empty, and the search participant fallback should also
		// return null gracefully
		IJavaElement[] elements = JDTUtils.findElementsAtSelection(
				cu, 0, 0, preferenceManager, monitor);
		assertTrue(elements == null || elements.length == 0,
				"Should return empty for whitespace/comment position");
	}

	// --- Behavioral: type hierarchy with no contributed participants ---

	@Test
	public void testTypeHierarchyNoDuplicateSubtypes() throws Exception {
		importProjects("maven/salut");
		IProject project = WorkspaceHelper.getProject("salut");
		TypeHierarchyHandler handler = new TypeHierarchyHandler();
		TypeHierarchyPrepareParams params = new TypeHierarchyPrepareParams();
		String uriString = project.getFile("src/main/java/org/sample/CallHierarchy.java")
				.getLocationURI().toString();
		params.setTextDocument(new TextDocumentIdentifier(uriString));
		params.setPosition(new Position(2, 43)); // Builder interface
		List<TypeHierarchyItem> items = handler.prepareTypeHierarchy(params, monitor);
		assertNotNull(items);
		assertEquals(1, items.size());
		TypeHierarchySubtypesParams subtypesParams = new TypeHierarchySubtypesParams();
		subtypesParams.setItem(items.get(0));
		List<TypeHierarchyItem> subtypes = handler.getSubtypeItems(subtypesParams, monitor);
		assertNotNull(subtypes);
		// Verify no duplicates from contributed participant supplementation
		Set<String> names = new HashSet<>();
		for (TypeHierarchyItem subtype : subtypes) {
			assertTrue(names.add(subtype.getName()),
					"Duplicate subtype: " + subtype.getName());
		}
	}

	@Test
	public void testTypeHierarchySupertypesStillWork() throws Exception {
		importProjects("maven/salut");
		IProject project = WorkspaceHelper.getProject("salut");
		TypeHierarchyHandler handler = new TypeHierarchyHandler();
		TypeHierarchyPrepareParams params = new TypeHierarchyPrepareParams();
		String uriString = project.getFile("src/main/java/org/sample/CallHierarchy.java")
				.getLocationURI().toString();
		params.setTextDocument(new TextDocumentIdentifier(uriString));
		params.setPosition(new Position(7, 27)); // FooBuilder class
		List<TypeHierarchyItem> items = handler.prepareTypeHierarchy(params, monitor);
		assertNotNull(items);
		assertEquals(1, items.size());
		TypeHierarchySupertypesParams supertypesParams = new TypeHierarchySupertypesParams();
		supertypesParams.setItem(items.get(0));
		List<TypeHierarchyItem> supertypes = handler.getSupertypeItems(supertypesParams, monitor);
		assertNotNull(supertypes);
		assertEquals(2, supertypes.size());
	}

	// --- Behavioral: NavigateToDefinition guard ---

	@Test
	public void testNavigateToDefinitionJavaFileStillWorks() throws Exception {
		importProjects("eclipse/hello");
		IProject project = WorkspaceHelper.getProject("hello");
		String uri = project.getFile("src/java/Foo.java").getLocationURI().toString();
		NavigateToDefinitionHandler handler = new NavigateToDefinitionHandler(preferenceManager);
		TextDocumentPositionParams posParams = new TextDocumentPositionParams();
		posParams.setTextDocument(new TextDocumentIdentifier(uri));
		posParams.setPosition(new Position(5, 13)); // "Foo" class name
		List<? extends org.eclipse.lsp4j.Location> definitions =
				handler.definition(posParams, monitor);
		assertNotNull(definitions);
		assertTrue(definitions.size() > 0,
				"Definition navigation should resolve for Java files");
	}

	// --- Contributed Language X participant tests ---

	@Test
	public void testContributedParticipantIsRegistered() {
		SearchParticipant[] participants = SearchEngine.getSearchParticipants();
		assertTrue(participants.length >= 2,
				"Should have at least 2 participants (default + Language X)");
		boolean found = false;
		for (SearchParticipant p : participants) {
			if (p instanceof TestDerivedSourceSearchParticipant) {
				found = true;
				break;
			}
		}
		assertTrue(found,
				"getSearchParticipants() should include TestDerivedSourceSearchParticipant");
	}

	@Test
	public void testContributedParticipantIsDerivedSourceSearchParticipant() {
		SearchParticipant[] participants = SearchEngine.getSearchParticipants();
		boolean found = false;
		for (SearchParticipant p : participants) {
			if (p instanceof DerivedSourceSearchParticipant && p instanceof TestDerivedSourceSearchParticipant) {
				found = true;
				break;
			}
		}
		assertTrue(found,
				"Contributed participant should be a DerivedSourceSearchParticipant");
	}

	@Test
	public void testParticipantInvokedDuringReferenceSearch() throws Exception {
		importProjects("eclipse/hello");
		IProject project = WorkspaceHelper.getProject("hello");
		PreferenceManager pm = mock(PreferenceManager.class);
		when(pm.getPreferences()).thenReturn(preferences);
		when(pm.isClientSupportsClassFileContent()).thenReturn(false);
		ReferencesHandler handler = new ReferencesHandler(pm);

		URI uri = project.getFile("src/java/Foo2.java").getRawLocationURI();
		String fileURI = ResourceUtils.fixURI(uri);
		ReferenceParams param = new ReferenceParams();
		param.setPosition(new Position(5, 16));
		param.setContext(new ReferenceContext(false));
		param.setTextDocument(new TextDocumentIdentifier(fileURI));

		TestDerivedSourceSearchParticipant.reset();
		handler.findReferences(param, monitor);

		assertTrue(TestDerivedSourceSearchParticipant.beginSearchingCount.get() > 0,
				"Contributed participant's beginSearching() should be called during reference search");
		assertTrue(TestDerivedSourceSearchParticipant.doneSearchingCount.get() > 0,
				"Contributed participant's doneSearching() should be called during reference search");
		assertTrue(TestDerivedSourceSearchParticipant.selectIndexesCount.get() > 0,
				"Contributed participant's selectIndexes() should be called during reference search");
	}

	@Test
	public void testParticipantInvokedDuringImplementationSearch() throws Exception {
		importProjects("eclipse/hello");
		IProject project = WorkspaceHelper.getProject("hello");
		String uri = project.getFile("src/java/IFoo.java").getLocationURI().toString();

		TestDerivedSourceSearchParticipant.reset();

		TextDocumentPositionParams posParams = new TextDocumentPositionParams();
		posParams.setTextDocument(new TextDocumentIdentifier(uri));
		posParams.setPosition(new Position(5, 18));
		List<? extends Location> implementations =
				new ImplementationsHandler(preferenceManager).findImplementations(posParams, monitor);
		assertNotNull(implementations);

		assertTrue(TestDerivedSourceSearchParticipant.beginSearchingCount.get() > 0,
				"Contributed participant's beginSearching() should be called during implementation search");
	}

	@Test
	public void testLangxFileIndexedByContributedParticipant() throws Exception {
		importProjects("eclipse/hello");
		assertTrue(TestDerivedSourceSearchParticipant.indexDocumentCount.get() > 0,
				"Contributed participant's indexDocument() should be called for .langx files");
	}
}
