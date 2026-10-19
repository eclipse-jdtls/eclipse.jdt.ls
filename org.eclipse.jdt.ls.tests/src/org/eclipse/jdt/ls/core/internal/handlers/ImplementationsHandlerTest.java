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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.net.URI;
import java.util.List;

import org.eclipse.core.resources.IProject;
import org.eclipse.jdt.ls.core.internal.JDTUtils;
import org.eclipse.jdt.ls.core.internal.ResourceUtils;
import org.eclipse.jdt.ls.core.internal.WorkspaceHelper;
import org.eclipse.jdt.ls.core.internal.managers.AbstractProjectsManagerBasedTest;
import org.eclipse.jdt.ls.core.internal.preferences.PreferenceManager;
import org.eclipse.jdt.ls.core.internal.preferences.Preferences;
import org.eclipse.lsp4j.Location;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.eclipse.lsp4j.TextDocumentPositionParams;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * ImplementationsHandlerTest
 */
public class ImplementationsHandlerTest extends AbstractProjectsManagerBasedTest{

	private ImplementationsHandler handler;
	private IProject project;


	@BeforeEach
	public void setup() throws Exception{
		importProjects("eclipse/hello");
		project = WorkspaceHelper.getProject("hello");
		preferenceManager = mock(PreferenceManager.class);
		when(preferenceManager.getPreferences()).thenReturn(new Preferences());
		handler = new ImplementationsHandler(preferenceManager);
	}

	@Test
	public void testEmpty(){
		TextDocumentPositionParams param = new TextDocumentPositionParams();
		param.setPosition(new Position(1, 1));
		param.setTextDocument(new TextDocumentIdentifier("/foo/bar"));
		List<? extends Location> implementations = handler.findImplementations(param, monitor);
		assertNotNull(implementations);
		assertTrue(implementations.isEmpty(), "implementations are not empty");
	}

	@Test
	public void testInterfaceImplementation() {
		URI uri = project.getFile("src/org/sample/IFoo.java").getRawLocationURI();
		String fileURI = ResourceUtils.fixURI(uri);
		TextDocumentPositionParams param = new TextDocumentPositionParams();
		param.setPosition(new Position(2, 20)); //Position over IFoo
		param.setTextDocument(new TextDocumentIdentifier(fileURI));
		List<? extends Location> implementations = handler.findImplementations(param, monitor);
		assertNotNull(implementations, "findImplementations should not return null");
		assertEquals(2, implementations.size());

		Location foo2 = implementations.stream().filter(i -> i.getUri().contains("org/sample/Foo2.java")).findFirst().get();
		assertEquals(JDTUtils.newLineRange(2, 13, 17), foo2.getRange());
		Location foo3 = implementations.stream().filter(i -> i.getUri().contains("org/sample/Foo3.java")).findFirst().get();
		assertEquals(JDTUtils.newLineRange(5, 13, 17), foo3.getRange());
	}

	@Test
	public void testClassImplementation() {
		URI uri = project.getFile("src/org/sample/Foo2.java").getRawLocationURI();
		String fileURI = ResourceUtils.fixURI(uri);

		TextDocumentPositionParams param = new TextDocumentPositionParams();
		param.setPosition(new Position(2, 14)); //Position over Foo2
		param.setTextDocument(new TextDocumentIdentifier(fileURI));
		List<? extends Location> implementations = handler.findImplementations(param, monitor);
		assertNotNull(implementations, "findImplementations should not return null");
		assertEquals(1, implementations.size(),implementations.toString());
		Location foo3 = implementations.get(0);
		assertTrue(foo3.getUri().contains("org/sample/Foo3.java"), "Unexpected implementation : " + foo3.getUri());
		assertEquals(JDTUtils.newLineRange(5, 13, 17), foo3.getRange());
	}

	@Test
	public void testMethodImplementation() {
		URI uri = project.getFile("src/org/sample/IFoo.java").getRawLocationURI();
		String fileURI = ResourceUtils.fixURI(uri);

		TextDocumentPositionParams param = new TextDocumentPositionParams();
		param.setPosition(new Position(4, 14)); //Position over IFoo#someMethod
		param.setTextDocument(new TextDocumentIdentifier(fileURI));
		List<? extends Location> implementations = handler.findImplementations(param, monitor);
		assertNotNull(implementations, "findImplementations should not return null");
		assertEquals(1, implementations.size(), implementations.toString());
		Location foo2 = implementations.get(0);
		assertTrue(foo2.getUri().contains("org/sample/Foo2.java"), "Unexpected implementation : " + foo2.getUri());
		//check range points to someMethod() position
		assertEquals(new Position(4, 16), foo2.getRange().getStart());
		assertEquals(new Position(4, 26), foo2.getRange().getEnd());
	}

