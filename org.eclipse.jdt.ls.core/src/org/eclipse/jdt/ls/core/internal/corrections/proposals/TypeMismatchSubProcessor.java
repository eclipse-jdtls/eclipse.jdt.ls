/*******************************************************************************
 * Copyright (c) 2000, 2024 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Copied from /org.eclipse.jdt.ui/src/org/eclipse/jdt/internal/ui/text/correction/TypeMismatchSubProcessor.java
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *     Benjamin Muskalla <bmuskalla@eclipsesource.com> - [quick fix] proposes wrong cast from Object to primitive int - https://bugs.eclipse.org/bugs/show_bug.cgi?id=100593
 *     Benjamin Muskalla <bmuskalla@eclipsesource.com> - [quick fix] "Add exceptions to..." quickfix does nothing - https://bugs.eclipse.org/bugs/show_bug.cgi?id=107924
 *******************************************************************************/
package org.eclipse.jdt.ls.core.internal.corrections.proposals;

import java.util.Collection;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.BodyDeclaration;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.IBinding;
import org.eclipse.jdt.core.dom.IMethodBinding;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.core.dom.SingleVariableDeclaration;
import org.eclipse.jdt.core.dom.Type;
import org.eclipse.jdt.core.dom.rewrite.ASTRewrite;
import org.eclipse.jdt.core.dom.rewrite.ImportRewrite;
import org.eclipse.jdt.core.dom.rewrite.ImportRewrite.ImportRewriteContext;
import org.eclipse.jdt.core.dom.rewrite.ImportRewrite.TypeLocation;
import org.eclipse.jdt.internal.core.manipulation.dom.ASTResolving;
import org.eclipse.jdt.internal.core.manipulation.util.BasicElementLabels;
import org.eclipse.jdt.internal.corext.codemanipulation.ContextSensitiveImportRewriteContext;
import org.eclipse.jdt.internal.ui.text.correction.IProposalRelevance;
import org.eclipse.jdt.internal.ui.text.correction.TypeMismatchBaseSubProcessor;
import org.eclipse.jdt.internal.ui.text.correction.proposals.CastCorrectionProposalCore;
import org.eclipse.jdt.internal.ui.text.correction.proposals.ChangeMethodSignatureProposalCore;
import org.eclipse.jdt.internal.ui.text.correction.proposals.ChangeMethodSignatureProposalCore.ChangeDescription;
import org.eclipse.jdt.internal.ui.text.correction.proposals.ImplementInterfaceProposalCore;
import org.eclipse.jdt.internal.ui.text.correction.proposals.NewVariableCorrectionProposalCore;
import org.eclipse.jdt.internal.ui.text.correction.proposals.OptionalCorrectionProposalCore;
import org.eclipse.jdt.internal.ui.text.correction.proposals.TypeChangeCorrectionProposalCore;
import org.eclipse.jdt.ls.core.internal.Messages;
import org.eclipse.jdt.ls.core.internal.corrections.CorrectionMessages;
import org.eclipse.jdt.ls.core.internal.corrections.ProposalKindWrapper;
import org.eclipse.jdt.ls.core.internal.handlers.CodeActionHandler;
import org.eclipse.jdt.ui.text.java.IInvocationContext;
import org.eclipse.jdt.ui.text.java.IProblemLocation;
import org.eclipse.jdt.ui.text.java.correction.ASTRewriteCorrectionProposalCore;
import org.eclipse.lsp4j.CodeActionKind;


public class TypeMismatchSubProcessor extends TypeMismatchBaseSubProcessor<ProposalKindWrapper> {

	public TypeMismatchSubProcessor() {
	}

	public static void addTypeMismatchProposals(IInvocationContext context, IProblemLocation problem,
			Collection<ProposalKindWrapper> proposals) throws CoreException {
		new TypeMismatchSubProcessor().collectTypeMismatchProposals(context, problem, proposals);
	}

	public static ITypeBinding boxUnboxPrimitives(ITypeBinding castType, ITypeBinding toCast, AST ast) {
		return TypeMismatchSubProcessor.boxOrUnboxPrimitives(castType, toCast, ast);
	}

	public static void addChangeSenderTypeProposals(IInvocationContext context, Expression nodeToCast,
			ITypeBinding castTypeBinding, boolean isAssignedNode, int relevance,
			Collection<ProposalKindWrapper> proposals) throws JavaModelException {
		new TypeMismatchSubProcessor().collectChangeSenderTypeProposals(context, nodeToCast, castTypeBinding, isAssignedNode, relevance, proposals);
	}

	public static ProposalKindWrapper createCastProposal(IInvocationContext context, ITypeBinding castTypeBinding, Expression nodeToCast, int relevance) {
		return new TypeMismatchSubProcessor().collectCastProposals(context, castTypeBinding, nodeToCast, relevance);
	}

