/*******************************************************************************
 * Copyright (c) 2000, 2017 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Copied from /org.eclipse.jdt.ui.tests/ui/org/eclipse/jdt/ui/tests/core/ImportOrganizeTest.java
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *     John Glassmyer <jogl@google.com> - import group sorting is broken - https://bugs.eclipse.org/430303
 *******************************************************************************/
package org.eclipse.jdt.ls.core.internal.codemanipulation;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Hashtable;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

import org.apache.commons.io.IOUtils;
import org.eclipse.core.resources.IFolder;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.core.runtime.Path;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IImportDeclaration;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IPackageFragment;
import org.eclipse.jdt.core.IPackageFragmentRoot;
import org.eclipse.jdt.core.ISourceRange;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.formatter.DefaultCodeFormatterConstants;
import org.eclipse.jdt.core.manipulation.CoreASTProvider;
import org.eclipse.jdt.core.manipulation.OrganizeImportsOperation;
import org.eclipse.jdt.core.manipulation.OrganizeImportsOperation.IChooseImportQuery;
import org.eclipse.jdt.core.search.TypeNameMatch;
import org.eclipse.jdt.ls.core.internal.DependencyUtil;
import org.eclipse.jdt.ls.core.internal.JavaLanguageServerPlugin;
import org.eclipse.jdt.ls.core.internal.WorkspaceHelper;
import org.eclipse.jdt.ls.core.internal.correction.TestOptions;
import org.eclipse.jdt.ls.core.internal.managers.AbstractMavenBasedTest;
import org.eclipse.jdt.ls.core.internal.preferences.Preferences;
import org.eclipse.jface.text.Document;
import org.eclipse.jface.text.IDocument;
import org.eclipse.text.edits.TextEdit;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;


public class ImportOrganizeTest extends AbstractMavenBasedTest {

	private static final String JUNIT_SRC_ENCODING = "ISO-8859-1";
	private IJavaProject javaProject;
	private IPackageFragmentRoot fSourceFolder;
	private IProject project;
	private File junitSrcArchive;

	@Before
	public void setup() throws Exception {
		importProjects("maven/quickstart");
		project = WorkspaceHelper.getProject("quickstart");
		javaProject = JavaCore.create(project);
		Hashtable<String, String> options= TestOptions.getDefaultOptions();
		options.put(DefaultCodeFormatterConstants.FORMATTER_NUMBER_OF_EMPTY_LINES_TO_PRESERVE, String.valueOf(99));
		javaProject.setOptions(options);
		fSourceFolder = javaProject.getPackageFragmentRoot(javaProject.getProject().getFolder("src/main/java"));
		File src = fSourceFolder.getResource().getLocation().toFile();
		src.mkdirs();
		project.getProject().refreshLocal(IResource.DEPTH_INFINITE, new NullProgressMonitor());
	}

	private void requireJUnitSources() throws Exception {
		junitSrcArchive = DependencyUtil.getSources("junit", "junit", "3.8.1");
		assertNotNull("junit-3.8.1-sources.jar not found", junitSrcArchive);
		addFilesFromJar(javaProject, junitSrcArchive, JUNIT_SRC_ENCODING);
	}

	@Override
	@After
	public void cleanUp() throws Exception {
		CoreASTProvider.getInstance().disposeAST();
		super.cleanUp();
		JavaLanguageServerPlugin.getPreferencesManager().getPreferences().setImportOrder(Preferences.JAVA_IMPORT_ORDER_DEFAULT);
	}

	protected IChooseImportQuery createQuery(final String name, final String[] choices, final int[] nEntries) {
		return new IChooseImportQuery() {
			@Override
			public TypeNameMatch[] chooseImports(TypeNameMatch[][] openChoices, ISourceRange[] ranges) {
				assertTrue(name + "-query-nchoices1", choices.length == openChoices.length);
				assertTrue(name + "-query-nchoices2", nEntries.length == openChoices.length);
				for (int i= 0; i < nEntries.length; i++) {
					assertTrue(name + "-query-cnt" + i, openChoices[i].length == nEntries[i]);
				}
				TypeNameMatch[] res= new TypeNameMatch[openChoices.length];
				for (int i= 0; i < openChoices.length; i++) {
					TypeNameMatch[] selection= openChoices[i];
					assertNotNull(name + "-query-setset" + i, selection);
					assertTrue(name + "-query-setlen" + i, selection.length > 0);
					TypeNameMatch found= null;
					for (int k= 0; k < selection.length; k++) {
						if (selection[k].getFullyQualifiedName().equals(choices[i])) {
							found= selection[k];
						}
					}
					assertNotNull(name + "-query-notfound" + i, found);
					res[i]= found;
				}
				return res;
			}
		};
	}

