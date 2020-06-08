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

package org.eclipse.jdt.ls.core.internal.syntaxserver;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.jdt.core.CompletionRequestor;
import org.eclipse.jdt.core.IBuffer;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaModelStatusConstants;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.WorkingCopyOwner;
import org.eclipse.jdt.internal.codeassist.CompletionEngine;
import org.eclipse.jdt.internal.core.CompilationUnit;
import org.eclipse.jdt.internal.core.JavaModelStatus;
import org.eclipse.jdt.internal.core.JavaProject;

public class ModelBasedCompletionEngine {

	public static void codeComplete(ICompilationUnit cu, int position, CompletionRequestor requestor, WorkingCopyOwner owner, IProgressMonitor monitor) throws JavaModelException {
		if (!(cu instanceof CompilationUnit)) {
			return;
		}

		if (requestor == null) {
			throw new IllegalArgumentException("Completion requestor cannot be null"); //$NON-NLS-1$
		}

		IBuffer buffer = cu.getBuffer();
		if (buffer == null) {
			return;
		}

		if (position < -1 || position > buffer.getLength()) {
			throw new JavaModelException(new JavaModelStatus(IJavaModelStatusConstants.INDEX_OUT_OF_BOUNDS));
		}

		JavaProject project = (JavaProject) cu.getJavaProject();
		ModelBasedSearchableEnvironment environment = new ModelBasedSearchableEnvironment(project, owner, requestor.isTestCodeExcluded());
		environment.setUnitToSkip((CompilationUnit) cu);

		// code complete
		CompletionEngine engine = new CompletionEngine(environment, requestor, project.getOptions(true), project, owner, monitor);
		engine.complete((CompilationUnit) cu, position, 0, cu);
	}
}
