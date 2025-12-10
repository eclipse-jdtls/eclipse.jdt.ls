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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.eclipse.jdt.core.IField;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IMethod;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.Signature;
import org.eclipse.jdt.ls.core.internal.HoverInfoProvider;
import org.eclipse.jdt.ls.core.internal.WorkspaceHelper;
import org.eclipse.jdt.ls.core.internal.managers.AbstractProjectsManagerBasedTest;
import org.eclipse.lsp4j.MarkedString;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Tests for javadoc and signature of java elements
 *
 * @author Alex Boyko
 *
 */
public class JavadocContentTest extends AbstractProjectsManagerBasedTest {

	private IJavaProject project;

	@BeforeEach
	public void setup() throws Exception {
		importProjects("eclipse/hello");
		project = JavaCore.create(WorkspaceHelper.getProject("hello"));
	}

	@Override
	@AfterEach
	public void cleanUp() throws Exception {
		super.cleanUp();
	}

	@Test
	public void testClassJavadoc() throws Exception {
		IType type = project.findType("org.sample.TestJavadoc");
		assertNotNull(type);
		MarkedString signature = HoverInfoProvider.computeSignature(type);
		assertEquals("org.sample.TestJavadoc<K, V>", signature.getValue());
		MarkedString javadoc = HoverInfoProvider.computeJavadoc(type);

		String expectedJavadoc = """
				Test javadoc class

				* **Type Parameters:**
				  * **\\<K\\>** the type of keys
				  * **\\<V\\>** the type of values
				* **Author:**
				  * Some dude
				  * Another one
				* **See Also:**
				  * [some.pkg.SomeClass]()
				  * [some.pkg.SomeClass.someMethod()]()""";
		assertEquals(expectedJavadoc, javadoc.getValue());
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
		IMethod method = type.getMethod("foo", new String[] {
				"QString;",
			Signature.SIG_INT
		});
		assertNotNull(method);
		MarkedString signature = HoverInfoProvider.computeSignature(method);
		assertEquals("String org.sample.TestJavadoc.foo(String input, int count)", signature.getValue());
		MarkedString javadoc = HoverInfoProvider.computeJavadoc(method);

		String expectedJavadoc = """
				Foo method

				* **Parameters:**
				  * **input** some input
				  * **count** some count
				* **Returns:**
				  * some string""";
		assertEquals(expectedJavadoc, javadoc.getValue());
	}

	@Test
	public void testLiteralCodeJavadoc() throws Exception {
		IType type = project.findType("org.sample.TestJavadoc");
		assertNotNull(type);
		IMethod method = type.getMethod("anotherMethod", new String[] {});
		assertNotNull(method);
		MarkedString signature = HoverInfoProvider.computeSignature(method);
		assertEquals("void org.sample.TestJavadoc.anotherMethod()", signature.getValue());
		MarkedString javadoc = HoverInfoProvider.computeJavadoc(method);

		// @formatter:off
		String expectedJavadoc = """

		      interface Service {
		         @LookupIfProperty(name = "service.foo.enabled", stringValue = "true")
		         String name();
		      }
		\s\s\s\s\s\s
		""";
		// @formatter:on
		assertEquals(expectedJavadoc, javadoc.getValue());

	}

	@Test
	public void testNullJavadoc() throws Exception {
		IType type = project.findType("org.sample.TestJavadoc");
		assertNotNull(type);
		IType inner = type.getType("Inner");
		assertNotNull(inner);
		assertNull(JavadocContentAccess2.getMarkdownContentReader(inner));
		assertNull(JavadocContentAccess2.getMarkdownContent(inner));
	}
}