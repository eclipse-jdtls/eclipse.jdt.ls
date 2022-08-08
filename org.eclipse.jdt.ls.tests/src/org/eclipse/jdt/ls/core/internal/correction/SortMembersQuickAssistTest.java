/*******************************************************************************
 * Copyright (c) 2022 Microsoft Corporation and others.
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
package org.eclipse.jdt.ls.core.internal.correction;

import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IPackageFragment;
import org.eclipse.jdt.core.IPackageFragmentRoot;
import org.eclipse.jdt.ls.core.internal.CodeActionUtil;
import org.eclipse.lsp4j.Range;
import org.junit.Before;
import org.junit.Test;

public class SortMembersQuickAssistTest extends AbstractQuickFixTest {
	private IJavaProject fJProject;
	private IPackageFragmentRoot fSourceFolder;

	@Before
	public void setup() throws Exception {
		fJProject = newEmptyProject();
		fJProject.setOptions(TestOptions.getDefaultOptions());
		fSourceFolder = fJProject.getPackageFragmentRoot(fJProject.getProject().getFolder("src"));
	}

	@Test
	public void testSortMembersForType() throws Exception {
		IPackageFragment pack = fSourceFolder.createPackageFragment("test", false, null);
		StringBuilder buf = new StringBuilder();
		buf.append("package test;\n");
		buf.append("public class A {\n");
		buf.append("	private String privateStr = \"private\";\n");
		buf.append("	private String getPrivateStr() { return \"private\"; }\n");
		buf.append("	public String publicStr = \"public\";\n");
		buf.append("	public String getPublicStr() { return \"public\"; }\n");
		buf.append("	private String privateStr1 = \"private1\";\n");
		buf.append("}\n");
		ICompilationUnit cu = pack.createCompilationUnit("A.java", buf.toString(), false, null);

		buf = new StringBuilder();
		buf.append("package test;\n");
		buf.append("public class A {\n");
		buf.append("	private String privateStr = \"private\";\n");
		buf.append("	public String publicStr = \"public\";\n");
		buf.append("	private String privateStr1 = \"private1\";\n");
		buf.append("	public String getPublicStr() { return \"public\"; }\n");
		buf.append("	private String getPrivateStr() { return \"private\"; }\n");
		buf.append("}\n");
		Expected e1 = new Expected("Sort Members for 'A.java'", buf.toString());

		Range selection = CodeActionUtil.getRange(cu, "A");
		assertCodeActions(cu, selection, e1);
	}

	@Test
	public void testSortMembersForTypeWithFields() throws Exception {
		IPackageFragment pack = fSourceFolder.createPackageFragment("test", false, null);
		this.preferenceManager.getPreferences().setAvoidVolatileChanges(false);
		StringBuilder buf = new StringBuilder();
		buf.append("package test;\n");
		buf.append("public class A {\n");
		buf.append("	private String privateStr = \"private\";\n");
		buf.append("	private String getPrivateStr() { return \"private\"; }\n");
		buf.append("	public String publicStr = \"public\";\n");
		buf.append("	public String getPublicStr() { return \"public\"; }\n");
		buf.append("	private String privateStr1 = \"private1\";\n");
		buf.append("}\n");
		ICompilationUnit cu = pack.createCompilationUnit("A.java", buf.toString(), false, null);

		buf = new StringBuilder();
		buf.append("package test;\n");
		buf.append("public class A {\n");
		buf.append("	public String publicStr = \"public\";\n");
		buf.append("	private String privateStr = \"private\";\n");
		buf.append("	private String privateStr1 = \"private1\";\n");
		buf.append("	public String getPublicStr() { return \"public\"; }\n");
		buf.append("	private String getPrivateStr() { return \"private\"; }\n");
		buf.append("}\n");
		Expected e1 = new Expected("Sort Members for 'A.java'", buf.toString());

		Range selection = CodeActionUtil.getRange(cu, "A");
		assertCodeActions(cu, selection, e1);
	}

	@Test
	public void testSortMembersForSelection() throws Exception {
		IPackageFragment pack = fSourceFolder.createPackageFragment("test", false, null);
		StringBuilder buf = new StringBuilder();
		buf.append("package test;\n");
		buf.append("public class A {\n");
		buf.append("	private String privateStr = \"private\";\n");
		buf.append("	private String getPrivateStr() { return \"private\"; }\n");
		buf.append("	public String publicStr = \"public\";\n");
		buf.append("	public String getPublicStr() { return \"public\"; }\n");
		buf.append("	private String privateStr1 = \"private1\";\n");
		buf.append("}\n");
		ICompilationUnit cu = pack.createCompilationUnit("A.java", buf.toString(), false, null);

		buf = new StringBuilder();
		buf.append("package test;\n");
		buf.append("public class A {\n");
		buf.append("	private String privateStr = \"private\";\n");
		buf.append("	public String publicStr = \"public\";\n");
		buf.append("	public String getPublicStr() { return \"public\"; }\n");
		buf.append("	private String getPrivateStr() { return \"private\"; }\n");
		buf.append("	private String privateStr1 = \"private1\";\n");
		buf.append("}\n");
		Expected e1 = new Expected("Sort Selected Members", buf.toString());

		Range selection = CodeActionUtil.getRange(cu, "private String getPrivateStr() { return \"private\"; }\n\tpublic String publicStr = \"public\";\n\tpublic String getPublicStr() { return \"public\"; }\n");
		assertCodeActions(cu, selection, e1);
	}
}
