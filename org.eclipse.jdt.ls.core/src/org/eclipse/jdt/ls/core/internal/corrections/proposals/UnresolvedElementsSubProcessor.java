/*******************************************************************************
 * Copyright (c) 2000, 2024 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Copied from /org.eclipse.jdt.ui/src/org/eclipse/jdt/internal/ui/text/correction/UnresolvedElementsSubProcessor.java
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *     Renaud Waldura &lt;renaud+eclipse@waldura.com&gt; - New class/interface with wizard
 *     Rabea Gransberger <rgransberger@gmx.de> - [quick fix] Fix several visibility issues - https://bugs.eclipse.org/394692
 *******************************************************************************/
package org.eclipse.jdt.ls.core.internal.corrections.proposals;

import java.util.Collection;
import java.util.List;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.compiler.IProblem;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.IVariableBinding;
import org.eclipse.jdt.core.dom.Name;
import org.eclipse.jdt.core.dom.NodeFinder;
import org.eclipse.jdt.core.manipulation.ChangeCorrectionProposalCore;
import org.eclipse.jdt.core.manipulation.TypeKinds;
import org.eclipse.jdt.internal.ui.text.correction.ReorgCorrectionsBaseSubProcessor;
import org.eclipse.jdt.internal.ui.text.correction.TypeMismatchBaseSubProcessor;
import org.eclipse.jdt.internal.ui.text.correction.UnresolvedElementsBaseSubProcessor;
import org.eclipse.jdt.internal.ui.text.correction.proposals.AddArgumentCorrectionProposalCore;
import org.eclipse.jdt.internal.ui.text.correction.proposals.AddModuleRequiresCorrectionProposalCore;
import org.eclipse.jdt.internal.ui.text.correction.proposals.AddTypeParameterProposalCore;
import org.eclipse.jdt.internal.ui.text.correction.proposals.CastCorrectionProposalCore;
import org.eclipse.jdt.internal.ui.text.correction.proposals.ChangeMethodSignatureProposalCore;
import org.eclipse.jdt.internal.ui.text.correction.proposals.LinkedCorrectionProposalCore;
import org.eclipse.jdt.internal.ui.text.correction.proposals.NewAnnotationMemberProposalCore;
import org.eclipse.jdt.internal.ui.text.correction.proposals.NewMethodCorrectionProposalCore;
import org.eclipse.jdt.internal.ui.text.correction.proposals.NewVariableCorrectionProposalCore;
import org.eclipse.jdt.internal.ui.text.correction.proposals.QualifyTypeProposalCore;
import org.eclipse.jdt.internal.ui.text.correction.proposals.RenameNodeCorrectionProposalCore;
import org.eclipse.jdt.internal.ui.text.correction.proposals.ReplaceCorrectionProposalCore;
import org.eclipse.jdt.ls.core.internal.JDTUtils;
import org.eclipse.jdt.ls.core.internal.JavaLanguageServerPlugin;
import org.eclipse.jdt.ls.core.internal.corrections.ProposalKindWrapper;
import org.eclipse.jdt.ls.core.internal.handlers.CodeActionHandler;
import org.eclipse.jdt.ui.text.java.IInvocationContext;
import org.eclipse.jdt.ui.text.java.IProblemLocation;
import org.eclipse.jdt.ui.text.java.correction.ASTRewriteCorrectionProposalCore;
import org.eclipse.lsp4j.CodeActionKind;


public class UnresolvedElementsSubProcessor extends UnresolvedElementsBaseSubProcessor<ProposalKindWrapper> {

	public static void getVariableProposals(IInvocationContext context, IProblemLocation problem,
			IVariableBinding resolvedField, Collection<ProposalKindWrapper> proposals) throws CoreException {
		new UnresolvedElementsSubProcessor().collectVariableProposals(context, problem, resolvedField, proposals);
	}

