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
import org.eclipse.jdt.internal.corext.util.JavaModelUtil;
import org.eclipse.jdt.ls.core.internal.JavaProjectHelper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * @author jjohnstn
 *
 */
public class NullAnnotationsQuickFix1d8Test extends AbstractQuickFixTest {

	private IJavaProject fJProject1;
	private IPackageFragmentRoot fSourceFolder;
	private String ANNOTATION_JAR_PATH;

	@BeforeEach
	public void setUp() throws Exception {
		fJProject1 = newEmptyProject();
		Map<String, String> options1d8 = new HashMap<>(fJProject1.getOptions(false));
		JavaModelUtil.setComplianceOptions(options1d8, JavaCore.VERSION_1_8);
		fJProject1.setOptions(options1d8);

		Hashtable<String, String> options = TestOptions.getDefaultOptions();
		options.put(DefaultCodeFormatterConstants.FORMATTER_TAB_CHAR, JavaCore.SPACE);
		options.put(DefaultCodeFormatterConstants.FORMATTER_TAB_SIZE, "4");
		options.put(DefaultCodeFormatterConstants.FORMATTER_NUMBER_OF_EMPTY_LINES_TO_PRESERVE, String.valueOf(99));
		options.put(JavaCore.COMPILER_PB_STATIC_ACCESS_RECEIVER, JavaCore.ERROR);
		options.put(JavaCore.COMPILER_PB_UNCHECKED_TYPE_OPERATION, JavaCore.IGNORE);
		options.put(JavaCore.COMPILER_PB_MISSING_HASHCODE_METHOD, JavaCore.WARNING);
		options.put(JavaCore.COMPILER_ANNOTATION_NULL_ANALYSIS, JavaCore.ENABLED);
		options.put(JavaCore.COMPILER_PB_NULL_SPECIFICATION_VIOLATION, JavaCore.ERROR);
		options.put(JavaCore.COMPILER_PB_NULL_REFERENCE, JavaCore.ERROR);
		options.put(JavaCore.COMPILER_PB_POTENTIAL_NULL_REFERENCE, JavaCore.WARNING);
		options.put(JavaCore.COMPILER_PB_NULL_ANNOTATION_INFERENCE_CONFLICT, JavaCore.WARNING);
		options.put(JavaCore.COMPILER_PB_NULL_UNCHECKED_CONVERSION, JavaCore.WARNING);
		options.put(JavaCore.COMPILER_PB_REDUNDANT_NULL_CHECK, JavaCore.WARNING);
		options.put(JavaCore.COMPILER_PB_NULL_UNCHECKED_CONVERSION, JavaCore.WARNING);

		JavaCore.setOptions(options);

		StubUtility.setCodeTemplate(CodeTemplateContextType.CATCHBLOCK_ID, "", null);
		StubUtility.setCodeTemplate(CodeTemplateContextType.CONSTRUCTORSTUB_ID, "", null);
		StubUtility.setCodeTemplate(CodeTemplateContextType.METHODSTUB_ID, "", null);

		if (ANNOTATION_JAR_PATH == null) {
			// these tests us the "old" null annotations
			ANNOTATION_JAR_PATH = getAnnotationLibPath();
		}
		JavaProjectHelper.addLibrary(fJProject1, new Path(ANNOTATION_JAR_PATH));

		fSourceFolder = fJProject1.getPackageFragmentRoot(fJProject1.getProject().getFolder("src"));
	}

	public static String getAnnotationLibPath() throws IOException {
		URL libEntry = Platform.getBundle("org.eclipse.jdt.ls.tests").getEntry("/testresources/org.eclipse.jdt.annotation_2.4.100.v20251017-1955.jar");
		return FileLocator.toFileURL(libEntry).getPath();
	}

	/*
	 * Problem: package default nonnull conflicts with inherited nullable (via generic substitution) (implicit vs. implicit)
	 * Location: method return
	 * Fixes:
	 * - change local to nullable (equal)
	 * - change local to nonnull  (covariant return)
	 * - change super to nonnull  (equal)
	 */
	@Test
	public void testBug499716_a() throws Exception {
		fJProject1.setOption(JavaCore.COMPILER_INHERIT_NULL_ANNOTATIONS, JavaCore.ENABLED);
		IPackageFragment pack1 = fSourceFolder.createPackageFragment("test1", false, null);
		String str = """
				@org.eclipse.jdt.annotation.NonNullByDefault
				package test1;
				""";
		pack1.createCompilationUnit("package-info.java", str, false, null);

		String str0 = """
			package test1;
			import org.eclipse.jdt.annotation.*;

			interface Type<@Nullable K> {
				@NonNullByDefault(DefaultLocation.RETURN_TYPE)
				K get();

				class U implements Type<@Nullable String> {
					@Override
					public String get() { // <-- error "The default '@NonNull' conflicts..."
						return "";
					}
				}
			}
			""";
		ICompilationUnit cu = pack1.createCompilationUnit("Test.java", str0, false, null);

		String str1 = """
				package test1;
				import org.eclipse.jdt.annotation.*;

				interface Type<@Nullable K> {
					@NonNullByDefault(DefaultLocation.RETURN_TYPE)
					K get();

					class U implements Type<@Nullable String> {
						@Override
						public @Nullable String get() { // <-- error "The default '@NonNull' conflicts..."
							return "";
						}
					}
				}
				""";

		String str2 = """
				package test1;
				import org.eclipse.jdt.annotation.*;

				interface Type<@Nullable K> {
					@NonNullByDefault(DefaultLocation.RETURN_TYPE)
					K get();

					class U implements Type<@Nullable String> {
						@Override
						public @NonNull String get() { // <-- error "The default '@NonNull' conflicts..."
							return "";
						}
					}
				}
				""";

		String str3 = """
				package test1;
				import org.eclipse.jdt.annotation.*;

				interface Type<@Nullable K> {
					@NonNullByDefault(DefaultLocation.RETURN_TYPE)
				    @NonNull
					K get();

					class U implements Type<@Nullable String> {
						@Override
						public String get() { // <-- error "The default '@NonNull' conflicts..."
							return "";
						}
					}
				}
				""";
		Expected e1 = new Expected("Change return type of 'get(..)' to '@Nullable'", str1);
		Expected e2 = new Expected("Change return type of 'get(..)' to '@NonNull'", str2);
		Expected e3 = new Expected("Change return type of overridden 'get(..)' to '@NonNull'", str3);
		assertCodeActions(cu, e1, e2, e3);
	}

