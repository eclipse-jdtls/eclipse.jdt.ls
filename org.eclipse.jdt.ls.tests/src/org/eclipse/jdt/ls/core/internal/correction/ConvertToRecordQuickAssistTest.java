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
		Map<String, String> options17 = new HashMap<>(fJProject.getOptions(false));
		JavaModelUtil.setComplianceOptions(options17, JavaCore.VERSION_17);
		fJProject.setOptions(options17);
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
	public void testConvertToRecord7() throws Exception {
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

					public Cls(int a) {
						this(a, "abc");
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
					public Cls(int a) {
						this(a, "abc");
					}
				}
				""";
		Expected e = new Expected(RefactoringCoreMessages.ConvertToRecordRefactoring_name, expected);
		Range selection = CodeActionUtil.getRange(cu, "getA");
		assertCodeActions(cu, selection, e);
	}

	@Test
	public void testConvertToRecord8() throws Exception {
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
					public static int c;

					static {
						c = 3;
					}

					public static int getC() {
						return c;
					}

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
					static {
						c = 3;
					}
					public static int c;

					public static int getC() {
						return c;
					}
				}
				""";
		Expected e = new Expected(RefactoringCoreMessages.ConvertToRecordRefactoring_name, expected);
		Range selection = CodeActionUtil.getRange(cu, "getA");
		assertCodeActions(cu, selection, e);
	}

	@Test
	public void testConvertToRecord9() throws Exception {
		String str = """
				module test {
				}
				""";
		IPackageFragment def = fSourceFolder.createPackageFragment("", false, null);
		def.createCompilationUnit("module-info.java", str, false, null);

		IPackageFragment pack = fSourceFolder.createPackageFragment("test", false, null);
		String str1 = """
				package test;

				interface Blah {
					void printSomething();
				}

				public class Cls implements Blah {
					private final int a;
					private final String b;
					public static int c;

					static {
						c = 3;
					}

					public static int getC() {
						return c;
					}

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
					public void printSomething() {
						System.out.println("here");
					}
				}
				""";
		ICompilationUnit cu = pack.createCompilationUnit("Cls.java", str1, false, null);

		String expected = """
				package test;

				interface Blah {
					void printSomething();
				}

				public record Cls(int a, String b) implements Blah {
					static {
						c = 3;
					}
					public static int c;

					public static int getC() {
						return c;
					}

					@Override
					public void printSomething() {
						System.out.println("here");
					}
				}
				""";
		Expected e = new Expected(RefactoringCoreMessages.ConvertToRecordRefactoring_name, expected);
		Range selection = CodeActionUtil.getRange(cu, "getA");
		assertCodeActions(cu, selection, e);
	}

	@Test
	public void testConvertToRecord10() throws Exception {
		String str = """
				module test {
				}
				""";
		IPackageFragment def = fSourceFolder.createPackageFragment("", false, null);
		def.createCompilationUnit("module-info.java", str, false, null);

		IPackageFragment pack = fSourceFolder.createPackageFragment("test", false, null);
		String str1 = """
				package test;

				public class Pair<T, U> {
					private final T first;
					private final U second;

					public Pair(T first, U second) {
						this.first = first;
						this.second = second;
					}

					public T getFirst() {
						return first;
					}

					public U getSecond() {
						return second;
					}
				}
				""";
		ICompilationUnit cu = pack.createCompilationUnit("Pair.java", str1, false, null);

		String expected = """
				package test;

				public record Pair<T, U>(T first, U second) {
				}
				""";
		Expected e = new Expected(RefactoringCoreMessages.ConvertToRecordRefactoring_name, expected);
		Range selection = CodeActionUtil.getRange(cu, "getFirst");
		assertCodeActions(cu, selection, e);
	}

	@Test
	public void testConvertToRecord11() throws Exception {
		String str = """
				module test {
				}
				""";
		IPackageFragment def = fSourceFolder.createPackageFragment("", false, null);
		def.createCompilationUnit("module-info.java", str, false, null);

		IPackageFragment pack = fSourceFolder.createPackageFragment("test", false, null);
		String str1 = """
				package test;

				import java.lang.annotation.Retention;
				import java.lang.annotation.RetentionPolicy;

				@Deprecated
				public class User {
					@NotNull
					private final String name;

					@Range(min = 0, max = 150)
					private final int age;

					public User(@NotNull String name, int age) {
						this.name = name;
						this.age = age;
					}

					@NotNull
					public String getName() {
						return name;
					}

					public int getAge() {
						return age;
					}
				}

				@Retention(RetentionPolicy.RUNTIME)
				@interface NotNull {}

				@Retention(RetentionPolicy.RUNTIME)
				@interface Range {
					int min();
					int max();
				}
				""";
		ICompilationUnit cu = pack.createCompilationUnit("User.java", str1, false, null);

		String expected = """
				package test;

				import java.lang.annotation.Retention;
				import java.lang.annotation.RetentionPolicy;

				@Deprecated
				public record User(@NotNull String name, @Range(min = 0, max = 150) int age) {
				}

				@Retention(RetentionPolicy.RUNTIME)
				@interface NotNull {}

				@Retention(RetentionPolicy.RUNTIME)
				@interface Range {
					int min();
					int max();
				}
				""";
		Expected e = new Expected(RefactoringCoreMessages.ConvertToRecordRefactoring_name, expected);
		Range selection = CodeActionUtil.getRange(cu, "getName");
		assertCodeActions(cu, selection, e);
	}

	@Test
	public void testConvertToRecord12() throws Exception {
		String str = """
				module test {
				}
				""";
		IPackageFragment def = fSourceFolder.createPackageFragment("", false, null);
		def.createCompilationUnit("module-info.java", str, false, null);

		IPackageFragment pack = fSourceFolder.createPackageFragment("test", false, null);
		String str1 = """
				package test;

				public class Identifier {
					private final String id;

					public Identifier(String id) {
						this.id = id;
					}

					public String getId() {
						return id;
					}
				}
				""";
		ICompilationUnit cu = pack.createCompilationUnit("Identifier.java", str1, false, null);

		String expected = """
				package test;

				public record Identifier(String id) {
				}
				""";
		Expected e = new Expected(RefactoringCoreMessages.ConvertToRecordRefactoring_name, expected);
		Range selection = CodeActionUtil.getRange(cu, "getId");
		assertCodeActions(cu, selection, e);
	}

	@Test
	public void testConvertToRecord13() throws Exception {
		String str = """
				module test {
				}
				""";
		IPackageFragment def = fSourceFolder.createPackageFragment("", false, null);
		def.createCompilationUnit("module-info.java", str, false, null);

		IPackageFragment pack = fSourceFolder.createPackageFragment("test", false, null);
		String str1 = """
				package test;

				class PackagePrivateCls {
					private final int value;

					PackagePrivateCls(int value) {
						this.value = value;
					}

					public int getValue() {
						return value;
					}
				}
				""";
		ICompilationUnit cu = pack.createCompilationUnit("PackagePrivateCls.java", str1, false, null);

		String expected = """
				package test;

				record PackagePrivateCls(int value) {
				}
				""";
		Expected e = new Expected(RefactoringCoreMessages.ConvertToRecordRefactoring_name, expected);
		Range selection = CodeActionUtil.getRange(cu, "getValue");
		assertCodeActions(cu, selection, e);
	}

	@Test
	public void testConvertToRecord14() throws Exception {
		String str = """
				module test {
				}
				""";
		IPackageFragment def = fSourceFolder.createPackageFragment("", false, null);
		def.createCompilationUnit("module-info.java", str, false, null);

		IPackageFragment pack = fSourceFolder.createPackageFragment("test", false, null);
		String str1 = """
				package test;

				public final class A {

					private final int a;
					private final String b;
					private int c;

					public A(int a, String b, int c) {
						class K {
							public static int doublex(int x) {
								return x * 2;
							}
						}
						this.a= K.doublex(a);
						if (a < 0) {
							this.b = massage(b);
						} else {
							this.b = b;
						}
						this.c= c;
					}

					private String massage(String s) {
						return s.toLowerCase();
					}

					public int getA() {
						return a;
					}

					public String getB() {
						return b;
					}

					public int getC() {
						return c;
					}
				}
				""";
		ICompilationUnit cu = pack.createCompilationUnit("A.java", str1, false, null);

		String expected = """
				package test;

				public record A(int a, String b, int c) {
					public A(int a, String b, int c) {
						class K {
							public static int doublex(int x) {
								return x * 2;
							}
						}
						this.a= K.doublex(a);
						if (a < 0) {
							this.b = massage(b);
						} else {
							this.b = b;
						}
						this.c= c;
					}

					private String massage(String s) {
						return s.toLowerCase();
					}
				}
				""";
		Expected e = new Expected(RefactoringCoreMessages.ConvertToRecordRefactoring_name, expected);
		Range selection = CodeActionUtil.getRange(cu, "massage");
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
	public void testNoConvertToRecord3() throws Exception {
		String str = """
				module test {
				}
				""";
		IPackageFragment def = fSourceFolder.createPackageFragment("", false, null);
		def.createCompilationUnit("module-info.java", str, false, null);

		IPackageFragment pack = fSourceFolder.createPackageFragment("test", false, null);
		String str1 = """
					package test;

					public abstract class Cls {
						private final static int a;
						private final String b;
						private double c;;

						public Cls(int a, String b, double c) {
							this.a= a;
							this.b= b;
							this.c= c;
						}

						public int getAValue() {
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

						private int getSum() {
							c = 4.0;
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
	public void testNoConvertToRecord7() throws Exception { // class extended
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

		Range selection = CodeActionUtil.getRange(cu, "Inner");
		assertCodeActionNotExists(cu, selection, RefactoringCoreMessages.ConvertToRecordRefactoring_name);
	}

	@Test
	public void testNoConvertToRecord8() throws Exception { // not all fields initialized
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

		Range selection = CodeActionUtil.getRange(cu, " b");
		assertCodeActionNotExists(cu, selection, RefactoringCoreMessages.ConvertToRecordRefactoring_name);
	}

	@Test
	public void testNoConvertToRecord9() throws Exception { // second constructor, non-chaining
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

						public Inner(int a, String b) {
							this.a= a;
							this.b= b;
							this.c= 2.0;
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

		Range selection = CodeActionUtil.getRange(cu, "Inner");
		assertCodeActionNotExists(cu, selection, RefactoringCoreMessages.ConvertToRecordRefactoring_name);
	}

	@Test
	public void testNoConvertToRecord10() throws Exception { // wrong type returned from getter
		String str = """
				module test {
				}
				""";
		IPackageFragment def = fSourceFolder.createPackageFragment("", false, null);
		def.createCompilationUnit("module-info.java", str, false, null);

		IPackageFragment pack = fSourceFolder.createPackageFragment("test", false, null);
		String str1 = """
				package test;

				public class WrongTypeCls {
					private final int a;

					public WrongTypeCls(int a) {
						this.a = a;
					}

					public long getA() {
						return (long) a;
					}
				}
				""";
		ICompilationUnit cu = pack.createCompilationUnit("WrongTypeCls.java", str1, false, null);

		Range selection = CodeActionUtil.getRange(cu, "WrongTypeCls");
		assertCodeActionNotExists(cu, selection, RefactoringCoreMessages.ConvertToRecordRefactoring_name);
	}

	@Test
	public void testNoConvertToRecord11() throws Exception { // instance initializer
		String str = """
				module test {
				}
				""";
		IPackageFragment def = fSourceFolder.createPackageFragment("", false, null);
		def.createCompilationUnit("module-info.java", str, false, null);

		IPackageFragment pack = fSourceFolder.createPackageFragment("test", false, null);
		String str1 = """
				package test;

				public class InitializerCls {
					private final int value;
					private final String name;

					{
						System.out.println("Instance initializer");
					}

					public InitializerCls(int value, String name) {
						this.value = value;
						this.name = name;
					}

					public int getValue() {
						return value;
					}

					public String getName() {
						return name;
					}
				}
				""";
		ICompilationUnit cu = pack.createCompilationUnit("InitializerCls.java", str1, false, null);

		Range selection = CodeActionUtil.getRange(cu, "InitializerCls");
		assertCodeActionNotExists(cu, selection, RefactoringCoreMessages.ConvertToRecordRefactoring_name);
	}

	@Test
	public void testNoConvertToRecord12() throws Exception { // complex constructor
		String str = """
				module test {
				}
				""";
		IPackageFragment def = fSourceFolder.createPackageFragment("", false, null);
		def.createCompilationUnit("module-info.java", str, false, null);

		IPackageFragment pack = fSourceFolder.createPackageFragment("test", false, null);
		String str1 = """
				package test;

				public class CalculatedCls {
					private final int value;
					private final int doubled;

					public CalculatedCls(int value) {
						this.value = value;
						this.doubled = value * 2;
					}

					public int getValue() {
						return value;
					}

					public int getDoubled() {
						return doubled;
					}
				}
				""";
		ICompilationUnit cu = pack.createCompilationUnit("CalculatedCls.java", str1, false, null);

		Range selection = CodeActionUtil.getRange(cu, "CalculatedCls");
		assertCodeActionNotExists(cu, selection, RefactoringCoreMessages.ConvertToRecordRefactoring_name);
	}

	@Test
	public void testNoConvertToRecord13() throws Exception { // native method
		String str = """
				module test {
				}
				""";
		IPackageFragment def = fSourceFolder.createPackageFragment("", false, null);
		def.createCompilationUnit("module-info.java", str, false, null);

		IPackageFragment pack = fSourceFolder.createPackageFragment("test", false, null);
		String str1 = """
				package test;

				public class NativeCls {
					private final int value;

					public NativeCls(int value) {
						this.value = value;
					}

					public int getValue() {
						return value;
					}

					public native void nativeMethod();
				}
				""";
		ICompilationUnit cu = pack.createCompilationUnit("NativeCls.java", str1, false, null);

		Range selection = CodeActionUtil.getRange(cu, "NativeCls");
		assertCodeActionNotExists(cu, selection, RefactoringCoreMessages.ConvertToRecordRefactoring_name);
	}


	@Test
	public void testNoConvertToRecord14() throws Exception { // protected method finalize
		String str = """
				module test {
				}
				""";
		IPackageFragment def = fSourceFolder.createPackageFragment("", false, null);
		def.createCompilationUnit("module-info.java", str, false, null);

		IPackageFragment pack = fSourceFolder.createPackageFragment("test", false, null);
		String str1 = """
				package test;

				public class NoFieldsCls {

					public NoFieldsCls() {
					}

					public class Inner {
						private int a;
						private String b;

						public Inner(int a, String b) {
							this.a = a;
							this.b = b;
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
		ICompilationUnit cu = pack.createCompilationUnit("NoFieldsCls.java", str1, false, null);

		Range selection = CodeActionUtil.getRange(cu, "NoFieldsCls");
		assertCodeActionNotExists(cu, selection, RefactoringCoreMessages.ConvertToRecordRefactoring_name);
	}

	@Test
	public void testNoConvertToRecord15() throws Exception { // all fields not initialized
		String str = """
				module test {
				}
				""";
		IPackageFragment def = fSourceFolder.createPackageFragment("", false, null);
		def.createCompilationUnit("module-info.java", str, false, null);

		IPackageFragment pack = fSourceFolder.createPackageFragment("test", false, null);
		String str1 = """
				package test;

				public class PartialInitCls {
					private final int a;
					private final int b = 10;

					public PartialInitCls(int a) {
						this.a = a;
					}

					public int getA() {
						return a;
					}

					public int getB() {
						return b;
					}
				}
				""";
		ICompilationUnit cu = pack.createCompilationUnit("PartialInitCls.java", str1, false, null);

		Range selection = CodeActionUtil.getRange(cu, "PartialInitCls");
		assertCodeActionNotExists(cu, selection, RefactoringCoreMessages.ConvertToRecordRefactoring_name);
	}

	@Test
	public void testNoConvertToRecord16() throws Exception { // sealed class
		String str = """
				module test {
				}
				""";
		IPackageFragment def = fSourceFolder.createPackageFragment("", false, null);
		def.createCompilationUnit("module-info.java", str, false, null);

		IPackageFragment pack = fSourceFolder.createPackageFragment("test", false, null);
		String str1 = """
				package test;

				public sealed class SealedCls permits SubCls {
					private final int a;
					private final int b = 10;

					public SealedCls(int a) {
						this.a = a;
					}

					public int getA() {
						return a;
					}

					public int getB() {
						return b;
					}
				}

				final class SubCls extends SealedCls {
					public SubCls() {
					}
				}
				""";
		ICompilationUnit cu = pack.createCompilationUnit("SubCls.java", str1, false, null);

		Range selection = CodeActionUtil.getRange(cu, "SealedCls");
		assertCodeActionNotExists(cu, selection, RefactoringCoreMessages.ConvertToRecordRefactoring_name);
	}

	@Test
	public void testNoConvertToRecord17() throws Exception { // no fields
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
					public Cls() {}

					public void printSomething() {
						System.out.println("something");
					}
				}
				""";
		ICompilationUnit cu = pack.createCompilationUnit("Cls.java", str1, false, null);

		Range selection = CodeActionUtil.getRange(cu, "Cls");
		assertCodeActionNotExists(cu, selection, RefactoringCoreMessages.ConvertToRecordRefactoring_name);
	}

	@Test
	public void testNoConvertToRecord18() throws Exception { // member class
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
					private int a;

					public Cls(int a) {
						this.a = a;
					}

					public int getA() {
						return a;
					}

					public class K {}
				}
				""";
		ICompilationUnit cu = pack.createCompilationUnit("Cls.java", str1, false, null);

		Range selection = CodeActionUtil.getRange(cu, "Cls");
		assertCodeActionNotExists(cu, selection, RefactoringCoreMessages.ConvertToRecordRefactoring_name);
	}

}
