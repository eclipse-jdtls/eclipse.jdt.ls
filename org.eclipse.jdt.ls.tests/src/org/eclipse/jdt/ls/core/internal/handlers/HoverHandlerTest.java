/*******************************************************************************
 * Copyright (c) 2016-2017 Red Hat Inc. and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Red Hat Inc. - initial API and implementation
 *******************************************************************************/
package org.eclipse.jdt.ls.core.internal.handlers;

import static org.eclipse.jdt.ls.core.internal.JsonMessageHelper.getParams;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import java.net.URI;
import java.nio.file.Paths;

import org.eclipse.core.resources.IProject;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IPackageFragment;
import org.eclipse.jdt.core.IPackageFragmentRoot;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.ls.core.internal.ResourceUtils;
import org.eclipse.jdt.ls.core.internal.WorkspaceHelper;
import org.eclipse.jdt.ls.core.internal.managers.AbstractProjectsManagerBasedTest;
import org.eclipse.lsp4j.Hover;
import org.eclipse.lsp4j.MarkedString;
import org.eclipse.lsp4j.TextDocumentPositionParams;
import org.junit.Before;
import org.junit.Test;

/**
 * @author Fred Bricon
 */
public class HoverHandlerTest extends AbstractProjectsManagerBasedTest {

	private static String HOVER_TEMPLATE =
			"{\n" +
					"    \"id\": \"1\",\n" +
					"    \"method\": \"textDocument/hover\",\n" +
					"    \"params\": {\n" +
					"        \"textDocument\": {\n" +
					"            \"uri\": \"${file}\"\n" +
					"        },\n" +
					"        \"position\": {\n" +
					"            \"line\": ${line},\n" +
					"            \"character\": ${char}\n" +
					"        }\n" +
					"    },\n" +
					"    \"jsonrpc\": \"2.0\"\n" +
					"}";

	private HoverHandler handler;

	private IProject project;

	private IPackageFragmentRoot sourceFolder;

	@Before
	public void setup() throws Exception {
		importProjects("eclipse/hello");
		project = WorkspaceHelper.getProject("hello");
		IJavaProject javaProject = JavaCore.create(project);
		sourceFolder = javaProject.getPackageFragmentRoot(javaProject.getProject().getFolder("src"));
		handler = new HoverHandler();
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
		MarkedString signature = hover.getContents().get(0).getRight();
		assertEquals("Unexpected hover " + signature, "java", signature.getLanguage());
		assertEquals("Unexpected hover " + signature, "java.Foo", signature.getValue());
		String doc = hover.getContents().get(1).getLeft();
		assertEquals("Unexpected hover " + doc, "This is foo", doc);
	}

	@Test
	public void testHoverStandalone() throws Exception {
		//given
		//Hovers on the System.out
		URI standalone = Paths.get("projects","maven","salut","src","main","java","java","Foo.java").toUri();
		String payload = createHoverRequest(standalone, 10, 71);
		TextDocumentPositionParams position = getParams(payload);

		//when
		Hover hover = handler.hover(position, monitor);

		//then
		assertNotNull(hover);
		assertNotNull(hover.getContents());
		MarkedString signature = hover.getContents().get(0).getRight();
		assertEquals("Unexpected hover " + signature, "java", signature.getLanguage());
		assertEquals("Unexpected hover " + signature, "java.Foo", signature.getValue());
		String doc = hover.getContents().get(1).getLeft();
		assertEquals("Unexpected hover "+doc, "This is foo", doc);
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
		String signature = hover.getContents().get(0).getRight().getValue();//
		assertEquals("Unexpected signature ", "java.internal", signature);
		String result = hover.getContents().get(1).getLeft();//
		assertEquals("Unexpected hover ", "this is a **bold** package!", result);
	}

	@Test
	public void testEmptyHover() throws Exception {
		//given
		//Hovers on the System.out
		URI standalone = Paths.get("projects","maven","salut","src","main","java","java","Foo.java").toUri();
		String payload = createHoverRequest(standalone, 1, 2);
		TextDocumentPositionParams position = getParams(payload);

		//when
		Hover hover = handler.hover(position, monitor);

		//then
		assertNotNull(hover);
		assertNotNull(hover.getContents());
		assertEquals(1, hover.getContents().size());
		assertEquals("Should find empty hover for " + payload, "", hover.getContents().get(0).getLeft());
	}

	String createHoverRequest(String file, int line, int kar) {
		URI uri = project.getFile(file).getRawLocationURI();
		return createHoverRequest(uri, line, kar);
	}

	String createHoverRequest(ICompilationUnit cu, int line, int kar) {
		URI uri = cu.getResource().getRawLocationURI();
		return createHoverRequest(uri, line, kar);
	}

	String createHoverRequest(URI file, int line, int kar) {
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
		MarkedString signature = hover.getContents().get(0).getRight();
		assertEquals("Unexpected hover " + signature, "java", signature.getLanguage());
		assertEquals("Unexpected hover " + signature, "String[] args - java.Foo.main(String[])", signature.getValue());
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
		String result = hover.getContents().get(1).getLeft();//
		assertEquals("Unexpected hover ", "This method comes from Foo", result);
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
		assertEquals(1, hover.getContents().size());
		assertEquals("Unexpected hover ", "javax", hover.getContents().get(0).getRight().getValue());
	}

	@Test
	public void testHoverOnPackageWithJavadoc() throws Exception {
		importProjects("maven/salut2");
		project = WorkspaceHelper.getProject("salut2");
		handler = new HoverHandler();
		//given
		//Hovers on the org.apache.commons import
		String payload = createHoverRequest("src/main/java/foo/Bar.java", 2, 22);
		TextDocumentPositionParams position = getParams(payload);

		//when
		Hover hover = handler.hover(position, monitor);
		assertNotNull(hover);
		String result = hover.getContents().get(0).getRight().getValue();//
		assertEquals("Unexpected hover ", "org.apache.commons", result);

		assertEquals(logListener.getErrors().toString(), 0, logListener.getErrors().size());
	}
	/**
	 * @param cu
	 * @return
	 */
	private String getTitleHover(ICompilationUnit cu, int line, int character) {
		// when
		Hover hover = getHover(cu, line, character);

		// then
		assertNotNull(hover);
		MarkedString result = hover.getContents().get(0).getRight();
		return result.getValue();
	}

	private Hover getHover(ICompilationUnit cu, int line, int character) {
		String payload = createHoverRequest(cu, line, character);
		TextDocumentPositionParams position = getParams(payload);
		return handler.hover(position, monitor);
	}
}
