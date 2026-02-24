/*******************************************************************************
 * Copyright (c) 2026 Red Hat Inc. and others.
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
package org.eclipse.jdt.ls.core.internal.correction;

import java.util.HashMap;
import java.util.Map;

import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IPackageFragment;
import org.eclipse.jdt.core.IPackageFragmentRoot;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.internal.corext.refactoring.RefactoringCoreMessages;
import org.eclipse.jdt.internal.corext.util.JavaModelUtil;
import org.eclipse.jdt.ls.core.internal.CodeActionUtil;
import org.eclipse.jdt.ls.core.internal.JDTUtils;
import org.eclipse.lsp4j.Range;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * @author jjohnstn
 *
 */
public class ConvertToRecordQuickAssistTest extends AbstractQuickFixTest {

	private IJavaProject fJProject;
	private IPackageFragmentRoot fSourceFolder;

	@BeforeEach
	public void setup() throws Exception {
		fJProject = newEmptyProject();
		Map<String, String> options16 = new HashMap<>(fJProject.getOptions(false));
		JavaModelUtil.setComplianceOptions(options16, JavaCore.VERSION_16);
		fJProject.setOptions(options16);
		fSourceFolder = fJProject.getPackageFragmentRoot(fJProject.getProject().getFolder("src"));
	}

	@Test
	public void testConvertToRecord1() throws Exception { // https://github.com/eclipse-jdt/eclipse.jdt.ui/issues/2681
		String str = """
				module test {
				}
				""";
		IPackageFragment def = fSourceFolder.createPackageFragment("", false, null);
		def.createCompilationUnit("module-info.java", str, false, null);

		IPackageFragment pack = fSourceFolder.createPackageFragment("test", false, null);
		String str1 = """
				package test;

				public class Cls {
					private final int a;
					private final String b;

					public Cls(int a, String b) {
						this.a= a;
						this.b= b;
					}

					public int getA() {
						return a;
					}

					public String getB() {
						return b;
					}
				}
				""";
		ICompilationUnit cu = pack.createCompilationUnit("Cls.java", str1, false, null);

		String expected = """
				package test;

				public record Cls(int a, String b) {
				}
				""";

		Expected e = new Expected(RefactoringCoreMessages.ConvertToRecordRefactoring_name, expected);
		Range selection = CodeActionUtil.getRange(cu, "getA");
		assertCodeActions(cu, selection, e);
	}

	@Test
	public void testConvertToRecord2() throws Exception {
		String str = """
				module test {
				}
				""";
		IPackageFragment def = fSourceFolder.createPackageFragment("", false, null);
		def.createCompilationUnit("module-info.java", str, false, null);

		IPackageFragment pack = fSourceFolder.createPackageFragment("test", false, null);
		String str1 = """
				package test;

				public class Cls {
					private final int a;
					private final String b;

					public Cls(int a, String b) {
						this.a= a;
						this.b= b;
					}

					public int getA() {
						return a;
					}

					public String getB() {
						return b;
					}
				}
				""";
		ICompilationUnit cu = pack.createCompilationUnit("Cls.java", str1, false, null);

		String str2 = """
				package test;

				public class Cls2 {
					public void foo() {
						Cls cls= new Cls(3, "abc");
						System.out.println(cls.getA());
						System.out.println(cls.getB());
					}
				}
				""";
		pack.createCompilationUnit("Cls2.java", str2, false, null);

		String expected = """
				package test;

				public record Cls(int a, String b) {
				}
				""";

		String expected2 = """
				package test;

				public class Cls2 {
					public void foo() {
						Cls cls= new Cls(3, "abc");
						System.out.println(cls.a());
						System.out.println(cls.b());
					}
				}
				""";
		Expected e = new Expected(RefactoringCoreMessages.ConvertToRecordRefactoring_name, expected);
		Expected e2 = new Expected(RefactoringCoreMessages.ConvertToRecordRefactoring_name, expected2);
		Range selection = CodeActionUtil.getRange(cu, "getA");
		assertCodeActionsMultiFile(cu, selection, e, e2);
	}

