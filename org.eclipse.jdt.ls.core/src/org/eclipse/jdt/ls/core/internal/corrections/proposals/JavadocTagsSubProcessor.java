/*******************************************************************************
 * Copyright (c) 2000, 2024 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Copied from /org.eclipse.jdt.ui/src/org/eclipse/jdt/internal/ui/text/correction/JavadocTagsSubProcessor.java
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.jdt.ls.core.internal.corrections.proposals;

import java.util.ArrayList;
import java.util.Collection;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.BodyDeclaration;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.Javadoc;
import org.eclipse.jdt.core.dom.ModuleDeclaration;
import org.eclipse.jdt.core.dom.rewrite.ASTRewrite;
import org.eclipse.jdt.internal.core.manipulation.dom.ASTResolving;
import org.eclipse.jdt.internal.corext.dom.ASTNodes;
import org.eclipse.jdt.internal.ui.text.correction.AddAllMissingJavadocTagsProposalCore;
import org.eclipse.jdt.internal.ui.text.correction.AddJavadocCommentProposalCore;
import org.eclipse.jdt.internal.ui.text.correction.AddMissingJavadocTagProposalCore;
import org.eclipse.jdt.internal.ui.text.correction.AddMissingModuleJavadocTagProposalCore;
import org.eclipse.jdt.internal.ui.text.correction.JavadocTagsBaseSubProcessor;
import org.eclipse.jdt.internal.ui.text.correction.ProblemLocation;
import org.eclipse.jdt.internal.ui.text.correction.proposals.ReplaceCorrectionProposalCore;
import org.eclipse.jdt.ls.core.internal.corrections.ProposalKindWrapper;
import org.eclipse.jdt.ls.core.internal.handlers.CodeActionHandler;
import org.eclipse.jdt.ui.text.java.IInvocationContext;
import org.eclipse.jdt.ui.text.java.IProblemLocation;
import org.eclipse.jdt.ui.text.java.correction.ASTRewriteCorrectionProposalCore;
import org.eclipse.lsp4j.CodeActionKind;


/**
 *
 */
public class JavadocTagsSubProcessor extends JavadocTagsBaseSubProcessor<ProposalKindWrapper> {
	public static void getMissingJavadocTagProposals(IInvocationContext context, IProblemLocation problem, Collection<ProposalKindWrapper> proposals) {
		new JavadocTagsSubProcessor().addMissingJavadocTagProposals(context, problem, proposals);
	}

	public static void getUnusedAndUndocumentedParameterOrExceptionProposals(IInvocationContext context,
			IProblemLocation problem, Collection<ProposalKindWrapper> proposals) {
		new JavadocTagsSubProcessor().addUnusedAndUndocumentedParameterOrExceptionProposals(context, problem, proposals);
	}

	public static void getMissingJavadocCommentProposals(IInvocationContext context, ASTNode coveringNode,
			Collection<ProposalKindWrapper> proposals, String kind) throws CoreException {
		ArrayList<ProposalKindWrapper> tmp = new ArrayList<>();
		// TODO this should be fixed upstream.
		// We should be able to pass the node, not only a problem
		IProblemLocation stub = new ProblemLocation(0, 0, 0, null, false, kind) {
			@Override
			public ASTNode getCoveringNode(CompilationUnit astRoot) {
				return coveringNode;
			}
		};
		new JavadocTagsSubProcessor().addMissingJavadocCommentProposals(context, stub, tmp);
		for (int i = 0; i < tmp.size(); i++) {
			proposals.add(new ProposalKindWrapper(tmp.get(i).getProposal(), kind));
		}
	}

