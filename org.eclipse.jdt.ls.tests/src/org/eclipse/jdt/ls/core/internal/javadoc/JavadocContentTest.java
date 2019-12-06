/*******************************************************************************
 * Copyright (c) 2018 Pivotal, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *     Pivotal, Inc. - initial API and implementation
 *******************************************************************************/
package org.eclipse.jdt.ls.core.internal.javadoc;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import org.eclipse.jdt.core.IField;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IMethod;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.ls.core.internal.HoverInfoProvider;
import org.eclipse.jdt.ls.core.internal.WorkspaceHelper;
import org.eclipse.jdt.ls.core.internal.managers.AbstractProjectsManagerBasedTest;
import org.eclipse.lsp4j.MarkedString;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

/**
 * Tests for javadoc and signature of java elements
 *
 * @author Alex Boyko
 *
 */
public class JavadocContentTest extends AbstractProjectsManagerBasedTest {

	private IJavaProject project;

	@Before
	public void setup() throws Exception {
		importProjects("eclipse/hello");
		project = JavaCore.create(WorkspaceHelper.getProject("hello"));
	}

	@Override
	@After
	public void cleanUp() throws Exception {
		super.cleanUp();
	}

	@Test
	public void testClassJavadoc() throws Exception {
		IType type = project.findType("org.sample.TestJavadoc");
		assertNotNull(type);
		MarkedString signature = HoverInfoProvider.computeSignature(type);
		assertEquals("org.sample.TestJavadoc", signature.getValue());
		MarkedString javadoc = HoverInfoProvider.computeJavadoc(type);
		assertEquals("Test javadoc class", javadoc.getValue());
	}

	@Test
	public void testFieldJavadoc() throws Exception {
		IType type = project.findType("org.sample.TestJavadoc");
		assertNotNull(type);
		IField field = type.getField("fooField");
		assertNotNull(field);
		MarkedString signature = HoverInfoProvider.computeSignature(field);
		assertEquals("int fooField", signature.getValue());
		MarkedString javadoc = HoverInfoProvider.computeJavadoc(field);
		assertEquals("Foo field", javadoc.getValue());
	}

	@Test
	public void testMethodJavadoc() throws Exception {
		IType type = project.findType("org.sample.TestJavadoc");
		assertNotNull(type);
		IMethod method = type.getMethod("foo", new String[0]);
		assertNotNull(method);
		MarkedString signature = HoverInfoProvider.computeSignature(method);
		assertEquals("String org.sample.TestJavadoc.foo()", signature.getValue());
		MarkedString javadoc = HoverInfoProvider.computeJavadoc(method);
		assertEquals("Foo method", javadoc.getValue());
	}
}
