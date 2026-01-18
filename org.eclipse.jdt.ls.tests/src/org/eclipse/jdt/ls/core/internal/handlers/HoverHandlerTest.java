/*******************************************************************************
 * Copyright (c) 2016-2017 Red Hat Inc. and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *     Red Hat Inc. - initial API and implementation
 *******************************************************************************/
package org.eclipse.jdt.ls.core.internal.handlers;

import static org.eclipse.jdt.ls.core.internal.JsonMessageHelper.getParams;
import static org.eclipse.jdt.ls.core.internal.WorkspaceHelper.getProject;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.File;
import java.net.URI;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.jdt.core.IClassFile;
import org.eclipse.jdt.core.IClasspathAttribute;
import org.eclipse.jdt.core.IClasspathEntry;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IPackageFragment;
import org.eclipse.jdt.core.IPackageFragmentRoot;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.ls.core.internal.ClassFileUtil;
import org.eclipse.jdt.ls.core.internal.DependencyUtil;
import org.eclipse.jdt.ls.core.internal.JDTUtils;
import org.eclipse.jdt.ls.core.internal.JavaLanguageServerPlugin;
import org.eclipse.jdt.ls.core.internal.ResourceUtils;
import org.eclipse.jdt.ls.core.internal.WorkspaceHelper;
import org.eclipse.jdt.ls.core.internal.managers.AbstractProjectsManagerBasedTest;
import org.eclipse.jdt.ls.core.internal.preferences.PreferenceManager;
import org.eclipse.jdt.ls.core.internal.preferences.Preferences;
import org.eclipse.jface.text.Document;
import org.eclipse.jface.text.FindReplaceDocumentAdapter;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.IRegion;
import org.eclipse.lsp4j.Hover;
import org.eclipse.lsp4j.MarkedString;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.eclipse.lsp4j.TextDocumentPositionParams;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * @author Fred Bricon
 */
public class HoverHandlerTest extends AbstractProjectsManagerBasedTest {

	private static String HOVER_TEMPLATE = """
			{
			    "id": "1",
			    "method": "textDocument/hover",
			    "params": {
			        "textDocument": {
			            "uri": "${file}"
			        },
			        "position": {
			            "line": ${line},
			            "character": ${char}
			        }
			    },
			    "jsonrpc": "2.0"
			}""";

	private HoverHandler handler;

	private IProject project;

	private IPackageFragmentRoot sourceFolder;

	private PreferenceManager preferenceManager;

	@BeforeEach
	public void setup() throws Exception {
		importProjects("eclipse/hello");
		project = WorkspaceHelper.getProject("hello");
		IJavaProject javaProject = JavaCore.create(project);
		sourceFolder = javaProject.getPackageFragmentRoot(javaProject.getProject().getFolder("src"));
		preferenceManager = mock(PreferenceManager.class);
		when(preferenceManager.getPreferences()).thenReturn(new Preferences());
		handler = new HoverHandler(preferenceManager);
	}

	@Test
	public void testHover() throws Exception {
		//given
		//Hovers on the System.out
		String payload = createHoverRequest("src/java/Foo.java", 5, 15);
		TextDocumentPositionParams position = getParams(payload);

		//when
		Hover hover = handler.hover(position, monitor);

		//then
		assertNotNull(hover);
		assertNotNull(hover.getContents());
		MarkedString signature = hover.getContents().getLeft().get(0).getRight();
		assertEquals("java", signature.getLanguage(), "Unexpected hover " + signature);
		assertEquals("java.Foo", signature.getValue(), "Unexpected hover " + signature);
		String doc = hover.getContents().getLeft().get(1).getLeft();
		assertEquals("This is foo", doc, "Unexpected hover " + doc);
	}

	@Test
	public void testHoverStandalone() throws Exception {
		//given
		//Hovers on the System.out
		URI standalone = Paths.get("projects", "maven", "salut", "src", "main", "java", "java", "Foo.java").toUri();
		String payload = createHoverRequest(standalone, 10, 71);
		TextDocumentPositionParams position = getParams(payload);

		//when
		Hover hover = handler.hover(position, monitor);

		//then
		assertNotNull(hover);
		assertNotNull(hover.getContents());
		MarkedString signature = hover.getContents().getLeft().get(0).getRight();
		assertEquals("java", signature.getLanguage(), "Unexpected hover " + signature);
		assertEquals("java.Foo", signature.getValue(), "Unexpected hover " + signature);
		String doc = hover.getContents().getLeft().get(1).getLeft();
		assertEquals("This is foo", doc, "Unexpected hover " + doc);
	}

	@Test
	public void testHoverPackage() throws Exception {
		// given
		// Hovers on the java.internal package
		String payload = createHoverRequest("src/java/Baz.java", 2, 16);
		TextDocumentPositionParams position = getParams(payload);

		// when
		Hover hover = handler.hover(position, monitor);

		// then
		assertNotNull(hover);
		String signature = hover.getContents().getLeft().get(0).getRight().getValue();//
		assertEquals("java.internal", signature, "Unexpected signature ");
		String result = hover.getContents().getLeft().get(1).getLeft();//
		assertEquals("this is a **bold** package!", result, "Unexpected hover ");
	}

	@Test
	public void testEmptyHover() throws Exception {
		//given
		//Hovers on the System.out
		URI standalone = Paths.get("projects", "maven", "salut", "src", "main", "java", "java", "Foo.java").toUri();
		String payload = createHoverRequest(standalone, 1, 2);
		TextDocumentPositionParams position = getParams(payload);

		//when
		Hover hover = handler.hover(position, monitor);

		//then
		assertNotNull(hover);
		assertNotNull(hover.getContents());
		assertEquals(1, hover.getContents().getLeft().size());
		assertEquals("", hover.getContents().getLeft().get(0).getLeft(), "Should find empty hover for " + payload);
	}

