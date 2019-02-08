/*******************************************************************************
 * Copyright (c) 2018 TypeFox. and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     TypeFox. - initial API and implementation
 *******************************************************************************/
package org.eclipse.jdt.ls.core.internal.highlighting;

import static com.google.common.collect.Lists.newArrayList;
import static org.eclipse.lsp4j.util.SemanticHighlightingTokens.decode;
import static org.hamcrest.CoreMatchers.hasItem;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IPackageFragment;
import org.eclipse.jdt.core.IPackageFragmentRoot;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.ls.core.internal.JDTUtils;
import org.eclipse.jdt.ls.core.internal.JavaClientConnection;
import org.eclipse.jdt.ls.core.internal.handlers.DocumentLifeCycleHandler;
import org.eclipse.jdt.ls.core.internal.managers.AbstractProjectsManagerBasedTest;
import org.eclipse.jdt.ls.core.internal.preferences.ClientPreferences;
import org.eclipse.lsp4j.DidChangeTextDocumentParams;
import org.eclipse.lsp4j.DidOpenTextDocumentParams;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.SemanticHighlightingInformation;
import org.eclipse.lsp4j.SemanticHighlightingParams;
import org.eclipse.lsp4j.TextDocumentContentChangeEvent;
import org.eclipse.lsp4j.TextDocumentItem;
import org.eclipse.lsp4j.VersionedTextDocumentIdentifier;
import org.eclipse.lsp4j.util.SemanticHighlightingTokens;
import org.eclipse.lsp4j.util.SemanticHighlightingTokens.Token;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.runners.MockitoJUnitRunner;

import com.google.common.collect.FluentIterable;
import com.google.common.collect.Lists;

@RunWith(MockitoJUnitRunner.class)
public class SemanticHighlightingTest extends AbstractProjectsManagerBasedTest {

	//@formatter:off
	private static final String CONTENT = "package _package;\n" +
			"\n" +
			"abstract class AnyClass {\n" +
			"\n" +
			"	protected String inheritedField = \"\";\n" +
			"\n" +
			"	abstract void abstractMethod(String s);\n" +
			"\n" +
			"	public static void staticMethod() {\n" +
			"	}\n" +
			"\n" +
			"}\n" +
			"\n" +
			"abstract class AbstractClassName {\n" +
			"\n" +
			"}\n" +
			"\n" +
			"interface InterfaceName<E> {\n" +
			"\n" +
			"	default Integer bar(Integer number) {\n" +
			"		return number + 1;\n" +
			"	}\n" +
			"\n" +
			"}\n" +
			"\n" +
			"/**\n" +
			" * This is about <code>ClassName</code>.\n" +
			" * {@link com.yourCompany.aPackage.Interface}\n" +
			" * @author author\n" +
			" * @deprecated use <code>OtherClass</code>\n" +
			" */\n" +
			"public class ClassName<E> extends AnyClass implements InterfaceName<String> {\n" +
			"	enum Color { RED, GREEN, BLUE };\n" +
			"	/* This comment may span multiple lines. */\n" +
			"	static Object staticField;\n" +
			"	// This comment may span only this line\n" +
			"	private E field;\n" +
			"	private AbstractClassName field2;\n" +
			"	// TASK: refactor\n" +
			"	@SuppressWarnings(value=\"all\")\n" +
			"	public int foo(Integer parameter) {\n" +
			"		abstractMethod(inheritedField);\n" +
			"		int local= 42*hashCode();\n" +
			"		staticMethod();\n" +
			"		Thread.currentThread().stop();\n" +
			"		return bar(local) + parameter;\n" +
			"	}\n" +
			"\n" +
			"	public void abstractMethod(String s) { }\n" +
			"}";
	//@formatter:off

	private DocumentLifeCycleHandler lifeCycleHandler;
	private TestJavaClientConnection javaClient;

	@Override
	protected ClientPreferences initPreferenceManager(boolean supportClassFileContents) {
		ClientPreferences clientPreferences = super.initPreferenceManager(supportClassFileContents);
		when(clientPreferences.isClassFileContentSupported()).thenReturn(supportClassFileContents);
		return clientPreferences;
	}

