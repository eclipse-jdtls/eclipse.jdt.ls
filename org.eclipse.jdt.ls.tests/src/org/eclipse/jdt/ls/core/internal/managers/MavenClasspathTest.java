/*******************************************************************************
 * Copyright (c) 2018 Red Hat Inc. and others.
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
package org.eclipse.jdt.ls.core.internal.managers;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.eclipse.core.resources.IProject;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IProblemRequestor;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.WorkingCopyOwner;
import org.eclipse.jdt.core.manipulation.CoreASTProvider;
import org.eclipse.jdt.ls.core.internal.JDTUtils;
import org.eclipse.jdt.ls.core.internal.JavaClientConnection;
import org.eclipse.jdt.ls.core.internal.handlers.DiagnosticsHandler;
import org.eclipse.jdt.ls.core.internal.handlers.DocumentLifeCycleHandler;
import org.eclipse.lsp4j.DidOpenTextDocumentParams;
import org.eclipse.lsp4j.TextDocumentItem;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class MavenClasspathTest extends AbstractMavenBasedTest {

	private CoreASTProvider sharedASTProvider;

	private DocumentLifeCycleHandler lifeCycleHandler;
	private JavaClientConnection javaClient;

	@BeforeEach
	public void setup() throws Exception {
		sharedASTProvider = CoreASTProvider.getInstance();
		sharedASTProvider.disposeAST();
		//		sharedASTProvider.clearASTCreationCount();
		javaClient = new JavaClientConnection(client);
		lifeCycleHandler = new DocumentLifeCycleHandler(javaClient, preferenceManager, projectsManager, false);
	}

	@AfterEach
	public void tearDown() throws Exception {
		sharedASTProvider.disposeAST();
		for (ICompilationUnit cu : JavaCore.getWorkingCopies(null)) {
			cu.discardWorkingCopy();
		}
	}

	@Test
	public void testMain() throws Exception {
		IProject project = importMavenProject("classpathtest");
		IJavaProject javaProject = JavaCore.create(project);
		IType type = javaProject.findType("main.App");
		ICompilationUnit cu = type.getCompilationUnit();
		openDocument(cu, cu.getSource(), 1);
		final DiagnosticsHandler handler = new DiagnosticsHandler(javaClient, cu);
		WorkingCopyOwner wcOwner = getWorkingCopy(handler);
		cu.reconcile(ICompilationUnit.NO_AST, true, wcOwner, null);
		assertEquals(1, handler.getProblems().size(), "There aren't any problems");
	}

	@Test
	public void testTest() throws Exception {
		IProject project = importMavenProject("classpathtest");
		IJavaProject javaProject = JavaCore.create(project);
		IType type = javaProject.findType("test.AppTest");
		ICompilationUnit cu = type.getCompilationUnit();
		openDocument(cu, cu.getSource(), 1);
		final DiagnosticsHandler handler = new DiagnosticsHandler(javaClient, cu);
		WorkingCopyOwner wcOwner = getWorkingCopy(handler);
		cu.reconcile(ICompilationUnit.NO_AST, true, wcOwner, null);
		assertEquals(0, handler.getProblems().size(), "There is a problem");
	}

	@Test
	public void typemismatchTest() throws Exception {
		IProject project = importMavenProject("typemismatch");
		IJavaProject javaProject = JavaCore.create(project);
		IType type = javaProject.findType("test.Test");
		ICompilationUnit cu = type.getCompilationUnit();
		openDocument(cu, cu.getSource(), 1);
		final DiagnosticsHandler handler = new DiagnosticsHandler(javaClient, cu);
		WorkingCopyOwner wcOwner = getWorkingCopy(handler);
		cu.reconcile(ICompilationUnit.NO_AST, true, wcOwner, null);
		long size = handler.getProblems().stream().filter(p -> p.isError()).count();
		assertEquals(0, size, "There is an error");
		String source = cu.getSource();
		source = source.replace("Test {", "Test { ");
		cu.getBuffer().setContents(source);
		cu.reconcile(ICompilationUnit.NO_AST, true, wcOwner, null);
		size = handler.getProblems().stream().filter(p -> p.isError()).count();
		assertEquals(0, size, "There is an error");
		source = source.replace("Test { ", "Test {  ");
		cu.getBuffer().setContents(source);
		cu.reconcile(ICompilationUnit.NO_AST, true, wcOwner, null);
		size = handler.getProblems().stream().filter(p -> p.isError()).count();
		assertEquals(0, size, "There is an error");
		source = source.replace("Test {  ", "Test {   ");
		cu.getBuffer().setContents(source);
		cu.reconcile(ICompilationUnit.NO_AST, true, wcOwner, null);
		size = handler.getProblems().stream().filter(p -> p.isError()).count();
		assertEquals(0, size, "There is an error");
		source = source.replace("Test {   ", "Test {");
		cu.getBuffer().setContents(source);
		cu.reconcile(ICompilationUnit.NO_AST, true, wcOwner, null);
		size = handler.getProblems().stream().filter(p -> p.isError()).count();
		assertEquals(0, size, "There is an error");
	}

	private void openDocument(ICompilationUnit cu, String content, int version) {
		DidOpenTextDocumentParams openParms = new DidOpenTextDocumentParams();
		TextDocumentItem textDocument = new TextDocumentItem();
		textDocument.setLanguageId("java");
		textDocument.setText(content);
		textDocument.setUri(JDTUtils.toURI(cu));
		textDocument.setVersion(version);
		openParms.setTextDocument(textDocument);
		lifeCycleHandler.didOpen(openParms);
	}

	private WorkingCopyOwner getWorkingCopy(IProblemRequestor handler) {
		return new WorkingCopyOwner() {

			@Override
			public IProblemRequestor getProblemRequestor(ICompilationUnit workingCopy) {
				return handler;
			}
		};
	}

}
