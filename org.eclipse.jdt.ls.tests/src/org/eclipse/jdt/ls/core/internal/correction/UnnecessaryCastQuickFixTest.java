/*******************************************************************************
 * Copyright (c) 2019 Red Hat Inc. and others.
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
package org.eclipse.jdt.ls.core.internal.correction;

import java.util.Map;

import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IPackageFragment;
import org.eclipse.jdt.core.IPackageFragmentRoot;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.ls.core.internal.CodeActionUtil;
import org.eclipse.lsp4j.Range;
import org.junit.Before;
import org.junit.Test;

/**
 * @author nikolas
 *
 */
public class UnnecessaryCastQuickFixTest extends AbstractQuickFixTest {

	private IJavaProject fJProject;
	private IPackageFragmentRoot fSourceFolder;

	@Before
	public void setup() throws Exception {
		fJProject = newEmptyProject();
		fJProject.setOptions(TestOptions.getDefaultOptions());
		fSourceFolder = fJProject.getPackageFragmentRoot(fJProject.getProject().getFolder("src"));
	}

	@Test
	public void testUnnecessaryCast() throws Exception {
		Map<String, String> testProjectOptions = fJProject.getOptions(false);
		testProjectOptions.put(JavaCore.COMPILER_PB_UNNECESSARY_TYPE_CHECK, JavaCore.ERROR);

		fJProject.setOptions(testProjectOptions);

		IPackageFragment pack = fSourceFolder.createPackageFragment("test", false, null);
		StringBuilder buf = new StringBuilder();
		buf.append("package test;\n");
		buf.append("public class Driver {\n");
		buf.append("}");
		pack.createCompilationUnit("Driver.java", buf.toString(), false, null);

		buf = new StringBuilder();
		buf.append("package test;\n");
		buf.append("public class BusDriver extends Driver {\n");
		buf.append("  public void drive() {\n");
		buf.append("    Driver d = (Driver) this;\n");
		buf.append("  }\n");
		buf.append("}");
		ICompilationUnit cu = pack.createCompilationUnit("BusDriver.java", buf.toString(), false, null);

		buf = new StringBuilder();
		buf.append("package test;\n");
		buf.append("public class BusDriver extends Driver {\n");
		buf.append("  public void drive() {\n");
		buf.append("    Driver d = this;\n");
		buf.append("  }\n");
		buf.append("}");
		Expected e1 = new Expected("Remove cast", buf.toString());

		Range selection = CodeActionUtil.getRange(cu, "FOO");
		assertCodeActions(cu, selection, e1);
	}
}
