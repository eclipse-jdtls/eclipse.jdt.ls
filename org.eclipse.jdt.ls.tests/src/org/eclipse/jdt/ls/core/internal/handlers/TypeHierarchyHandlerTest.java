/*******************************************************************************
 * Copyright (c) 2021 Microsoft Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *     Microsoft Corporation - initial API and implementation
*******************************************************************************/
package org.eclipse.jdt.ls.core.internal.handlers;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import java.util.List;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.jdt.ls.core.internal.WorkspaceHelper;
import org.eclipse.jdt.ls.core.internal.managers.AbstractInvisibleProjectBasedTest;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.SymbolKind;
import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.eclipse.lsp4j.TypeHierarchyItem;
import org.eclipse.lsp4j.TypeHierarchyPrepareParams;
import org.eclipse.lsp4j.TypeHierarchySubtypesParams;
import org.eclipse.lsp4j.TypeHierarchySupertypesParams;
import org.junit.Before;
import org.junit.Test;

public class TypeHierarchyHandlerTest extends AbstractInvisibleProjectBasedTest {

	private IProject fJProject;
	private TypeHierarchyHandler fHandler;

	@Before
	public void setup() throws Exception {
		importProjects("maven/salut");
		fJProject = WorkspaceHelper.getProject("salut");
		fHandler = new TypeHierarchyHandler();
	}

	@Test
	public void testSuperTypeHierarchy() throws Exception {
		IProgressMonitor monitor = new NullProgressMonitor();
		TypeHierarchyPrepareParams params = new TypeHierarchyPrepareParams();
		String uriString = fJProject.getFile("src/main/java/org/sample/CallHierarchy.java").getLocationURI().toString();
		TextDocumentIdentifier identifier = new TextDocumentIdentifier(uriString);
		Position position = new Position(7, 27);
		params.setTextDocument(identifier);
		params.setPosition(position);
		List<TypeHierarchyItem> items = fHandler.prepareTypeHierarchy(params, monitor);
		assertNotNull(items);
		assertEquals(1, items.size());
		assertEquals(items.get(0).getName(), "CallHierarchy$FooBuilder");
		TypeHierarchySupertypesParams supertypesParams = new TypeHierarchySupertypesParams();
		supertypesParams.setItem(items.get(0));
		List<TypeHierarchyItem> supertypesItems = fHandler.getSupertypeItems(supertypesParams, monitor);
		assertNotNull(supertypesItems);
		assertEquals(2, supertypesItems.size());
		assertEquals(supertypesItems.get(0).getName(), "Builder");
		assertEquals(supertypesItems.get(0).getKind(), SymbolKind.Interface);
		assertEquals(supertypesItems.get(1).getName(), "Object");
		assertEquals(supertypesItems.get(1).getKind(), SymbolKind.Class);
	}

	@Test
	public void testSubTypeHierarchy() throws Exception {
		IProgressMonitor monitor = new NullProgressMonitor();
		TypeHierarchyPrepareParams params = new TypeHierarchyPrepareParams();
		String uriString = fJProject.getFile("src/main/java/org/sample/CallHierarchy.java").getLocationURI().toString();
		TextDocumentIdentifier identifier = new TextDocumentIdentifier(uriString);
		Position position = new Position(2, 43);
		params.setTextDocument(identifier);
		params.setPosition(position);
		List<TypeHierarchyItem> items = fHandler.prepareTypeHierarchy(params, monitor);
		assertNotNull(items);
		assertEquals(1, items.size());
		assertEquals(items.get(0).getName(), "Builder");
		TypeHierarchySubtypesParams supertypesParams = new TypeHierarchySubtypesParams();
		supertypesParams.setItem(items.get(0));
		List<TypeHierarchyItem> subtypesItems = fHandler.getSubtypeItems(supertypesParams, monitor);
		assertNotNull(subtypesItems);
		assertEquals(9, subtypesItems.size());
	}