	@Test
	public void testMissingUnit() throws Exception {
		URI uri = Paths.get("projects", "maven", "salut", "src", "main", "java", "java", "Missing.java").toUri();
		String payload = createHoverRequest(uri, 0, 0);
		TextDocumentPositionParams position = getParams(payload);

		//when
		Hover hover = handler.hover(position, monitor);

		//then
		assertNotNull(hover);
		assertNotNull(hover.getContents());
		assertEquals(1, hover.getContents().getLeft().size());
		assertEquals("", hover.getContents().getLeft().get(0).getLeft(), "Should find empty hover for " + payload);
	}

	@Test
	public void testInvalidJavadoc() throws Exception {
		importProjects("maven/aspose");
		IProject project = null;
		ICompilationUnit unit = null;
		try {
			project = ResourcesPlugin.getWorkspace().getRoot().getProject("aspose");
			IJavaProject javaProject = JavaCore.create(project);
			IType type = javaProject.findType("org.sample.TestJavadoc");
			unit = type.getCompilationUnit();
			unit.becomeWorkingCopy(null);
			String uri = JDTUtils.toURI(unit);
			TextDocumentIdentifier textDocument = new TextDocumentIdentifier(uri);
			TextDocumentPositionParams position = new TextDocumentPositionParams(textDocument, new Position(8, 24));
			Hover hover = handler.hover(position, monitor);
			assertNotNull(hover);
			assertNotNull(hover.getContents());
			assertEquals(1, hover.getContents().getLeft().size());
			assertEquals("com.aspose.words.Document.Document(String fileName) throws Exception", hover.getContents().getLeft().get(0).getRight().getValue());
		} finally {
			if (unit != null) {
				unit.discardWorkingCopy();
			}
		}
	}

	String createHoverRequest(String file, int line, int kar) {
		URI uri = project.getFile(file).getRawLocationURI();
		return createHoverRequest(uri, line, kar);
	}

	String createHoverRequest(ICompilationUnit cu, int line, int kar) {
		URI uri = cu.getResource().getRawLocationURI();
		return createHoverRequest(uri, line, kar);
	}

	public static String createHoverRequest(URI file, int line, int kar) {
		String fileURI = ResourceUtils.fixURI(file);
		return HOVER_TEMPLATE.replace("${file}", fileURI)
				.replace("${line}", String.valueOf(line))
				.replace("${char}", String.valueOf(kar));
	}

	@Test
	public void testHoverVariable() throws Exception {
		//given
		//Hover on args parameter
		String argParam = createHoverRequest("src/java/Foo.java", 7, 37);
		TextDocumentPositionParams position = getParams(argParam);

		//when
		Hover hover = handler.hover(position, monitor);

		//then
		assertNotNull(hover);
		assertNotNull(hover.getContents());
		MarkedString signature = hover.getContents().getLeft().get(0).getRight();
		assertEquals("java", signature.getLanguage(), "Unexpected hover " + signature);
		assertEquals("String[] args - java.Foo.main(String[])", signature.getValue(), "Unexpected hover " + signature);
	}

	@Test
	public void testHoverMethod() throws Exception {
		IPackageFragment pack1 = sourceFolder.createPackageFragment("test1", false, null);
		StringBuilder buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("import java.util.Vector;\n");
		buf.append("public class E {\n");
		buf.append("   public int foo(String s) { }\n");
		buf.append("   public static void foo2(String s, String s2) { }\n");
		buf.append("}\n");
		ICompilationUnit cu = pack1.createCompilationUnit("E.java", buf.toString(), false, null);

		assertEquals("int test1.E.foo(String s)", getTitleHover(cu, 3, 15));
		assertEquals("void test1.E.foo2(String s, String s2)", getTitleHover(cu, 4, 24));
	}

	@Test
	public void testHoverTypeParameters() throws Exception {
		IPackageFragment pack1 = sourceFolder.createPackageFragment("test1", false, null);
		StringBuilder buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("import java.util.Vector;\n");
		buf.append("public class E<T> {\n");
		buf.append("   public T foo(T s) { }\n");
		buf.append("   public <U> U bar(U s) { }\n");
		buf.append("}\n");
		ICompilationUnit cu = pack1.createCompilationUnit("E.java", buf.toString(), false, null);

		assertEquals("T", getTitleHover(cu, 3, 10));
		assertEquals("T test1.E.foo(T s)", getTitleHover(cu, 3, 13));
		assertEquals("<U> U test1.E.bar(U s)", getTitleHover(cu, 4, 17));
	}

	@Test
	public void testHoverInheritedJavadoc() throws Exception {
		// given
		// Hovers on the overriding foo()
		String payload = createHoverRequest("src/java/Bar.java", 22, 19);
		TextDocumentPositionParams position = getParams(payload);

		// when
		Hover hover = handler.hover(position, monitor);

		// then
		assertNotNull(hover);
		String result = hover.getContents().getLeft().get(1).getLeft();//
		result = ResourceUtils.dos2Unix(result);
		// @formatter:off
		String expected = """
				This method comes from Foo\s\s
				**Overrides:** foo(...) in Foo

				* **Parameters:**
				  * **input** an input String""";
		// @formatter:on
		assertEquals(expected, result, "Unexpected hover ");
	}

	@Test
	public void testHoverOverNullElement() throws Exception {
		IPackageFragment pack1 = sourceFolder.createPackageFragment("test1", false, null);
		StringBuilder buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("import javax.xml.bind.Binder;\n");
		buf.append("public class E {}\n");
		ICompilationUnit cu = pack1.createCompilationUnit("E.java", buf.toString(), false, null);
		Hover hover = getHover(cu, 1, 8);
		assertNotNull(hover);
		assertEquals(1, hover.getContents().getLeft().size());
		assertEquals("javax", hover.getContents().getLeft().get(0).getRight().getValue(), "Unexpected hover ");
	}

