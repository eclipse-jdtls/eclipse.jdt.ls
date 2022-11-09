/*******************************************************************************
 * Copyright (c) 2022 Red Hat Inc. and others.
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
package org.eclipse.jdt.ls.core.internal.handlers;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import java.util.List;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IPackageFragment;
import org.eclipse.jdt.ls.core.internal.JDTUtils;
import org.eclipse.jdt.ls.core.internal.codemanipulation.AbstractSourceTestCase;
import org.eclipse.jdt.ls.core.internal.handlers.PasteEventHandler.DocumentPasteEdit;
import org.eclipse.jdt.ls.core.internal.handlers.PasteEventHandler.PasteEventParams;
import org.eclipse.lsp4j.FormattingOptions;
import org.eclipse.lsp4j.Location;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.TextEdit;
import org.eclipse.lsp4j.WorkspaceEdit;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

/**
 * Tests for {@link PasteEventHandler}
 */
public class PasteEventHandlerTest extends AbstractSourceTestCase {

	private IPackageFragment fPackageTest;

	@Before
	public void setup() throws Exception {
		fPackageTest = fRoot.createPackageFragment("test", true, null);
	}

	@Test
	public void testPasteIntoEmptyStringLiteral() throws CoreException {
		ICompilationUnit unit = fPackageTest.createCompilationUnit("A.java", //
				"package test;\n" + //
						"public class A {\n" + //
						"\tprivate String asdf = \"\";\n" + //
						"}\n",
				false, monitor);

		var params = new PasteEventParams( //
				l(JDTUtils.toUri(unit), 2, 24, 2, 24), //
				"aaa\naaa", //
				null, //
				new FormattingOptions(4, false));

		DocumentPasteEdit actual = PasteEventHandler.handlePasteEvent(params, null);

		Assert.assertEquals(new DocumentPasteEdit("aaa\\n\" + //\n\t\t\t\"aaa"), actual);
	}

	@Test
	public void testPasteWindowsNewlineInCopiedText() throws CoreException {
		ICompilationUnit unit = fPackageTest.createCompilationUnit("A.java", //
				"package test;\n" + //
						"public class A {\n" + //
						"\tprivate String asdf = \"\";\n" + //
						"}\n",
				false, monitor);

		var params = new PasteEventParams( //
				l(JDTUtils.toUri(unit), 2, 24, 2, 24), //
				"aaa\r\naaa", //
				null, //
				new FormattingOptions(4, false));

		DocumentPasteEdit actual = PasteEventHandler.handlePasteEvent(params, null);

		Assert.assertEquals(new DocumentPasteEdit("aaa\\r\\n\" + //\n\t\t\t\"aaa"), actual);
	}

	@Test
	public void testPasteWindowsNewlineInClassFile() throws CoreException {
		ICompilationUnit unit = fPackageTest.createCompilationUnit("A.java", //
				"package test;\r\n" + //
						"public class A {\r\n" + //
						"\tprivate String asdf = \"\";\r\n" + //
						"}\r\n",
				false, monitor);

		var params = new PasteEventParams( //
				l(JDTUtils.toUri(unit), 2, 24, 2, 24), //
				"aaa\naaa", //
				null, //
				new FormattingOptions(4, false));

		DocumentPasteEdit actual = PasteEventHandler.handlePasteEvent(params, null);

		Assert.assertEquals(new DocumentPasteEdit("aaa\\n\" + //\r\n\t\t\t\"aaa"), actual);
	}

	@Test
	public void testPasteBeforeStringLiteral() throws CoreException {
		ICompilationUnit unit = fPackageTest.createCompilationUnit("A.java", //
				"package test;\n" + //
						"public class A {\n" + //
						"\tprivate String asdf = \"asdf\";\n" + //
						"}\n",
				false, monitor);

		var params = new PasteEventParams( //
				l(JDTUtils.toUri(unit), 2, 23, 2, 23), //
				"aaa\naaa", //
				null, //
				new FormattingOptions(4, false));

		DocumentPasteEdit actual = PasteEventHandler.handlePasteEvent(params, null);

		Assert.assertEquals(null, actual);
	}

