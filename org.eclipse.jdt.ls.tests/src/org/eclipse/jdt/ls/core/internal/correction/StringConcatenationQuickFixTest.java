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

import java.text.MessageFormat;
import java.util.HashMap;
import java.util.Map;

import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IPackageFragment;
import org.eclipse.jdt.core.IPackageFragmentRoot;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.internal.corext.util.JavaModelUtil;
import org.eclipse.jdt.internal.ui.text.correction.CorrectionMessages;
import org.eclipse.jdt.ls.core.internal.CodeActionUtil;
import org.eclipse.lsp4j.Range;
import org.junit.Before;
import org.junit.Test;

public class StringConcatenationQuickFixTest extends AbstractQuickFixTest {
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
	public void testConvertToStringFormat() throws Exception {
		IPackageFragment pack = fSourceFolder.createPackageFragment("test", false, null);

		String content = """
				package test;
				public class Test {
					private void print(String name, int age) {
					  String value = "User name: " + name + ", age: " + age;
					}
				}
				""";
		ICompilationUnit cu = pack.createCompilationUnit("Test.java", content, false, null);

		String expected = """
				package test;
				public class Test {
					private void print(String name, int age) {
					  String value = String.format("User name: %s, age: %d", name, age);
					}
				}
				""";

		Expected e = new Expected(CorrectionMessages.QuickAssistProcessor_convert_to_string_format, expected);
		Range selection = CodeActionUtil.getRange(cu, "User name:");
		assertCodeActions(cu, selection, e);
	}

	@Test
	public void testConvertToStringBuilder() throws Exception {
		IPackageFragment pack = fSourceFolder.createPackageFragment("test", false, null);

		String content = """
				package test;
				public class Test {
					private void print(String name, int age) {
					  String value = "User name: " + name + ", age: " + age;
					}
				}
				""";
		ICompilationUnit cu = pack.createCompilationUnit("Test.java", content, false, null);

		String expected = """
				package test;
				public class Test {
					private void print(String name, int age) {
					  StringBuilder stringBuilder = new StringBuilder();
						stringBuilder.append("User name: ");
						stringBuilder.append(name);
						stringBuilder.append(", age: ");
						stringBuilder.append(age);
					String value = stringBuilder.toString();
					}
				}
				""";

		Expected e = new Expected(MessageFormat.format(CorrectionMessages.QuickAssistProcessor_convert_to_string_buffer_description, "StringBuilder"), expected);
		Range selection = CodeActionUtil.getRange(cu, "User name:");
		assertCodeActions(cu, selection, e);
	}

	@Test
	public void testConvertToMessageFormat() throws Exception {
		IPackageFragment pack = fSourceFolder.createPackageFragment("test", false, null);

		String content = """
				package test;
				public class Test {
					private void print(String name, int age) {
					  String value = "User name: " + name + ", age: " + age;
					}
				}
				""";
		ICompilationUnit cu = pack.createCompilationUnit("Test.java", content, false, null);

		String expected = """
				package test;

				import java.text.MessageFormat;

				public class Test {
					private void print(String name, int age) {
					  String value = MessageFormat.format("User name: {0}, age: {1}", name, age);
					}
				}
				""";

		Expected e = new Expected(CorrectionMessages.QuickAssistProcessor_convert_to_message_format, expected);
		Range selection = CodeActionUtil.getRange(cu, "User name:");
		assertCodeActions(cu, selection, e);
	}

	@Test
	public void testConvertToStringBuffer() throws Exception {
		Map<String, String> options1_4 = new HashMap<>(fJProject.getOptions(false));
		JavaModelUtil.setComplianceOptions(options1_4, JavaCore.VERSION_1_4);
		fJProject.setOptions(options1_4);

		try {
			IPackageFragment pack = fSourceFolder.createPackageFragment("test", false, null);

			String content = """
					package test;
					public class Test {
						private void print(String name, int age) {
						  String value = "User name: " + name + ", age: " + age;
						}
					}
					""";
			ICompilationUnit cu = pack.createCompilationUnit("Test.java", content, false, null);

			String expected = """
					package test;
					public class Test {
						private void print(String name, int age) {
						  StringBuffer stringBuffer = new StringBuffer();
							stringBuffer.append("User name: ");
							stringBuffer.append(name);
							stringBuffer.append(", age: ");
							stringBuffer.append(age);
						String value = stringBuffer.toString();
						}
					}
					""";

			Expected e = new Expected(MessageFormat.format(CorrectionMessages.QuickAssistProcessor_convert_to_string_buffer_description, "StringBuffer"), expected);
			Range selection = CodeActionUtil.getRange(cu, "User name:");
			assertCodeActions(cu, selection, e);
		} finally {
			Map<String, String> options17 = new HashMap<>(fJProject.getOptions(false));
			JavaModelUtil.setComplianceOptions(options17, JavaCore.VERSION_17);
			fJProject.setOptions(options17);
		}
	}

}
