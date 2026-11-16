/*******************************************************************************
 * Copyright (c) 2017-2018 Microsoft Corporation and others.
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Hashtable;
import java.util.List;
import java.util.Map;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.jdt.core.IClasspathAttribute;
import org.eclipse.jdt.core.IClasspathEntry;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IPackageFragment;
import org.eclipse.jdt.core.IPackageFragmentRoot;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.ls.core.internal.JDTUtils;
import org.eclipse.jdt.ls.core.internal.JavaProjectHelper;
import org.eclipse.jdt.ls.core.internal.TextEditUtil;
import org.eclipse.jdt.ls.core.internal.WorkspaceHelper;
import org.eclipse.jdt.ls.core.internal.correction.TestOptions;
import org.eclipse.jdt.ls.core.internal.managers.AbstractProjectsManagerBasedTest;
import org.eclipse.jdt.ls.core.internal.preferences.PreferenceManager;
import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.Document;
import org.eclipse.lsp4j.TextEdit;
import org.eclipse.lsp4j.WorkspaceEdit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class OrganizeImportsCommandTest extends AbstractProjectsManagerBasedTest {

	private IJavaProject fJProject1;

	private IPackageFragmentRoot fSourceFolder;

	private OrganizeImportsCommand command = new OrganizeImportsCommand();

	@BeforeEach
	public void setup() throws Exception {
		fJProject1 = newEmptyProject();
		Hashtable<String, String> options = TestOptions.getDefaultOptions();
		fJProject1.setOptions(options);
		fSourceFolder = fJProject1.getPackageFragmentRoot(fJProject1.getProject().getFolder("src"));
	}

	public void setupJava9() throws Exception {
		importExistingProjects("eclipse/java9");
		IProject project = WorkspaceHelper.getProject("java9");
		fJProject1 = JavaCore.create(project);
		fSourceFolder = fJProject1.getPackageFragmentRoot(fJProject1.getProject().getFolder("src/main/java"));
	}

	@Test
	void testGenericOrganizeImportsCall_InvalidFile() {
		assertThrows(CoreException.class, () -> {
			importProjects("eclipse/hello");
			OrganizeImportsCommand command = new OrganizeImportsCommand();
			command.organizeImports(Arrays.asList("no/such/file.java"));
		});
	}

	@Test
	public void testGenericOrganizeImportsCall_null() throws Exception {
		importProjects("eclipse/hello");
		OrganizeImportsCommand command = new OrganizeImportsCommand();
		Object o = command.organizeImports(null);
		assertNotNull(o);
	}

	@Test
	public void testGenericOrganizeImportsCall() throws Exception {
		importProjects("eclipse/hello");
		IProject project = WorkspaceHelper.getProject("hello");
		String filename = project.getFile("src/java/Foo4.java").getRawLocationURI().toString();

		OrganizeImportsCommand command = new OrganizeImportsCommand();
		Object result = command.organizeImports(Arrays.asList(filename));
		assertNotNull(result);
		assertTrue(result instanceof WorkspaceEdit);
		WorkspaceEdit ws = (WorkspaceEdit) result;
		assertFalse(ws.getChanges().isEmpty());
		TextEdit edit = ws.getChanges().values().stream().findFirst().get().get(0);
		assertEquals(0, edit.getRange().getStart().getLine());
		assertEquals(4, edit.getRange().getEnd().getLine());
	}

	@Test
	public void testOrganizeImportsModuleInfo() throws Exception {

		setupJava9();

		IPackageFragment pack1 = fSourceFolder.createPackageFragment("", false, null);

		StringBuilder buf = new StringBuilder();
		buf.append("import foo.bar.MyDriverAction;\n");
		buf.append("import java.sql.DriverAction;\n");
		buf.append("import java.sql.SQLException;\n");
		buf.append("\n");
		buf.append("module mymodule.nine {\n");
		buf.append("	requires java.sql;\n");
		buf.append("	exports foo.bar;\n");
		buf.append("	provides DriverAction with MyDriverAction;\n");
		buf.append("}\n");

		ICompilationUnit cu = pack1.createCompilationUnit("module-info.java", buf.toString(), false, null);

		buf = new StringBuilder();
		buf.append("import java.sql.DriverAction;\n");
		buf.append("\n");
		buf.append("import foo.bar.MyDriverAction;\n");
		buf.append("\n");
		buf.append("module mymodule.nine {\n");
		buf.append("	requires java.sql;\n");
		buf.append("	exports foo.bar;\n");
		buf.append("	provides DriverAction with MyDriverAction;\n");
		buf.append("}\n");

		WorkspaceEdit rootEdit = new WorkspaceEdit();
		command.organizeImportsInCompilationUnit(cu, rootEdit);
		assertEquals(buf.toString(), getOrganizeImportResult(cu, rootEdit));
	}

	@Test
	public void testOrganizeImportsAddsModuleRequirement() throws Exception {
		ICompilationUnit module = setupModularProject(false);
		IPackageFragment pack = fSourceFolder.createPackageFragment("demo", false, null);
		ICompilationUnit source = pack.createCompilationUnit("App.java", "package demo;\nclass App { One one; }\n", false, null);

		WorkspaceEdit edits = new WorkspaceEdit();
		command.organizeImportsInCompilationUnit(source, edits);

		assertEquals(2, edits.getChanges().size());
		assertTrue(getOrganizeImportResult(source, edits).contains("import dep.One;"));
		assertTrue(getOrganizeImportResult(module, edits).contains("requires dependency;"));
	}

	@Test
	public void testOrganizeImportsExistingImportAddsModuleRequirement() throws Exception {
		ICompilationUnit module = setupModularProject(false);
		IPackageFragment pack = fSourceFolder.createPackageFragment("demo", false, null);
		ICompilationUnit source = pack.createCompilationUnit("App.java", "package demo;\nimport dep.One;\nclass App { One one; }\n", false, null);

		WorkspaceEdit edits = new WorkspaceEdit();
		command.organizeImportsInCompilationUnit(source, edits);

		assertTrue(getOrganizeImportResult(module, edits).contains("requires dependency;"));
	}

	@Test
	public void testOrganizeImportsInPackageAddsModuleRequirement() throws Exception {
		ICompilationUnit module = setupModularProject(false);
		IPackageFragment pack = fSourceFolder.createPackageFragment("demo", false, null);
		pack.createCompilationUnit("App.java", "package demo;\nclass App { One one; }\n", false, null);

		WorkspaceEdit edits = command.organizeImportsInDirectory(pack.getResource().getLocationURI().toString(), fJProject1.getProject());

		assertTrue(getOrganizeImportResult(module, edits).contains("requires dependency;"));
	}

	@Test
	public void testOrganizeImportsInProjectCombinesModuleRequirements() throws Exception {
		ICompilationUnit module = setupModularProject(true);
		IPackageFragment pack = fSourceFolder.createPackageFragment("demo", false, null);
		ICompilationUnit first = pack.createCompilationUnit("First.java", "package demo;\nclass First { One one; }\n", false, null);
		ICompilationUnit second = pack.createCompilationUnit("Second.java", "package demo;\nclass Second { Two two; }\n", false, null);

		WorkspaceEdit edits = command.organizeImportsInProject(fJProject1.getProject());

		assertEquals(3, edits.getChanges().size());
		assertTrue(getOrganizeImportResult(first, edits).contains("import dep.One;"));
		assertTrue(getOrganizeImportResult(second, edits).contains("import alternative.Two;"));
		String moduleResult = getOrganizeImportResult(module, edits);
		assertTrue(moduleResult.contains("requires dependency;"));
		assertTrue(moduleResult.contains("requires alternative;"));
	}

	@Test
	public void testOrganizeImportsInProjectMergesSelectedModuleInfoEdits() throws Exception {
		ICompilationUnit module = setupModularProject(false);
		module.getBuffer().setContents("module demo { uses One; }\n");
		module.save(null, true);
		IPackageFragment pack = fSourceFolder.createPackageFragment("demo", false, null);
		pack.createCompilationUnit("App.java", "package demo;\nclass App { One one; }\n", false, null);

		WorkspaceEdit edits = command.organizeImportsInProject(fJProject1.getProject());

		String moduleResult = getOrganizeImportResult(module, edits);
		assertTrue(moduleResult.contains("import dep.One;"));
		assertTrue(moduleResult.contains("requires dependency;"));
	}

	@Test
	public void testOrganizeImportsUnused() throws CoreException, BadLocationException {

		IPackageFragment pack1 = fSourceFolder.createPackageFragment("test1", false, null);

		StringBuilder buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("\n");
		buf.append("import java.util.ArrayList;\n");
		buf.append("\n");
		buf.append("public class E {\n");
		buf.append("}\n");

		ICompilationUnit cu = pack1.createCompilationUnit("E.java", buf.toString(), false, null);

		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("\n");
		buf.append("public class E {\n");
		buf.append("}\n");

		WorkspaceEdit rootEdit = new WorkspaceEdit();
		command.organizeImportsInCompilationUnit(cu, rootEdit);
		assertEquals(buf.toString(), getOrganizeImportResult(cu, rootEdit));
	}

	@Test
	public void testOrganizeImportsSort() throws CoreException, BadLocationException {

		IPackageFragment pack1 = fSourceFolder.createPackageFragment("test1", false, null);

		StringBuilder buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("\n");
		buf.append("import java.util.HashMap;\n");
		buf.append("import java.util.ArrayList;\n");
		buf.append("\n");
		buf.append("public class E {\n");
		buf.append("\n");
		buf.append("    public E() {\n");
		buf.append("        ArrayList list = new ArrayList();\n");
		buf.append("        HashMap<String, String> map = new HashMap<String, String>();\n");
		buf.append("    }\n");
		buf.append("}\n");

		ICompilationUnit cu = pack1.createCompilationUnit("E.java", buf.toString(), false, null);

		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("\n");
		buf.append("import java.util.ArrayList;\n");
		buf.append("import java.util.HashMap;\n");
		buf.append("\n");
		buf.append("public class E {\n");
		buf.append("\n");
		buf.append("    public E() {\n");
		buf.append("        ArrayList list = new ArrayList();\n");
		buf.append("        HashMap<String, String> map = new HashMap<String, String>();\n");
		buf.append("    }\n");
		buf.append("}\n");
		WorkspaceEdit rootEdit = new WorkspaceEdit();
		command.organizeImportsInCompilationUnit(cu, rootEdit);
		assertEquals(buf.toString(), getOrganizeImportResult(cu, rootEdit));
	}

	@Test
	public void testOrganizeImportsAutomaticallyResolve() throws CoreException, BadLocationException {

		IPackageFragment pack1 = fSourceFolder.createPackageFragment("test1", false, null);

		StringBuilder buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("\n");
		buf.append("import java.util.ArrayList;\n");
		buf.append("\n");
		buf.append("public class E {\n");
		buf.append("\n");
		buf.append("    public E() {\n");
		buf.append("        ArrayList list = new ArrayList();\n");
		buf.append("        HashMap<String, String> map = new HashMap<String, String>();\n");
		buf.append("    }\n");
		buf.append("}\n");

		ICompilationUnit cu = pack1.createCompilationUnit("E.java", buf.toString(), false, null);

		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("\n");
		buf.append("import java.util.ArrayList;\n");
		buf.append("import java.util.HashMap;\n");
		buf.append("\n");
		buf.append("public class E {\n");
		buf.append("\n");
		buf.append("    public E() {\n");
		buf.append("        ArrayList list = new ArrayList();\n");
		buf.append("        HashMap<String, String> map = new HashMap<String, String>();\n");
		buf.append("    }\n");
		buf.append("}\n");

		WorkspaceEdit rootEdit = new WorkspaceEdit();
		command.organizeImportsInCompilationUnit(cu, rootEdit);
		assertEquals(buf.toString(), getOrganizeImportResult(cu, rootEdit));
	}

	@Test
	public void testOrganizeImportsFilterTypes() throws CoreException, BadLocationException {
		List<String> filteredTypes = new ArrayList<>();
		filteredTypes.add("java.util.*");
		PreferenceManager.getPrefs(null).setFilteredTypes(filteredTypes);
		try {
			IPackageFragment pack1 = fSourceFolder.createPackageFragment("test1", false, null);

			StringBuilder buf = new StringBuilder();
			buf.append("package test1;\n");
			buf.append("\n");
			buf.append("public class E {\n");
			buf.append("\n");
			buf.append("    public E() {\n");
			buf.append("        List list = new ArrayList();\n");
			buf.append("    }\n");
			buf.append("}\n");

			ICompilationUnit cu = pack1.createCompilationUnit("E.java", buf.toString(), false, null);

			buf = new StringBuilder();
			buf.append("package test1;\n");
			buf.append("\n");
			buf.append("import java.util.ArrayList;\n");
			buf.append("import java.util.List;\n");
			buf.append("\n");
			buf.append("public class E {\n");
			buf.append("\n");
			buf.append("    public E() {\n");
			buf.append("        List list = new ArrayList();\n");
			buf.append("    }\n");
			buf.append("}\n");

			WorkspaceEdit rootEdit = new WorkspaceEdit();
			command.organizeImportsInCompilationUnit(cu, rootEdit);
			assertEquals(0, rootEdit.getChanges().size());
			PreferenceManager.getPrefs(null).setFilteredTypes(Collections.emptyList());
			command.organizeImportsInCompilationUnit(cu, rootEdit);
			assertFalse(rootEdit.getChanges().isEmpty());
			assertEquals(buf.toString(), getOrganizeImportResult(cu, rootEdit));
		} finally {
			PreferenceManager.getPrefs(null).setFilteredTypes(Collections.emptyList());
		}
	}

	@Test
	public void testOrganizeImportsInPackage() throws CoreException, BadLocationException {

		IPackageFragment pack1 = fSourceFolder.createPackageFragment("test1", false, null);

		StringBuilder buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("\n");
		buf.append("import java.util.ArrayList;\n");
		buf.append("\n");
		buf.append("public class E {\n");
		buf.append("}\n");
		ICompilationUnit cu1 = pack1.createCompilationUnit("E.java", buf.toString(), false, null);

		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("\n");
		buf.append("import java.util.HashMap;\n");
		buf.append("import java.util.ArrayList;\n");
		buf.append("\n");
		buf.append("public class F {\n");
		buf.append("\n");
		buf.append("    public F() {\n");
		buf.append("        ArrayList list = new ArrayList();\n");
		buf.append("        HashMap<String, String> map = new HashMap<String, String>();\n");
		buf.append("    }\n");
		buf.append("}\n");

		ICompilationUnit cu2 = pack1.createCompilationUnit("F.java", buf.toString(), false, null);

		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("\n");
		buf.append("public class E {\n");
		buf.append("}\n");

		WorkspaceEdit rootEdit = new WorkspaceEdit();
		command.organizeImportsInPackageFragment(pack1, rootEdit);
		assertEquals(buf.toString(), getOrganizeImportResult(cu1, rootEdit));

		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("\n");
		buf.append("import java.util.ArrayList;\n");
		buf.append("import java.util.HashMap;\n");
		buf.append("\n");
		buf.append("public class F {\n");
		buf.append("\n");
		buf.append("    public F() {\n");
		buf.append("        ArrayList list = new ArrayList();\n");
		buf.append("        HashMap<String, String> map = new HashMap<String, String>();\n");
		buf.append("    }\n");
		buf.append("}\n");

		assertEquals(buf.toString(), getOrganizeImportResult(cu2, rootEdit));
	}

	@Test
	public void testOrganizeImportsInProject() throws CoreException, BadLocationException {

		IPackageFragment pack1 = fSourceFolder.createPackageFragment("test1", false, null);

		StringBuilder buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("\n");
		buf.append("import java.util.ArrayList;\n");
		buf.append("\n");
		buf.append("public class E {\n");
		buf.append("}\n");
		ICompilationUnit cu1 = pack1.createCompilationUnit("E.java", buf.toString(), false, null);

		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("\n");
		buf.append("import java.util.HashMap;\n");
		buf.append("import java.util.ArrayList;\n");
		buf.append("\n");
		buf.append("public class F {\n");
		buf.append("\n");
		buf.append("    public F() {\n");
		buf.append("        ArrayList list = new ArrayList();\n");
		buf.append("        HashMap<String, String> map = new HashMap<String, String>();\n");
		buf.append("    }\n");
		buf.append("}\n");

		ICompilationUnit cu2 = pack1.createCompilationUnit("F.java", buf.toString(), false, null);

		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("\n");
		buf.append("public class E {\n");
		buf.append("}\n");

		WorkspaceEdit rootEdit = command.organizeImportsInProject(pack1.getJavaProject().getProject());
		assertEquals(buf.toString(), getOrganizeImportResult(cu1, rootEdit));

		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("\n");
		buf.append("import java.util.ArrayList;\n");
		buf.append("import java.util.HashMap;\n");
		buf.append("\n");
		buf.append("public class F {\n");
		buf.append("\n");
		buf.append("    public F() {\n");
		buf.append("        ArrayList list = new ArrayList();\n");
		buf.append("        HashMap<String, String> map = new HashMap<String, String>();\n");
		buf.append("    }\n");
		buf.append("}\n");

		assertEquals(buf.toString(), getOrganizeImportResult(cu2, rootEdit));
	}

	@Test
	public void testOrganizeImportsOnDemandThreshold() throws Exception {
		int onDemandTreshold = preferenceManager.getPreferences().getImportOnDemandThreshold();
		try {
			preferenceManager.getPreferences().setImportOnDemandThreshold(2);
			IPackageFragment pack1 = fSourceFolder.createPackageFragment("test1", false, null);
			StringBuilder buf = new StringBuilder();
			buf.append("package test1;\n");
			buf.append("\n");
			buf.append("import java.util.HashMap;\n");
			buf.append("import java.util.ArrayList;\n");
			buf.append("\n");
			buf.append("public class E {\n");
			buf.append("\n");
			buf.append("    public E() {\n");
			buf.append("        ArrayList list = new ArrayList();\n");
			buf.append("        HashMap<String, String> map = new HashMap<String, String>();\n");
			buf.append("    }\n");
			buf.append("}\n");
			ICompilationUnit cu = pack1.createCompilationUnit("E.java", buf.toString(), false, null);
			buf = new StringBuilder();
			buf.append("package test1;\n");
			buf.append("\n");
			buf.append("import java.util.*;\n");
			buf.append("\n");
			buf.append("public class E {\n");
			buf.append("\n");
			buf.append("    public E() {\n");
			buf.append("        ArrayList list = new ArrayList();\n");
			buf.append("        HashMap<String, String> map = new HashMap<String, String>();\n");
			buf.append("    }\n");
			buf.append("}\n");
			WorkspaceEdit rootEdit = new WorkspaceEdit();
			command.organizeImportsInCompilationUnit(cu, rootEdit);
			assertEquals(buf.toString(), getOrganizeImportResult(cu, rootEdit));
		} finally {
			preferenceManager.getPreferences().setImportOnDemandThreshold(onDemandTreshold);
		}
	}

	@Test
	public void testOrganizeImportsStaticOnDemandThreshold() throws Exception {
		int staticOnDemandTreshold = preferenceManager.getPreferences().getStaticImportOnDemandThreshold();
		try {
			preferenceManager.getPreferences().setStaticImportOnDemandThreshold(2);
			IPackageFragment pack1 = fSourceFolder.createPackageFragment("test1", false, null);
			StringBuilder buf = new StringBuilder();
			buf.append("package test1;\n");
			buf.append("\n");
			buf.append("import static java.lang.Math.pow;\n");
			buf.append("import static java.lang.Math.sqrt;\n");
			buf.append("\n");
			buf.append("public class E {\n");
			buf.append("\n");
			buf.append("    public E() {\n");
			buf.append("        double d1 = sqrt(4);\n");
			buf.append("        double d2 = pow(2, 2);\n");
			buf.append("    }\n");
			buf.append("}\n");
			ICompilationUnit cu = pack1.createCompilationUnit("E.java", buf.toString(), false, null);
			buf = new StringBuilder();
			buf.append("package test1;\n");
			buf.append("\n");
			buf.append("import static java.lang.Math.*;\n");
			buf.append("\n");
			buf.append("public class E {\n");
			buf.append("\n");
			buf.append("    public E() {\n");
			buf.append("        double d1 = sqrt(4);\n");
			buf.append("        double d2 = pow(2, 2);\n");
			buf.append("    }\n");
			buf.append("}\n");
			WorkspaceEdit rootEdit = new WorkspaceEdit();
			command.organizeImportsInCompilationUnit(cu, rootEdit);
			assertEquals(buf.toString(), getOrganizeImportResult(cu, rootEdit));
		} finally {
			preferenceManager.getPreferences().setStaticImportOnDemandThreshold(staticOnDemandTreshold);
		}
	}

	private ICompilationUnit setupModularProject(boolean withAlternative) throws Exception {
		Map<String, String> options = fJProject1.getOptions(false);
		JavaCore.setComplianceOptions(JavaCore.VERSION_9, options);
		fJProject1.setOptions(options);
		addModuleProject("dependency", "dep", "One", options);
		if (withAlternative) {
			addModuleProject("alternative", "alternative", "Two", options);
		}
		return fSourceFolder.createPackageFragment("", false, null)
				.createCompilationUnit("module-info.java", "module demo {\n}\n", false, null);
	}

	private void addModuleProject(String moduleName, String packageName, String typeName, Map<String, String> options) throws Exception {
		IJavaProject dependency = newEmptyProject(moduleName);
		dependency.setOptions(options);
		IPackageFragmentRoot source = dependency.getPackageFragmentRoot(dependency.getProject().getFolder("src"));
		source.createPackageFragment("", false, null).createCompilationUnit("module-info.java",
				"module " + moduleName + " { exports " + packageName + "; }", false, null);
		source.createPackageFragment(packageName, false, null).createCompilationUnit(typeName + ".java",
				"package " + packageName + "; public class " + typeName + " {}", false, null);
		IClasspathAttribute[] attributes = { JavaCore.newClasspathAttribute(IClasspathAttribute.MODULE, "true") };
		IClasspathEntry entry = JavaCore.newProjectEntry(dependency.getPath(), null, false, attributes, false);
		JavaProjectHelper.addToClasspath(fJProject1, entry);
	}

	private String getOrganizeImportResult(ICompilationUnit cu, WorkspaceEdit we) throws BadLocationException, CoreException {
		List<TextEdit> change = we.getChanges().get(JDTUtils.toURI(cu));
		if (change == null) {
			return cu.getSource();
		}
		Document doc = new Document();
		doc.set(cu.getSource());

		return TextEditUtil.apply(doc, change);
	}
}