	public static void addIncompatibleReturnTypeProposals(IInvocationContext context, IProblemLocation problem,
			Collection<ProposalKindWrapper> proposals) throws JavaModelException {
		new TypeMismatchSubProcessor().collectIncompatibleReturnTypeProposals(context, problem, proposals);
	}

	public static void addIncompatibleThrowsProposals(IInvocationContext context, IProblemLocation problem,
			Collection<ProposalKindWrapper> proposals) throws JavaModelException {
		new TypeMismatchSubProcessor().collectIncompatibleThrowsProposals(context, problem, proposals);
	}

	public static void addTypeMismatchInForEachProposals(IInvocationContext context, IProblemLocation problem,
			Collection<ProposalKindWrapper> proposals) {
		new TypeMismatchSubProcessor().collectTypeMismatchInForEachProposals(context, problem, proposals);
	}

	@Override
	public void collectTypeMismatchProposals(IInvocationContext context, IProblemLocation problem, Collection<ProposalKindWrapper> proposals) throws CoreException {
		IProblemLocation wrapped = new ProblemLocationWrapper(problem) {
			@Override
			public String[] getProblemArguments() {
				// This is a hack to get around superclass restrictions
				String[] ret = super.getProblemArguments();
				if (ret == null || ret.length == 0) {
					return new String[] { "", "" };
				}
				if (ret.length == 1) {
					return new String[] { ret[0], "" };
				}
				return ret;
			}
		};
		super.collectTypeMismatchProposals(context, wrapped, proposals);
	}
	/* (non-Javadoc)
	 * @see org.eclipse.jdt.internal.ui.text.correction.TypeMismatchBaseSubProcessor#createInsertNullCheckProposal(java.lang.String, org.eclipse.jdt.core.ICompilationUnit, org.eclipse.jdt.core.dom.rewrite.ASTRewrite, int)
	 */
	@Override
	protected ProposalKindWrapper createInsertNullCheckProposal(String label, ICompilationUnit compilationUnit, ASTRewrite rewrite, int relevance) {
		ASTRewriteCorrectionProposalCore p = new ASTRewriteCorrectionProposalCore(label, compilationUnit, rewrite, relevance);
		return CodeActionHandler.wrap(p, CodeActionKind.QuickFix);
	}

	/* (non-Javadoc)
	 * @see org.eclipse.jdt.internal.ui.text.correction.TypeMismatchBaseSubProcessor#createChangeReturnTypeProposal(java.lang.String, org.eclipse.jdt.core.ICompilationUnit, org.eclipse.jdt.core.dom.rewrite.ASTRewrite, int, org.eclipse.jdt.core.dom.ITypeBinding, org.eclipse.jdt.core.dom.AST, org.eclipse.jdt.core.dom.CompilationUnit, org.eclipse.jdt.core.dom.MethodDeclaration, org.eclipse.jdt.core.dom.BodyDeclaration)
	 */
	@Override
	protected ProposalKindWrapper createChangeReturnTypeProposal(String label, ICompilationUnit cu, ASTRewrite rewrite, int relevance, ITypeBinding currBinding, AST ast, CompilationUnit astRoot, MethodDeclaration methodDeclaration,
			BodyDeclaration decl) {
		ASTRewriteCorrectionProposalCore proposal = new ASTRewriteCorrectionProposalCore(label, cu, rewrite, IProposalRelevance.CHANGE_METHOD_RETURN_TYPE);

		ImportRewrite imports = proposal.createImportRewrite(astRoot);
		ImportRewriteContext importRewriteContext = new ContextSensitiveImportRewriteContext(decl, imports);

		Type newReturnType = imports.addImport(currBinding, ast, importRewriteContext, TypeLocation.RETURN_TYPE);
		rewrite.replace(methodDeclaration.getReturnType2(), newReturnType, null);
		return CodeActionHandler.wrap(proposal, CodeActionKind.QuickFix);
	}

	/* (non-Javadoc)
	 * @see org.eclipse.jdt.internal.ui.text.correction.TypeMismatchBaseSubProcessor#createOptionalProposal(java.lang.String, org.eclipse.jdt.core.ICompilationUnit, org.eclipse.jdt.core.dom.Expression, int, int)
	 */
	@Override
	protected ProposalKindWrapper createOptionalProposal(String label, ICompilationUnit cu, Expression nodeToCast, int relevance, int optionalType) {
		OptionalCorrectionProposalCore core = new OptionalCorrectionProposalCore(label, cu, nodeToCast, relevance, optionalType);
		return CodeActionHandler.wrap(core, CodeActionKind.QuickFix);
	}

