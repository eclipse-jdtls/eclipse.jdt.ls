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
package org.eclipse.jdt.ls.core.internal.cleanup;

import static org.junit.Assert.assertEquals;

import java.io.File;
import java.util.Arrays;
import java.util.Collections;
import java.util.Hashtable;
import java.util.List;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IPackageFragment;
import org.eclipse.jdt.core.IPackageFragmentRoot;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.formatter.DefaultCodeFormatterConstants;
import org.eclipse.jdt.ls.core.internal.WorkspaceHelper;
import org.eclipse.jdt.ls.core.internal.correction.TestOptions;
import org.eclipse.jdt.ls.core.internal.managers.AbstractMavenBasedTest;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.eclipse.lsp4j.TextEdit;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

/**
 * Tests for clean ups.
 */
public class CleanUpsTest extends AbstractMavenBasedTest {

	public static CleanUpRegistry registry = new CleanUpRegistry();

	private IJavaProject javaProject;
	private IPackageFragmentRoot fSourceFolder;
	private IProject project;
	private IPackageFragment pack1;

	@Before
	public void setup() throws Exception {
		importProjects("maven/quickstart");
		project = WorkspaceHelper.getProject("quickstart");
		javaProject = JavaCore.create(project);
		Hashtable<String, String> options = TestOptions.getDefaultOptions();
		options.put(DefaultCodeFormatterConstants.FORMATTER_NUMBER_OF_EMPTY_LINES_TO_PRESERVE, String.valueOf(99));
		javaProject.setOptions(options);
		fSourceFolder = javaProject.getPackageFragmentRoot(javaProject.getProject().getFolder("src/main/java"));
		File src = fSourceFolder.getResource().getLocation().toFile();
		src.mkdirs();
		project.getProject().refreshLocal(IResource.DEPTH_INFINITE, new NullProgressMonitor());
		pack1 = fSourceFolder.createPackageFragment("test1", false, monitor);
	}

	@After
	public void teardown() throws Exception {
		pack1.delete(false, null);
	}

	@Test
	public void testNoCleanUp() throws Exception {

		String contents = "" + //
				"package test1;\n" + //
				"public class A implements Runnable {\n" + //
				"    public void run() {} \n" + //
				"    /**\n" + //
				"     * @deprecated\n" + //
				"     */\n" + //
				"    public void destroy() {} \n" + //
				"}\n";
		ICompilationUnit unit = pack1.createCompilationUnit("A.java", contents, false, monitor);
		String uri = unit.getUnderlyingResource().getLocationURI().toString();

		List<TextEdit> textEdits = registry.getEditsForAllActiveCleanUps(new TextDocumentIdentifier(uri), Collections.emptyList(), null);
		assertEquals(0, textEdits.size());
	}

	@Test
	public void testOverrideCleanUp() throws Exception {

		IPackageFragment pack1 = fSourceFolder.createPackageFragment("test1", false, monitor);

		String contents = "" + //
				"package test1;\n" + //
				"public class A implements Runnable {\n" + //
				"    public void run() {} \n" + //
				"    /**\n" + //
				"     * @deprecated\n" + //
				"     */\n" + //
				"    public void destroy() {} \n" + //
				"}\n";
		ICompilationUnit unit = pack1.createCompilationUnit("A.java", contents, false, monitor);
		String uri = unit.getUnderlyingResource().getLocationURI().toString();

		List<TextEdit> textEdits = registry.getEditsForAllActiveCleanUps(new TextDocumentIdentifier(uri), Arrays.asList("addOverride"), monitor);
		assertEquals(1, textEdits.size());
		assertEquals(te("@Override\n    ", r(2, 4, 2, 4)), textEdits.get(0));
	}

	@Test
	public void testDeprecatedCleanUp() throws Exception {

		String contents = "" + //
				"package test1;\n" + //
				"public class A implements Runnable {\n" + //
				"    public void run() {} \n" + //
				"    /**\n" + //
				"     * @deprecated\n" + //
				"     */\n" + //
				"    public void destroy() {} \n" + //
				"}\n";
		ICompilationUnit unit = pack1.createCompilationUnit("A.java", contents, false, monitor);
		String uri = unit.getUnderlyingResource().getLocationURI().toString();

		List<TextEdit> textEdits = registry.getEditsForAllActiveCleanUps(new TextDocumentIdentifier(uri), Arrays.asList("addDeprecated"), monitor);
		assertEquals(1, textEdits.size());
		assertEquals(te("@Deprecated\n    ", r(6, 4, 6, 4)), textEdits.get(0));
	}