	@Override
	public void addMissingJavadocCommentProposals(IInvocationContext context, IProblemLocation problem, Collection<ProposalKindWrapper> proposals) throws CoreException {
		ASTNode node = problem.getCoveringNode(context.getASTRoot());
		ASTNode node2 = ASTNodes.getNormalizedNode(node);
		BodyDeclaration bodyDeclaration = ASTResolving.findParentBodyDeclaration(node2);
		if (bodyDeclaration != null) {
			Javadoc javadoc = bodyDeclaration.getJavadoc();
			if (javadoc == null) {
				super.addMissingJavadocCommentProposals(context, problem, proposals);
			}
		}
	}

	@Override
	public void addMissingJavadocTagProposals(IInvocationContext context, ASTNode node, Collection<ProposalKindWrapper> proposals) {
		ASTNode node2 = ASTNodes.getNormalizedNode(node);
		BodyDeclaration bodyDeclaration = ASTResolving.findParentBodyDeclaration(node2);
		if (bodyDeclaration != null) {
			Javadoc javadoc = bodyDeclaration.getJavadoc();
			if (javadoc != null) {
				super.addMissingJavadocTagProposals(context, node, proposals);
			}
		}
	}

	public static void getRemoveJavadocTagProposals(IInvocationContext context, IProblemLocation problem,
			Collection<ProposalKindWrapper> proposals) {
		new JavadocTagsSubProcessor().addRemoveJavadocTagProposals(context, problem, proposals);
	}

	public static void getInvalidQualificationProposals(IInvocationContext context, IProblemLocation problem,
			Collection<ProposalKindWrapper> proposals) {
		new JavadocTagsSubProcessor().addInvalidQualificationProposals(context, problem, proposals);
	}

	/* (non-Javadoc)
	 * @see org.eclipse.jdt.internal.ui.text.correction.JavadocTagsBaseSubProcessor#addAllMissingJavadocTagsProposal(java.lang.String, org.eclipse.jdt.core.ICompilationUnit, org.eclipse.jdt.core.dom.ASTNode, int)
	 */
	@Override
	protected ProposalKindWrapper addAllMissingJavadocTagsProposal(String label2, ICompilationUnit compilationUnit, ASTNode parentDeclaration, int relevance) {
		AddAllMissingJavadocTagsProposalCore addAllMissing = new AddAllMissingJavadocTagsProposalCore(label2, compilationUnit, parentDeclaration, relevance);
		return CodeActionHandler.wrap(addAllMissing, CodeActionKind.QuickFix);
	}

	/* (non-Javadoc)
	 * @see org.eclipse.jdt.internal.ui.text.correction.JavadocTagsBaseSubProcessor#addMissingJavadocTagProposal(java.lang.String, org.eclipse.jdt.core.ICompilationUnit, org.eclipse.jdt.core.dom.ASTNode, org.eclipse.jdt.core.dom.ASTNode, int)
	 */
	@Override
	protected ProposalKindWrapper addMissingJavadocTagProposal(String label, ICompilationUnit compilationUnit, ASTNode parentDeclaration, ASTNode node, int relevance) {
		AddMissingJavadocTagProposalCore proposal = new AddMissingJavadocTagProposalCore(label, compilationUnit, parentDeclaration, node, relevance);
		return CodeActionHandler.wrap(proposal, CodeActionKind.QuickFix);
	}

	/* (non-Javadoc)
	 * @see org.eclipse.jdt.internal.ui.text.correction.JavadocTagsBaseSubProcessor#addAllMissingModuleJavadocTagsProposal(java.lang.String, org.eclipse.jdt.core.ICompilationUnit, org.eclipse.jdt.core.dom.ModuleDeclaration, org.eclipse.jdt.core.dom.ASTNode, int)
	 */
	@Override
	protected ProposalKindWrapper addAllMissingModuleJavadocTagsProposal(String label2, ICompilationUnit compilationUnit, ModuleDeclaration moduleDecl, ASTNode node, int relevance) {
		AddAllMissingJavadocTagsProposalCore addAllMissing = new AddAllMissingJavadocTagsProposalCore(label2, compilationUnit, moduleDecl, relevance);
		return CodeActionHandler.wrap(addAllMissing, CodeActionKind.QuickFix);
	}

