/*******************************************************************************
 * Copyright (c) 2017-2018 Red Hat Inc. and others.
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
package org.eclipse.jdt.ls.core.internal.handlers;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.net.URL;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.core.runtime.FileLocator;
import org.eclipse.core.runtime.Platform;
import org.eclipse.core.runtime.URIUtil;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.formatter.DefaultCodeFormatterConstants;
import org.eclipse.jdt.internal.ui.preferences.formatter.ProfileVersionerCore;
import org.eclipse.jdt.ls.core.internal.JDTUtils;
import org.eclipse.jdt.ls.core.internal.JavaLanguageServerTestPlugin;
import org.eclipse.jdt.ls.core.internal.ResourceUtils;
import org.eclipse.jdt.ls.core.internal.TextEditUtil;
import org.eclipse.jdt.ls.core.internal.managers.StandardProjectsManager;
import org.eclipse.lsp4j.DocumentFormattingParams;
import org.eclipse.lsp4j.DocumentOnTypeFormattingParams;
import org.eclipse.lsp4j.DocumentRangeFormattingParams;
import org.eclipse.lsp4j.FormattingOptions;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.eclipse.lsp4j.TextEdit;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;
import org.osgi.framework.Bundle;

/**
 * @author Fred Bricon
 */
@RunWith(MockitoJUnitRunner.class)
public class FormatterHandlerTest extends AbstractCompilationUnitBasedTest {

	private IJavaProject javaProject;

	private String originalTabChar;

	@Before
	public void setUp() {
		javaProject = JavaCore.create(project);
		originalTabChar = javaProject.getOption(DefaultCodeFormatterConstants.FORMATTER_TAB_CHAR, true);
		javaProject.setOption(DefaultCodeFormatterConstants.FORMATTER_TAB_CHAR, JavaCore.TAB);
	}

	@Test
	public void testDocumentFormatting() throws Exception {
		ICompilationUnit unit = getWorkingCopy("src/org/sample/Baz.java",
		//@formatter:off
			"package org.sample   ;\n\n" +
			"      public class Baz {  String name;}\n"
		//@formatter:on
		);

		String uri = JDTUtils.toURI(unit);
		TextDocumentIdentifier textDocument = new TextDocumentIdentifier(uri);
		FormattingOptions options = new FormattingOptions(4, true);// ident == 4 spaces
		DocumentFormattingParams params = new DocumentFormattingParams(textDocument, options);
		List<? extends TextEdit> edits = server.formatting(params).get();
		assertNotNull(edits);

		//@formatter:off
		String expectedText =
			"package org.sample;\n"
			+ "\n"
			+ "public class Baz {\n"
			+ "    String name;\n"
			+ "}\n";
		//@formatter:on

		String newText = TextEditUtil.apply(unit, edits);
		assertEquals(expectedText, newText);
	}

	@Test
	public void testJavaFormatEnable() throws Exception {
		String text =
		//@formatter:off
				"package org.sample   ;\n\n" +
				"      public class Baz {  String name;}\n";
			//@formatter:on"
		ICompilationUnit unit = getWorkingCopy("src/org/sample/Baz.java", text);
		preferences.setJavaFormatEnabled(false);
		String uri = JDTUtils.toURI(unit);
		TextDocumentIdentifier textDocument = new TextDocumentIdentifier(uri);
		FormattingOptions options = new FormattingOptions(4, true);// ident == 4 spaces
		DocumentFormattingParams params = new DocumentFormattingParams(textDocument, options);
		List<? extends TextEdit> edits = server.formatting(params).get();
		assertNotNull(edits);
		String newText = TextEditUtil.apply(unit, edits);
		assertEquals(text, newText);
	}

	@Test
	public void testDocumentFormattingWithTabs() throws Exception {
		javaProject.setOption(DefaultCodeFormatterConstants.FORMATTER_TAB_CHAR, JavaCore.SPACE);

		ICompilationUnit unit = getWorkingCopy("src/org/sample/Baz.java",
		//@formatter:off
			"package org.sample;\n\n" +
			"public class Baz {\n"+
			"    void foo(){\n"+
			"}\n"+
			"}\n"
		//@formatter:on
		);

		String uri = JDTUtils.toURI(unit);
		TextDocumentIdentifier textDocument = new TextDocumentIdentifier(uri);
		FormattingOptions options = new FormattingOptions(2, false);// ident == tab
		DocumentFormattingParams params = new DocumentFormattingParams(textDocument, options);
		List<? extends TextEdit> edits = server.formatting(params).get();
		assertNotNull(edits);

		//@formatter:off
		String expectedText =
			"package org.sample;\n"+
			"\n"+
			"public class Baz {\n"+
			"\tvoid foo() {\n"+
			"\t}\n"+
			"}\n";
		//@formatter:on
		String newText = TextEditUtil.apply(unit, edits);
		assertEquals(expectedText, newText);
	}

	@Test
	public void testFormatting_onOffTags() throws Exception {
		ICompilationUnit unit = getWorkingCopy("src/org/sample/Baz.java",
		//@formatter:off
			"package org.sample;\n\n" +
			"      public class Baz {\n"+
			"// @formatter:off\n"+
			"\tvoid foo(){\n"+
			"    }\n"+
			"// @formatter:on\n"+
			"}\n"
		//@formatter:off
		);

		String uri = JDTUtils.toURI(unit);
		TextDocumentIdentifier textDocument = new TextDocumentIdentifier(uri);
		FormattingOptions options = new FormattingOptions(4, false);// ident == tab
		DocumentFormattingParams params = new DocumentFormattingParams(textDocument, options);
		List<? extends TextEdit> edits = server.formatting(params).get();
		assertNotNull(edits);

		//@formatter:off
		String expectedText =
			"package org.sample;\n\n" +
			"public class Baz {\n"+
			"// @formatter:off\n"+
			"\tvoid foo(){\n"+
			"    }\n"+
			"// @formatter:on\n"+
			"}\n";
		//@formatter:on

		String newText = TextEditUtil.apply(unit, edits);
		assertEquals(expectedText, newText);

	}