	/* (non-Javadoc)
	 * @see org.eclipse.jdt.internal.ui.text.correction.TypeMismatchBaseSubProcessor#createImplementInterfaceProposal(org.eclipse.jdt.core.ICompilationUnit, org.eclipse.jdt.core.dom.ITypeBinding, org.eclipse.jdt.core.dom.CompilationUnit, org.eclipse.jdt.core.dom.ITypeBinding, int)
	 */
	@Override
	protected ProposalKindWrapper createImplementInterfaceProposal(ICompilationUnit nodeCu, ITypeBinding typeDecl, CompilationUnit astRoot, ITypeBinding castTypeBinding, int relevance) {
		ImplementInterfaceProposalCore core = new ImplementInterfaceProposalCore(nodeCu, typeDecl, astRoot, castTypeBinding, relevance);
		return CodeActionHandler.wrap(core, CodeActionKind.QuickFix);
	}

	/* (non-Javadoc)
	 * @see org.eclipse.jdt.internal.ui.text.correction.TypeMismatchBaseSubProcessor#createChangeSenderTypeProposal(org.eclipse.jdt.core.ICompilationUnit, org.eclipse.jdt.core.dom.IBinding, org.eclipse.jdt.core.dom.CompilationUnit, org.eclipse.jdt.core.dom.ITypeBinding, boolean, int)
	 */
	@Override
	protected ProposalKindWrapper createChangeSenderTypeProposal(ICompilationUnit targetCu, IBinding callerBindingDecl, CompilationUnit astRoot, ITypeBinding castTypeBinding, boolean isAssignedNode, int relevance) {
		TypeChangeCorrectionProposalCore p = new TypeChangeCorrectionProposalCore(targetCu, callerBindingDecl, astRoot, castTypeBinding, isAssignedNode, relevance);
		return CodeActionHandler.wrap(p, CodeActionKind.QuickFix);
	}

	/* (non-Javadoc)
	 * @see org.eclipse.jdt.internal.ui.text.correction.TypeMismatchBaseSubProcessor#createCastCorrectionProposal(java.lang.String, org.eclipse.jdt.core.ICompilationUnit, org.eclipse.jdt.core.dom.Expression, org.eclipse.jdt.core.dom.ITypeBinding, int)
	 */
	@Override
	protected ProposalKindWrapper createCastCorrectionProposal(String label, ICompilationUnit cu, Expression nodeToCast, ITypeBinding castTypeBinding, int relevance) {
		CastCorrectionProposalCore p = new CastCorrectionProposalCore(label, cu, nodeToCast, castTypeBinding, relevance);
		return CodeActionHandler.wrap(p, CodeActionKind.QuickFix);
	}

	/* (non-Javadoc)
	 * @see org.eclipse.jdt.internal.ui.text.correction.TypeMismatchBaseSubProcessor#createChangeReturnTypeOfOverridden(org.eclipse.jdt.core.ICompilationUnit, org.eclipse.jdt.core.dom.IMethodBinding, org.eclipse.jdt.core.dom.CompilationUnit, org.eclipse.jdt.core.dom.ITypeBinding, boolean, int, org.eclipse.jdt.core.dom.ITypeBinding)
	 */
	@Override
	protected ProposalKindWrapper createChangeReturnTypeOfOverridden(ICompilationUnit targetCu, IMethodBinding overriddenDecl, CompilationUnit astRoot, ITypeBinding returnType, boolean offerSuperTypeProposals, int relevance,
			ITypeBinding overridenDeclType) {
		TypeChangeCorrectionProposalCore proposal = new TypeChangeCorrectionProposalCore(targetCu, overriddenDecl, astRoot, returnType, false, relevance);
		if (overridenDeclType.isInterface()) {
			proposal.setDisplayName(Messages.format(CorrectionMessages.TypeMismatchSubProcessor_changereturnofimplemented_description, BasicElementLabels.getJavaElementName(overriddenDecl.getName())));
		} else {
			proposal.setDisplayName(Messages.format(CorrectionMessages.TypeMismatchSubProcessor_changereturnofoverridden_description, BasicElementLabels.getJavaElementName(overriddenDecl.getName())));
		}
		return CodeActionHandler.wrap(proposal, CodeActionKind.QuickFix);
	}

	/* (non-Javadoc)
	 * @see org.eclipse.jdt.internal.ui.text.correction.TypeMismatchBaseSubProcessor#createChangeIncompatibleReturnTypeProposal(org.eclipse.jdt.core.ICompilationUnit, org.eclipse.jdt.core.dom.IMethodBinding, org.eclipse.jdt.core.dom.CompilationUnit, org.eclipse.jdt.core.dom.ITypeBinding, boolean, int)
	 */
	@Override
	protected ProposalKindWrapper createChangeIncompatibleReturnTypeProposal(ICompilationUnit cu, IMethodBinding methodDecl, CompilationUnit astRoot, ITypeBinding overriddenReturnType, boolean offerSuperTypeProposals, int relevance) {
		TypeChangeCorrectionProposalCore p = new TypeChangeCorrectionProposalCore(cu, methodDecl, astRoot, overriddenReturnType, false, relevance);
		return CodeActionHandler.wrap(p, CodeActionKind.QuickFix);
	}

