/*******************************************************************************
 * Copyright (c) 2000, 2024 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Copied from /org.eclipse.jdt.ui/src/org/eclipse/jdt/internal/ui/text/correction/ReorgCorrectionsSubProcessor.java
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *     Matt Chapman, mpchapman@gmail.com - 89977 Make JDT .java agnostic
 *******************************************************************************/
package org.eclipse.jdt.ls.core.internal.corrections.proposals;

import java.util.Collection;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.dom.IBinding;
import org.eclipse.jdt.core.manipulation.CUCorrectionProposalCore;
import org.eclipse.jdt.core.manipulation.ChangeCorrectionProposalCore;
import org.eclipse.jdt.core.manipulation.ICleanUpFixCore;
import org.eclipse.jdt.core.refactoring.CompilationUnitChange;
import org.eclipse.jdt.internal.core.manipulation.util.BasicElementLabels;
import org.eclipse.jdt.internal.corext.fix.IProposableFix;
import org.eclipse.jdt.internal.corext.fix.UnusedCodeFixCore;
import org.eclipse.jdt.internal.corext.refactoring.changes.RenameCompilationUnitChange;
import org.eclipse.jdt.internal.corext.util.Messages;
import org.eclipse.jdt.internal.ui.fix.UnusedCodeCleanUp;
import org.eclipse.jdt.ui.text.java.IInvocationContext;
import org.eclipse.jdt.ui.text.java.IProblemLocation;
import org.eclipse.jdt.internal.ui.text.correction.IProposalRelevance;
import org.eclipse.jdt.internal.ui.text.correction.ReorgCorrectionsBaseSubProcessor;
import org.eclipse.jdt.internal.ui.text.correction.UnresolvedElementsBaseSubProcessor;
import org.eclipse.jdt.internal.ui.text.correction.proposals.CorrectMainTypeNameProposalCore;
import org.eclipse.jdt.internal.ui.text.correction.proposals.CorrectPackageDeclarationProposalCore;
import org.eclipse.jdt.ls.core.internal.JavaLanguageServerPlugin;
import org.eclipse.jdt.ls.core.internal.corrections.CorrectionMessages;
import org.eclipse.jdt.ls.core.internal.corrections.ProposalKindWrapper;
import org.eclipse.jdt.ls.core.internal.handlers.CodeActionHandler;
import org.eclipse.lsp4j.CodeActionKind;
import org.eclipse.ltk.core.refactoring.Change;
import org.eclipse.ltk.core.refactoring.CompositeChange;


public class ReorgCorrectionsSubProcessor extends ReorgCorrectionsBaseSubProcessor<ProposalKindWrapper> {

	public static void getWrongTypeNameProposals(IInvocationContext context, IProblemLocation problem,
			Collection<ProposalKindWrapper> proposals) {
		new ReorgCorrectionsSubProcessor().addWrongTypeNameProposals(context, problem, proposals);
	}

	public static void getWrongPackageDeclNameProposals(IInvocationContext context, IProblemLocation problem,
			Collection<ProposalKindWrapper> proposals) throws CoreException {
		new ReorgCorrectionsSubProcessor().addWrongPackageDeclNameProposals(context, problem, proposals);
	}

	public static void removeImportStatementProposals(IInvocationContext context, IProblemLocation problem,
			Collection<ProposalKindWrapper> proposals) throws CoreException {
		new ReorgCorrectionsSubProcessor().addRemoveImportStatementProposals(context, problem, proposals);
	}

	/* (non-Javadoc)
	 * @see org.eclipse.jdt.internal.ui.text.correction.ReorgCorrectionsBaseSubProcessor#createRenameCUProposal(java.lang.String, org.eclipse.jdt.internal.corext.refactoring.changes.RenameCompilationUnitChange, int)
	 */
	@Override
	public ProposalKindWrapper createRenameCUProposal(String label, RenameCompilationUnitChange change, int relevance) {
		ChangeCorrectionProposalCore proposal = new ChangeCorrectionProposalCore(label, change, relevance);
		return CodeActionHandler.wrap(proposal, CodeActionKind.QuickFix);
	}