	public static void getTypeProposals(IInvocationContext context, IProblemLocation problem,
			Collection<ProposalKindWrapper> proposals) throws CoreException {
		new UnresolvedElementsSubProcessor().collectTypeProposals(context, problem, proposals);
	}

	public static void addNewTypeProposals(ICompilationUnit cu, Name refNode, int kind, int relevance,
			Collection<ProposalKindWrapper> proposals) throws CoreException {
		new UnresolvedElementsSubProcessor().collectNewTypeProposals(cu, refNode, kind, relevance, null);
	}

	public static void getMethodProposals(IInvocationContext context, IProblemLocation problem,
			boolean isOnlyParameterMismatch, Collection<ProposalKindWrapper> proposals) throws CoreException {
		new UnresolvedElementsSubProcessor().collectMethodProposals(context, problem, isOnlyParameterMismatch, proposals);
	}

	public static void getConstructorProposals(IInvocationContext context, IProblemLocation problem,
			Collection<ProposalKindWrapper> proposals) throws CoreException {
		new UnresolvedElementsSubProcessor().collectConstructorProposals(context, problem, proposals);
	}

	public static void getAmbiguousTypeReferenceProposals(IInvocationContext context, IProblemLocation problem,
			Collection<ProposalKindWrapper> proposals) throws CoreException {
		new UnresolvedElementsSubProcessor().collectAmbiguosTypeReferenceProposals(context, problem, proposals);
	}

	public static void getArrayAccessProposals(IInvocationContext context, IProblemLocation problem,
			Collection<ProposalKindWrapper> proposals) {
		new UnresolvedElementsSubProcessor().collectArrayAccessProposals(context, problem, proposals);
	}

	public static void getAnnotationMemberProposals(IInvocationContext context, IProblemLocation problem,
			Collection<ProposalKindWrapper> proposals) throws CoreException {
		new UnresolvedElementsSubProcessor().collectAnnotationMemberProposals(context, problem, proposals);
	}

	/* (non-Javadoc)
	 * @see org.eclipse.jdt.internal.ui.text.correction.UnresolvedElementsBaseSubProcessor#addNewTypeProposalsInteractiveInnerLoop(org.eclipse.jdt.core.ICompilationUnit, org.eclipse.jdt.core.dom.Name, org.eclipse.jdt.core.IJavaElement, int, int, org.eclipse.jdt.core.dom.Name, java.util.Collection)
	 */
	@Override
	protected void addNewTypeProposalsInteractiveInnerLoop(ICompilationUnit cu, Name node, IJavaElement enclosing, int rel, int kind, Name refNode, Collection<ProposalKindWrapper> proposals) throws CoreException {
		if ((kind & TypeKinds.CLASSES) != 0) {
			NewCUProposal proposal = new NewCUProposal(cu, node, NewCUProposal.K_CLASS, enclosing, rel + 3);
			proposals.add(CodeActionHandler.wrap(proposal, CodeActionKind.QuickFix));
		}
		if ((kind & TypeKinds.INTERFACES) != 0) {
			NewCUProposal proposal = new NewCUProposal(cu, node, NewCUProposal.K_INTERFACE, enclosing, rel + 3);
			proposals.add(CodeActionHandler.wrap(proposal, CodeActionKind.QuickFix));
		}
		if ((kind & TypeKinds.ENUMS) != 0) {
			NewCUProposal proposal = new NewCUProposal(cu, node, NewCUProposal.K_ENUM, enclosing, rel + 3);
			proposals.add(CodeActionHandler.wrap(proposal, CodeActionKind.QuickFix));
		}
		if ((kind & TypeKinds.ANNOTATIONS) != 0) {
			NewCUProposal proposal = new NewCUProposal(cu, node, NewCUProposal.K_ANNOTATION, enclosing, rel + 3);
			proposals.add(CodeActionHandler.wrap(proposal, CodeActionKind.QuickFix));
			// TODO: addNullityAnnotationTypesProposals(cu, node, proposals);
		}
	}

