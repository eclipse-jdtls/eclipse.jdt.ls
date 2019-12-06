/*******************************************************************************
 * Copyright (c) 2017 Red Hat Inc. and others.
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
package org.eclipse.jdt.ls.core.internal.refactoring;

import java.util.Hashtable;

import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IPackageFragment;
import org.eclipse.jdt.core.IPackageFragmentRoot;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.ls.core.internal.correction.AbstractSelectionTest;
import org.eclipse.jdt.ls.core.internal.correction.TestOptions;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.junit.Before;
import org.junit.Test;

public class ConvertForLoopTest extends AbstractSelectionTest {

	private IJavaProject fJProject;
	private IPackageFragmentRoot fSourceFolder;

	@Before
	public void setup() throws Exception {
		fJProject = newEmptyProject();
		Hashtable<String, String> options = TestOptions.getDefaultOptions();
		options.put(JavaCore.COMPILER_PB_MISSING_OVERRIDE_ANNOTATION, JavaCore.WARNING);
		fJProject.setOptions(options);
		fSourceFolder = fJProject.getPackageFragmentRoot(fJProject.getProject().getFolder("src"));
	}

	@Test
	public void testConvertForLoop() throws Exception {
		IPackageFragment pack1 = fSourceFolder.createPackageFragment("test1", false, null);
		StringBuilder builder = new StringBuilder();
		builder.append("package test1;\n");
		builder.append("import java.util.List;\n");
		builder.append("public class E {\n");
		builder.append("    void foo(List<String> collection) {\n");
		builder.append("    	for (int i=0;i<collection.size();i++) {\n");
		builder.append("    		System.out.println(collection.get(i));\n");
		builder.append("    	}\n");
		builder.append("    }\n");
		builder.append("}\n");
		ICompilationUnit cu = pack1.createCompilationUnit("E.java", builder.toString(), false, null);
		builder = new StringBuilder();
		builder.append("package test1;\n");
		builder.append("import java.util.List;\n");
		builder.append("public class E {\n");
		builder.append("    void foo(List<String> collection) {\n");
		builder.append("    	for (String element : collection) {\n");
		builder.append("    		System.out.println(element);\n");
		builder.append("    	}\n");
		builder.append("    }\n");
		builder.append("}\n");
		Expected e = new Expected("Convert to enhanced 'for' loop", builder.toString());
		Range range = new Range(new Position(4, 5), new Position(4, 5));
		assertCodeActions(cu, range, e);
	}
}
