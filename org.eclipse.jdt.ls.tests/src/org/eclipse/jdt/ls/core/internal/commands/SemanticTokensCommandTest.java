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
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.List;

import org.eclipse.jdt.core.IBuffer;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IPackageFragment;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.ls.core.internal.JDTUtils;
import org.eclipse.jdt.ls.core.internal.WorkspaceHelper;
import org.eclipse.jdt.ls.core.internal.correction.TestOptions;
import org.eclipse.jdt.ls.core.internal.handlers.JsonRpcHelpers;
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
	public void testSemanticTokens_SourceAttachment() throws JavaModelException {
		TokenAssertionHelper.beginAssertion(classFileUri)
			.assertNextToken("foo", "namespace")
			.assertNextToken("public", "modifier")
			.assertNextToken("bar", "class", "public", "declaration")
			.assertNextToken("public", "modifier")
			.assertNextToken("static", "modifier")
			.assertNextToken("add", "function", "public", "static", "declaration")
			.assertNextToken("a", "parameter", "declaration")
			.assertNextToken("sum", "variable", "declaration")
			.assertNextToken("element", "variable", "declaration")
			.assertNextToken("a", "parameter")
			.assertNextToken("sum", "variable")
			.assertNextToken("element", "variable")
			.assertNextToken("sum", "variable")
		.endAssertion();
	}

	@Test
	public void testSemanticTokens_Methods() throws JavaModelException {
		TokenAssertionHelper.beginAssertion(getURI("Methods.java"), "function")
			.assertNextToken("foo1", "function", "public", "generic", "declaration")
			.assertNextToken("foo2", "function", "private", "declaration")
			.assertNextToken("foo3", "function", "protected", "declaration")
			.assertNextToken("foo4", "function", "static", "declaration")
			.assertNextToken("foo5", "function", "native", "declaration")
			.assertNextToken("foo6", "function", "deprecated", "declaration")

			.assertNextToken("main", "function", "public", "static", "declaration")
			.assertNextToken("Methods", "function", "public")

			.assertNextToken("foo1", "function", "public", "generic")
			.assertNextToken("foo2", "function", "private")
			.assertNextToken("foo3", "function", "protected")
			.assertNextToken("foo4", "function", "static")
			.assertNextToken("foo5", "function", "native")
			.assertNextToken("foo6", "function", "deprecated")
		.endAssertion();
	}

	@Test
	public void testSemanticTokens_Constructors() throws JavaModelException {
		TokenAssertionHelper.beginAssertion(getURI("Constructors.java"), "function")
			.assertNextToken("Constructors", "function", "private", "declaration")
			.assertNextToken("Constructors", "function", "private")
			.assertNextToken("InnerClass", "function", "protected")
			.assertNextToken("InnerClass", "function", "protected")
			.assertNextToken("InnerClass", "function", "protected", "generic")
		.endAssertion();
	}

	@Test
	public void testSemanticTokens_Properties() throws JavaModelException {
		TokenAssertionHelper.beginAssertion(getURI("Properties.java"), "property", "enumMember")
			.assertNextToken("bar1", "property", "public", "declaration")
			.assertNextToken("bar2", "property", "private", "declaration")
			.assertNextToken("bar3", "property", "protected", "declaration")
			.assertNextToken("bar2", "property", "private")
			.assertNextToken("bar4", "property", "readonly", "declaration")
			.assertNextToken("bar5", "property", "static", "declaration")
			.assertNextToken("bar6", "property", "public", "static", "readonly", "declaration")
			.assertNextToken("FIRST", "enumMember", "public", "static", "readonly", "declaration")
			.assertNextToken("SECOND", "enumMember", "public", "static", "readonly", "declaration")
		.endAssertion();
	}

	@Test
	public void testSemanticTokens_Variables() throws JavaModelException {
		TokenAssertionHelper.beginAssertion(getURI("Variables.java"), "variable", "parameter")
			.assertNextToken("string", "parameter", "declaration")
			.assertNextToken("bar1", "variable", "declaration")
			.assertNextToken("string", "parameter")
			.assertNextToken("bar2", "variable", "declaration")
			.assertNextToken("bar1", "variable")
			.assertNextToken("bar3", "variable", "readonly", "declaration")
		.endAssertion();
	}

	@Test
	public void testSemanticTokens_Types() throws JavaModelException {
		TokenAssertionHelper.beginAssertion(getURI("Types.java"), "class", "interface", "enum", "annotation", "typeParameter")
			.assertNextToken("Types", "class", "public", "declaration")
			.assertNextToken("String", "class", "public", "readonly")

			.assertNextToken("SomeClass", "class", "generic")
			.assertNextToken("String", "class", "public", "readonly", "typeArgument")
			.assertNextToken("SomeClass", "class", "generic", "typeArgument")
			.assertNextToken("String", "class", "public", "readonly", "typeArgument")
			.assertNextToken("Integer", "class", "public", "readonly", "typeArgument")

			.assertNextToken("SomeClass", "class", "generic", "declaration")
			.assertNextToken("T1", "typeParameter", "declaration")
			.assertNextToken("T2", "typeParameter", "declaration")
			.assertNextToken("T1", "typeParameter")
			.assertNextToken("T2", "typeParameter")

			.assertNextToken("SomeInterface", "interface", "static", "declaration")
			.assertNextToken("SomeEnum", "enum", "static", "readonly", "declaration")
			.assertNextToken("SomeAnnotation", "annotation", "static", "declaration")
		.endAssertion();
	}

	@Test
	public void testSemanticTokens_Packages() throws JavaModelException {
		TokenAssertionHelper.beginAssertion(getURI("Packages.java"), "namespace", "class", "property")
			.assertNextToken("foo", "namespace")

			.assertNextToken("java", "namespace", "importDeclaration")
			.assertNextToken("lang", "namespace", "importDeclaration")
			.assertNextToken("Math", "class", "public", "readonly", "importDeclaration")
			.assertNextToken("PI", "property", "public", "static", "readonly", "importDeclaration")

			.assertNextToken("java", "namespace", "importDeclaration")
			.assertNextToken("util", "namespace", "importDeclaration")

			.assertNextToken("java", "namespace", "importDeclaration")
			.assertNextToken("NonExistentClass", "class", "importDeclaration")

			.assertNextToken("java", "namespace", "importDeclaration")
			.assertNextToken("nio", "namespace", "importDeclaration")

			.assertNextToken("java", "namespace", "importDeclaration")

			.assertNextToken("java", "namespace", "importDeclaration")
			.assertNextToken("lang", "namespace", "importDeclaration")
			.assertNextToken("Math", "class", "public", "readonly", "importDeclaration")

			.assertNextToken("java", "namespace", "importDeclaration")
			.assertNextToken("lang", "namespace", "importDeclaration")
			.assertNextToken("Math", "class", "public", "readonly", "importDeclaration")

			.assertNextToken("Packages", "class", "public", "declaration")

			.assertNextToken("java", "namespace")
			.assertNextToken("lang", "namespace")
			.assertNextToken("String", "class", "public", "readonly")
			.assertNextToken("string", "property", "public", "declaration")
			.assertNextToken("java", "namespace")
			.assertNextToken("lang", "namespace")
		.endAssertion();
	}

	@Test
	public void testSemanticTokens_Annotations() throws JavaModelException {
		TokenAssertionHelper.beginAssertion(getURI("Annotations.java"), "annotation", "annotationMember")
			.assertNextToken("SuppressWarnings", "annotation", "public")
			.assertNextToken("SuppressWarnings", "annotation", "public")
			.assertNextToken("SuppressWarnings", "annotation", "public")
			.assertNextToken("value", "annotationMember", "public", "abstract")
		.endAssertion();
	}

	private String getURI(String compilationUnitName) {
		return JDTUtils.toURI(fooPackage.getCompilationUnit(compilationUnitName));
	}

	/**
	 * Helper class for asserting semantic tokens provided by the {@link SemanticTokensCommand},
	 * using the builder pattern. Call {@link #beginAssertion(String, String...)} to get an instance
	 * of the helper, then chain calls to {@link #assertNextToken(String, String, String...)} until no
	 * more tokens are expected, at which point {@link #endAssertion()} should finally be called.
	 */
	private static class TokenAssertionHelper {

		private static final SemanticTokensLegend LEGEND = new SemanticTokensLegend();

		private IBuffer buffer;
		private int currentLine = 0;
		private int currentColumn = 0;

		private int[] semanticTokensData;
		private int currentDataIndex = 0;

		private List<String> tokenTypeFilter;

		private TokenAssertionHelper(IBuffer buffer, int[] semanticTokensData, List<String> tokenTypeFilter) {
			this.buffer = buffer;
			this.semanticTokensData = semanticTokensData;
			this.tokenTypeFilter = tokenTypeFilter;
		}

		/**
		 * Begins an assertion for semantic tokens (calling {@link SemanticTokensCommand#provide(String)}),
		 * optionally providing a filter describing which token types to assert.
		 *
		 * @param uri The URI to assert provided semantic tokens for.
		 * @param tokenTypeFilter Specifies the type of semantic tokens to assert. Only token types
		 * matching the filter will be asserted. If the filter is empty, all tokens will be asserted.
		 * @return A new instace of {@link TokenAssertionHelper}.
		 * @throws JavaModelException
		 */
		public static TokenAssertionHelper beginAssertion(String uri, String... tokenTypeFilter) throws JavaModelException {
			SemanticTokens semanticTokens = SemanticTokensCommand.provide(uri);
			assertNotNull("Provided semantic tokens should not be null", semanticTokens);
			assertNotNull("Semantic tokens data should not be null", semanticTokens.getData());
			assertTrue("Semantic tokens data should contain 5 integers per token", semanticTokens.getData().length % 5 == 0);
			return new TokenAssertionHelper(JDTUtils.resolveTypeRoot(uri).getBuffer(), semanticTokens.getData(), Arrays.asList(tokenTypeFilter));
		}

		/**
		 * Asserts the next semantic token in the data provided by {@link SemanticTokensCommand}.
		 *
		 * @param expectedText The expected text at the location of the next semantic token.
		 * @param expectedType The expected type of the next semantic token.
		 * @param expectedModifiers The expected modifiers of the next semantic token.
		 * @return Itself.
		 */
		public TokenAssertionHelper assertNextToken(String expectedText, String expectedType, String... expectedModifiers) {
			assertTrue("Token of type '" + expectedType + "' should be present in the semantic tokens data",
				currentDataIndex < semanticTokensData.length);

			int deltaLine = semanticTokensData[currentDataIndex];
			int deltaColumn = semanticTokensData[currentDataIndex + 1];
			int length = semanticTokensData[currentDataIndex + 2];
			int typeIndex = semanticTokensData[currentDataIndex + 3];
			int encodedModifiers = semanticTokensData[currentDataIndex + 4];

			assertTrue("Token deltaLine should not be negative", deltaLine >= 0);
			assertTrue("Token deltaColumn should not be negative", deltaColumn >= 0);
			assertTrue("Token length should be greater than zero", length > 0);

			if (deltaLine == 0) {
				currentColumn += deltaColumn;
			}
			else {
				currentLine += deltaLine;
				currentColumn = deltaColumn;
			}

			currentDataIndex += 5;

			if (tokenTypeFilter.isEmpty() || tokenTypeFilter.contains(LEGEND.getTokenTypes()[typeIndex])) {
				assertTextMatchInBuffer(length, expectedText);
				assertTokenType(typeIndex, expectedType);
				assertTokenModifiers(encodedModifiers, Arrays.asList(expectedModifiers));

				return this;
			}
			else {
				return assertNextToken(expectedText, expectedType, expectedModifiers);
			}
		}

		/**
		 * Asserts that there are no more unexpected semantic tokens present in the data
		 * provided by {@link SemanticTokensCommand}.
		 */
		public void endAssertion() {
			if (tokenTypeFilter.isEmpty()) {
				assertTrue("There should be no more tokens", currentDataIndex == semanticTokensData.length);
			}
			else {
				while (currentDataIndex < semanticTokensData.length) {
					int currentTypeIndex = semanticTokensData[currentDataIndex + 3];
					String currentType = LEGEND.getTokenTypes()[currentTypeIndex];
					assertFalse(
						"There should be no more tokens matching the filter, but found '" + currentType + "' token",
						tokenTypeFilter.contains(currentType)
					);
					currentDataIndex += 5;
				}
			}
		}

		private void assertTextMatchInBuffer(int length, String expectedText) {
			String tokenTextInBuffer = buffer.getText(JsonRpcHelpers.toOffset(buffer, currentLine, currentColumn), length);
			assertEquals("Token text should match the token text range in the buffer.", expectedText, tokenTextInBuffer);
		}

		private void assertTokenType(int typeIndex, String expectedType) {
			assertEquals("Token type should be correct.", expectedType, LEGEND.getTokenTypes()[typeIndex]);
		}

		private void assertTokenModifiers(int encodedModifiers, List<String> expectedModifiers) {
			for (int i = 0; i < LEGEND.getTokenModifiers().length; i++) {
				String modifier = LEGEND.getTokenModifiers()[i];
				boolean modifierIsEncoded = ((encodedModifiers >>> i) & 1) == 1;
				boolean modifierIsExpected = expectedModifiers.contains(modifier);

				assertTrue(
					modifierIsExpected
						? "Expected modifier '" + modifier + "' to be encoded"
						: "Did not expect modifier '" + modifier + "' to be encoded",
					modifierIsExpected == modifierIsEncoded
				);
			}
		}
	}
}
