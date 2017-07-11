/*******************************************************************************
 * Copyright (c) 2017 Red Hat Inc. and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Red Hat Inc. - initial API and implementation
 *******************************************************************************/
package org.eclipse.jdt.ls.core.internal.handlers;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import java.util.Collections;
import java.util.List;

import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.ls.core.internal.JDTUtils;
import org.eclipse.jdt.ls.core.internal.TextEditUtil;
import org.eclipse.lsp4j.DocumentFormattingParams;
import org.eclipse.lsp4j.DocumentRangeFormattingParams;
import org.eclipse.lsp4j.FormattingOptions;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.eclipse.lsp4j.TextEdit;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.runners.MockitoJUnitRunner;

/**
 * @author Fred Bricon
 */
@RunWith(MockitoJUnitRunner.class)
public class FormatterHandlerTest extends AbstractCompilationUnitBasedTest {

	@Test
	public void testDocumentFormatting() throws Exception {
		ICompilationUnit unit = getWorkingCopy("src/org/sample/Baz.java",
		//@formatter:off
			"package org.sample   ;\n\n" +
			"      public class Baz {  String name;}\n"
		//@formatter:on
		);

		String uri = JDTUtils.getFileURI(unit);
		TextDocumentIdentifier textDocument = new TextDocumentIdentifier(uri);
		FormattingOptions options = new FormattingOptions(4, true, Collections.emptyMap());// ident == 4 spaces
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
		preferenceManager.getPreferences().setJavaFormatEnabled(false);
		String uri = JDTUtils.getFileURI(unit);
		TextDocumentIdentifier textDocument = new TextDocumentIdentifier(uri);
		FormattingOptions options = new FormattingOptions(4, true, Collections.emptyMap());// ident == 4 spaces
		DocumentFormattingParams params = new DocumentFormattingParams(textDocument, options);
		List<? extends TextEdit> edits = server.formatting(params).get();
		assertNotNull(edits);
		String newText = TextEditUtil.apply(unit, edits);
		assertEquals(text, newText);
	}

	@Test
	public void testDocumentFormattingWithTabs() throws Exception {
		ICompilationUnit unit = getWorkingCopy("src/org/sample/Baz.java",
		//@formatter:off
			"package org.sample;\n\n" +
			"public class Baz {\n"+
			"    void foo(){\n"+
			"}\n"+
			"}\n"
		//@formatter:on
		);

		String uri = JDTUtils.getFileURI(unit);
		TextDocumentIdentifier textDocument = new TextDocumentIdentifier(uri);
		FormattingOptions options = new FormattingOptions(2, false, Collections.emptyMap());// ident == tab
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

		String uri = JDTUtils.getFileURI(unit);
		TextDocumentIdentifier textDocument = new TextDocumentIdentifier(uri);
		FormattingOptions options = new FormattingOptions(4, false, Collections.emptyMap());// ident == tab
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

		String uri = JDTUtils.getFileURI(unit);
		TextDocumentIdentifier textDocument = new TextDocumentIdentifier(uri);

		Range range = new Range(new Position(2, 0), new Position(3, 5));// range around foo()
		DocumentRangeFormattingParams params = new DocumentRangeFormattingParams(range);
		params.setTextDocument(textDocument);
		params.setOptions(new FormattingOptions(3, true, Collections.emptyMap()));// ident == 3 spaces

		List<? extends TextEdit> edits = server.rangeFormatting(params).get();
		//@formatter:off
		String expectedText =
			"package org.sample;\n" +
			"      public class Baz {\n"+
			"   void foo() {\n" +
			"   }\n"+
			"	}\n";
		//@formatter:on
		String newText = TextEditUtil.apply(unit, edits);
		assertEquals(expectedText, newText);
	}
}