	/* (non-Javadoc)
	 * @see org.eclipse.jdt.internal.ui.text.correction.UnresolvedElementsBaseSubProcessor#getReorgSubProcessor()
	 */
	@Override
	protected ReorgCorrectionsBaseSubProcessor<ProposalKindWrapper> getReorgSubProcessor() {
		return new ReorgCorrectionsSubProcessor();
	}

	/* (non-Javadoc)
	 * @see org.eclipse.jdt.internal.ui.text.correction.UnresolvedElementsBaseSubProcessor#getTypeMismatchSubProcessor()
	 */
	@Override
	protected TypeMismatchBaseSubProcessor<ProposalKindWrapper> getTypeMismatchSubProcessor() {
		return new TypeMismatchSubProcessor();
	}

	/* (non-Javadoc)
	 * @see org.eclipse.jdt.internal.ui.text.correction.UnresolvedElementsBaseSubProcessor#getOriginalProposalFromT(java.lang.Object)
	 */
	@Override
	protected ChangeCorrectionProposalCore getOriginalProposalFromT(ProposalKindWrapper proposal) {
		return proposal == null ? null : proposal.getProposal();
	}

	/* (non-Javadoc)
	 * @see org.eclipse.jdt.internal.ui.text.correction.UnresolvedElementsBaseSubProcessor#newVariableCorrectionProposalToT(org.eclipse.jdt.internal.ui.text.correction.proposals.NewVariableCorrectionProposalCore, int)
	 */
	@Override
	protected ProposalKindWrapper newVariableCorrectionProposalToT(NewVariableCorrectionProposalCore core, int uid) {
		String declsToFinal = JavaLanguageServerPlugin.getPreferencesManager().getPreferences().getCodeGenerationAddFinalForNewDeclaration();
		core.setAddFinal("all".equals(declsToFinal) || "variables".equals(declsToFinal));
		return CodeActionHandler.wrap(core, CodeActionKind.QuickFix);
	}

	/* (non-Javadoc)
	 * @see org.eclipse.jdt.internal.ui.text.correction.UnresolvedElementsBaseSubProcessor#renameNodeCorrectionProposalToT(org.eclipse.jdt.internal.ui.text.correction.proposals.RenameNodeCorrectionProposalCore, int)
	 */
	@Override
	protected ProposalKindWrapper renameNodeCorrectionProposalToT(RenameNodeCorrectionProposalCore core, int uid) {
		return CodeActionHandler.wrap(core, CodeActionKind.QuickFix);
	}

	/* (non-Javadoc)
	 * @see org.eclipse.jdt.internal.ui.text.correction.UnresolvedElementsBaseSubProcessor#compositeProposalToT(org.eclipse.jdt.core.manipulation.ChangeCorrectionProposalCore, int)
	 */
	@Override
	protected ProposalKindWrapper compositeProposalToT(ChangeCorrectionProposalCore compositeProposal, int uid) {
		return CodeActionHandler.wrap(compositeProposal, CodeActionKind.QuickFix);
	}

	/* (non-Javadoc)
	 * @see org.eclipse.jdt.internal.ui.text.correction.UnresolvedElementsBaseSubProcessor#getQualifiedTypeNameHistoryBoost(java.lang.String, int, int)
	 */
	@Override
	protected int getQualifiedTypeNameHistoryBoost(String qualifiedName, int min, int max) {
		return 0;
	}

	/* (non-Javadoc)
	 * @see org.eclipse.jdt.internal.ui.text.correction.UnresolvedElementsBaseSubProcessor#linkedProposalToT(org.eclipse.jdt.internal.ui.text.correction.proposals.LinkedCorrectionProposalCore, int)
	 */
	@Override
	protected ProposalKindWrapper linkedProposalToT(LinkedCorrectionProposalCore core, int uid) {
		return CodeActionHandler.wrap(core, CodeActionKind.QuickFix);
	}