	@Test
	public void testMethodInvocationImplementation() {
		URI uri = project.getFile("src/org/sample/FooService.java").getRawLocationURI();
		String fileURI = ResourceUtils.fixURI(uri);

		TextDocumentPositionParams param = new TextDocumentPositionParams();
		param.setPosition(new Position(6, 14)); //Position over foo.someMethod
		param.setTextDocument(new TextDocumentIdentifier(fileURI));
		List<? extends Location> implementations = handler.findImplementations(param, monitor);
		assertNotNull(implementations, "findImplementations should not return null");
		assertEquals(1, implementations.size(), implementations.toString());
		Location foo2 = implementations.get(0);
		assertTrue(foo2.getUri().contains("org/sample/Foo2.java"), "Unexpected implementation : " + foo2.getUri());
		//check range points to someMethod() position
		assertEquals(new Position(4, 16), foo2.getRange().getStart());
		assertEquals(new Position(4, 26), foo2.getRange().getEnd());
	}

	@Test
	public void testMethodSuperInvocationImplementation() {
		URI uri = project.getFile("src/org/sample/FooChild.java").getRawLocationURI();
		String fileURI = ResourceUtils.fixURI(uri);

		TextDocumentPositionParams param = new TextDocumentPositionParams();
		param.setPosition(new Position(5, 14)); //Position over super.someMethod
		param.setTextDocument(new TextDocumentIdentifier(fileURI));
		List<? extends Location> implementations = handler.findImplementations(param, monitor);
		assertNotNull(implementations, "findImplementations should not return null");
		assertEquals(1, implementations.size(), implementations.toString());
		Location foo = implementations.get(0);
		assertTrue(foo.getUri().contains("org/sample/Foo.java"), "Unexpected implementation : " + foo.getUri());
		//check range points to someMethod() position
		assertEquals(new Position(8, 13), foo.getRange().getStart());
		assertEquals(new Position(8, 23), foo.getRange().getEnd());
	}

	@Test
	public void testClassImplementation_includeDefinition() {
		URI uri = project.getFile("src/org/sample/FooService.java").getRawLocationURI();
		String fileURI = ResourceUtils.fixURI(uri);

		TextDocumentPositionParams param = new TextDocumentPositionParams();
		param.setPosition(new Position(10, 20)); //Position over new Foo()
		param.setTextDocument(new TextDocumentIdentifier(fileURI));
		List<? extends Location> implementations = handler.findImplementations(param, monitor);
		assertNotNull(implementations, "findImplementations should not return null");
		assertEquals(2, implementations.size(), implementations.toString());
		Location foo = implementations.get(0);
		assertTrue(foo.getUri().contains("org/sample/Foo.java"), "Unexpected implementation : " + foo.getUri());
		//check range points to Foo class declaration position
		assertEquals(new Position(2, 13), foo.getRange().getStart());
		assertEquals(new Position(2, 16), foo.getRange().getEnd());
		foo = implementations.get(1);
		assertTrue(foo.getUri().contains("org/sample/FooChild.java"), "Unexpected implementation : " + foo.getUri());
		//check range points to FooChild class declaration position
		assertEquals(new Position(2, 13), foo.getRange().getStart());
		assertEquals(new Position(2, 21), foo.getRange().getEnd());
	}

	@Test
	public void testMethodImplementation_includeDefinition() {
		URI uri = project.getFile("src/org/sample/FooService.java").getRawLocationURI();
		String fileURI = ResourceUtils.fixURI(uri);

		TextDocumentPositionParams param = new TextDocumentPositionParams();
		param.setPosition(new Position(11, 13)); //Position over someMethod()
		param.setTextDocument(new TextDocumentIdentifier(fileURI));
		List<? extends Location> implementations = handler.findImplementations(param, monitor);
		assertNotNull(implementations, "findImplementations should not return null");
		assertEquals(2, implementations.size(), implementations.toString());
		Location foo = implementations.get(0);
		assertTrue(foo.getUri().contains("org/sample/Foo.java"), "Unexpected implementation : " + foo.getUri());
		//check range points to someMethod() position
		assertEquals(new Position(8, 13), foo.getRange().getStart());
		assertEquals(new Position(8, 23), foo.getRange().getEnd());
		foo = implementations.get(1);
		assertTrue(foo.getUri().contains("org/sample/FooChild.java"), "Unexpected implementation : " + foo.getUri());
		//check range points to someMethod() position
		assertEquals(new Position(4, 13), foo.getRange().getStart());
		assertEquals(new Position(4, 23), foo.getRange().getEnd());
	}