	@Test
	public void testPasteAfterStringLiteral() throws CoreException {
		ICompilationUnit unit = fPackageTest.createCompilationUnit("A.java", //
				"package test;\n" + //
						"public class A {\n" + //
						"\tprivate String asdf = \"asdf\";\n" + //
						"}\n",
				false, monitor);

		var params = new PasteEventParams( //
				l(JDTUtils.toUri(unit), 2, 29, 2, 29), //
				"aaa\naaa", //
				null, //
				new FormattingOptions(4, false));

		DocumentPasteEdit actual = PasteEventHandler.handlePasteEvent(params, null);

		Assert.assertEquals(null, actual);
	}

	@Test
	public void testPasteBeginningOfStringLiteral() throws CoreException {
		ICompilationUnit unit = fPackageTest.createCompilationUnit("A.java", //
				"package test;\n" + //
						"public class A {\n" + //
						"\tprivate String asdf = \"asdf\";\n" + //
						"}\n",
				false, monitor);

		var params = new PasteEventParams( //
				l(JDTUtils.toUri(unit), 2, 24, 2, 24), //
				"aaa\naaa", //
				null, //
				new FormattingOptions(4, false));

		DocumentPasteEdit actual = PasteEventHandler.handlePasteEvent(params, null);

		Assert.assertEquals(new DocumentPasteEdit("aaa\\n\" + //\n\t\t\t\"aaa"), actual);
	}

	@Test
	public void testPasteEndOfStringLiteral() throws CoreException {
		ICompilationUnit unit = fPackageTest.createCompilationUnit("A.java", //
				"package test;\n" + //
						"public class A {\n" + //
						"\tprivate String asdf = \"asdf\";\n" + //
						"}\n",
				false, monitor);

		var params = new PasteEventParams( //
				l(JDTUtils.toUri(unit), 2, 28, 2, 28), //
				"aaa\naaa", //
				null, //
				new FormattingOptions(4, false));

		DocumentPasteEdit actual = PasteEventHandler.handlePasteEvent(params, null);

		Assert.assertEquals(new DocumentPasteEdit("aaa\\n\" + //\n\t\t\t\"aaa"), actual);
	}

	@Test
	public void testPasteIntoStringBlock() throws CoreException {
		ICompilationUnit unit = fPackageTest.createCompilationUnit("A.java", //
				"package test;\n" + //
						"public class A {\n" + //
						"\tprivate String asdf = \"\"\"asdf\"\"\";\n" + //
						"}\n",
				false, monitor);

		var params = new PasteEventParams( //
				l(JDTUtils.toUri(unit), 2, 30, 2, 30), //
				"aaa\naaa", //
				null, //
				new FormattingOptions(4, false));

		DocumentPasteEdit actual = PasteEventHandler.handlePasteEvent(params, null);

		Assert.assertEquals(null, actual);
	}

	private static Location l(String uri, int startLine, int startChar, int endLine, int endChar) {
		Position start = new Position(startLine, startChar);
		Position end = new Position(endLine, endChar);
		Range range = new Range(start, end);
		Location location = new Location();
		location.setUri(uri);
		location.setRange(range);
		return location;
	}

	@Test
	public void testGetAddImportsWorkspaceEdit() throws CoreException {
		//@formatter:off
		ICompilationUnit unit = fPackageP.createCompilationUnit("B.java", "package p;\r\n" +
				"\r\n" +
				"public class B {\r\n" +
				"}"
				, true, null);
		String content = "	public List<String> b;\r\n" +
						"	public Set<String> c;\r\n";
		//@formatter:on
		String uri = JDTUtils.toURI(unit);
		Range range = new Range(new Position(3, 0), new Position(3, 0));
		PasteEventParams params = new PasteEventParams(new Location(uri, range), content, null, new FormattingOptions(4, false));
		DocumentPasteEdit documentPasteEdit = PasteEventHandler.getMissingImportsWorkspaceEdit(params, unit, monitor);
		assertNotNull(documentPasteEdit);
		WorkspaceEdit edit = documentPasteEdit.getAdditionalEdit();
		List<TextEdit> changes = edit.getChanges().get(uri);
		assertNotNull(changes);
		assertEquals(1, changes.size());
		//@formatter:off
		String expect = "\r\n" +
						"\r\n" +
						"import java.util.List;\r\n" +
						"import java.util.Set;\r\n" +
						"\r\n";
		//@formatter:on
		assertEquals(expect, changes.get(0).getNewText());
		assertEquals(new Range(new Position(0, 10), new Position(2, 0)), changes.get(0).getRange());
	}

