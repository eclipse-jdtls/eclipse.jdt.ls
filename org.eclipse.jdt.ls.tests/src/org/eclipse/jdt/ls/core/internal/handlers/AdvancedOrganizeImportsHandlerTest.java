/*******************************************************************************
 * Copyright (c) 2019 Microsoft Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Microsoft Corporation - initial API and implementation
 *******************************************************************************/

package org.eclipse.jdt.ls.core.internal.handlers;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import java.io.IOException;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IPackageFragment;
import org.eclipse.jdt.internal.corext.util.JavaModelUtil;
import org.eclipse.jdt.internal.corext.util.ValidateEditException;
import org.eclipse.jdt.ls.core.internal.codemanipulation.AbstractSourceTestCase;
import org.eclipse.jdt.ls.core.internal.handlers.OrganizeImportsHandler.ImportChoice;
import org.eclipse.text.edits.TextEdit;
import org.junit.Test;

public class AdvancedOrganizeImportsHandlerTest extends AbstractSourceTestCase {

	@Test
	public void testChooseImport() throws ValidateEditException, CoreException, IOException {
		//@formatter:off
		IPackageFragment package1 = fRoot.createPackageFragment("p1", true, null);
		ICompilationUnit unit1 = package1.createCompilationUnit("C.java", "package p1;\r\n" +
				"\r\n" +
				"public class C {\r\n" +
				"}"
				, true, null);
		IPackageFragment package2 = fRoot.createPackageFragment("p2", true, null);
		ICompilationUnit unit2 = package2.createCompilationUnit("C.java", "package p2;\r\n" +
				"\r\n" +
				"public class C {\r\n" +
				"}"
				, true, null);
		ICompilationUnit unit = fPackageP.createCompilationUnit("B.java", "package p;\r\n" +
				"\r\n" +
				"public class B {\r\n" +
				"	C c;\r\n" +
				"}"
				, true, null);
		//@formatter:on

		TextEdit edit = OrganizeImportsHandler.organizeImports(unit, (importChoices, ranges) -> {
			assertEquals(1, importChoices.length);
			assertEquals(1, ranges.length);
			assertEquals(2, importChoices[0].length);
			return new ImportChoice[] { importChoices[0][0] };
		});
		assertNotNull(edit);
		JavaModelUtil.applyEdit(unit, edit, true, null);

		/* @formatter:off */
		String expected = "package p;\r\n" +
				"\r\n" +
				"import p1.C;\r\n" +
				"\r\n" +
				"public class B {\r\n" +
				"	C c;\r\n" +
				"}";
		//@formatter:on
		compareSource(expected, unit.getSource());
	}
}