	/* (non-Javadoc)
	 * @see org.eclipse.jdt.internal.ui.text.correction.JavadocTagsBaseSubProcessor#addMissingModuleJavadocTagProposal(java.lang.String, org.eclipse.jdt.core.ICompilationUnit, org.eclipse.jdt.core.dom.ModuleDeclaration, org.eclipse.jdt.core.dom.ASTNode, int)
	 */
	@Override
	protected ProposalKindWrapper addMissingModuleJavadocTagProposal(String label, ICompilationUnit compilationUnit, ModuleDeclaration moduleDecl, ASTNode node, int relevance) {
		AddMissingModuleJavadocTagProposalCore core = new AddMissingModuleJavadocTagProposalCore(label, compilationUnit, moduleDecl, node, relevance);
		return CodeActionHandler.wrap(core, CodeActionKind.QuickFix);
	}

	/* (non-Javadoc)
	 * @see org.eclipse.jdt.internal.ui.text.correction.JavadocTagsBaseSubProcessor#addJavadocCommentProposal(java.lang.String, org.eclipse.jdt.core.ICompilationUnit, int, int, java.lang.String)
	 */
	@Override
	protected ProposalKindWrapper addJavadocCommentProposal(String label, ICompilationUnit cu, int relevance, int startPosition, String comment) {
		AddJavadocCommentProposalCore proposal = new AddJavadocCommentProposalCore(label, cu, relevance, startPosition, comment);
		return CodeActionHandler.wrap(proposal, CodeActionKind.QuickFix);
	}

	/* (non-Javadoc)
	 * @see org.eclipse.jdt.internal.ui.text.correction.JavadocTagsBaseSubProcessor#createRemoveJavadocTagProposals(java.lang.String, org.eclipse.jdt.core.ICompilationUnit, org.eclipse.jdt.core.dom.rewrite.ASTRewrite, int)
	 */
	@Override
	protected ProposalKindWrapper createRemoveJavadocTagProposals(String label, ICompilationUnit compilationUnit, ASTRewrite rewrite, int relevance) {
		ASTRewriteCorrectionProposalCore proposal = new ASTRewriteCorrectionProposalCore(label, compilationUnit, rewrite, relevance);
		return CodeActionHandler.wrap(proposal, CodeActionKind.QuickFix);
	}

	/* (non-Javadoc)
	 * @see org.eclipse.jdt.internal.ui.text.correction.JavadocTagsBaseSubProcessor#createRemoveDuplicateModuleJavadocTagProposal(java.lang.String, org.eclipse.jdt.core.ICompilationUnit, int, int, java.lang.String, int)
	 */
	@Override
	protected ProposalKindWrapper createRemoveDuplicateModuleJavadocTagProposal(String label, ICompilationUnit compilationUnit, int start, int length, String string, int relevance) {
		ReplaceCorrectionProposalCore core = new ReplaceCorrectionProposalCore(label, compilationUnit, start, length, string, relevance);
		return CodeActionHandler.wrap(core, CodeActionKind.QuickFix);
	}

	/* (non-Javadoc)
	 * @see org.eclipse.jdt.internal.ui.text.correction.JavadocTagsBaseSubProcessor#createInvalidQualificationProposal(java.lang.String, org.eclipse.jdt.core.ICompilationUnit, org.eclipse.jdt.core.dom.rewrite.ASTRewrite, int)
	 */
	@Override
	protected ProposalKindWrapper createInvalidQualificationProposal(String label, ICompilationUnit compilationUnit, ASTRewrite rewrite, int relevance) {
		ASTRewriteCorrectionProposalCore proposal = new ASTRewriteCorrectionProposalCore(label, compilationUnit, rewrite, relevance);
		return CodeActionHandler.wrap(proposal, CodeActionKind.QuickFix);
	}
}
