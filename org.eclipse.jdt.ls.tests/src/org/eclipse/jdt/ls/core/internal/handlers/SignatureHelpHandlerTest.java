/*******************************************************************************
 * Copyright (c) 2017 Microsoft Corporation and others.
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

import static org.eclipse.jdt.ls.core.internal.JsonMessageHelper.getParams;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.net.URI;

import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IPackageFragment;
import org.eclipse.jdt.core.IPackageFragmentRoot;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.ls.core.internal.ResourceUtils;
import org.eclipse.jdt.ls.core.internal.WorkspaceHelper;
import org.eclipse.jdt.ls.core.internal.preferences.PreferenceManager;
import org.eclipse.jdt.ls.core.internal.preferences.Preferences;
import org.eclipse.lsp4j.SignatureHelp;
import org.eclipse.lsp4j.SignatureHelpParams;
import org.eclipse.lsp4j.SignatureInformation;
import org.junit.Before;
import org.junit.Test;

public class SignatureHelpHandlerTest extends AbstractCompilationUnitBasedTest {

	private PreferenceManager preferenceManager;

	private static String HOVER_TEMPLATE =
			"{\n" +
					"    \"id\": \"1\",\n" +
					"    \"method\": \"textDocument/signatureHelp\",\n" +
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

	private SignatureHelpHandler handler;

	private IPackageFragmentRoot sourceFolder;

	@Override
	@Before
	public void setup() throws Exception {
		importProjects("eclipse/hello");
		project = WorkspaceHelper.getProject("hello");
		IJavaProject javaProject = JavaCore.create(project);
		sourceFolder = javaProject.getPackageFragmentRoot(javaProject.getProject().getFolder("src"));
		preferenceManager = mock(PreferenceManager.class);
		Preferences p = mock(Preferences.class);
		when(preferenceManager.getPreferences(null)).thenReturn(p);
		when(p.isSignatureHelpEnabled()).thenReturn(true);
		handler = new SignatureHelpHandler(preferenceManager);
	}

	@Test
	public void testSignatureHelp_singleMethod() throws JavaModelException {
		IPackageFragment pack1 = sourceFolder.createPackageFragment("test1", false, null);
		StringBuilder buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("public class E {\n");
		buf.append("   /** This is a method */\n");
		buf.append("   public int foo(String s) { }\n");
		buf.append("   public int bar(String s) { this.foo() }\n");
		buf.append("}\n");
		ICompilationUnit cu = pack1.createCompilationUnit("E.java", buf.toString(), false, null);

		SignatureHelp help = getSignatureHelp(cu, 4, 39);
		assertNotNull(help);
		assertEquals(1, help.getSignatures().size());
		assertEquals("foo(String s) : int", help.getSignatures().get(0).getLabel());
		assertTrue(help.getSignatures().get(0).getDocumentation().getLeft().length() > 0);
		assertEquals((Integer) 0, help.getActiveParameter());
	}

	@Test
	public void testSignatureHelp_multipeMethod() throws JavaModelException {
		IPackageFragment pack1 = sourceFolder.createPackageFragment("test1", false, null);
		StringBuilder buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("public class E {\n");
		buf.append("   public int foo(String s) { }\n");
		buf.append("   public int foo(int s) { }\n");
		buf.append("   public int foo(int s, String s) { }\n");
		buf.append("   public int bar(String s) { this.foo(2,  ) }\n");
		buf.append("}\n");
		ICompilationUnit cu = pack1.createCompilationUnit("E.java", buf.toString(), false, null);

		SignatureHelp help = getSignatureHelp(cu, 5, 42);
		assertNotNull(help);
		assertEquals(3, help.getSignatures().size());
		assertEquals((Integer) 1, help.getActiveParameter());
		assertEquals(help.getSignatures().get(help.getActiveSignature()).getLabel(), "foo(int s, String s) : int");
	}

	@Test
	public void testSignatureHelp_binary() throws JavaModelException {
		IPackageFragment pack1 = sourceFolder.createPackageFragment("test1", false, null);
		StringBuilder buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("public class E {\n");
		buf.append("   public int bar(String s) { System.out.println(  }\n");
		buf.append("}\n");
		ICompilationUnit cu = pack1.createCompilationUnit("E.java", buf.toString(), false, null);

		SignatureHelp help = getSignatureHelp(cu, 2, 50);
		assertNotNull(help);
		assertTrue(help.getSignatures().size() >= 10);
		assertTrue(help.getSignatures().get(help.getActiveSignature()).getLabel().equals("println() : void"));
		SignatureHelp help2 = getSignatureHelp(cu, 2, 49);
		assertEquals(help.getSignatures().size(), help2.getSignatures().size());
		assertEquals(help.getActiveSignature(), help2.getActiveSignature());
	}

	@Test
	public void testSignatureHelp_invalid() throws JavaModelException {
		IPackageFragment pack1 = sourceFolder.createPackageFragment("test1", false, null);
		StringBuilder buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("public class E {\n");
		buf.append("   public int bar(String s) { if (  }\n");
		buf.append("}\n");
		ICompilationUnit cu = pack1.createCompilationUnit("E.java", buf.toString(), false, null);
		SignatureHelp help = getSignatureHelp(cu, 2, 34);
		assertNotNull(help);
		assertEquals(0, help.getSignatures().size());
	}

	// See https://github.com/eclipse/eclipse.jdt.ls/pull/1015#issuecomment-487997215
	@Test
	public void testSignatureHelp_parameters() throws JavaModelException {
		IPackageFragment pack1 = sourceFolder.createPackageFragment("test1", false, null);
		StringBuilder buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("public class E {\n");
		buf.append("   public boolean bar() {\n");
		buf.append("     foo(\"\",)\n");
		buf.append("     return true;\n");
		buf.append("   }\n");
		buf.append("   public void foo(String s) {}\n");
		buf.append("   public void foo(String s, boolean bar) {}\n");
		buf.append("}\n");
		ICompilationUnit cu = pack1.createCompilationUnit("E.java", buf.toString(), false, null);
		SignatureHelp help = getSignatureHelp(cu, 3, 12);
		assertNotNull(help);
		assertEquals(2, help.getSignatures().size());
		assertEquals(help.getSignatures().get(help.getActiveSignature()).getLabel(), "foo(String s, boolean bar) : void");
	}

	@Test
	public void testSignatureHelp_activeSignature() throws JavaModelException {
		IPackageFragment pack1 = sourceFolder.createPackageFragment("test1", false, null);
		StringBuilder buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("public class E {\n");
		buf.append("   public void bar() {\n");
		buf.append("     foo(\"a\",\"b\");\n");
		buf.append("   }\n");
		buf.append("   public void foo(String s) {}\n");
		buf.append("   public void foo(String s, String b) {}\n");
		buf.append("   public void foo() {}\n");
		buf.append("}\n");
		ICompilationUnit cu = pack1.createCompilationUnit("E.java", buf.toString(), false, null);
		SignatureHelp help = getSignatureHelp(cu, 3, 12);
		assertNotNull(help);
		assertEquals(3, help.getSignatures().size());
		assertEquals(help.getSignatures().get(help.getActiveSignature()).getLabel(), "foo(String s, String b) : void");
	}

	@Test
	public void testSignatureHelp_constructor() throws JavaModelException {
		IPackageFragment pack1 = sourceFolder.createPackageFragment("test1", false, null);
		StringBuilder buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("public class E {\n");
		buf.append("   public void bar() {\n");
		buf.append("     new RuntimeException()\n");
		buf.append("   }\n");
		buf.append("}\n");
		ICompilationUnit cu = pack1.createCompilationUnit("E.java", buf.toString(), false, null);
		SignatureHelp help = getSignatureHelp(cu, 3, 26);
		assertNotNull(help);
		assertEquals(4, help.getSignatures().size());
		assertEquals(help.getSignatures().get(help.getActiveSignature()).getLabel(), "RuntimeException()");
	}

	@Test
	public void testSignatureHelp_constructorParameters() throws JavaModelException {
		IPackageFragment pack1 = sourceFolder.createPackageFragment("test1", false, null);
		StringBuilder buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("public class E {\n");
		buf.append("   public void bar() {\n");
		buf.append("     new RuntimeException(\"t\", )\n");
		buf.append("   }\n");
		buf.append("}\n");
		ICompilationUnit cu = pack1.createCompilationUnit("E.java", buf.toString(), false, null);
		SignatureHelp help = getSignatureHelp(cu, 3, 31);
		assertNotNull(help);
		assertEquals(4, help.getSignatures().size());
		assertTrue(help.getSignatures().get(help.getActiveSignature()).getLabel().matches("RuntimeException\\(String \\w+, Throwable \\w+\\)"));
	}

	@Test
	public void testSignatureHelp_constructorParameters2() throws JavaModelException {
		IPackageFragment pack1 = sourceFolder.createPackageFragment("test1", false, null);
		StringBuilder buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("public class E {\n");
		buf.append("   public void bar() {\n");
		buf.append("     new RuntimeException(\"foo\")\n");
		buf.append("   }\n");
		buf.append("}\n");
		ICompilationUnit cu = pack1.createCompilationUnit("E.java", buf.toString(), false, null);
		SignatureHelp help = getSignatureHelp(cu, 3, 31);
		assertNotNull(help);
		assertEquals(4, help.getSignatures().size());
		assertTrue(help.getSignatures().get(help.getActiveSignature()).getLabel().matches("RuntimeException\\(String \\w+\\)"));
	}

	@Test
	public void testSignatureHelp_javadoc() throws JavaModelException {
		IPackageFragment pack1 = sourceFolder.createPackageFragment("test1", false, null);
		StringBuilder buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("public class E {\n");
		buf.append("/**\n");
		buf.append(" * @see String#substring()\n");
		buf.append(" */\n");
		buf.append("   public int test() {}\n");
		buf.append("}\n");
		ICompilationUnit cu = pack1.createCompilationUnit("E.java", buf.toString(), false, null);
		SignatureHelp help = getSignatureHelp(cu, 3, 25);
		assertNotNull(help);
		assertEquals(2, help.getSignatures().size());
		assertTrue(help.getSignatures().get(help.getActiveSignature()).getLabel().matches("substring\\(\\w+ \\w+\\) : String"));
	}

	// See https://github.com/redhat-developer/vscode-java/issues/1258
	@Test
	public void testSignatureHelp_javadocOriginal() throws JavaModelException {
		IPackageFragment pack1 = sourceFolder.createPackageFragment("test1", false, null);
		StringBuilder buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("import java.util.LinkedList;\n");
		buf.append("import org.sample.MyList;\n");
		buf.append("public class E {\n\n");
		buf.append("	void test() {\n");
		buf.append("		MyList<String> l = new LinkedList<>();\n");
		buf.append("		l.add(\n");
		buf.append("	}\n");
		buf.append("}\n");
		ICompilationUnit cu = pack1.createCompilationUnit("E.java", buf.toString(), false, null);
		int[] loc = findCompletionLocation(cu, "l.add(");
		SignatureHelp help = getSignatureHelp(cu, loc[0], loc[1]);
		assertNotNull(help);
		assertEquals(2, help.getSignatures().size());
		SignatureInformation signature = help.getSignatures().get(help.getActiveSignature());
		assertTrue(signature.getLabel().equals("add(String e) : boolean"));
		String documentation = signature.getDocumentation().getLeft();
		assertEquals(" Test ", documentation);
	}

	@Test
	public void testSignatureHelp_varargs() throws JavaModelException {
		IPackageFragment pack1 = sourceFolder.createPackageFragment("test1", false, null);
		StringBuilder buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("import java.util.Arrays;\n");
		buf.append("public class E {\n");
		buf.append("	public static void main(String[] args) {\n");
		buf.append("		Arrays.asList(1,2,3);\n");
		buf.append("		demo(\"1\", \"2\",\"3\" )\n");
		buf.append("	}\n");
		buf.append("	public static void demo (String s, String... s2) {\n");
		buf.append("	}\n");
		buf.append("}\n");
		ICompilationUnit cu = pack1.createCompilationUnit("E.java", buf.toString(), false, null);
		SignatureHelp help = getSignatureHelp(cu, 4, 21);
		assertNotNull(help);
		assertEquals(1, help.getSignatures().size());
		assertTrue(help.getSignatures().get(help.getActiveSignature()).getLabel().startsWith("asList(T... "));
		help = getSignatureHelp(cu, 5, 19);
		assertNotNull(help);
		assertEquals(1, help.getSignatures().size());
		assertTrue(help.getSignatures().get(help.getActiveSignature()).getLabel().equals("demo(String s, String... s2) : void"));
	}

	@Test
	public void testSignatureHelp_varargs2() throws JavaModelException {
		IJavaProject javaProject = JavaCore.create(project);
		IType type = javaProject.findType("test1.Varargs");
		ICompilationUnit cu = type.getCompilationUnit();
		SignatureHelp help = getSignatureHelp(cu, 4, 16);
		assertNotNull(help);
		assertEquals(2, help.getSignatures().size());
		assertTrue(help.getSignatures().get(help.getActiveSignature()).getLabel().equals("run(Class<?> clazz, String... args) : void"));
	}

	@Test
	public void testSignatureHelp_lambda() throws JavaModelException {
		IJavaProject javaProject = JavaCore.create(project);
		IType type = javaProject.findType("test1.SignatureHelp");
		ICompilationUnit cu = type.getCompilationUnit();
		SignatureHelp help = getSignatureHelp(cu, 8, 14);
		assertNotNull(help);
		assertEquals(1, help.getSignatures().size());
		assertTrue(help.getSignatures().get(help.getActiveSignature()).getLabel().equals("test(Function<String,String> f) : void"));
	}

	@Test
	public void testSignatureHelp_parameterTypes() throws JavaModelException {
		IPackageFragment pack1 = sourceFolder.createPackageFragment("test1", false, null);
		StringBuilder buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("public class E {\n");
		buf.append("	public static void main(String[] args) {\n");
		buf.append("		 new RuntimeException(new Exception(),)\n");
		buf.append("	}\n");
		buf.append("}\n");
		ICompilationUnit cu = pack1.createCompilationUnit("E.java", buf.toString(), false, null);
		SignatureHelp help = getSignatureHelp(cu, 3, 40);
		assertNotNull(help);
		assertEquals(4, help.getSignatures().size());
		assertNull(help.getActiveParameter());
	}

	@Test
	public void testSignatureHelp_parameterObject() throws JavaModelException {
		IPackageFragment pack1 = sourceFolder.createPackageFragment("test1", false, null);
		StringBuilder buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("public class E {\n");
		buf.append("	public static void main(String[] args) {\n");
		buf.append("		 Object foo = new Object();\n");
		buf.append("		 System.err.println(foo);\n");
		buf.append("	}\n");
		buf.append("}\n");
		ICompilationUnit cu = pack1.createCompilationUnit("E.java", buf.toString(), false, null);
		SignatureHelp help = getSignatureHelp(cu, 4, 23);
		assertNotNull(help);
		assertEquals(10, help.getSignatures().size());
		assertNotNull(help.getActiveParameter());
		assertTrue(help.getSignatures().get(help.getActiveSignature()).getLabel().matches("println\\(Object \\w+\\) : void"));
	}

	@Test
	public void testSignatureHelp_stringLiteral() throws JavaModelException {
		IPackageFragment pack1 = sourceFolder.createPackageFragment("test1", false, null);
		StringBuilder buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("public class E {\n");
		buf.append("	public void foo(String p, int x) {\n");
		buf.append("		 foo(\"(\" , 1)\n");
		buf.append("	}\n");
		buf.append("}\n");
		ICompilationUnit cu = pack1.createCompilationUnit("E.java", buf.toString(), false, null);
		testStringLiteral(cu, 3, 7);
		testStringLiteral(cu, 3, 8);
		testStringLiteral(cu, 3, 9);
		testStringLiteral(cu, 3, 10);
		testStringLiteral(cu, 3, 11);
		testStringLiteral(cu, 3, 12);
		testStringLiteral(cu, 3, 13);
		testStringLiteral(cu, 3, 14);
	}

	private void testStringLiteral(ICompilationUnit cu, int line, int character) {
		SignatureHelp help = getSignatureHelp(cu, line, character);
		assertNotNull(help);
		assertEquals(1, help.getSignatures().size());
		assertNotNull(help.getActiveParameter());
		assertTrue(help.getSignatures().get(help.getActiveSignature()).getLabel().equals("foo(String p, int x) : void"));
	}

	@Test
	public void testSignatureHelp_assertEquals() throws Exception {
		importProjects("maven/classpathtest");
		project = WorkspaceHelper.getProject("classpathtest");
		IJavaProject javaProject = JavaCore.create(project);
		sourceFolder = javaProject.getPackageFragmentRoot(javaProject.getProject().getFolder("src/test/java"));
		IPackageFragment pack1 = sourceFolder.createPackageFragment("test1", false, null);
		StringBuilder buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("import static org.junit.Assert.assertEquals;\n");
		buf.append("public class E {\n");
		buf.append("	public static void main(String[] args) {\n");
		buf.append("		 long num = 1;\n");
		buf.append("		 assertEquals(num,num)\n");
		buf.append("	}\n");
		buf.append("}\n");
		ICompilationUnit cu = pack1.createCompilationUnit("E.java", buf.toString(), false, null);
		testAssertEquals(cu, 5, 16);
		testAssertEquals(cu, 5, 17);
		testAssertEquals(cu, 5, 18);
		testAssertEquals(cu, 5, 19);
		testAssertEquals(cu, 5, 20);
		testAssertEquals(cu, 5, 21);
		testAssertEquals(cu, 5, 22);
	}

	private void testAssertEquals(ICompilationUnit cu, int line, int character) {
		SignatureHelp help = getSignatureHelp(cu, line, character);
		assertNotNull(help);
		assertEquals(12, help.getSignatures().size());
		assertNotNull(help.getActiveParameter());
		assertTrue(help.getSignatures().get(help.getActiveSignature()).getLabel().equals("assertEquals(long expected, long actual) : void"));
	}

	private SignatureHelp getSignatureHelp(ICompilationUnit cu, int line, int character) {
		String payload = createSignatureHelpRequest(cu, line, character);
		SignatureHelpParams position = getParams(payload);
		return handler.signatureHelp(position, monitor);
	}

	String createSignatureHelpRequest(ICompilationUnit cu, int line, int kar) {
		URI uri = cu.getResource().getRawLocationURI();
		return createSignatureHelpRequest(uri, line, kar);
	}

	String createSignatureHelpRequest(URI file, int line, int kar) {
		String fileURI = ResourceUtils.fixURI(file);
		return HOVER_TEMPLATE.replace("${file}", fileURI).replace("${line}", String.valueOf(line)).replace("${char}", String.valueOf(kar));
	}

}