	@Test
	public void testFormatting_indent_switchstatementsDefault() throws Exception {
		ICompilationUnit unit = getWorkingCopy("src/org/sample/Baz.java",
		//@formatter:off
			"package org.sample;\n" +
			"\n" +
			"public class Baz {\n" +
			"    private enum Numbers {One, Two};\n" +
			"    public void foo() {\n" +
			"        Numbers n = Numbers.One;\n" +
			"        switch (n) {\n" +
			"        case One:\n" +
			"        return;\n" +
			"        case Two:\n" +
			"        return;\n" +
			"        default:\n" +
			"        break;\n" +
			"        }\n" +
			"    }\n" +
			"}"
		//@formatter:off
		);
		String uri = JDTUtils.toURI(unit);
		TextDocumentIdentifier textDocument = new TextDocumentIdentifier(uri);
		FormattingOptions options = new FormattingOptions(4, true);
		DocumentFormattingParams params = new DocumentFormattingParams(textDocument, options);
		List<? extends TextEdit> edits = server.formatting(params).get();
		assertNotNull(edits);
		//@formatter:off
		String expectedText =
			"package org.sample;\n" +
			"\n" +
			"public class Baz {\n" +
			"    private enum Numbers {\n" +
			"        One, Two\n" +
			"    };\n" +
			"\n" +
			"    public void foo() {\n" +
			"        Numbers n = Numbers.One;\n" +
			"        switch (n) {\n" +
			"            case One:\n" +
			"                return;\n" +
			"            case Two:\n" +
			"                return;\n" +
			"            default:\n" +
			"                break;\n" +
			"        }\n" +
			"    }\n" +
			"}";
		//@formatter:on
		String newText = TextEditUtil.apply(unit, edits);
		assertEquals(expectedText, newText);
	}

	@Test
	public void testFormatting_indent_switchstatementsFalse() throws Exception {
		String original = javaProject.getOption(DefaultCodeFormatterConstants.FORMATTER_INDENT_SWITCHSTATEMENTS_COMPARE_TO_SWITCH, true);
		try {
			javaProject.setOption(DefaultCodeFormatterConstants.FORMATTER_INDENT_SWITCHSTATEMENTS_COMPARE_TO_SWITCH, DefaultCodeFormatterConstants.FALSE);
			ICompilationUnit unit = getWorkingCopy("src/org/sample/Baz.java",
			//@formatter:off
				"package org.sample;\n" +
				"\n" +
				"public class Baz {\n" +
				"    private enum Numbers {One, Two};\n" +
				"    public void foo() {\n" +
				"        Numbers n = Numbers.One;\n" +
				"        switch (n) {\n" +
				"        case One:\n" +
				"        return;\n" +
				"        case Two:\n" +
				"        return;\n" +
				"        default:\n" +
				"        break;\n" +
				"        }\n" +
				"    }\n" +
				"}"
			//@formatter:off
			);
			String uri = JDTUtils.toURI(unit);
			TextDocumentIdentifier textDocument = new TextDocumentIdentifier(uri);
			FormattingOptions options = new FormattingOptions(4, true);
			DocumentFormattingParams params = new DocumentFormattingParams(textDocument, options);
			List<? extends TextEdit> edits = server.formatting(params).get();
			assertNotNull(edits);
			//@formatter:off
			String expectedText =
				"package org.sample;\n" +
				"\n" +
				"public class Baz {\n" +
				"    private enum Numbers {\n" +
				"        One, Two\n" +
				"    };\n" +
				"\n" +
				"    public void foo() {\n" +
				"        Numbers n = Numbers.One;\n" +
				"        switch (n) {\n" +
				"        case One:\n" +
				"            return;\n" +
				"        case Two:\n" +
				"            return;\n" +
				"        default:\n" +
				"            break;\n" +
				"        }\n" +
				"    }\n" +
				"}";
			//@formatter:on
			String newText = TextEditUtil.apply(unit, edits);
			assertEquals(expectedText, newText);
		} finally {
			javaProject.setOption(DefaultCodeFormatterConstants.FORMATTER_INDENT_SWITCHSTATEMENTS_COMPARE_TO_SWITCH, original);
		}
	}

	@Test
	public void testRangeFormatting() throws Exception {
		ICompilationUnit unit = getWorkingCopy("src/org/sample/Baz.java",
		//@formatter:off
			"package org.sample;\n" +
			"      public class Baz {\n"+
			"\tvoid foo(){\n" +
			"    }\n"+
			"	}\n"
		//@formatter:on
		);

		String uri = JDTUtils.toURI(unit);
		TextDocumentIdentifier textDocument = new TextDocumentIdentifier(uri);

		Range range = new Range(new Position(2, 0), new Position(3, 5));// range around foo()
		DocumentRangeFormattingParams params = new DocumentRangeFormattingParams(textDocument,new FormattingOptions(3, true), range); // ident == 3 spaces

		List<? extends TextEdit> edits = server.rangeFormatting(params).get();
		//@formatter:off
		String expectedText =
			"package org.sample;\n" +
			"      public class Baz {\n"+
			"         void foo() {\n" +
			"         }\n"+
			"	}\n";
		//@formatter:on
		String newText = TextEditUtil.apply(unit, edits);
		assertEquals(expectedText, newText);
	}

