/*******************************************************************************
 * Copyright (c) 2026 GK Software AG and others.
 *
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *     Stephan Herrmann - initial API and implementation
 *     IBM Corporation - bug fixes
 *     Red Hat Ltd - modified for use in jdt.ls
 *******************************************************************************/
package org.eclipse.jdt.ls.core.internal.correction;

import java.io.IOException;
import java.net.URL;
import java.util.Hashtable;

import org.eclipse.core.runtime.FileLocator;
import org.eclipse.core.runtime.Path;
import org.eclipse.core.runtime.Platform;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IPackageFragment;
import org.eclipse.jdt.core.IPackageFragmentRoot;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.formatter.DefaultCodeFormatterConstants;
import org.eclipse.jdt.internal.core.manipulation.CodeTemplateContextType;
import org.eclipse.jdt.internal.core.manipulation.StubUtility;
import org.eclipse.jdt.ls.core.internal.JavaProjectHelper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class NullAnnotationsQuickFixTest extends AbstractQuickFixTest {

	private IJavaProject fJProject1;
	private IPackageFragmentRoot fSourceFolder;
	private String ANNOTATION_JAR_PATH;

	@BeforeEach
	public void setUp() throws Exception {
		fJProject1 = newEmptyProject();
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
			ANNOTATION_JAR_PATH = getAnnotationV1LibPath();
		}
		JavaProjectHelper.addLibrary(fJProject1, new Path(ANNOTATION_JAR_PATH));

		fSourceFolder = fJProject1.getPackageFragmentRoot(fJProject1.getProject().getFolder("src"));
	}

	public static String getAnnotationV1LibPath() throws IOException {
		URL libEntry = Platform.getBundle("org.eclipse.jdt.ls.tests").getEntry("/testresources/org.eclipse.jdt.annotation_1.2.100.v20241001-0914.jar");
		return FileLocator.toFileURL(libEntry).getPath();
	}

	// ==== Problem:	dereferencing a @Nullable field
	// ==== Fix:		extract field access to a fresh local variable and add a null-check

	// basic case
	@Test
	public void testExtractNullableField1() throws Exception {
		IPackageFragment pack1 = fSourceFolder.createPackageFragment("test1", false, null);
		String str = """
				package test1;
				import org.eclipse.jdt.annotation.*;
				public class E {
				    @Nullable String f;
				    public void foo() {
				        System.out.println(f.toUpperCase());
				    }
				}
				""";
		ICompilationUnit cu = pack1.createCompilationUnit("E.java", str, false, null);

		String str1 = """
				package test1;
				import org.eclipse.jdt.annotation.*;
				public class E {
				    @Nullable String f;
				    public void foo() {
				        final String f2 = f;
				        if (f2 != null) {
				            System.out.println(f2.toUpperCase());
				        } else {
				            // TODO handle null value
				        }
				    }
				}
				""";
		Expected e1 = new Expected("Extract to checked local variable", str1.toString());
		assertCodeActions(cu, e1);
	}

	// statement is not element of a block - need to create a new block - local name f2 already in use
	@Test
	public void testExtractNullableField2() throws Exception {
		IPackageFragment pack1 = fSourceFolder.createPackageFragment("test1", false, null);
		String str = """
				package test1;
				import org.eclipse.jdt.annotation.*;
				public class E {
				    @Nullable String f;
				    public void foo(boolean b) {
				        @SuppressWarnings("unused") boolean f2 = false;
				        if (b)
				          System.out.println(f.toUpperCase());
				    }
				}
				""";
		ICompilationUnit cu = pack1.createCompilationUnit("E.java", str, false, null);

		String str1 = """
				package test1;
				import org.eclipse.jdt.annotation.*;
				public class E {
				    @Nullable String f;
				    public void foo(boolean b) {
				        @SuppressWarnings("unused") boolean f2 = false;
				        if (b) {
				            final String f3 = f;
				            if (f3 != null) {
				                System.out.println(f3.toUpperCase());
				            } else {
				                // TODO handle null value
				            }
				        }
				    }
				}
				""";
		Expected e1 = new Expected("Extract to checked local variable", str1.toString());
		assertCodeActions(cu, e1);
	}

	// field name is part of a qualified field reference - inside a return statement (type: int)
	@Test
	public void testExtractNullableField3() throws Exception {
		IPackageFragment pack1 = fSourceFolder.createPackageFragment("test1", false, null);
		String str = """
				package test1;
				import org.eclipse.jdt.annotation.*;
				public class E {
				    @Nullable E other;
				    int f;
				    public int foo(E that) {
				        return that.other.f;
				    }
				}
				""";
		ICompilationUnit cu = pack1.createCompilationUnit("E.java", str, false, null);

		String str1 = """
				package test1;
				import org.eclipse.jdt.annotation.*;
				public class E {
				    @Nullable E other;
				    int f;
				    public int foo(E that) {
				        final E other2 = that.other;
				        if (other2 != null) {
				            return other2.f;
				        } else {
				            // TODO handle null value
				            return 0;
				        }
				    }
				}
				""";
		Expected e1 = new Expected("Extract to checked local variable", str1.toString());
		assertCodeActions(cu, e1);
	}

	// field name is part of a this-qualified field reference - inside a return statement (type: String)
	@Test
	public void testExtractNullableField4() throws Exception {
		IPackageFragment pack1 = fSourceFolder.createPackageFragment("test1", false, null);
		String str = """
				package test1;
				import org.eclipse.jdt.annotation.*;
				public class E {
				    @Nullable E other;
				    @Nullable String f;
				    public String foo() {
				        return this.other.f;
				    }
				}
				""";
		ICompilationUnit cu = pack1.createCompilationUnit("E.java", str, false, null);

		String str1 = """
				package test1;
				import org.eclipse.jdt.annotation.*;
				public class E {
				    @Nullable E other;
				    @Nullable String f;
				    public String foo() {
				        final E other2 = this.other;
				        if (other2 != null) {
				            return other2.f;
				        } else {
				            // TODO handle null value
				            return null;
				        }
				    }
				}
				""";
		Expected e1 = new Expected("Extract to checked local variable", str1.toString());
		assertCodeActions(cu, e1);
	}

	// field referenced inside the rhs of an assignment-as-expression
	@Test
	public void testExtractNullableField5() throws Exception {
		IPackageFragment pack1 = fSourceFolder.createPackageFragment("test1", false, null);
		String str = """
				package test1;
				import org.eclipse.jdt.annotation.*;
				public class E {
				    @Nullable E other;
				    @Nullable String f;
				    public void foo() {
				        String lo;
				        if ((lo = this.other.f) != null)
				            System.out.println(lo);
				    }
				}
				""";
		ICompilationUnit cu = pack1.createCompilationUnit("E.java", str, false, null);

		String str1 = """
				package test1;
				import org.eclipse.jdt.annotation.*;
				public class E {
				    @Nullable E other;
				    @Nullable String f;
				    public void foo() {
				        String lo;
				        final E other2 = this.other;
				        if (other2 != null) {
				            if ((lo = other2.f) != null)
				                System.out.println(lo);
				        } else {
				            // TODO handle null value
				        }
				    }
				}
				""";
		Expected e1 = new Expected("Extract to checked local variable", str1.toString());
		assertCodeActions(cu, e1);
	}

	// reference to field of array type - dereferenced by f[0] and f.length
	@Test
	public void testExtractNullableField6() throws Exception {
		IPackageFragment pack1 = fSourceFolder.createPackageFragment("test1", false, null);
		String str = """
				package test1;
				import org.eclipse.jdt.annotation.*;
				public class E {
				    @Nullable String[] f1;
				    @Nullable String[] f2;
				    public void foo() {
				        System.out.println(f1[0]);
				        System.out.println(f2.length);
				    }
				}
				""";
		ICompilationUnit cu = pack1.createCompilationUnit("E.java", str, false, null);

		String str1 = """
				package test1;
				import org.eclipse.jdt.annotation.*;
				public class E {
				    @Nullable String[] f1;
				    @Nullable String[] f2;
				    public void foo() {
				        final String[] f12 = f1;
				        if (f12 != null) {
				            System.out.println(f12[0]);
				        } else {
				            // TODO handle null value
				        }
				        System.out.println(f2.length);
				    }
				}
				""";

		Expected e1 = new Expected("Extract to checked local variable", str1.toString());
		assertCodeActions(cu, e1);
	}

	// field has a generic type
	@Test
	public void testExtractNullableField7() throws Exception {
		IPackageFragment pack1 = fSourceFolder.createPackageFragment("test1", false, null);
		String str = """
				package test1;
				import org.eclipse.jdt.annotation.*;
				import java.util.List;
				public class E {
				    @Nullable List<String> f;
				    public void foo() {
				        System.out.println(f.size());
				    }
				}
				""";
		ICompilationUnit cu = pack1.createCompilationUnit("E.java", str, false, null);

		String str1 = """
				package test1;
				import org.eclipse.jdt.annotation.*;
				import java.util.List;
				public class E {
				    @Nullable List<String> f;
				    public void foo() {
				        final List<String> f2 = f;
				        if (f2 != null) {
				            System.out.println(f2.size());
				        } else {
				            // TODO handle null value
				        }
				    }
				}
				""";
		Expected e1 = new Expected("Extract to checked local variable", str1.toString());
		assertCodeActions(cu, e1);
	}

	// occurrences inside a class initializer
	@Test
	public void testExtractNullableField8() throws Exception {
		IPackageFragment pack1 = fSourceFolder.createPackageFragment("test1", false, null);
		String str = """
				package test1;
				import org.eclipse.jdt.annotation.*;
				public class E {
				    @Nullable Exception e;
				    {
				        e.printStackTrace();
				    }
				}
				""";
		ICompilationUnit cu = pack1.createCompilationUnit("E.java", str, false, null);

		String str1 = """
				package test1;
				import org.eclipse.jdt.annotation.*;
				public class E {
				    @Nullable Exception e;
				    {
				        final Exception e2 = e;
				        if (e2 != null) {
				            e2.printStackTrace();
				        } else {
				            // TODO handle null value
				        }
				    }
				}
				""";
		Expected e1 = new Expected("Extract to checked local variable", str1.toString());
		assertCodeActions(cu, e1);
	}

	// field reference inside a local variable initialization - ensure correct scoping of this local
	@Test
	public void testExtractNullableField9() throws Exception {
		IPackageFragment pack1 = fSourceFolder.createPackageFragment("test1", false, null);
		String str = """
				package test1;
				import org.eclipse.jdt.annotation.*;
				public class E {
				    @Nullable String f;
				    public String foo() {
				        String upper = f.toUpperCase();
				        System.out.println(upper);
				        return upper;
				    }
				}
				""";
		ICompilationUnit cu = pack1.createCompilationUnit("E.java", str, false, null);

		String str1 = """
				package test1;
				import org.eclipse.jdt.annotation.*;
				public class E {
				    @Nullable String f;
				    public String foo() {
				        final String f2 = f;
				        if (f2 != null) {
				            String upper = f2.toUpperCase();
				            System.out.println(upper);
				            return upper;
				        } else {
				            // TODO handle null value
				        }
				    }
				}
				""";
		Expected e1 = new Expected("Extract to checked local variable", str1.toString());
		assertCodeActions(cu, e1);
	}

	// ==== Problem:	using a @Nullable or un-annotated field in assignment/return context expecting @NonNull
	// ==== Fix:		extract field access to a fresh local variable and add a null-check

	// return situation, field reference is this.f
	@Test
	public void testExtractPotentiallyNullField1() throws Exception {
		IPackageFragment pack1 = fSourceFolder.createPackageFragment("test1", false, null);
		String str = """
				package test1;
				import org.eclipse.jdt.annotation.*;
				public class E {
				    @Nullable String f;
				    public @NonNull String foo() {
				        return this.f;
				    }
				}
				""";
		ICompilationUnit cu = pack1.createCompilationUnit("E.java", str, false, null);

		String str1 = """
				package test1;
				import org.eclipse.jdt.annotation.*;
				public class E {
				    @Nullable String f;
				    public @NonNull String foo() {
				        final String f2 = this.f;
				        if (f2 != null) {
				            return f2;
				        } else {
				            // TODO handle null value
				            return null;
				        }
				    }
				}
				""";

		// secondary proposal: Change return type of 'foo(..)' to '@Nullable'
		String str2 = """
				package test1;
				import org.eclipse.jdt.annotation.*;
				public class E {
				    @Nullable String f;
				    public @Nullable String foo() {
				        return this.f;
				    }
				}
				""";
		Expected e1 = new Expected("Extract to checked local variable", str1.toString());
		Expected e2 = new Expected("Change return type of 'foo(..)' to '@Nullable'", str2.toString());
		assertCodeActions(cu, e1, e2);
	}

	// message send argument situation, field reference is local.f
	@Test
	public void testExtractPotentiallyNullField2() throws Exception {
		IPackageFragment pack1 = fSourceFolder.createPackageFragment("test1", false, null);
		String str = """
				package test1;
				import org.eclipse.jdt.annotation.*;
				public class E {
				    @Nullable String f;
				    public void foo() {
				        E local = this;
				        bar(local.f);
				    }
				    public void bar(@NonNull String s) { }
				}
				""";
		ICompilationUnit cu = pack1.createCompilationUnit("E.java", str, false, null);

		String str1 = """
				package test1;
				import org.eclipse.jdt.annotation.*;
				public class E {
				    @Nullable String f;
				    public void foo() {
				        E local = this;
				        final String f2 = local.f;
				        if (f2 != null) {
				            bar(f2);
				        } else {
				            // TODO handle null value
				        }
				    }
				    public void bar(@NonNull String s) { }
				}
				""";
		Expected e1 = new Expected("Extract to checked local variable", str1.toString());
		assertCodeActions(cu, e1);
	}

	// @Nullable argument is used where @NonNull is required -> change to @NonNull
	@Test
	public void testChangeParameter1a() throws Exception {
		IPackageFragment pack1 = fSourceFolder.createPackageFragment("test1", false, null);
		String str = """
				package test1;
				import org.eclipse.jdt.annotation.*;
				public class E {
				    void foo(@Nullable Exception e1) {
				        @NonNull Exception e = new Exception();
				        e = e1;
				    }
				}
				""";
		ICompilationUnit cu = pack1.createCompilationUnit("E.java", str, false, null);

		String str1 = """
				package test1;
				import org.eclipse.jdt.annotation.*;
				public class E {
				    void foo(@NonNull Exception e1) {
				        @NonNull Exception e = new Exception();
				        e = e1;
				    }
				}
				""";
		Expected e1 = new Expected("Change parameter 'e1' to '@NonNull'", str1.toString());
		assertCodeActions(cu, e1);
	}

	// unspec'ed argument is used where @NonNull is required -> change to @NonNull
	@Test
	public void testChangeParameter1b() throws Exception {
		IPackageFragment pack1 = fSourceFolder.createPackageFragment("test1", false, null);
		String str = """
				package test1;
				import org.eclipse.jdt.annotation.*;
				public class E {
				    void foo(Exception e1) {
				        @NonNull Exception e = new Exception();
				        e = e1;
				    }
				}
				""";
		ICompilationUnit cu = pack1.createCompilationUnit("E.java", str, false, null);

		String str1 = """
				package test1;
				import org.eclipse.jdt.annotation.*;
				public class E {
				    void foo(@NonNull Exception e1) {
				        @NonNull Exception e = new Exception();
				        e = e1;
				    }
				}
				""";
		Expected e1 = new Expected("Change parameter 'e1' to '@NonNull'", str1.toString());
		assertCodeActions(cu, e1);
	}

	@Test
	public void testChangeParameter1c() throws Exception {
		IPackageFragment pack1 = fSourceFolder.createPackageFragment("test1", false, null);
		String str = """
				package test1;
				import org.eclipse.jdt.annotation.*;
				public class E {
				    @NonNull Object foo(@Nullable Object o) {
				        return o;
				    }
				}
				""";
		ICompilationUnit cu = pack1.createCompilationUnit("E.java", str, false, null);

		String str1 = """
				package test1;
				import org.eclipse.jdt.annotation.*;
				public class E {
				    @NonNull Object foo(@NonNull Object o) {
				        return o;
				    }
				}
				""";
		Expected e1 = new Expected("Change parameter 'o' to '@NonNull'", str1.toString());
		assertCodeActions(cu, e1);
	}

	@Test
	public void testChangeParameter1d() throws Exception {
		IPackageFragment pack1 = fSourceFolder.createPackageFragment("test1", false, null);
		String str = """
				package test1;
				import org.eclipse.jdt.annotation.*;
				public class E {
				    @NonNull Object foo(Object o) {
				        return o;
				    }
				}
				""";
		ICompilationUnit cu = pack1.createCompilationUnit("E.java", str, false, null);

		String str1 = """
				package test1;
				import org.eclipse.jdt.annotation.*;
				public class E {
				    @NonNull Object foo(@NonNull Object o) {
				        return o;
				    }
				}
				""";
		Expected e1 = new Expected("Change parameter 'o' to '@NonNull'", str1.toString());
		assertCodeActions(cu, e1);
	}

	// don't propose to change argument if mismatch is in an assignment to the argument
	@Test
	public void testChangeParameter2() throws Exception {
		IPackageFragment pack1 = fSourceFolder.createPackageFragment("test1", false, null);
		String str = """
				package test1;
				import org.eclipse.jdt.annotation.*;
				public class E {
				    void foo(@NonNull Exception e1) {
				        e1 = null;
				    }
				}
				""";
		ICompilationUnit cu = pack1.createCompilationUnit("E.java", str, false, null);

		assertCodeActionNotExists(cu, "Change parameter 'o' to '@NonNull'");
	}

	// Attempt to override a @Nullable argument with a @NonNull argument
	// -> change to @Nullable
	// -> change overridden to @NonNull
	@Test
	public void testChangeParameter3a() throws Exception {
		IPackageFragment pack1 = fSourceFolder.createPackageFragment("test1", false, null);
		String str = """
				package test1;
				import org.eclipse.jdt.annotation.*;
				public class E {
				    void foo(@Nullable Exception e1) {
				    }
				}
				""";
		pack1.createCompilationUnit("E.java", str, false, null);

		String str1 = """
				package test1;
				import org.eclipse.jdt.annotation.*;
				public class E2 extends E {
				    void foo(@NonNull Exception e1) {
				        e1.printStackTrace();
				    }
				}
				""";
		ICompilationUnit cu = pack1.createCompilationUnit("E2.java", str1, false, null);

		String str2 = """
				package test1;
				import org.eclipse.jdt.annotation.*;
				public class E2 extends E {
				    void foo(@Nullable Exception e1) {
				        e1.printStackTrace();
				    }
				}
				""";

		String str3 = """
				package test1;
				import org.eclipse.jdt.annotation.*;
				public class E {
				    void foo(@NonNull Exception e1) {
				    }
				}
				""";
		Expected e1 = new Expected("Change parameter 'e1' to '@Nullable'", str2.toString());
		Expected e2 = new Expected("Change parameter in overridden 'foo(..)' to '@NonNull'", str3.toString());
		assertCodeActions(cu, e1, e2);
	}

	// Attempt to override a @Nullable argument with an unspec'ed argument
	// -> change to @Nullable
	@Test
	public void testChangeParameter3b() throws Exception {
		IPackageFragment pack1 = fSourceFolder.createPackageFragment("test1", false, null);
		String str = """
				package test1;
				import org.eclipse.jdt.annotation.*;
				public class E {
				    void foo(@Nullable Exception e1) {
				    }
				}
				""";
		pack1.createCompilationUnit("E.java", str, false, null);

		String str1 = """
				package test1;
				public class E2 extends E {
				    void foo(Exception e1) {
				        e1.printStackTrace();
				    }
				}
				""";
		ICompilationUnit cu = pack1.createCompilationUnit("E2.java", str1, false, null);

		String str2 = """
				package test1;

				import org.eclipse.jdt.annotation.Nullable;

				public class E2 extends E {
				    void foo(@Nullable Exception e1) {
				        e1.printStackTrace();
				    }
				}
				""";
		Expected e1 = new Expected("Change parameter 'e1' to '@Nullable'", str2);
		assertCodeActions(cu, e1);
	}

	// Attempt to override a @NonNull argument with an unspec'ed argument
	// -> change to @NonNull
	@Test
	public void testChangeParameter3c() throws Exception {
		// quickfix only offered with this warning enabled, but no need to say, because default is already "warning"
		//		this.fJProject1.setOption(JavaCore.COMPILER_PB_NONNULL_PARAMETER_ANNOTATION_DROPPED, JavaCore.WARNING);
		IPackageFragment pack1 = fSourceFolder.createPackageFragment("test1", false, null);
		String str = """
				package test1;
				import org.eclipse.jdt.annotation.*;
				public class E {
				    void foo(@NonNull Exception e1) {
				    }
				}
				""";
		pack1.createCompilationUnit("E.java", str, false, null);

		String str1 = """
				package test1;
				public class E2 extends E {
				    void foo(Exception e1) {
				        e1.printStackTrace();
				    }
				}
				""";
		ICompilationUnit cu = pack1.createCompilationUnit("E2.java", str1, false, null);

		String str2 = """
				package test1;

				import org.eclipse.jdt.annotation.NonNull;

				public class E2 extends E {
				    void foo(@NonNull Exception e1) {
				        e1.printStackTrace();
				    }
				}
				""";
		Expected e1 = new Expected("Change parameter 'e1' to '@NonNull'", str2);
		assertCodeActions(cu, e1);
	}

	// http://bugs.eclipse.org/400668 - [quick fix] The fix change parameter type to @Nonnull generated a null change
	// don't confuse changing arguments of current method and target method
	// -> split into two proposals
	@Test
	public void testChangeParameter4() throws Exception {
		IPackageFragment pack1 = fSourceFolder.createPackageFragment("test1", false, null);
		String str = """
				package test1;
				import org.eclipse.jdt.annotation.*;
				public class E {
				    void foo(@NonNull Object o) {
				        // nop
				    }
				}
				""";
		pack1.createCompilationUnit("E.java", str, false, null);

		String str1 = """
				package test1;
				import org.eclipse.jdt.annotation.*;
				public class E2 {
				    void test(E e, @Nullable Object in) {
				        e.foo(in);
				    }
				}
				""";
		ICompilationUnit cu = pack1.createCompilationUnit("E2.java", str1, false, null);

		String str2 = """
				package test1;
				import org.eclipse.jdt.annotation.*;
				public class E2 {
				    void test(E e, @NonNull Object in) {
				        e.foo(in);
				    }
				}
				""";

		String str3 = """
				package test1;
				import org.eclipse.jdt.annotation.*;
				public class E {
				    void foo(@Nullable Object o) {
				        // nop
				    }
				}
				""";
		Expected e1 = new Expected("Change parameter 'in' to '@NonNull'", str2);
		Expected e2 = new Expected("Change parameter of 'foo(..)' to '@Nullable'", str3);
		assertCodeActions(cu, e1, e2);
	}

	// http://bugs.eclipse.org/400668 - [quick fix] The fix change parameter type to @Nonnull generated a null change
	// don't confuse changing arguments of current method and target method
	// -> split into two proposals
	// variant with un-annotated parameter
	@Test
	public void testChangeParameter4a() throws Exception {
		IPackageFragment pack1 = fSourceFolder.createPackageFragment("test1", false, null);
		String str = """
				package test1;
				import org.eclipse.jdt.annotation.*;
				public class E {
				    void foo(@NonNull Object o) {
				        // nop
				    }
				}
				""";
		pack1.createCompilationUnit("E.java", str, false, null);

		String str1 = """
				package test1;
				public class E2 {
				    void test(E e, Object in) {
				        e.foo(in);
				    }
				}
				""";
		ICompilationUnit cu = pack1.createCompilationUnit("E2.java", str1, false, null);

		String str2 = """
				package test1;

				import org.eclipse.jdt.annotation.NonNull;

				public class E2 {
				    void test(E e, @NonNull Object in) {
				        e.foo(in);
				    }
				}
				""";

		String str3 = """
				package test1;
				import org.eclipse.jdt.annotation.*;
				public class E {
				    void foo(@Nullable Object o) {
				        // nop
				    }
				}
				""";
		Expected e1 = new Expected("Change parameter 'in' to '@NonNull'", str2);
		Expected e2 = new Expected("Change parameter of 'foo(..)' to '@Nullable'", str3);
		assertCodeActions(cu, e1, e2);
	}

	// Bug 405086 - [quick fix] don't propose null annotations when those are disabled
	@Test
	public void testChangeParameter5() throws Exception {
		fJProject1.setOption(JavaCore.COMPILER_ANNOTATION_NULL_ANALYSIS, JavaCore.DISABLED);
		try {
			IPackageFragment pack1 = fSourceFolder.createPackageFragment("test1", false, null);
			String str = """
					package test1;
					public class E {
					    void foo(Object o) {
					        if (o == null) return;
					        if (o != null) System.out.print(o.toString());
					    }
					}
					""";
			ICompilationUnit cu = pack1.createCompilationUnit("E.java", str, false, null);

			assertCodeActionNotExists(cu, "Change parameter 'o' to @Nullable");
			assertCodeActionNotExists(cu, "Change parameter 'o' to @NonNull");
		} finally {
			fJProject1.setOption(JavaCore.COMPILER_ANNOTATION_NULL_ANALYSIS, JavaCore.ENABLED);
		}
	}

	// Bug 405086 - [quick fix] don't propose null annotations when those are disabled
	// don't propose a parameter change if there was no parameter annotation being the cause for the warning
	@Test
	public void testChangeParameter6() throws Exception {
		IPackageFragment pack1 = fSourceFolder.createPackageFragment("test1", false, null);
		String str = """
				package test1;
				public class E {
				    void foo(Object o) {
				        if (o == null) return;
				        if (o != null) System.out.print(o.toString());
				    }
				}
				""";
		ICompilationUnit cu = pack1.createCompilationUnit("E.java", str, false, null);

		assertCodeActionNotExists(cu, "Change parameter 'o' to @Nullable");
		assertCodeActionNotExists(cu, "Change parameter 'o' to @NonNull");
	}

	// Bug 405086 - [quick fix] don't propose null annotations when those are disabled
	// positive case (redundant check)
	@Test
	public void testChangeParameter7() throws Exception {
		IPackageFragment pack1 = fSourceFolder.createPackageFragment("test1", false, null);
		String str = """
				package test1;
				import org.eclipse.jdt.annotation.*;
				public class E {
				    void foo(@NonNull Object o) {
				        if (o != null) System.out.print(o.toString());
				    }
				}
				""";
		ICompilationUnit cu = pack1.createCompilationUnit("E.java", str, false, null);

		String str1 = """
				package test1;
				import org.eclipse.jdt.annotation.*;
				public class E {
				    void foo(@Nullable Object o) {
				        if (o != null) System.out.print(o.toString());
				    }
				}
				""";
		Expected e1 = new Expected("Change parameter 'o' to '@Nullable'", str1);
		assertCodeActions(cu, e1);
	}

	// Bug 405086 - [quick fix] don't propose null annotations when those are disabled
	// positive case 2 (check always false)
	@Test
	public void testChangeParameter8() throws Exception {
		IPackageFragment pack1 = fSourceFolder.createPackageFragment("test1", false, null);
		String str = """
				package test1;
				public class E {
				    void foo(@org.eclipse.jdt.annotation.NonNull Object o) {
				        if (o == null) System.out.print("NOK");
				    }
				}
				""";
		ICompilationUnit cu = pack1.createCompilationUnit("E.java", str, false, null);

		String str1 = """
				package test1;

				import org.eclipse.jdt.annotation.Nullable;

				public class E {
				    void foo(@Nullable Object o) {
				        if (o == null) System.out.print("NOK");
				    }
				}
				""";
		Expected e1 = new Expected("Change parameter 'o' to '@Nullable'", str1);
		assertCodeActions(cu, e1);
	}

	// http://bugs.eclipse.org/395555 - [quickfix] Update null annotation quick fixes for bug 388281
	// conflict between inherited and default nullness
	@Test
	public void testChangeParameter9() throws Exception {
		fJProject1.setOption(JavaCore.COMPILER_INHERIT_NULL_ANNOTATIONS, JavaCore.ENABLED);
		try {
			IPackageFragment pack1 = fSourceFolder.createPackageFragment("test1", false, null);
			String str = """
					package test1;
					import org.eclipse.jdt.annotation.*;
					public class E {
					    void foo(@Nullable Object o) {
					        // nop
					    }
					}
					""";
			pack1.createCompilationUnit("E.java", str, false, null);

			String str1 = """
					package test1;
					import org.eclipse.jdt.annotation.*;
					@NonNullByDefault
					public class E2 extends E {
					    void foo(Object o) {
					        System.out.print("E2");
					    }
					}
					""";
			ICompilationUnit cu = pack1.createCompilationUnit("E2.java", str1, false, null);

			String str2 = """
					package test1;
					import org.eclipse.jdt.annotation.*;
					@NonNullByDefault
					public class E2 extends E {
					    void foo(@Nullable Object o) {
					        System.out.print("E2");
					    }
					}
					""";

			String str3 = """
					package test1;
					import org.eclipse.jdt.annotation.*;
					public class E {
					    void foo(@NonNull Object o) {
					        // nop
					    }
					}
					""";
			Expected e1 = new Expected("Change parameter 'o' to '@Nullable'", str2);
			Expected e2 = new Expected("Change parameter in overridden 'foo(..)' to '@NonNull'", str3);
			assertCodeActions(cu, e1, e2);
		} finally {
			fJProject1.setOption(JavaCore.COMPILER_INHERIT_NULL_ANNOTATIONS, JavaCore.DISABLED);
		}
	}

	// returning @Nullable value from @NonNull method -> change to @Nullable return
	@Test
	public void testChangeReturn1() throws Exception {
		IPackageFragment pack1 = fSourceFolder.createPackageFragment("test1", false, null);
		String str = """
				package test1;
				import org.eclipse.jdt.annotation.*;
				public class E {
				    @NonNull Object foo() {
				        @Nullable Object o = null;
				        return o;
				    }
				}
				""";
		ICompilationUnit cu = pack1.createCompilationUnit("E.java", str, false, null);

		String str1 = """
				package test1;
				import org.eclipse.jdt.annotation.*;
				public class E {
				    @Nullable Object foo() {
				        @Nullable Object o = null;
				        return o;
				    }
				}
				""";
		Expected e1 = new Expected("Change return type of 'foo(..)' to '@Nullable'", str1);
		assertCodeActions(cu, e1);
	}

	@Test
	public void testChangeReturn2a() throws Exception {
		IPackageFragment pack1 = fSourceFolder.createPackageFragment("test1", false, null);
		String str = """
				package test1;
				import org.eclipse.jdt.annotation.*;
				public class E {
				    @NonNull Object foo() {
				        return new Object();
				    }
				}
				""";
		pack1.createCompilationUnit("E.java", str, false, null);

		String str1 = """
				package test1;
				import org.eclipse.jdt.annotation.*;
				public class E2 extends E {
				    @Nullable Object foo() {
				        return new Object();
				    }
				}
				""";
		ICompilationUnit cu = pack1.createCompilationUnit("E2.java", str1, false, null);

		String str2 = """
				package test1;
				import org.eclipse.jdt.annotation.*;
				public class E2 extends E {
				    @NonNull Object foo() {
				        return new Object();
				    }
				}
				""";

		String str3 = """
				package test1;
				import org.eclipse.jdt.annotation.*;
				public class E {
				    @Nullable Object foo() {
				        return new Object();
				    }
				}
				""";
		Expected e1 = new Expected("Change return type of 'foo(..)' to '@NonNull'", str2);
		Expected e2 = new Expected("Change return type of overridden 'foo(..)' to '@Nullable'", str3);
		assertCodeActions(cu, e1, e2);
	}

	@Test
	public void testChangeReturn2b() throws Exception {
		IPackageFragment pack1 = fSourceFolder.createPackageFragment("test1", false, null);
		String str = """
				package test1;
				import org.eclipse.jdt.annotation.*;
				public class E {
				    @NonNull Object foo() {
				        return new Object();
				    }
				}
				""";
		pack1.createCompilationUnit("E.java", str, false, null);

		String str1 = """
				package test1;
				public class E2 extends E {
				    Object foo() {
				        return new Object();
				    }
				}
				""";
		ICompilationUnit cu = pack1.createCompilationUnit("E2.java", str1, false, null);

		String str2 = """
				package test1;

				import org.eclipse.jdt.annotation.NonNull;

				public class E2 extends E {
				    @NonNull
				    Object foo() {
				        return new Object();
				    }
				}
				""";
		Expected e1 = new Expected("Change return type of 'foo(..)' to '@NonNull'", str2);
		assertCodeActions(cu, e1);
	}

	// https://bugs.eclipse.org/395555 - [quickfix] Update null annotation quick fixes for bug 388281
	// conflict between nullness inherited from different parents
	@Test
	public void testChangeReturn3() throws Exception {
		fJProject1.setOption(JavaCore.COMPILER_INHERIT_NULL_ANNOTATIONS, JavaCore.ENABLED);
		try {
			IPackageFragment pack1 = fSourceFolder.createPackageFragment("test1", false, null);
			String str = """
					package test1;
					import org.eclipse.jdt.annotation.*;
					public class E {
					    @NonNull Object foo() {
					        // nop
					    }
					}
					""";
			pack1.createCompilationUnit("E.java", str, false, null);

			String str1 = """
					package test1;
					import org.eclipse.jdt.annotation.*;
					public interface IE {
					    @Nullable Object foo();
					}
					""";
			pack1.createCompilationUnit("IE.java", str1, false, null);

			String str2 = """
					package test1;
					public class E2 extends E implements IE {
					    public Object foo() {
					        return this;
					    }
					}
					""";
			ICompilationUnit cu = pack1.createCompilationUnit("E2.java", str2, false, null);

			String str3 = """
					package test1;

					import org.eclipse.jdt.annotation.Nullable;

					public class E2 extends E implements IE {
					    public @Nullable Object foo() {
					        return this;
					    }
					}
					""";

			String str4 = """
					package test1;

					import org.eclipse.jdt.annotation.NonNull;

					public class E2 extends E implements IE {
					    public @NonNull Object foo() {
					        return this;
					    }
					}
					""";
			Expected e1 = new Expected("Change return type of 'foo(..)' to '@Nullable'", str3);
			Expected e2 = new Expected("Change return type of 'foo(..)' to '@NonNull'", str4);
			assertCodeActions(cu, e1, e2);
		} finally {
			fJProject1.setOption(JavaCore.COMPILER_INHERIT_NULL_ANNOTATIONS, JavaCore.DISABLED);
		}
	}

	// https://bugs.eclipse.org/378724 - Null annotations are extremely hard to use in an existing project
	// see comment 12
	// remove @Nullable without adding redundant @NonNull (due to @NonNullByDefault)
	@Test
	public void testChangeReturn4() throws Exception {
		IPackageFragment pack1 = fSourceFolder.createPackageFragment("test1", false, null);
		String str = """
				package test1;
				import org.eclipse.jdt.annotation.*;
				@NonNullByDefault
				public class E {
				    @Nullable Object bar() {
				        return new Object();
				    }
				}
				""";
		pack1.createCompilationUnit("E.java", str, false, null);

		String str1 = """
				package test1;
				import org.eclipse.jdt.annotation.*;
				public class E2 {
				    @NonNull Object foo(E e) {
				        return e.bar();
				    }
				}
				""";
		ICompilationUnit cu = pack1.createCompilationUnit("E2.java", str1, false, null);

		String str2 = """
				package test1;
				import org.eclipse.jdt.annotation.*;
				public class E2 {
				    @Nullable Object foo(E e) {
				        return e.bar();
				    }
				}
				""";

		String str3 = """
				package test1;
				import org.eclipse.jdt.annotation.*;
				@NonNullByDefault
				public class E {
				    Object bar() {
				        return new Object();
				    }
				}
				""";
		Expected e1 = new Expected("Change return type of 'foo(..)' to '@Nullable'", str2);
		Expected e2 = new Expected("Change return type of 'bar(..)' to '@NonNull'", str3);
		assertCodeActions(cu, e1, e2);
	}

	// https://bugs.eclipse.org/378724 - Null annotations are extremely hard to use in an existing project
	// see comment 12
	// remove @Nullable without adding redundant @NonNull (due to @NonNullByDefault)
	// variant: package-level default
	@Test
	public void testChangeReturn5() throws Exception {
		String suppressOptionalErrors = fJProject1.getOption(JavaCore.COMPILER_PB_SUPPRESS_OPTIONAL_ERRORS, true);
		try {
			fJProject1.setOption(JavaCore.COMPILER_PB_SUPPRESS_OPTIONAL_ERRORS, JavaCore.ENABLED);
			IPackageFragment pack1 = fSourceFolder.createPackageFragment("test1", false, null);

			String str = """
					@org.eclipse.jdt.annotation.NonNullByDefault
					package test1;
					""";
			pack1.createCompilationUnit("package-info.java", str, false, null);

			String str1 = """
					package test1;
					import org.eclipse.jdt.annotation.*;
					public class E {
					    @Nullable Object bar() {
					        return new Object();
					    }
					}
					""";
			pack1.createCompilationUnit("E.java", str1, false, null);

			String str2 = """
					package test1;
					public class E2 {
					    public Object foo(E e) {
					        return e.bar();
					    }
					}
					""";
			ICompilationUnit cu = pack1.createCompilationUnit("E2.java", str2, false, null);

			String str3 = """
					package test1;

					import org.eclipse.jdt.annotation.Nullable;

					public class E2 {
					    public @Nullable Object foo(E e) {
					        return e.bar();
					    }
					}
					""";

			String str4 = """
					package test1;
					import org.eclipse.jdt.annotation.*;
					public class E {
					    Object bar() {
					        return new Object();
					    }
					}
					""";
			Expected e1 = new Expected("Change return type of 'foo(..)' to '@Nullable'", str3);
			Expected e2 = new Expected("Change return type of 'bar(..)' to '@NonNull'", str4);
			assertCodeActions(cu, e1, e2);
		} finally {
			fJProject1.setOption(JavaCore.COMPILER_PB_SUPPRESS_OPTIONAL_ERRORS, suppressOptionalErrors);
		}
	}

	// https://bugs.eclipse.org/378724 - Null annotations are extremely hard to use in an existing project
	// see comment 12
	// remove @Nullable without adding redundant @NonNull (due to @NonNullByDefault)
	// variant: cancelled default
	@Test
	public void testChangeReturn6() throws Exception {
		IPackageFragment pack1 = fSourceFolder.createPackageFragment("test1", false, null);

		String str = """
				@org.eclipse.jdt.annotation.NonNullByDefault
				package test1;
				""";
		pack1.createCompilationUnit("package-info.java", str, false, null);

		String str0 = """
				package test1;
				import org.eclipse.jdt.annotation.*;
				@NonNullByDefault(false)
				public class E {
				    @Nullable Object bar() {
				        return new Object();
				    }
				}
				""";
		pack1.createCompilationUnit("E.java", str0, false, null);

		String str1 = """
				package test1;
				public class E2 {
				    public Object foo(E e) {
				        return e.bar();
				    }
				}
				""";
		ICompilationUnit cu = pack1.createCompilationUnit("E2.java", str1, false, null);

		String str2 = """
				package test1;

				import org.eclipse.jdt.annotation.Nullable;

				public class E2 {
				    public @Nullable Object foo(E e) {
				        return e.bar();
				    }
				}
				""";
		String str3 = """
				package test1;
				import org.eclipse.jdt.annotation.*;
				@NonNullByDefault(false)
				public class E {
				    @NonNull Object bar() {
				        return new Object();
				    }
				}
				""";
		Expected e1 = new Expected("Change return type of 'foo(..)' to '@Nullable'", str2);
		Expected e2 = new Expected("Change return type of 'bar(..)' to '@NonNull'", str3);
		assertCodeActions(cu, e1, e2);
	}

	@Test
	public void testRemoveRedundantAnnotation1() throws Exception {
		IPackageFragment pack1 = fSourceFolder.createPackageFragment("test1", false, null);
		String str = """
				package test1;
				import org.eclipse.jdt.annotation.*;
				@NonNullByDefault
				public class E {
				    void foo(@NonNull Object o) {
				    }
				}
				""";
		ICompilationUnit cu = pack1.createCompilationUnit("E.java", str, false, null);

		String str1 = """
				package test1;
				import org.eclipse.jdt.annotation.*;
				@NonNullByDefault
				public class E {
				    void foo(Object o) {
				    }
				}
				""";
		Expected e1 = new Expected("Remove redundant nullness annotation", str1);
		assertCodeActions(cu, e1);
	}

	@Test
	public void testRemoveRedundantAnnotation2() throws Exception {
		IPackageFragment pack1 = fSourceFolder.createPackageFragment("test1", false, null);
		String str = """
				package test1;
				import org.eclipse.jdt.annotation.*;
				@NonNullByDefault
				public class E {
				    @NonNull
				    Object foo(Object o) {
				        return new Object();
				    }
				}
				""";
		ICompilationUnit cu = pack1.createCompilationUnit("E.java", str, false, null);

		String str1 = """
				package test1;
				import org.eclipse.jdt.annotation.*;
				@NonNullByDefault
				public class E {
				    Object foo(Object o) {
				        return new Object();
				    }
				}
				""";
		Expected e1 = new Expected("Remove redundant nullness annotation", str1);
		assertCodeActions(cu, e1);
	}

	@Test
	public void testRemoveRedundantAnnotation3() throws Exception {
		IPackageFragment pack1 = fSourceFolder.createPackageFragment("test1", false, null);
		String str = """
				package test1;
				import org.eclipse.jdt.annotation.*;
				@NonNullByDefault
				public class E {
				    @NonNull
				    public Object foo(Object o) {
				        return new Object();
				    }
				}
				""";
		ICompilationUnit cu = pack1.createCompilationUnit("E.java", str, false, null);

		String str1 = """
				package test1;
				import org.eclipse.jdt.annotation.*;
				@NonNullByDefault
				public class E {
				    public Object foo(Object o) {
				        return new Object();
				    }
				}
				""";
		Expected e1 = new Expected("Remove redundant nullness annotation", str1);
		assertCodeActions(cu, e1);
	}

	@Test
	public void testRemoveRedundantAnnotation4() throws Exception {
		IPackageFragment pack1 = fSourceFolder.createPackageFragment("test1", false, null);
		String str = """
				package test1;
				import org.eclipse.jdt.annotation.*;
				@NonNullByDefault
				public class E {
				    @NonNullByDefault
				    void foo(Object o) {
				    }
				}
				""";
		ICompilationUnit cu = pack1.createCompilationUnit("E.java", str, false, null);

		String str1 = """
				package test1;
				import org.eclipse.jdt.annotation.*;
				@NonNullByDefault
				public class E {
				    void foo(Object o) {
				    }
				}
				""";
		Expected e1 = new Expected("Remove redundant nullness annotation", str1);
		assertCodeActions(cu, e1);
	}

	@Test
	public void testRemoveRedundantAnnotation5() throws Exception {
		IPackageFragment pack1 = fSourceFolder.createPackageFragment("test1", false, null);
		String str = """
				package test1;
				import org.eclipse.jdt.annotation.*;
				@NonNullByDefault
				public class E {
				    @NonNullByDefault
				    class E1 {
				    }
				}
				""";
		ICompilationUnit cu = pack1.createCompilationUnit("E.java", str, false, null);

		String str1 = """
				package test1;
				import org.eclipse.jdt.annotation.*;
				@NonNullByDefault
				public class E {
				    class E1 {
				    }
				}
				""";
		Expected e1 = new Expected("Remove redundant nullness annotation", str1);
		assertCodeActions(cu, e1);
	}

	@Test
	public void testRemoveRedundantAnnotation6() throws Exception {
		IPackageFragment pack1 = fSourceFolder.createPackageFragment("test1", false, null);
		String str = """
				package test1;
				import org.eclipse.jdt.annotation.*;
				public class E {
				    @NonNullByDefault
				    void foo(Object o) {
				        @NonNullByDefault
				        class E1 {
				        }
				    }
				}
				""";
		ICompilationUnit cu = pack1.createCompilationUnit("E.java", str, false, null);

		String str1 = """
				package test1;
				import org.eclipse.jdt.annotation.*;
				public class E {
				    @NonNullByDefault
				    void foo(Object o) {
				        class E1 {
				        }
				    }
				}
				""";
		Expected e1 = new Expected("Remove redundant nullness annotation", str1);
		assertCodeActions(cu, e1);
	}

	@Test
	public void testRemoveRedundantAnnotation7() throws Exception {
		IPackageFragment pack1 = fSourceFolder.createPackageFragment("test1", false, null);
		String str = """
				@org.eclipse.jdt.annotation.NonNullByDefault
				package test1;
				""";
		pack1.createCompilationUnit("package-info.java", str, false, null);

		String str1 = """
				package test1;
				import org.eclipse.jdt.annotation.*;
				@NonNullByDefault
				public class E {
				}
				""";
		ICompilationUnit cu = pack1.createCompilationUnit("E.java", str1, false, null);

		String str2 = """
				package test1;
				import org.eclipse.jdt.annotation.*;
				public class E {
				}
				""";
		Expected e1 = new Expected("Remove redundant nullness annotation", str2);
		assertCodeActions(cu, e1);
	}

	@Test
	public void testRemoveRedundantAnnotation8() throws Exception {
		IPackageFragment pack1 = fSourceFolder.createPackageFragment("test1", false, null);
		String str = """
				@org.eclipse.jdt.annotation.NonNullByDefault
				package test1;
				""";
		pack1.createCompilationUnit("package-info.java", str, false, null);

		String str1 = """
				package test1;
				import org.eclipse.jdt.annotation.*;
				public class E {
				    @NonNullByDefault
				    void foo(Object o) {
				    }
				}
				""";
		ICompilationUnit cu = pack1.createCompilationUnit("E.java", str1, false, null);

		String str2 = """
				package test1;
				import org.eclipse.jdt.annotation.*;
				public class E {
				    void foo(Object o) {
				    }
				}
				""";
		Expected e1 = new Expected("Remove redundant nullness annotation", str2);
		assertCodeActions(cu, e1);
	}

	@Test
	public void testAddNonNull() throws Exception {
		IPackageFragment pack1 = fSourceFolder.createPackageFragment("test1", false, null);
		String str = """
				package test1;
				public class E {
				    public <T extends Number> double foo(boolean b) {
				        Number n=Integer.valueOf(1);
				        if(b) {
				          n = null;
				        };
				        return n.doubleValue();
				    }
				}
				""";
		ICompilationUnit cu = pack1.createCompilationUnit("E.java", str, false, null);

		String str1 = """
				package test1;

				import org.eclipse.jdt.annotation.NonNull;

				public class E {
				    public <T extends Number> double foo(boolean b) {
				        @NonNull
				        Number n=Integer.valueOf(1);
				        if(b) {
				          n = null;
				        };
				        return n.doubleValue();
				    }
				}
				""";
		Expected e1 = new Expected("Declare 'n' as '@NonNull' to see the root problem", str1);
		assertCodeActions(cu, e1);
	}

	// Attempt to override an unspec'ed argument with a @NonNull argument
	// -> change to @Nullable
	// -> change overridden to @NonNull
	// Specific for this test: arg name is different in overridden method.
	@Test
	public void testBug506108() throws Exception {
		IPackageFragment pack1 = fSourceFolder.createPackageFragment("test1", false, null);
		String str = """
				package test1;
				import org.eclipse.jdt.annotation.*;
				public class E {
				    void foo(Exception e, Exception e1, Exception e2) {
				    }
				}
				""";
		pack1.createCompilationUnit("E.java", str, false, null);

		String str1 = """
				package test1;
				import org.eclipse.jdt.annotation.*;
				public class E2 extends E {
				    void foo(Exception e1, @NonNull Exception e2, Exception e) {
				        e2.printStackTrace();
				    }
				}
				""";
		ICompilationUnit cu = pack1.createCompilationUnit("E2.java", str1, false, null);

		String str2 = """
				package test1;
				import org.eclipse.jdt.annotation.*;
				public class E2 extends E {
				    void foo(Exception e1, @Nullable Exception e2, Exception e) {
				        e2.printStackTrace();
				    }
				}
				""";

		String str3 = """
				package test1;
				import org.eclipse.jdt.annotation.*;
				public class E {
				    void foo(Exception e, @NonNull Exception e1, Exception e2) {
				    }
				}
				""";
		Expected e1 = new Expected("Change parameter 'e2' to '@Nullable'", str2);
		Expected e2 = new Expected("Change parameter in overridden 'foo(..)' to '@NonNull'", str3);
		assertCodeActions(cu, e1, e2);
	}

	@Test
	public void testBug525428a() throws Exception {
		fJProject1.setOption(JavaCore.COMPILER_PB_MISSING_NONNULL_BY_DEFAULT_ANNOTATION, JavaCore.ERROR);
		try {
			IPackageFragment pack1 = fSourceFolder.createPackageFragment("test1", false, null);
			String str = """
					package test1;
					""";
			ICompilationUnit cu = pack1.createCompilationUnit("package-info.java", str, false, null);

			String str1 = """
					@org.eclipse.jdt.annotation.NonNullByDefault
					package test1;
					""";
			Expected e1 = new Expected("Add '@NonNullByDefault' to the package declaration", str1);
			assertCodeActions(cu, e1);
		} finally {
			fJProject1.setOption(JavaCore.COMPILER_PB_MISSING_NONNULL_BY_DEFAULT_ANNOTATION, JavaCore.IGNORE);
		}
	}

	@Test
	public void testBug513423a() throws Exception {
		IPackageFragment pack1 = fSourceFolder.createPackageFragment("test1", false, null);
		String str = """
				package test1;
				import org.eclipse.jdt.annotation.NonNullByDefault;

				@NonNullByDefault
				public class E extends RuntimeException {
					private static final long serialVersionUID = 1L;

					public void printStackTrace(
						// Illegal redefinition of parameter s, inherited method from Throwable
						// does not constrain this parameter
						java.io.PrintStream s) {
							if (s != null) {
								synchronized (s) {
									s.print(getClass().getName() + ": ");
									s.print(getStackTrace());
								}
							}
					}
				}
				""";
		ICompilationUnit cu = pack1.createCompilationUnit("E.java", str, false, null);

		String str1 = """
				package test1;
				import org.eclipse.jdt.annotation.NonNullByDefault;
				import org.eclipse.jdt.annotation.Nullable;

				@NonNullByDefault
				public class E extends RuntimeException {
					private static final long serialVersionUID = 1L;

					public void printStackTrace(
						// Illegal redefinition of parameter s, inherited method from Throwable
						// does not constrain this parameter
						java.io.@Nullable PrintStream s) {
							if (s != null) {
								synchronized (s) {
									s.print(getClass().getName() + ": ");
									s.print(getStackTrace());
								}
							}
					}
				}
				""";
		Expected e1 = new Expected("Change parameter 's' to '@Nullable'", str1);
		assertCodeActions(cu, e1);

	}

	@Test
	public void testBug513423b() throws Exception {
		IPackageFragment pack1 = fSourceFolder.createPackageFragment("test1", false, null);
		String str0 = """
				package test1;

				import static java.lang.annotation.ElementType.TYPE_USE;

				import java.lang.annotation.Documented;
				import java.lang.annotation.ElementType;
				import java.lang.annotation.Retention;
				import java.lang.annotation.RetentinPolicy;
				import java.lang.annotation.Target;

				@Documented
				@Retention(RetentionPolicy.CLASS)
				@Target({ TYPE_USE })
				public @interface SomeAnnotation {
					// marker annotation with no members
				}
				""";
		pack1.createCompilationUnit("SomeAnnotation.java", str0, false, null);

		String str = """
				package test1;
				import org.eclipse.jdt.annotation.NonNullByDefault;

				@NonNullByDefault
				public class E extends RuntimeException {
					private static final long serialVersionUID = 1L;

					public void printStackTrace(
						// Illegal redefinition of parameter s, inherited method from Throwable
						// does not constrain this parameter
						java.io.@SomeAnnotation PrintStream s) {
							if (s != null) {
								synchronized (s) {
									s.print(getClass().getName() + ": ");
									s.print(getStackTrace());
								}
							}
					}
				}
				""";
		ICompilationUnit cu = pack1.createCompilationUnit("E.java", str, false, null);

		String str1 = """
				package test1;
				import org.eclipse.jdt.annotation.NonNullByDefault;
				import org.eclipse.jdt.annotation.Nullable;

				@NonNullByDefault
				public class E extends RuntimeException {
					private static final long serialVersionUID = 1L;

					public void printStackTrace(
						// Illegal redefinition of parameter s, inherited method from Throwable
						// does not constrain this parameter
						java.io.@SomeAnnotation @Nullable PrintStream s) {
							if (s != null) {
								synchronized (s) {
									s.print(getClass().getName() + ": ");
									s.print(getStackTrace());
								}
							}
					}
				}
				""";
		Expected e1 = new Expected("Change parameter 's' to '@Nullable'", str1);
		assertCodeActions(cu, e1);

	}

	@Test
	public void testGH1294() throws Exception {
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
						return new IInputValidator() {
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
						return new IInputValidator() {
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
}