	/*
	 * Problem: package default nonnull conflicts with inherited nullable (via generic substitution) (implicit vs. implicit)
	 * Location: parameter
	 * Fixes:
	 * - change local to nullable
	 * - change super to nonnull
	 * No covariant parameter!
	 */
	@Test
	public void testBug499716_b() throws Exception {
		fJProject1.setOption(JavaCore.COMPILER_INHERIT_NULL_ANNOTATIONS, JavaCore.ENABLED);
		IPackageFragment pack1 = fSourceFolder.createPackageFragment("test1", false, null);
		String str = """
				@org.eclipse.jdt.annotation.NonNullByDefault
				package test1;
				""";
		pack1.createCompilationUnit("package-info.java", str, false, null);

		String str1 = """
				package test1;
				import org.eclipse.jdt.annotation.*;

				interface Type<@Nullable K> {
					void set(int i, K arg);

					class U implements Type<@Nullable String> {
						@Override
						public void set(int i, String arg) {
						}
					}
				}
				""";
		ICompilationUnit cu = pack1.createCompilationUnit("Test.java", str1, false, null);

		String str2 = """
				package test1;
				import org.eclipse.jdt.annotation.*;

				interface Type<@Nullable K> {
					void set(int i, K arg);

					class U implements Type<@Nullable String> {
						@Override
						public void set(int i, @Nullable String arg) {
						}
					}
				}
				""";

		String str3 = """
				package test1;
				import org.eclipse.jdt.annotation.*;

				interface Type<@Nullable K> {
					void set(int i, @NonNull K arg);

					class U implements Type<@Nullable String> {
						@Override
						public void set(int i, String arg) {
						}
					}
				}
				""";
		Expected e1 = new Expected("Change parameter 'arg' to '@Nullable'", str2);
		Expected e2 = new Expected("Change parameter in overridden 'set(..)' to '@NonNull'", str3);
		assertCodeActions(cu, e1, e2);
	}

	/*
	 * Problem: explicit nullable conflicts with inherited nonnull (from type default) (illegal override)
	 * Location: return type
	 * Fixes:
	 * - change local to nonnull
	 * - change super to nullable
	 * No contravariant return!
	 */
	@Test
	public void testBug499716_c() throws Exception {
		fJProject1.setOption(JavaCore.COMPILER_INHERIT_NULL_ANNOTATIONS, JavaCore.ENABLED);
		IPackageFragment pack1 = fSourceFolder.createPackageFragment("test1", false, null);

		String str = """
				package test1;
				import org.eclipse.jdt.annotation.*;

				@NonNullByDefault(DefaultLocation.RETURN_TYPE)
				interface Type {
					String get();

					class U implements Type {
						@Override
						public @Nullable String get() {
							return "";
						}
					}
				}
				""";
		ICompilationUnit cu = pack1.createCompilationUnit("Test.java", str, false, null);

		String str1 = """
				package test1;
				import org.eclipse.jdt.annotation.*;

				@NonNullByDefault(DefaultLocation.RETURN_TYPE)
				interface Type {
					String get();

					class U implements Type {
						@Override
						public String get() {
							return "";
						}
					}
				}
				""";

		String str2 = """
				package test1;
				import org.eclipse.jdt.annotation.*;

				@NonNullByDefault(DefaultLocation.RETURN_TYPE)
				interface Type {
					@Nullable
				    String get();

					class U implements Type {
						@Override
						public @Nullable String get() {
							return "";
						}
					}
				}
				""";
		Expected e1 = new Expected("Change return type of 'get(..)' to '@NonNull'", str1);
		Expected e2 = new Expected("Change return type of overridden 'get(..)' to '@Nullable'", str2);
		assertCodeActions(cu, e1, e2);
	}