	@Test
	public void testDocumentFormattingWithCustomOption() throws Exception {
		ICompilationUnit unit = getWorkingCopy("src/org/sample/Baz.java",
			//@formatter:off
			"@Deprecated package org.sample;\n\n" +
				"public class Baz {\n"+
				"    /**Java doc @param a some parameter*/\n"+
				"\tvoid foo(int a){;;\n"+
				"}\n"+
				"}\n"
			//@formatter:on
		);

		String uri = JDTUtils.toURI(unit);
		TextDocumentIdentifier textDocument = new TextDocumentIdentifier(uri);
		FormattingOptions options = new FormattingOptions(2, true);
		options.putNumber("org.eclipse.jdt.core.formatter.blank_lines_before_package", Integer.valueOf(2));
		options.putString("org.eclipse.jdt.core.formatter.insert_new_line_after_annotation_on_package", "do not insert");
		options.putBoolean("org.eclipse.jdt.core.formatter.put_empty_statement_on_new_line", Boolean.TRUE);
		DocumentFormattingParams params = new DocumentFormattingParams(textDocument, options);
		preferences.setJavaFormatComments(false);
		List<? extends TextEdit> edits = server.formatting(params).get();
		assertNotNull(edits);

		String expectedText =
			"\n"+
				"\n"+
				"@Deprecated package org.sample;\n"+
				"\n"+
				"public class Baz {\n"+
				"  /**Java doc @param a some parameter*/\n"+
				"  void foo(int a) {\n"+
				"    ;\n"+
				"    ;\n"+
				"  }\n"+
				"}\n";
		String newText = TextEditUtil.apply(unit, edits);
		preferences.setJavaFormatComments(true);
		assertEquals(expectedText, newText);
	}

	@Test
	public void testGoogleFormatter() throws Exception {
		try {
			String text =
			//@formatter:off
					"package org.sample;\n\n" +
					"public class Baz {\n" +
					"  String name;\n" +
					"}\n";
				//@formatter:on"
			ICompilationUnit unit = getWorkingCopy("src/org/sample/Baz.java", text);
			String uri = JDTUtils.toURI(unit);
			TextDocumentIdentifier textDocument = new TextDocumentIdentifier(uri);
			FormattingOptions options = new FormattingOptions(2, true);// ident == 2 spaces
			DocumentFormattingParams params = new DocumentFormattingParams(textDocument, options);
			Bundle bundle = Platform.getBundle(JavaLanguageServerTestPlugin.PLUGIN_ID);
			URL googleFormatter = bundle.getEntry("/formatter resources/eclipse-java-google-style.xml");
			URL url = FileLocator.resolve(googleFormatter);
			preferences.setFormatterUrl(url.toExternalForm());
			StandardProjectsManager.configureSettings(preferences);
			List<? extends TextEdit> edits = server.formatting(params).get();
			assertNotNull(edits);
			String newText = TextEditUtil.apply(unit, edits);
			assertEquals(text, newText);
		} finally {
			preferences.setFormatterUrl(null);
			StandardProjectsManager.configureSettings(preferences);
		}
	}

	@Test
	public void testGoogleFormatterFilePath() throws Exception {
		try {
			String text =
			//@formatter:off
					"package org.sample;\n\n" +
					"public class Baz {\n" +
					"  String name;\n" +
					"}\n";
				//@formatter:on"
			ICompilationUnit unit = getWorkingCopy("src/org/sample/Baz.java", text);
			String uri = JDTUtils.toURI(unit);
			TextDocumentIdentifier textDocument = new TextDocumentIdentifier(uri);
			FormattingOptions options = new FormattingOptions(2, true);// ident == 2 spaces
			DocumentFormattingParams params = new DocumentFormattingParams(textDocument, options);
			Bundle bundle = Platform.getBundle(JavaLanguageServerTestPlugin.PLUGIN_ID);
			URL googleFormatter = bundle.getEntry("/formatter resources/eclipse-java-google-style.xml");
			URL url = FileLocator.resolve(googleFormatter);
			File file = ResourceUtils.toFile(URIUtil.toURI(url));
			preferences.setFormatterUrl(file.getAbsolutePath());
			StandardProjectsManager.configureSettings(preferences);
			List<? extends TextEdit> edits = server.formatting(params).get();
			assertNotNull(edits);
			String newText = TextEditUtil.apply(unit, edits);
			assertEquals(text, newText);
		} finally {
			preferences.setFormatterUrl(null);
			StandardProjectsManager.configureSettings(preferences);
		}
	}

	@Test
	public void testFilePath() throws Exception {
		try {
			Bundle bundle = Platform.getBundle(JavaLanguageServerTestPlugin.PLUGIN_ID);
			URL formatterUrl = bundle.getEntry("/formatter/test.xml");
			URL url = FileLocator.resolve(formatterUrl);
			File file = ResourceUtils.toFile(URIUtil.toURI(url));
			assertTrue(file.exists());
			preferences.setFormatterUrl(file.getAbsolutePath());
			StandardProjectsManager.configureSettings(preferences);
			assertTrue(preferences.getFormatterAsURI().isAbsolute());
		} finally {
			preferences.setFormatterUrl(null);
			StandardProjectsManager.configureSettings(preferences);
		}
	}

