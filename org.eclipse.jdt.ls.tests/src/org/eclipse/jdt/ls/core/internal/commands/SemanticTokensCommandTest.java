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
import java.util.List;
import java.util.Map;

import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IPackageFragment;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.ls.core.internal.JDTUtils;
import org.eclipse.jdt.ls.core.internal.WorkspaceHelper;
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
	private IJavaProject semanticTokensProject;
	private IPackageFragment fooPackage;
	private SemanticTokensLegend legend = SemanticTokensCommand.getLegend();
	private String classFileUri = "jdt://contents/foo.jar/foo/bar.class?%3Dsemantic-tokens%2Ffoo.jar%3Cfoo%28bar.class";

	@Before
	public void setup() throws Exception {
		importProjects("eclipse/semantic-tokens");
		semanticTokensProject = JavaCore.create(WorkspaceHelper.getProject("semantic-tokens"));
		semanticTokensProject.setOptions(TestOptions.getDefaultOptions());
		fooPackage = semanticTokensProject.getPackageFragmentRoot(
			semanticTokensProject.getProject().getFolder("src")
		).getPackageFragment("foo");
	}

	@Test
	public void testSemanticTokens_SourceAttachment() {
		SemanticTokens tokens = SemanticTokensCommand.provide(classFileUri);
		Map<Integer, Map<Integer, int[]>> decodedTokens = decode(tokens);

		assertToken(decodedTokens, 0, 8, 3, "namespace");
		assertToken(decodedTokens, 2, 13, 3, "class", "public", "declaration");
		assertToken(decodedTokens, 3, 22, 3, "function", "public", "static", "declaration");
		assertToken(decodedTokens, 3, 33, 1, "parameter", "declaration");
		assertToken(decodedTokens, 4, 12, 3, "variable", "declaration");
	}

	@Test
	public void testSemanticTokens_Methods() throws JavaModelException {
		Map<Integer, Map<Integer, int[]>> decodedTokens = decodeSourceFile("Methods.java");

		assertToken(decodedTokens, 4, 13, 4, "function", "public", "declaration");
		assertToken(decodedTokens, 5, 14, 4, "function", "private", "declaration");
		assertToken(decodedTokens, 6, 16, 4, "function", "protected", "declaration");
		assertToken(decodedTokens, 7, 13, 4, "function", "static", "declaration");
		assertToken(decodedTokens, 9, 6, 4, "function", "deprecated", "declaration");
		assertToken(decodedTokens, 10, 20, 4, "function", "public", "static", "declaration");
	}

	@Test
	public void testSemanticTokens_Constructors() throws JavaModelException {
		Map<Integer, Map<Integer, int[]>> decodedTokens = decodeSourceFile("Constructors.java");

		assertToken(decodedTokens, 4, 9, 12, "function", "private", "declaration");
		assertToken(decodedTokens, 5, 23, 12, "function", "private");
		assertToken(decodedTokens, 6, 48, 10, "function", "protected");
		assertToken(decodedTokens, 7, 64, 10, "function", "protected");
		assertToken(decodedTokens, 8, 56, 10, "function", "protected");
	}

	@Test
	public void testSemanticTokens_Properties() throws JavaModelException {
		Map<Integer, Map<Integer, int[]>> decodedTokens = decodeSourceFile("Properties.java");

		assertToken(decodedTokens, 4, 15, 4, "property", "public", "declaration");
		assertToken(decodedTokens, 5, 13, 4, "property", "private", "declaration");
		assertToken(decodedTokens, 6, 19, 4, "property", "protected", "declaration");
		assertToken(decodedTokens, 7, 14, 4, "property", "readonly", "declaration");
		assertToken(decodedTokens, 8, 12, 4, "property", "static", "declaration");
		assertToken(decodedTokens, 9, 25, 4, "property", "static", "public", "readonly", "declaration");
		assertToken(decodedTokens, 12, 2, 5, "enumMember", "static", "public", "readonly", "declaration");
		assertToken(decodedTokens, 13, 2, 6, "enumMember", "static", "public", "readonly", "declaration");
	}

	@Test
	public void testSemanticTokens_Variables() throws JavaModelException {
		Map<Integer, Map<Integer, int[]>> decodedTokens = decodeSourceFile("Variables.java");

		assertToken(decodedTokens, 4, 31, 6, "parameter", "declaration");
		assertToken(decodedTokens, 5, 16, 6, "parameter");
		assertToken(decodedTokens, 6, 9, 4, "variable", "declaration");
		assertToken(decodedTokens, 7, 15, 4, "variable", "readonly", "declaration");
	}

	@Test
	public void testSemanticTokens_Types() throws JavaModelException {
		Map<Integer, Map<Integer, int[]>> decodedTokens = decodeSourceFile("Types.java");

		assertToken(decodedTokens, 4, 8, 6, "class", "public", "readonly");
		assertToken(decodedTokens, 7, 7, 9, "class", "declaration");
		assertToken(decodedTokens, 7, 17, 1, "typeParameter", "declaration");
		assertToken(decodedTokens, 8, 11, 13, "interface", "declaration", "static");
		assertToken(decodedTokens, 9, 6, 8, "enum", "declaration", "static", "readonly");
		assertToken(decodedTokens, 10, 12, 14, "annotation", "declaration", "static");
	}

	@Test
	public void testSemanticTokens_Packages() throws JavaModelException {
		Map<Integer, Map<Integer, int[]>> decodedTokens = decodeSourceFile("Packages.java");

		assertToken(decodedTokens, 2, 14, 4, "namespace");
		assertToken(decodedTokens, 2, 19, 4, "namespace");
		assertToken(decodedTokens, 3, 7, 4, "namespace");
		assertToken(decodedTokens, 3, 12, 4, "namespace");
		assertToken(decodedTokens, 4, 7, 4, "namespace");
		assertToken(decodedTokens, 5, 7, 4, "namespace");
		assertToken(decodedTokens, 5, 12, 3, "namespace");
		assertToken(decodedTokens, 6, 7, 4, "namespace");
	}

	@Test
	public void testSemanticTokens_Annotations() throws JavaModelException {
		Map<Integer, Map<Integer, int[]>> decodedTokens = decodeSourceFile("Annotations.java");

		assertToken(decodedTokens, 4, 2, 16, "annotation", "public");
		assertToken(decodedTokens, 7, 2, 16, "annotation", "public");
		assertToken(decodedTokens, 10, 2, 16, "annotation", "public");
		assertToken(decodedTokens, 10, 19, 5, "annotationMember", "public", "abstract");
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

	private void assertToken(Map<Integer, Map<Integer, int[]>> decodedTokens, int line, int column, int length, String tokenTypeString, String... modifierStrings) {
		Map<Integer, int[]> tokensOfTheLine = decodedTokens.get(line);
		assertNotNull(tokensOfTheLine);
		int[] token = tokensOfTheLine.get(column); // 0: length, 1: typeIndex, 2: encodedModifiers
		assertNotNull(token);
		assertEquals(length, token[0]);
		assertEquals(tokenTypeString, legend.getTokenTypes().get(token[1]));
		assertModifiers(legend.getTokenModifiers(), token[2], Arrays.asList(modifierStrings));
	}

	private Map<Integer, Map<Integer, int[]>> decodeSourceFile(String name) {
		ICompilationUnit cu = fooPackage.getCompilationUnit(name);
		SemanticTokens tokens = SemanticTokensCommand.provide(JDTUtils.toURI(cu));
		return decode(tokens);
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