	/*
	 * Problem: package default nonnull conflicts with declared nullable in super (illegal override)
	 * Location: method parameter
	 * Fixes:
	 * - change local to nullable
	 * - change super to implicit nonnull (by removing to make default apply)
	 * No covariant parameter!
	 */
	@Test
	public void testBug499716_d() throws Exception {
		IPackageFragment pack1 = fSourceFolder.createPackageFragment("test1", false, null);
		String str = """
				@org.eclipse.jdt.annotation.NonNullByDefault
				package test1;
				""";
		pack1.createCompilationUnit("package-info.java", str, true, null);

		String str1 = """
				package test1;
				import org.eclipse.jdt.annotation.*;

				interface Type {
					void set(@Nullable String s);

					class U implements Type {
						@Override
						public void set(String t) {
						}
					}
				}
				""";
		ICompilationUnit cu = pack1.createCompilationUnit("Test.java", str1, false, null);

		String str2 = """
				package test1;
				import org.eclipse.jdt.annotation.*;

				interface Type {
					void set(@Nullable String s);

					class U implements Type {
						@Override
						public void set(@Nullable String t) {
						}
					}
				}
				""";

		String str3 = """
				package test1;
				import org.eclipse.jdt.annotation.*;

				interface Type {
					void set(String s);

					class U implements Type {
						@Override
						public void set(String t) {
						}
					}
				}
				""";
		Expected e1 = new Expected("Change parameter 't' to '@Nullable'", str2);
		Expected e2 = new Expected("Change parameter in overridden 'set(..)' to '@NonNull'", str3);
		assertCodeActions(cu, e1, e2);
	}

	/*
	 * Test that no redundant null annotations are created.
	 * Variation 1: @NonNullByDefault applies everywhere, type is non-null
	 */
	@Test
	public void test443146a() throws Exception {
		IPackageFragment pack1 = fSourceFolder.createPackageFragment("test1", false, null);
		String str = """
				@org.eclipse.jdt.annotation.NonNullByDefault
				package test1;
				""";
		pack1.createCompilationUnit("package-info.java", str, true, null);

		String str1 = """
				package test1;
				import java.util.Map;
				import org.eclipse.jdt.annotation.*;

				abstract class Test {
					abstract Map<? extends Map<String, @Nullable Integer>, String[][]> f();

					public void g() {
						x=f();
					}
				}
				""";
		ICompilationUnit cu = pack1.createCompilationUnit("Test.java", str1, false, null);

		String str2 = """
				package test1;
				import java.util.Map;
				import org.eclipse.jdt.annotation.*;

				abstract class Test {
					private Map<? extends Map<String, @Nullable Integer>, String[][]> x;

				    abstract Map<? extends Map<String, @Nullable Integer>, String[][]> f();

					public void g() {
						x=f();
					}
				}
				""";

		String str3 = """
				package test1;
				import java.util.Map;
				import org.eclipse.jdt.annotation.*;

				abstract class Test {
					abstract Map<? extends Map<String, @Nullable Integer>, String[][]> f();

					public void g(Map<? extends Map<String, @Nullable Integer>, String[][]> x) {
						x=f();
					}
				}
				""";

		String str4 = """
				package test1;
				import java.util.Map;
				import org.eclipse.jdt.annotation.*;

				abstract class Test {
					abstract Map<? extends Map<String, @Nullable Integer>, String[][]> f();

					public void g() {
						Map<? extends Map<String, @Nullable Integer>, String[][]> x = f();
					}
				}
				""";
		Expected e1 = new Expected("Create field 'x'", str2);
		Expected e2 = new Expected("Create parameter 'x'", str3);
		Expected e3 = new Expected("Create local variable 'x'", str4);
		assertCodeActions(cu, e1, e2, e3);
	}

	/*
	 * Test that no redundant null annotations are created.
	 * Variation 2: @NonNullByDefault applies everywhere
	 * Note, that there is no @Nullable generated for the 'local variable' case.
	 */
	@Test
	public void test443146b() throws Exception {
		IPackageFragment pack1 = fSourceFolder.createPackageFragment("test1", false, null);
		String str = """
				@org.eclipse.jdt.annotation.NonNullByDefault
				package test1;
				""";
		pack1.createCompilationUnit("package-info.java", str, true, null);

		String str1 = """
				package test1;
				import java.util.Map;
				import org.eclipse.jdt.annotation.*;

				abstract class Test {
					abstract @Nullable Map<? extends Map<String, @Nullable Integer>, String[][]> f();

					public void g() {
						x=f();
					}
				}
				""";
		ICompilationUnit cu = pack1.createCompilationUnit("Test.java", str1, false, null);

		String str2 = """
				package test1;
				import java.util.Map;
				import org.eclipse.jdt.annotation.*;

				abstract class Test {
					private @Nullable Map<? extends Map<String, @Nullable Integer>, String[][]> x;

				    abstract @Nullable Map<? extends Map<String, @Nullable Integer>, String[][]> f();

					public void g() {
						x=f();
					}
				}
				""";

		String str3 = """
				package test1;
				import java.util.Map;
				import org.eclipse.jdt.annotation.*;

				abstract class Test {
					abstract @Nullable Map<? extends Map<String, @Nullable Integer>, String[][]> f();

					public void g(@Nullable Map<? extends Map<String, @Nullable Integer>, String[][]> x) {
						x=f();
					}
				}
				""";

		String str4 = """
				package test1;
				import java.util.Map;
				import org.eclipse.jdt.annotation.*;

				abstract class Test {
					abstract @Nullable Map<? extends Map<String, @Nullable Integer>, String[][]> f();

					public void g() {
						Map<? extends Map<String, @Nullable Integer>, String[][]> x = f();
					}
				}
				""";
		Expected e1 = new Expected("Create field 'x'", str2);
		Expected e2 = new Expected("Create parameter 'x'", str3);
		Expected e3 = new Expected("Create local variable 'x'", str4);
		assertCodeActions(cu, e1, e2, e3);
	}

