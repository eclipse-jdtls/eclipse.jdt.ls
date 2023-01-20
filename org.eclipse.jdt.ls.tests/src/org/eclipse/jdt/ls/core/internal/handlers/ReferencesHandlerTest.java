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
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;

import org.eclipse.core.resources.IProject;
import org.eclipse.jdt.core.IClassFile;
import org.eclipse.jdt.core.IField;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.ls.core.internal.JDTUtils;
import org.eclipse.jdt.ls.core.internal.ResourceUtils;
import org.eclipse.jdt.ls.core.internal.WorkspaceHelper;
import org.eclipse.jdt.ls.core.internal.managers.AbstractProjectsManagerBasedTest;
import org.eclipse.jdt.ls.core.internal.preferences.PreferenceManager;
import org.eclipse.lsp4j.Location;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.ReferenceContext;
import org.eclipse.lsp4j.ReferenceParams;
import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.junit.Before;
import org.junit.Test;

/**
 * ReferencesHandlerTest
 */
public class ReferencesHandlerTest extends AbstractProjectsManagerBasedTest{

	private ReferencesHandler handler;
	private IProject project;


	@Before
	public void setup() throws Exception{
		importProjects("eclipse/hello");
		project = WorkspaceHelper.getProject("hello");
		preferenceManager = mock(PreferenceManager.class);
		when(preferenceManager.getPreferences()).thenReturn(preferences);
		handler = new ReferencesHandler(preferenceManager);
	}

	@Test
	public void testEmpty(){
		ReferenceParams param = new ReferenceParams();
		param.setPosition(new Position(1, 1));
		param.setContext(new ReferenceContext(false));
		param.setTextDocument( new TextDocumentIdentifier("/foo/bar"));
		List<Location> references =  handler.findReferences(param, monitor);
		assertNotNull(references);
		assertTrue("references are not empty", references.isEmpty());
	}

	@Test
	public void testReference(){
		URI uri = project.getFile("src/java/Foo2.java").getRawLocationURI();
		String fileURI = ResourceUtils.fixURI(uri);

		ReferenceParams param = new ReferenceParams();
		param.setPosition(new Position(5,16));
		param.setContext(new ReferenceContext(false));
		param.setTextDocument( new TextDocumentIdentifier(fileURI));
		List<Location> references =  handler.findReferences(param, monitor);
		assertNotNull("findReferences should not return null",references);
		assertEquals(1, references.size());
		Location l = references.get(0);
		String refereeUri = ResourceUtils.fixURI(project.getFile("src/java/Foo3.java").getRawLocationURI());
		assertEquals(refereeUri, l.getUri());
	}

	@Test
	public void testIncludeAccessors() {
		boolean includeAccessors = preferenceManager.getPreferences().isIncludeAccessors();
		try {
			URI uri = project.getFile("src/org/ref/Apple.java").getRawLocationURI();
			String fileURI = ResourceUtils.fixURI(uri);
			ReferenceParams param = new ReferenceParams();
			param.setPosition(new Position(3, 18));
			param.setContext(new ReferenceContext(false));
			param.setTextDocument(new TextDocumentIdentifier(fileURI));
			preferenceManager.getPreferences().setIncludeAccessors(false);
			List<Location> references = handler.findReferences(param, monitor);
			assertNotNull("findReferences should not return null", references);
			assertEquals(3, references.size());
			preferenceManager.getPreferences().setIncludeAccessors(true);
			references = handler.findReferences(param, monitor);
			assertNotNull("findReferences should not return null", references);
			assertEquals(5, references.size());
			Location l = references.get(0);
			String refereeUri = ResourceUtils.fixURI(project.getFile("src/org/ref/Apple.java").getRawLocationURI());
			assertEquals(refereeUri, l.getUri());
			l = references.get(4);
			refereeUri = ResourceUtils.fixURI(project.getFile("src/org/ref/Test.java").getRawLocationURI());
			assertEquals(refereeUri, l.getUri());
		} finally {
			preferenceManager.getPreferences().setIncludeAccessors(includeAccessors);
		}
	}

