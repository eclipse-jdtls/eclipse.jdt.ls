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

package org.eclipse.jdt.ls.core.internal.corrections.proposals;

import java.util.Arrays;
import java.util.Collection;

import org.eclipse.jdt.core.IClassFile;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.Name;
import org.eclipse.jdt.internal.ui.text.correction.IProblemLocationCore;
import org.eclipse.jdt.ls.core.internal.corrections.IInvocationContext;
import org.eclipse.jdt.ls.core.internal.text.correction.CUCorrectionCommandProposal;
import org.eclipse.lsp4j.CodeActionKind;

public class CleanBuildSubProcessor {
    public static void cleanBuildForUnresolvedImportProposals(IInvocationContext context, IProblemLocationCore problem,
			Collection<ChangeCorrectionProposal> proposals) {
		final ICompilationUnit cu= context.getCompilationUnit();
		ASTNode coveringNode = problem.getCoveringNode(context.getASTRoot());
		if (coveringNode instanceof Name) {
			String fullyQualifiedName = ((Name) coveringNode).getFullyQualifiedName();
			try {
				IType type = cu.getJavaProject().findType(fullyQualifiedName);
				if (type != null) {
					ICompilationUnit compilationUnit = (ICompilationUnit) type.getAncestor(IJavaElement.COMPILATION_UNIT);
					IClassFile cf = (IClassFile) type.getAncestor(IJavaElement.CLASS_FILE);
					if (compilationUnit != null && cf == null) {
						proposals.add(new CUCorrectionCommandProposal("Execute 'clean build'", CodeActionKind.QuickFix, cu, IProposalRelevance.NO_SUGGESSTIONS_AVAILABLE, "java.workspace.compile", Arrays.asList(Boolean.TRUE)));
					}
				}
			} catch (JavaModelException e) {
				// do nothing
			}
		}
	}
}