	@Before
	public void setup() throws Exception {
		javaClient = new TestJavaClientConnection(client);
		lifeCycleHandler = new DocumentLifeCycleHandler(javaClient, preferenceManager, projectsManager, false);
	}

	@After
	public void tearDown() throws Exception {
		javaClient.disconnect();
		for (ICompilationUnit unit : JavaCore.getWorkingCopies(null)) {
			unit.discardWorkingCopy();
		}
	}

	@Test
	public void testDidOpen() throws Exception {
		IJavaProject project = newEmptyProject();
		IPackageFragmentRoot src = project.getPackageFragmentRoot(project.getProject().getFolder("src"));
		IPackageFragment _package = src.createPackageFragment("_package", false, null);
		ICompilationUnit unit = _package.createCompilationUnit("ClassName.java", CONTENT, false, null);
		openDocument(unit, unit.getSource(), 1);
		assertEquals(1, javaClient.params.size());
		assertTrue(!javaClient.params.get(0).getLines().isEmpty());
	}

	@Test
	public void testDidOpen_autoBoxing() throws Exception {
		String content = "package _package;\n"
				+ "\n"
				+ "public class A {\n" +
				"  public static void main(String[] args) {\n" +
				"    Integer integer = Integer.valueOf(36);\n" +
				"    System.out.println(10 + integer);\n" +
				"  }\n" +
				"}";
		int version = 1;
		IJavaProject project = newEmptyProject();
		IPackageFragmentRoot src = project.getPackageFragmentRoot(project.getProject().getFolder("src"));
		IPackageFragment _package = src.createPackageFragment("_package", false, null);
		ICompilationUnit unit = _package.createCompilationUnit("A.java", content, false, null);
		openDocument(unit, unit.getSource(), version);

		assertEquals(1, javaClient.params.size());
		List<SemanticHighlightingInformation> lines = javaClient.params.get(0).getLines();
		assertEquals(4, lines.size());
		SemanticHighlightingInformation line5 = FluentIterable.from(lines).firstMatch(line -> line.getLine() == 5).get();
		SemanticHighlightingTokens.Token unboxingToken = FluentIterable.from(decode(line5.getTokens())).firstMatch(token -> token.character == 28).get();
		assertEquals(unboxingToken.length, 7);
		assertThat(SemanticHighlightingService.getScopes(unboxingToken.scope), hasItem("variable.other.autoboxing.java"));
	}

	@Test
	public void testDidChange() throws Exception {
		StringBuilder sb = new StringBuilder();
		String beginContent = "package _package;\n\npublic class A { }\n";
		String endContent = "class B { }\n";
		sb.append(beginContent);
		sb.append(endContent);

		int version = 1;
		IJavaProject project = newEmptyProject();
		IPackageFragmentRoot src = project.getPackageFragmentRoot(project.getProject().getFolder("src"));
		IPackageFragment _package = src.createPackageFragment("_package", false, null);
		ICompilationUnit unit = _package.createCompilationUnit("A.java", sb.toString(), false, null);
		openDocument(unit, unit.getSource(), version);

		assertEquals(1, javaClient.params.size());
		SemanticHighlightingParams params = javaClient.params.get(0);
		assertEquals(2, params.getLines().size());

		SemanticHighlightingInformation informationA = params.getLines().get(0);
		assertEquals(2, informationA.getLine());
		List<Token> informationATokens = decode(informationA.getTokens());
		assertEquals(1, informationATokens.size());
		SemanticHighlightingTokens.Token tokenA = informationATokens.get(0);
		assertEquals(13, tokenA.character);
		assertEquals(1, tokenA.length);
		assertThat(SemanticHighlightingService.getScopes(tokenA.scope), hasItem("entity.name.type.class.java"));

		SemanticHighlightingInformation informationB = params.getLines().get(1);
		assertEquals(3, informationB.getLine());
		List<Token> informationBTokens = decode(informationB.getTokens());
		assertEquals(1, informationBTokens.size());
		SemanticHighlightingTokens.Token tokenB = informationBTokens.get(0);
		assertEquals(6, tokenB.character);
		assertEquals(1, tokenB.length);
		assertThat(SemanticHighlightingService.getScopes(tokenB.scope), hasItem("entity.name.type.class.java"));

		javaClient.params.clear();
		String insertContent = "class InsertedClass { }\n";
		Range insertRange = JDTUtils.toRange(unit, beginContent.length(), 0);
		changeDocument(unit, insertContent, version++, insertRange, 0);
		params = javaClient.params.get(0);

		informationB = params.getLines().get(0);
		informationBTokens = decode(informationB.getTokens());
		assertEquals(3, informationB.getLine());
		assertEquals(1, informationBTokens.size());
		tokenB = informationBTokens.get(0);
		assertEquals(6, tokenB.character);
		assertEquals(13, tokenB.length);
		assertThat(SemanticHighlightingService.getScopes(tokenB.scope), hasItem("entity.name.type.class.java"));
	}

