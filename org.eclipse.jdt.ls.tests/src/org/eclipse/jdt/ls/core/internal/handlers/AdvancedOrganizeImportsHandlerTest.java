/*******************************************************************************
 * Copyright (c) 2019 Microsoft Corporation and others.
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
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IImportDeclaration;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IPackageFragment;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.internal.corext.util.JavaModelUtil;
import org.eclipse.jdt.internal.corext.util.ValidateEditException;
import org.eclipse.jdt.ls.core.internal.JavaLanguageServerPlugin;
import org.eclipse.jdt.ls.core.internal.codemanipulation.AbstractSourceTestCase;
import org.eclipse.jdt.ls.core.internal.handlers.OrganizeImportsHandler.ImportCandidate;
import org.eclipse.jdt.ls.core.internal.handlers.OrganizeImportsHandler.ImportSelection;
import org.eclipse.jdt.ls.core.internal.managers.BuildSupportManager;
import org.eclipse.text.edits.TextEdit;
import org.junit.Test;

public class AdvancedOrganizeImportsHandlerTest extends AbstractSourceTestCase {

	@Test
	public void testChooseImport() throws ValidateEditException, CoreException, IOException {
		//@formatter:off
		IPackageFragment package1 = fRoot.createPackageFragment("p1", true, null);
		ICompilationUnit unit1 = package1.createCompilationUnit("C.java", "package p1;\r\n" +
				"\r\n" +
				"public class C {\r\n" +
				"}"
				, true, null);
		IPackageFragment package2 = fRoot.createPackageFragment("p2", true, null);
		ICompilationUnit unit2 = package2.createCompilationUnit("C.java", "package p2;\r\n" +
				"\r\n" +
				"public class C {\r\n" +
				"}"
				, true, null);
		ICompilationUnit unit = fPackageP.createCompilationUnit("B.java", "package p;\r\n" +
				"\r\n" +
				"public class B {\r\n" +
				"	C c;\r\n" +
				"}"
				, true, null);
		//@formatter:on

		TextEdit edit = OrganizeImportsHandler.organizeImports(unit, (selections) -> {
			assertEquals(1, selections.length);
			ImportSelection selection = selections[0];
			assertEquals(2, selection.candidates.length);
			assertEquals("p1.C", selection.candidates[0].fullyQualifiedName);
			assertEquals("p2.C", selection.candidates[1].fullyQualifiedName);
			assertEquals(3, selection.range.getStart().getLine());
			assertEquals(1, selection.range.getStart().getCharacter());
			assertEquals(3, selection.range.getEnd().getLine());
			assertEquals(2, selection.range.getEnd().getCharacter());
			return new ImportCandidate[] { selection.candidates[0] };
		});
		assertNotNull(edit);
		JavaModelUtil.applyEdit(unit, edit, true, null);

		/* @formatter:off */
		String expected = "package p;\r\n" +
				"\r\n" +
				"import p1.C;\r\n" +
				"\r\n" +
				"public class B {\r\n" +
				"	C c;\r\n" +
				"}";
		//@formatter:on
		compareSource(expected, unit.getSource());
	}

	@Test
	public void testStaticImports() throws ValidateEditException, CoreException, IOException {
		String[] favourites = JavaLanguageServerPlugin.getPreferencesManager().getPreferences().getJavaCompletionFavoriteMembers();
		try {
			List<String> list = new ArrayList<>();
			list.add("java.lang.Math.*");
			list.add("java.util.stream.Collectors.*");
			JavaLanguageServerPlugin.getPreferencesManager().getPreferences().setJavaCompletionFavoriteMembers(list);
			//@formatter:off
			IPackageFragment pack = fRoot.createPackageFragment("p1", true, null);
			ICompilationUnit unit = pack.createCompilationUnit("C.java",
					"package p1;\n" +
					"\n" +
					"public class C {\n" +
					"    List list = List.of(1).stream().collect(toList());\n" +
					"    double i = abs(-1);\n" +
					"    double pi = PI;\n" +
					"}\n"
					, true, null);
			//@formatter:on
			TextEdit edit = OrganizeImportsHandler.organizeImports(unit, (selections) -> {
				return new ImportCandidate[0];
			});
			assertNotNull(edit);
			JavaModelUtil.applyEdit(unit, edit, true, null);
			/* @formatter:off */
			String expected = "package p1;\n" +
					"\n" +
					"import static java.lang.Math.PI;\n" +
					"import static java.lang.Math.abs;\n" +
					"import static java.util.stream.Collectors.toList;\n" +
					"\n" +
					"import java.util.List;\n" +
					"\n" +
					"public class C {\n" +
					"    List list = List.of(1).stream().collect(toList());\n" +
					"    double i = abs(-1);\n" +
					"    double pi = PI;\n" +
					"}\n";
			//@formatter:on
			compareSource(expected, unit.getSource());
		} finally {
			JavaLanguageServerPlugin.getPreferencesManager().getPreferences().setJavaCompletionFavoriteMembers(Arrays.asList(favourites));
		}
	}

	@Test
	public void testAmbiguousStaticImports() throws Exception {
		importProjects("maven/salut4");
		IProject project = ResourcesPlugin.getWorkspace().getRoot().getProject("salut4");
		assertTrue(BuildSupportManager.find("Maven").get().applies(project));
		String[] favourites = JavaLanguageServerPlugin.getPreferencesManager().getPreferences().getJavaCompletionFavoriteMembers();
		try {
			List<String> list = new ArrayList<>();
			list.add("org.junit.jupiter.api.Assertions.*");
			list.add("org.junit.jupiter.api.Assumptions.*");
			list.add("org.junit.jupiter.api.DynamicContainer.*");
			list.add("org.junit.jupiter.api.DynamicTest.*");
			list.add("org.hamcrest.MatcherAssert.*");
			list.add("org.hamcrest.Matchers.*");
			list.add("org.mockito.Mockito.*");
			list.add("org.mockito.ArgumentMatchers.*");
			list.add("org.mockito.Answers.*");
			list.add("org.mockito.hamcrest.MockitoHamcrest.*");
			list.add("org.mockito.ArgumentMatchers.*");
			JavaLanguageServerPlugin.getPreferencesManager().getPreferences().setJavaCompletionFavoriteMembers(list);
			IJavaProject javaProject = JavaCore.create(project);
			IType type = javaProject.findType("org.sample.MyTest");
			ICompilationUnit unit = type.getCompilationUnit();
			TextEdit edit = OrganizeImportsHandler.organizeImports(unit, (selections) -> {
				return new ImportCandidate[0];
			});
			assertNotNull(edit);
			JavaModelUtil.applyEdit(unit, edit, true, null);
			/* @formatter:off */
			String expected = "package org.sample;\n" +
					"\n" +
					"import static org.hamcrest.MatcherAssert.assertThat;\n" +
					"import static org.hamcrest.Matchers.any;\n" +
					"import static org.junit.jupiter.api.Assertions.assertEquals;\n" +
					"\n" +
					"import org.junit.jupiter.api.Test;\n" +
					"\n" +
					"public class MyTest {\n" +
					"    @Test\n" +
					"    public void test() {\n" +
					"        assertEquals(1, 1, \"message\");\n" +
					"        assertThat(\"test\", true);\n" +
					"        any();\n" +
					"    }\n" +
					"}\n";
			//@formatter:on
			compareSource(expected, unit.getSource());
		} finally {
			JavaLanguageServerPlugin.getPreferencesManager().getPreferences().setJavaCompletionFavoriteMembers(Arrays.asList(favourites));
		}
	}

	// https://github.com/redhat-developer/vscode-java/issues/2012
	@Test
	public void testDuplicateStaticImports() throws Exception {
		importProjects("maven/salut6");
		IProject project = ResourcesPlugin.getWorkspace().getRoot().getProject("salut6");
		assertTrue(BuildSupportManager.find("Maven").get().applies(project));
		String[] favourites = JavaLanguageServerPlugin.getPreferencesManager().getPreferences().getJavaCompletionFavoriteMembers();
		try {
			List<String> list = new ArrayList<>();
			list.add("org.assertj.core.api.Assertions.*");
			JavaLanguageServerPlugin.getPreferencesManager().getPreferences().setJavaCompletionFavoriteMembers(list);
			IJavaProject javaProject = JavaCore.create(project);
			IType type = javaProject.findType("org.sample.MyTest");
			ICompilationUnit unit = type.getCompilationUnit();
			int importsCount = unit.getImports().length;
			assertEquals(2, importsCount);
			TextEdit edit = OrganizeImportsHandler.organizeImports(unit, (selections) -> {
				return new ImportCandidate[0];
			});
			assertNull(edit);
			type = javaProject.findType("org.sample.MyTest2");
			unit = type.getCompilationUnit();
			edit = OrganizeImportsHandler.organizeImports(unit, (selections) -> {
				return new ImportCandidate[0];
			});
			assertNotNull(edit);
			JavaModelUtil.applyEdit(unit, edit, true, null);
			IImportDeclaration[] imports = unit.getImports();
			assertEquals(2, imports.length);
			Optional<IImportDeclaration> el = Stream.of(imports).filter(p -> p.getElementName().equals("org.hamcrest.MatcherAssert.assertThat")).findFirst();
			assertNotNull(el.get());
		} finally {
			JavaLanguageServerPlugin.getPreferencesManager().getPreferences().setJavaCompletionFavoriteMembers(Arrays.asList(favourites));
		}
	}

	// https://github.com/redhat-developer/vscode-java/issues/2861
	@Test
	public void testRemoveStaticImports() throws Exception {
		importProjects("maven/salut4");
		IProject project = ResourcesPlugin.getWorkspace().getRoot().getProject("salut4");
		assertTrue(BuildSupportManager.find("Maven").get().applies(project));
		int staticImportOnDemandThreshold = JavaLanguageServerPlugin.getPreferencesManager().getPreferences().getStaticImportOnDemandThreshold();
		String[] favourites = JavaLanguageServerPlugin.getPreferencesManager().getPreferences().getJavaCompletionFavoriteMembers();
		try {
			JavaLanguageServerPlugin.getPreferencesManager().getPreferences().setStaticImportOnDemandThreshold(1);
			List<String> list = new ArrayList<>();
			list.add("org.sample.Test2.*");
			JavaLanguageServerPlugin.getPreferencesManager().getPreferences().setJavaCompletionFavoriteMembers(list);
			IJavaProject javaProject = JavaCore.create(project);
			IType type = javaProject.findType("org.sample.Test1");
			ICompilationUnit unit = type.getCompilationUnit();
			int importsCount = unit.getImports().length;
			assertEquals(2, importsCount);
			TextEdit edit = OrganizeImportsHandler.organizeImports(unit, (selections) -> {
				return new ImportCandidate[0];
			});
			assertNull(edit);
		} finally {
			JavaLanguageServerPlugin.getPreferencesManager().getPreferences().setStaticImportOnDemandThreshold(staticImportOnDemandThreshold);
			JavaLanguageServerPlugin.getPreferencesManager().getPreferences().setJavaCompletionFavoriteMembers(Arrays.asList(favourites));
		}
	}
}
