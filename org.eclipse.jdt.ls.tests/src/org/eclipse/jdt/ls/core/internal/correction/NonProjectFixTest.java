/*******************************************************************************
 * Copyright (c) 2020 Microsoft Corporation and others.
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

package org.eclipse.jdt.ls.core.internal.correction;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.List;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.jdt.core.IBuffer;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IPackageFragment;
import org.eclipse.jdt.core.IPackageFragmentRoot;
import org.eclipse.jdt.core.IProblemRequestor;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.WorkingCopyOwner;
import org.eclipse.jdt.core.compiler.IProblem;
import org.eclipse.jdt.ls.core.internal.DocumentAdapter;
import org.eclipse.jdt.ls.core.internal.JDTUtils;
import org.eclipse.jdt.ls.core.internal.JavaClientConnection;
import org.eclipse.jdt.ls.core.internal.JavaLanguageServerPlugin;
import org.eclipse.jdt.ls.core.internal.handlers.CodeActionHandler;
import org.eclipse.jdt.ls.core.internal.handlers.DiagnosticsHandler;
import org.eclipse.jdt.ls.core.internal.managers.AbstractProjectsManagerBasedTest;
import org.eclipse.jdt.ls.core.internal.preferences.ClientPreferences;
import org.eclipse.jdt.ls.core.internal.preferences.Preferences;
import org.eclipse.jdt.ls.core.internal.text.correction.ActionMessages;
import org.eclipse.lsp4j.CodeAction;
import org.eclipse.lsp4j.CodeActionContext;
import org.eclipse.lsp4j.CodeActionKind;
import org.eclipse.lsp4j.CodeActionParams;
import org.eclipse.lsp4j.Command;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.runners.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class NonProjectFixTest extends AbstractProjectsManagerBasedTest {
	private JavaClientConnection javaClient;
	@Mock
	private ClientPreferences clientPreferences;

	@Before
	public void setup() throws Exception {
		mockPreferences();
		javaClient = new JavaClientConnection(client);
		JavaLanguageServerPlugin.getNonProjectDiagnosticsState().setGlobalErrorLevel(true);
	}

	@After
	public void tearDown() throws Exception {
		JavaLanguageServerPlugin.getNonProjectDiagnosticsState().setGlobalErrorLevel(true);
		javaClient.disconnect();
		for (ICompilationUnit cu : JavaCore.getWorkingCopies(null)) {
			cu.discardWorkingCopy();
		}
	}

	private Preferences mockPreferences() {
		Preferences mockPreferences = Mockito.mock(Preferences.class);
		Mockito.when(preferenceManager.getPreferences()).thenReturn(mockPreferences);
		Mockito.when(preferenceManager.getPreferences(Mockito.any())).thenReturn(mockPreferences);
		when(this.preferenceManager.getClientPreferences()).thenReturn(clientPreferences);
		when(clientPreferences.isSupportedCodeActionKind(CodeActionKind.QuickFix)).thenReturn(true);
		return mockPreferences;
	}

	private List<Either<Command, CodeAction>> getCodeActions(ICompilationUnit cu, IProblem problem) throws JavaModelException {		
		CodeActionParams parms = new CodeActionParams();
		Range range = JDTUtils.toRange(cu, problem.getSourceStart(), 0);

		TextDocumentIdentifier textDocument = new TextDocumentIdentifier();
		textDocument.setUri(JDTUtils.toURI(cu));
		parms.setTextDocument(textDocument);
		parms.setRange(range);
		CodeActionContext context = new CodeActionContext();
		context.setDiagnostics(DiagnosticsHandler.toDiagnosticsArray(cu, Arrays.asList(problem), true));
		context.setOnly(Arrays.asList(CodeActionKind.QuickFix));
		parms.setContext(context);

		return new CodeActionHandler(this.preferenceManager).getCodeActionCommands(parms, new NullProgressMonitor());
	}

	@Test
	public void testReportAllErrorsFixForNonProjectFile() throws Exception {
		IJavaProject javaProject = newDefaultProject();
		IPackageFragmentRoot sourceFolder = javaProject.getPackageFragmentRoot(javaProject.getProject().getFolder("src"));
		IPackageFragment pack1 = sourceFolder.createPackageFragment("java", false, null);

		// @formatter:off
		String standaloneFileContent =
				"package java;\n"+
				"public class Foo extends UnknownType {\n"+
				"	public void method1(){\n"+
				"		super.whatever()\n"+
				"	}\n"+
				"}";
		// @formatter:on

		ICompilationUnit cu1 = pack1.createCompilationUnit("Foo.java", standaloneFileContent, false, null);
		final DiagnosticsHandler handler = new DiagnosticsHandler(javaClient, cu1);
		WorkingCopyOwner wcOwner = createWorkingCopyOwner(cu1, handler);
		cu1.becomeWorkingCopy(null);
		try {
			cu1.reconcile(ICompilationUnit.NO_AST, true, wcOwner, null);
			List<IProblem> problems = handler.getProblems();
			assertFalse(problems.isEmpty());

			List<Either<Command, CodeAction>> actions = getCodeActions(cu1, problems.get(0));
			assertEquals(2, actions.size());
			CodeAction action = actions.get(0).getRight();
			assertEquals(CodeActionKind.QuickFix, action.getKind());
			assertEquals(ActionMessages.ReportAllErrorsForThisFile, action.getCommand().getTitle());
			assertEquals(3, action.getCommand().getArguments().size());
			assertEquals("thisFile", action.getCommand().getArguments().get(1));
			assertEquals(false, action.getCommand().getArguments().get(2));
	
			action = actions.get(1).getRight();
			assertEquals(CodeActionKind.QuickFix, action.getKind());
			assertEquals(ActionMessages.ReportAllErrorsForAnyNonProjectFile, action.getCommand().getTitle());
			assertEquals(3, action.getCommand().getArguments().size());
			assertEquals("anyNonProjectFile", action.getCommand().getArguments().get(1));
			assertEquals(false, action.getCommand().getArguments().get(2));
		} finally {
			cu1.discardWorkingCopy();
		}
	}

	private WorkingCopyOwner createWorkingCopyOwner(ICompilationUnit cu, DiagnosticsHandler handler) {
		return new WorkingCopyOwner() {

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
	}

	@Test
	public void testReportSyntaxErrorsFixForNonProjectFile() throws Exception {
		JavaLanguageServerPlugin.getNonProjectDiagnosticsState().setGlobalErrorLevel(false);
		IJavaProject javaProject = newDefaultProject();
		IPackageFragmentRoot sourceFolder = javaProject.getPackageFragmentRoot(javaProject.getProject().getFolder("src"));
		IPackageFragment pack1 = sourceFolder.createPackageFragment("java", false, null);

		// @formatter:off
		String standaloneFileContent =
				"package java;\n"+
				"public class Foo extends UnknownType {\n"+
				"	public void method1(){\n"+
				"		super.whatever()\n"+
				"	}\n"+
				"}";
		// @formatter:on

		ICompilationUnit cu1 = pack1.createCompilationUnit("Foo.java", standaloneFileContent, false, null);
		final DiagnosticsHandler handler = new DiagnosticsHandler(javaClient, cu1);
		WorkingCopyOwner wcOwner = createWorkingCopyOwner(cu1, handler);
		cu1.becomeWorkingCopy(null);
		try {
			cu1.reconcile(ICompilationUnit.NO_AST, true, wcOwner, null);
			List<IProblem> problems = handler.getProblems();
			assertFalse(problems.isEmpty());

			List<Either<Command, CodeAction>> actions = getCodeActions(cu1, problems.get(0));
			assertEquals(2, actions.size());
			CodeAction action = actions.get(0).getRight();
			assertEquals(CodeActionKind.QuickFix, action.getKind());
			assertEquals(ActionMessages.ReportSyntaxErrorsForThisFile, action.getCommand().getTitle());
			assertEquals(3, action.getCommand().getArguments().size());
			assertEquals("thisFile", action.getCommand().getArguments().get(1));
			assertEquals(true, action.getCommand().getArguments().get(2));
	
			action = actions.get(1).getRight();
			assertEquals(CodeActionKind.QuickFix, action.getKind());
			assertEquals(ActionMessages.ReportSyntaxErrorsForAnyNonProjectFile, action.getCommand().getTitle());
			assertEquals(3, action.getCommand().getArguments().size());
			assertEquals("anyNonProjectFile", action.getCommand().getArguments().get(1));
			assertEquals(true, action.getCommand().getArguments().get(2));
		} finally {
			cu1.discardWorkingCopy();
		}
	}
}
