/*******************************************************************************
 * Copyright (c) 2026 IBM Corporation and others.
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
 *     Red Hat Ltd - modified for usage in jdt.ls
 *******************************************************************************/
package org.eclipse.jdt.ls.core.internal.correction;

import java.util.HashMap;
import java.util.Map;

import org.eclipse.jdt.core.IClasspathAttribute;
import org.eclipse.jdt.core.IClasspathEntry;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IPackageFragment;
import org.eclipse.jdt.core.IPackageFragmentRoot;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.formatter.DefaultCodeFormatterConstants;
import org.eclipse.jdt.internal.core.ClasspathAttribute;
import org.eclipse.jdt.internal.corext.util.JavaModelUtil;
import org.eclipse.jdt.ls.core.internal.JavaProjectHelper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class NullAnnotationsQuickFix9Test extends AbstractQuickFixTest {

	private IJavaProject fJProject1;

	private IJavaProject fJProject2;

	private IPackageFragmentRoot fSourceFolder;

	@BeforeEach
	public void setUp() throws Exception {
		fJProject2 = newEmptyProject("annots");
		Map<String, String> options9 = new HashMap<>(fJProject2.getOptions(false));
		JavaModelUtil.setComplianceOptions(options9, JavaCore.VERSION_9);
		fJProject2.setOptions(options9);

		IPackageFragmentRoot sourceFolder = fJProject2.getPackageFragmentRoot(fJProject2.getProject().getFolder("src"));
		IPackageFragment def = sourceFolder.createPackageFragment("", false, null);
		String str = """
				module annots {
				     exports annots;\s
				}
				""";
		def.createCompilationUnit("module-info.java", str, false, null);

		IPackageFragment annots = sourceFolder.createPackageFragment("annots", false, null);
		String str1 = """
				package annots;

				import java.lang.annotation.ElementType;
				import java.lang.annotation.Target;

				@Target(ElementType.TYPE_USE)
				public @interface Nullable {
				}
				""";
		annots.createCompilationUnit("Nullable.java", str1, false, null);

		String str2 = """
				package annots;

				import java.lang.annotation.ElementType;
				import java.lang.annotation.Target;

				@Target(ElementType.TYPE_USE)
				public @interface NonNull {
				}
				""";
		annots.createCompilationUnit("NonNull.java", str2, false, null);

		String str3 = """
				package annots;

				public enum DefaultLocation {
					PARAMETER, RETURN_TYPE, FIELD, TYPE_BOUND, TYPE_ARGUMENT
				}
				""";
		annots.createCompilationUnit("DefaultLocation.java", str3, false, null);

		String str4 = """
				package annots;

				import static annots.DefaultLocation.*;

				public @interface NonNullByDefault {
					DefaultLocation[] value() default { PARAMETER, RETURN_TYPE, FIELD, TYPE_BOUND, TYPE_ARGUMENT };
				}
				""";
		annots.createCompilationUnit("NonNullByDefault.java", str4, false, null);

		fJProject1 = newEmptyProject("TestProject1");
		Map<String, String> options9_1 = new HashMap<>(fJProject1.getOptions(false));
		JavaModelUtil.setComplianceOptions(options9_1, JavaCore.VERSION_9);
		fJProject1.setOptions(options9_1);

		Map<String, String> options = fJProject1.getOptions(false);
		options.put(JavaCore.COMPILER_ANNOTATION_NULL_ANALYSIS, JavaCore.ENABLED);
		options.put(JavaCore.COMPILER_NULLABLE_ANNOTATION_NAME, "annots.Nullable");
		options.put(JavaCore.COMPILER_NONNULL_ANNOTATION_NAME, "annots.NonNull");
		options.put(JavaCore.COMPILER_NONNULL_BY_DEFAULT_ANNOTATION_NAME, "annots.NonNullByDefault");
		options.put(DefaultCodeFormatterConstants.FORMATTER_TAB_CHAR, JavaCore.SPACE);
		options.put(DefaultCodeFormatterConstants.FORMATTER_TAB_SIZE, "4");
		options.put(DefaultCodeFormatterConstants.FORMATTER_NUMBER_OF_EMPTY_LINES_TO_PRESERVE, String.valueOf(99));
		fJProject1.setOptions(options);

		IClasspathAttribute[] attributes = { new ClasspathAttribute(IClasspathAttribute.MODULE, "true") };
		IClasspathEntry cpe = JavaCore.newProjectEntry(fJProject2.getProject().getFullPath(), null, false, attributes, false);
		JavaProjectHelper.addToClasspath(fJProject1, cpe);

		fSourceFolder = fJProject1.getPackageFragmentRoot(fJProject1.getProject().getFolder("src"));
	}

	@Test
	public void testBug530580a() throws Exception {
		String str = """
				@annots.NonNullByDefault module test {
				 requires annots;\
				}
				""";
		IPackageFragment def = fSourceFolder.createPackageFragment("", false, null);
		def.createCompilationUnit("module-info.java", str, false, null);

		IPackageFragment pack1 = fSourceFolder.createPackageFragment("test1", false, null);

		String str1 = """
				package test1;
				import java.util.Map;
				import annots.*;

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
				import annots.*;

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
				import annots.*;

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
				import annots.*;

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

	@Test
	public void testBug530580b() throws Exception {
		String str = """
				@annots.NonNullByDefault module test {
				 requires annots;\
				}
				""";
		IPackageFragment def = fSourceFolder.createPackageFragment("", false, null);
		def.createCompilationUnit("module-info.java", str, false, null);

		IPackageFragment pack1 = fSourceFolder.createPackageFragment("test1", false, null);

		String str1 = """
				package test1;
				import annots.*;

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
				import annots.*;

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
				import annots.*;

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
}

