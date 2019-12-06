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
package org.eclipse.jdt.ls.core.internal.handlers;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

import java.util.Arrays;
import java.util.Hashtable;
import java.util.List;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IResource;
import org.eclipse.jdt.core.IBuffer;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IPackageFragment;
import org.eclipse.jdt.core.IPackageFragmentRoot;
import org.eclipse.jdt.core.IProblemRequestor;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.WorkingCopyOwner;
import org.eclipse.jdt.core.compiler.IProblem;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.manipulation.CoreASTProvider;
import org.eclipse.jdt.ls.core.internal.DocumentAdapter;
import org.eclipse.jdt.ls.core.internal.JavaClientConnection;
import org.eclipse.jdt.ls.core.internal.managers.AbstractProjectsManagerBasedTest;
import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.DiagnosticSeverity;
import org.eclipse.lsp4j.DiagnosticTag;
import org.eclipse.lsp4j.Range;
import org.junit.Before;
import org.junit.Test;

public class DiagnosticHandlerTest extends AbstractProjectsManagerBasedTest {

	private JavaClientConnection javaClient;

	@Before
	public void setup() throws Exception {
		CoreASTProvider sharedASTProvider = CoreASTProvider.getInstance();
		sharedASTProvider.disposeAST();
		//		sharedASTProvider.clearASTCreationCount();
		javaClient = new JavaClientConnection(client);
	}
	@Test
	public void testMultipleLineRange() throws Exception {
		IJavaProject javaProject = newEmptyProject();
		Hashtable<String, String> options = JavaCore.getOptions();
		options.put(JavaCore.COMPILER_PB_DEAD_CODE, JavaCore.WARNING);
		javaProject.setOptions(options);
		IPackageFragmentRoot sourceFolder = javaProject.getPackageFragmentRoot(javaProject.getProject().getFolder("src"));
		IPackageFragment pack1 = sourceFolder.createPackageFragment("test1", false, null);

		StringBuilder buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("public class E {\n");
		buf.append("    public boolean foo(boolean b1) {\n");
		buf.append("        if (false) {\n");
		buf.append("            return true;\n");
		buf.append("        }\n");
		buf.append("        return false;\n");
		buf.append("    }\n");
		buf.append("}\n");
		ICompilationUnit cu = pack1.createCompilationUnit("E.java", buf.toString(), false, null);

		CompilationUnit astRoot = CoreASTProvider.getInstance().getAST(cu, CoreASTProvider.WAIT_YES, monitor);
		IProblem[] problems = astRoot.getProblems();
		List<Diagnostic> diagnostics = DiagnosticsHandler.toDiagnosticsArray(cu, Arrays.asList(problems), true);
		assertEquals(1, diagnostics.size());
		Range range = diagnostics.get(0).getRange();
		assertNotEquals(range.getStart().getLine(), range.getEnd().getLine());
	}

	@Test
	public void testTask() throws Exception {
		IJavaProject javaProject = newEmptyProject();
		IPackageFragmentRoot sourceFolder = javaProject.getPackageFragmentRoot(javaProject.getProject().getFolder("src"));
		IPackageFragment pack1 = sourceFolder.createPackageFragment("test1", false, null);

		StringBuilder buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("public class E {\n");
		buf.append("    // TODO task\n");
		buf.append("}\n");
		ICompilationUnit cu = pack1.createCompilationUnit("E.java", buf.toString(), false, null);

		final DiagnosticsHandler handler = new DiagnosticsHandler(javaClient, cu);
		WorkingCopyOwner wcOwner = new WorkingCopyOwner() {

			/* (non-Javadoc)
			 * @see org.eclipse.jdt.core.WorkingCopyOwner#createBuffer(org.eclipse.jdt.core.ICompilationUnit)
			 */
			@Override
			public IBuffer createBuffer(ICompilationUnit workingCopy) {
				ICompilationUnit original = workingCopy.getPrimary();
				IResource resource = original.getResource();
				if (resource instanceof IFile) {
					return new DocumentAdapter(workingCopy, (IFile) resource);
				}
				return DocumentAdapter.Null;
			}

			/* (non-Javadoc)
			 * @see org.eclipse.jdt.core.WorkingCopyOwner#getProblemRequestor(org.eclipse.jdt.core.ICompilationUnit)
			 */
			@Override
			public IProblemRequestor getProblemRequestor(ICompilationUnit workingCopy) {
				return handler;
			}

		};
		cu.becomeWorkingCopy(null);
		try {
			cu.reconcile(ICompilationUnit.NO_AST, true, wcOwner, null);
			List<IProblem> problems = handler.getProblems();
			assertEquals(problems.size(), 1);
			List<Diagnostic> diagnostics = DiagnosticsHandler.toDiagnosticsArray(cu, problems, true);
			assertEquals(diagnostics.size(), 1);
			DiagnosticSeverity severity = diagnostics.get(0).getSeverity();
			assertEquals(severity, DiagnosticSeverity.Information);
		} finally {
			cu.discardWorkingCopy();
		}
	}

