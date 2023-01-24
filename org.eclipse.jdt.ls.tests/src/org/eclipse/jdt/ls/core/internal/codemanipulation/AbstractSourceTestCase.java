/*******************************************************************************
 * Copyright (c) 2000, 2008 IBM Corporation and others.
 *
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/

package org.eclipse.jdt.ls.core.internal.codemanipulation;

import static org.junit.Assert.assertNotNull;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.util.Map;

import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IPackageFragment;
import org.eclipse.jdt.core.IPackageFragmentRoot;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.formatter.DefaultCodeFormatterConstants;
import org.eclipse.jdt.internal.core.manipulation.CodeTemplateContextType;
import org.eclipse.jdt.internal.core.manipulation.StubUtility;
import org.eclipse.jdt.ls.core.internal.managers.AbstractProjectsManagerBasedTest;
import org.eclipse.jdt.ls.core.internal.managers.StandardProjectsManager;
import org.junit.Assert;
import org.junit.Before;

public class AbstractSourceTestCase extends AbstractProjectsManagerBasedTest {
	private IJavaProject fJavaProject;
	protected IPackageFragment fPackageP;
	protected IType fClassA;
	protected ICompilationUnit fCuA;
	protected IPackageFragmentRoot fRoot;

	private void initCodeTemplates() {
		Map<String, String> options = fJavaProject.getOptions(true);
		options.put(DefaultCodeFormatterConstants.FORMATTER_TAB_CHAR, JavaCore.TAB);
		options.put(DefaultCodeFormatterConstants.FORMATTER_TAB_SIZE, "4");
		options.put(DefaultCodeFormatterConstants.FORMATTER_LINE_SPLIT, "999");
		options.put(DefaultCodeFormatterConstants.FORMATTER_BLANK_LINES_BEFORE_FIELD, "1");
		options.put(DefaultCodeFormatterConstants.FORMATTER_BLANK_LINES_BEFORE_METHOD, "1");
		fJavaProject.setOptions(options);

		String getterComment = "/**\r\n" + " * @return Returns the ${bare_field_name}.\r\n" + " */";
		String getterBody = "return ${field};";

		String setterComment = "/**\r\n" + " * @param ${param} The ${bare_field_name} to set.\r\n" + " */";
		String setterBody = "${field} = ${param};";

		StubUtility.setCodeTemplate(CodeTemplateContextType.GETTERCOMMENT_ID, getterComment, null);
		StubUtility.setCodeTemplate(CodeTemplateContextType.SETTERCOMMENT_ID, setterComment, null);
		StubUtility.setCodeTemplate(CodeTemplateContextType.GETTERSTUB_ID, getterBody, null);
		StubUtility.setCodeTemplate(CodeTemplateContextType.SETTERSTUB_ID, setterBody, null);

		String methodComment = "/**\r\n" + " * ${tags}\r\n" + " */";
		String methodBody = "// ${todo} Auto-generated method stub\nthrow new UnsupportedOperationException(\"Unimplemented method \'${enclosing_method}\'\");";
		String methodBodySuper = "// ${todo} Auto-generated method stub\r\n" + "${body_statement}";

		String constructorComment = "/**\r\n" + " * ${tags}\r\n" + " */";
		String constructorBody = "${body_statement}\r\n" + "// ${todo} Auto-generated constructor stub";

		StubUtility.setCodeTemplate(CodeTemplateContextType.METHODCOMMENT_ID, methodComment, null);
		StubUtility.setCodeTemplate(CodeTemplateContextType.METHODSTUB_ID, methodBody, null);
		StubUtility.setCodeTemplate(CodeTemplateContextType.METHODSTUB_ALTERNATIVE_ID, methodBodySuper, null);
		StubUtility.setCodeTemplate(CodeTemplateContextType.CONSTRUCTORCOMMENT_ID, constructorComment, null);
		StubUtility.setCodeTemplate(CodeTemplateContextType.CONSTRUCTORSTUB_ID, constructorBody, null);
	}

	@Before
	public void setUp() throws Exception {
		StandardProjectsManager.configureSettings(preferences, true);
		fJavaProject = newEmptyProject();
		fRoot = fJavaProject.findPackageFragmentRoot(fJavaProject.getPath().append("src"));
		assertNotNull(fRoot);
		fPackageP = fRoot.createPackageFragment("p", true, null);
		fCuA = fPackageP.getCompilationUnit("A.java");
		fClassA = fCuA.createType("public class A {\n}\n", null, true, null);

		initCodeTemplates();
	}

	public static void compareSource(String expected, String actual) throws IOException {
		if (actual == null || expected == null) {
			if (actual == expected) {
				return;
			}
			if (actual == null) {
				Assert.assertTrue("Content not as expected: is 'null' expected: " + expected, false);
			} else {
				Assert.assertTrue("Content not as expected: expected 'null' is: " + actual, false);
			}
		}

		BufferedReader read1 = new BufferedReader(new StringReader(actual));
		BufferedReader read2 = new BufferedReader(new StringReader(expected));

		int line = 1;
		do {
			String s1 = read1.readLine();
			String s2 = read2.readLine();

			if (s1 == null || !s1.equals(s2)) {
				if (s1 == null && s2 == null) {
					return;
				}
				String diffStr = (s1 == null) ? s2 : s1;

				String message = "Content not as expected: Content is: \n" + actual + "\nDiffers at line " + line + ": " + diffStr + "\nExpected contents: \n" + expected;
				Assert.assertEquals(message, expected, actual);
			}
			line++;
		} while (true);
	}
}
