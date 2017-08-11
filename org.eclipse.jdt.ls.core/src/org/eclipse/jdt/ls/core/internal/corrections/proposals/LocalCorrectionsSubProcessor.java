/*******************************************************************************
 * Copyright (c) 2000, 2017 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Copied from org.eclipse.jdt.internal.ui.text.correction.LocalCorrectionsSubProcessor;
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *     Renaud Waldura &lt;renaud+eclipse@waldura.com&gt; - Access to static proposal
 *     Benjamin Muskalla <bmuskalla@innoopract.com> - [quick fix] Shouldn't offer "Add throws declaration" quickfix for overriding signature if result would conflict with overridden signature
 *     Lukas Hanke <hanke@yatta.de> - Bug 241696 [quick fix] quickfix to iterate over a collection - https://bugs.eclipse.org/bugs/show_bug.cgi?id=241696
 *     Sandra Lions <sandra.lions-piron@oracle.com> - [quick fix] for qualified enum constants in switch-case labels - https://bugs.eclipse.org/bugs/90140
 *     Stephan Herrmann - Contribution for Bug 463360 - [override method][null] generating method override should not create redundant null annotations
 *******************************************************************************/
package org.eclipse.jdt.ls.core.internal.corrections.proposals;

import java.util.Collection;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.jdt.core.compiler.IProblem;
import org.eclipse.jdt.core.refactoring.CompilationUnitChange;
import org.eclipse.jdt.ls.core.internal.JavaLanguageServerPlugin;
import org.eclipse.jdt.ls.core.internal.corext.fix.IProposableFix;
import org.eclipse.jdt.ls.core.internal.corext.fix.UnimplementedCodeFix;
import org.eclipse.jdt.ls.core.internal.corext.fix.UnusedCodeFix;
import org.eclipse.jdt.ls.core.internal.corrections.IInvocationContext;
import org.eclipse.jdt.ls.core.internal.corrections.IProblemLocation;

public class LocalCorrectionsSubProcessor {

	public static void addUnimplementedMethodsProposals(IInvocationContext context, IProblemLocation problem, Collection<CUCorrectionProposal> proposals) {
		IProposableFix fix = UnimplementedCodeFix.createAddUnimplementedMethodsFix(context.getASTRoot(), problem);

		if (fix != null) {
			try {
				CompilationUnitChange change = fix.createChange(null);
				CUCorrectionProposal proposal = new CUCorrectionProposal(change.getName(), change.getCompilationUnit(), change, IProposalRelevance.ADD_UNIMPLEMENTED_METHODS);
				proposals.add(proposal);
			} catch (CoreException e) {
				JavaLanguageServerPlugin.log(e);
			}
		}
	}

	public static void addUnusedMemberProposal(IInvocationContext context, IProblemLocation problem, Collection<CUCorrectionProposal> proposals) {
		int problemId = problem.getProblemId();

		UnusedCodeFix fix = UnusedCodeFix.createUnusedMemberFix(context.getASTRoot(), problem, false);
		if (fix != null) {
			try {
				CompilationUnitChange change = fix.createChange(null);
				CUCorrectionProposal proposal = new CUCorrectionProposal(change.getName(), change.getCompilationUnit(), change, IProposalRelevance.UNUSED_MEMBER);
				proposals.add(proposal);
			} catch (CoreException e) {
				JavaLanguageServerPlugin.log(e);
			}
		}

		if (problemId == IProblem.UnusedPrivateField) {
			GetterSetterCorrectionSubProcessor.addGetterSetterProposal(context, problem, proposals);
		}
	}
}