	@Test
	public void testHoverOnPackageWithJavadoc() throws Exception {
		importProjects("maven/salut2");
		project = WorkspaceHelper.getProject("salut2");
		handler = new HoverHandler(preferenceManager);
		//given
		//Hovers on the org.apache.commons import
		String payload = createHoverRequest("src/main/java/foo/Bar.java", 2, 22);
		TextDocumentPositionParams position = getParams(payload);

		//when
		Hover hover = handler.hover(position, monitor);
		assertNotNull(hover);
		String result = hover.getContents().getLeft().get(0).getRight().getValue();//
		assertEquals("org.apache.commons", result, "Unexpected hover ");

		assertEquals(0, logListener.getErrors().size(), logListener.getErrors().toString());
	}

	@Test
	public void testHoverThrowable() throws Exception {
		String uriString = ClassFileUtil.getURI(project, "java.lang.Exception");
		IClassFile classFile = JDTUtils.resolveClassFile(uriString);
		String contents = JavaLanguageServerPlugin.getContentProviderManager().getSource(classFile, monitor);
		IDocument document = new Document(contents);
		IRegion region = new FindReplaceDocumentAdapter(document).find(0, "Throwable", true, false, false, false);
		int offset = region.getOffset();
		int line = document.getLineOfOffset(offset);
		int character = offset - document.getLineOffset(line);
		TextDocumentIdentifier textDocument = new TextDocumentIdentifier(uriString);
		Position position = new Position(line, character);
		TextDocumentPositionParams params = new TextDocumentPositionParams(textDocument, position);
		Hover hover = handler.hover(params, monitor);
		assertNotNull(hover);
		assertFalse(hover.getContents().getLeft().isEmpty(), "Unexpected hover ");
	}

	@Test
	public void testHoverUnresolvedType() throws Exception {
		importProjects("eclipse/unresolvedtype");
		project = WorkspaceHelper.getProject("unresolvedtype");
		handler = new HoverHandler(preferenceManager);
		//given
		//Hovers on the IFoo
		String payload = createHoverRequest("src/pckg/Foo.java", 2, 31);
		TextDocumentPositionParams position = getParams(payload);

		// when
		Hover hover = handler.hover(position, monitor);
		assertNotNull(hover);
		assertTrue(hover.getContents().getLeft().isEmpty(), "Unexpected hover ");
	}

	@Test
	public void testHoverWithAttachedJavadoc() throws Exception {
		File commonPrimitivesJdoc = DependencyUtil.getJavadoc("commons-primitives", "commons-primitives", "1.0");
		assertNotNull(commonPrimitivesJdoc, "Unable to locate  commons-primitives-1.0-javadoc.jar");

		importProjects("maven/attached-javadoc");
		project = WorkspaceHelper.getProject("attached-javadoc");
		handler = new HoverHandler(preferenceManager);
		//given
		//Hovers on org.apache.commons.collections.primitives.ShortCollections which has no source but an attached javadoc
		String payload = createHoverRequest("src/main/java/org/sample/Bar.java", 2, 56);
		TextDocumentPositionParams position = getParams(payload);

		// when
		Hover hover = handler.hover(position, monitor);
		assertNotNull(hover, "Hover is null");
		assertEquals(2, hover.getContents().getLeft().size(), "Unexpected hover contents:\n" + hover.getContents());
		Either<String, MarkedString> javadoc = hover.getContents().getLeft().get(1);
		String content = null;
		assertTrue(javadoc != null && javadoc.getLeft() != null && (content = javadoc.getLeft()) != null, "javadoc has null content");
		assertTrue(content.contains("This class consists exclusively of static methods that operate on or\nreturn ShortCollections"), "Unexpected hover :\n" + content);
		assertTrue(content.contains("**Author:**"), "Unexpected hover :\n" + content);
	}

	@Test
	public void testHoverOnJavadocWithValueTag() throws Exception {
		importProjects("maven/salut");
		project = WorkspaceHelper.getProject("salut");
		handler = new HoverHandler(preferenceManager);
		//given
		String payload = createHoverRequest("src/main/java/java/Foo2.java", 12, 30);
		TextDocumentPositionParams position = getParams(payload);

		// when
		Hover hover = handler.hover(position, monitor);
		assertNotNull(hover, "Hover is null");
		assertEquals(2, hover.getContents().getLeft().size(), "Unexpected hover contents:\n" + hover.getContents());
		Either<String, MarkedString> javadoc = hover.getContents().getLeft().get(1);
		String content = null;
		assertTrue(javadoc != null && javadoc.getLeft() != null && (content = javadoc.getLeft()) != null, "javadoc has null content");
		assertMatches("\\[\"SimpleStringData\"\\]\\(file:/.*/salut/src/main/java/java/Foo2.java#13\\) is a simple String", content);
	}

	@Test
	public void testHoverOnJavadocWithLinkToMethodInClass() throws Exception {
		importProjects("maven/salut");
		project = WorkspaceHelper.getProject("salut");
		handler = new HoverHandler(preferenceManager);
		//given
		String payload = createHoverRequest("src/main/java/java/Foo2.java", 18, 25);
		TextDocumentPositionParams position = getParams(payload);

		// when
		Hover hover = handler.hover(position, monitor);
		assertNotNull(hover, "Hover is null");
		assertEquals(2, hover.getContents().getLeft().size(), "Unexpected hover contents:\n" + hover.getContents());
		Either<String, MarkedString> javadoc = hover.getContents().getLeft().get(1);
		String content = null;
		assertTrue(javadoc != null && javadoc.getLeft() != null && (content = javadoc.getLeft()) != null, "javadoc has null content");
		assertMatches("\\[newMethodBeingLinkedToo\\]\\(file:/.*/salut/src/main/java/java/Foo2.java#23\\)", content);
	}

