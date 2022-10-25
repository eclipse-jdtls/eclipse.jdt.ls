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

import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.core.runtime.Path;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IPackageFragment;
import org.eclipse.jdt.core.IPackageFragmentRoot;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.ls.core.internal.JavaLanguageServerPlugin;
import org.eclipse.jdt.ls.core.internal.ResourceUtils;
import org.eclipse.jdt.ls.core.internal.WorkspaceHelper;
import org.eclipse.jdt.ls.core.internal.preferences.PreferenceManager;
import org.eclipse.jdt.ls.core.internal.preferences.Preferences;
import org.eclipse.lsp4j.MarkupContent;
import org.eclipse.lsp4j.SignatureHelp;
import org.eclipse.lsp4j.SignatureHelpParams;
import org.eclipse.lsp4j.SignatureInformation;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
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
		JavaLanguageServerPlugin.setPreferencesManager(preferenceManager);
		Preferences p = mock(Preferences.class);
		when(preferenceManager.getPreferences()).thenReturn(p);
		when(p.isImportMavenEnabled()).thenReturn(true);
		when(p.isSignatureHelpEnabled()).thenReturn(true);
		when(p.isSignatureHelpDescriptionEnabled()).thenReturn(false);
		handler = new SignatureHelpHandler(preferenceManager);
	}

	@Test
	public void testSignatureHelp_singleMethod() throws JavaModelException {
		when(preferenceManager.getPreferences().isSignatureHelpDescriptionEnabled()).thenReturn(true);
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
		assertEquals("foo(String s) : int", help.getSignatures().get(help.getActiveSignature()).getLabel());
		assertTrue(help.getSignatures().get(help.getActiveSignature()).getDocumentation().getLeft().length() > 0);
		assertEquals((Integer) 0, help.getActiveParameter());
	}

	@Test
	public void testSignatureHelp_multipleMethods() throws JavaModelException {
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

	// https://github.com/eclipse/eclipse.jdt.ls/issues/1980
	@Test
	public void testSignatureHelp_double() throws JavaModelException {
		IPackageFragment pack1 = sourceFolder.createPackageFragment("test1", false, null);
		StringBuilder buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("public class E {\n");
		buf.append("    public E(String s) {\n");
		buf.append("        this.unique(\n");
		buf.append("    }\n");
		buf.append("    public void unique(double d) {}\n");
		buf.append("}\n");
		ICompilationUnit cu = pack1.createCompilationUnit("E.java", buf.toString(), false, null);
		SignatureHelp help = getSignatureHelp(cu, 3, 20);
		assertNotNull(help);
		assertEquals(1, help.getSignatures().size());
		assertEquals((Integer) 0, help.getActiveParameter());
		assertEquals(help.getSignatures().get(help.getActiveSignature()).getLabel(), "unique(double d) : void");
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
	public void testSignatureHelp_constructor2() throws JavaModelException {
		IPackageFragment pack1 = sourceFolder.createPackageFragment("test1", false, null);
		StringBuilder buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("public class E {\n");
		buf.append("   public void bar() {\n");
		buf.append("     new String(,);\n");
		buf.append("   }\n");
		buf.append("}\n");
		ICompilationUnit cu = pack1.createCompilationUnit("E.java", buf.toString(), false, null);
		SignatureHelp help = getSignatureHelp(cu, 3, 16);
		assertTrue(help.getSignatures().size() > 0);
		assertTrue(help.getSignatures().get(help.getActiveSignature()).getLabel().matches("String\\(byte\\[\\] \\w+, Charset \\w+\\)"));
		assertEquals(0, help.getActiveParameter().intValue());

		help = getSignatureHelp(cu, 3, 17);
		assertTrue(help.getSignatures().size() > 0);
		assertTrue(help.getSignatures().get(help.getActiveSignature()).getLabel().matches("String\\(byte\\[\\] \\w+, Charset \\w+\\)"));
		assertEquals(1, help.getActiveParameter().intValue());
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
	public void testSignatureHelp_constructorParameters3() throws JavaModelException {
		IPackageFragment pack1 = sourceFolder.createPackageFragment("test1", false, null);
		StringBuilder buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("import java.text.StringCharacterIterator;\n");
		buf.append("public class E {\n");
		buf.append("   public void bar() {\n");
		buf.append("     new StringCharacterIterator(\"\", 0,  , 2);\n");
		buf.append("   }\n");
		buf.append("}\n");
		ICompilationUnit cu = pack1.createCompilationUnit("E.java", buf.toString(), false, null);
		SignatureHelp help = getSignatureHelp(cu, 4, 39);
		assertNotNull(help);
		assertEquals(3, help.getSignatures().size());
		// StringCharacterIterator(String arg0, int arg1, int arg2, int arg3)
		assertTrue(help.getSignatures().get(help.getActiveSignature()).getLabel().matches("StringCharacterIterator\\(String \\w+, int \\w+, int \\w+, int \\w+\\)"));
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
		when(preferenceManager.getPreferences().isSignatureHelpDescriptionEnabled()).thenReturn(true);
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
	public void testSignatureHelp_varargs3() throws JavaModelException {
		IPackageFragment pack1 = sourceFolder.createPackageFragment("test1", false, null);
		StringBuilder buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("public class E {\n");
		buf.append("	public void foo(String... args) {}\n");
		buf.append("	public void foo(Integer a, String... args) {}\n");
		buf.append("	public void bar() {\n");
		buf.append("		foo( , args);");
		buf.append("	}\n");
		buf.append("}\n");
		ICompilationUnit cu = pack1.createCompilationUnit("E.java", buf.toString(), false, null);
		SignatureHelp help = getSignatureHelp(cu, 5, 6);
		assertNotNull(help);
		assertEquals(2, help.getSignatures().size());
		SignatureInformation signature = help.getSignatures().get(help.getActiveSignature());
		assertTrue(signature.getLabel().equals("foo(Integer a, String... args) : void"));
		assertEquals(0, help.getActiveParameter().intValue());

		help = getSignatureHelp(cu, 5, 9);
		assertNotNull(help);
		assertEquals(1, help.getActiveParameter().intValue());
	}

	@Test
	public void testSignatureHelp_varargs4() throws JavaModelException {
		IPackageFragment pack1 = sourceFolder.createPackageFragment("test1", false, null);
		StringBuilder buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("public class E {\n");
		buf.append("	public void foo(String... args) {}\n");
		buf.append("	public void foo(Integer a, String... args) {}\n");
		buf.append("	public void bar() {\n");
		buf.append("		foo( , \"()\", \"\");");
		buf.append("	}\n");
		buf.append("}\n");
		ICompilationUnit cu = pack1.createCompilationUnit("E.java", buf.toString(), false, null);
		SignatureHelp help = getSignatureHelp(cu, 5, 6);
		assertNotNull(help);
		assertEquals(2, help.getSignatures().size());
		SignatureInformation signature = help.getSignatures().get(help.getActiveSignature());
		assertTrue(signature.getLabel().equals("foo(Integer a, String... args) : void"));
		assertEquals(0, help.getActiveParameter().intValue());

		help = getSignatureHelp(cu, 5, 11);
		assertNotNull(help);
		assertEquals(2, help.getSignatures().size());
		signature = help.getSignatures().get(help.getActiveSignature());
		assertTrue(signature.getLabel().equals("foo(Integer a, String... args) : void"));
		assertEquals(1, help.getActiveParameter().intValue());

		help = getSignatureHelp(cu, 5, 16);
		assertNotNull(help);
		assertEquals(2, help.getSignatures().size());
		signature = help.getSignatures().get(help.getActiveSignature());
		assertTrue(signature.getLabel().equals("foo(Integer a, String... args) : void"));
		assertEquals(1, help.getActiveParameter().intValue());
	}

	@Test
	public void testSignatureHelp_bracket() throws JavaModelException {
		IPackageFragment pack1 = sourceFolder.createPackageFragment("test1", false, null);
		StringBuilder buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("public class E {\n");
		buf.append("	public void foo(Integer a, String... args) {}\n");
		buf.append("	public void bar() {\n");
		buf.append("		foo(1, \"()\");");
		buf.append("	}\n");
		buf.append("}\n");
		ICompilationUnit cu = pack1.createCompilationUnit("E.java", buf.toString(), false, null);
		SignatureHelp help = getSignatureHelp(cu, 4, 11);
		assertNotNull(help);
		assertEquals(1, help.getSignatures().size());
		SignatureInformation signature = help.getSignatures().get(help.getActiveSignature());
		assertTrue(signature.getLabel().equals("foo(Integer a, String... args) : void"));
		assertEquals(1, help.getActiveParameter().intValue());
	}

	@Test
	public void testSignatureHelp_bracket2() throws JavaModelException {
		IPackageFragment pack1 = sourceFolder.createPackageFragment("test1", false, null);
		StringBuilder buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("public class E {\n");
		buf.append("	public void bar() {\n");
		buf.append("		System.out.println(\"{}\");\n");
		buf.append("	}\n");
		buf.append("}\n");
		ICompilationUnit cu = pack1.createCompilationUnit("E.java", buf.toString(), false, null);
		SignatureHelp help = getSignatureHelp(cu, 3, 23);
		assertNotNull(help);
		SignatureInformation signature = help.getSignatures().get(help.getActiveSignature());
		assertTrue(signature.getLabel().startsWith("println(String"));
		assertEquals(0, help.getActiveParameter().intValue());
	}

	@Test
	public void testSignatureHelp_bracket3() throws JavaModelException {
		IPackageFragment pack1 = sourceFolder.createPackageFragment("test1", false, null);
		StringBuilder buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("public class E {\n");
		buf.append("	public void bar() {\n");
		buf.append("		System.out.println(\"()\");\n");
		buf.append("	}\n");
		buf.append("}\n");
		ICompilationUnit cu = pack1.createCompilationUnit("E.java", buf.toString(), false, null);
		SignatureHelp help = getSignatureHelp(cu, 3, 23);
		assertNotNull(help);
		SignatureInformation signature = help.getSignatures().get(help.getActiveSignature());
		assertTrue(signature.getLabel().startsWith("println(String"));
		assertEquals(0, help.getActiveParameter().intValue());
	}

	@Test
	public void testSignatureHelp_bracket4() throws JavaModelException {
		IPackageFragment pack1 = sourceFolder.createPackageFragment("test1", false, null);
		StringBuilder buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("public class E {\n");
		buf.append("	public void bar() {\n");
		buf.append("		System.out.println(\"[]\");\n");
		buf.append("	}\n");
		buf.append("}\n");
		ICompilationUnit cu = pack1.createCompilationUnit("E.java", buf.toString(), false, null);
		SignatureHelp help = getSignatureHelp(cu, 3, 23);
		assertNotNull(help);
		SignatureInformation signature = help.getSignatures().get(help.getActiveSignature());
		assertTrue(signature.getLabel().startsWith("println(String"));
		assertEquals(0, help.getActiveParameter().intValue());
	}

	@Test
	public void testSignatureHelp_diamond() throws JavaModelException {
		IPackageFragment pack1 = sourceFolder.createPackageFragment("test1", false, null);
		StringBuilder buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("import java.util.HashMap;\n");
		buf.append("public class E {\n");
		buf.append("	public void foo(Object o1) {}\n");
		buf.append("	public void foo(Object o1, Object o2) {}\n");
		buf.append("	public void bar() {\n");
		buf.append("		foo(new HashMap<String, String>());\n");
		buf.append("	}\n");
		buf.append("}\n");
		ICompilationUnit cu = pack1.createCompilationUnit("E.java", buf.toString(), false, null);
		SignatureHelp help = getSignatureHelp(cu, 6, 27);
		assertNotNull(help);
		SignatureInformation signature = help.getSignatures().get(help.getActiveSignature());
		assertTrue(signature.getLabel().equals("foo(Object o1) : void"));
		assertEquals(0, help.getActiveParameter().intValue());
	}

	@Test
	public void testSignatureHelp_comma() throws JavaModelException {
		IPackageFragment pack1 = sourceFolder.createPackageFragment("test1", false, null);
		StringBuilder buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("public class E {\n");
		buf.append("	public void bar() {\n");
		buf.append("		System.out.println(\",\");\n");
		buf.append("	}\n");
		buf.append("}\n");
		ICompilationUnit cu = pack1.createCompilationUnit("E.java", buf.toString(), false, null);
		SignatureHelp help = getSignatureHelp(cu, 3, 23);
		assertNotNull(help);
		SignatureInformation signature = help.getSignatures().get(help.getActiveSignature());
		assertTrue(signature.getLabel().startsWith("println(String"));
		assertEquals(0, help.getActiveParameter().intValue());
	}

	@Test
	public void testSignatureHelp_complexArgs() throws JavaModelException {
		IPackageFragment pack1 = sourceFolder.createPackageFragment("test1", false, null);
		StringBuilder buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("public class E {\n");
		buf.append("	public void foo(Integer[] a, Character[] b) {}\n");
		buf.append("	public void bar() {\n");
		buf.append("		foo(new Integer[]{1, 2, 3}, new Character[]{'a', 'b', 'c'});\n");
		buf.append("	}\n");
		buf.append("}\n");
		ICompilationUnit cu = pack1.createCompilationUnit("E.java", buf.toString(), false, null);
		SignatureHelp help = getSignatureHelp(cu, 4, 12);
		assertNotNull(help);
		assertEquals(1, help.getSignatures().size());
		assertEquals(0, help.getActiveParameter().intValue());

		help = getSignatureHelp(cu, 4, 18);
		assertNotNull(help);
		assertEquals(1, help.getSignatures().size());
		assertEquals(0, help.getActiveParameter().intValue());

		help = getSignatureHelp(cu, 4, 25);
		assertNotNull(help);
		assertEquals(1, help.getSignatures().size());
		assertEquals(0, help.getActiveParameter().intValue());

		help = getSignatureHelp(cu, 4, 40);
		assertNotNull(help);
		assertEquals(1, help.getSignatures().size());
		assertEquals(1, help.getActiveParameter().intValue());

		help = getSignatureHelp(cu, 4, 44);
		assertNotNull(help);
		assertEquals(1, help.getSignatures().size());
		assertEquals(1, help.getActiveParameter().intValue());

		help = getSignatureHelp(cu, 4, 54);
		assertNotNull(help);
		assertEquals(1, help.getSignatures().size());
		assertEquals(1, help.getActiveParameter().intValue());
	}

	@Test
	public void testSignatureHelp_defaultConstructor() throws JavaModelException {
		IPackageFragment pack1 = sourceFolder.createPackageFragment("test1", false, null);
		StringBuilder buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("public class E {\n");
		buf.append("	public void bar() {\n");
		buf.append("		F f = new F();\n");
		buf.append("	}\n");
		buf.append("}\n");
		buf.append("class F {}\n");
		ICompilationUnit cu = pack1.createCompilationUnit("E.java", buf.toString(), false, null);
		SignatureHelp help = getSignatureHelp(cu, 3, 14);
		assertNotNull(help);
		SignatureInformation signature = help.getSignatures().get(help.getActiveSignature());
		assertTrue(signature.getLabel().equals("F()"));
		assertEquals(-1, help.getActiveParameter().intValue());
	}

	@Test
	public void testSignatureHelp_constructorInvocation() throws JavaModelException {
		IPackageFragment pack1 = sourceFolder.createPackageFragment("test1", false, null);
		StringBuilder buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("public class E {\n");
		buf.append("	public E(int a) {\n");
		buf.append("		this(1, 1);\n");
		buf.append("	}\n");
		buf.append("	public E(int a, int b) {}\n");
		buf.append("}\n");
		ICompilationUnit cu = pack1.createCompilationUnit("E.java", buf.toString(), false, null);
		SignatureHelp help = getSignatureHelp(cu, 3, 7);
		SignatureInformation signature = help.getSignatures().get(help.getActiveSignature());
		assertTrue(signature.getLabel().equals("E(int a, int b)"));
		assertEquals(0, help.getActiveParameter().intValue());

		help = getSignatureHelp(cu, 3, 10);
		assertEquals(1, help.getActiveParameter().intValue());
	}

	@Test
	public void testSignatureHelp_record() throws Exception {
		importProjects("eclipse/java16");
		IProject proj = WorkspaceHelper.getProject("java16");
		IJavaProject javaProject = JavaCore.create(proj);
		ICompilationUnit unit = (ICompilationUnit) javaProject.findElement(new Path("foo/bar/Bar.java"));
		SignatureHelp help = getSignatureHelp(unit, 9, 10);
		assertNotNull(help);
		SignatureInformation signature = help.getSignatures().get(help.getActiveSignature());
		assertTrue(signature.getLabel().equals("Edge(int fromNodeId, int toNodeId, Object fromPoint, Object toPoint, double length, Object profile)"));
	}

	@Test
	public void testSignatureHelp_superConstructorInvocation() throws JavaModelException {
		IPackageFragment pack1 = sourceFolder.createPackageFragment("test1", false, null);
		StringBuilder buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("public class E extends F {\n");
		buf.append("	public E() {\n");
		buf.append("		super(1, 2);\n");
		buf.append("	}\n");
		buf.append("}\n");
		buf.append("class F {\n");
		buf.append("	public F(int a, int b) {}\n");
		buf.append("}\n");
		ICompilationUnit cu = pack1.createCompilationUnit("E.java", buf.toString(), false, null);
		SignatureHelp help = getSignatureHelp(cu, 3, 11);
		assertNotNull(help);
		SignatureInformation signature = help.getSignatures().get(help.getActiveSignature());
		assertTrue(signature.getLabel().equals("F(int a, int b)"));
		assertEquals(1, help.getActiveParameter().intValue());
	}

	@Test
	public void testSignatureHelp_superMethod() throws JavaModelException {
		IPackageFragment pack1 = sourceFolder.createPackageFragment("test1", false, null);
		StringBuilder buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("public class E extends F {\n");
		buf.append("	public void foo() {\n");
		buf.append("		E.super.equals(1);\n");
		buf.append("	}\n");
		buf.append("}\n");
		buf.append("class F {}\n");
		ICompilationUnit cu = pack1.createCompilationUnit("E.java", buf.toString(), false, null);
		SignatureHelp help = getSignatureHelp(cu, 3, 17);
		assertNotNull(help);
		SignatureInformation signature = help.getSignatures().get(help.getActiveSignature());
		assertTrue(signature.getLabel().startsWith("equals(Object"));
		assertEquals(0, help.getActiveParameter().intValue());
	}

	@Test
	public void testSignatureHelp_differentDeclaringType() throws Exception {
		IPackageFragment pack1 = sourceFolder.createPackageFragment("test1", false, null);
		StringBuilder buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("public class E {\n");
		buf.append("	public String foo(String a) {return null;}\n");
		buf.append("	public F get() {return null;}\n");
		buf.append("	public void bar() {\n");
		buf.append("		F f = get();\n");
		buf.append("		if (f instanceof G) {\n");
		buf.append("			((G) f).foo();\n");
		buf.append("		}\n");
		buf.append("	}\n");
		buf.append("}\n");
		buf.append("class F {\n");
		buf.append("	public String foo() {return null;}\n");
		buf.append("}\n");
		buf.append("class G extends F {\n");
		buf.append("	public String foo() {return null;}\n");
		buf.append("}\n");
		ICompilationUnit cu = pack1.createCompilationUnit("E.java", buf.toString(), false, null);
		SignatureHelp help = getSignatureHelp(cu, 7, 15);
		assertNotNull(help);
		SignatureInformation signature = help.getSignatures().get(help.getActiveSignature());
		assertTrue(signature.getLabel().equals("foo() : String"));
	}

	@Test
	public void testSignatureHelp_methodChain() throws Exception {
		IPackageFragment pack1 = sourceFolder.createPackageFragment("test1", false, null);
		StringBuilder buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("public class E {\n");
		buf.append("	public E setFoo() {\n");
		buf.append("		return this;\n");
		buf.append("	}\n");
		buf.append("	public E setBar() {\n");
		buf.append("		return this;\n");
		buf.append("	}\n");
		buf.append("	public void foo() {\n");
		buf.append("		setFoo().setBar();\n");
		buf.append("	}\n");
		buf.append("}\n");
		ICompilationUnit cu = pack1.createCompilationUnit("E.java", buf.toString(), false, null);
		SignatureHelp help = getSignatureHelp(cu, 9, 9);
		assertNotNull(help);
		SignatureInformation signature = help.getSignatures().get(help.getActiveSignature());
		assertTrue(signature.getLabel().equals("setFoo() : E"));

		help = getSignatureHelp(cu, 9, 18);
		assertNotNull(help);
		signature = help.getSignatures().get(help.getActiveSignature());
		assertTrue(signature.getLabel().equals("setBar() : E"));
	}

	@Test
	public void testSignatureHelp_callFromMethodName() throws Exception {
		IPackageFragment pack1 = sourceFolder.createPackageFragment("test1", false, null);
		StringBuilder buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("public class E {\n");
		buf.append("	public void foo(Integer a, String... args) {}\n");
		buf.append("	public void bar() {\n");
		buf.append("		foo(1, \"()\");");
		buf.append("	}\n");
		buf.append("}\n");
		ICompilationUnit cu = pack1.createCompilationUnit("E.java", buf.toString(), false, null);
		SignatureHelp help = getSignatureHelp(cu, 4, 4);
		assertNotNull(help);
		assertEquals(1, help.getSignatures().size());
		assertEquals(-1, help.getActiveParameter().intValue());
	}

	@Test
	public void testSignatureHelp_enclosingMethods() throws Exception {
		IPackageFragment pack1 = sourceFolder.createPackageFragment("test1", false, null);
		StringBuilder buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("public class E {\n");
		buf.append("	public void foo(String a) {}\n");
		buf.append("	public void bar() {\n");
		buf.append("		foo(new String());");
		buf.append("	}\n");
		buf.append("}\n");
		ICompilationUnit cu = pack1.createCompilationUnit("E.java", buf.toString(), false, null);
		SignatureHelp help = getSignatureHelp(cu, 4, 10);
		assertNotNull(help);
		assertEquals(1, help.getSignatures().size());
		SignatureInformation signature = help.getSignatures().get(help.getActiveSignature());
		assertTrue(signature.getLabel().equals("foo(String a) : void"));
		assertEquals(0, help.getActiveParameter().intValue());

		help = getSignatureHelp(cu, 4, 17);
		assertNotNull(help);
		signature = help.getSignatures().get(help.getActiveSignature());
		assertTrue(signature.getLabel().equals("String()"));
		assertEquals(-1, help.getActiveParameter().intValue());
	}

	@Test
	public void testSignatureHelp_textBlock() throws Exception {
		importProjects("eclipse/java16");
		project = WorkspaceHelper.getProject("java16");
		IJavaProject javaProject = JavaCore.create(project);
		sourceFolder = javaProject.getPackageFragmentRoot(javaProject.getProject().getFolder("src/main/java"));
		IPackageFragment pack1 = sourceFolder.createPackageFragment("foo.bar", false, null);
		StringBuilder buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("public class E {\n");
		buf.append("	public void bar() {\n");
		buf.append("		System.out.println(\"\"\"\n");
		buf.append("			(,{,[],})\"\"\");\n");
		buf.append("	}\n");
		buf.append("}\n");
		ICompilationUnit cu = pack1.createCompilationUnit("E.java", buf.toString(), false, null);
		SignatureHelp help = getSignatureHelp(cu, 4, 10);
		assertNotNull(help);
		SignatureInformation signature = help.getSignatures().get(help.getActiveSignature());
		assertTrue(signature.getLabel().startsWith("println(String"));
		assertEquals(0, help.getActiveParameter().intValue());
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
	public void testSignatureHelp_lambda2() throws JavaModelException {
		IPackageFragment pack1 = sourceFolder.createPackageFragment("test1", false, null);
		StringBuilder buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("import java.util.Arrays;\n");
		buf.append("public class E {\n");
		buf.append("	public static void main(String[] args) {\n");
		buf.append("		 Arrays.stream(args).filter(a -> a.length() > 0).toArray(String[]::new);\n");
		buf.append("	}\n");
		buf.append("}\n");
		ICompilationUnit cu = pack1.createCompilationUnit("E.java", buf.toString(), false, null);
		SignatureHelp help = getSignatureHelp(cu, 4, 26);
		assertEquals(-1, help.getActiveParameter().intValue());

		help = getSignatureHelp(cu, 4, 30);
		assertEquals(0, help.getActiveParameter().intValue());
	}

	@Test
	public void testSignatureHelp_genericTypes() throws JavaModelException {
		IPackageFragment pack1 = sourceFolder.createPackageFragment("test1", false, null);
		StringBuilder buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("import java.util.HashMap;\n");
		buf.append("import java.util.Map;\n");
		buf.append("public class E {\n");
		buf.append("	public foo() {\n");
		buf.append("		 Map<String, Object> map = new HashMap<>();\n");
		buf.append("		 map.put(\"key\", \"value\");\n");
		buf.append("	}\n");
		buf.append("}\n");
		ICompilationUnit cu = pack1.createCompilationUnit("E.java", buf.toString(), false, null);
		SignatureHelp help = getSignatureHelp(cu, 6, 11);
		SignatureInformation signature = help.getSignatures().get(help.getActiveSignature());
		assertTrue(signature.getLabel().matches("put\\(String \\w+\\, Object \\w+\\) : Object"));
		assertEquals(0, help.getActiveParameter().intValue());

		help = getSignatureHelp(cu, 6, 18);
		assertEquals(1, help.getActiveParameter().intValue());
	}

	@Test
	public void testSignatureHelp_genericTypes2() throws JavaModelException {
		IPackageFragment pack1 = sourceFolder.createPackageFragment("test1", false, null);
		StringBuilder buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("import java.util.List;\n");
		buf.append("import java.util.stream.Collectors;\n");
		buf.append("public class E {\n");
		buf.append("	public foo(List<String> foo) {\n");
		buf.append("		String s = foo.stream().map(m -> m.toString()).collect(Collectors.joining(\", \"));\n");
		buf.append("	}\n");
		buf.append("}\n");
		ICompilationUnit cu = pack1.createCompilationUnit("E.java", buf.toString(), false, null);
		SignatureHelp help = getSignatureHelp(cu, 5, 57);
		SignatureInformation signature = help.getSignatures().get(help.getActiveSignature());
		assertTrue(signature.getLabel().matches("collect\\(Collector<\\? super String,A,R> \\w+\\) : R"));
		assertEquals(0, help.getActiveParameter().intValue());
	}

	@Test
	public void testSignatureHelp_genericTypes3() throws JavaModelException {
		IPackageFragment pack1 = sourceFolder.createPackageFragment("test1", false, null);
		StringBuilder buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("import java.util.Map;\n");
		buf.append("public class E {\n");
		buf.append("	public foo() {\n");
		buf.append("		Map<Object, Object> a = null;\n");
		buf.append("		for (Map.Entry<Object, Object> b : a.entrySet()) {\n");
		buf.append("			b.getKey().toString();\n");
		buf.append("		}\n");
		buf.append("	}\n");
		buf.append("}\n");
		ICompilationUnit cu = pack1.createCompilationUnit("E.java", buf.toString(), false, null);
		SignatureHelp help = getSignatureHelp(cu, 6, 12);
		SignatureInformation signature = help.getSignatures().get(help.getActiveSignature());
		assertTrue(signature.getLabel().equals("getKey() : Object"));
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
	public void testSignatureHelp_skip() throws JavaModelException {
		IPackageFragment pack1 = sourceFolder.createPackageFragment("test1", false, null);
		StringBuilder buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("public class E {\n");
		buf.append("	public foo() {\n");
		buf.append("		new Object()\n");
		buf.append("	}\n");
		buf.append("}\n");
		ICompilationUnit cu = pack1.createCompilationUnit("E.java", buf.toString(), false, null);
		SignatureHelp help = getSignatureHelp(cu, 3, 14);
		assertEquals(0, help.getSignatures().size());
	}

	@Test
	public void testSignatureHelp_nestedInvocation() throws JavaModelException {
		IPackageFragment pack1 = sourceFolder.createPackageFragment("test1", false, null);
		StringBuilder buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("public class E {\n");
		buf.append("	/**\n");
		buf.append("	 * foo\n");
		buf.append("	 */\n");
		buf.append("	public String foo() {return \"\";}\n");
		buf.append("	/**\n");
		buf.append("	 * bar\n");
		buf.append("	 */\n");
		buf.append("	public void bar(String a) {}\n");
		buf.append("	public test() {\n");
		buf.append("		bar(foo());\n");
		buf.append("	}\n");
		buf.append("}\n");
		ICompilationUnit cu = pack1.createCompilationUnit("E.java", buf.toString(), false, null);
		SignatureHelp help = getSignatureHelp(cu, 11, 10);
		assertEquals(1, help.getSignatures().size());
		assertEquals("foo() : String", help.getSignatures().get(help.getActiveSignature()).getLabel());
		help = getSignatureHelp(cu, 11, 11);
		assertEquals("bar(String a) : void", help.getSignatures().get(help.getActiveSignature()).getLabel());
		assertEquals(1, help.getSignatures().size());
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
		// see https://bugs.eclipse.org/bugs/show_bug.cgi?id=575149
		// assertEquals(1, help.getSignatures().size());
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

	// https://github.com/redhat-developer/vscode-java/issues/2097
	@Test
	public void testSignatureHelpConstructor() throws JavaModelException {
		IJavaProject javaProject = JavaCore.create(project);
		IType type = javaProject.findType("test1.SignatureHelp2097");
		ICompilationUnit cu = type.getCompilationUnit();
		SignatureHelp help = getSignatureHelp(cu, 10, 51);
		assertNotNull(help);
		assertEquals(1, help.getSignatures().size());
		assertTrue(help.getSignatures().get(help.getActiveSignature()).getLabel().equals("SignatureHelp2097(String name)"));
		help = getSignatureHelp(cu, 11, 53);
		assertNotNull(help);
		assertEquals(1, help.getSignatures().size());
		assertTrue(help.getSignatures().get(help.getActiveSignature()).getLabel().equals("SignatureHelp2097(String name)"));
	}

	// https://github.com/redhat-developer/vscode-java/issues/2097
	@Test
	public void testSignatureHelpMethod() throws Exception {
		IJavaProject javaProject = JavaCore.create(project);
		IType type = javaProject.findType("test1.SignatureHelp2097");
		IType resultType = javaProject.findType("test1.Result", new NullProgressMonitor());
		assertNotNull(resultType);
		ICompilationUnit cu = type.getCompilationUnit();
		SignatureHelp help = getSignatureHelp(cu, 16, 42);
		assertNotNull(help);
		assertEquals(3, help.getSignatures().size());
		assertTrue(help.getSignatures().get(help.getActiveSignature()).getLabel().equals("success(String msg, Object data) : Boolean"));
		help = getSignatureHelp(cu, 18, 39);
		assertNotNull(help);
		assertEquals(3, help.getSignatures().size());
		assertTrue(help.getSignatures().get(help.getActiveSignature()).getLabel().equals("fail(Object data) : Boolean"));
		help = getSignatureHelp(cu, 23, 31);
		assertNotNull(help);
		assertEquals(3, help.getSignatures().size());
		assertTrue(help.getSignatures().get(help.getActiveSignature()).getLabel().equals("fail(String msg, Object data) : Boolean"));
	}

	@Test
	public void testSignatureHelpDescriptionDisabled() throws Exception {
		IPackageFragment pack1 = sourceFolder.createPackageFragment("test1", false, null);
		StringBuilder buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("public class E {\n");
		buf.append("	/**\n");
		buf.append("	 * This is an API.\n");
		buf.append("	 */\n");
		buf.append("	public void foo(String s) {\n");
		buf.append("		 foo(null)\n");
		buf.append("	}\n");
		buf.append("}\n");
		ICompilationUnit cu = pack1.createCompilationUnit("E.java", buf.toString(), false, null);
		SignatureHelp help = getSignatureHelp(cu, 6, 7);
		assertNotNull(help);
		assertNull(help.getSignatures().get(help.getActiveSignature()).getDocumentation());
	}

	@Test
	public void testSignatureHelpDescriptionEnabled() throws Exception {
		when(preferenceManager.getPreferences().isSignatureHelpDescriptionEnabled()).thenReturn(true);
		IPackageFragment pack1 = sourceFolder.createPackageFragment("test1", false, null);
		StringBuilder buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("public class E {\n");
		buf.append("	/**\n");
		buf.append("	 * This is an API.\n");
		buf.append("	 */\n");
		buf.append("	public void foo(String s) {\n");
		buf.append("		 foo(null)\n");
		buf.append("	}\n");
		buf.append("}\n");
		ICompilationUnit cu = pack1.createCompilationUnit("E.java", buf.toString(), false, null);
		SignatureHelp help = getSignatureHelp(cu, 6, 7);
		assertNotNull(help);
		Either<String, MarkupContent> documentation = help.getSignatures().get(help.getActiveSignature()).getDocumentation();
		assertEquals("This is an API.", documentation.getLeft().trim());
	}

	@Test
	public void testSignatureHelp_erasureType() throws Exception {
		when(preferenceManager.getPreferences().isSignatureHelpDescriptionEnabled()).thenReturn(true);
		IPackageFragment pack1 = sourceFolder.createPackageFragment("test1", false, null);
		StringBuilder buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("public class E {\n");
		buf.append("	public void foo() {\n");
		buf.append("		new V<String>();\n");
		buf.append("	}\n");
		buf.append("}\n");
		buf.append("class V<T> {\n");
		buf.append("	/** hi */\n");
		buf.append("	public V() {}\n");
		buf.append("	private V(String a) {}\n");
		buf.append("}\n");
		ICompilationUnit cu = pack1.createCompilationUnit("E.java", buf.toString(), false, null);
		SignatureHelp help = getSignatureHelp(cu, 3, 16);
		assertNotNull(help);
		assertEquals(1, help.getSignatures().size());
		Either<String, MarkupContent> documentation = help.getSignatures().get(help.getActiveSignature()).getDocumentation();
		assertEquals("hi", documentation.getLeft().trim());
	}

	@Test
	public void testSignatureHelpInClassFile() throws Exception {
		String uri = "jdt://contents/java.base/java.lang/String.class";
		String payload = HOVER_TEMPLATE.replace("${file}", uri).replace("${line}", "10").replace("${char}", "10");
		SignatureHelpParams position = getParams(payload);
		SignatureHelp sh = handler.signatureHelp(position, monitor);
		assertNotNull(sh);
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