	/*
	 * Test that no redundant null annotations are created.
	 * Variation 3: @NonNullByDefault doesn't apply at the target locations (so annotations ARE expected, but not for the local variable)
	 */
	@Test
	public void test443146c() throws Exception {
		IPackageFragment pack1 = fSourceFolder.createPackageFragment("test1", false, null);
		String str = """
				@org.eclipse.jdt.annotation.NonNullByDefault
				package test1;
				""";
		pack1.createCompilationUnit("package-info.java", str, true, null);

		String str1 = """
				package test1;
				import java.util.Map;
				import org.eclipse.jdt.annotation.*;

				@NonNullByDefault({})
				abstract class Test {
					@NonNullByDefault
					abstract Map<? extends Map<String, @Nullable Integer>, String[][]> f();

					public void g() {
						x=f();
					}
				}
				""";
		ICompilationUnit cu = pack1.createCompilationUnit("Test.java", str1, false, null);

		String str2 = """
				package test1;
				import java.util.Map;
				import org.eclipse.jdt.annotation.*;

				@NonNullByDefault({})
				abstract class Test {
					private @NonNull Map<? extends @NonNull Map<@NonNull String, @Nullable Integer>, String @NonNull [][]> x;

				    @NonNullByDefault
					abstract Map<? extends Map<String, @Nullable Integer>, String[][]> f();

					public void g() {
						x=f();
					}
				}
				""";

		String str3 = """
				package test1;
				import java.util.Map;
				import org.eclipse.jdt.annotation.*;

				@NonNullByDefault({})
				abstract class Test {
					@NonNullByDefault
					abstract Map<? extends Map<String, @Nullable Integer>, String[][]> f();

					public void g(@NonNull Map<? extends @NonNull Map<@NonNull String, @Nullable Integer>, String @NonNull [][]> x) {
						x=f();
					}
				}
				""";

		String str4 = """
				package test1;
				import java.util.Map;
				import org.eclipse.jdt.annotation.*;

				@NonNullByDefault({})
				abstract class Test {
					@NonNullByDefault
					abstract Map<? extends Map<String, @Nullable Integer>, String[][]> f();

					public void g() {
						Map<? extends @NonNull Map<@NonNull String, @Nullable Integer>, String @NonNull [][]> x = f();
					}
				}
				""";
		Expected e1 = new Expected("Create field 'x'", str2);
		Expected e2 = new Expected("Create parameter 'x'", str3);
		Expected e3 = new Expected("Create local variable 'x'", str4);
		assertCodeActions(cu, e1, e2, e3);
	}

	/*
	 * Test that no null annotations are created in inapplicable location, here: cast.
	 */
	@Test
	public void test443146d() throws Exception {
		IPackageFragment pack1 = fSourceFolder.createPackageFragment("test1", false, null);
		String str = """
				package test1;
				import java.util.Map;
				import org.eclipse.jdt.annotation.*;

				abstract class Test {
					@NonNull Map<@NonNull String, @Nullable Integer> f(Object o) {
						return o;
					}
				}
				""";
		ICompilationUnit cu = pack1.createCompilationUnit("Test.java", str, false, null);

		String str1 = """
				package test1;
				import java.util.Map;
				import org.eclipse.jdt.annotation.*;

				abstract class Test {
					@NonNull Map<@NonNull String, @Nullable Integer> f(Object o) {
						return (Map<@NonNull String, @Nullable Integer>) o;
					}
				}
				""";
		Expected e1 = new Expected("Add cast to 'Map<String, Integer>'", str1);
		assertCodeActions(cu, e1);
	}

	/*
	 * Variation: @NonNullByDefault applies everywhere, type is a type variable
	 */
	@Test
	public void test443146e() throws Exception {
		IPackageFragment pack1 = fSourceFolder.createPackageFragment("test1", false, null);
		String str = """
				@org.eclipse.jdt.annotation.NonNullByDefault
				package test1;
				""";
		pack1.createCompilationUnit("package-info.java", str, true, null);

		String str1 = """
				package test1;
				import java.util.Map;
				import org.eclipse.jdt.annotation.*;

				abstract class Test<T> {
					abstract @NonNull T f();

					public void g() {
						x=f();
					}
				}
				""";
		ICompilationUnit cu = pack1.createCompilationUnit("Test.java", str1, false, null);

		String str2 = """
				package test1;
				import java.util.Map;
				import org.eclipse.jdt.annotation.*;

				abstract class Test<T> {
					private @NonNull T x;

				    abstract @NonNull T f();

					public void g() {
						x=f();
					}
				}
				""";

		String str3 = """
				package test1;
				import java.util.Map;
				import org.eclipse.jdt.annotation.*;

				abstract class Test<T> {
					abstract @NonNull T f();

					public void g(@NonNull T x) {
						x=f();
					}
				}
				""";

		String str4 = """
				package test1;
				import java.util.Map;
				import org.eclipse.jdt.annotation.*;

				abstract class Test<T> {
					abstract @NonNull T f();

					public void g() {
						@NonNull
				        T x = f();
					}
				}
				""";
		Expected e1 = new Expected("Create field 'x'", str2);
		Expected e2 = new Expected("Create parameter 'x'", str3);
		Expected e3 = new Expected("Create local variable 'x'", str4);
		assertCodeActions(cu, e1, e2, e3);
	}