	@Test
	public void testRelativeFilePath() throws Exception {
		try {
			String formatterUrl = "../../formatter/test.xml";
			preferences.setFormatterUrl(formatterUrl);
			StandardProjectsManager.configureSettings(preferences);
			assertTrue(preferences.getFormatterAsURI().isAbsolute());
		} finally {
			preferences.setFormatterUrl(null);
			StandardProjectsManager.configureSettings(preferences);
		}
	}

	@Test // typing ; should format the current line
	public void testFormattingOnTypeSemiColumn() throws Exception {
		javaProject.setOption(DefaultCodeFormatterConstants.FORMATTER_TAB_CHAR, JavaCore.SPACE);

		ICompilationUnit unit = getWorkingCopy("src/org/sample/Baz.java",
		//@formatter:off
			  "package org.sample;\n\n"
			+ "public class Baz {  \n"
			+ "String          name       ;\n"//typed ; here
			+ "}\n"
		//@formatter:on
		);

		String uri = JDTUtils.toURI(unit);
		TextDocumentIdentifier textDocument = new TextDocumentIdentifier(uri);
		FormattingOptions options = new FormattingOptions(4, false);// ident == tab

		DocumentOnTypeFormattingParams params = new DocumentOnTypeFormattingParams(textDocument, options, new Position(3, 27), ";");

		preferences.setJavaFormatOnTypeEnabled(true);
		List<? extends TextEdit> edits = server.onTypeFormatting(params).get();
		assertNotNull(edits);


		//@formatter:off
		String expectedText =
			  "package org.sample;\n"
			+ "\n"
			+ "public class Baz {  \n"
			+ "\tString name;\n"
			+ "}\n";
		//@formatter:on

		String newText = TextEditUtil.apply(unit, edits);
		assertEquals(expectedText, newText);
	}

	@Test // typing new_line should format the current line if previous character doesn't close a block
	public void testFormattingOnTypeNewLine() throws Exception {
		ICompilationUnit unit = getWorkingCopy("src/org/sample/Baz.java",
		//@formatter:off
			  "package org.sample;\n"
			+ "\n"
			+ "    public      class     Baz {  \n"
			+ "String          name       ;\n"//typed \n here
			+ "}\n"
		//@formatter:on
		);

		String uri = JDTUtils.toURI(unit);
		TextDocumentIdentifier textDocument = new TextDocumentIdentifier(uri);
		FormattingOptions options = new FormattingOptions(4, true);// ident == 4 spaces

		DocumentOnTypeFormattingParams params = new DocumentOnTypeFormattingParams(textDocument, options, new Position(3, 28), "\n");

		preferences.setJavaFormatOnTypeEnabled(true);
		List<? extends TextEdit> edits = server.onTypeFormatting(params).get();
		assertNotNull(edits);

		//@formatter:off
		String expectedText =
			  "package org.sample;\n"
			+ "\n"
			+ "    public      class     Baz {  \n"//this part won't be formatted
			+ "        String name;\n"
			+ "}\n";
		//@formatter:on

		String newText = TextEditUtil.apply(unit, edits);
		assertEquals(expectedText, newText);
	}

	@Test // typing } should format the previous block
	public void testFormattingOnTypeCloseBlock() throws Exception {
		ICompilationUnit unit = getWorkingCopy("src/org/sample/Baz.java",
		//@formatter:off
			  "package org.sample;\n"
			+ "\n"
			+ "    public      class     Baz {  \n"
			+ "String          name       ;\n"
			+ "}  "//typed } here
		//@formatter:on
		);

		String uri = JDTUtils.toURI(unit);
		TextDocumentIdentifier textDocument = new TextDocumentIdentifier(uri);
		FormattingOptions options = new FormattingOptions(4, true);// ident == 4 spaces

		DocumentOnTypeFormattingParams params = new DocumentOnTypeFormattingParams(textDocument, options,new Position(4, 0), "}");

		preferences.setJavaFormatOnTypeEnabled(true);
		List<? extends TextEdit> edits = server.onTypeFormatting(params).get();
		assertNotNull(edits);

		//@formatter:off
		String expectedText =
			  "package org.sample;\n"
			+ "\n"
			+ "public class Baz {\n"
			+ "    String name;\n"
			+ "}";
		//@formatter:on

		String newText = TextEditUtil.apply(unit, edits);
		assertEquals(expectedText, newText);
	}

	@Test // typing new_line after opening a block should only format the current line
	public void testFormattingOnTypeReturnAfterOpeningBlock() throws Exception {
		ICompilationUnit unit = getWorkingCopy("src/org/sample/Baz.java",
		//@formatter:off
			  "package org.sample;\n"
			+ "\n"
			+ "    public      class     Baz {  \n"//typed \n here
			+ "String          name       ;\n"
			+ "}  \n"
		//@formatter:on
		);

		String uri = JDTUtils.toURI(unit);
		TextDocumentIdentifier textDocument = new TextDocumentIdentifier(uri);
		FormattingOptions options = new FormattingOptions(4, true);// ident == 4 spaces

		DocumentOnTypeFormattingParams params = new DocumentOnTypeFormattingParams(textDocument, options, new Position(2, 33), "\n");

		preferences.setJavaFormatOnTypeEnabled(true);
		List<? extends TextEdit> edits = server.onTypeFormatting(params).get();
		assertNotNull(edits);

		//@formatter:off
		String expectedText =
			  "package org.sample;\n"
			+ "\n"
			+ "public class Baz {\n"
			+ "String          name       ;\n"
			+ "}  \n";
		//@formatter:on

		String newText = TextEditUtil.apply(unit, edits);
		assertEquals(expectedText, newText);
	}