	/* (non-Javadoc)
	 * @see org.eclipse.jdt.internal.ui.text.correction.TypeMismatchBaseSubProcessor#createChangeMethodSignatureProposal(java.lang.String, org.eclipse.jdt.core.ICompilationUnit, org.eclipse.jdt.core.dom.CompilationUnit, org.eclipse.jdt.core.dom.IMethodBinding, org.eclipse.jdt.internal.ui.text.correction.proposals.ChangeMethodSignatureProposalCore.ChangeDescription[], org.eclipse.jdt.internal.ui.text.correction.proposals.ChangeMethodSignatureProposalCore.ChangeDescription[], int)
	 */
	@Override
	protected ProposalKindWrapper createChangeMethodSignatureProposal(String label, ICompilationUnit cu, CompilationUnit astRoot, IMethodBinding methodDeclBinding, ChangeDescription[] paramChanges, ChangeDescription[] changes,
			int relevance) {
		ChangeMethodSignatureProposalCore p = new ChangeMethodSignatureProposalCore(label, cu, astRoot, methodDeclBinding, null, changes, relevance);
		return CodeActionHandler.wrap(p, CodeActionKind.QuickFix);
	}

	/* (non-Javadoc)
	 * @see org.eclipse.jdt.internal.ui.text.correction.TypeMismatchBaseSubProcessor#createNewVariableCorrectionProposal(java.lang.String, org.eclipse.jdt.core.ICompilationUnit, int, org.eclipse.jdt.core.dom.SimpleName, org.eclipse.jdt.core.dom.ITypeBinding, int)
	 */
	@Override
	protected ProposalKindWrapper createNewVariableCorrectionProposal(String label, ICompilationUnit cu, int local, SimpleName simpleName, ITypeBinding senderBinding, int relevance) {
		NewVariableCorrectionProposalCore p = new NewVariableCorrectionProposalCore(label, cu, NewVariableCorrectionProposalCore.LOCAL, simpleName, null, relevance, false);
		return CodeActionHandler.wrap(p, CodeActionKind.QuickFix);
	}

	/* (non-Javadoc)
	 * @see org.eclipse.jdt.internal.ui.text.correction.TypeMismatchBaseSubProcessor#createIncompatibleForEachTypeProposal(java.lang.String, org.eclipse.jdt.core.ICompilationUnit, org.eclipse.jdt.core.dom.rewrite.ASTRewrite, int, org.eclipse.jdt.core.dom.CompilationUnit, org.eclipse.jdt.core.dom.AST, org.eclipse.jdt.core.dom.ITypeBinding, org.eclipse.jdt.core.dom.ASTNode, org.eclipse.jdt.core.dom.SingleVariableDeclaration)
	 */
	@Override
	protected ProposalKindWrapper createIncompatibleForEachTypeProposal(String label, ICompilationUnit cu, ASTRewrite rewrite, int incompatibleForeachType, CompilationUnit astRoot, AST ast, ITypeBinding expectedBinding,
			ASTNode selectedNode, SingleVariableDeclaration parameter) {
		ASTRewriteCorrectionProposalCore proposal = new ASTRewriteCorrectionProposalCore(label, cu, rewrite, IProposalRelevance.INCOMPATIBLE_FOREACH_TYPE);

		ImportRewrite importRewrite = proposal.createImportRewrite(astRoot);
		ImportRewriteContext importRewriteContext = new ContextSensitiveImportRewriteContext(ASTResolving.findParentBodyDeclaration(selectedNode), importRewrite);
		Type newType = importRewrite.addImport(expectedBinding, ast, importRewriteContext, TypeLocation.LOCAL_VARIABLE);
		rewrite.replace(parameter.getType(), newType, null);

		return CodeActionHandler.wrap(proposal, CodeActionKind.QuickFix);
	}

	/* (non-Javadoc)
	 * @see org.eclipse.jdt.internal.ui.text.correction.TypeMismatchBaseSubProcessor#createChangeConstructorTypeProposal(org.eclipse.jdt.core.ICompilationUnit, org.eclipse.jdt.core.dom.ASTNode, org.eclipse.jdt.core.dom.CompilationUnit, org.eclipse.jdt.core.dom.ITypeBinding, int)
	 */
	@Override
	protected ProposalKindWrapper createChangeConstructorTypeProposal(ICompilationUnit targetCu, ASTNode callerNode, CompilationUnit astRoot, ITypeBinding castTypeBinding, int relevance) {
		return null;
	}

}