	/*
	 * Variation: @NonNullByDefault applies everywhere, type contains explicit @NonNull on wildcard and type variable
	 */
	@Test
	public void test443146f() throws Exception {
		IPackageFragment pack1 = fSourceFolder.createPackageFragment("test1", false, null);
		String str = """
				@org.eclipse.jdt.annotation.NonNullByDefault
				package test1;
				""";
		pack1.createCompilationUnit("package-info.java", str, true, null);

		String str1 = """
				package test1;
				import java.util.Map;
				import org.eclipse.jdt.annotation.*;

				abstract class Test<T> {
					abstract Map<Map<@NonNull ?, Integer>, @NonNull T> f();

					public void g() {
						x=f();
					}
				}
				""";
		ICompilationUnit cu = pack1.createCompilationUnit("Test.java", str1, false, null);

		String str2 = """
				package test1;
				import java.util.Map;
				import org.eclipse.jdt.annotation.*;

				abstract class Test<T> {
					private Map<Map<@NonNull ?, Integer>, @NonNull T> x;

				    abstract Map<Map<@NonNull ?, Integer>, @NonNull T> f();

					public void g() {
						x=f();
					}
				}
				""";

		String str3 = """
				package test1;
				import java.util.Map;
				import org.eclipse.jdt.annotation.*;

				abstract class Test<T> {
					abstract Map<Map<@NonNull ?, Integer>, @NonNull T> f();

					public void g(Map<Map<@NonNull ?, Integer>, @NonNull T> x) {
						x=f();
					}
				}
				""";

		String str4 = """
				package test1;
				import java.util.Map;
				import org.eclipse.jdt.annotation.*;

				abstract class Test<T> {
					abstract Map<Map<@NonNull ?, Integer>, @NonNull T> f();

					public void g() {
						Map<Map<@NonNull ?, Integer>, @NonNull T> x = f();
					}
				}
				""";
		Expected e1 = new Expected("Create field 'x'", str2);
		Expected e2 = new Expected("Create parameter 'x'", str3);
		Expected e3 = new Expected("Create local variable 'x'", str4);
		assertCodeActions(cu, e1, e2, e3);
	}

	@Test
	public void testBug513682() throws Exception {
		IPackageFragment pack1 = fSourceFolder.createPackageFragment("test1", false, null);
		String str = """
				package test1;
				import org.eclipse.jdt.annotation.*;
				@NonNullByDefault
				public class Test {
				    void foo(Object o) {
				      if(o != null) {
				          o.hashCode();
				      }
				    }
				}
				""";
		ICompilationUnit cu = pack1.createCompilationUnit("Test.java", str, false, null);

		String str1 = """
				package test1;
				import org.eclipse.jdt.annotation.*;
				@NonNullByDefault
				public class Test {
				    void foo(@Nullable Object o) {
				      if(o != null) {
				          o.hashCode();
				      }
				    }
				}
				""";
		Expected e1 = new Expected("Change parameter 'o' to '@Nullable'", str1);
		assertCodeActions(cu, e1);
	}

	@Test
	public void testBug513209a() throws Exception {
		IPackageFragment pack1 = fSourceFolder.createPackageFragment("test1", false, null);
		String str = """
				package test1;
				public class A {
				   public void SomeMethod(
				      String[] a)
				   {

				   }
				}
				""";
		pack1.createCompilationUnit("A.java", str, false, null);

		String str1 = """
				package test1;
				import org.eclipse.jdt.annotation.*;
				@NonNullByDefault
				public class B extends A {
				   @Override
				   public void SomeMethod(
				      String[] a)
				   {

				   }
				}
				""";
		ICompilationUnit cu = pack1.createCompilationUnit("B.java", str1, false, null);

		String str2 = """
				package test1;
				import org.eclipse.jdt.annotation.*;
				@NonNullByDefault
				public class B extends A {
				   @Override
				   public void SomeMethod(
				      String @Nullable [] a)
				   {

				   }
				}
				""";
		Expected e1 = new Expected("Change parameter 'a' to '@Nullable'", str2);
		assertCodeActions(cu, e1);
	}

	@Test
	public void testBug513209b() throws Exception {
		IPackageFragment pack1 = fSourceFolder.createPackageFragment("test1", false, null);
		String str = """
				package test1;
				public class A {
				   public void SomeMethod(
				      int[][] a)
				   {

				   }
				}
				""";
		pack1.createCompilationUnit("A.java", str, false, null);

		String str1 = """
				package test1;
				import org.eclipse.jdt.annotation.*;
				@NonNullByDefault
				public class B extends A {
				   @Override
				   public void SomeMethod(
				      int[][] a)
				   {

				   }
				}
				""";
		ICompilationUnit cu = pack1.createCompilationUnit("B.java", str1, false, null);

		String str2 = """
				package test1;
				import org.eclipse.jdt.annotation.*;
				@NonNullByDefault
				public class B extends A {
				   @Override
				   public void SomeMethod(
				      int @Nullable [][] a)
				   {

				   }
				}
				""";
		Expected e1 = new Expected("Change parameter 'a' to '@Nullable'", str2);
		assertCodeActions(cu, e1);
	}