	@Test // typing new_line after closing a block should format the that block
	public void testFormattingOnTypeReturnAfterClosedBlock() throws Exception {
		ICompilationUnit unit = getWorkingCopy("src/org/sample/Baz.java",
		//@formatter:off
			  "package org.sample;\n"
			+ "\n"
			+ "    public      class     Baz {  \n"
			+ "String          name       ;\n"
			+ "}  \n"//typed \n here
		//@formatter:on
		);

		String uri = JDTUtils.toURI(unit);
		TextDocumentIdentifier textDocument = new TextDocumentIdentifier(uri);
		FormattingOptions options = new FormattingOptions(4, true);// ident == 4 spaces

		DocumentOnTypeFormattingParams params = new DocumentOnTypeFormattingParams(textDocument, options,  new Position(4, 3), "\n");

		preferences.setJavaFormatOnTypeEnabled(true);
		List<? extends TextEdit> edits = server.onTypeFormatting(params).get();
		assertNotNull(edits);

		//@formatter:off
		String expectedText =
			  "package org.sample;\n"
			+ "\n"
			+ "public class Baz {\n"
			+ "    String name;\n"
			+ "}\n";
		//@formatter:on

		String newText = TextEditUtil.apply(unit, edits);
		assertEquals(expectedText, newText);
	}

	@Test // typing new_line after inserting a new line should format the previous block if previous non-whitespace char is }
	public void testFormattingOnTypeReturnAfterEmptyLine() throws Exception {
		ICompilationUnit unit = getWorkingCopy("src/org/sample/Baz.java",
		//@formatter:off
			  "package org.sample;\n"
			+ "\n"
			+ "    public      class     Baz {  \n"
			+ "String          name       ;\n"
			+ "}  \n"
			+ "   \n"//typed \n here
		//@formatter:on
		);

		String uri = JDTUtils.toURI(unit);
		TextDocumentIdentifier textDocument = new TextDocumentIdentifier(uri);
		FormattingOptions options = new FormattingOptions(4, true);// ident == 4 spaces

		DocumentOnTypeFormattingParams params = new DocumentOnTypeFormattingParams(textDocument, options, new Position(5, 3), "\n");

		preferences.setJavaFormatOnTypeEnabled(true);
		List<? extends TextEdit> edits = server.onTypeFormatting(params).get();
		assertNotNull(edits);

		//@formatter:off
		String expectedText =
			  "package org.sample;\n"
			+ "\n"
			+ "public class Baz {\n"
			+ "    String name;\n"
			+ "}\n"
			+ "   \n";
		//@formatter:on

		String newText = TextEditUtil.apply(unit, edits);
		assertEquals(expectedText, newText);
	}

	@Test // typing new_line after an empty block on a single line should format that block
	public void testFormattingOnTypeReturnAfterEmptyBlock() throws Exception {
		ICompilationUnit unit = getWorkingCopy("src/org/sample/Baz.java",
		//@formatter:off
			  "package org.sample;\n"
			+ "\n"
			+ "    public      class     Baz {}  \n"//typed \n here
		//@formatter:on
		);

		String uri = JDTUtils.toURI(unit);
		TextDocumentIdentifier textDocument = new TextDocumentIdentifier(uri);
		FormattingOptions options = new FormattingOptions(4, true);// ident == 4 spaces

		DocumentOnTypeFormattingParams params = new DocumentOnTypeFormattingParams(textDocument, options, new Position(2, 34), "\n");

		preferences.setJavaFormatOnTypeEnabled(true);
		List<? extends TextEdit> edits = server.onTypeFormatting(params).get();
		assertNotNull(edits);

		//@formatter:off
		String expectedText =
			  "package org.sample;\n"
			+ "\n"
			+ "public class Baz {\n"
			+ "}\n";
		//@formatter:on

		String newText = TextEditUtil.apply(unit, edits);
		assertEquals(expectedText, newText);
	}

	@Test // https://github.com/redhat-developer/vscode-java/issues/3396
	public void testFormattingOnTypeReturnMidline() throws Exception {
		ICompilationUnit unit = getWorkingCopy("src/org/sample/Baz.java",
		//@formatter:off
			  "package org.sample;\n"
			+ "\n"
			+ "public class Baz {\n"
			+ "    public String print() {\n"
			+ "        int a = 1;\n"
			+ "        return String.format(\"Value: {}\",\n"
			+ "        a);\n"
			+ "    }\n"
			+ "}"
		//@formatter:on
		);

		String uri = JDTUtils.toURI(unit);
		TextDocumentIdentifier textDocument = new TextDocumentIdentifier(uri);
		FormattingOptions options = new FormattingOptions(4, true);// ident == 4 spaces

		DocumentOnTypeFormattingParams params = new DocumentOnTypeFormattingParams(textDocument, options, new Position(5, 41), "\n");

		preferences.setJavaFormatOnTypeEnabled(true);
		List<? extends TextEdit> edits = server.onTypeFormatting(params).get();
		assertNotNull(edits);

		//@formatter:off
		String expectedText =
			  "package org.sample;\n"
			+ "\n"
			+ "public class Baz {\n"
			+ "    public String print() {\n"
			+ "        int a = 1;\n"
			+ "        return String.format(\"Value: {}\",\n"
			+ "                a);\n"
			+ "    }\n"
			+ "}";
		//@formatter:on

		String newText = TextEditUtil.apply(unit, edits);
		assertEquals(expectedText, newText);
	}

