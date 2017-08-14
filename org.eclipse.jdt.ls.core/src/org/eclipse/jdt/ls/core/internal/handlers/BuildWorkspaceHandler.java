/*******************************************************************************
 * Copyright (c) 2017 Microsoft Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Microsoft Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.jdt.ls.core.internal.handlers;

import static org.eclipse.jdt.ls.core.internal.JavaLanguageServerPlugin.logException;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.eclipse.core.resources.IncrementalProjectBuilder;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.OperationCanceledException;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.compiler.IProblem;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.ls.core.internal.BuildWorkspaceResult;
import org.eclipse.jdt.ls.core.internal.BuildWorkspaceStatus;
import org.eclipse.jdt.ls.core.internal.SharedASTProvider;

/**
 * @author xuzho
 *
 */
public class BuildWorkspaceHandler {
	private SharedASTProvider sharedASTProvider;

	public BuildWorkspaceHandler() {
		this.sharedASTProvider = SharedASTProvider.getInstance();
	}

	public BuildWorkspaceResult buildWorkspace(IProgressMonitor monitor) {
		try {
			ResourcesPlugin.getWorkspace().build(IncrementalProjectBuilder.INCREMENTAL_BUILD, monitor);
			List<IProblem> errors = getBuildErrors(monitor);
			if (errors.isEmpty()) {
				return new BuildWorkspaceResult(BuildWorkspaceStatus.SUCCEED);
			} else {
				return new BuildWorkspaceResult(BuildWorkspaceStatus.FAILED);
			}
		} catch (CoreException e) {
			logException("Failed to build workspace.", e);
			return new BuildWorkspaceResult(BuildWorkspaceStatus.FAILED);
		} catch (OperationCanceledException e) {
			return new BuildWorkspaceResult(BuildWorkspaceStatus.CANCELLED);
		}
	}

	private List<IProblem> getBuildErrors(IProgressMonitor monitor) {
		this.sharedASTProvider.invalidateAll();
		List<IProblem> errors = new ArrayList<>();
		List<ICompilationUnit> toValidate = Arrays.asList(JavaCore.getWorkingCopies(null));
		List<CompilationUnit> astRoots = this.sharedASTProvider.getASTs(toValidate, monitor);
		for (CompilationUnit astRoot : astRoots) {
			for (IProblem problem : astRoot.getProblems()) {
				if (problem.isError()) {
					errors.add(problem);
				}
			}
		}
		return errors;
	}
}