	private void assertImports(ICompilationUnit cu, String[] imports) throws Exception {
		IImportDeclaration[] desc= cu.getImports();
		assertEquals(cu.getElementName() + "-count", imports.length, desc.length);
		for (int i= 0; i < imports.length; i++) {
			assertEquals(cu.getElementName() + "-cmpentries" + i, desc[i].getElementName(), imports[i]);
		}
	}

	@Test
	public void test1() throws Exception {
		requireJUnitSources();
		ICompilationUnit cu= (ICompilationUnit) javaProject.findElement(new Path("junit/runner/BaseTestRunner.java"));
		assertNotNull("BaseTestRunner.java", cu);
		IPackageFragmentRoot root= (IPackageFragmentRoot)cu.getParent().getParent();
		IPackageFragment pack= root.createPackageFragment("mytest", true, null);
		ICompilationUnit colidingCU= pack.getCompilationUnit("TestListener.java");
		colidingCU.createType("public abstract class TestListener {\n}\n", null, true, null);
		String[] order= new String[0];
		IChooseImportQuery query= createQuery("BaseTestRunner", new String[] { "junit.framework.TestListener" }, new int[] { 2 });
		OrganizeImportsOperation op = createOperation(cu, order, false, true, true, query);
		TextEdit edit = op.createTextEdit(new NullProgressMonitor());
		IDocument document = new Document(cu.getSource());
		edit.apply(document);
		try {
			cu.becomeWorkingCopy(new NullProgressMonitor());
			cu.getBuffer().setContents(document.get());
			cu.reconcile(ICompilationUnit.NO_AST, true, null, new NullProgressMonitor());
			//@formatter:off
			assertImports(cu, new String[] {
				"java.io.BufferedReader",
				"java.io.File",
				"java.io.FileInputStream",
				"java.io.FileOutputStream",
				"java.io.IOException",
				"java.io.InputStream",
				"java.io.PrintWriter",
				"java.io.StringReader",
				"java.io.StringWriter",
				"java.lang.reflect.InvocationTargetException",
				"java.lang.reflect.Method",
				"java.lang.reflect.Modifier",
				"java.text.NumberFormat",
				"java.util.Properties",
				"junit.framework.AssertionFailedError",
				"junit.framework.Test",
				"junit.framework.TestListener",
				"junit.framework.TestSuite"
			});
			//@formatter:on
		} finally {
			cu.discardWorkingCopy();
		}
	}

	private void addFilesFromJar(IJavaProject javaProject, File jarFile, String encoding) throws InvocationTargetException, CoreException, IOException {
		IFolder src = (IFolder) fSourceFolder.getResource();
		File targetFile = src.getLocation().toFile();
		try (JarFile file = new JarFile(jarFile)) {
			for (JarEntry entry : Collections.list(file.entries())) {
				if (entry.isDirectory()) {
					continue;
				}
				try (InputStream in = file.getInputStream(entry); Reader reader = new InputStreamReader(in, encoding)) {
					File outFile = new File(targetFile, entry.getName());
					outFile.getParentFile().mkdirs();
					try (OutputStream out = new FileOutputStream(outFile); Writer writer = new OutputStreamWriter(out, encoding)) {
						IOUtils.copy(reader, writer);
					}
				}
			}
		}
		javaProject.getProject().refreshLocal(IResource.DEPTH_INFINITE, new NullProgressMonitor());
	}

	@Test
	public void test1WithOrder() throws Exception {
		requireJUnitSources();
		ICompilationUnit cu = (ICompilationUnit) javaProject.findElement(new Path("junit/runner/BaseTestRunner.java"));
		assertNotNull("BaseTestRunner.java is missing", cu);
		IPackageFragmentRoot root= (IPackageFragmentRoot)cu.getParent().getParent();
		IPackageFragment pack= root.createPackageFragment("mytest", true, null);
		ICompilationUnit colidingCU= pack.getCompilationUnit("TestListener.java");
		colidingCU.createType("public abstract class TestListener {\n}\n", null, true, null);
		String[] order= new String[] { "junit", "java.text", "java.io", "java" };
		IChooseImportQuery query= createQuery("BaseTestRunner", new String[] { "junit.framework.TestListener" }, new int[] { 2 });
		OrganizeImportsOperation op = createOperation(cu, order, false, true, true, query);
		TextEdit edit = op.createTextEdit(new NullProgressMonitor());
		IDocument document = new Document(cu.getSource());
		edit.apply(document);
		try {
			cu.becomeWorkingCopy(new NullProgressMonitor());
			cu.getBuffer().setContents(document.get());
			cu.reconcile(ICompilationUnit.NO_AST, true, null, new NullProgressMonitor());
			//@formatter:off
			assertImports(cu, new String[] {
				"junit.framework.AssertionFailedError",
				"junit.framework.Test",
				"junit.framework.TestListener",
				"junit.framework.TestSuite",
				"java.text.NumberFormat",
				"java.io.BufferedReader",
				"java.io.File",
				"java.io.FileInputStream",
				"java.io.FileOutputStream",
				"java.io.IOException",
				"java.io.InputStream",
				"java.io.PrintWriter",
				"java.io.StringReader",
				"java.io.StringWriter",
				"java.lang.reflect.InvocationTargetException",
				"java.lang.reflect.Method",
				"java.lang.reflect.Modifier",
				"java.util.Properties"
			});
			//@formatter:on
		} finally {
			cu.discardWorkingCopy();
		}
	}