	@Test
	public void testNotUsed() throws Exception {
		IJavaProject javaProject = newEmptyProject();
		IPackageFragmentRoot sourceFolder = javaProject.getPackageFragmentRoot(javaProject.getProject().getFolder("src"));
		IPackageFragment pack1 = sourceFolder.createPackageFragment("test1", false, null);

		StringBuilder buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("public class E {\n");
		buf.append("    private int i;\n");
		buf.append("}\n");
		ICompilationUnit cu = pack1.createCompilationUnit("E.java", buf.toString(), false, null);

		final DiagnosticsHandler handler = new DiagnosticsHandler(javaClient, cu);
		WorkingCopyOwner wcOwner = new WorkingCopyOwner() {

			/* (non-Javadoc)
			 * @see org.eclipse.jdt.core.WorkingCopyOwner#createBuffer(org.eclipse.jdt.core.ICompilationUnit)
			 */
			@Override
			public IBuffer createBuffer(ICompilationUnit workingCopy) {
				ICompilationUnit original = workingCopy.getPrimary();
				IResource resource = original.getResource();
				if (resource instanceof IFile) {
					return new DocumentAdapter(workingCopy, (IFile) resource);
				}
				return DocumentAdapter.Null;
			}

			/* (non-Javadoc)
			 * @see org.eclipse.jdt.core.WorkingCopyOwner#getProblemRequestor(org.eclipse.jdt.core.ICompilationUnit)
			 */
			@Override
			public IProblemRequestor getProblemRequestor(ICompilationUnit workingCopy) {
				return handler;
			}

		};
		cu.becomeWorkingCopy(null);
		try {
			cu.reconcile(ICompilationUnit.NO_AST, true, wcOwner, null);
			List<IProblem> problems = handler.getProblems();
			assertEquals(problems.size(), 1);
			List<Diagnostic> diagnostics = DiagnosticsHandler.toDiagnosticsArray(cu, problems, true);
			assertEquals(diagnostics.size(), 1);
			DiagnosticSeverity severity = diagnostics.get(0).getSeverity();
			assertEquals(severity, DiagnosticSeverity.Warning);
		} finally {
			cu.discardWorkingCopy();
		}
	}

	@Test
	public void testDeprecated() throws Exception {
		IJavaProject javaProject = newEmptyProject();
		IPackageFragmentRoot sourceFolder = javaProject.getPackageFragmentRoot(javaProject.getProject().getFolder("src"));
		IPackageFragment pack1 = sourceFolder.createPackageFragment("test1", false, null);

		StringBuilder buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("import java.security.Certificate;\n");
		ICompilationUnit cu = pack1.createCompilationUnit("E.java", buf.toString(), false, null);

		CompilationUnit astRoot = CoreASTProvider.getInstance().getAST(cu, CoreASTProvider.WAIT_YES, monitor);
		IProblem[] problems = astRoot.getProblems();
		List<Diagnostic> diagnostics = DiagnosticsHandler.toDiagnosticsArray(cu, Arrays.asList(problems), true);
		assertEquals(2, diagnostics.size());
		List<DiagnosticTag> tags = diagnostics.get(0).getTags();
		assertEquals(1, tags.size());
		assertEquals(DiagnosticTag.Deprecated, tags.get(0));
	}

	@Test
	public void testUnnecessary() throws Exception {
		IJavaProject javaProject = newEmptyProject();
		IPackageFragmentRoot sourceFolder = javaProject.getPackageFragmentRoot(javaProject.getProject().getFolder("src"));
		IPackageFragment pack1 = sourceFolder.createPackageFragment("test1", false, null);

		StringBuilder buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("import java.security.*;\n");
		ICompilationUnit cu = pack1.createCompilationUnit("E.java", buf.toString(), false, null);

		CompilationUnit asRoot = CoreASTProvider.getInstance().getAST(cu, CoreASTProvider.WAIT_YES, monitor);
		IProblem[] problems = asRoot.getProblems();
		List<Diagnostic> diagnostics = DiagnosticsHandler.toDiagnosticsArray(cu, Arrays.asList(problems), true);
		assertEquals(1, diagnostics.size());
		List<DiagnosticTag> tags = diagnostics.get(0).getTags();
		assertEquals(1, tags.size());
		assertEquals(DiagnosticTag.Unnecessary, tags.get(0));
	}

}
