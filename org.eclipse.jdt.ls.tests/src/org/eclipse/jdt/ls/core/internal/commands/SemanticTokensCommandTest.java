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
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.Hashtable;
import java.util.List;

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
		assertToken(tokens, 0, legend, 4, 16, 4, "method", Arrays.asList("public"));
		assertToken(tokens, 1, legend, 1, 17, 4, "method", Arrays.asList("private"));
		assertToken(tokens, 2, legend, 1, 19, 4, "method", Arrays.asList("protected"));
		assertToken(tokens, 3, legend, 1, 16, 4, "method", Arrays.asList("static"));
		assertToken(tokens, 4, legend, 2, 9, 4, "method", Arrays.asList("deprecated"));
		assertToken(tokens, 5, legend, 1, 23, 4, "method", Arrays.asList("public", "static"));
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
		assertToken(tokens, 0, legend, 4, 18, 4, "variable", Arrays.asList("public"));
		assertToken(tokens, 1, legend, 1, 16, 4, "variable", Arrays.asList("private"));
		assertToken(tokens, 2, legend, 1, 22, 4, "variable", Arrays.asList("protected"));
		assertToken(tokens, 3, legend, 1, 17, 4, "variable", Arrays.asList("final"));
		assertToken(tokens, 4, legend, 1, 15, 4, "variable", Arrays.asList("static"));
		assertToken(tokens, 5, legend, 1, 28, 4, "variable", Arrays.asList("static", "public", "final"));
	}

	private void assertToken(SemanticTokens tokens, int tokenIndex, SemanticTokensLegend legend, int deltaLine, int deltaCol, int length, String tokenTypeString, List<String> modifierStrings) {
		List<Integer> data = tokens.getData();

		int offset = 5 * tokenIndex;
		assertEquals(deltaLine, data.get(offset + 0).intValue());
		assertEquals(deltaCol, data.get(offset + 1).intValue());
		assertEquals(length, data.get(offset + 2).intValue());

		List<String> tokenTypes = legend.getTokenTypes();
		int tokenTypeIndex = data.get(offset + 3).intValue();
		assertEquals(tokenTypes.get(tokenTypeIndex), tokenTypeString);

		List<String> tokenModifiers = legend.getTokenModifiers();
		int encodedModifiers = data.get(offset + 4).intValue();
		assertModifiers(tokenModifiers, encodedModifiers, modifierStrings);
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
}