	@Test
	public void testDidChange_insertNewLineAbove() throws Exception {
		//@formatter:off
		String content = "package _package;\n" +
				"\n" +
				"public class A { }\n" +
				"class SB { }\n" +
				"class SC { }";
		//@formatter:on

		int version = 1;
		IJavaProject project = newEmptyProject();
		IPackageFragmentRoot src = project.getPackageFragmentRoot(project.getProject().getFolder("src"));
		IPackageFragment _package = src.createPackageFragment("_package", false, null);
		ICompilationUnit unit = _package.createCompilationUnit("A.java", content, false, null);
		openDocument(unit, unit.getSource(), version);
		assertEquals(1, javaClient.params.size());
		SemanticHighlightingParams params = javaClient.params.get(0);
		assertEquals(3, params.getLines().size());

		javaClient.params.clear();
		String insertContent = "\n";
		Range insertRange = JDTUtils.toRange(unit, 0, 0);
		changeDocument(unit, insertContent, version++, insertRange, 0);
		assertEquals(0, javaClient.params.size());
	}

	@Test
	public void testDidChange_insertNewLineBelow() throws Exception {
		//@formatter:off
		String content = "package _package;\n" +
				"\n" +
				"public class A { }\n" +
				"class SB { }\n" +
				"class SC { }";
		//@formatter:on

		int version = 1;
		IJavaProject project = newEmptyProject();
		IPackageFragmentRoot src = project.getPackageFragmentRoot(project.getProject().getFolder("src"));
		IPackageFragment _package = src.createPackageFragment("_package", false, null);
		ICompilationUnit unit = _package.createCompilationUnit("A.java", content, false, null);
		openDocument(unit, unit.getSource(), version);
		assertEquals(1, javaClient.params.size());
		SemanticHighlightingParams params = javaClient.params.get(0);
		assertEquals(3, params.getLines().size());

		javaClient.params.clear();
		String insertContent = "\n";
		Range insertRange = JDTUtils.toRange(unit, content.length(), 0);
		changeDocument(unit, insertContent, version++, insertRange, 0);
		assertEquals(0, javaClient.params.size());
	}

	@Test
	public void testDidChange_insertNewLineMiddle() throws Exception {
		//@formatter:off
		String content = "package _package;\n" +
				"\n" +
				"public class A { }\n" +
				"class SB { }\n" +
				"class SC { }";
		//@formatter:on

		int version = 1;
		IJavaProject project = newEmptyProject();
		IPackageFragmentRoot src = project.getPackageFragmentRoot(project.getProject().getFolder("src"));
		IPackageFragment _package = src.createPackageFragment("_package", false, null);
		ICompilationUnit unit = _package.createCompilationUnit("A.java", content, false, null);
		openDocument(unit, unit.getSource(), version);
		assertEquals(1, javaClient.params.size());
		SemanticHighlightingParams params = javaClient.params.get(0);
		assertEquals(3, params.getLines().size());

		javaClient.params.clear();
		String insertContent = "\n";
		Range insertRange = JDTUtils.toRange(unit, content.indexOf("class SC"), 0);
		changeDocument(unit, insertContent, version++, insertRange, 0);
		assertEquals(0, javaClient.params.size());
	}