	@Test
	public void testHoverOnJavadocWithLinkToMethodInOtherClass() throws Exception {
		importProjects("maven/salut");
		project = WorkspaceHelper.getProject("salut");
		handler = new HoverHandler(preferenceManager);

		//given
		String payload = createHoverRequest("src/main/java/java/Foo2.java", 29, 25);
		TextDocumentPositionParams position = getParams(payload);

		// when
		Hover hover = handler.hover(position, monitor);
		assertNotNull(hover, "Hover is null");
		assertEquals(2, hover.getContents().getLeft().size(), "Unexpected hover contents:\n" + hover.getContents());
		Either<String, MarkedString> javadoc = hover.getContents().getLeft().get(1);
		String content = null;
		assertTrue(javadoc != null && javadoc.getLeft() != null && (content = javadoc.getLeft()) != null, "javadoc has null content");
		assertMatches("\\[Foo.linkedFromFoo2\\(\\)\\]\\(file:/.*/salut/src/main/java/java/Foo.java#14\\)", content);
	}

	@Test
	public void testHoverOnJavadocWithMultipleDifferentTypesOfTags() throws Exception {
		importProjects("maven/salut");
		project = WorkspaceHelper.getProject("salut");
		handler = new HoverHandler(preferenceManager);
		//given
		String payload = createHoverRequest("src/main/java/java/Foo2.java", 44, 25);
		TextDocumentPositionParams position = getParams(payload);

		// when
		Hover hover = handler.hover(position, monitor);
		assertNotNull(hover, "Hover is null");
		assertEquals(2, hover.getContents().getLeft().size(), "Unexpected hover contents:\n" + hover.getContents());
		Either<String, MarkedString> javadoc = hover.getContents().getLeft().get(1);
		String content = null;
		assertTrue(javadoc != null && javadoc.getLeft() != null && (content = javadoc.getLeft()) != null, "javadoc has null content");

		//@formatter:off
		String expectedJavadoc =
				"""
			This Javadoc contains a link to \\[newMethodBeingLinkedToo\\]\\(file:/.*/salut/src/main/java/java/Foo2.java#23\\)

			\\* \\*\\*Parameters:\\*\\*
			  \\* \\*\\*someString\\*\\* the string to enter
			\\* \\*\\*Returns:\\*\\*
			  \\* String
			\\* \\*\\*Throws:\\*\\*
			  \\* \\[IOException\\]\\(jdt:/.*\\)
			\\* \\*\\*Since:\\*\\*
			  \\* 0.0.1
			\\* \\*\\*Version:\\*\\*
			  \\* 0.0.1
			\\* \\*\\*Author:\\*\\*
			  \\* jpinkney
			\\* \\*\\*See Also:\\*\\*
			  \\* \\[Online docs for java\\]\\(https://docs.oracle.com/javase/7/docs/api/\\)
			\\* \\*\\*API Note:\\*\\*
			  \\* This is a note""";


		//@formatter:on
		assertMatches(expectedJavadoc, ResourceUtils.dos2Unix(content));
	}

	@Test
	public void testHoverWhenLinkDoesNotExist() throws Exception {
		importProjects("maven/salut");
		project = WorkspaceHelper.getProject("salut");
		handler = new HoverHandler(preferenceManager);

		//given
		String payload = createHoverRequest("src/main/java/java/Foo2.java", 51, 26);
		TextDocumentPositionParams position = getParams(payload);

		// when
		Hover hover = handler.hover(position, monitor);
		assertNotNull(hover, "Hover is null");
		assertEquals(2, hover.getContents().getLeft().size(), "Unexpected hover contents:\n" + hover.getContents());
		Either<String, MarkedString> javadoc = hover.getContents().getLeft().get(1);
		String content = null;
		assertTrue(javadoc != null && javadoc.getLeft() != null && (content = javadoc.getLeft()) != null, "javadoc has null content");
		assertMatches("This link doesnt work \\[LinkToSomethingNotFound\\]\\(\\)", content);
	}

	@Test
	public void testHoverJavadocWithExtraTags() throws Exception {
		IPackageFragment pack1 = sourceFolder.createPackageFragment("test1", false, null);
		String content = """
				package test1;
				/**
				 * Some text.
				 *
				 * @uses java.sql.Driver
				 *
				 * @moduleGraph
				 * @since 9
				 */
				public class Meh {}
				""";
		//@formatter:on
		ICompilationUnit cu = pack1.createCompilationUnit("Meh.java", content, false, null);
		Hover hover = getHover(cu, 9, 15);
		assertNotNull(hover);
		assertEquals(2, hover.getContents().getLeft().size());

		//@formatter:off
		String expectedJavadoc = """
			Some text.

			* **Since:**
			  * 9
			* **Uses:**
			  * java.sql.Driver
			* **@moduleGraph**""";
		//@formatter:on
		String actual = hover.getContents().getLeft().get(1).getLeft();
		actual = ResourceUtils.dos2Unix(actual);
		assertEquals(expectedJavadoc, actual, "Unexpected hover ");
	}

