/*******************************************************************************
 * Copyright (c) 2020 Microsoft Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *     Microsoft Corporation - initial API and implementation
 *******************************************************************************/

package org.eclipse.jdt.ls.core.internal.commands;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.List;
import java.util.Map;

import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IPackageFragment;
import org.eclipse.jdt.core.IPackageFragmentRoot;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.ls.core.internal.JDTUtils;
import org.eclipse.jdt.ls.core.internal.correction.TestOptions;
import org.eclipse.jdt.ls.core.internal.managers.AbstractProjectsManagerBasedTest;
import org.eclipse.jdt.ls.core.internal.semantictokens.SemanticTokens;
import org.eclipse.jdt.ls.core.internal.semantictokens.SemanticTokensLegend;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.runners.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class SemanticTokensCommandTest extends AbstractProjectsManagerBasedTest {
	private IJavaProject fJProject1;
	private IPackageFragmentRoot fSourceFolder;

	@Before
	public void setup() throws Exception {
		fJProject1 = newEmptyProject();
		Hashtable<String, String> options = TestOptions.getDefaultOptions();
		fJProject1.setOptions(options);
		fSourceFolder = fJProject1.getPackageFragmentRoot(fJProject1.getProject().getFolder("src"));
	}

	@Test
	public void testSemanticTokens_methods() throws JavaModelException {
		IPackageFragment pack1 = fSourceFolder.createPackageFragment("test1", false, null);
		StringBuilder buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("\n");
		buf.append("public class E {\n");
		buf.append("\n");
		buf.append("	public void foo1() {}\n");
		buf.append("	private void foo2() {}\n");
		buf.append("	protected void foo3() {}\n");
		buf.append("	static void foo4() {}\n");
		buf.append("	@Deprecated\n");
		buf.append("	void foo5() {}\n");
		buf.append("	public static void main(String args[]) {}\n");
		buf.append("}\n");

		ICompilationUnit cu = pack1.createCompilationUnit("E.java", buf.toString(), false, null);
		SemanticTokensLegend legend = SemanticTokensCommand.getLegend();
		SemanticTokens tokens = SemanticTokensCommand.provide(JDTUtils.toURI(cu));
		Map<Integer, Map<Integer, int[]>> decodedTokens = decode(tokens);

		assertToken(decodedTokens, legend, 4, 13, 4, "function", Arrays.asList("public", "declaration"));
		assertToken(decodedTokens, legend, 5, 14, 4, "function", Arrays.asList("private", "declaration"));
		assertToken(decodedTokens, legend, 6, 16, 4, "function", Arrays.asList("protected", "declaration"));
		assertToken(decodedTokens, legend, 7, 13, 4, "function", Arrays.asList("static", "declaration"));
		assertToken(decodedTokens, legend, 9, 6, 4, "function", Arrays.asList("deprecated", "declaration"));
		assertToken(decodedTokens, legend, 10, 20, 4, "function", Arrays.asList("public", "static", "declaration"));
	}

	@Test
	public void testSemanticTokens_constructors() throws JavaModelException {
		IPackageFragment pack1 = fSourceFolder.createPackageFragment("test1", false, null);
		StringBuilder buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("\n");
		buf.append("public class E {\n");
		buf.append("\n");
		buf.append("	private E() {\n");
		buf.append("		E e = new E();\n");
		buf.append("		E.InnerClass innerClass1 = new E.InnerClass();\n");
		buf.append("		E.InnerClass innerClass2 = new @SomeAnnotation E.InnerClass();\n");
		buf.append("		E.InnerClass<String> innerClass3 = new E.InnerClass<>();\n");
		buf.append("	}\n");
		buf.append("\n");
		buf.append("	protected class InnerClass<T> {}\n");
		buf.append("}\n");

		ICompilationUnit cu = pack1.createCompilationUnit("E.java", buf.toString(), false, null);
		SemanticTokensLegend legend = SemanticTokensCommand.getLegend();
		SemanticTokens tokens = SemanticTokensCommand.provide(JDTUtils.toURI(cu));
		Map<Integer, Map<Integer, int[]>> decodedTokens = decode(tokens);

		assertToken(decodedTokens, legend, 4, 9, 1, "function", Arrays.asList("private", "declaration"));
		assertToken(decodedTokens, legend, 5, 12, 1, "function", Arrays.asList("private"));
		assertToken(decodedTokens, legend, 6, 35, 10, "function", Arrays.asList("protected"));
		assertToken(decodedTokens, legend, 7, 51, 10, "function", Arrays.asList("protected"));
		assertToken(decodedTokens, legend, 8, 43, 10, "function", Arrays.asList("protected"));
	}

	@Test
	public void testSemanticTokens_properties() throws JavaModelException {
		IPackageFragment pack1 = fSourceFolder.createPackageFragment("test1", false, null);
		StringBuilder buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("\n");
		buf.append("public class E {\n");
		buf.append("\n");
		buf.append("	public String bar1;\n");
		buf.append("	private int bar2;\n");
		buf.append("	protected boolean bar3;\n");
		buf.append("	final String bar4;\n");
		buf.append("	static int bar5;\n");
		buf.append("	public final static int bar6 = 1;\n");
		buf.append("\n");
		buf.append("	enum SomeEnum {\n");
		buf.append("		FIRST,\n");
		buf.append("		SECOND\n");
		buf.append("	}\n");
		buf.append("}\n");
		ICompilationUnit cu = pack1.createCompilationUnit("E.java", buf.toString(), false, null);
		SemanticTokensLegend legend = SemanticTokensCommand.getLegend();
		SemanticTokens tokens = SemanticTokensCommand.provide(JDTUtils.toURI(cu));
		Map<Integer, Map<Integer, int[]>> decodedTokens = decode(tokens);
		assertToken(decodedTokens, legend, 4, 15, 4, "property", Arrays.asList("public", "declaration"));
		assertToken(decodedTokens, legend, 5, 13, 4, "property", Arrays.asList("private", "declaration"));
		assertToken(decodedTokens, legend, 6, 19, 4, "property", Arrays.asList("protected", "declaration"));
		assertToken(decodedTokens, legend, 7, 14, 4, "property", Arrays.asList("readonly", "declaration"));
		assertToken(decodedTokens, legend, 8, 12, 4, "property", Arrays.asList("static", "declaration"));
		assertToken(decodedTokens, legend, 9, 25, 4, "property", Arrays.asList("static", "public", "readonly", "declaration"));
		assertToken(decodedTokens, legend, 12, 2, 5, "enumMember", Arrays.asList("static", "public", "readonly", "declaration"));
		assertToken(decodedTokens, legend, 13, 2, 6, "enumMember", Arrays.asList("static", "public", "readonly", "declaration"));
	}

	@Test
	public void testSemanticTokens_variables() throws JavaModelException {
		IPackageFragment pack1 = fSourceFolder.createPackageFragment("test1", false, null);
		StringBuilder buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("\n");
		buf.append("public class E {\n");
		buf.append("\n");
		buf.append("	public static void foo(String string) {\n");
		buf.append("		String bar1 = string;\n");
		buf.append("		String bar2;\n");
		buf.append("		final String bar3 = \"test\";\n");
		buf.append("	}\n");
		buf.append("}\n");
		ICompilationUnit cu = pack1.createCompilationUnit("E.java", buf.toString(), false, null);
		SemanticTokensLegend legend = SemanticTokensCommand.getLegend();
		SemanticTokens tokens = SemanticTokensCommand.provide(JDTUtils.toURI(cu));
		Map<Integer, Map<Integer, int[]>> decodedTokens = decode(tokens);
		assertToken(decodedTokens, legend, 4, 31, 6, "parameter", Arrays.asList("declaration"));
		assertToken(decodedTokens, legend, 5, 16, 6, "parameter", Arrays.asList());
		assertToken(decodedTokens, legend, 6, 9, 4, "variable", Arrays.asList("declaration"));
		assertToken(decodedTokens, legend, 7, 15, 4, "variable", Arrays.asList("readonly", "declaration"));
	}

	@Test
	public void testSemanticTokens_types() throws JavaModelException {
		IPackageFragment pack1 = fSourceFolder.createPackageFragment("test1", false, null);
		StringBuilder buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("\n");
		buf.append("public class E {\n");
		buf.append("\n");
		buf.append("	public String // comments\n");
		buf.append("		s1 = \"Happy\",\n");
		buf.append("		s2, s3;\n");
		buf.append("	class SomeClass<T> {}\n");
		buf.append("	interface SomeInterface {}\n");
		buf.append("	enum SomeEnum {}\n");
		buf.append("	@interface SomeAnnotation {}\n");
		buf.append("}\n");
		ICompilationUnit cu = pack1.createCompilationUnit("E.java", buf.toString(), false, null);
		SemanticTokensLegend legend = SemanticTokensCommand.getLegend();
		SemanticTokens tokens = SemanticTokensCommand.provide(JDTUtils.toURI(cu));
		Map<Integer, Map<Integer, int[]>> decodedTokens = decode(tokens);
		assertToken(decodedTokens, legend, 4, 8, 6, "class", Arrays.asList("public", "readonly"));
		assertToken(decodedTokens, legend, 7, 7, 9, "class", Arrays.asList("declaration"));
		assertToken(decodedTokens, legend, 7, 17, 1, "typeParameter", Arrays.asList("declaration"));
		assertToken(decodedTokens, legend, 8, 11, 13, "interface", Arrays.asList("declaration", "static"));
		assertToken(decodedTokens, legend, 9, 6, 8, "enum", Arrays.asList("declaration", "static", "readonly"));
		assertToken(decodedTokens, legend, 10, 12, 14, "annotation", Arrays.asList("declaration", "static"));
	}

	@Test
	public void testSemanticTokens_packages() throws JavaModelException {
		IPackageFragment pack1 = fSourceFolder.createPackageFragment("test1", false, null);
		StringBuilder buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("\n");
		buf.append("import static java.lang.Math.PI;\n");
		buf.append("import java.util.List;\n");
		buf.append("import java.NonExistentClass;\n");
		buf.append("import java.nio.*;\n");
		buf.append("import java.*;\n");
		buf.append("\n");
		buf.append("public class E {\n");
		buf.append("}\n");
		ICompilationUnit cu = pack1.createCompilationUnit("E.java", buf.toString(), false, null);
		SemanticTokensLegend legend = SemanticTokensCommand.getLegend();
		SemanticTokens tokens = SemanticTokensCommand.provide(JDTUtils.toURI(cu));
		Map<Integer, Map<Integer, int[]>> decodedTokens = decode(tokens);
		assertToken(decodedTokens, legend, 2, 14, 4, "namespace", Arrays.asList());
		assertToken(decodedTokens, legend, 2, 19, 4, "namespace", Arrays.asList());
		assertToken(decodedTokens, legend, 3, 7, 4, "namespace", Arrays.asList());
		assertToken(decodedTokens, legend, 3, 12, 4, "namespace", Arrays.asList());
		assertToken(decodedTokens, legend, 4, 7, 4, "namespace", Arrays.asList());
		assertToken(decodedTokens, legend, 5, 7, 4, "namespace", Arrays.asList());
		assertToken(decodedTokens, legend, 5, 12, 3, "namespace", Arrays.asList());
		assertToken(decodedTokens, legend, 6, 7, 4, "namespace", Arrays.asList());
	}

	@Test
	public void testSemanticTokens_annotations() throws JavaModelException {
		IPackageFragment pack1 = fSourceFolder.createPackageFragment("test1", false, null);
		StringBuilder buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("\n");
		buf.append("public class E {\n");
		buf.append("	@SuppressWarnings\n");
		buf.append("	public void foo {}\n");
		buf.append("	@SuppressWarnings(\"all\")\n");
		buf.append("	public void bar {}\n");
		buf.append("	@SuppressWarnings(value=\"all\")\n");
		buf.append("	public void baz {}\n");
		buf.append("}\n");
		ICompilationUnit cu = pack1.createCompilationUnit("E.java", buf.toString(), false, null);
		SemanticTokensLegend legend = SemanticTokensCommand.getLegend();
		SemanticTokens tokens = SemanticTokensCommand.provide(JDTUtils.toURI(cu));
		Map<Integer, Map<Integer, int[]>> decodedTokens = decode(tokens);
		assertToken(decodedTokens, legend, 3, 2, 16, "annotation", Arrays.asList("public"));
		assertToken(decodedTokens, legend, 5, 2, 16, "annotation", Arrays.asList("public"));
		assertToken(decodedTokens, legend, 7, 2, 16, "annotation", Arrays.asList("public"));
		assertToken(decodedTokens, legend, 7, 19, 5, "annotationMember", Arrays.asList("public", "abstract"));
	}

	private void assertModifiers(List<String> tokenModifiers, int encodedModifiers, List<String> modifierStrings) {
		int cnt = 0;
		for (int i=0; i<tokenModifiers.size(); i++) {
			if((encodedModifiers & (0b00000001 << i)) != 0) {
				String current = tokenModifiers.get(i);
				assertTrue(modifierStrings.contains(current));
				cnt += 1;
			}
		}
		assertEquals(cnt, modifierStrings.size());
	}

	private void assertToken(Map<Integer, Map<Integer, int[]>> decodedTokens, SemanticTokensLegend legend, int line, int column, int length, String tokenTypeString, List<String> modifierStrings) {
		Map<Integer, int[]> tokensOfTheLine = decodedTokens.get(line);
		assertNotNull(tokensOfTheLine);
		int[] token = tokensOfTheLine.get(column); // 0: length, 1: typeIndex, 2: encodedModifiers
		assertNotNull(token);
		assertEquals(length, token[0]);
		assertEquals(tokenTypeString, legend.getTokenTypes().get(token[1]));
		assertModifiers(legend.getTokenModifiers(), token[2], modifierStrings);
	}

	private Map<Integer, Map<Integer, int[]>> decode(SemanticTokens tokens) {
		List<Integer> data = tokens.getData();
		int total = data.size() / 5;
		Map<Integer, Map<Integer, int[]>> decodedTokens = new HashMap<>();
		int currentLine = 0;
		int currentColumn = 0;
		for(int i = 0; i<total; i++) {
			int offset = 5 * i;
			int deltaLine = data.get(offset).intValue();
			int deltaColumn = data.get(offset+1).intValue();
			int length = data.get(offset+2).intValue();
			int typeIndex = data.get(offset+3).intValue();
			int encodedModifiers = data.get(offset+4).intValue();

			if (deltaLine > 0) {
				currentLine += deltaLine;
				currentColumn = deltaColumn;
			} else {
				currentColumn += deltaColumn;
			}
			Map<Integer, int[]> tokensOfTheLine = decodedTokens.getOrDefault(currentLine, new HashMap<>());
			tokensOfTheLine.put(currentColumn, new int[] {length, typeIndex, encodedModifiers});
			decodedTokens.putIfAbsent(currentLine, tokensOfTheLine);
		}
		return decodedTokens;
	}
}
