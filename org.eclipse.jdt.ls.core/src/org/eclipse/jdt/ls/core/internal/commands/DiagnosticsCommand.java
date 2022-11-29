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

package org.eclipse.jdt.ls.core.internal.commands;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IWorkspaceRunnable;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.jdt.core.IBuffer;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IProblemRequestor;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.WorkingCopyOwner;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.manipulation.CoreASTProvider;
import org.eclipse.jdt.ls.core.internal.DiagnosticsState;
import org.eclipse.jdt.ls.core.internal.DocumentAdapter;
import org.eclipse.jdt.ls.core.internal.JDTUtils;
import org.eclipse.jdt.ls.core.internal.JavaClientConnection;
import org.eclipse.jdt.ls.core.internal.JavaLanguageServerPlugin;
import org.eclipse.jdt.ls.core.internal.handlers.DiagnosticsHandler;

public class DiagnosticsCommand {

	public static Object refreshDiagnostics(String uri, String scope, boolean syntaxOnly) {
		DiagnosticsState state = JavaLanguageServerPlugin.getNonProjectDiagnosticsState();
		boolean refreshAll = false;
		if (Objects.equals(scope, "thisFile")) {
			state.setErrorLevel(uri, syntaxOnly);
		} else if (Objects.equals(scope, "anyNonProjectFile")) {
			state.setGlobalErrorLevel(syntaxOnly);
			refreshAll = true;
		}

		ICompilationUnit target = refreshAll ? null : JDTUtils.resolveCompilationUnit(uri);
		refreshDiagnostics(target);

		return null;
	}

	private static void refreshDiagnostics(final ICompilationUnit target) {
		final JavaClientConnection connection = JavaLanguageServerPlugin.getInstance().getClientConnection();
		if (connection == null) {
			JavaLanguageServerPlugin.logError("The client connection doesn't exist.");
			return;
		}

		try {
			ResourcesPlugin.getWorkspace().run(new IWorkspaceRunnable() {
				@Override
				public void run(IProgressMonitor monitor) throws CoreException {
					List<ICompilationUnit> units = getNonProjectCompilationUnits(target, monitor);
					for (ICompilationUnit unit : units) {
						publishDiagnostics(connection, unit, monitor);
					}
				}
			}, new NullProgressMonitor());
		} catch (CoreException e) {
			JavaLanguageServerPlugin.logException("Refresh Diagnostics for non-project Java files", e);
		}
	}

	private static List<ICompilationUnit> getNonProjectCompilationUnits(ICompilationUnit target, IProgressMonitor monitor) {
		List<ICompilationUnit> candidates = new ArrayList<>();
		CoreASTProvider sharedASTProvider = CoreASTProvider.getInstance();
		if (target == null) {
			List<ICompilationUnit> workingCopies = Arrays.asList(JavaCore.getWorkingCopies(null));
			for (ICompilationUnit wc : workingCopies) {
				if (JDTUtils.isDefaultProject(wc) || !JDTUtils.isOnClassPath(wc)) {
					candidates.add(wc);
				}
			}
		} else {
			CompilationUnit unit = sharedASTProvider.getAST(target, CoreASTProvider.WAIT_YES, monitor);
			candidates.add((ICompilationUnit) unit.getTypeRoot());
		}

		return candidates;
	}

	private static void publishDiagnostics(JavaClientConnection connection, ICompilationUnit unit, IProgressMonitor monitor) throws JavaModelException {
		final DiagnosticsHandler handler = new DiagnosticsHandler(connection, unit);
		WorkingCopyOwner wcOwner = new WorkingCopyOwner() {
			@Override
			public IBuffer createBuffer(ICompilationUnit workingCopy) {
				ICompilationUnit original = workingCopy.getPrimary();
				IResource resource = original.getResource();
				if (resource instanceof IFile file) {
					return new DocumentAdapter(workingCopy, file);
				}
				return DocumentAdapter.Null;
			}

			@Override
			public IProblemRequestor getProblemRequestor(ICompilationUnit workingCopy) {
				return handler;
			}

		};
		int flags = ICompilationUnit.FORCE_PROBLEM_DETECTION | ICompilationUnit.ENABLE_BINDINGS_RECOVERY | ICompilationUnit.ENABLE_STATEMENTS_RECOVERY;
		unit.reconcile(ICompilationUnit.NO_AST, flags, wcOwner, monitor);
	}
}
