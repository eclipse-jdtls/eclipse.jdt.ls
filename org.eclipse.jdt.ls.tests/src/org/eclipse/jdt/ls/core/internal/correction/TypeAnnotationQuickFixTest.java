/*******************************************************************************
 * Copyright (c) 2026 Till Brychcy and others.
 *
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *     Till Brychcy - initial API and implementation
 *     IBM Corporation - refactored to jdt.ls
 *******************************************************************************/
package org.eclipse.jdt.ls.core.internal.correction;

import java.io.IOException;
import java.net.URL;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.Map;

import org.eclipse.core.runtime.FileLocator;
import org.eclipse.core.runtime.Path;
import org.eclipse.core.runtime.Platform;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IPackageFragment;
import org.eclipse.jdt.core.IPackageFragmentRoot;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.formatter.DefaultCodeFormatterConstants;
import org.eclipse.jdt.internal.core.manipulation.CodeTemplateContextType;
import org.eclipse.jdt.internal.core.manipulation.StubUtility;
import org.eclipse.jdt.internal.core.manipulation.dom.ASTResolving;
import org.eclipse.jdt.internal.corext.codemanipulation.CodeGenerationSettingsConstants;
import org.eclipse.jdt.internal.corext.util.JavaModelUtil;
import org.eclipse.jdt.ls.core.internal.JavaProjectHelper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class TypeAnnotationQuickFixTest extends AbstractQuickFixTest {

	private IJavaProject fJProject1;
	private String ANNOTATION_JAR_PATH;
	private IPackageFragmentRoot fSourceFolder;

	@BeforeEach
	public void setUp() throws Exception {
		fJProject1 = newEmptyProject();
		Map<String, String> options1d8 = new HashMap<>(fJProject1.getOptions(false));
		JavaModelUtil.setComplianceOptions(options1d8, JavaCore.VERSION_1_8);
		fJProject1.setOptions(options1d8);

		Hashtable<String, String> options = TestOptions.getDefaultOptions();
		JavaModelUtil.setComplianceOptions(options, JavaCore.VERSION_1_8);
		options.put(DefaultCodeFormatterConstants.FORMATTER_TAB_CHAR, JavaCore.SPACE);
		options.put(DefaultCodeFormatterConstants.FORMATTER_TAB_SIZE, "4");
		options.put(DefaultCodeFormatterConstants.FORMATTER_INSERT_NEW_LINE_AFTER_ANNOTATION_ON_LOCAL_VARIABLE, JavaCore.DO_NOT_INSERT);
		options.put(CodeGenerationSettingsConstants.CODEGEN_ADD_COMMENTS, DefaultCodeFormatterConstants.TRUE);
		JavaCore.setOptions(options);

		StubUtility.setCodeTemplate(CodeTemplateContextType.METHODSTUB_ID, "", null);
		StubUtility.setCodeTemplate(CodeTemplateContextType.CONSTRUCTORSTUB_ID, "", null);

		fSourceFolder = fJProject1.getPackageFragmentRoot(fJProject1.getProject().getFolder("src"));
	}


	public static String getAnnotationLibPath() throws IOException {
		URL libEntry = Platform.getBundle("org.eclipse.jdt.ls.tests").getEntry("/testresources/org.eclipse.jdt.annotation_2.4.100.v20251017-1955.jar");
		return FileLocator.toFileURL(libEntry).getPath();
	}

	protected static CompilationUnit getASTRoot(ICompilationUnit cu) {
		return ASTResolving.createQuickFixAST(cu, null);
	}

	public void runQuickFixTest(String testCaseSpecificInput, String... testCaseSpecificOutputs) throws Exception {
		runQuickFixTest2(testCaseSpecificInput, "Move type annotation", testCaseSpecificOutputs);
	}

	public void runQuickFixTest2(String testCaseSpecificInput, String label, String[] testCaseSpecificOutputs) throws Exception {
		Hashtable<String, String> options= JavaCore.getOptions();
		options.put(JavaCore.COMPILER_ANNOTATION_NULL_ANALYSIS, JavaCore.ENABLED);
		options.put(JavaCore.COMPILER_PB_NULL_SPECIFICATION_VIOLATION, JavaCore.ERROR);
		options.put(JavaCore.COMPILER_PB_NULL_REFERENCE, JavaCore.ERROR);
		options.put(JavaCore.COMPILER_PB_POTENTIAL_NULL_REFERENCE, JavaCore.WARNING);
		options.put(JavaCore.COMPILER_PB_NULL_ANNOTATION_INFERENCE_CONFLICT, JavaCore.WARNING);
		options.put(JavaCore.COMPILER_PB_NULL_UNCHECKED_CONVERSION, JavaCore.WARNING);
		options.put(JavaCore.COMPILER_PB_REDUNDANT_NULL_CHECK, JavaCore.WARNING);
		options.put(JavaCore.COMPILER_PB_NULL_UNCHECKED_CONVERSION, JavaCore.WARNING);
		JavaCore.setOptions(options);

		if (ANNOTATION_JAR_PATH == null) {
			// these tests us the "old" null annotations
			ANNOTATION_JAR_PATH = getAnnotationLibPath();
		}
		JavaProjectHelper.addLibrary(fJProject1, new Path(ANNOTATION_JAR_PATH));

		IPackageFragment pack1= fSourceFolder.createPackageFragment("test", false, null);

		String prefix= """
			package test;

			import java.lang.annotation.ElementType;
			import java.lang.annotation.Target;

			import org.eclipse.jdt.annotation.Nullable;

			@Target(ElementType.TYPE_USE) @interface X {}
			@Target(ElementType.TYPE_USE) @interface Y {}
			@Target(ElementType.TYPE_USE) @interface N { int[] v1(); String v2(); }
			@Target(ElementType.TYPE_USE) @interface S { int[] value(); }
			@Target({ ElementType.METHOD, ElementType.FIELD, ElementType.PARAMETER, ElementType.LOCAL_VARIABLE }) @interface Old {}

			class File {}

			class Outer {
				static class StaticInner {}
				class Inner {}
			}

			class Generic<T> {@Nullable Object f;}
			""";

		String input= prefix +
				"public class Test {\n" +
				testCaseSpecificInput + "\n" +
				"}\n";
		ICompilationUnit cu= pack1.createCompilationUnit("Test.java", input, false, null);

		int nProblems= testCaseSpecificOutputs.length;
		for (int p= 0; p < nProblems; p++) {
			String expected= prefix +
					"public class Test {\n" +
					testCaseSpecificOutputs[p] + "\n" +
					"}\n";
			Expected e = new Expected(label, expected);
			assertCodeActionExists(cu, e);
		}
	}

	@Test
	public void testMoveAnnotationM1() throws Exception {
		runQuickFixTest(
				"@Old @Nullable test.File m1() { return null; }",
				"@Old test.@Nullable File m1() { return null; }");
	}

	// assert that multiple problems produce their expected proposals independently
	@Test
	public void testMoveAnnotationM1a() throws Exception {
		runQuickFixTest(
				"@Old @X @Nullable test.File m1() { return null; }",
				"@Old @Nullable test.@X File m1() { return null; }"
				);
	}

	@Test
	public void testMoveAnnotationM2() throws Exception {
		runQuickFixTest(
				"@Old @Nullable test.@X File m2() { return null; }",
				"@Old test.@X @Nullable File m2() { return null; }");
	}

	@Test
	public void testMoveAnnotationM3() throws Exception {
		runQuickFixTest(
				"@Nullable Outer.StaticInner m4() { return null; }",
				"Outer.@Nullable StaticInner m4() { return null; }");
	}

	@Test
	public void testMoveAnnotationM4() throws Exception {
		runQuickFixTest(
				"@Nullable Outer.@X StaticInner m3() { return null; }",
				"Outer.@X @Nullable StaticInner m3() { return null; }");
	}

	@Test
	public void testMoveAnnotationM5() throws Exception {
		runQuickFixTest(
				"@Nullable Outer.Inner m5() { return null; }",
				"Outer.@Nullable Inner m5() { return null; }");
	}

	@Test
	public void testMoveAnnotationM6() throws Exception {
		runQuickFixTest(
				"@Nullable @Y Outer.@X Inner m6() { return null; }",
				"@Y Outer.@X @Nullable Inner m6() { return null; }");
	}

	@Test
	public void testMoveAnnotationM7() throws Exception {
		runQuickFixTest(
				"@Nullable test.Generic<?> m7() { return null; }",
				"test.@Nullable Generic<?> m7() { return null; }");
	}

	@Test
	public void testMoveAnnotationM8() throws Exception {
		runQuickFixTest(
				"@Old @Nullable test.Generic<?> m8() { return null; }",
				"@Old test.@Nullable Generic<?> m8() { return null; }");
	}


	@Test
	public void testMoveAnnotationM9() throws Exception {
		runQuickFixTest(
				"@Nullable test.Generic<?>[] m9() { return null; }",
				"test.Generic<?> @Nullable [] m9() { return null; }");
	}

	@Test
	public void testMoveAnnotationM10() throws Exception {
		runQuickFixTest(
				"@Nullable test.Generic<?> @X [] m10() { return null; }",
				"test.Generic<?> @X @Nullable [] m10() { return null; }");
	}

	@Test
	public void testMoveAnnotationM11() throws Exception {
		runQuickFixTest(
				"@Old @Nullable test.Generic<?>[] m11() { return null; }",
				"@Old test.Generic<?> @Nullable [] m11() { return null; }");
	}

	@Test
	public void testMoveAnnotationM12() throws Exception {
		runQuickFixTest(
				"@Old @Nullable test.Generic<?> @Y [] @X [] m12() { return null; }",
				"@Old test.Generic<?> @Y @Nullable [] @X [] m12() { return null; }");
	}

	@Test
	public void testMoveAnnotationP1() throws Exception {
		runQuickFixTest(
				"void p1(@Nullable test.File arg) {}",
				"void p1(test.@Nullable File arg) {}");
	}

	@Test
	public void testMoveAnnotationP2() throws Exception {
		runQuickFixTest(
				"void p2(@Nullable test.@X File arg) {}",
				"void p2(test.@X @Nullable File arg) {}");
	}

	@Test
	public void testMoveAnnotationP3() throws Exception {
		runQuickFixTest(
				"void p3(@Nullable @Y Outer.@X Inner arg) {}",
				"void p3(@Y Outer.@X @Nullable Inner arg) {}");
	}

	@Test
	public void testMoveAnnotationP4() throws Exception {
		runQuickFixTest(
				"void p4(@Nullable test.Generic<?> @Y [] @X [] arg) {}",
				"void p4(test.Generic<?> @Y @Nullable [] @X [] arg) {}");
	}

	@Test
	public void testMoveAnnotationL1() throws Exception {
		runQuickFixTest(
				"void l1() { @Nullable test.@X File var1, var2;}",
				"void l1() { test.@X @Nullable File var1, var2;}");
	}

	@Test
	public void testMoveAnnotationB1() throws Exception {
		runQuickFixTest(
				"<T extends @Nullable test.File> T b1() { return null; }",
				"<T extends test.@Nullable File> T b1() { return null; }");
	}

	@Test
	public void testMoveAnnotationB2() throws Exception {
		runQuickFixTest(
				"<T extends @org.eclipse.jdt.annotation.Nullable test.File> T b2() { return null; }",
				"<T extends test.@org.eclipse.jdt.annotation.Nullable File> T b2() { return null; }");
	}

	@Test
	public void testMoveAnnotationB3() throws Exception {
		runQuickFixTest(
				"<T extends @org.eclipse.jdt.annotation.Nullable test.@X File> T b3() { return null; }",
				"<T extends test.@X @org.eclipse.jdt.annotation.Nullable File> T b3() { return null; }");
	}

	@Test
	public void testMoveAnnotationC1() throws Exception {
		runQuickFixTest(
				"Object c1() { return (@Nullable test.File) null; }",
				"Object c1() { return (test.@Nullable File) null; }");
	}

	@Test
	public void testMoveAnnotationF1() throws Exception {
		runQuickFixTest(
				"@Nullable test.File f1a, f1b;",
				"test.@Nullable File f1a, f1b;");
	}

	@Test
	public void testNormalAnnotationP4() throws Exception {
		runQuickFixTest(
				"void p4(@N(v1 = {100,101}, v2 = \"someString\") test.Generic<?> @Y [] @X [] arg) {}",
				"void p4(test.Generic<?> @Y @N(v1 = {100,101}, v2 = \"someString\") [] @X [] arg) {}");
	}

	// assert that formatting (here space after ,) is preserved by the quickfix
	@Test
	public void testNormalAnnotationP4a() throws Exception {
		runQuickFixTest(
				"void p4(@N(v1 = {100, 101}, v2 = \"someString\") test.Generic<?> @Y [] @X [] arg) {}",
				"void p4(test.Generic<?> @Y @N(v1 = {100, 101}, v2 = \"someString\") [] @X [] arg) {}");
	}

	@Test
	public void testMoveNormalAnnotationL1() throws Exception {
		runQuickFixTest(
				"void l1() { @N(v1 = 100, v2 = \"someString\") test.@X File var1, var2;}",
				"void l1() { test.@X @N(v1 = 100, v2 = \"someString\") File var1, var2;}");
	}

	@Test
	public void testMoveSingleValueAnnotationB2() throws Exception {
		runQuickFixTest(
				"<T extends @S(1) test.File> T b2() { return null; }",
				"<T extends test.@S(1) File> T b2() { return null; }");
	}
	@Test
	public void testMoveSingleValueAnnotationF1() throws Exception {
		runQuickFixTest(
				"@S({1,2}) test.File f1a, f1b;",
				"test.@S({1,2}) File f1a, f1b;");
	}
	// assert that a comment is preserved:
	@Test
	public void testMoveSingleValueAnnotationF1a() throws Exception {
		runQuickFixTest(
				"@S({1,2}) test/*here*/.File f1a, f1b;",
				"test/*here*/.@S({1,2}) File f1a, f1b;");
	}
	@Test
	public void testMoveAnnotationOnByteArray() throws Exception {
		runQuickFixTest(
				"@Old @Nullable byte [] m12() { return null; }",
				"@Old byte @Nullable [] m12() { return null; }");
	}
	@Test
	public void testMoveAnnotationOnByteMultiDimArray() throws Exception {
		runQuickFixTest(
				"@Old @Nullable byte [][] m12() { return null; }",
				"@Old byte @Nullable [][] m12() { return null; }");
	}
	@Test
	public void testMoveAnnotationOnByteArraySuffixSyntax() throws Exception {
		runQuickFixTest(
				"@Old @Nullable byte m12()[] { return null; }",
				"@Old byte m12() @Nullable [] { return null; }");
	}
	@Test
	public void testMoveAnnotationOnByteArrayMixedSyntax() throws Exception {
		runQuickFixTest(
				"@Old @Nullable byte [] m12()[][] { return null; }",
				"@Old byte [] m12() @Nullable [][] { return null; }");
	}

	@Test
	public void testRemoveAnnotationOnByteNoArray() throws Exception {
		runQuickFixTest2(
				"@Old @Nullable byte m12() { return 0; }",
				"Remove type annotation",
				new String[] { "@Old byte m12() { return 0; }" });
	}
}