	@Test
	public void testDidChange_deleteNewLineAbove() throws Exception {
		//@formatter:off
		String content = "// delete this line\n" +
				"package _package;\n" +
				"\n" +
				"public class A { }\n" +
				"class SB { }\n" +
				"class SC { }";
		//@formatter:on

		int version = 1;
		IJavaProject project = newEmptyProject();
		IPackageFragmentRoot src = project.getPackageFragmentRoot(project.getProject().getFolder("src"));
		IPackageFragment _package = src.createPackageFragment("_package", false, null);
		ICompilationUnit unit = _package.createCompilationUnit("A.java", content, false, null);
		openDocument(unit, unit.getSource(), version);
		assertEquals(1, javaClient.params.size());
		SemanticHighlightingParams params = javaClient.params.get(0);
		assertEquals(3, params.getLines().size());

		javaClient.params.clear();
		Range deleteRange = JDTUtils.toRange(unit, content.indexOf("// delete this line"), "// delete this line\n".length());
		changeDocument(unit, "", version++, deleteRange, "// delete this line\n".length());
		assertEquals(0, javaClient.params.size());
	}

	@Test
	public void testDidChange_deleteNewLineBelow() throws Exception {
		//@formatter:off
		String content = "package _package;\n" +
				"\n" +
				"public class A { }\n" +
				"class SB { }\n" +
				"class SC { }\n" +
				"// delete this line\n";
		//@formatter:on

		int version = 1;
		IJavaProject project = newEmptyProject();
		IPackageFragmentRoot src = project.getPackageFragmentRoot(project.getProject().getFolder("src"));
		IPackageFragment _package = src.createPackageFragment("_package", false, null);
		ICompilationUnit unit = _package.createCompilationUnit("A.java", content, false, null);
		openDocument(unit, unit.getSource(), version);
		assertEquals(1, javaClient.params.size());
		SemanticHighlightingParams params = javaClient.params.get(0);
		assertEquals(3, params.getLines().size());

		javaClient.params.clear();
		Range deleteRange = JDTUtils.toRange(unit, content.indexOf("// delete this line"), "// delete this line\n".length());
		changeDocument(unit, "", version++, deleteRange, "// delete this line\n".length());
		assertEquals(0, javaClient.params.size());
	}

	@Test
	public void testDidChange_deleteNewLineMiddle() throws Exception {
		//@formatter:off
		String content = "package _package;\n" +
				"\n" +
				"// delete this line\n" +
				"\n" +
				"public class A { }\n" +
				"class SB { }\n" +
				"class SC { }";
		//@formatter:on

		int version = 1;
		IJavaProject project = newEmptyProject();
		IPackageFragmentRoot src = project.getPackageFragmentRoot(project.getProject().getFolder("src"));
		IPackageFragment _package = src.createPackageFragment("_package", false, null);
		ICompilationUnit unit = _package.createCompilationUnit("A.java", content, false, null);
		openDocument(unit, unit.getSource(), version);
		assertEquals(1, javaClient.params.size());
		SemanticHighlightingParams params = javaClient.params.get(0);
		assertEquals(3, params.getLines().size());

		javaClient.params.clear();
		Range deleteRange = JDTUtils.toRange(unit, content.indexOf("// delete this line"), "// delete this line\n".length());
		changeDocument(unit, "", version++, deleteRange, "// delete this line\n".length());
		assertEquals(0, javaClient.params.size());
	}

	@Test
	public void testDidChange_editLineMiddle_Add() throws Exception {
		//@formatter:off
		String content = "package _package;\n" +
				"\n" +
				"public class A { }\n" +
				"class SB { }\n" +
				"class SC { }";
		//@formatter:on

		int version = 1;
		IJavaProject project = newEmptyProject();
		IPackageFragmentRoot src = project.getPackageFragmentRoot(project.getProject().getFolder("src"));
		IPackageFragment _package = src.createPackageFragment("_package", false, null);
		ICompilationUnit unit = _package.createCompilationUnit("A.java", content, false, null);
		openDocument(unit, unit.getSource(), version);
		assertEquals(1, javaClient.params.size());
		SemanticHighlightingParams params = javaClient.params.get(0);
		assertEquals(3, params.getLines().size());

		javaClient.params.clear();
		Range editRange = JDTUtils.toRange(unit, content.indexOf("public class A"), "public class A".length());
		changeDocument(unit, "class SA { } public class A", version++, editRange, "public class A".length());
		assertEquals(1, javaClient.params.size());
		assertEquals(1, javaClient.params.get(0).getLines().size());
		assertEquals(2, javaClient.params.get(0).getLines().get(0).getLine());
		List<Token> tokens = decode(javaClient.params.get(0).getLines().get(0).getTokens());

		SemanticHighlightingTokens.Token tokenSA = tokens.get(0);
		assertEquals(6, tokenSA.character);
		assertEquals(2, tokenSA.length);
		assertThat(SemanticHighlightingService.getScopes(tokenSA.scope), hasItem("entity.name.type.class.java"));

		SemanticHighlightingTokens.Token tokenA = tokens.get(1);
		assertEquals(26, tokenA.character);
		assertEquals(1, tokenA.length);
		assertThat(SemanticHighlightingService.getScopes(tokenA.scope), hasItem("entity.name.type.class.java"));
	}