	@Test
	public void testDisableFormattingOnType() throws Exception {
		//@formatter:off
		String text =  "package org.sample;\n"
					+ "\n"
					+ "    public      class     Baz {  \n"
					+ "String          name       ;\n"
					+ "}\n";
				//@formatter:on
		ICompilationUnit unit = getWorkingCopy("src/org/sample/Baz.java", text);

		String uri = JDTUtils.toURI(unit);
		TextDocumentIdentifier textDocument = new TextDocumentIdentifier(uri);
		FormattingOptions options = new FormattingOptions(4, true);// ident == 4 spaces

		DocumentOnTypeFormattingParams params = new DocumentOnTypeFormattingParams(textDocument, options, new Position(3, 28), "\n");
		//Check it's disabled by default
		List<? extends TextEdit> edits = server.onTypeFormatting(params).get();
		assertNotNull(edits);

		String newText = TextEditUtil.apply(unit, edits);
		assertEquals(text, newText);
	}

	@Test
	public void testUpdateFormatterVersion() throws Exception {
		// see: https://github.com/redhat-developer/vscode-java/issues/1640
		String text =
			//@formatter:off
			"package org.sample;\n\n" +
			"public class Baz {\n"+
				"\tpublic void test1() {\n"+
					"\t\tObject o = new Object() {};\n"+
				"\t}\n"+
			"}\n";
			//@formatter:on
		ICompilationUnit unit = getWorkingCopy("src/org/sample/Baz.java", text);
		String uri = JDTUtils.toURI(unit);
		TextDocumentIdentifier textDocument = new TextDocumentIdentifier(uri);
		FormattingOptions options = new FormattingOptions(2, true);// ident == 2 spaces
		DocumentFormattingParams params = new DocumentFormattingParams(textDocument, options);
		Bundle bundle = Platform.getBundle(JavaLanguageServerTestPlugin.PLUGIN_ID);
		URL testFormatter = bundle.getEntry("/formatter resources/version13.xml");
		URL url = FileLocator.resolve(testFormatter);
		File file = ResourceUtils.toFile(URIUtil.toURI(url));
		preferences.setFormatterUrl(file.getAbsolutePath());
		try {
			StandardProjectsManager.configureSettings(preferences);
			List<? extends TextEdit> edits = server.formatting(params).get();
			assertNotNull(edits);
			String newText = TextEditUtil.apply(unit, edits);
			String textResult =
			//@formatter:off
				"package org.sample;\n\n" +
				"public class Baz {\n"+
				"  public void test1() {\n"+
				"    Object o = new Object() {};\n"+
				"  }\n"+
				"}\n";
				//@formatter:on
			assertEquals(textResult, newText);
		} finally {
			preferences.setFormatterUrl(null);
			StandardProjectsManager.configureSettings(preferences);
		}
	}

	@Test
	public void testStringFormattingWithUpdating() throws Exception {
		//@formatter:off
		String text = "package org.sample;\n"
					+ "\n"
					+ "    public      class     Baz {public void test1() {Object o = new Object() {};}}  \n";
		//@formatter:on
		Map<String, String> options = new HashMap<>();
		options.put("org.eclipse.jdt.core.formatter.insert_new_line_in_empty_anonymous_type_declaration", "do not insert");
		FormatterHandler handler = new FormatterHandler(preferenceManager);
		String formattedText =  handler.stringFormatting(text, options, 13, monitor);
		//@formatter:off
		String expectedText =
			  "package org.sample;\n"
			+ "\n"
			+ "public class Baz {\n"
			+ "\tpublic void test1() {\n"
			+ "\t\tObject o = new Object() {};\n"
			+ "\t}\n"
			+ "}\n";
		//@formatter:on
		assertEquals(formattedText, expectedText);
	}

	@Test
	public void testStringFormatting() throws Exception {
		//@formatter:off
		String text = "package org.sample;\n"
					+ "\n"
					+ "    public      class     Baz {}  \n";
		//@formatter:on
		FormatterHandler handler = new FormatterHandler(preferenceManager);
		Map<String, String> options = new HashMap<>();
		options.put("org.eclipse.jdt.core.formatter.blank_lines_after_package", "3");
		String formattedText = handler.stringFormatting(text, options, ProfileVersionerCore.getCurrentVersion(), monitor);
		//@formatter:off
		String expectedText =
			  "package org.sample;\n"
			+ "\n"
			+ "\n"
			+ "\n"
			+ "public class Baz {\n"
			+ "}\n";
		//@formatter:on
		assertEquals(formattedText, expectedText);
	}

	@Test
	public void testStringFormattingWithDefaultSettings() throws Exception {
		//@formatter:off
		String text = "package org.sample;\n"
					+ "\n"
					+ "    public      class     Baz {}  \n";
		//@formatter:on
		FormatterHandler handler = new FormatterHandler(preferenceManager);
		String formattedText = handler.stringFormatting(text, null, ProfileVersionerCore.getCurrentVersion(), monitor);
		//@formatter:off
		String expectedText =
			  "package org.sample;\n"
			+ "\n"
			+ "public class Baz {\n"
			+ "}\n";
		//@formatter:on
		assertEquals(formattedText, expectedText);
	}

	@Test
	public void testDefaultFormatterPreferences() throws Exception {
		assertEquals(DefaultCodeFormatterConstants.FALSE, JavaCore.getOption(DefaultCodeFormatterConstants.FORMATTER_JOIN_WRAPPED_LINES));
		assertEquals(DefaultCodeFormatterConstants.FALSE, JavaCore.getOption(DefaultCodeFormatterConstants.FORMATTER_JOIN_LINES_IN_COMMENTS));
		assertEquals(DefaultCodeFormatterConstants.TRUE, JavaCore.getOption(DefaultCodeFormatterConstants.FORMATTER_INDENT_SWITCHSTATEMENTS_COMPARE_TO_SWITCH));
		assertEquals(DefaultCodeFormatterConstants.TRUE, JavaCore.getOption(DefaultCodeFormatterConstants.FORMATTER_USE_ON_OFF_TAGS));
	}

