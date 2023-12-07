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
package org.eclipse.jdt.ls.core.internal.handlers;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;

import java.util.Arrays;
import java.util.Hashtable;
import java.util.List;

import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.jdt.core.IBuffer;
import org.eclipse.jdt.core.IClasspathEntry;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IPackageFragment;
import org.eclipse.jdt.core.IPackageFragmentRoot;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.ls.core.internal.JDTUtils;
import org.eclipse.jdt.ls.core.internal.WorkspaceHelper;
import org.eclipse.jdt.ls.core.internal.correction.TestOptions;
import org.eclipse.jdt.ls.core.internal.handlers.BaseDocumentLifeCycleHandler.DocumentMonitor;
import org.eclipse.jdt.ls.core.internal.managers.AbstractProjectsManagerBasedTest;
import org.eclipse.lsp4j.SemanticTokens;
import org.eclipse.lsp4j.SemanticTokensLegend;
import org.eclipse.lsp4j.SemanticTokensParams;
import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class SemanticTokensHandlerTest extends AbstractProjectsManagerBasedTest {
	private IJavaProject semanticTokensProject;
	private IPackageFragment fooPackage;
	private String classFileUri = "jdt://contents/foo.jar/foo/bar.class?%3Dsemantic-tokens%2Ffoo.jar%3Cfoo%28bar.class";
	private String moduleInfoUri;

	@Before
	public void setup() throws Exception {
		importProjects("maven/semantic-tokens");
		semanticTokensProject = JavaCore.create(WorkspaceHelper.getProject("semantic-tokens"));
		setOptions();

		IPackageFragmentRoot root = semanticTokensProject.getPackageFragmentRoot(
			semanticTokensProject.getProject().getFolder("src/main/java")
		);
		fooPackage = root.getPackageFragment("foo");
		moduleInfoUri = JDTUtils.toURI(root.getPackageFragment("").getCompilationUnit("module-info.java"));
	}

	private void setOptions() {
		Hashtable<String, String> options = TestOptions.getDefaultOptions();
		JavaCore.setComplianceOptions("16", options);
		semanticTokensProject.setOptions(options);
	}

	private void addTestLibraryToClasspath() throws JavaModelException {
		IClasspathEntry[] oldEntries = semanticTokensProject.getRawClasspath();
		IClasspathEntry[] newEntries = Arrays.copyOf(oldEntries, oldEntries.length + 1);
		newEntries[oldEntries.length] = JavaCore.newLibraryEntry(
			semanticTokensProject.getProject().getFile("foo.jar").getFullPath(),
			semanticTokensProject.getProject().getFile("foo-sources.jar").getFullPath(),
			null
		);
		semanticTokensProject.setRawClasspath(newEntries, null);
	}

	@Test
	public void testSemanticTokens_SourceAttachment() throws JavaModelException {
		addTestLibraryToClasspath();
		TokenAssertionHelper.beginAssertion(classFileUri)
			.assertNextToken("foo", "namespace")
			.assertNextToken("public", "modifier")
			.assertNextToken("class", "modifier")
			.assertNextToken("bar", "class", "public", "declaration")
			.assertNextToken("public", "modifier")
			.assertNextToken("static", "modifier")
			.assertNextToken("add", "method", "public", "static", "declaration")
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
		TokenAssertionHelper.beginAssertion(getURI("Methods.java"), "method", "class", "keyword")
			.assertNextToken("Methods", "class", "public", "declaration")
			.assertNextToken("foo1", "method", "public", "generic", "declaration")
			.assertNextToken("foo2", "method", "private", "declaration")
			.assertNextToken("foo3", "method", "protected", "declaration")
			.assertNextToken("foo4", "method", "static", "declaration")
			.assertNextToken("String", "class", "public", "readonly")
			.assertNextToken("foo5", "method", "native", "declaration")
			.assertNextToken("foo6", "method", "deprecated", "declaration")
			.assertNextToken("String", "class", "public", "readonly")

			.assertNextToken("main", "method", "public", "static", "declaration")
			.assertNextToken("String", "class", "public", "readonly")
			.assertNextToken("Methods", "class", "public")
			.assertNextToken("Methods", "class", "public", "constructor")
			.assertNextToken("String", "class", "public", "readonly", "typeArgument")
			.assertNextToken("foo1", "method", "public", "generic")
			.assertNextToken("foo2", "method", "private")
			.assertNextToken("foo3", "method", "protected")
			.assertNextToken("foo4", "method", "static")
			.assertNextToken("Integer", "class", "public", "readonly", "typeArgument")
			.assertNextToken("foo6", "method", "deprecated")
			.assertNextToken("foo5", "method", "native")
			.assertNextToken("Class", "class", "readonly", "public", "generic")
			.assertNextToken("class", "keyword")
			.assertNextToken("m", "class")
			.assertNextToken("Class", "class", "readonly", "public", "generic")
		.endAssertion();
	}

	@Test
	public void testSemanticTokens_Constructors() throws JavaModelException {
		TokenAssertionHelper.beginAssertion(getURI("Constructors.java"))
			.assertNextToken("foo", "namespace")
			.assertNextToken("public", "modifier")
			.assertNextToken("class", "modifier")
			.assertNextToken("Constructors", "class", "public", "declaration")
			.assertNextToken("private", "modifier")
			.assertNextToken("Constructors", "class", "private", "constructor", "declaration")

			.assertNextToken("Constructors", "class", "public")
			.assertNextToken("c1", "variable", "declaration")
			.assertNextToken("Constructors", "class", "private", "constructor")

			.assertNextToken("Constructors", "class", "public")
			.assertNextToken("c2", "variable", "declaration")
			.assertNextToken("String", "class", "public", "readonly", "typeArgument")
			.assertNextToken("Constructors", "class", "private", "constructor")

			.assertNextToken("Constructors", "class", "public")
			.assertNextToken("InnerClass", "class", "protected")
			.assertNextToken("i1", "variable", "declaration")
			.assertNextToken("Constructors", "class", "public")
			.assertNextToken("InnerClass", "class", "protected", "constructor")

			.assertNextToken("Constructors", "class", "public")
			.assertNextToken("InnerClass", "class", "protected")
			.assertNextToken("i2", "variable", "declaration")
			.assertNextToken("SomeAnnotation", "annotation", "public")
			.assertNextToken("Constructors", "class", "public")
			.assertNextToken("InnerClass", "class", "protected", "constructor")

			.assertNextToken("Constructors", "class", "public")
			.assertNextToken("InnerClass", "class", "protected", "generic")
			.assertNextToken("String", "class", "public", "readonly", "typeArgument")
			.assertNextToken("i3", "variable", "declaration")
			.assertNextToken("Constructors", "class", "public")
			.assertNextToken("InnerClass", "class", "protected", "generic", "constructor")
			.assertNextToken("String", "class", "public", "readonly", "typeArgument")

			.assertNextToken("InnerClass", "class", "protected", "generic")
			.assertNextToken("Integer", "class", "public", "readonly", "typeArgument")
			.assertNextToken("i4", "variable", "declaration")
			.assertNextToken("InnerClass", "class", "protected", "generic", "constructor")

			.assertNextToken("GenericConstructor", "class", "private")
			.assertNextToken("g1", "variable", "declaration")
			.assertNextToken("String", "class", "public", "readonly", "typeArgument")
			.assertNextToken("GenericConstructor", "class", "protected", "generic", "constructor")

			.assertNextToken("Constructors", "class", "public")
			.assertNextToken("InnerRecord", "record", "private", "static", "readonly")
			.assertNextToken("r1", "variable", "declaration")
			.assertNextToken("Constructors", "class", "public")
			.assertNextToken("InnerRecord", "record", "private", "constructor")

			.assertNextToken("InnerRecord", "record", "protected", "constructor")

			.assertNextToken("protected", "modifier")
			.assertNextToken("class", "modifier")
			.assertNextToken("InnerClass", "class", "protected", "generic", "declaration")
			.assertNextToken("T", "typeParameter", "declaration")

			.assertNextToken("private", "modifier")
			.assertNextToken("class", "modifier")
			.assertNextToken("GenericConstructor", "class", "private", "declaration")
			.assertNextToken("protected", "modifier")
			.assertNextToken("T", "typeParameter", "declaration")
			.assertNextToken("GenericConstructor", "class", "protected", "generic", "constructor", "declaration")

			.assertNextToken("public", "modifier")
			.assertNextToken("InnerEnum", "enum", "public", "static", "readonly", "declaration")
			.assertNextToken("FOO", "enumMember", "public", "static", "readonly", "declaration")
			.assertNextToken("InnerEnum", "enum", "private", "constructor", "declaration")
			.assertNextToken("String", "class", "public", "readonly")
			.assertNextToken("string", "parameter", "declaration")

			.assertNextToken("private", "modifier")
			.assertNextToken("record", "modifier")
			.assertNextToken("InnerRecord", "record", "private", "static", "readonly", "declaration")
			.assertNextToken("String", "class", "public", "readonly")
			.assertNextToken("string", "recordComponent", "declaration")
			.assertNextToken("integer", "recordComponent", "declaration")
			.assertNextToken("protected", "modifier")
			.assertNextToken("InnerRecord", "record", "protected", "constructor", "declaration")

			.assertNextToken("public", "modifier")
			.assertNextToken("interface", "modifier")
			.assertNextToken("TestInterface", "interface", "public", "static", "declaration")

			.assertNextToken("class", "modifier")
			.assertNextToken("TestClass", "class", "declaration")
			.assertNextToken("implements", "modifier")
			.assertNextToken("TestInterface", "interface", "public", "static")

			.assertNextToken("class", "modifier")
			.assertNextToken("TestExtends", "class", "declaration")
			.assertNextToken("extends", "modifier")
			.assertNextToken("TestClass", "class")

			.assertNextToken("class", "modifier")
			.assertNextToken("TestCombo", "class", "declaration")
			.assertNextToken("extends", "modifier")
			.assertNextToken("TestClass", "class")
			.assertNextToken("implements", "modifier")
			.assertNextToken("TestInterface", "interface", "public", "static")

			.assertNextToken("sealed", "modifier")
			.assertNextToken("interface", "modifier")
			.assertNextToken("TestSealedInterface", "interface", "sealed", "static", "declaration")
			.assertNextToken("permits", "modifier")
			.assertNextToken("TestPermits", "class", "readonly")

			.assertNextToken("final", "modifier")
			.assertNextToken("class", "modifier")
			.assertNextToken("TestPermits", "class", "readonly", "declaration")
			.assertNextToken("implements", "modifier")
			.assertNextToken("TestSealedInterface", "interface", "sealed", "static")


		.endAssertion();
	}

	@Test
	public void testSemanticTokens_Fields() throws JavaModelException {
		TokenAssertionHelper.beginAssertion(getURI("Fields.java"), "property", "enumMember", "recordComponent")
			.assertNextToken("bar1", "property", "public", "declaration")
			.assertNextToken("bar2", "property", "private", "declaration")
			.assertNextToken("bar3", "property", "protected", "declaration")
			.assertNextToken("bar2", "property", "private")
			.assertNextToken("bar4", "property", "readonly", "declaration")
			.assertNextToken("bar5", "property", "static", "declaration")
			.assertNextToken("bar6", "property", "public", "static", "readonly", "declaration")
			.assertNextToken("FIRST", "enumMember", "public", "static", "readonly", "declaration")
			.assertNextToken("SECOND", "enumMember", "public", "static", "readonly", "declaration")
			.assertNextToken("i", "recordComponent", "declaration")
			.assertNextToken("f", "recordComponent", "declaration")
			.assertNextToken("i", "property", "private", "readonly")
			.assertNextToken("f", "property", "private", "readonly")
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
		TokenAssertionHelper.beginAssertion(getURI("Types.java"), "class", "interface", "enum", "annotation", "record", "typeParameter", "keyword", "modifier")
			.assertNextToken("public", "modifier")
			.assertNextToken("class", "modifier")
			.assertNextToken("Types", "class", "public", "declaration")

			.assertNextToken("public", "modifier")
			.assertNextToken("String", "class", "public", "readonly")

			.assertNextToken("protected", "modifier")
			.assertNextToken("final", "modifier")
			.assertNextToken("Class", "class", "public", "readonly", "generic")
			.assertNextToken("String", "class", "public", "readonly")
			.assertNextToken("class", "keyword")

			.assertNextToken("public", "modifier")
			.assertNextToken("SomeClass", "class", "public", "generic")
			.assertNextToken("String", "class", "public", "readonly", "typeArgument")
			.assertNextToken("SomeClass", "class", "public", "generic", "typeArgument")
			.assertNextToken("String", "class", "public", "readonly", "typeArgument")
			.assertNextToken("Integer", "class", "public", "readonly", "typeArgument")

			.assertNextToken("SomeAnnotation", "annotation", "static")
			.assertNextToken("Types", "class", "public")
			.assertNextToken("class", "keyword")
			.assertNextToken("public", "modifier")
			.assertNextToken("class", "modifier")
			.assertNextToken("SomeClass", "class", "public", "generic", "declaration")
			.assertNextToken("T1", "typeParameter", "declaration")
			.assertNextToken("T2", "typeParameter", "declaration")
			.assertNextToken("T1", "typeParameter")
			.assertNextToken("T2", "typeParameter")

			.assertNextToken("interface", "modifier")
			.assertNextToken("SomeInterface", "interface", "static", "declaration")
			.assertNextToken("SomeEnum", "enum", "static", "readonly", "declaration")
			.assertNextToken("SomeAnnotation", "annotation", "static", "declaration")
			.assertNextToken("Class", "class", "public", "readonly", "generic")
			.assertNextToken("record", "modifier")
			.assertNextToken("SomeRecord", "record", "static", "readonly", "declaration")
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
			.assertNextToken("NonExistentClass", "namespace", "importDeclaration")

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
			.assertNextToken("String", "class", "public", "constructor")
		.endAssertion();
	}

	@Test
	public void testSemanticTokens_Annotations() throws JavaModelException {
		TokenAssertionHelper.beginAssertion(getURI("Annotations.java"), "annotation", "annotationMember")
			.assertNextToken("SomeAnnotation", "annotation", "public")
			.assertNextToken("SuppressWarnings", "annotation", "public")
			.assertNextToken("SuppressWarnings", "annotation", "public")
			.assertNextToken("value", "annotationMember", "public", "abstract")
		.endAssertion();
	}

	@Test
	public void testSemanticTokens_Modules() throws JavaModelException {
		TokenAssertionHelper.beginAssertion(moduleInfoUri)
			.assertNextToken("@code", "keyword", "documentation")
			.assertNextToken("@uses", "keyword", "documentation")
			.assertNextToken("@moduleGraph", "keyword", "documentation")

			.assertNextToken("foo", "namespace")
			.assertNextToken("bar", "namespace")
			.assertNextToken("baz", "namespace")

			.assertNextToken("java", "namespace")
			.assertNextToken("base", "namespace")
			.assertNextToken("java", "namespace")
			.assertNextToken("desktop", "namespace")
			.assertNextToken("java", "namespace")
			.assertNextToken("net", "namespace")
			.assertNextToken("http", "namespace")
			.assertNextToken("java", "namespace")
			.assertNextToken("sql", "namespace")

			.assertNextToken("foo", "namespace")
			.assertNextToken("java", "namespace")
			.assertNextToken("base", "namespace")
			.assertNextToken("java", "namespace")
			.assertNextToken("desktop", "namespace")

			.assertNextToken("foo", "namespace")
			.assertNextToken("java", "namespace")
			.assertNextToken("base", "namespace")
			.assertNextToken("java", "namespace")
			.assertNextToken("net", "namespace")
			.assertNextToken("http", "namespace")

			.assertNextToken("java", "namespace")
			.assertNextToken("sql", "namespace")
			.assertNextToken("Driver", "interface", "public")
		.endAssertion();
	}

	@Test
	public void testSemanticTokens_Javadoc() throws JavaModelException {
		TokenAssertionHelper.beginAssertion(getURI("Javadoc.java"))
			.assertNextToken("foo", "namespace")
			.assertNextToken("@implNote", "keyword", "documentation")
			.assertNextToken("@link", "keyword", "documentation")
			.assertNextToken("java", "namespace", "documentation")
			.assertNextToken("lang", "namespace", "documentation")
			.assertNextToken("String", "class", "public", "readonly", "documentation")
			.assertNextToken("public", "modifier")
			.assertNextToken("class", "modifier")
			.assertNextToken("Javadoc", "class", "public", "declaration")

			.assertNextToken("@code", "keyword", "documentation")
			.assertNextToken("@param", "keyword", "documentation")
			.assertNextToken("arg1", "parameter", "documentation")
			.assertNextToken("@link", "keyword", "documentation")
			.assertNextToken("Integer", "class", "public", "readonly", "documentation")
			.assertNextToken("@param", "keyword", "documentation")
			.assertNextToken("arg2", "parameter", "documentation")
			.assertNextToken("@link", "keyword", "documentation")
			.assertNextToken("Double", "class", "public", "readonly", "documentation")
			.assertNextToken("@return", "keyword", "documentation")
			.assertNextToken("@link", "keyword", "documentation")
			.assertNextToken("String", "class", "public", "readonly", "documentation")
			.assertNextToken("public", "modifier")
			.assertNextToken("String", "class", "public", "readonly")
			.assertNextToken("getString", "method", "public", "declaration")
			.assertNextToken("Integer", "class", "public", "readonly")
			.assertNextToken("arg1", "parameter", "declaration")
			.assertNextToken("Double", "class", "public", "readonly")
			.assertNextToken("arg2", "parameter", "declaration")

			.assertNextToken("@link", "keyword", "documentation")
			.assertNextToken("Javadoc", "class", "public", "documentation")
			.assertNextToken("getString", "method", "public", "documentation")
			.assertNextToken("Integer", "class", "public", "readonly", "documentation")
			.assertNextToken("Double", "class", "public", "readonly", "documentation")
			.assertNextToken("@see", "keyword", "documentation")
			.assertNextToken("getString", "method", "public", "documentation")
			.assertNextToken("Integer", "class", "public", "readonly", "documentation")
			.assertNextToken("Double", "class", "public", "readonly", "documentation")
			.assertNextToken("@return", "keyword", "documentation")
			.assertNextToken("@link", "keyword", "documentation")
			.assertNextToken("Integer", "class", "public", "readonly", "documentation")
			.assertNextToken("private", "modifier")
			.assertNextToken("getInt", "method", "private", "declaration")
		.endAssertion();
	}

	@Test
	public void testSemanticTokens_ClassLiterals() throws JavaModelException {
		TokenAssertionHelper.beginAssertion(getURI("ClassLiterals.java"), "keyword")
			.assertNextToken("class", "keyword")
			.assertNextToken("class", "keyword")
		.endAssertion();
	}

	private String getURI(String compilationUnitName) {
		return JDTUtils.toURI(fooPackage.getCompilationUnit(compilationUnitName));
	}

	/**
	 * Helper class for asserting semantic tokens provided by the {@link SemanticTokensHandler},
	 * using the builder pattern. Call {@link #beginAssertion(String, String...)} to get an instance
	 * of the helper, then chain calls to {@link #assertNextToken(String, String, String...)} until no
	 * more tokens are expected, at which point {@link #endAssertion()} should finally be called.
	 */
	private static class TokenAssertionHelper {

		private static final SemanticTokensLegend LEGEND = SemanticTokensHandler.legend();

		private IBuffer buffer;
		private int currentLine = 0;
		private int currentColumn = 0;

		private List<Integer> semanticTokensData;
		private int currentDataIndex = 0;

		private List<String> tokenTypeFilter;

		private TokenAssertionHelper(IBuffer buffer, List<Integer> semanticTokensData, List<String> tokenTypeFilter) {
			this.buffer = buffer;
			this.semanticTokensData = semanticTokensData;
			this.tokenTypeFilter = tokenTypeFilter;
		}

		/**
		 * Begins an assertion for semantic tokens (calling {@link SemanticTokensHandler#provide(String)}),
		 * optionally providing a filter describing which token types to assert.
		 *
		 * @param uri The URI to assert provided semantic tokens for.
		 * @param tokenTypeFilter Specifies the type of semantic tokens to assert. Only token types
		 * matching the filter will be asserted. If the filter is empty, all tokens will be asserted.
		 * @return A new instace of {@link TokenAssertionHelper}.
		 * @throws JavaModelException
		 */
		public static TokenAssertionHelper beginAssertion(String uri, String... tokenTypeFilter) throws JavaModelException {
			SemanticTokens semanticTokens = SemanticTokensHandler.full(new NullProgressMonitor(), new SemanticTokensParams(new TextDocumentIdentifier(uri)), mock(DocumentMonitor.class));
			assertNotNull("Provided semantic tokens should not be null", semanticTokens);
			assertNotNull("Semantic tokens data should not be null", semanticTokens.getData());
			assertTrue("Semantic tokens data should contain 5 integers per token", semanticTokens.getData().size() % 5 == 0);
			return new TokenAssertionHelper(JDTUtils.resolveTypeRoot(uri).getBuffer(), semanticTokens.getData(), Arrays.asList(tokenTypeFilter));
		}

		/**
		 * Asserts the next semantic token in the data provided by {@link SemanticTokensHandler}.
		 *
		 * @param expectedText The expected text at the location of the next semantic token.
		 * @param expectedType The expected type of the next semantic token.
		 * @param expectedModifiers The expected modifiers of the next semantic token.
		 * @return Itself.
		 */
		public TokenAssertionHelper assertNextToken(String expectedText, String expectedType, String... expectedModifiers) {
			assertTrue("Token of type '" + expectedType + "' should be present in the semantic tokens data",
				currentDataIndex < semanticTokensData.size());

			int deltaLine = semanticTokensData.get(currentDataIndex);
			int deltaColumn = semanticTokensData.get(currentDataIndex + 1);
			int length = semanticTokensData.get(currentDataIndex + 2);
			int typeIndex = semanticTokensData.get(currentDataIndex + 3);
			int encodedModifiers = semanticTokensData.get(currentDataIndex + 4);

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

			if (tokenTypeFilter.isEmpty() || tokenTypeFilter.contains(LEGEND.getTokenTypes().get(typeIndex))) {
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
		 * provided by {@link SemanticTokensHandler}.
		 */
		public void endAssertion() {
			if (tokenTypeFilter.isEmpty()) {
				assertTrue("There should be no more tokens", currentDataIndex == semanticTokensData.size());
			}
			else {
				while (currentDataIndex < semanticTokensData.size()) {
					int currentTypeIndex = semanticTokensData.get(currentDataIndex + 3);
					String currentType = LEGEND.getTokenTypes().get(currentTypeIndex);
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
			assertEquals("Token type should be correct.", expectedType, LEGEND.getTokenTypes().get(typeIndex));
		}

		private void assertTokenModifiers(int encodedModifiers, List<String> expectedModifiers) {
			for (int i = 0; i < LEGEND.getTokenModifiers().size(); i++) {
				String modifier = LEGEND.getTokenModifiers().get(i);
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