	@Test
	public void testUseThisCleanUp() throws Exception {
		String contents = "" + //
				"package test1;\n" + //
				"public class A {\n" + //
				"    private int value;\n" + //
				"    public int getValue() {\n" + //
				"        return value;\n" + //
				"    }\n" + //
				"}\n";
		ICompilationUnit unit = pack1.createCompilationUnit("A.java", contents, false, monitor);
		String uri = unit.getUnderlyingResource().getLocationURI().toString();

		List<TextEdit> textEdits = registry.getEditsForAllActiveCleanUps(new TextDocumentIdentifier(uri), Arrays.asList("qualifyMembers"), monitor);
		assertEquals(1, textEdits.size());
		assertEquals(te("this.value", r(4, 15, 4, 20)), textEdits.get(0));
	}

	@Test
	public void testUseClassNameCleanUp1() throws Exception {
		String contents = "" + //
				"package test1;\n" + //
				"public class A {\n" + //
				"    private static final int VALUE = 10;\n" + //
				"    public int getValue() {\n" + //
				"        return VALUE;\n" + //
				"    }\n" + //
				"}\n";
		ICompilationUnit unit = pack1.createCompilationUnit("A.java", contents, false, monitor);
		String uri = unit.getUnderlyingResource().getLocationURI().toString();

		List<TextEdit> textEdits = registry.getEditsForAllActiveCleanUps(new TextDocumentIdentifier(uri), Arrays.asList("qualifyStaticMembers"), monitor);
		assertEquals(1, textEdits.size());
		assertEquals(te("A.VALUE", r(4, 15, 4, 20)), textEdits.get(0));
	}

	@Test
	public void testUseClassNameCleanUp2() throws Exception {
		String contents = "" + //
				"package test1;\n" + //
				"import static java.lang.System.out;\n" + //
				"public class A {\n" + //
				"    private static final int VALUE = 10;\n" + //
				"    public int getValue() {\n" + //
				"        out.println(\"moo\");\n" + //
				"        return A.VALUE;\n" + //
				"    }\n" + //
				"}\n";
		ICompilationUnit unit = pack1.createCompilationUnit("A.java", contents, false, monitor);
		String uri = unit.getUnderlyingResource().getLocationURI().toString();

		List<TextEdit> textEdits = registry.getEditsForAllActiveCleanUps(new TextDocumentIdentifier(uri), Arrays.asList("qualifyStaticMembers"), monitor);
		assertEquals(1, textEdits.size());
		assertEquals(te("System.out", r(5, 8, 5, 11)), textEdits.get(0));
	}

	@Test
	public void testAccessCleanUpsDontInterfere() throws Exception {
		String contents = "" + //
				"package test1;\n" + //
				"public class A {\n" + //
				"    private static final int NUMBER = 10;\n" + //
				"    private static final int value;\n" + //
				"    public int getValue() {\n" + //
				"        return this.value;\n" + //
				"    }\n" + //
				"}\n";
		ICompilationUnit unit = pack1.createCompilationUnit("A.java", contents, false, monitor);
		String uri = unit.getUnderlyingResource().getLocationURI().toString();

		List<TextEdit> textEdits = registry.getEditsForAllActiveCleanUps(new TextDocumentIdentifier(uri), Arrays.asList("qualifyStaticMembers", "qualifyMembers"), monitor);
		assertEquals(0, textEdits.size());

		contents = "" + //
				"package test1;\n" + //
				"public class A {\n" + //
				"    private static final int NUMBER = 10;\n" + //
				"    private static final int value;\n" + //
				"    public int getValue() {\n" + //
				"        return A.NUMBER;\n" + //
				"    }\n" + //
				"}\n";
		unit = pack1.createCompilationUnit("A.java", contents, true, monitor);

		textEdits = registry.getEditsForAllActiveCleanUps(new TextDocumentIdentifier(uri), Arrays.asList("qualifyStaticMembers", "qualifyMembers"), monitor);
		assertEquals(0, textEdits.size());
	}

	private static TextEdit te(String contents, Range range) {
		TextEdit textEdit = new TextEdit();
		textEdit.setNewText(contents);
		textEdit.setRange(range);
		return textEdit;
	}

	private static Range r(int beginLine, int beginChar, int endLine, int endChar) {
		Position start = new Position(beginLine, beginChar);
		Position end = new Position(endLine, endChar);
		Range range = new Range(start, end);
		return range;
	}

}
