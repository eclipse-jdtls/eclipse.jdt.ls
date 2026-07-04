/*******************************************************************************
 * Copyright (c) 2026 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Copied from /org.eclipse.jdt.ui.tests/ui/org/eclipse/jdt/ui/tests/quickfix/JavadocQuickFixTest.java
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.jdt.ls.core.internal.correction;

import java.util.Hashtable;

import org.eclipse.core.runtime.preferences.DefaultScope;
import org.eclipse.core.runtime.preferences.IEclipsePreferences;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IPackageFragment;
import org.eclipse.jdt.core.IPackageFragmentRoot;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.internal.core.manipulation.StubUtility;
import org.eclipse.jdt.internal.corext.util.JavaModelUtil;
import org.eclipse.jdt.ls.core.internal.IConstants;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class MarkdownJavadocQuickFixTest extends AbstractQuickFixTest {

	private IJavaProject fJProject1;
	private IPackageFragmentRoot fSourceFolder;

	@BeforeEach
	public void setup() throws Exception {
		fJProject1 = newEmptyProject();
		Hashtable<String, String> options = TestOptions.getDefaultOptions();
		//Map<String, String> options = new HashMap<>(fJProject1.getOptions(false));
		JavaModelUtil.setComplianceOptions(options, JavaCore.VERSION_23);
		options.put(JavaCore.COMPILER_PB_INVALID_JAVADOC, JavaCore.ERROR);
		options.put(JavaCore.COMPILER_PB_INVALID_JAVADOC_TAGS, JavaCore.ENABLED);
		options.put(JavaCore.COMPILER_PB_MISSING_JAVADOC_TAGS_METHOD_TYPE_PARAMETERS, JavaCore.ENABLED);
		options.put(JavaCore.COMPILER_PB_MISSING_JAVADOC_TAGS, JavaCore.ERROR);
		options.put(JavaCore.COMPILER_PB_MISSING_JAVADOC_COMMENTS, JavaCore.ERROR);
		options.put(JavaCore.COMPILER_PB_MISSING_JAVADOC_COMMENTS_OVERRIDING, JavaCore.ENABLED);

		fJProject1.setOptions(options);

		fSourceFolder = fJProject1.getPackageFragmentRoot(fJProject1.getProject().getFolder("src"));
		IEclipsePreferences defEclipsePrefs = DefaultScope.INSTANCE.getNode(IConstants.PLUGIN_ID);
		defEclipsePrefs.put(StubUtility.CODEGEN_USE_MARKDOWN, Boolean.TRUE.toString());
	}

	@Override
	@AfterEach
	public void cleanUp() throws Exception {
		super.cleanUp();
		IEclipsePreferences defEclipsePrefs = DefaultScope.INSTANCE.getNode(IConstants.PLUGIN_ID);
		defEclipsePrefs.put(StubUtility.CODEGEN_USE_MARKDOWN, Boolean.FALSE.toString());
	}

	@Test
	public void testAddMarkdownJavadocForOverriddenMethodInRecord() throws Exception {
		IPackageFragment pack1 = fSourceFolder.createPackageFragment("test1", false, null);

		String source = """
				package test1;

				/**
				 */
				public class Person5 {
				    @Override
				    public String toString() {
				        return name + " (" + age + ")";
				    }
				}
				""";

		ICompilationUnit cu = pack1.createCompilationUnit("Person5.java", source, false, null);

		String expected = """
				package test1;

				/**
				 */
				public class Person5 {
				    /// (non-Javadoc)
				    /// @see java.lang.Object#toString()
				    @Override
				    public String toString() {
				        return name + " (" + age + ")";
				    }
				}
				""";

		Expected e1 = new Expected("Add Javadoc comment", expected);
		assertCodeActions(cu, e1);
	}

	@Test
	public void testMarkdownMissingMethodComment() throws Exception {
		IPackageFragment pack1 = fSourceFolder.createPackageFragment("test1", false, null);
		String source = """
				package test1;
				import java.io.IOException;
				/**
				 */
				public class E {
				    public <A> void foo(int a) throws IOException {
				    }
				}
				""";
		ICompilationUnit cu = pack1.createCompilationUnit("E.java", source, false, null);

		String expected = """
				package test1;
				import java.io.IOException;
				/**
				 */
				public class E {
				    /// @param <A>
				    /// @param a
				    /// @throws IOException
				    public <A> void foo(int a) throws IOException {
				    }
				}
				""";
		Expected e1 = new Expected("Add Javadoc comment", expected);
		assertCodeActions(cu, e1);
	}

	@Test
	public void testMarkdownMissingMethodCommentNoParams() throws Exception {
		IPackageFragment pack1 = fSourceFolder.createPackageFragment("test1", false, null);
		String source = """
				package test1;
				/**
				 * Some comment
				 */
				public class E {
				    public void empty() {
				    }
				}
				""";
		ICompilationUnit cu = pack1.createCompilationUnit("E.java", source, false, null);

		String expected = """
				package test1;
				/**
				 * Some comment
				 */
				public class E {
				    ///\s
				    public void empty() {
				    }
				}
				""";
		Expected e1 = new Expected("Add Javadoc comment", expected);
		assertCodeActions(cu, e1);
	}

	@Test
	public void testMarkdownMissingConstructorComment() throws Exception {
		IPackageFragment pack1 = fSourceFolder.createPackageFragment("test1", false, null);
		String source = """
				package test1;
				import java.io.IOException;
				/**
				 */
				public class E {
				    public E(int a) throws IOException {
				    }
				}
				""";
		ICompilationUnit cu = pack1.createCompilationUnit("E.java", source, false, null);

		String expected = """
				package test1;
				import java.io.IOException;
				/**
				 */
				public class E {
				    /// @param a
				    /// @throws IOException
				    public E(int a) throws IOException {
				    }
				}
				""";
		Expected e1 = new Expected("Add Javadoc comment", expected);
		assertCodeActions(cu, e1);
	}

	@Test
	public void testMarkdownAddSimpleTypeComment() throws Exception {
		IPackageFragment pack1 = fSourceFolder.createPackageFragment("test1", false, null);
		String source = """
				package test1;
				public class MyClass {
				}
				""";
		ICompilationUnit cu = pack1.createCompilationUnit("MyClass.java", source, false, null);

		String expected = """
				package test1;
				/// MyClass
				public class MyClass {
				}
				""";
		Expected e1 = new Expected("Add Javadoc comment", expected);
		assertCodeActions(cu, e1);
	}

	@Test
	public void testMarkdownMissingTypeComment() throws Exception {
		IPackageFragment pack1 = fSourceFolder.createPackageFragment("test1", false, null);
		String source = """
				package test1;
				public class E<A, B> {
				}
				""";
		ICompilationUnit cu = pack1.createCompilationUnit("E.java", source, false, null);

		String expected = """
				package test1;
				/// E
				/// @param <A>
				/// @param <B>
				public class E<A, B> {
				}
				""";
		Expected e1 = new Expected("Add Javadoc comment", expected);
		assertCodeActions(cu, e1);
	}

	@Test
	public void testMarkdownMissingTypeCommentWithoutParams() throws Exception {
		IPackageFragment pack1 = fSourceFolder.createPackageFragment("test1", false, null);
		String source = """
				package test1;
				public class E {
				}
				""";
		ICompilationUnit cu = pack1.createCompilationUnit("E.java", source, false, null);

		String expected = """
				package test1;
				/// E
				public class E {
				}
				""";
		Expected e1 = new Expected("Add Javadoc comment", expected);
		assertCodeActions(cu, e1);
	}

	@Test
	public void testMarkdownMissingFieldComment() throws Exception {
		this.setIgnoredCommands("Extract.*");
		IPackageFragment pack1 = fSourceFolder.createPackageFragment("test1", false, null);
		String source = """
				package test1;
				/**
				 */
				public class E {
				    public static final int COLOR= 1;
				}
				""";
		ICompilationUnit cu = pack1.createCompilationUnit("E.java", source, false, null);

		String expected = """
				package test1;
				/**
				 */
				public class E {
				    ///\s
				    public static final int COLOR= 1;
				}
				""";
		Expected e1 = new Expected("Add Javadoc comment", expected);
		assertCodeActions(cu, e1);
	}

	@Test
	public void testMarkdownAddRecordCommentWithGenerics() throws Exception {
		IPackageFragment pack1 = fSourceFolder.createPackageFragment("test1", false, null);
		String source = """
				package test1;
				public record Container<T>(T value, String label) {
				}
				""";
		ICompilationUnit cu = pack1.createCompilationUnit("Container.java", source, false, null);

		String expected = """
				package test1;
				/// Container
				/// @param <T>
				/// @param value
				/// @param label
				public record Container<T>(T value, String label) {
				}
				""";
		Expected e1 = new Expected("Add Javadoc comment", expected);
		assertCodeActions(cu, e1);
	}

	@Test
	public void testMarkdownAddJavadocForOverriddenMethod() throws Exception {
		IPackageFragment pack1 = fSourceFolder.createPackageFragment("test1", false, null);
		String source = """
				package test1;
				/**
				 */
				public class E {
				    @Override
				    public String toString() {
				        return "E []";
				    }
				}
				""";
		ICompilationUnit cu = pack1.createCompilationUnit("E.java", source, false, null);

		String expected = """
				package test1;
				/**
				 */
				public class E {
				    /// (non-Javadoc)
				    /// @see java.lang.Object#toString()
				    @Override
				    public String toString() {
				        return "E []";
				    }
				}
				""";
		Expected e1 = new Expected("Add Javadoc comment", expected);
		assertCodeActions(cu, e1);
	}

	@Test
	public void testMarkdownAddJavadocForOverriddenMethodInRecord() throws Exception {
		IPackageFragment pack1 = fSourceFolder.createPackageFragment("test1", false, null);
		String source = """
				package test1;

				/**
				 */
				public record Person(String name, int age) {
				    @Override
				    public String toString() {
				        return name + " (" + age + ")";
				    }
				}
				""";
		ICompilationUnit cu = pack1.createCompilationUnit("Person.java", source, false, null);

		String expected = """
				package test1;

				/**
				 */
				public record Person(String name, int age) {
				    /// (non-Javadoc)
				    /// @see java.lang.Record#toString()
				    @Override
				    public String toString() {
				        return name + " (" + age + ")";
				    }
				}
				""";
		Expected e1 = new Expected("Add Javadoc comment", expected);
		assertCodeActions(cu, e1);
	}

	@Test
	public void testMarkdownNoJavadocActionWhenParentDocExists() throws Exception {
		IPackageFragment pack1 = fSourceFolder.createPackageFragment("pack", false, null);
		String source = """
				package pack;

				/**
				 */
				public class B extends A<Integer> {
				    public void foo(Integer x) {
				    }
				}
				class A<T extends Number> {
				    /// @param x
				    public void foo(T x) {
				    }
				}
				""";
		ICompilationUnit cu = pack1.createCompilationUnit("B.java", source, false, null);
		assertCodeActionNotExists(cu, "Add Javadoc for 'foo'");
	}

	@Test
	public void testMissingParam1() throws Exception {
		IPackageFragment pack1 = fSourceFolder.createPackageFragment("test1", false, null);
		String source = """
				package test1;
				/**
				 */
				public class E {
				    /// @param b
				    ///     comment on second line.
				    /// @param c
				    public void foo(int a, int b, int c) {
				    }
				}
				""";
		ICompilationUnit cu = pack1.createCompilationUnit("E.java", source, false, null);

		String expected = """
				package test1;
				/**
				 */
				public class E {
				    /// @param a\s
				    /// @param b
				    ///     comment on second line.
				    /// @param c
				    public void foo(int a, int b, int c) {
				    }
				}
				""";
		Expected e1 = new Expected("Add all missing tags", expected);
		Expected e2 = new Expected("Add '@param' tag", expected);
		assertCodeActions(cu, e1, e2);
	}

	@Test
	public void testMissingParam5() throws Exception {
		IPackageFragment pack1 = fSourceFolder.createPackageFragment("test1", false, null);
		String source = """
				package test1;
				/// @param <B> Hello
				public class E<A, B> {
				}
				""";
		ICompilationUnit cu = pack1.createCompilationUnit("E.java", source, false, null);

		String expected = """
				package test1;
				/// @param <A>\s
				/// @param <B> Hello
				public class E<A, B> {
				}
				""";
		Expected e1 = new Expected("Add all missing tags", expected);
		Expected e2 = new Expected("Add '@param' tag", expected);
		assertCodeActions(cu, e1, e2);
	}

	@Test
	public void testMissingReturn() throws Exception {
		IPackageFragment pack1 = fSourceFolder.createPackageFragment("test1", false, null);
		String source = """
				package test1;
				/**
				 */
				public class E {
				    ///
				    public int foo() {
				        return 1;
				    }
				}
				""";
		ICompilationUnit cu = pack1.createCompilationUnit("E.java", source, false, null);

		String expected = """
				package test1;
				/**
				 */
				public class E {
				    ///
				    /// @return\s
				    public int foo() {
				        return 1;
				    }
				}
				""";
		Expected e1 = new Expected("Add all missing tags", expected);
		Expected e2 = new Expected("Add '@return' tag", expected);
		assertCodeActions(cu, e1, e2);
	}

	@Test
	public void testMissingReturn2() throws Exception {
		IPackageFragment pack1 = fSourceFolder.createPackageFragment("test1", false, null);
		String source = """
				package test1;
				/**
				 */
				public class E {
				    /// @throws Exception
				    public int foo() throws Exception {
				        return 1;
				    }
				}
				""";
		ICompilationUnit cu = pack1.createCompilationUnit("E.java", source, false, null);

		String expected = """
				package test1;
				/**
				 */
				public class E {
				    /// @return\s
				    /// @throws Exception
				    public int foo() throws Exception {
				        return 1;
				    }
				}
				""";
		Expected e1 = new Expected("Add all missing tags", expected);
		Expected e2 = new Expected("Add '@return' tag", expected);
		assertCodeActions(cu, e1, e2);
	}

	@Test
	public void testInsertAllMissing() throws Exception {
		IPackageFragment pack1 = fSourceFolder.createPackageFragment("test1", false, null);
		String source = """
				package test1;
				/**
				 */
				public class E {
				    /// @throws Exception
				    public int foo(int a, int b) throws NullPointerException, Exception {
				        return 1;
				    }
				}
				""";
		ICompilationUnit cu = pack1.createCompilationUnit("E.java", source, false, null);

		String expected = """
				package test1;
				/**
				 */
				public class E {
				    /// @param a\s
				    /// @param b\s
				    /// @return\s
				    /// @throws NullPointerException\s
				    /// @throws Exception
				    public int foo(int a, int b) throws NullPointerException, Exception {
				        return 1;
				    }
				}
				""";
		Expected e1 = new Expected("Add all missing tags", expected);
		assertCodeActionExists(cu, e1);
	}

	@Test
	public void testRemoveParamTag() throws Exception {
		IPackageFragment pack1 = fSourceFolder.createPackageFragment("test1", false, null);
		String source = """
				package test1;
				/**
				 */
				public class E {
				    /// @param a
				    ///      comment on second line.
				    /// @param c
				    public void foo(int c) {
				    }
				}
				""";
		ICompilationUnit cu = pack1.createCompilationUnit("E.java", source, false, null);

		String expected = """
				package test1;
				/**
				 */
				public class E {
				    /// @param c
				    public void foo(int c) {
				    }
				}
				""";
		Expected e1 = new Expected("Remove tag", expected);
		assertCodeActions(cu, e1);
	}

	@Test
	public void testRemoveThrowsTag() throws Exception {
		IPackageFragment pack1 = fSourceFolder.createPackageFragment("test1", false, null);
		String source = """
				package test1;
				/**
				 */
				public class E {
				    /// @param a
				    ///      comment on second line.
				    /// @param c
				    /// @throws Exception Thrown by surprise.
				    public void foo(int a, int c) {
				    }
				}
				""";
		ICompilationUnit cu = pack1.createCompilationUnit("E.java", source, false, null);

		String expected = """
				package test1;
				/**
				 */
				public class E {
				    /// @param a
				    ///      comment on second line.
				    /// @param c
				    public void foo(int a, int c) {
				    }
				}
				""";
		Expected e1 = new Expected("Remove tag", expected);
		assertCodeActions(cu, e1);
	}

	@Test
	public void testInvalidQualification() throws Exception {
		IPackageFragment pack1 = fSourceFolder.createPackageFragment("pack", false, null);
		String source1 = """
				package pack;

				public class A {
				    public static class B {
				    }
				}
				""";
		pack1.createCompilationUnit("A.java", source1, false, null);

		IPackageFragment pack2 = fSourceFolder.createPackageFragment("pack2", false, null);
		String source2 = """
				package pack2;

				import pack.A;

				/// {@link A.B}\s
				public class E {
				}
				""";
		ICompilationUnit cu = pack2.createCompilationUnit("E.java", source2, false, null);

		String expected = """
				package pack2;

				import pack.A;

				/// {@link pack.A.B}\s
				public class E {
				}
				""";
		Expected e1 = new Expected("Qualify inner type name", expected);
		assertCodeActions(cu, e1);
	}

}