	@Test
	public void testJoinWrappedLines() throws Exception {
		//@formatter:off
		String text = "/**\n"
				+ " * line 1\n"
				+ " * line 2\n"
				+ " */\n"
				+ "public enum X {\n"
				+ "       ONE,\n"
				+ "       TWO,\n"
				+ "       THREE;\n"
				+ "}\n";
		//@formatter:on
		FormatterHandler handler = new FormatterHandler(preferenceManager);
		String formattedText = handler.stringFormatting(text, null, ProfileVersionerCore.getCurrentVersion(), monitor);
		//@formatter:off
		String expectedText = "/**\n"
				+ " * line 1\n"
				+ " * line 2\n"
				+ " */\n"
				+ "public enum X {\n"
				+ "\tONE,\n"
				+ "\tTWO,\n"
				+ "\tTHREE;\n"
				+ "}\n";
		//@formatter:on
		assertEquals(formattedText, expectedText);
		Map<String, String> options = FormatterHandler.getCombinedDefaultFormatterSettings();
		options.put(DefaultCodeFormatterConstants.FORMATTER_JOIN_WRAPPED_LINES, DefaultCodeFormatterConstants.TRUE);
		options.put(DefaultCodeFormatterConstants.FORMATTER_JOIN_LINES_IN_COMMENTS, DefaultCodeFormatterConstants.TRUE);
		formattedText = handler.stringFormatting(text, options, ProfileVersionerCore.getCurrentVersion(), monitor);
		//@formatter:off
		expectedText = "/**\n"
				+ " * line 1 line 2\n"
				+ " */\n"
				+ "public enum X {\n"
				+ "\tONE, TWO, THREE;\n"
				+ "}\n";
		//@formatter:on
		assertEquals(formattedText, expectedText);
	}

	@Test
	public void testIndentSwitchStatements() throws Exception {
		//@formatter:off
		String text = "public class Hello {\n"
				+ "    public static void main(String[] args) {\n"
				+ "        switch (args.length) {\n"
				+ "        case 0:\n"
				+ "            System.err.println(\"none\");\n"
				+ "            break;\n"
				+ "        case 1:\n"
				+ "            System.err.println(\"one\");\n"
				+ "            break;\n"
				+ "        default:\n"
				+ "            System.err.println(\"many\");\n"
				+ "        }\n"
				+ "    }\n"
				+ "}\n";
		//@formatter:on
		FormatterHandler handler = new FormatterHandler(preferenceManager);
		String formattedText = handler.stringFormatting(text, null, ProfileVersionerCore.getCurrentVersion(), monitor);
		//@formatter:off
		String expectedText = "public class Hello {\n"
				+ "\tpublic static void main(String[] args) {\n"
				+ "\t\tswitch (args.length) {\n"
				+ "\t\t\tcase 0:\n"
				+ "\t\t\t\tSystem.err.println(\"none\");\n"
				+ "\t\t\t\tbreak;\n"
				+ "\t\t\tcase 1:\n"
				+ "\t\t\t\tSystem.err.println(\"one\");\n"
				+ "\t\t\t\tbreak;\n"
				+ "\t\t\tdefault:\n"
				+ "\t\t\t\tSystem.err.println(\"many\");\n"
				+ "\t\t}\n"
				+ "\t}\n"
				+ "}\n";
		//@formatter:on
		assertEquals(formattedText, expectedText);
	}

	@Test
	public void testProfileSettings() throws Exception {
		assertEquals(DefaultCodeFormatterConstants.END_OF_LINE, JavaCore.getOption(DefaultCodeFormatterConstants.FORMATTER_BRACE_POSITION_FOR_BLOCK));
		try {
			String formatterUrl = "../../formatter/test.xml";
			// valid profile
			preferences.setFormatterUrl(formatterUrl);
			preferences.setFormatterProfileName("GoogleStyle");
			StandardProjectsManager.configureSettings(preferences);
			assertEquals(DefaultCodeFormatterConstants.NEXT_LINE, JavaCore.getOption(DefaultCodeFormatterConstants.FORMATTER_BRACE_POSITION_FOR_BLOCK));
			// reset
			preferences.setFormatterUrl(null);
			preferences.setFormatterProfileName(null);
			StandardProjectsManager.configureSettings(preferences);
			assertEquals(DefaultCodeFormatterConstants.END_OF_LINE, JavaCore.getOption(DefaultCodeFormatterConstants.FORMATTER_BRACE_POSITION_FOR_BLOCK));
			// invalid profile
			preferences.setFormatterUrl(formatterUrl);
			preferences.setFormatterProfileName("Invalid");
			StandardProjectsManager.configureSettings(preferences);
			assertEquals(DefaultCodeFormatterConstants.END_OF_LINE, JavaCore.getOption(DefaultCodeFormatterConstants.FORMATTER_BRACE_POSITION_FOR_BLOCK));
			// empty profile (valid)
			preferences.setFormatterUrl(formatterUrl);
			preferences.setFormatterProfileName("");
			StandardProjectsManager.configureSettings(preferences);
			assertEquals(DefaultCodeFormatterConstants.NEXT_LINE, JavaCore.getOption(DefaultCodeFormatterConstants.FORMATTER_BRACE_POSITION_FOR_BLOCK));
		} finally {
			preferences.setFormatterUrl(null);
			preferences.setFormatterProfileName(null);
			StandardProjectsManager.configureSettings(preferences);
		}
		assertEquals(DefaultCodeFormatterConstants.END_OF_LINE, JavaCore.getOption(DefaultCodeFormatterConstants.FORMATTER_BRACE_POSITION_FOR_BLOCK));
	}