	@Test
	public void testUnimplementedClassImplementation_includeDefinition() {
		URI uri = project.getFile("src/org/sample/FooService.java").getRawLocationURI();
		String fileURI = ResourceUtils.fixURI(uri);

		TextDocumentPositionParams param = new TextDocumentPositionParams();
		param.setPosition(new Position(14, 13)); //Position over AbstractFoo.
		param.setTextDocument(new TextDocumentIdentifier(fileURI));
		List<? extends Location> implementations = handler.findImplementations(param, monitor);
		assertNotNull(implementations, "findImplementations should not return null");
		assertEquals(1, implementations.size(), implementations.toString());
		Location foo = implementations.get(0);
		assertTrue(foo.getUri().contains("org/sample/AbstractFoo.java"), "Unexpected implementation : " + foo.getUri());
		//check range points to AbstractFoo class declaration position
		assertEquals(new Position(2, 22), foo.getRange().getStart());
		assertEquals(new Position(2, 33), foo.getRange().getEnd());
	}

	@Test
	public void testUnimplementedMethodImplementation_includeDefinition() {
		URI uri = project.getFile("src/org/sample/FooService.java").getRawLocationURI();
		String fileURI = ResourceUtils.fixURI(uri);

		TextDocumentPositionParams param = new TextDocumentPositionParams();
		param.setPosition(new Position(15, 13)); //Position over someMethod()
		param.setTextDocument(new TextDocumentIdentifier(fileURI));
		List<? extends Location> implementations = handler.findImplementations(param, monitor);
		assertNotNull(implementations, "findImplementations should not return null");
		assertEquals(1, implementations.size(), implementations.toString());
		Location foo = implementations.get(0);
		assertTrue(foo.getUri().contains("org/sample/AbstractFoo.java"), "Unexpected implementation : " + foo.getUri());
		//check range points to someMethod() position
		assertEquals(new Position(4, 15), foo.getRange().getStart());
		assertEquals(new Position(4, 25), foo.getRange().getEnd());
	}

	@Test
	public void testImplementationFromBinaryTypeWithoutClassContentSupport() {
		//Only workspace implementation returned
		List<? extends Location> implementations = getRunnableImplementations();
		assertEquals(1, implementations.size(), implementations.toString());
		assertTrue(implementations.get(0).getUri().contains("org/sample/RunnableTest.java"), "Unexpected implementation : " + implementations.get(0).getUri());
		assertEquals(JDTUtils.newLineRange(2, 13, 25), implementations.get(0).getRange());
	}

	@Test
	public void testImplementationFromBinaryTypeWithClassContentSupport() {
		when(preferenceManager.isClientSupportsClassFileContent()).thenReturn(true);
		//workspace + binary implementations returned
		List<? extends Location> implementations = getRunnableImplementations();
		// only jdk classes are expected to implement Runnable
		assertEquals(8, implementations.size(), implementations.toString());
		Range defaultRange = JDTUtils.newRange();

		boolean containsRunnableTest = implementations.stream().anyMatch(i -> i.getUri().contains("org/sample/RunnableTest.java"));
		assertTrue(containsRunnableTest, "Implementation not found : org/sample/RunnableTest.java");

		List<? extends Location> implementainsNotRunnableTest = implementations.stream().filter(i -> !i.getUri().contains("org/sample/RunnableTest.java")).toList();
		for (Location implem : implementainsNotRunnableTest) {
			assertTrue(implem.getUri().contains("rtstubs.jar"), "Unexpected implementation : " + implem.getUri());
			assertEquals(defaultRange, implem.getRange(), "Expected default location ");//no jdk sources available
		}
	}

	private List<? extends Location> getRunnableImplementations() {
		URI uri = project.getFile("src/org/sample/RunnableTest.java").getRawLocationURI();
		String fileURI = ResourceUtils.fixURI(uri);

		TextDocumentPositionParams param = new TextDocumentPositionParams();
		param.setPosition(new Position(2, 42));//implementations of java.lang.Runnable
		param.setTextDocument(new TextDocumentIdentifier(fileURI));
		List<? extends Location> implementations = handler.findImplementations(param, monitor);
		assertNotNull(implementations, "findImplementations should not return null");
		return implementations;
	}

	@Test
	public void testInvalidElement() {
		URI uri = project.getFile("src/org/sample/Foo4.java").getRawLocationURI();
		String fileURI = ResourceUtils.fixURI(uri);

		TextDocumentPositionParams param = new TextDocumentPositionParams();
		param.setPosition(new Position(3, 34)); //Position over T
		param.setTextDocument(new TextDocumentIdentifier(fileURI));
		List<? extends Location> implementations = handler.findImplementations(param, monitor);
		assertNotNull(implementations, "findImplementations should not return null");
		assertEquals(0, implementations.size(), implementations.toString());
	}

}