	@Test
	public void testGetAddImportsAmbigousWorkspaceEdit() throws CoreException {
		//@formatter:off
		ICompilationUnit unit = fPackageP.createCompilationUnit("B.java", "package p;\r\n" +
				"\r\n" +
				"public class B {\r\n" +
				"}"
				, true, null);
		ICompilationUnit unit1 = fPackageTest.createCompilationUnit("List.java", "package test;\r\n" +
				"\r\n" +
				"public class List {\r\n" +
				"}"
				, true, null);
		String content = "	public List<String> b;\r\n" +
						"	public Set<String> c;\r\n";
		//@formatter:on
		String uri = JDTUtils.toURI(unit);
		Range range = new Range(new Position(3, 0), new Position(3, 0));
		PasteEventParams params = new PasteEventParams(new Location(uri, range), content, null, new FormattingOptions(4, false));
		DocumentPasteEdit documentPasteEdit = PasteEventHandler.getMissingImportsWorkspaceEdit(params, unit, monitor);
		assertNotNull(documentPasteEdit);
		WorkspaceEdit edit = documentPasteEdit.getAdditionalEdit();
		List<TextEdit> changes = edit.getChanges().get(uri);
		assertNotNull(changes);
		assertEquals(1, changes.size());
		//@formatter:off
		String expect = "\r\n" +
						"\r\n" +
						"import java.util.Set;\r\n" +
						"\r\n";
		//@formatter:on
		assertEquals(expect, changes.get(0).getNewText());
		assertEquals(new Range(new Position(0, 10), new Position(2, 0)), changes.get(0).getRange());
	}

	@Test
	public void testGetAddImportsResolveAmbigousWorkspaceEdit() throws CoreException {
		//@formatter:off
		ICompilationUnit unit = fPackageP.createCompilationUnit("B.java", "package p;\r\n" +
				"\r\n" +
				"public class B {\r\n" +
				"}"
				, true, null);
		ICompilationUnit unit1 = fPackageTest.createCompilationUnit("List.java", "package test;\r\n" +
				"\r\n" +
				"public class List {\r\n" +
				"}"
				, true, null);
		ICompilationUnit unit2 = fPackageP.createCompilationUnit("C.java", "package p;\r\n" +
				"\r\n" +
				"import java.util.List;\r\n" +
				"import java.util.Set;\r\n" +
				"public class C {\r\n" +
				"	public List<String> b;\r\n" +
				"	public Set<String> c;\r\n" +
				"}"
				, true, null);
		String content = "	public List<String> b;\r\n" +
						"	public Set<String> c;\r\n";
		//@formatter:on
		String uri = JDTUtils.toURI(unit);
		Range range = new Range(new Position(3, 0), new Position(3, 0));
		PasteEventParams params = new PasteEventParams(new Location(uri, range), content, JDTUtils.toURI(unit2), new FormattingOptions(4, false));
		DocumentPasteEdit documentPasteEdit = PasteEventHandler.getMissingImportsWorkspaceEdit(params, unit, monitor);
		assertNotNull(documentPasteEdit);
		WorkspaceEdit edit = documentPasteEdit.getAdditionalEdit();
		List<TextEdit> changes = edit.getChanges().get(uri);
		assertNotNull(changes);
		assertEquals(1, changes.size());
		String expect = "\r\n" +
						"\r\n" +
						"import java.util.List;\r\n" +
						"import java.util.Set;\r\n" +
						"\r\n";
		assertEquals(expect, changes.get(0).getNewText());
		assertEquals(new Range(new Position(0, 10), new Position(2, 0)), changes.get(0).getRange());
	}

}
