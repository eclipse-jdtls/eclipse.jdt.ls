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

/**
 * Tests against projects with 1.8 compliance and "old" declaration null
 * annotations. Those tests are made to run on Java Spider 1.8 .
 */
public class NullAnnotationsQuickFix1d8MixTest extends AbstractQuickFixTest {

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
			ANNOTATION_JAR_PATH = getAnnotationLibPath();
		}
		JavaProjectHelper.addLibrary(fJProject1, new Path(ANNOTATION_JAR_PATH));

		fSourceFolder = fJProject1.getPackageFragmentRoot(fJProject1.getProject().getFolder("src"));
	}

	public static String getAnnotationLibPath() throws IOException {
		URL libEntry = Platform.getBundle("org.eclipse.jdt.ls.tests").getEntry("/testresources/org.eclipse.jdt.annotation_2.4.100.v20251017-1955.jar");
		return FileLocator.toFileURL(libEntry).getPath();
	}


	// ==== Problem:	unchecked conversion, type elided lambda arg
	// ==== Fixes:		change downstream method parameter to @Nullable
	//					add @SW("null")

	@Test
	public void testBug473068_elided() throws Exception {
		IPackageFragment pack1 = fSourceFolder.createPackageFragment("testNullAnnotations", false, null);
		String str = """
				package testNullAnnotations;

				import org.eclipse.jdt.annotation.NonNull;

				interface Consumer<T> {
				    void accept(T t);
				}
				public class Snippet {
				\t
					public void select(final double min, final double max) {
					    doStuff(0, 1, min, max, (data) -> updateSelectionData(data));
					}
				\t
					private void doStuff(int a, int b, final double min, final double max, Consumer<Object> postAction) {

					}
				    private void updateSelectionData(final @NonNull Object data) {
				    }
				}
				""";

		ICompilationUnit cu = pack1.createCompilationUnit("Snippet.java", str, false, null);

		String str1 = """
				package testNullAnnotations;

				import org.eclipse.jdt.annotation.NonNull;
				import org.eclipse.jdt.annotation.Nullable;

				interface Consumer<T> {
				    void accept(T t);
				}
				public class Snippet {
				\t
					public void select(final double min, final double max) {
					    doStuff(0, 1, min, max, (data) -> updateSelectionData(data));
					}
				\t
					private void doStuff(int a, int b, final double min, final double max, Consumer<Object> postAction) {

					}
				    private void updateSelectionData(final @Nullable Object data) {
				    }
				}
				""";

		String str2 = """
				package testNullAnnotations;

				import org.eclipse.jdt.annotation.NonNull;

				interface Consumer<T> {
				    void accept(T t);
				}
				public class Snippet {
				\t
					@SuppressWarnings("null")
				    public void select(final double min, final double max) {
					    doStuff(0, 1, min, max, (data) -> updateSelectionData(data));
					}
				\t
					private void doStuff(int a, int b, final double min, final double max, Consumer<Object> postAction) {

					}
				    private void updateSelectionData(final @NonNull Object data) {
				    }
				}
				""";
		Expected e1 = new Expected("Change parameter of 'updateSelectionData(..)' to '@Nullable'", str1);
		Expected e2 = new Expected("Add @SuppressWarnings 'null' to 'select()'", str2);
		assertCodeActions(cu, e1, e2);
	}

	// ==== Problem:	unchecked conversion, explicitly typed lambda arg
	// ==== Fixes:		annotate lambda arg (@NonNull)
	//					change downstream method parameter to @Nullable
	//					add @SW("null")

	@Test
	public void testBug473068_explicit_type() throws Exception {
		IPackageFragment pack1 = fSourceFolder.createPackageFragment("testNullAnnotations", false, null);
		String str = """
				package testNullAnnotations;

				import org.eclipse.jdt.annotation.NonNull;

				interface Consumer<T> {
				    void accept(T t);
				}
				public class Snippet {
				\t
					public void select(final double min, final double max) {
					    doStuff(0, 1, min, max, (Object data) -> updateSelectionData(data));
					}
				\t
					private void doStuff(int a, int b, final double min, final double max, Consumer<Object> postAction) {

					}
				    private void updateSelectionData(final @NonNull Object data) {
				    }
				}
				""";
		ICompilationUnit cu = pack1.createCompilationUnit("Snippet.java", str, false, null);

		String str1 = """
				package testNullAnnotations;

				import org.eclipse.jdt.annotation.NonNull;

				interface Consumer<T> {
				    void accept(T t);
				}
				public class Snippet {
				\t
					public void select(final double min, final double max) {
					    doStuff(0, 1, min, max, (@NonNull Object data) -> updateSelectionData(data));
					}
				\t
					private void doStuff(int a, int b, final double min, final double max, Consumer<Object> postAction) {

					}
				    private void updateSelectionData(final @NonNull Object data) {
				    }
				}
				""";

		String str2 = """
				package testNullAnnotations;

				import org.eclipse.jdt.annotation.NonNull;
				import org.eclipse.jdt.annotation.Nullable;

				interface Consumer<T> {
				    void accept(T t);
				}
				public class Snippet {
				\t
					public void select(final double min, final double max) {
					    doStuff(0, 1, min, max, (Object data) -> updateSelectionData(data));
					}
				\t
					private void doStuff(int a, int b, final double min, final double max, Consumer<Object> postAction) {

					}
				    private void updateSelectionData(final @Nullable Object data) {
				    }
				}
				""";

		String str3 = """
				package testNullAnnotations;

				import org.eclipse.jdt.annotation.NonNull;

				interface Consumer<T> {
				    void accept(T t);
				}
				public class Snippet {
				\t
					@SuppressWarnings("null")
				    public void select(final double min, final double max) {
					    doStuff(0, 1, min, max, (Object data) -> updateSelectionData(data));
					}
				\t
					private void doStuff(int a, int b, final double min, final double max, Consumer<Object> postAction) {

					}
				    private void updateSelectionData(final @NonNull Object data) {
				    }
				}
				""";
		Expected e1 = new Expected("Change parameter 'data' to '@NonNull'", str1);
		Expected e2 = new Expected("Change parameter of 'updateSelectionData(..)' to '@Nullable'", str2);
		Expected e3 = new Expected("Add @SuppressWarnings 'null' to 'select()'", str3);
		assertCodeActions(cu, e1, e2, e3);
	}
}

