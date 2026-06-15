/*******************************************************************************
 * Copyright (c) 2026 Red Hat Inc. and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *     Red Hat Inc. - initial API and implementation
 *******************************************************************************/
package org.eclipse.jdt.ls.core.internal.commands;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.jdt.ls.core.internal.WorkspaceHelper;
import org.eclipse.jdt.ls.core.internal.managers.AbstractInvisibleProjectBasedTest;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.eclipse.lsp4j.TextDocumentPositionParams;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class FullyQualifiedNameCommandTest extends AbstractInvisibleProjectBasedTest {

	private IProject fJProject;
	private FullyQualifiedNameCommand fCommand;

	@BeforeEach
	public void setup() throws Exception {
		importProjects("maven/salut");
		fJProject = WorkspaceHelper.getProject("salut");
		fCommand = new FullyQualifiedNameCommand();
	}

	@Test
	public void testTypeFullyQualifiedName() throws Exception {
		TextDocumentPositionParams params = new TextDocumentPositionParams();
		String uriString = fJProject.getFile("src/main/java/org/sample/TestJavadoc.java").getLocationURI().toString();

		params.setTextDocument(new TextDocumentIdentifier(uriString));
		params.setPosition(new Position(2, 16));

		String result = fCommand.getFullyQualifiedName(params, new NullProgressMonitor());

		assertEquals("org.sample.TestJavadoc", result);
	}

	@Test
	public void testMethodFullyQualifiedName() throws Exception {
		TextDocumentPositionParams params = new TextDocumentPositionParams();
		String uriString = fJProject.getFile("src/main/java/org/sample/TestJavadoc.java").getLocationURI().toString();

		params.setTextDocument(new TextDocumentIdentifier(uriString));
		params.setPosition(new Position(4, 17));

		String result = fCommand.getFullyQualifiedName(params, new NullProgressMonitor());

		assertEquals("org.sample.TestJavadoc.foo", result);
	}

	@Test
	public void testFieldFullyQualifiedName() throws Exception {
		TextDocumentPositionParams params = new TextDocumentPositionParams();
		String uriString = fJProject.getFile("src/main/java/org/sample/TestJavadoc.java").getLocationURI().toString();

		params.setTextDocument(new TextDocumentIdentifier(uriString));
		params.setPosition(new Position(6, 18));

		String result = fCommand.getFullyQualifiedName(params, new NullProgressMonitor());

		assertEquals("org.sample.TestJavadoc$Inner.test", result);
	}

	@Test
	public void testImportedTypeFullyQualifiedName() throws Exception {
		TextDocumentPositionParams params = new TextDocumentPositionParams();
		String uriString = fJProject.getFile("src/main/java/org/sample/TestJavadoc.java").getLocationURI().toString();

		params.setTextDocument(new TextDocumentIdentifier(uriString));
		params.setPosition(new Position(1, 38));

		String result = fCommand.getFullyQualifiedName(params, new NullProgressMonitor());

		assertEquals("org.apache.commons.lang3.text.WordUtils", result);
	}

	@Test
	public void testPackageFullyQualifiedName() throws Exception {
		TextDocumentPositionParams params = new TextDocumentPositionParams();
		String uriString = fJProject.getFile("src/main/java/org/sample/TestJavadoc.java").getLocationURI().toString();

		params.setTextDocument(new TextDocumentIdentifier(uriString));
		params.setPosition(new Position(0, 12));

		String result = fCommand.getFullyQualifiedName(params, new NullProgressMonitor());

		assertEquals("org.sample", result);
	}

	@Test
	public void testLocalVariableFullyQualifiedName() throws Exception {
		TextDocumentPositionParams params = new TextDocumentPositionParams();
		String uriString = fJProject.getFile("src/main/java/org/sample/TestJavadoc.java").getLocationURI().toString();

		params.setTextDocument(new TextDocumentIdentifier(uriString));
		params.setPosition(new Position(5, 9));

		String result = fCommand.getFullyQualifiedName(params, new NullProgressMonitor());

		assertEquals("org.sample.TestJavadoc.foo.inner", result);
	}

	@Test
	public void testAnnotationFullyQualifiedName() throws Exception {
		TextDocumentPositionParams params = new TextDocumentPositionParams();
		String uriString = fJProject.getFile("src/main/java/org/sample/CallHierarchyOther.java").getLocationURI().toString();

		params.setTextDocument(new TextDocumentIdentifier(uriString));
		params.setPosition(new Position(11, 4));

		String result = fCommand.getFullyQualifiedName(params, new NullProgressMonitor());

		assertEquals("java.lang.Deprecated", result);
	}
}
