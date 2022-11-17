/*******************************************************************************
 * Copyright (c) 2000, 2017 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
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

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.List;
import java.util.Map;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.compiler.IProblem;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.Block;
import org.eclipse.jdt.core.dom.BodyDeclaration;
import org.eclipse.jdt.core.dom.CastExpression;
import org.eclipse.jdt.core.dom.CatchClause;
import org.eclipse.jdt.core.dom.ChildListPropertyDescriptor;
import org.eclipse.jdt.core.dom.ClassInstanceCreation;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.ConditionalExpression;
import org.eclipse.jdt.core.dom.EmptyStatement;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.ExpressionStatement;
import org.eclipse.jdt.core.dom.ForStatement;
import org.eclipse.jdt.core.dom.IMethodBinding;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.IVariableBinding;
import org.eclipse.jdt.core.dom.IfStatement;
import org.eclipse.jdt.core.dom.InfixExpression;
import org.eclipse.jdt.core.dom.Initializer;
import org.eclipse.jdt.core.dom.LambdaExpression;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.MethodReference;
import org.eclipse.jdt.core.dom.Modifier;
import org.eclipse.jdt.core.dom.Name;
import org.eclipse.jdt.core.dom.ParenthesizedExpression;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.core.dom.SingleVariableDeclaration;
import org.eclipse.jdt.core.dom.Statement;
import org.eclipse.jdt.core.dom.StringLiteral;
import org.eclipse.jdt.core.dom.StructuralPropertyDescriptor;
import org.eclipse.jdt.core.dom.SwitchCase;
import org.eclipse.jdt.core.dom.SwitchExpression;
import org.eclipse.jdt.core.dom.SwitchStatement;
import org.eclipse.jdt.core.dom.ThrowStatement;
import org.eclipse.jdt.core.dom.TryStatement;
import org.eclipse.jdt.core.dom.Type;
import org.eclipse.jdt.core.dom.TypeDeclaration;
import org.eclipse.jdt.core.dom.UnionType;
import org.eclipse.jdt.core.dom.VariableDeclarationExpression;
import org.eclipse.jdt.core.dom.WhileStatement;
import org.eclipse.jdt.core.dom.rewrite.ASTRewrite;
import org.eclipse.jdt.core.dom.rewrite.ImportRewrite;
import org.eclipse.jdt.core.dom.rewrite.ImportRewrite.ImportRewriteContext;
import org.eclipse.jdt.core.dom.rewrite.ImportRewrite.TypeLocation;
import org.eclipse.jdt.core.dom.rewrite.ListRewrite;
import org.eclipse.jdt.core.manipulation.CleanUpOptionsCore;
import org.eclipse.jdt.core.refactoring.CompilationUnitChange;
import org.eclipse.jdt.internal.core.manipulation.StubUtility;
import org.eclipse.jdt.internal.core.manipulation.dom.ASTResolving;
import org.eclipse.jdt.internal.core.manipulation.dom.NecessaryParenthesesChecker;
import org.eclipse.jdt.internal.corext.codemanipulation.ContextSensitiveImportRewriteContext;
import org.eclipse.jdt.internal.corext.dom.ASTNodes;
import org.eclipse.jdt.internal.corext.dom.Bindings;
import org.eclipse.jdt.internal.corext.dom.CodeScopeBuilder;
import org.eclipse.jdt.internal.corext.dom.Selection;
import org.eclipse.jdt.internal.corext.fix.CleanUpConstants;
import org.eclipse.jdt.internal.corext.fix.CodeStyleFixCore;
import org.eclipse.jdt.internal.corext.fix.IProposableFix;
import org.eclipse.jdt.internal.corext.fix.MissingAnnotationAttributesFixCore;
import org.eclipse.jdt.internal.corext.fix.SealedClassFixCore;
import org.eclipse.jdt.internal.corext.fix.UnimplementedCodeFixCore;
import org.eclipse.jdt.internal.corext.fix.UnusedCodeFixCore;
import org.eclipse.jdt.internal.corext.refactoring.util.NoCommentSourceRangeComputer;
import org.eclipse.jdt.internal.corext.refactoring.util.SurroundWithAnalyzer;
import org.eclipse.jdt.internal.corext.util.JavaModelUtil;
import org.eclipse.jdt.internal.ui.fix.CodeStyleCleanUpCore;
import org.eclipse.jdt.internal.ui.fix.UnnecessaryCodeCleanUpCore;
import org.eclipse.jdt.internal.ui.text.correction.IProblemLocationCore;
import org.eclipse.jdt.internal.ui.util.ASTHelper;
import org.eclipse.jdt.ls.core.internal.JavaLanguageServerPlugin;
import org.eclipse.jdt.ls.core.internal.corext.refactoring.surround.ExceptionAnalyzer;
import org.eclipse.jdt.ls.core.internal.corext.refactoring.surround.SurroundWithTryCatchRefactoring;
import org.eclipse.jdt.ls.core.internal.corrections.CorrectionMessages;
import org.eclipse.jdt.ls.core.internal.corrections.IInvocationContext;
import org.eclipse.jdt.ls.core.internal.corrections.InnovationContext;
import org.eclipse.jdt.ls.core.internal.corrections.InvertBooleanUtility;
import org.eclipse.jdt.ls.core.internal.corrections.proposals.ChangeMethodSignatureProposal.ChangeDescription;
import org.eclipse.jdt.ls.core.internal.corrections.proposals.ChangeMethodSignatureProposal.InsertDescription;
import org.eclipse.jdt.ls.core.internal.corrections.proposals.ChangeMethodSignatureProposal.RemoveDescription;
import org.eclipse.jdt.ls.core.internal.text.correction.ModifierCorrectionSubProcessor;
import org.eclipse.jdt.ls.core.internal.text.correction.QuickAssistProcessor;
import org.eclipse.lsp4j.CodeActionKind;

public class LocalCorrectionsSubProcessor {

	private static final String ADD_STATIC_ACCESS_ID = "org.eclipse.jdt.ui.correction.changeToStatic"; //$NON-NLS-1$

	public static void addUncaughtExceptionProposals(IInvocationContext context, IProblemLocationCore problem, Collection<ChangeCorrectionProposal> proposals) throws CoreException {
		ICompilationUnit cu = context.getCompilationUnit();

		CompilationUnit astRoot = context.getASTRoot();
		ASTNode selectedNode = context.getSelectionLength() > 0 ? context.getCoveredNode() : problem.getCoveringNode(astRoot);
		if (selectedNode == null) {
			return;
		}
		while (selectedNode != null && !(selectedNode instanceof Statement) && !(selectedNode instanceof VariableDeclarationExpression) && !(selectedNode.getLocationInParent() == LambdaExpression.BODY_PROPERTY)
				&& !(selectedNode instanceof MethodReference)) {
			selectedNode = selectedNode.getParent();
		}
		if (selectedNode == null) {
			return;
		}

		int offset = selectedNode.getStartPosition();
		int length = selectedNode.getLength();
		int selectionEnd = context.getSelectionOffset() + context.getSelectionLength();
		if (selectionEnd > offset + length) {
			// extend the selection if more than one statement is selected (bug 72149)
			length = selectionEnd - offset;
		}

		//Surround with proposals
		SurroundWithTryCatchRefactoring refactoring = SurroundWithTryCatchRefactoring.create(cu, offset, length);
		if (refactoring == null) {
			return;
		}

		refactoring.setLeaveDirty(true);
		if (refactoring.checkActivationBasics(astRoot).isOK()) {
			String label = CorrectionMessages.LocalCorrectionsSubProcessor_surroundwith_trycatch_description;
			RefactoringCorrectionProposal proposal = new RefactoringCorrectionProposal(label, CodeActionKind.QuickFix, cu, refactoring, IProposalRelevance.SURROUND_WITH_TRY_CATCH);
			proposal.setLinkedProposalModel(refactoring.getLinkedProposalModel());
			proposals.add(proposal);
		}

		if (JavaModelUtil.is1d7OrHigher(cu.getJavaProject())) {
			refactoring = SurroundWithTryCatchRefactoring.create(cu, offset, length, true);
			if (refactoring == null) {
				return;
			}

			refactoring.setLeaveDirty(true);
			if (refactoring.checkActivationBasics(astRoot).isOK()) {
				String label = CorrectionMessages.LocalCorrectionsSubProcessor_surroundwith_trymulticatch_description;
				RefactoringCorrectionProposal proposal = new RefactoringCorrectionProposal(label, CodeActionKind.QuickFix, cu, refactoring, IProposalRelevance.SURROUND_WITH_TRY_MULTICATCH);
				proposal.setLinkedProposalModel(refactoring.getLinkedProposalModel());
				proposals.add(proposal);
			}
		}

		//Surround with try-with
		getTryWithResourceProposals(context, problem, proposals);

		//Catch exception
		BodyDeclaration decl = ASTResolving.findParentBodyDeclaration(selectedNode);
		if (decl == null) {
			return;
		}

		ASTNode enclosingNode = SurroundWithAnalyzer.getEnclosingNode(selectedNode);
		if (enclosingNode == null) {
			return;
		}

		ITypeBinding[] uncaughtExceptions = ExceptionAnalyzer.perform(enclosingNode, Selection.createFromStartLength(offset, length));
		if (uncaughtExceptions.length == 0) {
			return;
		}

		TryStatement surroundingTry = ASTResolving.findParentTryStatement(selectedNode);
		AST ast = astRoot.getAST();
		if (surroundingTry != null && (ASTNodes.isParent(selectedNode, surroundingTry.getBody()) || selectedNode.getLocationInParent() == TryStatement.RESOURCES_PROPERTY)) {
			{
				ASTRewrite rewrite = ASTRewrite.create(surroundingTry.getAST());

				String label = CorrectionMessages.LocalCorrectionsSubProcessor_addadditionalcatch_description;
				LinkedCorrectionProposal proposal = new LinkedCorrectionProposal(label, CodeActionKind.QuickFix, cu, rewrite, IProposalRelevance.ADD_ADDITIONAL_CATCH);

				ImportRewrite imports = proposal.createImportRewrite(context.getASTRoot());
				ImportRewriteContext importRewriteContext = new ContextSensitiveImportRewriteContext(decl, imports);

				CodeScopeBuilder.Scope scope = CodeScopeBuilder.perform(decl, Selection.createFromStartLength(offset, length)).findScope(offset, length);
				scope.setCursor(offset);

				ListRewrite clausesRewrite = rewrite.getListRewrite(surroundingTry, TryStatement.CATCH_CLAUSES_PROPERTY);
				for (int i = 0; i < uncaughtExceptions.length; i++) {
					ITypeBinding excBinding = uncaughtExceptions[i];
					String varName = StubUtility.getExceptionVariableName(cu.getJavaProject());
					String name = scope.createName(varName, false);
					SingleVariableDeclaration var = ast.newSingleVariableDeclaration();
					var.setName(ast.newSimpleName(name));
					var.setType(imports.addImport(excBinding, ast, importRewriteContext, TypeLocation.EXCEPTION));
					CatchClause newClause = ast.newCatchClause();
					newClause.setException(var);
					String catchBody = StubUtility.getCatchBodyContent(cu, excBinding.getName(), name, selectedNode, String.valueOf('\n'));
					if (catchBody != null) {
						ASTNode node = rewrite.createStringPlaceholder(catchBody, ASTNode.RETURN_STATEMENT);
						newClause.getBody().statements().add(node);
					}
					clausesRewrite.insertLast(newClause, null);

					String typeKey = "type" + i; //$NON-NLS-1$
					String nameKey = "name" + i; //$NON-NLS-1$
					proposal.addLinkedPosition(rewrite.track(var.getType()), false, typeKey);
					proposal.addLinkedPosition(rewrite.track(var.getName()), false, nameKey);
					addExceptionTypeLinkProposals(proposal, excBinding, typeKey);
				}
				proposals.add(proposal);
			}

			if (JavaModelUtil.is1d7OrHigher(cu.getJavaProject())) {
				List<CatchClause> catchClauses = surroundingTry.catchClauses();

				if (catchClauses != null && catchClauses.size() == 1) {
					List<ITypeBinding> filteredExceptions = SurroundWithTryCatchRefactoring.filterSubtypeExceptions(uncaughtExceptions);
					String label = filteredExceptions.size() > 1 ? CorrectionMessages.LocalCorrectionsSubProcessor_addexceptionstoexistingcatch_description
							: CorrectionMessages.LocalCorrectionsSubProcessor_addexceptiontoexistingcatch_description;
					ASTRewrite rewrite = ASTRewrite.create(ast);
					LinkedCorrectionProposal proposal = new LinkedCorrectionProposal(label, CodeActionKind.QuickFix, cu, rewrite, IProposalRelevance.ADD_EXCEPTIONS_TO_EXISTING_CATCH);
					ImportRewrite imports = proposal.createImportRewrite(context.getASTRoot());
					ImportRewriteContext importRewriteContext = new ContextSensitiveImportRewriteContext(decl, imports);

					CatchClause catchClause = catchClauses.get(0);
					Type type = catchClause.getException().getType();
					if (type instanceof UnionType unionType) {
						ListRewrite listRewrite = rewrite.getListRewrite(unionType, UnionType.TYPES_PROPERTY);
						for (int i = 0; i < filteredExceptions.size(); i++) {
							ITypeBinding excBinding = filteredExceptions.get(i);
							Type type2 = imports.addImport(excBinding, ast, importRewriteContext, TypeLocation.EXCEPTION);
							listRewrite.insertLast(type2, null);

							String typeKey = "type" + i; //$NON-NLS-1$
							proposal.addLinkedPosition(rewrite.track(type2), false, typeKey);
							addExceptionTypeLinkProposals(proposal, excBinding, typeKey);
						}
					} else {
						UnionType newUnionType = ast.newUnionType();
						List<Type> types = newUnionType.types();

						types.add((Type) rewrite.createCopyTarget(type));
						for (int i = 0; i < filteredExceptions.size(); i++) {
							ITypeBinding excBinding = filteredExceptions.get(i);
							Type type2 = imports.addImport(excBinding, ast, importRewriteContext, TypeLocation.EXCEPTION);
							types.add(type2);

							String typeKey = "type" + i; //$NON-NLS-1$
							proposal.addLinkedPosition(rewrite.track(type2), false, typeKey);
							addExceptionTypeLinkProposals(proposal, excBinding, typeKey);
						}
						rewrite.replace(type, newUnionType, null);
					}
					proposals.add(proposal);
				} else if (catchClauses != null && catchClauses.size() == 0) {
					List<ITypeBinding> filteredExceptions = SurroundWithTryCatchRefactoring.filterSubtypeExceptions(uncaughtExceptions);
					if (filteredExceptions.size() > 1) {
						String label = CorrectionMessages.LocalCorrectionsSubProcessor_addadditionalmulticatch_description;
						ASTRewrite rewrite = ASTRewrite.create(ast);
						LinkedCorrectionProposal proposal = new LinkedCorrectionProposal(label, CodeActionKind.QuickFix, cu, rewrite, IProposalRelevance.ADD_ADDITIONAL_MULTI_CATCH);
						ImportRewrite imports = proposal.createImportRewrite(context.getASTRoot());
						ImportRewriteContext importRewriteContext = new ContextSensitiveImportRewriteContext(decl, imports);

						CodeScopeBuilder.Scope scope = CodeScopeBuilder.perform(decl, Selection.createFromStartLength(offset, length)).findScope(offset, length);
						scope.setCursor(offset);

						CatchClause newCatchClause = ast.newCatchClause();
						String varName = StubUtility.getExceptionVariableName(cu.getJavaProject());
						String name = scope.createName(varName, false);
						SingleVariableDeclaration var = ast.newSingleVariableDeclaration();
						var.setName(ast.newSimpleName(name));

						UnionType newUnionType = ast.newUnionType();
						List<Type> types = newUnionType.types();

						for (int i = 0; i < filteredExceptions.size(); i++) {
							ITypeBinding excBinding = filteredExceptions.get(i);
							Type type2 = imports.addImport(excBinding, ast, importRewriteContext, TypeLocation.EXCEPTION);
							types.add(type2);

							String typeKey = "type" + i; //$NON-NLS-1$
							proposal.addLinkedPosition(rewrite.track(type2), false, typeKey);
							addExceptionTypeLinkProposals(proposal, excBinding, typeKey);
						}
						String nameKey = "name"; //$NON-NLS-1$
						proposal.addLinkedPosition(rewrite.track(var.getName()), false, nameKey);
						var.setType(newUnionType);
						newCatchClause.setException(var);
						String catchBody = StubUtility.getCatchBodyContent(cu, "Exception", name, selectedNode, String.valueOf('\n')); //$NON-NLS-1$
						if (catchBody != null) {
							ASTNode node = rewrite.createStringPlaceholder(catchBody, ASTNode.RETURN_STATEMENT);
							newCatchClause.getBody().statements().add(node);
						}
						ListRewrite listRewrite = rewrite.getListRewrite(surroundingTry, TryStatement.CATCH_CLAUSES_PROPERTY);
						listRewrite.insertFirst(newCatchClause, null);
						proposals.add(proposal);
					}
				}
			}
		}

		//Add throws declaration
		if (enclosingNode instanceof MethodDeclaration methodDecl) {
			IMethodBinding binding = methodDecl.resolveBinding();
			boolean isApplicable = (binding != null);
			if (isApplicable) {
				IMethodBinding overriddenMethod = Bindings.findOverriddenMethod(binding, true);
				if (overriddenMethod != null) {
					isApplicable = overriddenMethod.getDeclaringClass().isFromSource();
					if (!isApplicable) { // bug 349051
						ITypeBinding[] exceptionTypes = overriddenMethod.getExceptionTypes();
						ArrayList<ITypeBinding> unhandledExceptions = new ArrayList<>(uncaughtExceptions.length);
						for (int i = 0; i < uncaughtExceptions.length; i++) {
							ITypeBinding curr = uncaughtExceptions[i];
							if (isSubtype(curr, exceptionTypes)) {
								unhandledExceptions.add(curr);
							}
						}
						uncaughtExceptions = unhandledExceptions.toArray(new ITypeBinding[unhandledExceptions.size()]);
						isApplicable |= uncaughtExceptions.length > 0;
					}
				}
			}
			if (isApplicable) {
				ITypeBinding[] methodExceptions = binding.getExceptionTypes();
				ArrayList<ITypeBinding> unhandledExceptions = new ArrayList<>(uncaughtExceptions.length);
				for (int i = 0; i < uncaughtExceptions.length; i++) {
					ITypeBinding curr = uncaughtExceptions[i];
					if (!isSubtype(curr, methodExceptions)) {
						unhandledExceptions.add(curr);
					}
				}
				uncaughtExceptions = unhandledExceptions.toArray(new ITypeBinding[unhandledExceptions.size()]);

				List<Type> exceptions = methodDecl.thrownExceptionTypes();
				int nExistingExceptions = exceptions.size();
				ChangeDescription[] desc = new ChangeDescription[nExistingExceptions + uncaughtExceptions.length];
				for (int i = 0; i < exceptions.size(); i++) {
					Type elem = exceptions.get(i);
					if (isSubtype(elem.resolveBinding(), uncaughtExceptions)) {
						desc[i] = new RemoveDescription();
					}
				}
				for (int i = 0; i < uncaughtExceptions.length; i++) {
					desc[i + nExistingExceptions] = new InsertDescription(uncaughtExceptions[i], ""); //$NON-NLS-1$
				}

				String label = CorrectionMessages.LocalCorrectionsSubProcessor_addthrows_description;

				ChangeMethodSignatureProposal proposal = new ChangeMethodSignatureProposal(label, cu, astRoot, binding, null, desc, IProposalRelevance.ADD_THROWS_DECLARATION);
				for (int i = 0; i < uncaughtExceptions.length; i++) {
					addExceptionTypeLinkProposals(proposal, uncaughtExceptions[i], proposal.getExceptionTypeGroupId(i + nExistingExceptions));
				}
				proposals.add(proposal);
			}
		}
	}

	private static void addExceptionTypeLinkProposals(LinkedCorrectionProposal proposal, ITypeBinding exc, String key) {
		// all super classes except Object
		while (exc != null && !"java.lang.Object".equals(exc.getQualifiedName())) { //$NON-NLS-1$
			proposal.addLinkedPositionProposal(key, exc);
			exc = exc.getSuperclass();
		}
	}

	private static boolean isSubtype(ITypeBinding curr, ITypeBinding[] addedExceptions) {
		while (curr != null) {
			for (int i = 0; i < addedExceptions.length; i++) {
				if (curr == addedExceptions[i]) {
					return true;
				}
			}
			curr = curr.getSuperclass();
		}
		return false;
	}

	public static void addUnreachableCatchProposals(IInvocationContext context, IProblemLocationCore problem, Collection<ChangeCorrectionProposal> proposals) {
		ASTNode selectedNode = problem.getCoveringNode(context.getASTRoot());
		if (selectedNode == null) {
			return;
		}

		QuickAssistProcessor.getCatchClauseToThrowsProposals(context, selectedNode, proposals);
	}

	public static void addUnimplementedMethodsProposals(IInvocationContext context, IProblemLocationCore problem, Collection<ChangeCorrectionProposal> proposals) {
		IProposableFix fix = UnimplementedCodeFixCore.createAddUnimplementedMethodsFix(context.getASTRoot(), problem);

		if (fix != null) {
			try {
				CompilationUnitChange change = fix.createChange(null);
				CUCorrectionProposal proposal = new CUCorrectionProposal(change.getName(), CodeActionKind.QuickFix, change.getCompilationUnit(), change, IProposalRelevance.ADD_UNIMPLEMENTED_METHODS);
				proposals.add(proposal);
			} catch (CoreException e) {
				JavaLanguageServerPlugin.log(e);
			}
		}
	}

	public static void addUnusedMemberProposal(IInvocationContext context, IProblemLocationCore problem, Collection<ChangeCorrectionProposal> proposals) {
		int problemId = problem.getProblemId();

		UnusedCodeFixCore fix = UnusedCodeFixCore.createUnusedMemberFix(context.getASTRoot(), problem, false);
		if (fix != null) {
			try {
				CompilationUnitChange change = fix.createChange(null);
				CUCorrectionProposal proposal = new CUCorrectionProposal(change.getName(), CodeActionKind.QuickFix, change.getCompilationUnit(), change, IProposalRelevance.UNUSED_MEMBER);
				proposals.add(proposal);
			} catch (CoreException e) {
				JavaLanguageServerPlugin.log(e);
			}
		}

		if (problemId == IProblem.LocalVariableIsNeverUsed) {
			fix = UnusedCodeFixCore.createUnusedMemberFix(context.getASTRoot(), problem, true);
			if (fix != null) {
				try {
					CompilationUnitChange change = fix.createChange(null);
					CUCorrectionProposal proposal = new CUCorrectionProposal(change.getName(), CodeActionKind.QuickFix, change.getCompilationUnit(), change, IProposalRelevance.UNUSED_MEMBER);
					proposals.add(proposal);
				} catch (CoreException e) {
					JavaLanguageServerPlugin.log(e);
				}
			}
		}

		if (problemId == IProblem.ArgumentIsNeverUsed) {
			JavadocTagsSubProcessor.getUnusedAndUndocumentedParameterOrExceptionProposals(context, problem, proposals);
			fix = UnusedCodeFixCore.createUnusedParameterFix(context.getASTRoot(), problem);
			if (fix != null) {
				try {
					CompilationUnitChange change = fix.createChange(null);
					CUCorrectionProposal proposal = new CUCorrectionProposal(change.getName(), CodeActionKind.QuickFix, change.getCompilationUnit(), change, IProposalRelevance.UNUSED_MEMBER);
					proposals.add(proposal);
				} catch (CoreException e) {
					JavaLanguageServerPlugin.log(e);
				}
			}
		}
	}

	public static void getUnreachableCodeProposals(IInvocationContext context, IProblemLocationCore problem, Collection<ChangeCorrectionProposal> proposals) {
		CompilationUnit root = context.getASTRoot();
		ASTNode selectedNode = problem.getCoveringNode(root);
		if (selectedNode == null) {
			return;
		}

		ASTNode parent = selectedNode.getParent();
		while (parent instanceof ExpressionStatement) {
			selectedNode = parent;
			parent = selectedNode.getParent();
		}

		if (parent instanceof WhileStatement) {
			addRemoveIncludingConditionProposal(context, parent, null, proposals);

		} else if (selectedNode.getLocationInParent() == IfStatement.THEN_STATEMENT_PROPERTY) {
			Statement elseStatement = ((IfStatement) parent).getElseStatement();
			addRemoveIncludingConditionProposal(context, parent, elseStatement, proposals);

		} else if (selectedNode.getLocationInParent() == IfStatement.ELSE_STATEMENT_PROPERTY) {
			Statement thenStatement = ((IfStatement) parent).getThenStatement();
			addRemoveIncludingConditionProposal(context, parent, thenStatement, proposals);

		} else if (selectedNode.getLocationInParent() == ForStatement.BODY_PROPERTY) {
			Statement body = ((ForStatement) parent).getBody();
			addRemoveIncludingConditionProposal(context, parent, body, proposals);

		} else if (selectedNode.getLocationInParent() == ConditionalExpression.THEN_EXPRESSION_PROPERTY) {
			Expression elseExpression = ((ConditionalExpression) parent).getElseExpression();
			addRemoveIncludingConditionProposal(context, parent, elseExpression, proposals);

		} else if (selectedNode.getLocationInParent() == ConditionalExpression.ELSE_EXPRESSION_PROPERTY) {
			Expression thenExpression = ((ConditionalExpression) parent).getThenExpression();
			addRemoveIncludingConditionProposal(context, parent, thenExpression, proposals);

		} else if (selectedNode.getLocationInParent() == InfixExpression.RIGHT_OPERAND_PROPERTY) {
			// also offer split && / || condition proposals:
			InfixExpression infixExpression = (InfixExpression) parent;
			Expression leftOperand = infixExpression.getLeftOperand();

			ASTRewrite rewrite = ASTRewrite.create(parent.getAST());

			Expression replacement = leftOperand;
			while (replacement instanceof ParenthesizedExpression expression) {
				replacement = expression.getExpression();
			}

			Expression toReplace = infixExpression;
			while (toReplace.getLocationInParent() == ParenthesizedExpression.EXPRESSION_PROPERTY) {
				toReplace = (Expression) toReplace.getParent();
			}

			if (NecessaryParenthesesChecker.needsParentheses(replacement, toReplace.getParent(), toReplace.getLocationInParent())) {
				if (leftOperand instanceof ParenthesizedExpression) {
					replacement = (Expression) replacement.getParent();
				} else if (infixExpression.getLocationInParent() == ParenthesizedExpression.EXPRESSION_PROPERTY) {
					toReplace = ((ParenthesizedExpression) toReplace).getExpression();
				}
			}

			rewrite.replace(toReplace, rewrite.createMoveTarget(replacement), null);

			String label = CorrectionMessages.LocalCorrectionsSubProcessor_removeunreachablecode_description;
			addRemoveProposal(context, rewrite, label, proposals);

			InnovationContext assistContext = new InnovationContext(context.getCompilationUnit(), infixExpression.getRightOperand().getStartPosition() - 1, 0);
			assistContext.setASTRoot(root);
			InvertBooleanUtility.getSplitAndConditionProposals(assistContext, infixExpression, proposals);
			InvertBooleanUtility.getSplitOrConditionProposals(assistContext, infixExpression, proposals);

		} else if (selectedNode instanceof Statement && selectedNode.getLocationInParent().isChildListProperty()) {
			// remove all statements following the unreachable:
			List<Statement> statements = ASTNodes.<Statement>getChildListProperty(selectedNode.getParent(), (ChildListPropertyDescriptor) selectedNode.getLocationInParent());
			int idx = statements.indexOf(selectedNode);

			ASTRewrite rewrite = ASTRewrite.create(selectedNode.getAST());
			String label = CorrectionMessages.LocalCorrectionsSubProcessor_removeunreachablecode_description;

			if (idx > 0) {
				Object prevStatement = statements.get(idx - 1);
				if (prevStatement instanceof IfStatement ifStatement) {
					if (ifStatement.getElseStatement() == null) {
						// remove if (true), see https://bugs.eclipse.org/bugs/show_bug.cgi?id=261519
						rewrite.replace(ifStatement, rewrite.createMoveTarget(ifStatement.getThenStatement()), null);
						label = CorrectionMessages.LocalCorrectionsSubProcessor_removeunreachablecode_including_condition_description;
					}
				}
			}

			for (int i = idx; i < statements.size(); i++) {
				ASTNode statement = statements.get(i);
				if (statement instanceof SwitchCase) {
					break; // stop at case *: and default:
				}
				rewrite.remove(statement, null);
			}

			addRemoveProposal(context, rewrite, label, proposals);

		} else {
			// no special case, just remove the node:
			addRemoveProposal(context, selectedNode, proposals);
		}
	}

	private static void addRemoveProposal(IInvocationContext context, ASTNode selectedNode, Collection<ChangeCorrectionProposal> proposals) {
		ASTRewrite rewrite = ASTRewrite.create(selectedNode.getAST());
		rewrite.remove(selectedNode, null);

		String label = CorrectionMessages.LocalCorrectionsSubProcessor_removeunreachablecode_description;
		addRemoveProposal(context, rewrite, label, proposals);
	}

	private static void addRemoveIncludingConditionProposal(IInvocationContext context, ASTNode toRemove, ASTNode replacement, Collection<ChangeCorrectionProposal> proposals) {
		String label = CorrectionMessages.LocalCorrectionsSubProcessor_removeunreachablecode_including_condition_description;
		AST ast = toRemove.getAST();
		ASTRewrite rewrite = ASTRewrite.create(ast);
		ASTRewriteCorrectionProposal proposal = new ASTRewriteCorrectionProposal(label, CodeActionKind.QuickFix, context.getCompilationUnit(), rewrite, IProposalRelevance.REMOVE_UNREACHABLE_CODE_INCLUDING_CONDITION);

		if (replacement == null || replacement instanceof EmptyStatement || replacement instanceof Block && ((Block) replacement).statements().size() == 0) {
			if (ASTNodes.isControlStatementBody(toRemove.getLocationInParent())) {
				rewrite.replace(toRemove, toRemove.getAST().newBlock(), null);
			} else {
				rewrite.remove(toRemove, null);
			}

		} else if (toRemove instanceof Expression toRemoveExpression && replacement instanceof Expression replacementExpression) {
			Expression moved = (Expression) rewrite.createMoveTarget(replacement);
			ITypeBinding explicitCast = ASTNodes.getExplicitCast(replacementExpression, toRemoveExpression);
			if (explicitCast != null) {
				CastExpression cast = ast.newCastExpression();
				if (NecessaryParenthesesChecker.needsParentheses(replacementExpression, cast, CastExpression.EXPRESSION_PROPERTY)) {
					ParenthesizedExpression parenthesized = ast.newParenthesizedExpression();
					parenthesized.setExpression(moved);
					moved = parenthesized;
				}
				cast.setExpression(moved);
				ImportRewrite imports = proposal.createImportRewrite(context.getASTRoot());
				ImportRewriteContext importRewriteContext = new ContextSensitiveImportRewriteContext(toRemove, imports);
				cast.setType(imports.addImport(explicitCast, ast, importRewriteContext, TypeLocation.CAST));
				moved = cast;
			}
			rewrite.replace(toRemove, moved, null);

		} else {
			ASTNode parent = toRemove.getParent();
			ASTNode moveTarget;
			if ((parent instanceof Block || parent instanceof SwitchStatement) && replacement instanceof Block block) {
				ListRewrite listRewrite = rewrite.getListRewrite(replacement, Block.STATEMENTS_PROPERTY);
				List<Statement> list = block.statements();
				int lastIndex = list.size() - 1;
				moveTarget = listRewrite.createMoveTarget(list.get(0), list.get(lastIndex));
			} else {
				moveTarget = rewrite.createMoveTarget(replacement);
			}

			rewrite.replace(toRemove, moveTarget, null);
		}

		proposals.add(proposal);
	}

	private static void addRemoveProposal(IInvocationContext context, ASTRewrite rewrite, String label, Collection<ChangeCorrectionProposal> proposals) {
		ASTRewriteCorrectionProposal proposal = new ASTRewriteCorrectionProposal(label, CodeActionKind.QuickFix, context.getCompilationUnit(), rewrite, 10);
		proposals.add(proposal);
	}

	public static void addConstructorFromSuperclassProposal(IInvocationContext context, IProblemLocationCore problem, Collection<ChangeCorrectionProposal> proposals) throws CoreException {
		ASTNode selectedNode = problem.getCoveringNode(context.getASTRoot());
		if (selectedNode == null) {
			return;
		}

		TypeDeclaration typeDeclaration = null;
		if (selectedNode.getLocationInParent() == TypeDeclaration.NAME_PROPERTY) {
			typeDeclaration = (TypeDeclaration) selectedNode.getParent();
		} else {
			BodyDeclaration declaration = ASTResolving.findParentBodyDeclaration(selectedNode);
			if (declaration instanceof Initializer && problem.getProblemId() == IProblem.UnhandledExceptionInDefaultConstructor) {
				addUncaughtExceptionProposals(context, problem, proposals);
			}
			return;
		}

		ITypeBinding binding = typeDeclaration.resolveBinding();
		if (binding == null || binding.getSuperclass() == null) {
			return;
		}
		ICompilationUnit cu = context.getCompilationUnit();
		IMethodBinding[] methods = binding.getSuperclass().getDeclaredMethods();
		for (int i = 0; i < methods.length; i++) {
			IMethodBinding curr = methods[i];
			if (curr.isConstructor() && !Modifier.isPrivate(curr.getModifiers())) {
				proposals.add(new ConstructorFromSuperclassProposal(cu, typeDeclaration, curr, IProposalRelevance.ADD_CONSTRUCTOR_FROM_SUPER_CLASS));
			}
		}
	}


	public static void addUnnecessaryCastProposal(IInvocationContext context, IProblemLocationCore problem, Collection<ChangeCorrectionProposal> proposals) {
		IProposableFix fix = UnusedCodeFixCore.createRemoveUnusedCastFix(context.getASTRoot(), problem);
		if (fix != null) {
			Map<String, String> options = new Hashtable<>();
			options.put(CleanUpConstants.REMOVE_UNNECESSARY_CASTS, CleanUpOptionsCore.TRUE);
			FixCorrectionProposal proposal = new FixCorrectionProposal(fix, new UnnecessaryCodeCleanUpCore(options), IProposalRelevance.REMOVE_UNUSED_CAST, context);
			proposals.add(proposal);
		}
	}

	/*
	 * Fix instance accesses and indirect (static) accesses to static fields/methods
	 */
	public static void addCorrectAccessToStaticProposals(IInvocationContext context, IProblemLocationCore problem, Collection<ChangeCorrectionProposal> proposals) throws CoreException {
		IProposableFix fix = CodeStyleFixCore.createIndirectAccessToStaticFix(context.getASTRoot(), problem);
		if (fix != null) {

			Map<String, String> options = new HashMap<>();
			options.put(CleanUpConstants.MEMBER_ACCESSES_STATIC_QUALIFY_WITH_DECLARING_CLASS, CleanUpOptionsCore.TRUE);
			options.put(CleanUpConstants.MEMBER_ACCESSES_STATIC_QUALIFY_WITH_DECLARING_CLASS_SUBTYPE_ACCESS, CleanUpOptionsCore.TRUE);
			FixCorrectionProposal proposal = new FixCorrectionProposal(fix, new CodeStyleCleanUpCore(options), IProposalRelevance.CREATE_INDIRECT_ACCESS_TO_STATIC, context);
			//proposal.setCommandId(ADD_STATIC_ACCESS_ID);
			proposals.add(proposal);
			return;
		}

		IProposableFix[] fixes = CodeStyleFixCore.createNonStaticAccessFixes(context.getASTRoot(), problem);
		if (fixes != null) {
			IProposableFix fix1 = fixes[0];
			Map<String, String> options = new HashMap<>();
			options.put(CleanUpConstants.MEMBER_ACCESSES_STATIC_QUALIFY_WITH_DECLARING_CLASS, CleanUpOptionsCore.TRUE);
			options.put(CleanUpConstants.MEMBER_ACCESSES_STATIC_QUALIFY_WITH_DECLARING_CLASS_INSTANCE_ACCESS, CleanUpOptionsCore.TRUE);
			FixCorrectionProposal proposal = new FixCorrectionProposal(fix1, new CodeStyleCleanUpCore(options), IProposalRelevance.CREATE_NON_STATIC_ACCESS_USING_DECLARING_TYPE, context, CodeActionKind.QuickFix);
			//proposal.setCommandId(ADD_STATIC_ACCESS_ID);
			proposals.add(proposal);

			if (fixes.length > 1) {
				Map<String, String> options1 = new HashMap<>();
				options1.put(CleanUpConstants.MEMBER_ACCESSES_STATIC_QUALIFY_WITH_DECLARING_CLASS, CleanUpOptionsCore.TRUE);
				options1.put(CleanUpConstants.MEMBER_ACCESSES_STATIC_QUALIFY_WITH_DECLARING_CLASS_SUBTYPE_ACCESS, CleanUpOptionsCore.TRUE);
				options1.put(CleanUpConstants.MEMBER_ACCESSES_STATIC_QUALIFY_WITH_DECLARING_CLASS_INSTANCE_ACCESS, CleanUpOptionsCore.TRUE);
				IProposableFix fix2 = fixes[1];
				proposal = new FixCorrectionProposal(fix2, new CodeStyleCleanUpCore(options), IProposalRelevance.CREATE_NON_STATIC_ACCESS_USING_INSTANCE_TYPE, context, CodeActionKind.QuickFix);
				proposals.add(proposal);
			}
		}
		ModifierCorrectionSubProcessor.addNonAccessibleReferenceProposal(context, problem, proposals, ModifierCorrectionSubProcessor.TO_NON_STATIC, IProposalRelevance.REMOVE_STATIC_MODIFIER);
	}

	public static void addRedundantSuperInterfaceProposal(IInvocationContext context, IProblemLocationCore problem, Collection<ChangeCorrectionProposal> proposals) {
		ASTNode selectedNode = problem.getCoveringNode(context.getASTRoot());
		if (!(selectedNode instanceof Name)) {
			return;
		}
		ASTNode node = ASTNodes.getNormalizedNode(selectedNode);
		ASTRewrite rewrite = ASTRewrite.create(node.getAST());
		rewrite.remove(node, null);
		String label = CorrectionMessages.LocalCorrectionsSubProcessor_remove_redundant_superinterface;
		ASTRewriteCorrectionProposal proposal = new ASTRewriteCorrectionProposal(label, CodeActionKind.QuickFix, context.getCompilationUnit(), rewrite, IProposalRelevance.REMOVE_REDUNDANT_SUPER_INTERFACE);
		proposals.add(proposal);
	}

	public static void getMissingEnumConstantCaseProposals(IInvocationContext context, IProblemLocationCore problem, Collection<ChangeCorrectionProposal> proposals) {
		for (ChangeCorrectionProposal proposal : proposals) {
			if (CorrectionMessages.LocalCorrectionsSubProcessor_add_missing_cases_description.equals(proposal.getName())) {
				return;
			}
		}

		ASTNode selectedNode = problem.getCoveringNode(context.getASTRoot());
		if (selectedNode instanceof Expression) {
			StructuralPropertyDescriptor locationInParent = selectedNode.getLocationInParent();
			ASTNode parent = selectedNode.getParent();
			ITypeBinding binding;
			List<Statement> statements;

			if (locationInParent == SwitchStatement.EXPRESSION_PROPERTY) {
				SwitchStatement statement = (SwitchStatement) parent;
				binding = statement.getExpression().resolveTypeBinding();
				statements = statement.statements();
			} else if (locationInParent == SwitchExpression.EXPRESSION_PROPERTY) {
				SwitchExpression switchExpression = (SwitchExpression) parent;
				binding = switchExpression.getExpression().resolveTypeBinding();
				statements = switchExpression.statements();
			} else {
				return;
			}

			if (binding == null || !binding.isEnum()) {
				return;
			}

			ArrayList<String> missingEnumCases = new ArrayList<>();
			boolean hasDefault = evaluateMissingSwitchCases(binding, statements, missingEnumCases);
			if (missingEnumCases.size() == 0 && hasDefault) {
				return;
			}

			createMissingCaseProposals(context, parent, missingEnumCases, proposals);
		}
	}

	@SuppressWarnings("deprecation")
	public static boolean evaluateMissingSwitchCases(ITypeBinding enumBindings, List<Statement> switchStatements, ArrayList<String> enumConstNames) {
		for (IVariableBinding field : enumBindings.getDeclaredFields()) {
			if (field.isEnumConstant()) {
				enumConstNames.add(field.getName());
			}
		}

		boolean hasDefault = false;
		List<Statement> statements = switchStatements;
		for (int i = 0; i < statements.size(); i++) {
			Statement curr = statements.get(i);
			if (curr instanceof SwitchCase switchCase) {
				if (ASTHelper.isSwitchCaseExpressionsSupportedInAST(switchCase.getAST())) {
					List<Expression> expressions = switchCase.expressions();
					if (expressions.size() == 0) {
						hasDefault = true;
					} else {
						for (Expression expression : expressions) {
							if (expression instanceof SimpleName simpleName) {
								enumConstNames.remove(simpleName.getFullyQualifiedName());
							}
						}
					}
				} else {
					Expression expression = switchCase.getExpression();
					if (expression instanceof SimpleName simpleName) {
						enumConstNames.remove(simpleName.getFullyQualifiedName());
					} else if (expression == null) {
						hasDefault = true;
					}
				}
			}
		}
		return hasDefault;
	}

	@SuppressWarnings("deprecation")
	public static void createMissingCaseProposals(IInvocationContext context, ASTNode parent, ArrayList<String> enumConstNames, Collection<ChangeCorrectionProposal> proposals) {
		List<Statement> statements;
		Expression expression;
		if (parent instanceof SwitchStatement switchStatement) {
			statements = switchStatement.statements();
			expression = switchStatement.getExpression();
		} else if (parent instanceof SwitchExpression switchExpression) {
			statements = switchExpression.statements();
			expression = switchExpression.getExpression();
		} else {
			return;
		}
		int defaultIndex = statements.size();
		for (int i = 0; i < statements.size(); i++) {
			Statement curr = statements.get(i);
			if (curr instanceof SwitchCase switchCase) {
				if (ASTHelper.isSwitchCaseExpressionsSupportedInAST(switchCase.getAST())) {
					if (switchCase.expressions().size() == 0) {
						defaultIndex = i;
						break;
					}
				} else if (switchCase.getExpression() == null) {
					defaultIndex = i;
					break;
				}
			}
		}
		boolean hasDefault = defaultIndex < statements.size();

		AST ast = parent.getAST();

		if (enumConstNames.size() > 0) {
			ASTRewrite astRewrite = ASTRewrite.create(ast);
			ListRewrite listRewrite;
			if (parent instanceof SwitchStatement) {
				listRewrite = astRewrite.getListRewrite(parent, SwitchStatement.STATEMENTS_PROPERTY);
			} else {
				listRewrite = astRewrite.getListRewrite(parent, SwitchExpression.STATEMENTS_PROPERTY);
			}

			String label = CorrectionMessages.LocalCorrectionsSubProcessor_add_missing_cases_description;
			LinkedCorrectionProposal proposal = new LinkedCorrectionProposal(label, CodeActionKind.QuickFix, context.getCompilationUnit(), astRewrite, IProposalRelevance.ADD_MISSING_CASE_STATEMENTS);

			for (int i = 0; i < enumConstNames.size(); i++) {
				SwitchCase newSwitchCase = ast.newSwitchCase();
				String enumConstName = enumConstNames.get(i);
				Name newName = ast.newName(enumConstName);
				if (ASTHelper.isSwitchCaseExpressionsSupportedInAST(ast)) {
					newSwitchCase.expressions().add(newName);
				} else {
					newSwitchCase.setExpression(newName);
				}
				listRewrite.insertAt(newSwitchCase, defaultIndex, null);
				defaultIndex++;
				if (!hasDefault) {
					if (ASTHelper.isSwitchExpressionNodeSupportedInAST(ast)) {
						if (statements.size() > 0) {
							Statement firstStatement = statements.get(0);
							SwitchCase switchCase = (SwitchCase) firstStatement;
							boolean isArrow = switchCase.isSwitchLabeledRule();
							newSwitchCase.setSwitchLabeledRule(isArrow);
							if (isArrow || parent instanceof SwitchExpression) {
								ThrowStatement newThrowStatement = getThrowForUnsupportedCase(expression, ast, astRewrite);
								listRewrite.insertLast(newThrowStatement, null);
								proposal.addLinkedPosition(astRewrite.track(newThrowStatement), true, enumConstName);
							} else {
								listRewrite.insertAt(ast.newBreakStatement(), defaultIndex, null);
							}
						} else {
							listRewrite.insertAt(ast.newBreakStatement(), defaultIndex, null);
						}
					} else {
						listRewrite.insertAt(ast.newBreakStatement(), defaultIndex, null);
					}

					defaultIndex++;
				}
			}
			if (!hasDefault) {
				SwitchCase newSwitchCase = ast.newSwitchCase();
				listRewrite.insertAt(newSwitchCase, defaultIndex, null);
				defaultIndex++;

				if (ASTHelper.isSwitchExpressionNodeSupportedInAST(ast)) {
					if (statements.size() > 0) {
						Statement firstStatement = statements.get(0);
						SwitchCase switchCase = (SwitchCase) firstStatement;
						boolean isArrow = switchCase.isSwitchLabeledRule();
						newSwitchCase.setSwitchLabeledRule(isArrow);
						if (isArrow || parent instanceof SwitchExpression) {
							ThrowStatement newThrowStatement = getThrowForUnexpectedDefault(expression, ast, astRewrite);
							listRewrite.insertLast(newThrowStatement, null);
							proposal.addLinkedPosition(astRewrite.track(newThrowStatement), true, "defaultCase"); //$NON-NLS-1$
						} else {
							listRewrite.insertAt(ast.newBreakStatement(), defaultIndex, null);
						}
					} else {
						listRewrite.insertAt(ast.newBreakStatement(), defaultIndex, null);
					}
				} else {
					newSwitchCase.setExpression(null);
					listRewrite.insertAt(ast.newBreakStatement(), defaultIndex, null);
				}
			}
			proposals.add(proposal);
		}
		if (!hasDefault) {
			createMissingDefaultProposal(context, parent, proposals);
		}
	}

	private static ThrowStatement getThrowForUnsupportedCase(Expression switchExpr, AST ast, ASTRewrite astRewrite) {
		ThrowStatement newThrowStatement = ast.newThrowStatement();
		ClassInstanceCreation newCic = ast.newClassInstanceCreation();
		newCic.setType(ast.newSimpleType(ast.newSimpleName("UnsupportedOperationException"))); //$NON-NLS-1$
		InfixExpression newInfixExpr = ast.newInfixExpression();
		StringLiteral newStringLiteral = ast.newStringLiteral();
		newStringLiteral.setLiteralValue("Unimplemented case: "); //$NON-NLS-1$
		newInfixExpr.setLeftOperand(newStringLiteral);
		newInfixExpr.setOperator(InfixExpression.Operator.PLUS);
		newInfixExpr.setRightOperand((Expression) astRewrite.createCopyTarget(switchExpr));
		newCic.arguments().add(newInfixExpr);
		newThrowStatement.setExpression(newCic);
		return newThrowStatement;
	}

	public static void addMissingDefaultCaseProposal(IInvocationContext context, IProblemLocationCore problem, Collection<ChangeCorrectionProposal> proposals) {
		ASTNode selectedNode = problem.getCoveringNode(context.getASTRoot());
		if (selectedNode instanceof Expression) {
			StructuralPropertyDescriptor locationInParent = selectedNode.getLocationInParent();
			ASTNode parent = selectedNode.getParent();
			List<Statement> statements;

			if (locationInParent == SwitchStatement.EXPRESSION_PROPERTY) {
				statements = ((SwitchStatement) parent).statements();
			} else if (locationInParent == SwitchExpression.EXPRESSION_PROPERTY) {
				statements = ((SwitchExpression) parent).statements();
			} else {
				return;
			}

			for (Statement statement : statements) {
				if (statement instanceof SwitchCase switchCase && switchCase.isDefault()) {
					return;
				}
			}
			createMissingDefaultProposal(context, parent, proposals);
		}
	}

	@SuppressWarnings("deprecation")
	private static void createMissingDefaultProposal(IInvocationContext context, ASTNode parent, Collection<ChangeCorrectionProposal> proposals) {
		List<Statement> statements;
		Expression expression;
		if (parent instanceof SwitchStatement switchStatement) {
			statements = switchStatement.statements();
			expression = switchStatement.getExpression();
		} else if (parent instanceof SwitchExpression switchExpression) {
			statements = switchExpression.statements();
			expression = switchExpression.getExpression();
		} else {
			return;
		}
		AST ast = parent.getAST();
		ASTRewrite astRewrite = ASTRewrite.create(ast);
		ListRewrite listRewrite;
		if (parent instanceof SwitchStatement) {
			listRewrite = astRewrite.getListRewrite(parent, SwitchStatement.STATEMENTS_PROPERTY);
		} else {
			listRewrite = astRewrite.getListRewrite(parent, SwitchExpression.STATEMENTS_PROPERTY);
		}
		String label = CorrectionMessages.LocalCorrectionsSubProcessor_add_default_case_description;
		LinkedCorrectionProposal proposal = new LinkedCorrectionProposal(label, CodeActionKind.QuickFix, context.getCompilationUnit(), astRewrite, IProposalRelevance.ADD_MISSING_DEFAULT_CASE);

		SwitchCase newSwitchCase = ast.newSwitchCase();
		listRewrite.insertLast(newSwitchCase, null);

		if (ASTHelper.isSwitchCaseExpressionsSupportedInAST(ast)) {
			if (statements.size() > 0) {
				Statement firstStatement = statements.get(0);
				SwitchCase switchCase = (SwitchCase) firstStatement;
				boolean isArrow = switchCase.isSwitchLabeledRule();
				newSwitchCase.setSwitchLabeledRule(isArrow);
				if (isArrow || parent instanceof SwitchExpression) {
					ThrowStatement newThrowStatement = getThrowForUnexpectedDefault(expression, ast, astRewrite);
					listRewrite.insertLast(newThrowStatement, null);
					proposal.addLinkedPosition(astRewrite.track(newThrowStatement), true, null);
				} else {
					listRewrite.insertLast(ast.newBreakStatement(), null);
				}
			} else {
				listRewrite.insertLast(ast.newBreakStatement(), null);
			}
		} else {
			newSwitchCase.setExpression(null);
			listRewrite.insertLast(ast.newBreakStatement(), null);
		}

		proposals.add(proposal);
	}

	private static ThrowStatement getThrowForUnexpectedDefault(Expression switchExpression, AST ast, ASTRewrite astRewrite) {
		ThrowStatement newThrowStatement = ast.newThrowStatement();
		ClassInstanceCreation newCic = ast.newClassInstanceCreation();
		newCic.setType(ast.newSimpleType(ast.newSimpleName("IllegalArgumentException"))); //$NON-NLS-1$
		InfixExpression newInfixExpr = ast.newInfixExpression();
		StringLiteral newStringLiteral = ast.newStringLiteral();
		newStringLiteral.setLiteralValue("Unexpected value: "); //$NON-NLS-1$
		newInfixExpr.setLeftOperand(newStringLiteral);
		newInfixExpr.setOperator(InfixExpression.Operator.PLUS);
		newInfixExpr.setRightOperand((Expression) astRewrite.createCopyTarget(switchExpression));
		newCic.arguments().add(newInfixExpr);
		newThrowStatement.setExpression(newCic);
		return newThrowStatement;
	}

	public static void addCasesOmittedProposals(IInvocationContext context, IProblemLocationCore problem, Collection<ChangeCorrectionProposal> proposals) {
		ASTNode selectedNode = problem.getCoveringNode(context.getASTRoot());
		if (selectedNode instanceof Expression && selectedNode.getLocationInParent() == SwitchStatement.EXPRESSION_PROPERTY) {
			AST ast = selectedNode.getAST();
			SwitchStatement parent = (SwitchStatement) selectedNode.getParent();

			for (Statement statement : (List<Statement>) parent.statements()) {
				if (statement instanceof SwitchCase switchCase && switchCase.isDefault()) {

					// insert //$CASES-OMITTED$:
					ASTRewrite rewrite = ASTRewrite.create(ast);
					rewrite.setTargetSourceRangeComputer(new NoCommentSourceRangeComputer());
					ListRewrite listRewrite = rewrite.getListRewrite(parent, SwitchStatement.STATEMENTS_PROPERTY);
					ASTNode casesOmittedComment = rewrite.createStringPlaceholder("//$CASES-OMITTED$", ASTNode.EMPTY_STATEMENT); //$NON-NLS-1$
					listRewrite.insertBefore(casesOmittedComment, statement, null);

					String label = CorrectionMessages.LocalCorrectionsSubProcessor_insert_cases_omitted;
					ASTRewriteCorrectionProposal proposal = new ASTRewriteCorrectionProposal(label, CodeActionKind.QuickFix, context.getCompilationUnit(), rewrite, IProposalRelevance.INSERT_CASES_OMITTED);
					proposals.add(proposal);
					break;
				}
			}
		}
	}

	public static void getTryWithResourceProposals(IInvocationContext context, IProblemLocationCore problem, Collection<ChangeCorrectionProposal> proposals) {
		ASTNode coveringNode = problem.getCoveringNode(context.getASTRoot());
		if (coveringNode != null) {
			try {
				ArrayList<ASTNode> coveredNodes = QuickAssistProcessor.getFullyCoveredNodes(context, coveringNode);
				QuickAssistProcessor.getTryWithResourceProposals(context, coveringNode, coveredNodes, proposals);
			} catch (IllegalArgumentException | CoreException e) {
				JavaLanguageServerPlugin.logException(e);
			}
		}
	}

	public static void addTypeAsPermittedSubTypeProposal(IInvocationContext context, IProblemLocationCore problem, Collection<ChangeCorrectionProposal> proposals) {
		SealedClassFixCore fix = SealedClassFixCore.addTypeAsPermittedSubTypeProposal(context.getASTRoot(), problem);
		if (fix != null) {
			String label = fix.getDisplayString();
			CompilationUnitChange change;
			try {
				change = fix.createChange(null);

				ASTNode selectedNode = problem.getCoveringNode(context.getASTRoot());
				IType sealedType = SealedClassFixCore.getSealedType(selectedNode);
				ICompilationUnit unit = SealedClassFixCore.getCompilationUnitForSealedType(sealedType);
				CUCorrectionProposal proposal = new CUCorrectionProposal(label, CodeActionKind.QuickFix, unit, change, IProposalRelevance.DECLARE_SEALED_AS_DIRECT_SUPER_TYPE);
				proposals.add(proposal);
			} catch (CoreException e) {
				// do nothing
			}
		}
	}

	public static void addSealedAsDirectSuperTypeProposal(IInvocationContext context, IProblemLocationCore problem, Collection<ChangeCorrectionProposal> proposals) {
		SealedClassFixCore fix = SealedClassFixCore.addSealedAsDirectSuperTypeProposal(context.getASTRoot(), problem);
		if (fix != null) {
			String label = fix.getDisplayString();
			CompilationUnitChange change;
			try {
				change = fix.createChange(null);

				ASTNode selectedNode = problem.getCoveringNode(context.getASTRoot());
				IType permittedType = SealedClassFixCore.getPermittedType(selectedNode);
				ICompilationUnit unit = permittedType.getCompilationUnit();
				CUCorrectionProposal proposal = new CUCorrectionProposal(label, CodeActionKind.QuickFix, unit, change, IProposalRelevance.DECLARE_SEALED_AS_DIRECT_SUPER_TYPE);
				proposals.add(proposal);
			} catch (CoreException e) {
				// do nothing
			}
		}
	}

	public static void addValueForAnnotationProposals(IInvocationContext context, IProblemLocationCore problem, Collection<ChangeCorrectionProposal> proposals) {
		MissingAnnotationAttributesFixCore fix = MissingAnnotationAttributesFixCore.addMissingAnnotationAttributesProposal(context.getASTRoot(), problem);
		if (fix != null) {
			String label = fix.getDisplayString();
			CompilationUnitChange change;
			try {
				change = fix.createChange(null);
				CUCorrectionProposal proposal = new CUCorrectionProposal(label, CodeActionKind.QuickFix, context.getCompilationUnit(), change, IProposalRelevance.ADD_MISSING_ANNOTATION_ATTRIBUTES);
				proposals.add(proposal);
			} catch (CoreException e) {
				// do nothing
			}
		}
	}

}
