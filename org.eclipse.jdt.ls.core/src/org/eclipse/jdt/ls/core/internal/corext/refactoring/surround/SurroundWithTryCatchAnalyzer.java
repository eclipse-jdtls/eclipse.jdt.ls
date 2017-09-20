/*******************************************************************************
 * Copyright (c) 2000, 2015 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Originally copied from org.eclipse.jdt.internal.corext.refactoring.surround.SurroundWithTryCatchAnalyzer
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.jdt.ls.core.internal.corext.refactoring.surround;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.MethodReference;
import org.eclipse.jdt.ls.core.internal.corext.dom.Selection;
import org.eclipse.jdt.ls.core.internal.corext.refactoring.RefactoringCoreMessages;

public class SurroundWithTryCatchAnalyzer extends SurroundWithAnalyzer {
	private ITypeBinding[] fExceptions;

	public SurroundWithTryCatchAnalyzer(ICompilationUnit unit, Selection selection) throws CoreException {
		super(unit, selection, true);
	}

	public ITypeBinding[] getExceptions() {
		return fExceptions;
	}

	@Override
	public void endVisit(CompilationUnit node) {
		ASTNode enclosingNode = null;
		if (!getStatus().hasFatalError() && hasSelectedNodes()) {
			enclosingNode= getEnclosingNode(getFirstSelectedNode());
		}

		super.endVisit(node);
		if (enclosingNode != null && !getStatus().hasFatalError()) {
			fExceptions = ExceptionAnalyzer.perform(enclosingNode, getSelection());
			if (fExceptions == null || fExceptions.length == 0) {
				if (enclosingNode instanceof MethodReference) {
					invalidSelection(RefactoringCoreMessages.SurroundWithTryCatchAnalyzer_doesNotContain);
				} else {
					fExceptions = new ITypeBinding[] { node.getAST().resolveWellKnownType("java.lang.Exception") }; //$NON-NLS-1$
				}
			}
		}
	}
}