	/* (non-Javadoc)
	 * @see org.eclipse.jdt.internal.ui.text.correction.UnresolvedElementsBaseSubProcessor#changeCorrectionProposalToT(org.eclipse.jdt.core.manipulation.ChangeCorrectionProposalCore, int)
	 */
	@Override
	protected ProposalKindWrapper changeCorrectionProposalToT(ChangeCorrectionProposalCore core, int uid) {
		return CodeActionHandler.wrap(core, CodeActionKind.QuickFix);
	}

	/* (non-Javadoc)
	 * @see org.eclipse.jdt.internal.ui.text.correction.UnresolvedElementsBaseSubProcessor#qualifyTypeProposalToT(org.eclipse.jdt.internal.ui.text.correction.proposals.QualifyTypeProposalCore, int)
	 */
	@Override
	protected ProposalKindWrapper qualifyTypeProposalToT(QualifyTypeProposalCore core, int uid) {
		// Guessing on the kind here, as this was not in the jdt.ls version
		return CodeActionHandler.wrap(core, CodeActionKind.QuickFix);
	}

	/* (non-Javadoc)
	 * @see org.eclipse.jdt.internal.ui.text.correction.UnresolvedElementsBaseSubProcessor#addTypeParametersToT(org.eclipse.jdt.internal.ui.text.correction.proposals.AddTypeParameterProposalCore, int)
	 */
	@Override
	protected ProposalKindWrapper addTypeParametersToT(AddTypeParameterProposalCore core, int uid) {
		return CodeActionHandler.wrap(core, CodeActionKind.QuickFix);
	}

	/* (non-Javadoc)
	 * @see org.eclipse.jdt.internal.ui.text.correction.UnresolvedElementsBaseSubProcessor#addModuleRequiresProposalToT(org.eclipse.jdt.internal.ui.text.correction.proposals.AddModuleRequiresCorrectionProposalCore, int)
	 */
	@Override
	protected ProposalKindWrapper addModuleRequiresProposalToT(AddModuleRequiresCorrectionProposalCore core, int uid) {
		// Guessing on the kind here, as this was not in the jdt.ls version
		return CodeActionHandler.wrap(core, CodeActionKind.QuickFix);
	}

	/* (non-Javadoc)
	 * @see org.eclipse.jdt.internal.ui.text.correction.UnresolvedElementsBaseSubProcessor#replaceCorrectionProposalToT(org.eclipse.jdt.internal.ui.text.correction.proposals.ReplaceCorrectionProposalCore, int)
	 */
	@Override
	protected ProposalKindWrapper replaceCorrectionProposalToT(ReplaceCorrectionProposalCore core, int uid) {
		return CodeActionHandler.wrap(core, CodeActionKind.QuickFix);
	}

	/* (non-Javadoc)
	 * @see org.eclipse.jdt.internal.ui.text.correction.UnresolvedElementsBaseSubProcessor#castCorrectionProposalToT(org.eclipse.jdt.internal.ui.text.correction.proposals.CastCorrectionProposalCore, int)
	 */
	@Override
	protected ProposalKindWrapper castCorrectionProposalToT(CastCorrectionProposalCore core, int uid) {
		return CodeActionHandler.wrap(core, CodeActionKind.QuickFix);
	}

	/* (non-Javadoc)
	 * @see org.eclipse.jdt.internal.ui.text.correction.UnresolvedElementsBaseSubProcessor#addArgumentCorrectionProposalToT(org.eclipse.jdt.internal.ui.text.correction.proposals.AddArgumentCorrectionProposalCore, int)
	 */
	@Override
	protected ProposalKindWrapper addArgumentCorrectionProposalToT(AddArgumentCorrectionProposalCore core, int uid) {
		return CodeActionHandler.wrap(core, CodeActionKind.QuickFix);
	}