	@Test
	public void testStaticImports1() throws Exception {
		IPackageFragment pack1 = fSourceFolder.createPackageFragment("pack1", false, null);
		StringBuilder buf = new StringBuilder();
		buf.append("package pack1;\n");
		buf.append("\n");
		buf.append("import static java.lang.System.out;\n");
		buf.append("\n");
		buf.append("public class C {\n");
		buf.append("    public int foo() {\n");
		buf.append("        out.print(File.separator);\n");
		buf.append("    }\n");
		buf.append("}\n");
		ICompilationUnit cu= pack1.createCompilationUnit("C.java", buf.toString(), false, null);
		String[] order= new String[] { "java", "pack", "#java" };
		IChooseImportQuery query= createQuery("C", new String[] {}, new int[] {});
		OrganizeImportsOperation op = createOperation(cu, order, false, true, true, query);
		TextEdit edit = op.createTextEdit(new NullProgressMonitor());
		IDocument document = new Document(cu.getSource());
		edit.apply(document);
		try {
			cu.becomeWorkingCopy(new NullProgressMonitor());
			cu.getBuffer().setContents(document.get());
			cu.reconcile(ICompilationUnit.NO_AST, true, null, new NullProgressMonitor());
			buf = new StringBuilder();
			buf.append("package pack1;\n");
			buf.append("\n");
			buf.append("import java.io.File;\n");
			buf.append("\n");
			buf.append("import static java.lang.System.out;\n");
			buf.append("\n");
			buf.append("public class C {\n");
			buf.append("    public int foo() {\n");
			buf.append("        out.print(File.separator);\n");
			buf.append("    }\n");
			buf.append("}\n");
			assertTrue(cu.getSource().equals(buf.toString()));
		} finally {
			cu.discardWorkingCopy();
		}
	}

	@Test
	public void testStaticImports2() throws Exception {
		IPackageFragment pack1 = fSourceFolder.createPackageFragment("pack1", false, null);
		StringBuilder buf = new StringBuilder();
		buf.append("package pack1;\n");
		buf.append("\n");
		buf.append("import static java.io.File.*;\n");
		buf.append("\n");
		buf.append("public class C {\n");
		buf.append("    public String foo() {\n");
		buf.append("        return pathSeparator + separator + File.separator;\n");
		buf.append("    }\n");
		buf.append("}\n");
		ICompilationUnit cu= pack1.createCompilationUnit("C.java", buf.toString(), false, null);
		String[] order= new String[] { "#java.io.File", "java", "pack" };
		IChooseImportQuery query= createQuery("C", new String[] {}, new int[] {});
		OrganizeImportsOperation op = createOperation(cu, order, false, true, true, query);
		TextEdit edit = op.createTextEdit(new NullProgressMonitor());
		IDocument document = new Document(cu.getSource());
		edit.apply(document);
		try {
			cu.becomeWorkingCopy(new NullProgressMonitor());
			cu.getBuffer().setContents(document.get());
			cu.reconcile(ICompilationUnit.NO_AST, true, null, new NullProgressMonitor());
			buf = new StringBuilder();
			buf.append("package pack1;\n");
			buf.append("\n");
			buf.append("import static java.io.File.pathSeparator;\n");
			buf.append("import static java.io.File.separator;\n");
			buf.append("\n");
			buf.append("import java.io.File;\n");
			buf.append("\n");
			buf.append("public class C {\n");
			buf.append("    public String foo() {\n");
			buf.append("        return pathSeparator + separator + File.separator;\n");
			buf.append("    }\n");
			buf.append("}\n");
			assertTrue(cu.getSource().equals(buf.toString()));
		} finally {
			cu.discardWorkingCopy();
		}
	}

	protected OrganizeImportsOperation createOperation(ICompilationUnit cu, String[] order, boolean ignoreLowerCaseNames, boolean save, boolean allowSyntaxErrors, IChooseImportQuery chooseImportQuery) {
		setOrganizeImportSettings(order);
		return new OrganizeImportsOperation(cu, null, ignoreLowerCaseNames, save, allowSyntaxErrors, chooseImportQuery);
	}

	protected void setOrganizeImportSettings(String[] order) {
		Preferences prefs = JavaLanguageServerPlugin.getPreferencesManager().getPreferences();
		List<String> importOrder = new ArrayList<>();
		if (order != null) {
			importOrder.addAll(Arrays.asList(order));
		}
		prefs.setImportOrder(importOrder);
	}

}