	@Test
	public void testBug513209c() throws Exception {
		IPackageFragment pack1 = fSourceFolder.createPackageFragment("test1", false, null);
		String str = """
				package test1;
				public class A {
				   public void SomeMethod(
				      String[] a)
				   {

				   }
				}
				""";
		pack1.createCompilationUnit("A.java", str, false, null);

		String str1 = """
				package test1;
				import org.eclipse.jdt.annotation.*;
				public class B extends A {
				   @Override
				   public void SomeMethod(
				      String @NonNull [] a)
				   {

				   }
				}
				""";
		ICompilationUnit cu = pack1.createCompilationUnit("B.java", str1, false, null);

		String str2 = """
				package test1;
				import org.eclipse.jdt.annotation.*;
				public class B extends A {
				   @Override
				   public void SomeMethod(
				      String @Nullable [] a)
				   {

				   }
				}
				""";
		Expected e1 = new Expected("Change parameter 'a' to '@Nullable'", str2);
		assertCodeActions(cu, e1);
	}

	@Test
	public void testBug513209d() throws Exception {
		IPackageFragment pack1 = fSourceFolder.createPackageFragment("test1", false, null);
		String str = """
				package test1;
				import org.eclipse.jdt.annotation.*;
				@NonNullByDefault
				public class A {
				   public String[][][] SomeMethod()
				   {
						return null;
				   }
				}
				""";
		pack1.createCompilationUnit("A.java", str, false, null);

		String str1 = """
				package test1;
				public class B extends A {
				   @Override
				   public String[][][] SomeMethod()
				   {
						return new String[0][][];
				   }
				}
				""";
		ICompilationUnit cu = pack1.createCompilationUnit("B.java", str1, false, null);

		String str2 = """
				package test1;

				import org.eclipse.jdt.annotation.NonNull;

				public class B extends A {
				   @Override
				   public String @NonNull [][][] SomeMethod()
				   {
						return new String[0][][];
				   }
				}
				""";
		Expected e1 = new Expected("Change return type of 'SomeMethod(..)' to '@NonNull'", str2);
		assertCodeActions(cu, e1);
	}

	@Test
	public void testBug525424() throws Exception {
		Hashtable<String, String> options = JavaCore.getOptions();
		try {
			Hashtable<String, String> myOptions = new Hashtable<>(options);
			myOptions.put(JavaCore.COMPILER_NULLABLE_ANNOTATION_NAME, "my.Nullable");
			myOptions.put(JavaCore.COMPILER_NONNULL_ANNOTATION_NAME, "my.NonNull");
			myOptions.put(JavaCore.COMPILER_NONNULL_BY_DEFAULT_ANNOTATION_NAME, "my.NonNullByDefault");
			JavaCore.setOptions(myOptions);

			IPackageFragment my = fSourceFolder.createPackageFragment("my", false, null);
			String str = """
					package my;

					import java.lang.annotation.ElementType;
					import java.lang.annotation.Target;


					@Target(ElementType.TYPE_USE)
					public @interface Nullable {
					}
					""";
			my.createCompilationUnit("Nullable.java", str, false, null);

			String str1 = """
					package my;

					import java.lang.annotation.ElementType;
					import java.lang.annotation.Target;

					@Target(ElementType.TYPE_USE)
					public @interface NonNull {
					}
					""";
			my.createCompilationUnit("NonNull.java", str1, false, null);

			String str2 = """
					package my;

					public enum DefaultLocation {
						PARAMETER, RETURN_TYPE, FIELD, TYPE_BOUND, TYPE_ARGUMENT
					}
					""";
			my.createCompilationUnit("DefaultLocation.java", str2, false, null);

			String str3 = """
					package my;

					import static my.DefaultLocation.*;

					public @interface NonNullByDefault {
						DefaultLocation[] value() default { PARAMETER, RETURN_TYPE, FIELD, TYPE_BOUND, TYPE_ARGUMENT };
					}
					""";
			my.createCompilationUnit("NonNullByDefault.java", str3, false, null);

			IPackageFragment pack1 = fSourceFolder.createPackageFragment("test1", false, null);
			String str4 = """
					package test1;
					public class A {
					   public void SomeMethod(
					      String[] a)
					   {

					   }
					}
					""";
			pack1.createCompilationUnit("A.java", str4, false, null);

			String str5 = """
					package test1;
					import my.*;
					@NonNullByDefault
					public class B extends A {
					   @Override
					   public void SomeMethod(
					      String[] a)
					   {

					   }
					}
					""";
			ICompilationUnit cu = pack1.createCompilationUnit("B.java", str5, false, null);

			String str6 = """
					package test1;
					import my.*;
					@NonNullByDefault
					public class B extends A {
					   @Override
					   public void SomeMethod(
					      String @Nullable [] a)
					   {

					   }
					}
					""";
			Expected e1 = new Expected("Change parameter 'a' to '@Nullable'", str6);
			assertCodeActions(cu, e1);
		} finally {
			JavaCore.setOptions(options);
		}
	}
		@Test
		public void testGH1294() throws Exception {
			IPackageFragment my= fSourceFolder.createPackageFragment("my", false, null);
			ICompilationUnit cu= my.createCompilationUnit("Test.java",
					"""
					package my;
					import org.eclipse.jdt.annotation.*;
					interface IInputValidator {
						public String isValid(String newText);
					}
					public class Test {
						public static IInputValidator getRefNameInputValidator(
								final Object repo, final String refPrefix,
								final boolean errorOnEmptyName) {
							return new IInputValidator() {
								@Override
								public String isValid(String newText) {
									String validationStatus = validateNewRefName(newText, this,
											refPrefix, errorOnEmptyName);
									return validationStatus;
								}
							};
						}
						@NonNull
						public static String validateNewRefName(String refNameInput,
								@NonNull Object repo, @NonNull String refPrefix,
								final boolean errorOnEmptyName) {
							return "";
						}
					}
					""",
					false, null);

			String str1 = """
					package my;
					import org.eclipse.jdt.annotation.*;
					interface IInputValidator {
						public String isValid(String newText);
					}
					public class Test {
						public static IInputValidator getRefNameInputValidator(
								final Object repo, final @NonNull String refPrefix,
								final boolean errorOnEmptyName) {
							return new IInputValidator() {
								@Override
								public String isValid(String newText) {
									String validationStatus = validateNewRefName(newText, this,
											refPrefix, errorOnEmptyName);
									return validationStatus;
								}
							};
						}
						@NonNull
						public static String validateNewRefName(String refNameInput,
								@NonNull Object repo, @NonNull String refPrefix,
								final boolean errorOnEmptyName) {
							return "";
						}
					}
					""";
			Expected e1 = new Expected("Change parameter 'refPrefix' to '@NonNull'", str1);
			assertCodeActions(cu, e1);
		}