	/* (non-Javadoc)
	 * @see org.eclipse.jdt.internal.ui.text.correction.UnresolvedElementsBaseSubProcessor#changeMethodSignatureProposalToT(org.eclipse.jdt.internal.ui.text.correction.proposals.ChangeMethodSignatureProposalCore, int)
	 */
	@Override
	protected ProposalKindWrapper changeMethodSignatureProposalToT(ChangeMethodSignatureProposalCore core, int uid) {
		return CodeActionHandler.wrap(core, CodeActionKind.QuickFix);
	}

	/* (non-Javadoc)
	 * @see org.eclipse.jdt.internal.ui.text.correction.UnresolvedElementsBaseSubProcessor#newMethodProposalToT(org.eclipse.jdt.internal.ui.text.correction.proposals.NewMethodCorrectionProposalCore, int)
	 */
	@Override
	protected ProposalKindWrapper newMethodProposalToT(NewMethodCorrectionProposalCore core, int uid) {
		return CodeActionHandler.wrap(core, CodeActionKind.QuickFix);
	}

	/* (non-Javadoc)
	 * @see org.eclipse.jdt.internal.ui.text.correction.UnresolvedElementsBaseSubProcessor#rewriteProposalToT(org.eclipse.jdt.ui.text.java.correction.ASTRewriteCorrectionProposalCore, int)
	 */
	@Override
	protected ProposalKindWrapper rewriteProposalToT(ASTRewriteCorrectionProposalCore core, int uid) {
		return CodeActionHandler.wrap(core, CodeActionKind.QuickFix);
	}

	/* (non-Javadoc)
	 * @see org.eclipse.jdt.internal.ui.text.correction.UnresolvedElementsBaseSubProcessor#newAnnotationProposalToT(org.eclipse.jdt.internal.ui.text.correction.proposals.NewAnnotationMemberProposalCore, int)
	 */
	@Override
	protected ProposalKindWrapper newAnnotationProposalToT(NewAnnotationMemberProposalCore core, int uid) {
		return CodeActionHandler.wrap(core, CodeActionKind.QuickFix);
	}

	/* (non-Javadoc)
	 * @see org.eclipse.jdt.internal.ui.text.correction.UnresolvedElementsBaseSubProcessor#renameNodeProposalToT(org.eclipse.jdt.internal.ui.text.correction.proposals.RenameNodeCorrectionProposalCore, int)
	 */
	@Override
	protected ProposalKindWrapper renameNodeProposalToT(RenameNodeCorrectionProposalCore core, int uid) {
		return CodeActionHandler.wrap(core, CodeActionKind.QuickFix);
	}

	@Override
	public void collectTypeProposals(IInvocationContext context, IProblemLocation problem, Collection<ProposalKindWrapper> proposals) throws CoreException {
		// This is a hack because upstream does not behave as we expect.
		IProblemLocation wrap = new ProblemLocationWrapper(problem) {
			@Override
			public ASTNode getCoveringNode(CompilationUnit astRoot) {
				ICompilationUnit cu = context.getCompilationUnit();
				ASTNode selectedNode = problem.getCoveringNode(context.getASTRoot());
				try {
					if (problem.getProblemId() == IProblem.UndefinedType && cu.getBuffer() != null && cu.getBuffer().getChar(problem.getOffset()) == '@') {
						int offset = problem.getOffset() + 1;
						int length = problem.getLength() - 1;
						while (offset < cu.getBuffer().getLength() && length >= 0 && Character.isWhitespace(cu.getBuffer().getChar(offset))) {
							offset++;
							length--;
						}
						NodeFinder finder = new NodeFinder(context.getASTRoot(), offset, length);
						selectedNode = finder.getCoveringNode();
					}
				} catch (CoreException ce) {
					JavaLanguageServerPlugin.log(ce);
				}
				return selectedNode;
			}
		};
		boolean isUnnamedClass = List.of(context.getCompilationUnit().getTypes()).stream().anyMatch(t -> JDTUtils.isUnnamedClass(t));
		if (!isUnnamedClass) {
			super.collectTypeProposals(context, wrap, proposals);
		}
	}
}