	/* (non-Javadoc)
	 * @see org.eclipse.jdt.internal.ui.text.correction.ReorgCorrectionsBaseSubProcessor#createCorrectMainTypeNameProposal(org.eclipse.jdt.core.ICompilationUnit, org.eclipse.jdt.ui.text.java.IInvocationContext, java.lang.String, java.lang.String, int)
	 */
	@Override
	public ProposalKindWrapper createCorrectMainTypeNameProposal(ICompilationUnit cu, IInvocationContext context, String currTypeName, String newTypeName, int relevance) {
		String title = Messages.format(CorrectionMessages.ReorgCorrectionsSubProcessor_renametype_description, BasicElementLabels.getJavaElementName(newTypeName));
		CorrectMainTypeNameProposalCore p = new CorrectMainTypeNameProposalCore(title, cu, null, context, currTypeName, newTypeName, relevance);
		return CodeActionHandler.wrap(p, CodeActionKind.QuickFix);
	}

	/* (non-Javadoc)
	 * @see org.eclipse.jdt.internal.ui.text.correction.ReorgCorrectionsBaseSubProcessor#createCorrectPackageDeclarationProposal(org.eclipse.jdt.core.ICompilationUnit, org.eclipse.jdt.internal.ui.text.correction.IProblemLocation, int)
	 */
	@Override
	protected ProposalKindWrapper createCorrectPackageDeclarationProposal(ICompilationUnit cu, IProblemLocation problem, int relevance) {
		return CodeActionHandler.wrap(new CorrectPackageDeclarationProposalCore(cu, problem, relevance), CodeActionKind.QuickFix);
	}

	/* (non-Javadoc)
	 * @see org.eclipse.jdt.internal.ui.text.correction.ReorgCorrectionsBaseSubProcessor#createMoveToNewPackageProposal(java.lang.String, org.eclipse.ltk.core.refactoring.CompositeChange, int)
	 */
	@Override
	protected ProposalKindWrapper createMoveToNewPackageProposal(String label, CompositeChange composite, int relevance) {
		ChangeCorrectionProposalCore p = new ChangeCorrectionProposalCore(label, composite, IProposalRelevance.MOVE_CU_TO_PACKAGE);
		return CodeActionHandler.wrap(p, CodeActionKind.QuickFix);
	}

	/* (non-Javadoc)
	 * @see org.eclipse.jdt.internal.ui.text.correction.ReorgCorrectionsBaseSubProcessor#createOrganizeImportsProposal(java.lang.String, org.eclipse.ltk.core.refactoring.Change, org.eclipse.jdt.core.ICompilationUnit, int)
	 */
	@Override
	protected ProposalKindWrapper createOrganizeImportsProposal(String name, Change change, ICompilationUnit cu, int relevance) {
		return null;
	}

	@Override
	public void addRemoveImportStatementProposals(IInvocationContext context, IProblemLocation problem, Collection<ProposalKindWrapper> proposals) {
		super.addRemoveImportStatementProposals(context, problem, proposals);
		ICleanUpFixCore removeAllUnusedImportsFix = UnusedCodeFixCore.createCleanUp(context.getASTRoot(), false, false, false, false, false, true, false, false);
		if (removeAllUnusedImportsFix != null) {
			try {
				CompilationUnitChange change = removeAllUnusedImportsFix.createChange(null);
				CUCorrectionProposalCore proposal = new CUCorrectionProposalCore(CorrectionMessages.ReorgCorrectionsSubProcessor_remove_all_unused_imports, change.getCompilationUnit(), change, IProposalRelevance.REMOVE_UNUSED_IMPORT);
				proposals.add(CodeActionHandler.wrap(proposal, CodeActionKind.QuickFix));
			} catch (CoreException ce) {
				JavaLanguageServerPlugin.log(ce);
			}
		}
	}

