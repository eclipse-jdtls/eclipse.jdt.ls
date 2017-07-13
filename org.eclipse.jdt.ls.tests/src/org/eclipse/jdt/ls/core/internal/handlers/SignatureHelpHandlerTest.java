/*******************************************************************************
 * Copyright (c) 2017 Microsoft Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Microsoft Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.jdt.ls.core.internal.handlers;

import static org.eclipse.jdt.ls.core.internal.JsonMessageHelper.getParams;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.net.URI;

import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IPackageFragment;
import org.eclipse.jdt.core.IPackageFragmentRoot;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.ls.core.internal.ResourceUtils;
import org.eclipse.jdt.ls.core.internal.WorkspaceHelper;
import org.eclipse.jdt.ls.core.internal.preferences.PreferenceManager;
import org.eclipse.jdt.ls.core.internal.preferences.Preferences;
import org.eclipse.lsp4j.SignatureHelp;
import org.eclipse.lsp4j.TextDocumentPositionParams;
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
		assertEquals(help.getSignatures().size(), 1);
		assertEquals(help.getSignatures().get(0).getLabel(), "foo(String s) : int");
		assertTrue(help.getSignatures().get(0).getDocumentation().length() > 0);
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
		assertEquals(help.getSignatures().size(), 3);
		assertEquals(help.getActiveParameter(), (Integer) 1);
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
		assertTrue(help.getSignatures().get(help.getActiveSignature()).getLabel().matches("println\\(\\w+ \\w+\\) : void"));
	}

	private SignatureHelp getSignatureHelp(ICompilationUnit cu, int line, int character) {
		String payload = createSignatureHelpRequest(cu, line, character);
		TextDocumentPositionParams position = getParams(payload);
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