	@Test
	public void testConvertToRecord3() throws Exception {
		String str = """
				module test {
				}
				""";
		IPackageFragment def = fSourceFolder.createPackageFragment("", false, null);
		def.createCompilationUnit("module-info.java", str, false, null);

		IPackageFragment pack = fSourceFolder.createPackageFragment("test", false, null);
		String str1 = """
				package test;

				public class Cls {
					// Inner class
					public static class Inner {
						private final int a;
						private final String b;

						public Inner(int a, String b) {
							this.a= a;
							this.b= b;
						}

						public int getA() {
							return a;
						}

						public String getB() {
							return b;
						}
					}
				}
				""";
		ICompilationUnit cu = pack.createCompilationUnit("Cls.java", str1, false, null);

		String str2 = """
				package test;

				public class Cls2 {
					public void foo() {
						Cls.Inner cls= new Cls.Inner(3, "abc");
						System.out.println(cls.getA());
						System.out.println(cls.getB());
					}
				}
				""";
		pack.createCompilationUnit("Cls2.java", str2, false, null);

		String expected = """
				package test;

				public class Cls {
					// Inner class
					public static record Inner(int a, String b) {
					}
				}
				""";

		String expected2 = """
				package test;

				public class Cls2 {
					public void foo() {
						Cls.Inner cls= new Cls.Inner(3, "abc");
						System.out.println(cls.a());
						System.out.println(cls.b());
					}
				}
				""";

		Expected e = new Expected(RefactoringCoreMessages.ConvertToRecordRefactoring_name, expected);
		Expected e2 = new Expected(RefactoringCoreMessages.ConvertToRecordRefactoring_name, expected2);
		Range selection = CodeActionUtil.getRange(cu, "getA");
		assertCodeActionsMultiFile(cu, selection, e, e2);
	}

	@Test
	public void testConvertToRecord4() throws Exception {
		String str = """
				module test {
				}
				""";
		IPackageFragment def = fSourceFolder.createPackageFragment("", false, null);
		def.createCompilationUnit("module-info.java", str, false, null);

		IPackageFragment pack = fSourceFolder.createPackageFragment("test", false, null);
		String str1 = """
				package test;

				public class Cls {
					/**
					 * Class Inner
					 */
					private final class Inner {
						private final int a;
						private final String b;
						private double c;

						public Inner(int a, String b, double c) {
							this.a= a;
							this.b= b;
							this.c= c;
						}

						public int getA() {
							return a;
						}

						public String getB() {
							return b;
						}

						public double getC() {
							return c;
						}
					}
					public void foo() {
						Inner inner= new Inner(1, "comment", 4.3);
						System.out.println(inner.getA());
						System.out.println(inner.getB());
						System.out.println(inner.getC());
					}
				}
				""";
		ICompilationUnit cu = pack.createCompilationUnit("Cls.java", str1, false, null);

		String expected = """
				package test;

				public class Cls {
					/**
					 * Class Inner
					 */
					private record Inner(int a, String b, double c) {
					}
					public void foo() {
						Inner inner= new Inner(1, "comment", 4.3);
						System.out.println(inner.a());
						System.out.println(inner.b());
						System.out.println(inner.c());
					}
				}
				""";
		Expected e = new Expected(RefactoringCoreMessages.ConvertToRecordRefactoring_name, expected);
		String source = cu.getSource();
		int start = source.indexOf("Inner");
		Range selection = JDTUtils.toRange(cu, start, 5);
		assertCodeActions(cu, selection, e);
	}

	@Test
	public void testConvertToRecord5() throws Exception { // https://github.com/eclipse-jdt/eclipse.jdt.ui/issues/2681
		String str = """
				module test {
				}
				""";
		IPackageFragment def = fSourceFolder.createPackageFragment("", false, null);
		def.createCompilationUnit("module-info.java", str, false, null);

		IPackageFragment pack = fSourceFolder.createPackageFragment("test", false, null);
		String str1 = """
				package test;

				/* Class Cls */
				public class Cls extends Object {
					private final int a;
					private final String b;

					public Cls(int a, String b) {
						this.a= a;
						this.b= b;
					}

					public int getA() {
						return a;
					}

					public String getB() {
						return b;
					}
				}
				""";
		ICompilationUnit cu = pack.createCompilationUnit("Cls.java", str1, false, null);

		String expected = """
				package test;

				/* Class Cls */
				public record Cls(int a, String b) {
				}
				""";
		Expected e = new Expected(RefactoringCoreMessages.ConvertToRecordRefactoring_name, expected);
		Range selection = CodeActionUtil.getRange(cu, "getA");
		assertCodeActions(cu, selection, e);

	}

