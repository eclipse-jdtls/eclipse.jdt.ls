/*******************************************************************************
 * Copyright (c) 2023 Red Hat Inc. and others.
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
package org.eclipse.jdt.ls.core.internal.handlers;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IPackageFragment;
import org.eclipse.jdt.core.manipulation.CoreASTProvider;
import org.eclipse.jdt.ls.core.internal.JDTUtils;
import org.eclipse.jdt.ls.core.internal.codemanipulation.AbstractSourceTestCase;
import org.eclipse.lsp4j.Position;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

/**
 * Tests for {@link SmartDetectionHandler}
 */
public class SmartDetectionHandlerTest extends AbstractSourceTestCase {

	private IPackageFragment fPackageTest;

	@Before
	public void setup() throws Exception {
		fPackageTest = fRoot.createPackageFragment("test", true, null);
	}

	@Test
	public void testSmartSemicolonDetection() throws CoreException {
		//@formatter:off
		ICompilationUnit unit = fPackageTest.createCompilationUnit("A.java",
				"package test;\n" + //
						"public class A {\n" +
						"\tprivate String str = new String()\n" +
						"}\n",
				false, monitor);
		//@formatter:on
		CoreASTProvider.getInstance().setActiveJavaElement(unit);
		String uri = JDTUtils.toUri(unit);
		var params = new SmartDetectionParams(uri, new Position(2, 33));
		Object result = new SmartDetectionHandler(params).getLocation(null);
		assertTrue(result instanceof SmartDetectionParams);
		assertEquals(2, ((SmartDetectionParams) result).getPosition().getLine());
		assertEquals(34, ((SmartDetectionParams) result).getPosition().getCharacter());
		assertEquals(uri, ((SmartDetectionParams) result).getUri());
	}

	@Test
	public void testSmartSemicolonDetectionInJavadoc() throws CoreException {
		//@formatter:off
		ICompilationUnit unit = fPackageTest.createCompilationUnit("A.java",
				"package test;\n" +
						"/**\n" +
						" * new String()\n" +
						" */\n" +
						"public class A {\n" +
						"\tprivate String str = new String()\n" +
						"}\n",
				false, monitor);
		//@formatter:off
		CoreASTProvider.getInstance().setActiveJavaElement(unit);
		String uri = JDTUtils.toUri(unit);
		var params = new SmartDetectionParams(uri, new Position(2, 14));
		Object result = new SmartDetectionHandler(params).getLocation(null);
		assertNull(result);
	}

	@Override
	@After
	public void cleanUp() throws Exception {
		super.cleanUp();
		CoreASTProvider.getInstance().disposeAST();
	}

}