	@After
	public void tearDown() {
		javaProject.setOption(DefaultCodeFormatterConstants.FORMATTER_TAB_CHAR, originalTabChar);
		preferences.setJavaFormatOnTypeEnabled(false);
	}

	@Test
	public void testNotebook() throws Exception {
		String text = """
				import java.math.  BigDecimal;

				BigDecimal   n1 = new
				   BigDecimal("0");
				System.  out.  println(n1);
				""";
		FormattingOptions options = new FormattingOptions(4, true);// ident == 4 spaces
		List<TextEdit> edits = new FormatterHandler(preferenceManager).formatJavaCode(text, options, null, monitor);
		assertNotNull(edits);
		assertEquals(1, edits.size());
		String newText = """
				import java.math.BigDecimal;

				BigDecimal n1 = new BigDecimal("0");
				System.out.println(n1);
				""";
		assertEquals(newText, edits.get(0).getNewText());
	}

	@Test
	public void testNotebook2() throws Exception {
		String text = """
				import static smile.plot.vega.Predicate.*;

				var bar = new View("2D Histogram Heatmap").width(300).height(200);
				    bar.mark("rect");
				bar.viewConfig()
				        .stroke("transparent");
				bar.data().values(new DatasetLoader().loadAsJson("movies").toPrettyString());
				bar.encode("x", "IMDB Rating")
				            .type("quantitative")
				        .title("IMDB Rating")
				            .bin(new BinParams().maxBins(60));
				bar.encode("y", "Rotten Tomatoes Rating")
				        .type("quantitative")
				        .bin(new BinParams().maxBins(40));
				bar.encode("color", null)
				        .type("quantitative")
				        .aggregate("count");
				bar.transform()
				        .filter(and(valid("IMDB Rating"), valid("Rotten Tomatoes Rating")));

				display(bar.toPrettyString(), "application/vnd.vegalite.v5+json");

				""";
		FormattingOptions options = new FormattingOptions(4, true);// ident == 4 spaces
		List<TextEdit> edits = new FormatterHandler(preferenceManager).formatJavaCode(text, options, null, monitor);
		assertNotNull(edits);
		assertEquals(1, edits.size());
		String newText = """
				import static smile.plot.vega.Predicate.*;

				var bar = new View("2D Histogram Heatmap").width(300).height(200);
				bar.mark("rect");
				bar.viewConfig()
				        .stroke("transparent");
				bar.data().values(new DatasetLoader().loadAsJson("movies").toPrettyString());
				bar.encode("x", "IMDB Rating")
				        .type("quantitative")
				        .title("IMDB Rating")
				        .bin(new BinParams().maxBins(60));
				bar.encode("y", "Rotten Tomatoes Rating")
				        .type("quantitative")
				        .bin(new BinParams().maxBins(40));
				bar.encode("color", null)
				        .type("quantitative")
				        .aggregate("count");
				bar.transform()
				        .filter(and(valid("IMDB Rating"), valid("Rotten Tomatoes Rating")));

				display(bar.toPrettyString(), "application/vnd.vegalite.v5+json");
				""";
		assertEquals(newText, edits.get(0).getNewText());
	}

	@Test
	public void testNotebookRange() throws Exception {
		String text = """
				import java.math.BigDecimal;

				BigDecimal n1
				= new BigDecimal("0");
				System.out.println(n1);

				System.out.  println();

				""";
		FormattingOptions options = new FormattingOptions(4, true);// ident == 4 spaces
		Range range = new Range(new Position(2, 0), new Position(4, 23));
		List<TextEdit> edits = new FormatterHandler(preferenceManager).formatJavaCode(text, options, range, monitor);
		assertNotNull(edits);
		assertEquals(1, edits.size());
		String newText = """
				import java.math.BigDecimal;

				BigDecimal n1 = new BigDecimal("0");
				System.out.println(n1);

				System.out.  println();

				""";
		assertEquals(newText, edits.get(0).getNewText());
	}

	@Test
	public void testNotebookClass() throws Exception {
		String text = """
				import java.math.BigDecimal;

				public class Test {
					public static void main(String[] args) {
						BigDecimal n1 =
						new BigDecimal("0");
						System.out  .println(n1);
					}
				}
				""";
		FormattingOptions options = new FormattingOptions(4, true);// ident == 4 spaces
		List<TextEdit> edits = new FormatterHandler(preferenceManager).formatJavaCode(text, options, null, monitor);
		assertNotNull(edits);
		assertEquals(1, edits.size());
		String newText = """
				import java.math.BigDecimal;

				public class Test {
				    public static void main(String[] args) {
				        BigDecimal n1 = new BigDecimal("0");
				        System.out.println(n1);
				    }
				}
				""";
		assertEquals(newText, edits.get(0).getNewText());
	}

	@Test
	public void testNotebookMalformed() throws Exception {
		String text = """
				void test() {
				System.out.  println("test");

				test();
				""";
		FormattingOptions options = new FormattingOptions(4, true);// ident == 4 spaces
		List<TextEdit> edits = new FormatterHandler(preferenceManager).formatJavaCode(text, options, null, monitor);
		assertNotNull(edits);
		assertEquals(1, edits.size());
		//@formatter:off
		String newText = "\n    void test() {\n"
				+ "        System.out.println(\"test\");\n"
				+ "\n"
				+ "        test();\n";
		//@formatter:on
		assertEquals(newText, edits.get(0).getNewText());
	}

}
