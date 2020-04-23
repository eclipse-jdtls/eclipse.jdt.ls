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
		buf.append("    public void foo1() {}\n");
		buf.append("    private void foo2() {}\n");
		buf.append("    protected void foo3() {}\n");
		buf.append("    static void foo4() {}\n");
		buf.append("    @Deprecated\n");
		buf.append("    void foo5() {}\n");
		buf.append("    public static void main(String args[]) {}\n");
		buf.append("}\n");

		ICompilationUnit cu = pack1.createCompilationUnit("E.java", buf.toString(), false, null);

		SemanticTokensLegend legend = SemanticTokensCommand.getLegend();
		SemanticTokens tokens = SemanticTokensCommand.provide(JDTUtils.toURI(cu));
		Map<Integer, Map<Integer, int[]>> decodedTokens = decode(tokens);
		assertToken(decodedTokens, legend, 4, 16, 4, "function", Arrays.asList("public"));
		assertToken(decodedTokens, legend, 5, 17, 4, "function", Arrays.asList("private"));
		assertToken(decodedTokens, legend, 6, 19, 4, "function", Arrays.asList("protected"));
		assertToken(decodedTokens, legend, 7, 16, 4, "function", Arrays.asList("static"));
		assertToken(decodedTokens, legend, 9, 9, 4, "function", Arrays.asList("deprecated"));
		assertToken(decodedTokens, legend, 10, 23, 4, "function", Arrays.asList("public", "static"));
	}


	@Test
	public void testSemanticTokens_variables() throws JavaModelException {
		IPackageFragment pack1 = fSourceFolder.createPackageFragment("test1", false, null);
		StringBuilder buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("\n");
		buf.append("public class E {\n");
		buf.append("\n");
		buf.append("    public String bar1;\n");
		buf.append("    private int bar2;\n");
		buf.append("    protected boolean bar3;\n");
		buf.append("    final String bar4;\n");
		buf.append("    static int bar5;\n");
		buf.append("    public final static int bar6 = 1;\n");
		buf.append("}\n");
		ICompilationUnit cu = pack1.createCompilationUnit("E.java", buf.toString(), false, null);
		SemanticTokensLegend legend = SemanticTokensCommand.getLegend();
		SemanticTokens tokens = SemanticTokensCommand.provide(JDTUtils.toURI(cu));
		Map<Integer, Map<Integer, int[]>> decodedTokens = decode(tokens);
		assertToken(decodedTokens, legend, 4, 18, 4, "variable", Arrays.asList("public"));
		assertToken(decodedTokens, legend, 5, 16, 4, "variable", Arrays.asList("private"));
		assertToken(decodedTokens, legend, 6, 22, 4, "variable", Arrays.asList("protected"));
		assertToken(decodedTokens, legend, 7, 17, 4, "variable", Arrays.asList("readonly"));
		assertToken(decodedTokens, legend, 8, 15, 4, "variable", Arrays.asList("static"));
		assertToken(decodedTokens, legend, 9, 28, 4, "variable", Arrays.asList("static", "public", "readonly"));
	}

	@Test
	public void testSemanticTokens_types() throws JavaModelException {
		IPackageFragment pack1 = fSourceFolder.createPackageFragment("test1", false, null);
		StringBuilder buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("\n");
		buf.append("public class E {\n");
		buf.append("\n");
		buf.append("    public String // comments\n");
		buf.append("      s1 = \"Happy\",\n");
		buf.append("      s2, s3;\n");
		buf.append("}\n");
		ICompilationUnit cu = pack1.createCompilationUnit("E.java", buf.toString(), false, null);
		SemanticTokensLegend legend = SemanticTokensCommand.getLegend();
		SemanticTokens tokens = SemanticTokensCommand.provide(JDTUtils.toURI(cu));
		Map<Integer, Map<Integer, int[]>> decodedTokens = decode(tokens);
		assertToken(decodedTokens, legend, 4, 11, 6, "type", Arrays.asList());
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
