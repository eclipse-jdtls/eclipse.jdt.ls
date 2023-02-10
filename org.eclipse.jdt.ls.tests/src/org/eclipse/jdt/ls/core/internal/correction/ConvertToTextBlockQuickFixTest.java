/*******************************************************************************
 * Copyright (c) 2023 Red Hat Inc. and others.
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
import org.eclipse.jdt.internal.corext.fix.FixMessages;
import org.eclipse.jdt.internal.corext.util.JavaModelUtil;
import org.eclipse.jdt.ls.core.internal.CodeActionUtil;
import org.eclipse.lsp4j.Range;
import org.junit.Before;
import org.junit.Test;

public class ConvertToTextBlockQuickFixTest extends AbstractQuickFixTest {
	private IJavaProject fJProject;
	private IPackageFragmentRoot fSourceFolder;

	@Before
	public void setup() throws Exception {
		fJProject = newEmptyProject();
		Map<String, String> options17 = new HashMap<>(fJProject.getOptions(false));
		JavaModelUtil.setComplianceOptions(options17, JavaCore.VERSION_17);
		fJProject.setOptions(options17);
		fSourceFolder = fJProject.getPackageFragmentRoot(fJProject.getProject().getFolder("src"));
	}

	@Test
	public void testConvertToTextBlock() throws Exception {
		IPackageFragment pack = fSourceFolder.createPackageFragment("test", false, null);

		String content = """
				package test;
				public class Test {
				  String html = "<html>\\n"
				      + "    <body>\\n"
				      + "        <span>example text</span>\\n"
				      + "    </body>\\n"
				      + "</html>";
				}
				""";
		ICompilationUnit cu = pack.createCompilationUnit("Test.java", content, false, null);

		String expected = """
			package test;
			public class Test {
			  String html = \"""
				<html>
				    <body>
				        <span>example text</span>
				    </body>
				</html>\""";
			}
			""";

		Expected e = new Expected(FixMessages.StringConcatToTextBlockFix_convert_msg, expected);
		Range selection = CodeActionUtil.getRange(cu, "<body>");
		assertCodeActions(cu, selection, e);
	}

	@Test
	public void testNoConvertToTextBlock() throws Exception {
		Map<String, String> options11 = new HashMap<>(fJProject.getOptions(false));
		JavaModelUtil.setComplianceOptions(options11, JavaCore.VERSION_11);
		fJProject.setOptions(options11);

		IPackageFragment pack = fSourceFolder.createPackageFragment("test", false, null);

		String content = """
				package test;
				public class Test {
				  String html = "<html>\\n"
				      + "    <body>\\n"
				      + "        <span>example text</span>\\n"
				      + "    </body>\\n"
				      + "</html>";
				}
				""";
		ICompilationUnit cu = pack.createCompilationUnit("Test.java", content, false, null);

		Range selection = CodeActionUtil.getRange(cu, "<body>");
		assertCodeActionNotExists(cu, selection, FixMessages.StringConcatToTextBlockFix_convert_msg);
	}

}