	/**
	 * A simple program.
	 * {@snippet :
	 * class HelloWorld {
	 * 	public static void main(String... args) {
	 * 		System.out.println("Hello World!");      // @highlight substring="println"
	 * 	}
	 * }
	 * }
	 */
	@Test
	public void testHoverJavadocSnippet() throws Exception {
		String name = "java18";
		importProjects("eclipse/" + name);
		IProject project = getProject(name);
		IJavaProject javaProject = JavaCore.create(project);
		IPackageFragmentRoot packageFragmentRoot = javaProject.getPackageFragmentRoot(project.getFolder("src/main/java"));
		IPackageFragment pack1 = packageFragmentRoot.createPackageFragment("test1", false, null);
		StringBuilder buf = new StringBuilder();
		//@formatter:off
		buf.append("""
			package test1;
			/**
			 * A simple program.
			 * {@snippet :
			 * class HelloWorld {
			 *     public static void main(String... args) {
			 *         System.out.println("Hello World!");    // @highlight substring="println"
			 *     }
			 * }
			 * }
			 */
			public class Test {
			}
			""");
		//@formatter:on
		ICompilationUnit cu = pack1.createCompilationUnit("Test.java", buf.toString(), false, null);
		Hover hover = getHover(cu, 11, 15);
		assertNotNull(hover);
		assertEquals(2, hover.getContents().getLeft().size());

		//@formatter:off
		String expectedJavadoc = """
			A simple program.

			&nbsp;class HelloWorld { \s
			&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;public static void main(String... args) { \s
			&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;System.out.**println**("Hello World!");   \s
			&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;}\s\s
			&nbsp;} \s
			  \s
			""";
		//@formatter:on
		String actual = hover.getContents().getLeft().get(1).getLeft();
		actual = ResourceUtils.dos2Unix(actual);
		assertEquals(expectedJavadoc, actual, "Unexpected hover ");
	}

	@Test
	public void testHoverJavadocSnippet2() throws Exception {
		String name = "java18";
		importProjects("eclipse/" + name);
		IProject project = getProject(name);
		IJavaProject javaProject = JavaCore.create(project);
		IPackageFragmentRoot packageFragmentRoot = javaProject.getPackageFragmentRoot(project.getFolder("src/main/java"));
		IPackageFragment pack1 = packageFragmentRoot.createPackageFragment("test1", false, null);
		StringBuilder buf = new StringBuilder();
		//@formatter:off
		buf.append("""
			package test1;
			/**
			 * A simple program.
			 * {@snippet :
			 *   int x = 1;
			 * }
			 */
			public class Test {
			}
			""");
		//@formatter:on
		ICompilationUnit cu = pack1.createCompilationUnit("Test.java", buf.toString(), false, null);
		Hover hover = getHover(cu, 7, 15);
		assertNotNull(hover);
		assertEquals(2, hover.getContents().getLeft().size());

		//@formatter:off
		String expectedJavadoc = """
			A simple program.

			&nbsp;&nbsp;&nbsp;int x = 1;\s\s
			  \s
			""";
		//@formatter:on
		String actual = hover.getContents().getLeft().get(1).getLeft();
		actual = ResourceUtils.dos2Unix(actual);
		assertEquals(expectedJavadoc, actual, "Unexpected hover ");
	}

	@Test
	public void testHoverJavadocLinkPlain() throws Exception {
		String name = "java18";
		importProjects("eclipse/" + name);
		IProject project = getProject(name);
		IJavaProject javaProject = JavaCore.create(project);
		IPackageFragmentRoot packageFragmentRoot = javaProject.getPackageFragmentRoot(project.getFolder("src/main/java"));
		IPackageFragment pack1 = packageFragmentRoot.createPackageFragment("test1", false, null);
		StringBuilder buf = new StringBuilder();
		//@formatter:off
		buf.append("""
			package test1;
			/**
			 * <h4><a id="special_cases_constructor">Special cases</a></h4>
			 * A simple mention of {@linkplain ##special_cases_constructor Special Cases}.
			 * <p> A link to {@linkplain String}
			 */
			public class Test {
			}
			""");
		//@formatter:on
		ICompilationUnit cu = pack1.createCompilationUnit("Test.java", buf.toString(), false, null);
		Hover hover = getHover(cu, 6, 15);
		assertNotNull(hover);
		assertEquals(2, hover.getContents().getLeft().size());

		//@formatter:off
		String expectedJavadoc = """
			#### Special cases

			A simple mention of Special Cases.

			A link to [String](jdt://contents/rtstubs.jar/java.lang/String.class)""";
		//@formatter:on

		String actual = hover.getContents().getLeft().get(1).getLeft();

		// remove everything after the first .class in links, till the first closing parenthesis
		// so [String](jdt://contents/rtstubs.jar/java.lang/String.class?=...) would be converted to [String](jdt://contents/rtstubs.jar/java.lang/String.class)
		actual = actual.replaceAll("(\\]\\(jdt://[^\\)]*?\\.class)[^\\)]*\\)", "$1)");

		actual = ResourceUtils.dos2Unix(actual);
		assertEquals(expectedJavadoc, actual, "Unexpected hover ");
	}