	// https://github.com/redhat-developer/vscode-java/issues/2871
	@Test
	public void testMultipleProjects() throws Exception {
		importProjects("eclipse/gh2871");
		IProject project = WorkspaceHelper.getProject("project1");
		IProgressMonitor monitor = new NullProgressMonitor();
		TypeHierarchyPrepareParams params = new TypeHierarchyPrepareParams();
		String uriString = project.getFile("src/org/sample/First.java").getLocationURI().toString();
		TextDocumentIdentifier identifier = new TextDocumentIdentifier(uriString);
		Position position = new Position(1, 22);
		params.setTextDocument(identifier);
		params.setPosition(position);
		List<TypeHierarchyItem> items = fHandler.prepareTypeHierarchy(params, monitor);
		assertNotNull(items);
		assertEquals(1, items.size());
		assertEquals("First", items.get(0).getName());
		TypeHierarchySubtypesParams supertypesParams = new TypeHierarchySubtypesParams();
		supertypesParams.setItem(items.get(0));
		List<TypeHierarchyItem> subtypesItems = fHandler.getSubtypeItems(supertypesParams, monitor);
		assertNotNull(subtypesItems);
		assertEquals(1, subtypesItems.size());
		assertEquals("Second", subtypesItems.get(0).getName());
	}

	@Test
	public void testMethodHierarchy() throws Exception {
		importProjects("maven/type-hierarchy");
		IProject project = WorkspaceHelper.getProject("type-hierarchy");
		String uriString = project.getFile("src/main/java/org/example/Zero.java").getLocationURI().toString();
		TextDocumentIdentifier identifier = new TextDocumentIdentifier(uriString);
		Position position = new Position(3, 17); // public void f[o]o()
		TypeHierarchyPrepareParams zeroParams = new TypeHierarchyPrepareParams();
		zeroParams.setTextDocument(identifier);
		zeroParams.setPosition(position);
		List<TypeHierarchyItem> zeroItems = fHandler.prepareTypeHierarchy(zeroParams, monitor);
		assertNotNull(zeroItems);
		assertEquals(1, zeroItems.size());
		assertEquals("Zero", zeroItems.get(0).getName());
		assertEquals(SymbolKind.Class, zeroItems.get(0).getKind());

		TypeHierarchySupertypesParams supertypesParams = new TypeHierarchySupertypesParams();
		supertypesParams.setItem(zeroItems.get(0));
		List<TypeHierarchyItem> supertypesItems = fHandler.getSupertypeItems(supertypesParams, monitor);
		assertNotNull(supertypesItems);
		// do not show java.lang.Object if target method isn't from there
		assertEquals(0, supertypesItems.size());

		TypeHierarchySubtypesParams subtypesParams = new TypeHierarchySubtypesParams();
		subtypesParams.setItem(zeroItems.get(0));
		List<TypeHierarchyItem> subtypesItems = fHandler.getSubtypeItems(subtypesParams, monitor);
		assertNotNull(subtypesItems);
		assertEquals(SymbolKind.Class, subtypesItems.get(0).getKind()); // one
		assertEquals("One", subtypesItems.get(0).getName()); // one
		assertEquals(SymbolKind.Null, subtypesItems.get(1).getKind()); // two
		assertEquals("Two", subtypesItems.get(1).getName()); // two

		TypeHierarchyItem one = subtypesItems.get(0);
		TypeHierarchyItem two = subtypesItems.get(1);
		subtypesParams.setItem(one);
		subtypesItems = fHandler.getSubtypeItems(subtypesParams, monitor);
		assertNotNull(subtypesItems);
		assertEquals(SymbolKind.Null, subtypesItems.get(1).getKind()); // three
		assertEquals("Three", subtypesItems.get(1).getName()); // three
		assertEquals(SymbolKind.Class, subtypesItems.get(0).getKind()); // four
		assertEquals("Four", subtypesItems.get(0).getName()); // four

		subtypesParams.setItem(two);
		subtypesItems = fHandler.getSubtypeItems(subtypesParams, monitor);
		assertNotNull(subtypesItems);
		assertEquals(SymbolKind.Null, subtypesItems.get(0).getKind()); // five
		assertEquals("Five", subtypesItems.get(0).getName()); // five
		assertEquals(SymbolKind.Class, subtypesItems.get(1).getKind()); // six
		assertEquals("Six", subtypesItems.get(1).getName()); // six
	}
}
