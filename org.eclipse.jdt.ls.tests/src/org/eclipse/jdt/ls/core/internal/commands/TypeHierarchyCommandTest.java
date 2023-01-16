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
package org.eclipse.jdt.ls.core.internal.commands;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import java.util.List;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.jdt.ls.core.internal.WorkspaceHelper;
import org.eclipse.jdt.ls.core.internal.managers.AbstractInvisibleProjectBasedTest;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.eclipse.lsp4j.TypeHierarchyDirection;
import org.eclipse.lsp4j.TypeHierarchyItem;
import org.eclipse.lsp4j.TypeHierarchyParams;
import org.junit.Before;
import org.junit.Test;

public class TypeHierarchyCommandTest extends AbstractInvisibleProjectBasedTest {

	private IProject fJProject;
	private TypeHierarchyCommand fCommand;

	@Before
	public void setup() throws Exception {
		importProjects("maven/salut");
		fJProject = WorkspaceHelper.getProject("salut");
		fCommand = new TypeHierarchyCommand();
	}

	@Test
	public void testTypeHierarchy() throws Exception {
		IProgressMonitor monitor = new NullProgressMonitor();
		TypeHierarchyParams params = new TypeHierarchyParams();
		String uriString = fJProject.getFile("src/main/java/org/sample/TestJavadoc.java").getLocationURI().toString();
		TextDocumentIdentifier identifier = new TextDocumentIdentifier(uriString);
		Position position = new Position(4, 20);
		params.setTextDocument(identifier);
		params.setResolve(1);
		params.setDirection(TypeHierarchyDirection.Both);
		params.setPosition(position);
		TypeHierarchyItem item = fCommand.typeHierarchy(params, monitor);
		assertNotNull(item);
		assertEquals(item.getName(), "TestJavadoc");
		assertNotNull(item.getChildren());
		assertEquals(item.getChildren().size(), 0);
		assertNotNull(item.getParents());
		assertEquals(item.getParents().size(), 1);
		assertEquals(item.getParents().get(0).getName(), "Object");
	}

	@Test
	public void testSuperTypeHierarchy() throws Exception {
		IProgressMonitor monitor = new NullProgressMonitor();
		TypeHierarchyParams params = new TypeHierarchyParams();
		String uriString = fJProject.getFile("src/main/java/org/sample/CallHierarchy.java").getLocationURI().toString();
		TextDocumentIdentifier identifier = new TextDocumentIdentifier(uriString);
		Position position = new Position(7, 27);
		params.setTextDocument(identifier);
		params.setResolve(1);
		params.setDirection(TypeHierarchyDirection.Parents);
		params.setPosition(position);
		TypeHierarchyItem item = fCommand.typeHierarchy(params, monitor);
		assertNotNull(item);
		assertEquals(item.getName(), "CallHierarchy$FooBuilder");
		assertNull(item.getChildren());
		assertEquals(item.getParents().size(), 2);
		TypeHierarchyItem builder = item.getParents().get(0);
		assertNotNull(builder);
		assertEquals(builder.getName(), "Builder");
		assertNull(builder.getParents());
		TypeHierarchyItem object = item.getParents().get(1);
		assertNotNull(object);
		assertEquals(object.getName(), "Object");
		assertNull(object.getParents());
	}

	@Test
	public void testSubTypeHierarchy() throws Exception {
		IProgressMonitor monitor = new NullProgressMonitor();
		TypeHierarchyParams params = new TypeHierarchyParams();
		String uriString = fJProject.getFile("src/main/java/org/sample/CallHierarchy.java").getLocationURI().toString();
		TextDocumentIdentifier identifier = new TextDocumentIdentifier(uriString);
		Position position = new Position(2, 43);
		params.setTextDocument(identifier);
		params.setResolve(2);
		params.setDirection(TypeHierarchyDirection.Children);
		params.setPosition(position);
		TypeHierarchyItem item = fCommand.typeHierarchy(params, monitor);
		assertNotNull(item);
		assertEquals(item.getName(), "Builder");
		assertNull(item.getParents());
		assertEquals(item.getChildren().size(), 9);
		for (TypeHierarchyItem child : item.getChildren()) {
			List<TypeHierarchyItem> subChild = child.getChildren();
			assertNotNull(subChild);
			if (subChild.size() == 1) {
				assertEquals(subChild.get(0).getName(), "ReflectionToStringBuilder");
			}
		}
	}

	// https://github.com/redhat-developer/vscode-java/issues/2871
	@Test
	public void testMultipleProjects() throws Exception {
		importProjects("eclipse/gh2871");
		IProject project = WorkspaceHelper.getProject("project1");
		IProgressMonitor monitor = new NullProgressMonitor();
		TypeHierarchyParams params = new TypeHierarchyParams();
		String uriString = project.getFile("src/org/sample/First.java").getLocationURI().toString();
		TextDocumentIdentifier identifier = new TextDocumentIdentifier(uriString);
		Position position = new Position(1, 22);
		params.setTextDocument(identifier);
		params.setResolve(1);
		params.setDirection(TypeHierarchyDirection.Both);
		params.setPosition(position);
		TypeHierarchyItem item = fCommand.typeHierarchy(params, monitor);
		assertNotNull(item);
		assertEquals(item.getName(), "First");
		assertNotNull(item.getChildren());
		assertEquals(item.getChildren().size(), 1);
		assertEquals(item.getChildren().get(0).getName(), "Second");
	}
}