	@Test
	public void testDidChange_editLineMiddle_Remove() throws Exception {
		//@formatter:off
		String content = "package _package;\n" +
				"\n" +
				"class SA { } public class A { }\n" +
				"class SB { }\n" +
				"class SC { }";
		//@formatter:on

		int version = 1;
		IJavaProject project = newEmptyProject();
		IPackageFragmentRoot src = project.getPackageFragmentRoot(project.getProject().getFolder("src"));
		IPackageFragment _package = src.createPackageFragment("_package", false, null);
		ICompilationUnit unit = _package.createCompilationUnit("A.java", content, false, null);
		openDocument(unit, unit.getSource(), version);
		assertEquals(1, javaClient.params.size());
		SemanticHighlightingParams params = javaClient.params.get(0);
		assertEquals(3, params.getLines().size());

		javaClient.params.clear();
		Range editRange = JDTUtils.toRange(unit, content.indexOf("class SA { } "), "class SA { } ".length());
		changeDocument(unit, "", version++, editRange, "class SA { } ".length());
		assertEquals(1, javaClient.params.size());
		assertEquals(1, javaClient.params.get(0).getLines().size());
		assertEquals(2, javaClient.params.get(0).getLines().get(0).getLine());
		List<SemanticHighlightingTokens.Token> tokens = decode(javaClient.params.get(0).getLines().get(0).getTokens());

		SemanticHighlightingTokens.Token tokenA = tokens.get(0);
		assertEquals(13, tokenA.character);
		assertEquals(1, tokenA.length);
		assertThat(SemanticHighlightingService.getScopes(tokenA.scope), hasItem("entity.name.type.class.java"));
	}

	@Test
	public void testDidChange_insertWhitespace() throws Exception {
		//@formatter:off
		String content = "package _package;\n" +
				"\n" +
				"public class A {\n" +
				"  public static void main(String[] args) {\n" +
				"    String s = new String(\"foo\");\n" +
				"  }\n" +
				"}";
		//@formatter:on

		int version = 1;
		IJavaProject project = newEmptyProject();
		IPackageFragmentRoot src = project.getPackageFragmentRoot(project.getProject().getFolder("src"));
		IPackageFragment _package = src.createPackageFragment("_package", false, null);
		ICompilationUnit unit = _package.createCompilationUnit("A.java", content, false, null);
		openDocument(unit, unit.getSource(), version);
		assertEquals(1, javaClient.params.size());
		SemanticHighlightingParams params = javaClient.params.get(0);
		assertEquals(3, params.getLines().size());

		javaClient.params.clear();
		String insertContent = " ";
		Range insertRange = JDTUtils.toRange(unit, content.indexOf("    String s = new ") + "    String s = new ".length(), 0);
		changeDocument(unit, insertContent, version++, insertRange, 0);
		assertEquals(1, javaClient.params.size());

		List<SemanticHighlightingInformation> lines = javaClient.params.get(0).getLines();
		assertEquals(1, lines.size());
		assertEquals(4, lines.get(0).getLine());

		List<SemanticHighlightingTokens.Token> tokens = decode(lines.get(0).getTokens());
		assertEquals(3, tokens.size());
		assertEquals(4, tokens.get(0).character); // 4 is the indentation offset.
		assertEquals(6, tokens.get(0).length);
		assertEquals(11, tokens.get(1).character);
		assertEquals(1, tokens.get(1).length);
		assertEquals(20, tokens.get(2).character);
		assertEquals(6, tokens.get(2).length);
	}

