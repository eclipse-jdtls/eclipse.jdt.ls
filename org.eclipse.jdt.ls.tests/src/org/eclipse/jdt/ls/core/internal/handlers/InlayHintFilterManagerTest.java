/*******************************************************************************
 * Copyright (c) 2022 Microsoft Corporation and others.
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

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;

import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IMethod;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.ClassInstanceCreation;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.manipulation.CoreASTProvider;
import org.junit.Before;
import org.junit.Test;

public class InlayHintFilterManagerTest extends AbstractCompilationUnitBasedTest {

	@Before
	public void setUp() {
		InlayHintFilterManager.instance().reset();
	}

	@Test
	public void testMethodSimpleMatch() throws JavaModelException {
		preferences.setInlayHintsExclusionList(Arrays.asList("*.print(*)"));
		ICompilationUnit unit = getWorkingCopy(
			"src/Foo.java",
			"public class Foo {\n" +
			"	void print(int i) {}\n" +
			"	void output() {}\n" +
			"}\n"
		);
		IType primaryType = unit.findPrimaryType();
		for (IMethod method : primaryType.getMethods()) {
			if ("print".equals(method.getElementName())) {
				assertTrue(InlayHintFilterManager.instance().match(method));
			} else if ("output".equals(method.getElementName())) {
				assertFalse(InlayHintFilterManager.instance().match(method));
			}
		}
	}

	@Test
	public void testMethodSimpleMatch2() throws JavaModelException {
		preferences.setInlayHintsExclusionList(Arrays.asList("*.Arrays.asList"));
		ICompilationUnit unit = getWorkingCopy(
			"src/Foo.java",
			"import java.util.Arrays;\n" +
			"public class Foo {\n" +
			"	void foo() {\n" +
			"		Arrays.asList(1, 2);\n" +
			"	}\n" +
			"}\n"
		);
		CompilationUnit root = CoreASTProvider.getInstance().getAST(unit, CoreASTProvider.WAIT_YES, monitor);
		root.accept(new ASTVisitor() {
			@Override
			public boolean visit(MethodInvocation node) {
				IMethod method = (IMethod) node.resolveMethodBinding().getJavaElement();
				assertTrue(InlayHintFilterManager.instance().match(method));
				return true;
			}
		});
	}

	@Test
	public void testMethodStartMatch() throws JavaModelException {
		preferences.setInlayHintsExclusionList(Arrays.asList("*.*print*(*)"));
		ICompilationUnit unit = getWorkingCopy(
			"src/Foo.java",
			"public class Foo {\n" +
			"	void print(int i) {}\n" +
			"	void println(String s) {}\n" +
			"	void sprint(String s) {}\n" +
			"}\n"
		);
		IType primaryType = unit.findPrimaryType();
		for (IMethod method : primaryType.getMethods()) {
			if (method.getElementName().contains("print")) {
				assertTrue(InlayHintFilterManager.instance().match(method));
			}
		}
	}

	@Test
	public void testMethodParameterMatch() throws JavaModelException {
		preferences.setInlayHintsExclusionList(Arrays.asList("*.foo(*,*)"));
		ICompilationUnit unit = getWorkingCopy(
			"src/Foo.java",
			"public class Foo {\n" +
			"	void foo(String s1) {}\n" +
			"	void foo(String s1, String s2) {}\n" +
			"	void foo(String s1, String s2, String s3) {}\n" +
			"}\n"
		);
		IType primaryType = unit.findPrimaryType();
		for (IMethod method : primaryType.getMethods()) {
			if (method.getParameters().length == 2) {
				assertTrue(InlayHintFilterManager.instance().match(method));
			} else {
				assertFalse(InlayHintFilterManager.instance().match(method));
			}
		}
	}

	@Test
	public void testMethodParameterMatch2() throws JavaModelException {
		preferences.setInlayHintsExclusionList(Arrays.asList("(from*, to*)"));
		ICompilationUnit unit = getWorkingCopy(
			"src/Foo.java",
			"public class Foo {\n" +
			"	void foo(String from) {}\n" +
			"	void foo(String from, String to) {}\n" +
			"	void foo(String fromStart, String toEnd) {}\n" +
			"}\n"
		);
		IType primaryType = unit.findPrimaryType();
		for (IMethod method : primaryType.getMethods()) {
			if (method.getParameters().length == 2) {
				assertTrue(InlayHintFilterManager.instance().match(method));
			} else {
				assertFalse(InlayHintFilterManager.instance().match(method));
			}
		}
	}

	@Test
	public void testConstructorMatch() throws JavaModelException {
		preferences.setInlayHintsExclusionList(Arrays.asList("*Foo"));
		ICompilationUnit unit = getWorkingCopy(
			"src/Foo.java",
			"public class Foo {\n" +
			"	public Foo(int foo) {}\n" +
			"	public Foo(int foo, int bar) {}\n" +
			"}\n"
		);
		IType primaryType = unit.findPrimaryType();
		for (IMethod method : primaryType.getMethods()) {
			if (method.isConstructor()) {
				assertTrue(InlayHintFilterManager.instance().match(method));
			}
		}
	}

	@Test
	public void testConstructorMatch2() throws JavaModelException {
		preferences.setInlayHintsExclusionList(Arrays.asList("java.foo.Foo.*"));
		ICompilationUnit unit = getWorkingCopy(
			"src/java/foo/Foo.java",
			"public class Foo {\n" +
			"	public Foo(int foo) {}\n" +
			"	public Foo(int foo, int bar) {}\n" +
			"	public bar() {\n" +
			"		new Foo(1);\n" +
			"		new Foo(1, 2);\n" +
			"	}\n" +
			"}\n"
		);
		CompilationUnit root = CoreASTProvider.getInstance().getAST(unit, CoreASTProvider.WAIT_YES, monitor);
		root.accept(new ASTVisitor() {
			@Override
			public boolean visit(ClassInstanceCreation node) {
				IMethod method = (IMethod) node.resolveConstructorBinding().getJavaElement();
				assertTrue(InlayHintFilterManager.instance().match(method));
				return true;
			}
		});
	}
}