		@Test
		public void testGH1294_noQuickfix() throws Exception {
			IPackageFragment my = fSourceFolder.createPackageFragment("my", false, null);
			ICompilationUnit cu = my.createCompilationUnit("Test.java", """
					package my;
					import org.eclipse.jdt.annotation.*;
					import java.util.List;
					interface IInputValidator {
						public String isValid(String newText);
					}
					public class Test {
						public static IInputValidator getRefNameInputValidator(
								final Object repo, final List<String> refPrefix,
								final boolean errorOnEmptyName) {
							return new IInputValidator() {
								@Override
								public String isValid(String newText) {
									String validationStatus = validateNewRefName(newText, this,
											refPrefix, errorOnEmptyName);
									return validationStatus;
								}
							};
						}
						@NonNull
						public static String validateNewRefName(String refNameInput,
								@NonNull Object repo, List<@NonNull String> refPrefix,
								final boolean errorOnEmptyName) {
							return "";
						}
					}
					""", false, null);

			assertCodeActionExists(cu, new String[] { "Add @SuppressWarnings 'null' to 'isValid()'", "Add @SuppressWarnings 'null' to 'validationStatus'" });
		}

		@Test
		public void testGH1294_lambda() throws Exception {
			IPackageFragment my = fSourceFolder.createPackageFragment("my", false, null);
			ICompilationUnit cu = my.createCompilationUnit("Test.java", """
					package my;
					import org.eclipse.jdt.annotation.*;
					interface IInputValidator {
						public String isValid(String newText);
					}
					public class Test {
						public static IInputValidator getRefNameInputValidator(
								final Object repo, final String refPrefix,
								final boolean errorOnEmptyName) {
							return (String newText) -> {
									String validationStatus = validateNewRefName(newText, new Object(),
											refPrefix, errorOnEmptyName);
									return validationStatus;
							};
						}
						@NonNull
						public static String validateNewRefName(String refNameInput,
								@NonNull Object repo, @NonNull String refPrefix,
								final boolean errorOnEmptyName) {
							return "";
						}
					}
					""", false, null);

			String str1 = """
					package my;
					import org.eclipse.jdt.annotation.*;
					interface IInputValidator {
						public String isValid(String newText);
					}
					public class Test {
						public static IInputValidator getRefNameInputValidator(
								final Object repo, final @NonNull String refPrefix,
								final boolean errorOnEmptyName) {
							return (String newText) -> {
									String validationStatus = validateNewRefName(newText, new Object(),
											refPrefix, errorOnEmptyName);
									return validationStatus;
							};
						}
						@NonNull
						public static String validateNewRefName(String refNameInput,
								@NonNull Object repo, @NonNull String refPrefix,
								final boolean errorOnEmptyName) {
							return "";
						}
					}
					""";
			Expected e1 = new Expected("Change parameter 'refPrefix' to '@NonNull'", str1);
			assertCodeActions(cu, e1);
		}

		protected static CompilationUnit getASTRoot(ICompilationUnit cu) {
			return ASTResolving.createQuickFixAST(cu, null);
		}

		@Test
		public void testGH1294_varargs() throws Exception {
			IPackageFragment my = fSourceFolder.createPackageFragment("my", false, null);
			ICompilationUnit cu = my.createCompilationUnit("Test.java", """
					package my;
					import org.eclipse.jdt.annotation.*;
					interface IInputValidator {
						public String isValid(@NonNull String newText);
					}
					public class Test {
						public static IInputValidator getRefNameInputValidator(
								final Object repo, final String refPrefix,
								final boolean errorOnEmptyName) {
							return new IInputValidator() {
								public String isValid(@NonNull String newText) {
									String validationStatus = validateNewRefName(newText, refPrefix);
									return validationStatus;
								}
							};
						}
						@NonNull
						public static String validateNewRefName(@NonNull String... refPrefix) {
							return "";
						}
					}
					""", false, null);

			assertCodeActionExists(cu, new String[] { "Add @SuppressWarnings 'null' to 'isValid()'", "Add @SuppressWarnings 'null' to 'validationStatus'", "Change parameter 'refPrefix' to '@NonNull'" });
		}