	@Test
	public void testDidChange_singleLineComment() throws Exception {
		//@formatter:off
		String content = "package _package;\n" +
				"\n" +
				"public class A {\n" +
				"  public static void main(String[] args) {\n" +
				"    String s = new String(\"foo\");\n" +
				"  }\n" +
				"}";
		//@formatter:on

		int version = 1;
		IJavaProject project = newEmptyProject();
		IPackageFragmentRoot src = project.getPackageFragmentRoot(project.getProject().getFolder("src"));
		IPackageFragment _package = src.createPackageFragment("_package", false, null);
		ICompilationUnit unit = _package.createCompilationUnit("A.java", content, false, null);
		openDocument(unit, unit.getSource(), version);
		assertEquals(1, javaClient.params.size());
		SemanticHighlightingParams params = javaClient.params.get(0);
		assertEquals(3, params.getLines().size());

		javaClient.params.clear();
		String insertContent = " // ";
		Range insertRange = JDTUtils.toRange(unit, content.indexOf("    String s = new "), 0);
		changeDocument(unit, insertContent, version++, insertRange, 0);
		assertEquals(1, javaClient.params.size());

		List<SemanticHighlightingInformation> lines = javaClient.params.get(0).getLines();
		assertEquals(1, lines.size());
		assertEquals(4, lines.get(0).getLine());
		List<SemanticHighlightingTokens.Token> tokens = decode(lines.get(0).getTokens());
		assertEquals(0, tokens.size());
	}

	@Test
	public void testDidChange_blockComment() throws Exception {
		//@formatter:off
		String content = "package _package;\n" +
				"\n" +
				"public class A {\n" +
				"  public static void main(String[] args) {\n" +
				"    String s = new String(\"foo\");\n" +
				"    Integer i = Integer.valueOf(36);\n" +
				"  }\n" +
				"}";
		//@formatter:on

		int version = 1;
		IJavaProject project = newEmptyProject();
		IPackageFragmentRoot src = project.getPackageFragmentRoot(project.getProject().getFolder("src"));
		IPackageFragment _package = src.createPackageFragment("_package", false, null);
		ICompilationUnit unit = _package.createCompilationUnit("A.java", content, false, null);
		openDocument(unit, unit.getSource(), version);
		assertEquals(1, javaClient.params.size());
		SemanticHighlightingParams params = javaClient.params.get(0);
		assertEquals(4, params.getLines().size());

		// This will result in a compiler error that we fix with the next change.
		javaClient.params.clear();
		changeDocument(unit, version++, new TextDocumentContentChangeEvent(new Range(new Position(3, 42), new Position(3, 42)), 0, "/*"));

		assertEquals(1, javaClient.params.size());
		List<SemanticHighlightingInformation> lines = javaClient.params.get(0).getLines();
		assertEquals(3, lines.size());

		assertEquals(3, lines.get(0).getLine());
		assertEquals(0, decode(lines.get(0).getTokens()).size());
		assertEquals(4, lines.get(1).getLine());
		assertEquals(0, decode(lines.get(1).getTokens()).size());
		assertEquals(5, lines.get(2).getLine());
		assertEquals(0, decode(lines.get(2).getTokens()).size());

		javaClient.params.clear();
		changeDocument(unit, version++, new TextDocumentContentChangeEvent(new Range(new Position(6, 2), new Position(6, 2)), 0, "*/"));

		assertEquals(1, javaClient.params.size());

		lines = javaClient.params.get(0).getLines();
		assertEquals(1, lines.size());
		assertEquals(3, lines.get(0).getLine());

		List<SemanticHighlightingTokens.Token> tokens = decode(lines.get(0).getTokens());
		assertEquals(3, tokens.size());

		assertEquals(21, tokens.get(0).character);
		assertEquals(4, tokens.get(0).length);

		assertEquals(26, tokens.get(1).character);
		assertEquals(6, tokens.get(1).length);

		assertEquals(35, tokens.get(2).character);
		assertEquals(4, tokens.get(2).length);
	}

