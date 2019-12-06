/*******************************************************************************
 * Copyright (c) 2019 Red Hat Inc. and others.
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
package org.eclipse.jdt.ls.core.internal.correction;

import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IPackageFragment;
import org.eclipse.jdt.core.IPackageFragmentRoot;
import org.eclipse.jdt.core.compiler.IProblem;
import org.junit.Before;
import org.junit.Test;

/**
 * @author Nikolas Komonen - nkomonen@redhat.com
 *
 */
public class VisibilityQuickFixTest extends AbstractQuickFixTest {

	private IJavaProject fJProject1;
	private IPackageFragmentRoot fSourceFolder;

	@Before
	public void setup() throws Exception {
		fJProject1 = newEmptyProject();

		fSourceFolder = fJProject1.getPackageFragmentRoot(fJProject1.getProject().getFolder("src"));
	}

	@Test
	public void testMethodReducesVisibility() throws Exception {
		int problem = IProblem.MethodReducesVisibility;
		IPackageFragment pack1 = fSourceFolder.createPackageFragment("test1", false, null);

		StringBuilder bufB = new StringBuilder();
		bufB.append("package test1;\n");
		bufB.append("public class B {\n");
		bufB.append("    public void foo() {\n");
		bufB.append("    }\n");
		bufB.append("}\n");
		pack1.createCompilationUnit("B.java", bufB.toString(), false, null);

		StringBuilder buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("import B;\n");
		buf.append("public class A extends B {\n");
		buf.append("    private void foo() {\n");
		buf.append("    }\n");
		buf.append("}\n");
		ICompilationUnit cu = pack1.createCompilationUnit("A.java", buf.toString(), false, null);

		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("import B;\n");
		buf.append("public class A extends B {\n");
		buf.append("    public void foo() {\n");
		buf.append("    }\n");
		buf.append("}\n");

		Expected e1 = new Expected("Change visibility of 'A.foo' to 'public'", buf.toString());

		assertCodeActions(cu, e1);

	}

	@Test
	public void testInheritedMethodReducesVisibilityAndOverridingNonVisibleMethod() throws Exception {
		int problem = IProblem.InheritedMethodReducesVisibility;
		int problem2 = IProblem.OverridingNonVisibleMethod;
		// The code action fixes both of these diagnostic issues

		IPackageFragment pack1 = fSourceFolder.createPackageFragment("p", false, null);
		StringBuilder buf = new StringBuilder();
		buf.append("package p;	\n");
		buf.append("public abstract class X {	\n");
		buf.append("	abstract void foo();	\n");
		buf.append("	public interface I {	\n");
		buf.append("		void foo();	\n");
		buf.append("	}	\n");
		buf.append("}");
		pack1.createCompilationUnit("X.java", buf.toString(), false, null);

		buf = new StringBuilder();
		buf.append("package p;	\n");
		buf.append("public abstract class X {	\n");
		buf.append("	protected abstract void foo();	\n");
		buf.append("	public interface I {	\n");
		buf.append("		void foo();	\n");
		buf.append("	}	\n");
		buf.append("}");

		IPackageFragment pack2 = fSourceFolder.createPackageFragment("q", false, null);

		StringBuilder buf2 = new StringBuilder();
		buf2.append("package q;	\n");
		buf2.append("public abstract class Y extends p.X {	\n");
		buf2.append("	void foo(){}	\n");
		buf2.append("}	\n");
		buf2.append("class Z extends Y implements p.X.I {	\n");
		buf2.append("}");
		ICompilationUnit cu = pack2.createCompilationUnit("Y.java", buf2.toString(), false, null);

		Expected e3 = new Expected("Change visibility of 'X.foo' to 'protected'", buf.toString());
		assertCodeActions(cu, e3);
	}
}