	@Test
	public void testEnumInClassFile() throws Exception {
		when(preferenceManager.isClientSupportsClassFileContent()).thenReturn(true);
		importProjects("eclipse/reference");
		IProject referenceProject = WorkspaceHelper.getProject("reference");
		IJavaProject javaProject = JavaCore.create(referenceProject);
		IType element = javaProject.findType("org.sample.Foo");
		IClassFile cf = (IClassFile) element.getAncestor(IJavaElement.CLASS_FILE);
		URI uri = JDTUtils.toURI(JDTUtils.toUri(cf));
		String fileURI = ResourceUtils.fixURI(uri);
		ReferenceParams param = new ReferenceParams();
		param.setPosition(new Position(7, 6));
		param.setContext(new ReferenceContext(false));
		param.setTextDocument(new TextDocumentIdentifier(fileURI));
		List<Location> references = handler.findReferences(param, monitor);
		assertNotNull("findReferences should not return null", references);
		assertEquals(2, references.size());
		Location l = references.get(0);
		String refereeUri = ResourceUtils.fixURI(referenceProject.getFile("src/org/reference/Main.java").getRawLocationURI());
		assertEquals(refereeUri, l.getUri());
		l = references.get(1);
		assertEquals(fileURI, l.getUri());
	}

	// https://github.com/redhat-developer/vscode-java/issues/2227
	@Test
	public void testPotentialMatch() throws Exception {
		when(preferenceManager.isClientSupportsClassFileContent()).thenReturn(true);
		importProjects("eclipse/reference");
		IProject referenceProject = WorkspaceHelper.getProject("reference");
		URI uri = referenceProject.getFile("src/org/reference/User.java").getRawLocationURI();
		String fileURI = ResourceUtils.fixURI(uri);
		ReferenceParams param = new ReferenceParams();
		param.setPosition(new Position(1, 15));
		param.setContext(new ReferenceContext(false));
		param.setTextDocument(new TextDocumentIdentifier(fileURI));
		List<Location> references = handler.findReferences(param, monitor);
		assertEquals(0, references.size());
	}

	// https://github.com/eclipse/eclipse.jdt.ls/issues/2148
	@Test
	public void testDeclarationInReferences() throws Exception {
		importProjects("eclipse/reference");
		IProject referenceProject = WorkspaceHelper.getProject("reference");
		URI uri = referenceProject.getFile("src/org/reference/Main.java").getRawLocationURI();
		String fileURI = ResourceUtils.fixURI(uri);
		ReferenceParams param = new ReferenceParams();
		param.setPosition(new Position(12, 22));
		param.setContext(new ReferenceContext(true));
		param.setTextDocument(new TextDocumentIdentifier(fileURI));
		List<Location> references = handler.findReferences(param, monitor);
		assertEquals(2, references.size());
		Location l = references.get(0);
		assertEquals(new Range(new Position(12, 20), new Position(12, 32)), l.getRange());
		l = references.get(1);
		assertEquals(new Range(new Position(14, 15), new Position(14, 25)), l.getRange());
	}

	// https://github.com/eclipse/eclipse.jdt.ls/issues/2405
	@Test
	public void testReferencesInJRE() throws Exception {
		when(preferenceManager.isClientSupportsClassFileContent()).thenReturn(true);
		IJavaProject javaProject = JavaCore.create(project);
		IType system = javaProject.findType("java.lang.System");
		IField field = system.getField("out");
		assertTrue(field.exists());
		List<Location> references = new ArrayList<>();
		handler.search(field, references, monitor, true);
		assertNotNull("findReferences should not return null", references);
		Location location = references.stream().filter(r -> r.getUri().startsWith("jdt://contents/rtstubs.jar/java.lang/System.class")).findFirst().get();
		assertNotNull(location);
	}

}
