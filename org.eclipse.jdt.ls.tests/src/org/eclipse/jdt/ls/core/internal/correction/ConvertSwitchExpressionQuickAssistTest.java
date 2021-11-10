/*******************************************************************************
 * Copyright (c) 2022 Red Hat Inc. and others.
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

import java.util.HashMap;
import java.util.Map;

import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IPackageFragment;
import org.eclipse.jdt.core.IPackageFragmentRoot;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.internal.corext.util.JavaModelUtil;
import org.eclipse.jdt.ls.core.internal.CodeActionUtil;
import org.eclipse.lsp4j.Range;
import org.junit.Before;
import org.junit.Test;

public class ConvertSwitchExpressionQuickAssistTest extends AbstractQuickFixTest {
	private IJavaProject fJProject;
	private IPackageFragmentRoot fSourceFolder;

	@Before
	public void setup() throws Exception {
		fJProject = newEmptyProject();
		Map<String, String> options14 = new HashMap<>(fJProject.getOptions(false));
		JavaModelUtil.setComplianceOptions(options14, JavaCore.VERSION_14);
		fJProject.setOptions(options14);
		fSourceFolder = fJProject.getPackageFragmentRoot(fJProject.getProject().getFolder("src"));
	}

	@Test
	public void testConvertToSwitchExpression1() throws Exception {
		IPackageFragment pack = fSourceFolder.createPackageFragment("test", false, null);

		StringBuilder buf = new StringBuilder();
		buf.append("package test;\n");
		buf.append("public class Cls {\n");
		buf.append("	public int foo(Day day) {\n");
		buf.append("		// return variable\n");
		buf.append("		int i;\n");
		buf.append("		switch (day) {\n");
		buf.append("			case SATURDAY:\n");
		buf.append("			case SUNDAY: i = 5; break;\n");
		buf.append("			case MONDAY:\n");
		buf.append("			case TUESDAY, WEDNESDAY: i = 7; break;\n");
		buf.append("			case THURSDAY:\n");
		buf.append("			case FRIDAY: i = 14; break;\n");
		buf.append("			default :\n");
		buf.append("				i = 22;\n");
		buf.append("				break;\n");
		buf.append("		}\n");
		buf.append("		return i;\n");
		buf.append("	}\n");
		buf.append("}\n");
		buf.append("\n");
		buf.append("enum Day {\n");
		buf.append("    MONDAY, TUESDAY, WEDNESDAY, THURSDAY, FRIDAY, SATURDAY, SUNDAY;\n");
		buf.append("}\n");
		ICompilationUnit cu = pack.createCompilationUnit("Cls.java", buf.toString(), false, null);

		buf = new StringBuilder();
		buf.append("package test;\n");
		buf.append("public class Cls {\n");
		buf.append("	public int foo(Day day) {\n");
		buf.append("		// return variable\n");
		buf.append("		int i = switch (day) {\n");
		buf.append("			case SATURDAY, SUNDAY -> 5;\n");
		buf.append("			case MONDAY, TUESDAY, WEDNESDAY -> 7;\n");
		buf.append("			case THURSDAY, FRIDAY -> 14;\n");
		buf.append("			default -> 22;\n");
		buf.append("		};\n");
		buf.append("		return i;\n");
		buf.append("	}\n");
		buf.append("}\n");
		buf.append("\n");
		buf.append("enum Day {\n");
		buf.append("    MONDAY, TUESDAY, WEDNESDAY, THURSDAY, FRIDAY, SATURDAY, SUNDAY;\n");
		buf.append("}\n");

		Expected e = new Expected("Convert to switch expression", buf.toString());
		Range selection = CodeActionUtil.getRange(cu, "switch");
		assertCodeActions(cu, selection, e);
	}

	@Test
	public void testNoConvertToSwitchExpression1() throws Exception {
		IPackageFragment pack = fSourceFolder.createPackageFragment("test", false, null);

		StringBuilder buf = new StringBuilder();
		buf.append("package test;\n");
		buf.append("public class Cls {\n");
		buf.append("	static int i;\n");
		buf.append("	static {\n");
		buf.append("		// var comment\n");
		buf.append("		int j = 4;\n");
		buf.append("		// logic comment\n");
		buf.append("		switch (j) {\n");
		buf.append("			case 0: break; // no statements\n");
		buf.append("			case 1: i = 5; break;\n");
		buf.append("			case 2:\n");
		buf.append("			case 3:\n");
		buf.append("			case 4: System.out.println(\"here\"); i = 7; break;\n");
		buf.append("			case 5:\n");
		buf.append("			case 6: i = 14; break;\n");
		buf.append("			default: i = 22; break;\n");
		buf.append("		}\n");
		buf.append("	}\n");
		buf.append("}\n");
		ICompilationUnit cu = pack.createCompilationUnit("Cls.java", buf.toString(), false, null);
		Range selection = CodeActionUtil.getRange(cu, "switch");
		assertCodeActionNotExists(cu, selection, "Convert to switch expression");
	}

}
