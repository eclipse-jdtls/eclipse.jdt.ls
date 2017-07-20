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

package org.eclipse.jdt.ls.core.internal.corext.refactoring.rename;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IMethod;
import org.eclipse.jdt.core.IType;
import org.eclipse.lsp4j.WorkspaceEdit;
import org.eclipse.text.edits.ReplaceEdit;
import org.eclipse.text.edits.TextEdit;

public class RenameTypeProcessor extends RenameProcessor {

	public RenameTypeProcessor(IJavaElement selectedElement) {
		super(selectedElement);
	}

	@Override
	public void renameOccurrences(WorkspaceEdit edit, String newName, IProgressMonitor monitor) throws CoreException {
		super.renameOccurrences(edit, newName, monitor);

		IType t = (IType) fElement;
		IMethod[] methods = t.getMethods();

		for (IMethod method : methods) {
			if (method.isConstructor()) {
				TextEdit replaceEdit = new ReplaceEdit(method.getNameRange().getOffset(), method.getNameRange().getLength(), newName);
				convert(edit, t.getCompilationUnit(), replaceEdit);
			}
		}
	}
}