	/* (non-Javadoc)
	 * @see org.eclipse.jdt.internal.ui.text.correction.ReorgCorrectionsBaseSubProcessor#createRemoveUnusedImportProposal(org.eclipse.jdt.internal.corext.fix.IProposableFix, org.eclipse.jdt.internal.ui.fix.UnusedCodeCleanUp, int, org.eclipse.jdt.ui.text.java.IInvocationContext)
	 */
	@Override
	protected ProposalKindWrapper createRemoveUnusedImportProposal(IProposableFix fix, UnusedCodeCleanUp unusedCodeCleanUp, int relevance, IInvocationContext context) {
		if (fix != null) {
			try {
				CompilationUnitChange change = fix.createChange(null);
				CUCorrectionProposalCore proposal = new CUCorrectionProposalCore(change.getName(), change.getCompilationUnit(), change, relevance);
				return CodeActionHandler.wrap(proposal, CodeActionKind.QuickFix);
			} catch (CoreException e) {
				JavaLanguageServerPlugin.log(e);
			}
		}
		return null;
	}

	/* (non-Javadoc)
	 * @see org.eclipse.jdt.internal.ui.text.correction.ReorgCorrectionsBaseSubProcessor#createProjectSetupFixProposal(org.eclipse.jdt.ui.text.java.IInvocationContext, org.eclipse.jdt.internal.ui.text.correction.IProblemLocation, java.lang.String, java.util.Collection)
	 */
	@Override
	public ProposalKindWrapper createProjectSetupFixProposal(IInvocationContext context, IProblemLocation problem, String missingType, Collection<ProposalKindWrapper> proposals) {
		// Not yet implemented in jdt.ls,  jdt.ui impl is UI-based
		return null;
	}

	/* (non-Javadoc)
	 * @see org.eclipse.jdt.internal.ui.text.correction.ReorgCorrectionsBaseSubProcessor#getUnresolvedElementsSubProcessor()
	 */
	@Override
	public UnresolvedElementsBaseSubProcessor<ProposalKindWrapper> getUnresolvedElementsSubProcessor() {
		return new UnresolvedElementsSubProcessor();
	}

	/* (non-Javadoc)
	 * @see org.eclipse.jdt.internal.ui.text.correction.ReorgCorrectionsBaseSubProcessor#createChangeToRequiredCompilerComplianceProposal(java.lang.String, org.eclipse.jdt.core.IJavaProject, boolean, java.lang.String, int)
	 */
	@Override
	protected ProposalKindWrapper createChangeToRequiredCompilerComplianceProposal(String label1, IJavaProject project, boolean changeOnWorkspace, String requiredVersion, int relevance) {
		// Not yet implemented in jdt.ls,  jdt.ui impl is UI-based
		return null;
	}

	/* (non-Javadoc)
	 * @see org.eclipse.jdt.internal.ui.text.correction.ReorgCorrectionsBaseSubProcessor#createChangeToRequiredCompilerComplianceProposal(java.lang.String, org.eclipse.jdt.core.IJavaProject, boolean, java.lang.String, boolean, int)
	 */
	@Override
	protected ProposalKindWrapper createChangeToRequiredCompilerComplianceProposal(String label2, IJavaProject project, boolean changeOnWorkspace, String requiredVersion, boolean enablePreviews, int relevance) {
		// Not yet implemented in jdt.ls,  jdt.ui impl is UI-based
		return null;
	}

	/* (non-Javadoc)
	 * @see org.eclipse.jdt.internal.ui.text.correction.ReorgCorrectionsBaseSubProcessor#createOpenBuildPathCorrectionProposal(org.eclipse.core.resources.IProject, java.lang.String, int, org.eclipse.jdt.core.dom.IBinding)
	 */
	@Override
	protected ProposalKindWrapper createOpenBuildPathCorrectionProposal(IProject project, String label, int relevance, IBinding referencedElement) {
		// Not yet implemented in jdt.ls,  jdt.ui impl is UI-based
		return null;
	}

}