	@Test
	public void testDidChange_insertConsecutiveLinesMiddle_singleChange() throws Exception {
		//@formatter:off
		String content = "package _package;\n" +
				"\n" +
				"class B {\n" +
				"}\n" +
				"public class A {\n" +
				"  public B b;\n" +
				"  public A a;\n" +
				"}";
		//@formatter:on

		int version = 1;
		IJavaProject project = newEmptyProject();
		IPackageFragmentRoot src = project.getPackageFragmentRoot(project.getProject().getFolder("src"));
		IPackageFragment _package = src.createPackageFragment("_package", false, null);
		ICompilationUnit unit = _package.createCompilationUnit("A.java", content, false, null);
		openDocument(unit, unit.getSource(), version);
		assertEquals(1, javaClient.params.size());
		SemanticHighlightingParams params = javaClient.params.get(0);
		assertEquals(4, params.getLines().size());

		javaClient.params.clear();
		String insertContent = "\n";
		Range insertRange = JDTUtils.toRange(unit, content.indexOf("  public A a;\n"), 0);
		changeDocument(unit, insertContent, version++, insertRange, 0);
		assertEquals(0, javaClient.params.size());

		// Now, insert a new line again. We're expecting zero deltas.
		javaClient.params.clear();
		insertRange = JDTUtils.toRange(unit, content.indexOf("  public A a;\n"), 0);
		changeDocument(unit, insertContent, version++, insertRange, 0);
		assertEquals(0, javaClient.params.size());
	}

	@Test
	public void testDidChange_insertConsecutiveLinesMiddle_multipleChanges() throws Exception {
		//@formatter:off
		String content = "package _package;\n" +
				"\n" +
				"class B {\n" +
				"}\n" +
				"public class A {\n" +
				"  public B b;\n" +
				"  public A a;\n" +
				"}";
		//@formatter:on

		int version = 1;
		IJavaProject project = newEmptyProject();
		IPackageFragmentRoot src = project.getPackageFragmentRoot(project.getProject().getFolder("src"));
		IPackageFragment _package = src.createPackageFragment("_package", false, null);
		ICompilationUnit unit = _package.createCompilationUnit("A.java", content, false, null);
		openDocument(unit, unit.getSource(), version);
		assertEquals(1, javaClient.params.size());
		SemanticHighlightingParams params = javaClient.params.get(0);
		assertEquals(4, params.getLines().size());

		// monaco sends the changes in the following way when entering the first NL after `public B b;`
		/*
		[TextDocumentContentChangeEvent [
		  range = Range [
		    start = Position [
		      line = 5
		      character = 13
		    ]
		    end = Position [
		      line = 5
		      character = 13
		    ]
		  ]
		  rangeLength = 0
		  text = "\n  "
		]]
		 */
		javaClient.params.clear();
		changeDocument(unit, version++, new TextDocumentContentChangeEvent(new Range(new Position(5, 13), new Position(5, 13)), 0, "\n  "));
		assertEquals(0, javaClient.params.size());

		// monaco sends the changes in the following way when entering the second NL at the current position of the cursor.
		/*
		[TextDocumentContentChangeEvent [
		  range = Range [
		    start = Position [
		      line = 6
		      character = 2
		    ]
		    end = Position [
		      line = 6
		      character = 2
		    ]
		  ]
		  rangeLength = 0
		  text = "\n  "
		], TextDocumentContentChangeEvent [
		  range = Range [
		    start = Position [
		      line = 6
		      character = 0
		    ]
		    end = Position [
		      line = 6
		      character = 2
		    ]
		  ]
		  rangeLength = 2
		  text = ""
		]]
		 */
		javaClient.params.clear();
		//@formatter:off
		changeDocument(unit, version++,
				new TextDocumentContentChangeEvent(new Range(new Position(6, 2), new Position(6, 2)), 0, "\n  "),
				new TextDocumentContentChangeEvent(new Range(new Position(6, 0), new Position(6, 2)), 2, "")
		);
		//@formatter:on
		assertEquals(0, javaClient.params.size());
	}