	@Test
	public void testConvertToRecord6() throws Exception { // https://github.com/eclipse-jdt/eclipse.jdt.ui/issues/2
		String str = """
				module test {
				}
				""";
		IPackageFragment def = fSourceFolder.createPackageFragment("", false, null);
		def.createCompilationUnit("module-info.java", str, false, null);

		IPackageFragment pack = fSourceFolder.createPackageFragment("test", false, null);
		String str1 = """
				package test;

				import java.util.Objects;

				/* Class Cls */
				public class Cls extends Object {
					private final int a;
					private final String b;

					public Cls(int a, String b) {
						this.a= a;
						this.b= b;
					}

					public int getA() {
						return a;
					}

					public String getB() {
						return b;
					}

					@Override
					public String toString() {
						return "A [a=" + a + ", b=" + b + "]";
					}

					@Override
					public int hashCode() {
						return Objects.hash(a, b);
					}

					@Override
					public boolean equals(Object obj) {
						if (this == obj)
							return true;
						if (obj == null)
							return false;
						if (getClass() != obj.getClass())
							return false;
						Cls other = (Cls) obj;
						return a == other.a && Objects.equals(b, other.b);
					}
				}
				""";
		ICompilationUnit cu = pack.createCompilationUnit("Cls.java", str1, false, null);

		String expected = """
				package test;

				import java.util.Objects;

				/* Class Cls */
				public record Cls(int a, String b) {
					@Override
					public String toString() {
						return "A [a=" + a + ", b=" + b + "]";
					}

					@Override
					public int hashCode() {
						return Objects.hash(a, b);
					}

					@Override
					public boolean equals(Object obj) {
						if (this == obj)
							return true;
						if (obj == null)
							return false;
						if (getClass() != obj.getClass())
							return false;
						Cls other = (Cls) obj;
						return a == other.a && Objects.equals(b, other.b);
					}
				}
				""";
		Expected e = new Expected(RefactoringCoreMessages.ConvertToRecordRefactoring_name, expected);
		Range selection = CodeActionUtil.getRange(cu, "getA");
		assertCodeActions(cu, selection, e);
	}

	@Test
	public void testNoConvertToRecord1() throws Exception { // https://github.com/eclipse-jdt/eclipse.jdt.ui/issues/2681
		String str = """
				module test {
				}
				""";
		IPackageFragment def = fSourceFolder.createPackageFragment("", false, null);
		def.createCompilationUnit("module-info.java", str, false, null);

		IPackageFragment pack = fSourceFolder.createPackageFragment("test", false, null);
		String str1 = """
					package test;

					public class Cls {
						private final int a;
						private final String b;
						private double c;

						public Cls(int a, String b, double c) {
							this.a= a;
							this.b= b;
							this.c= c;
						}

						public int getA() {
							return a;
						}

						public String getB() {
							return b;
						}
					}
				""";
		ICompilationUnit cu = pack.createCompilationUnit("Cls.java", str1, false, null);

		Range selection = CodeActionUtil.getRange(cu, "Cls");
		assertCodeActionNotExists(cu, selection, RefactoringCoreMessages.ConvertToRecordRefactoring_name);
	}

	@Test
	public void testNoConvertToRecord2() throws Exception { // https://github.com/eclipse-jdt/eclipse.jdt.ui/issues/2681
		String str = """
				module test {
				}
				""";
		IPackageFragment def = fSourceFolder.createPackageFragment("", false, null);
		def.createCompilationUnit("module-info.java", str, false, null);

		IPackageFragment pack = fSourceFolder.createPackageFragment("test", false, null);
		String str1 = """
					package test;

					public class Cls {
						private final int a;
						private final String b;
						private double c = 2.4;;

						public Cls(int a, String b) {
							this.a= a;
							this.b= b;
						}

						public int getA() {
							return a;
						}

						public String getB() {
							return b;
						}
					}
				""";
		ICompilationUnit cu = pack.createCompilationUnit("Cls.java", str1, false, null);

		Range selection = CodeActionUtil.getRange(cu, "Cls");
		assertCodeActionNotExists(cu, selection, RefactoringCoreMessages.ConvertToRecordRefactoring_name);
	}

	@Test
	public void testNoConvertToRecord3() throws Exception { // https://github.com/eclipse-jdt/eclipse.jdt.ui/issues/2681
		String str = """
				module test {
				}
				""";
		IPackageFragment def = fSourceFolder.createPackageFragment("", false, null);
		def.createCompilationUnit("module-info.java", str, false, null);

		IPackageFragment pack = fSourceFolder.createPackageFragment("test", false, null);
		String str1 = """
					package test;

					public class Cls {
						private final static int a;
						private final String b;
						private double c;;

						public Cls(int a, String b, double c) {
							this.a= a;
							this.b= b;
							this.c= c;
						}

						public static int getAValue() {
							return a;
						}

						public String getB() {
							return b;
						}

						public double getC() {
							return c;
						}
					}
				""";
		ICompilationUnit cu = pack.createCompilationUnit("Cls.java", str1, false, null);

		Range selection = CodeActionUtil.getRange(cu, "Cls");
		assertCodeActionNotExists(cu, selection, RefactoringCoreMessages.ConvertToRecordRefactoring_name);
	}