	@Test
	public void testHoverJavadocDlDtDd() throws Exception {
		String name = "java18";
		importProjects("eclipse/" + name);
		IProject project = getProject(name);
		IJavaProject javaProject = JavaCore.create(project);
		IPackageFragmentRoot packageFragmentRoot = javaProject.getPackageFragmentRoot(project.getFolder("src/main/java"));
		IPackageFragment pack1 = packageFragmentRoot.createPackageFragment("test1", false, null);
		StringBuilder buf = new StringBuilder();
		//@formatter:off
		buf.append("""
			package test1;
			/**
			 * <dl>
			 *   <dt><a id="def_language"><b>language</b></a></dt>
			 *
			 *   <dd>ISO 639 alpha-2 or alpha-3 language code, or registered
			 *   language subtags up to 8 alpha letters (for future enhancements).
			 *   When a language has both an alpha-2 code and an alpha-3 code, the
			 *   alpha-2 code must be used.  You can find a full list of valid
			 *   language codes in the IANA Language Subtag Registry (search for
			 *   "Type: language").  The language field is case insensitive, but
			 *   {@code Locale} always canonicalizes to lower case.</dd>
			 *
			 *   <dd>Well-formed language values have the form
			 *   <code>[a-zA-Z]{2,8}</code>.  Note that this is not the full
			 *   BCP47 language production, since it excludes extlang.  They are
			 *   not needed since modern three-letter language codes replace
			 *   them.</dd>
			 *
			 *   <dd>Example: "en" (English), "ja" (Japanese), "kok" (Konkani)</dd>
			 *
			 *   <dt><a id="def_script"><b>script</b></a></dt>
			 *
			 *   <dd>ISO 15924 alpha-4 script code.  You can find a full list of
			 *   valid script codes in the IANA Language Subtag Registry (search
			 *   for "Type: script").  The script field is case insensitive, but
			 *   {@code Locale} always canonicalizes to title case (the first
			 *   letter is upper case and the rest of the letters are lower
			 *   case).</dd>
			 *
			 *   <dd>Well-formed script values have the form
			 *   <code>[a-zA-Z]{4}</code></dd>
			 *
			 *   <dd>Example: "Latn" (Latin), "Cyrl" (Cyrillic)</dd>
			 *
			 * </dl>
			 */
			public class Test {
			}
			""");
		//@formatter:on
		ICompilationUnit cu = pack1.createCompilationUnit("Test.java", buf.toString(), false, null);
		Hover hover = getHover(cu, 37, 15);
		assertNotNull(hover);
		assertEquals(2, hover.getContents().getLeft().size());

		//@formatter:off
		String expectedJavadoc = """
			**language** \s
			ISO 639 alpha-2 or alpha-3 language code, or registered
			    language subtags up to 8 alpha letters (for future enhancements).
			    When a language has both an alpha-2 code and an alpha-3 code, the
			    alpha-2 code must be used. You can find a full list of valid
			    language codes in the IANA Language Subtag Registry (search for
			    "Type: language"). The language field is case insensitive, but
			    `Locale` always canonicalizes to lower case. \s
			Well-formed language values have the form
			    `[a-zA-Z]{2,8}`. Note that this is not the full
			    BCP47 language production, since it excludes extlang. They are
			    not needed since modern three-letter language codes replace
			    them. \s
			Example: "en" (English), "ja" (Japanese), "kok" (Konkani) \s

			**script** \s
			ISO 15924 alpha-4 script code. You can find a full list of
			    valid script codes in the IANA Language Subtag Registry (search
			    for "Type: script"). The script field is case insensitive, but
			    `Locale` always canonicalizes to title case (the first
			    letter is upper case and the rest of the letters are lower
			    case). \s
			Well-formed script values have the form
			    `[a-zA-Z]{4}` \s
			Example: "Latn" (Latin), "Cyrl" (Cyrillic) \s""";
		//@formatter:on
		String actual = hover.getContents().getLeft().get(1).getLeft();
		actual = ResourceUtils.dos2Unix(actual);
		assertEquals(expectedJavadoc, actual, "Unexpected hover ");
	}

	@Test
	public void testHoverOnPackageWithNewJavadoc() throws Exception {
		// See org.eclipse.jdt.ls.tests/testresources/java-doc/readme.txt to generate the remote javadoc
		importProjects("eclipse/remote-javadoc");
		project = WorkspaceHelper.getProject("remote-javadoc");

		// First we need to attach our custom Javadoc to java-doc-0.0.1-SNAPSHOT.jar
		IJavaProject javaProject = JavaCore.create(project);
		IClasspathEntry[] classpath = javaProject.getRawClasspath();
		List<IClasspathEntry> newClasspath = new ArrayList<>(classpath.length);

		for (IClasspathEntry cpe : classpath) {
			if (cpe.getEntryKind() == IClasspathEntry.CPE_LIBRARY && cpe.getPath().lastSegment().equals("java-doc-0.0.1-SNAPSHOT.jar")) {
				String javadocPath = new File("testresources/java-doc/apidocs").getAbsoluteFile().toURI().toString();
				IClasspathAttribute atts[] = new IClasspathAttribute[] { JavaCore.newClasspathAttribute("javadoc_location", javadocPath) };
				IClasspathEntry newCpe = JavaCore.newLibraryEntry(cpe.getPath(), null, null, null, atts, false);
				newClasspath.add(newCpe);
			} else {
				newClasspath.add(cpe);
			}
		}

		javaProject.setRawClasspath(newClasspath.toArray(new IClasspathEntry[classpath.length]), monitor);

		handler = new HoverHandler(preferenceManager);
		//given
		//Hovers on the java.sql import
		String payload = createHoverRequest("src/main/java/foo/bar/Bar.java", 2, 14);
		TextDocumentPositionParams position = getParams(payload);

		//when
		Hover hover = handler.hover(position, monitor);
		assertNotNull(hover);
		String javadoc = hover.getContents().getLeft().get(1).getLeft();
		//Javadoc was read from file://.../org.eclipse.jdt.ls.tests/testresources/java-doc/apidocs/bar/foo/package-summary.html
		assertTrue(javadoc.contains("this doc is powered by **HTML5**"));
		assertFalse(javadoc.contains("----"));//no table nonsense

	}