	@Test
	public void testDidChange_insertClassMiddle() throws Exception {
		//@formatter:off
		String content = "package _package;\n" +
				"\n" +
				"class B {\n" +
				"}\n" +
				"public class A {\n" +
				"  public B b;\n" +
				"}";
		//@formatter:on

		int version = 1;
		IJavaProject project = newEmptyProject();
		IPackageFragmentRoot src = project.getPackageFragmentRoot(project.getProject().getFolder("src"));
		IPackageFragment _package = src.createPackageFragment("_package", false, null);
		ICompilationUnit unit = _package.createCompilationUnit("A.java", content, false, null);
		openDocument(unit, unit.getSource(), version);
		assertEquals(1, javaClient.params.size());
		SemanticHighlightingParams params = javaClient.params.get(0);
		assertEquals(3, params.getLines().size());

		javaClient.params.clear();
		String insertContent = "class C { private A a; }\n";
		Range insertRange = JDTUtils.toRange(unit, content.indexOf("class B {"), 0);
		changeDocument(unit, insertContent, version++, insertRange, 0);
		assertEquals(1, javaClient.params.size());
		assertEquals(1, javaClient.params.get(0).getLines().size());
		assertEquals(2, javaClient.params.get(0).getLines().get(0).getLine());
		List<SemanticHighlightingTokens.Token> tokens = decode(javaClient.params.get(0).getLines().get(0).getTokens());
		assertEquals(3, tokens.size());
		SemanticHighlightingTokens.Token tokenC = tokens.get(0);
		assertEquals(6, tokenC.character);
		assertEquals(1, tokenC.length);

		SemanticHighlightingTokens.Token tokenCFieldAType = tokens.get(1);
		assertEquals(18, tokenCFieldAType.character);
		assertEquals(1, tokenCFieldAType.length);

		SemanticHighlightingTokens.Token tokenCFieldA = tokens.get(2);
		assertEquals(20, tokenCFieldA.character);
		assertEquals(1, tokenCFieldA.length);
	}

	protected void openDocument(ICompilationUnit unit, String content, int version) {
		DidOpenTextDocumentParams openParms = new DidOpenTextDocumentParams();
		TextDocumentItem textDocument = new TextDocumentItem();
		textDocument.setLanguageId("java");
		textDocument.setText(content);
		textDocument.setUri(JDTUtils.toURI(unit));
		textDocument.setVersion(version);
		openParms.setTextDocument(textDocument);
		lifeCycleHandler.didOpen(openParms);
	}

	protected void changeDocument(ICompilationUnit unit, int version, TextDocumentContentChangeEvent event, TextDocumentContentChangeEvent... rest) {
		DidChangeTextDocumentParams changeParms = new DidChangeTextDocumentParams();
		VersionedTextDocumentIdentifier textDocument = new VersionedTextDocumentIdentifier();
		textDocument.setUri(JDTUtils.toURI(unit));
		textDocument.setVersion(version);
		changeParms.setTextDocument(textDocument);
		changeParms.setContentChanges(Lists.asList(event, rest));
		lifeCycleHandler.didChange(changeParms);
	}

	protected void changeDocument(ICompilationUnit unit, String content, int version, Range range, int length) {
		DidChangeTextDocumentParams changeParms = new DidChangeTextDocumentParams();
		VersionedTextDocumentIdentifier textDocument = new VersionedTextDocumentIdentifier();
		textDocument.setUri(JDTUtils.toURI(unit));
		textDocument.setVersion(version);
		changeParms.setTextDocument(textDocument);
		TextDocumentContentChangeEvent event = new TextDocumentContentChangeEvent();
		if (range != null) {
			event.setRange(range);
			event.setRangeLength(length);
		}
		event.setText(content);
		List<TextDocumentContentChangeEvent> contentChanges = new ArrayList<>();
		contentChanges.add(event);
		changeParms.setContentChanges(contentChanges);
		lifeCycleHandler.didChange(changeParms);
	}

	private static final class TestJavaClientConnection extends JavaClientConnection {

		public List<SemanticHighlightingParams> params;

		public TestJavaClientConnection(JavaLanguageClient client) {
			super(client);
			this.params = newArrayList();
		}

		@Override
		public void semanticHighlighting(SemanticHighlightingParams params) {
			this.params.add(params);
			super.semanticHighlighting(params);
		}

	}

}