	@Test
	public void testNoConvertToRecord4() throws Exception { // https://github.com/eclipse-jdt/eclipse.jdt.ui/issues/2681
		String str = """
				module test {
				}
				""";
		IPackageFragment def = fSourceFolder.createPackageFragment("", false, null);
		def.createCompilationUnit("module-info.java", str, false, null);

		IPackageFragment pack = fSourceFolder.createPackageFragment("test", false, null);
		String str1 = """
					package test;

					public class Cls {
						private final int a;
						private final String b;
						private double c;;

						public Cls(int a, String b, double c) {
							this.a= a;
							this.b= b;
							this.c= c;
						}

						public int getA() {
							return a;
						}

						public String getB() {
							return b;
						}

						public double getC() {
							return c;
						}

						public int getSum() {
							return a + b.length();
						}
					}
				""";
		ICompilationUnit cu = pack.createCompilationUnit("Cls.java", str1, false, null);

		Range selection = CodeActionUtil.getRange(cu, "Cls");
		assertCodeActionNotExists(cu, selection, RefactoringCoreMessages.ConvertToRecordRefactoring_name);
	}

	@Test
	public void testNoConvertToRecord5() throws Exception { // https://github.com/eclipse-jdt/eclipse.jdt.ui/issues/2681
		String str = """
				module test {
				}
				""";
		IPackageFragment def = fSourceFolder.createPackageFragment("", false, null);
		def.createCompilationUnit("module-info.java", str, false, null);

		IPackageFragment pack = fSourceFolder.createPackageFragment("test", false, null);
		String str1 = """
					package test;

					public class Cls {
						private final int a;
						private final String b;
						public double c;;

						public Cls(int a, String b, double c) {
							this.a= a;
							this.b= b;
							this.c= c;
						}

						public int getA() {
							return a;
						}

						public String getB() {
							return b;
						}

						public double getC() {
							return c;
						}
					}
				""";
		ICompilationUnit cu = pack.createCompilationUnit("Cls.java", str1, false, null);

		Range selection = CodeActionUtil.getRange(cu, "Cls");
		assertCodeActionNotExists(cu, selection, RefactoringCoreMessages.ConvertToRecordRefactoring_name);
	}

	@Test
	public void testNoConvertToRecord6() throws Exception { // https://github.com/eclipse-jdt/eclipse.jdt.ui/issues/2681
		String str = """
				module test {
				}
				""";
		IPackageFragment def = fSourceFolder.createPackageFragment("", false, null);
		def.createCompilationUnit("module-info.java", str, false, null);

		IPackageFragment pack = fSourceFolder.createPackageFragment("test", false, null);
		String str1 = """
					package test;

					class K {
					}
					public class Cls extends K {
						private final int a;
						private final String b;
						private double c;;

						public Cls(int a, String b, double c) {
							this.a= a;
							this.b= b;
							this.c= c;
						}

						public int getA() {
							return a;
						}

						public String getB() {
							return b;
						}

						public double getC() {
							return c;
						}
					}
				""";
		ICompilationUnit cu = pack.createCompilationUnit("Cls.java", str1, false, null);

		Range selection = CodeActionUtil.getRange(cu, "Cls");
		assertCodeActionNotExists(cu, selection, RefactoringCoreMessages.ConvertToRecordRefactoring_name);
	}

	@Test
	public void testNoConvertToRecord7() throws Exception {
		String str = """
				module test {
				}
				""";
		IPackageFragment def = fSourceFolder.createPackageFragment("", false, null);
		def.createCompilationUnit("module-info.java", str, false, null);

		IPackageFragment pack = fSourceFolder.createPackageFragment("test", false, null);
		String str1 = """
				package test;

				public class Cls {
					/**
					 * Class Inner
					 */
					private class Inner {
						private final int a;
						private final String b;
						private double c;

						public Inner(int a, String b, double c) {
							this.a= a;
							this.b= b;
							this.c= c;
						}

						public int getA() {
							return a;
						}

						public String getB() {
							return b;
						}

						public double getC() {
							return c;
						}
					}
					private class Inner2 extends Inner {
						public Inner2() {
							super(2, "blah", 5.2);
						}
					}
					public void foo() {
						Inner inner= new Inner(1, "comment", 4.3);
						System.out.println(inner.getA());
						System.out.println(inner.getB());
						System.out.println(inner.getC());
					}
				}
				""";
		ICompilationUnit cu = pack.createCompilationUnit("Cls.java", str1, false, null);

		Range selection = CodeActionUtil.getRange(cu, "Cls");
		assertCodeActionNotExists(cu, selection, RefactoringCoreMessages.ConvertToRecordRefactoring_name);
	}

}