	@Test
	public void testHoverOnJava10var() throws Exception {
		importProjects("eclipse/java10");
		project = WorkspaceHelper.getProject("java10");
		assertNoErrors(project);

		handler = new HoverHandler(preferenceManager);
		//given
		//Hovers on name.toUpperCase()
		String payload = createHoverRequest("src/main/java/foo/bar/Foo.java", 8, 34);
		TextDocumentPositionParams position = getParams(payload);

		//when
		Hover hover = handler.hover(position, monitor);
		assertNotNull(hover);
		assertNotNull(hover.getContents().getLeft().get(0).getRight(), hover.toString());
		String javadoc = hover.getContents().getLeft().get(0).getRight().getValue();
		assertEquals("String java.lang.String.toUpperCase()", javadoc);

	}

	@Test
	public void testHoverOnJava11var() throws Exception {
		importProjects("eclipse/java11");
		project = WorkspaceHelper.getProject("java11");
		assertNoErrors(project);

		handler = new HoverHandler(preferenceManager);
		//given
		//Hovers on the 1st var of (var i, var j)
		String payload = createHoverRequest("src/main/java/foo/bar/Foo.java", 15, 21);
		TextDocumentPositionParams position = getParams(payload);

		//when
		Hover hover = handler.hover(position, monitor);
		assertNotNull(hover);
		assertNotNull(hover.getContents().getLeft().get(0).getRight(), hover.toString());
		String type = hover.getContents().getLeft().get(0).getRight().getValue();
		assertEquals("foo.bar.Foo", type);
		String javadoc = hover.getContents().getLeft().get(1).getLeft();
		assertEquals("It's a Foo class", javadoc);

		//Hovers on the 2nd var of (var i, var j)
		payload = createHoverRequest("src/main/java/foo/bar/Foo.java", 15, 28);
		position = getParams(payload);

		//when
		hover = handler.hover(position, monitor);
		assertNotNull(hover);
		assertNotNull(hover.getContents().getLeft().get(0).getRight(), hover.toString());
		type = hover.getContents().getLeft().get(0).getRight().getValue();
		assertEquals("foo.bar.Foo.Bar", type);
		javadoc = hover.getContents().getLeft().get(1).getLeft();
		assertEquals("It's a Bar interface", javadoc);

	}

	@Test
	public void testNoLinkWhenClassContentUnsupported() throws Exception {
		initPreferenceManager(false);
		testClassContentSupport("Uses \\[WordUtils\\]\\(\\)");
	}

	@Test
	public void testLinkWhenClassContentSupported() throws Exception {
		assertNotNull(DependencyUtil.getSources("org.apache.commons", "commons-lang3", "3.5"));
		when(preferenceManager.isClientSupportsClassFileContent()).thenReturn(true);
		testClassContentSupport("Uses \\[WordUtils\\]\\(jdt:.*\\)");
	}

	private void testClassContentSupport(String expectedJavadoc) throws Exception {
		importProjects("maven/salut");
		project = WorkspaceHelper.getProject("salut");
		handler = new HoverHandler(preferenceManager);
		//given
		//Hovers on name.toUpperCase()
		String payload = createHoverRequest("src/main/java/org/sample/TestJavadoc.java", 17, 20);
		TextDocumentPositionParams position = getParams(payload);

		//when
		Hover hover = handler.hover(position, monitor);
		assertNotNull(hover);
		String javadoc = hover.getContents().getLeft().get(1).getLeft();
		assertMatches(expectedJavadoc, javadoc);
	}

	// https://github.com/eclipse/eclipse.jdt.ls/issues/1856
	@Test
	public void testEnum() throws Exception {
		String payload = createHoverRequest("src/org/sample/TestEnum.java", 5, 33);
		TextDocumentPositionParams position = getParams(payload);
		Hover hover = handler.hover(position, monitor);
		assertNotNull(hover);
		assertNotNull(hover.getContents());
		MarkedString signature = hover.getContents().getLeft().get(0).getRight();
		assertEquals("java", signature.getLanguage(), "Unexpected hover " + signature);
		assertEquals("ENUM1", signature.getValue(), "Unexpected hover " + signature);
	}

	@Test
	public void testHoverMarkdownComment() throws Exception {
		String name = "java23";
		importProjects("eclipse/" + name);
		IProject project = getProject(name);
		IJavaProject javaProject = JavaCore.create(project);
		IPackageFragmentRoot packageFragmentRoot = javaProject.getPackageFragmentRoot(project.getFolder("src/main/java"));
		IPackageFragment pack1 = packageFragmentRoot.createPackageFragment("test", false, null);
		StringBuilder buf = new StringBuilder();
		//@formatter:off
		buf.append("package test;\n"
				+ "/// ## TestClass\n"
				+ "///\n"
				+ "/// Paragraph\n"
				+ "///\n"
				+ "/// - item 1\n"
				+ "/// - _item 2_\n"
				+ "    public class Test {\n"
				+ "    /// ### m()\n"
				+ "    ///\n"
				+ "    /// Paragraph with _emphasis_\n"
				+ "    /// - item 1\n"
				+ "    /// - item 2\n"
				+ "    /// @param i an _integer_ !\n"
				+ "    void m(int i) {\n"
				+ "    }\n"
				+ "}\n"
				+ "");
		//@formatter:on
		ICompilationUnit cu = pack1.createCompilationUnit("Test.java", buf.toString(), false, null);
		Hover hover = getHover(cu, 7, 18);
		assertNotNull(hover);
		assertEquals(2, hover.getContents().getLeft().size());

		//@formatter:off
		String expectedJavadoc = "## TestClass  \n"
				+ "Paragraph  \n"
				+ "- item 1\n"
				+ "- _item 2_";
		//@formatter:on
		String actual = hover.getContents().getLeft().get(1).getLeft();
		actual = ResourceUtils.dos2Unix(actual);
		assertEquals(expectedJavadoc, actual, "Unexpected hover ");
	}