		@Test
		public void testGH1294_varargs_ok() throws Exception {
			IPackageFragment my = fSourceFolder.createPackageFragment("my", false, null);
			ICompilationUnit cu = my.createCompilationUnit("Test.java", """
					package my;
					import org.eclipse.jdt.annotation.*;
					interface IInputValidator {
						public String isValid(@NonNull String newText);
					}
					public class Test {
						public static IInputValidator getRefNameInputValidator(
								final Object repo, final String refPrefix,
								final boolean errorOnEmptyName) {
							return new IInputValidator() {
								public String isValid(@NonNull String newText) {
									String validationStatus = validateNewRefName(newText, refPrefix);
									return validationStatus;
								}
							};
						}
						@NonNull
						public static String validateNewRefName(String s1, @NonNull String s2, @NonNull String... refPrefix) {
							return "";
						}
					}
					""", false, null);

			assertCodeActionExists(cu, new String[] { "Add @SuppressWarnings 'null' to 'isValid()'", "Add @SuppressWarnings 'null' to 'validationStatus'", "Change parameter 'refPrefix' to '@NonNull'",
					"Change parameter of 'validateNewRefName(..)' to '@Nullable'" });
		}

		@Test
		public void testGH2913() throws Exception {
			IPackageFragment my = fSourceFolder.createPackageFragment("my", false, null);
			ICompilationUnit cu = my.createCompilationUnit("Test.java", """
					package my;
					import org.eclipse.jdt.annotation.*;
					public class Test {
					  public void invokeMe(@Nullable String aaa) {
					    System.out.println(aaa.length());
					  }
					}
					""", false, null);

			String str1 = """
					package my;
					import org.eclipse.jdt.annotation.*;
					public class Test {
					  public void invokeMe(@NonNull String aaa) {
					    System.out.println(aaa.length());
					  }
					}
					""";
			Expected e1 = new Expected("Change 'aaa' to '@NonNull'", str1);
			assertCodeActionExists(cu, e1);
		}

		@Test
		public void testGH2919() throws Exception {
			IPackageFragment my = fSourceFolder.createPackageFragment("my", false, null);
			ICompilationUnit cu = my.createCompilationUnit("Test.java", """
					package my;
					import org.eclipse.jdt.annotation.NonNull;
					import org.eclipse.jdt.annotation.Nullable;
					public class Test {
					  public @Nullable @NonNull String invokeMe(String aaa) {
					    return "abc";
					  }
					}
					""", false, null);

			String str1 = """
					package my;
					import org.eclipse.jdt.annotation.Nullable;
					public class Test {
					  public @Nullable String invokeMe(String aaa) {
					    return "abc";
					  }
					}
					""";
			Expected e1 = new Expected("Remove '@NonNull'", str1);

			String str2 = """
					package my;
					import org.eclipse.jdt.annotation.NonNull;
					public class Test {
					  public @NonNull String invokeMe(String aaa) {
					    return "abc";
					  }
					}
					""";
			Expected e2 = new Expected("Remove '@Nullable'", str2);
			assertCodeActions(cu, e1, e2);
		}

		@Test
		public void testGH2822_1() throws Exception {
			IPackageFragment my = fSourceFolder.createPackageFragment("my", false, null);
			ICompilationUnit cu = my.createCompilationUnit("Test.java", """
					package my;
					import org.eclipse.jdt.annotation.*;
					public class Test {
					  public void doStuff() {
					    invokeMe(getString());
					  }
					  public void invokeMe(@NonNull String value) {
					    System.out.println(value.length());
					  }
					  public @Nullable String getString() {
					    return "my string";
					  }
					}
					""", false, null);


			String str1 = """
					package my;
					import org.eclipse.jdt.annotation.*;
					public class Test {
					  public void doStuff() {
					    invokeMe(getString());
					  }
					  public void invokeMe(@NonNull String value) {
					    System.out.println(value.length());
					  }
					  public @NonNull String getString() {
					    return "my string";
					  }
					}
					""";
			Expected e1 = new Expected("Change 'getString()' to '@NonNull'", str1);
			assertCodeActionExists(cu, e1);
		}

		@Test
		public void testGH2822_2() throws Exception {
			IPackageFragment my = fSourceFolder.createPackageFragment("my", false, null);
			ICompilationUnit cu = my.createCompilationUnit("Test.java", """
					package my;
					import org.eclipse.jdt.annotation.*;
					public class Test {
					  public void doStuff() {
					    invokeMe(getString());
					  }
					  public void invokeMe(@NonNull String value) {
					    System.out.println(value.length());
					  }
					  public @Nullable String getString() {
					    return getOtherString();
					  }
					  public @NonNull String getOtherString() {
					  	return "other string";
					  }
					}
					""", false, null);

			String str1 = """
					package my;
					import org.eclipse.jdt.annotation.*;
					public class Test {
					  public void doStuff() {
					    invokeMe(getString());
					  }
					  public void invokeMe(@NonNull String value) {
					    System.out.println(value.length());
					  }
					  public @NonNull String getString() {
					    return getOtherString();
					  }
					  public @NonNull String getOtherString() {
					  	return "other string";
					  }
					}
					""";
			Expected e1 = new Expected("Change 'getString()' to '@NonNull'", str1);
			assertCodeActionExists(cu, e1);
		}

}