	private String getTitleHover(ICompilationUnit cu, int line, int character) {
		// when
		Hover hover = getHover(cu, line, character);

		// then
		assertNotNull(hover);
		MarkedString result = hover.getContents().getLeft().get(0).getRight();
		return result.getValue();
	}

	private Hover getHover(ICompilationUnit cu, int line, int character) {
		String payload = createHoverRequest(cu, line, character);
		TextDocumentPositionParams position = getParams(payload);
		return handler.hover(position, monitor);
	}

	@Test
	public void testHoverJavadocDisabled() throws Exception {
		// given - preference is disabled via configuration
		Preferences prefs =  Preferences.createFrom(Map.of(Preferences.JAVA_HOVER_JAVADOC_ENABLED_KEY, false));
		assertFalse(prefs.isHoverJavadocEnabled(), "Javadoc should be disabled");
		when(preferenceManager.getPreferences()).thenReturn(prefs);
		handler = new HoverHandler(preferenceManager);

		// when - hover on a class with Javadoc
		String payload = createHoverRequest("src/java/Foo.java", 5, 15);
		TextDocumentPositionParams position = getParams(payload);
		Hover hoverResult = handler.hover(position, monitor);

		// then - hover should be null
		assertNull(hoverResult, "Hover should be null when Javadoc is disabled");

		// when - enable preference and hover again
		prefs =  Preferences.createFrom(Map.of(Preferences.JAVA_HOVER_JAVADOC_ENABLED_KEY, true));
		when(preferenceManager.getPreferences()).thenReturn(prefs);
		handler = new HoverHandler(preferenceManager);
		Hover hoverResultEnabled = handler.hover(position, monitor);

		// then - hover should now include Javadoc
		assertNotNull(hoverResultEnabled);
		assertTrue(hoverResultEnabled.getContents().getLeft().size() > 0, "Hover should contain content when Javadoc is enabled");
		if (hoverResultEnabled.getContents().getLeft().size() > 1) {
			String doc = hoverResultEnabled.getContents().getLeft().get(1).getLeft();
			assertEquals("This is foo", doc, "Unexpected hover " + doc);
		}
	}

	@Test
	public void testHoverJavadocWithIndexTag() throws Exception {
		IPackageFragment pack1 = sourceFolder.createPackageFragment("test1", false, null);
		String content = """
				package test1;
				/**
				 * Some <dfn>{@index "locale-sensitive"}</dfn> text.
				 */
				public class Meh {}
				""";
		//@formatter:on
		ICompilationUnit cu = pack1.createCompilationUnit("Meh.java", content, false, null);
		Hover hover = getHover(cu, 4, 14);
		assertNotNull(hover);
		assertEquals(2, hover.getContents().getLeft().size());

		//@formatter:off
		String expectedJavadoc = "Some _\"locale-sensitive\"_ text.";
		//@formatter:on
		String actual = hover.getContents().getLeft().get(1).getLeft();
		actual = ResourceUtils.dos2Unix(actual);
		assertEquals(expectedJavadoc, actual, "Unexpected hover ");
	}

	@Test
	public void testHoverMarkdownWithCodeTag_01() throws Exception {
		String name = "java25";
		importProjects("eclipse/" + name);
		IProject project = getProject(name);
		IJavaProject javaProject = JavaCore.create(project);
		IPackageFragmentRoot packageFragmentRoot = javaProject.getPackageFragmentRoot(project.getFolder("src/main/java"));
		IPackageFragment pack1 = packageFragmentRoot.createPackageFragment("test", false, null);
		StringBuilder buf = new StringBuilder();
		//@formatter:off
		buf.append("package test;\n"
				+ "/// {@code List<String>}\n"
				+ "public class Markdown{}\n");
		//@formatter:on
		ICompilationUnit cu = pack1.createCompilationUnit("Test.java", buf.toString(), false, null);
		Hover hover = getHover(cu, 2, 14);
		assertNotNull(hover);
		assertEquals(2, hover.getContents().getLeft().size());
		StringBuilder expectedJavadoc = new StringBuilder();
		//@formatter:off
		expectedJavadoc.append("` List<String>` ");
		//@formatter:on
		String actual = hover.getContents().getLeft().get(1).getLeft();
		actual = ResourceUtils.dos2Unix(actual);
		assertEquals(expectedJavadoc.toString(), actual, "Unexpected hover ");
	}

	@Test
	public void testHoverMarkdownWithCodeTag_02() throws Exception {
		String name = "java25";
		importProjects("eclipse/" + name);
		IProject project = getProject(name);
		IJavaProject javaProject = JavaCore.create(project);
		IPackageFragmentRoot packageFragmentRoot = javaProject.getPackageFragmentRoot(project.getFolder("src/main/java"));
		IPackageFragment pack1 = packageFragmentRoot.createPackageFragment("test", false, null);
		StringBuilder buf = new StringBuilder();
		//@formatter:off
		buf.append("package test;\n"
				+ "/// {@literal List<String> <>*^&`[]}\n"
				+ "public class Markdown{}\n");
		//@formatter:on
		ICompilationUnit cu = pack1.createCompilationUnit("Test.java", buf.toString(), false, null);
		Hover hover = getHover(cu, 2, 14);
		assertNotNull(hover);
		assertEquals(2, hover.getContents().getLeft().size());
		StringBuilder expectedJavadoc = new StringBuilder();
		//@formatter:off
		expectedJavadoc.append(" List\\<String\\> \\<\\>\\*\\^\\&\\`\\[\\] ");
		//@formatter:on
		String actual = hover.getContents().getLeft().get(1).getLeft();
		actual = ResourceUtils.dos2Unix(actual);
		assertEquals(expectedJavadoc.toString(), actual, "Unexpected hover ");
	}
}
