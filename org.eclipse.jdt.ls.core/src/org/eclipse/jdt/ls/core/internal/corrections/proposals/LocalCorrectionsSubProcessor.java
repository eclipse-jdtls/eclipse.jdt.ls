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
import java.util.List;
import java.util.Map;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.compiler.IProblem;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.Block;
import org.eclipse.jdt.core.dom.CastExpression;
import org.eclipse.jdt.core.dom.ChildListPropertyDescriptor;
import org.eclipse.jdt.core.dom.ClassInstanceCreation;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.ConditionalExpression;
import org.eclipse.jdt.core.dom.EmptyStatement;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.ExpressionStatement;
import org.eclipse.jdt.core.dom.ForStatement;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.IVariableBinding;
import org.eclipse.jdt.core.dom.IfStatement;
import org.eclipse.jdt.core.dom.InfixExpression;
import org.eclipse.jdt.core.dom.Name;
import org.eclipse.jdt.core.dom.ParenthesizedExpression;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.core.dom.Statement;
import org.eclipse.jdt.core.dom.StringLiteral;
import org.eclipse.jdt.core.dom.StructuralPropertyDescriptor;
import org.eclipse.jdt.core.dom.SwitchCase;
import org.eclipse.jdt.core.dom.SwitchExpression;
import org.eclipse.jdt.core.dom.SwitchStatement;
import org.eclipse.jdt.core.dom.ThrowStatement;
import org.eclipse.jdt.core.dom.WhileStatement;
import org.eclipse.jdt.core.dom.rewrite.ASTRewrite;
import org.eclipse.jdt.core.dom.rewrite.ImportRewrite;
import org.eclipse.jdt.core.dom.rewrite.ImportRewrite.ImportRewriteContext;
import org.eclipse.jdt.core.dom.rewrite.ImportRewrite.TypeLocation;
import org.eclipse.jdt.core.dom.rewrite.ListRewrite;
import org.eclipse.jdt.core.manipulation.CUCorrectionProposalCore;
import org.eclipse.jdt.core.manipulation.CleanUpOptionsCore;
import org.eclipse.jdt.core.refactoring.CompilationUnitChange;
import org.eclipse.jdt.internal.core.manipulation.dom.NecessaryParenthesesChecker;
import org.eclipse.jdt.internal.corext.codemanipulation.ContextSensitiveImportRewriteContext;
import org.eclipse.jdt.internal.corext.dom.ASTNodes;
import org.eclipse.jdt.internal.corext.fix.CleanUpConstants;
import org.eclipse.jdt.internal.corext.fix.CodeStyleFixCore;
import org.eclipse.jdt.internal.corext.fix.IProposableFix;
import org.eclipse.jdt.internal.corext.fix.RenameUnusedVariableFixCore;
import org.eclipse.jdt.internal.corext.fix.SealedClassFixCore;
import org.eclipse.jdt.internal.corext.fix.UnimplementedCodeFixCore;
import org.eclipse.jdt.internal.corext.fix.UnusedCodeFixCore;
import org.eclipse.jdt.internal.corext.util.JavaModelUtil;
import org.eclipse.jdt.internal.ui.fix.CodeStyleCleanUpCore;
import org.eclipse.jdt.internal.ui.text.correction.IProposalRelevance;
import org.eclipse.jdt.internal.ui.text.correction.LocalCorrectionsBaseSubProcessor;
import org.eclipse.jdt.internal.ui.text.correction.ProblemLocation;
import org.eclipse.jdt.internal.ui.text.correction.proposals.ChangeMethodSignatureProposalCore;
import org.eclipse.jdt.internal.ui.text.correction.proposals.ConstructorFromSuperclassProposalCore;
import org.eclipse.jdt.internal.ui.text.correction.proposals.CreateNewObjectProposalCore;
import org.eclipse.jdt.internal.ui.text.correction.proposals.CreateObjectReferenceProposalCore;
import org.eclipse.jdt.internal.ui.text.correction.proposals.CreateVariableReferenceProposalCore;
import org.eclipse.jdt.internal.ui.text.correction.proposals.FixCorrectionProposalCore;
import org.eclipse.jdt.internal.ui.text.correction.proposals.LinkedCorrectionProposalCore;
import org.eclipse.jdt.internal.ui.text.correction.proposals.MissingAnnotationAttributesProposalCore;
import org.eclipse.jdt.internal.ui.text.correction.proposals.ModifierChangeCorrectionProposalCore;
import org.eclipse.jdt.internal.ui.text.correction.proposals.NewMethodCorrectionProposalCore;
import org.eclipse.jdt.internal.ui.text.correction.proposals.NewVariableCorrectionProposalCore;
import org.eclipse.jdt.internal.ui.text.correction.proposals.RefactoringCorrectionProposalCore;
import org.eclipse.jdt.internal.ui.text.correction.proposals.ReplaceCorrectionProposalCore;
import org.eclipse.jdt.internal.ui.util.ASTHelper;
import org.eclipse.jdt.ls.core.internal.JavaLanguageServerPlugin;
import org.eclipse.jdt.ls.core.internal.corrections.CorrectionMessages;
import org.eclipse.jdt.ls.core.internal.corrections.InnovationContext;
import org.eclipse.jdt.ls.core.internal.corrections.InvertBooleanUtility;
import org.eclipse.jdt.ls.core.internal.corrections.ProposalKindWrapper;
import org.eclipse.jdt.ls.core.internal.handlers.CodeActionHandler;
import org.eclipse.jdt.ls.core.internal.text.correction.ModifierCorrectionSubProcessor;
import org.eclipse.jdt.ls.core.internal.text.correction.QuickAssistProcessor;
import org.eclipse.jdt.ui.text.java.IInvocationContext;
import org.eclipse.jdt.ui.text.java.IProblemLocation;
import org.eclipse.jdt.ui.text.java.correction.ASTRewriteCorrectionProposalCore;
import org.eclipse.lsp4j.CodeActionKind;

public class LocalCorrectionsSubProcessor extends LocalCorrectionsBaseSubProcessor<ProposalKindWrapper> {

	public static void addUncaughtExceptionProposals(IInvocationContext context, IProblemLocation problem, Collection<ProposalKindWrapper> proposals) throws CoreException {
		// See https://github.com/eclipse-jdtls/eclipse.jdt.ls/issues/1189
		if (context.getSelectionLength() > 0) {
			problem = new ProblemLocation(problem.getOffset(), problem.getLength(), problem.getProblemId(), problem.getProblemArguments(), problem.isError(), problem.getMarkerType()) {
				@Override
				public ASTNode getCoveringNode(CompilationUnit astRoot) {
					return context.getCoveredNode();
				}
			};
		}
		new LocalCorrectionsSubProcessor().getUncaughtExceptionProposals(context, problem, proposals);
	}

	public static void addUnreachableCatchProposals(IInvocationContext context, IProblemLocation problem, Collection<ProposalKindWrapper> proposals) {
		ASTNode selectedNode = problem.getCoveringNode(context.getASTRoot());
		if (selectedNode == null) {
			return;
		}

		QuickAssistProcessor.getCatchClauseToThrowsProposals(context, selectedNode, proposals);
	}

	public static void addUnimplementedMethodsProposals(IInvocationContext context, IProblemLocation problem, Collection<ProposalKindWrapper> proposals) {
		IProposableFix fix = UnimplementedCodeFixCore.createAddUnimplementedMethodsFix(context.getASTRoot(), problem);

		if (fix != null) {
			try {
				CompilationUnitChange change = fix.createChange(null);
				CUCorrectionProposalCore proposal = new CUCorrectionProposalCore(change.getName(), change.getCompilationUnit(), change, IProposalRelevance.ADD_UNIMPLEMENTED_METHODS);
				proposals.add(CodeActionHandler.wrap(proposal, CodeActionKind.QuickFix));
			} catch (CoreException e) {
				JavaLanguageServerPlugin.log(e);
			}
		}
	}

	public static void addUnusedMemberProposal(IInvocationContext context, IProblemLocation problem, Collection<ProposalKindWrapper> proposals) {
		int problemId = problem.getProblemId();

		if (JavaModelUtil.is22OrHigher(context.getCompilationUnit().getJavaProject()) && (problemId == IProblem.LocalVariableIsNeverUsed || problemId == IProblem.LambdaParameterIsNeverUsed)) {
			RenameUnusedVariableFixCore fix = RenameUnusedVariableFixCore.createRenameToUnnamedFix(context.getASTRoot(), problem);
			if (fix != null) {
				try {
					CompilationUnitChange change = fix.createChange(null);
					CUCorrectionProposalCore proposal = new CUCorrectionProposalCore(change.getName(), change.getCompilationUnit(), change, IProposalRelevance.UNUSED_MEMBER);
					proposals.add(CodeActionHandler.wrap(proposal, CodeActionKind.QuickFix));
				} catch (CoreException e) {
					JavaLanguageServerPlugin.log(e);
				}
			}
		}
		if (problemId == IProblem.LambdaParameterIsNeverUsed) {
			return;
		}

		UnusedCodeFixCore fix = UnusedCodeFixCore.createUnusedMemberFix(context.getASTRoot(), problem, false);
		if (fix != null) {
			try {
				CompilationUnitChange change = fix.createChange(null);
				CUCorrectionProposalCore proposal = new CUCorrectionProposalCore(change.getName(), change.getCompilationUnit(), change, IProposalRelevance.UNUSED_MEMBER);
				proposals.add(CodeActionHandler.wrap(proposal, CodeActionKind.QuickFix));
			} catch (CoreException e) {
				JavaLanguageServerPlugin.log(e);
			}
		}

		if (problemId == IProblem.LocalVariableIsNeverUsed) {
			fix = UnusedCodeFixCore.createUnusedMemberFix(context.getASTRoot(), problem, true);
			if (fix != null) {
				try {
					CompilationUnitChange change = fix.createChange(null);
					CUCorrectionProposalCore proposal = new CUCorrectionProposalCore(change.getName(), change.getCompilationUnit(), change, IProposalRelevance.UNUSED_MEMBER);
					proposals.add(CodeActionHandler.wrap(proposal, CodeActionKind.QuickFix));
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
					CUCorrectionProposalCore proposal = new CUCorrectionProposalCore(change.getName(), change.getCompilationUnit(), change, IProposalRelevance.UNUSED_MEMBER);
					proposals.add(CodeActionHandler.wrap(proposal, CodeActionKind.QuickFix));
				} catch (CoreException e) {
					JavaLanguageServerPlugin.log(e);
				}
			}
		}
	}

	public static void getUnreachableCodeProposals(IInvocationContext context, IProblemLocation problem, Collection<ProposalKindWrapper> proposals) {
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

	private static void addRemoveProposal(IInvocationContext context, ASTNode selectedNode, Collection<ProposalKindWrapper> proposals) {
		ASTRewrite rewrite = ASTRewrite.create(selectedNode.getAST());
		rewrite.remove(selectedNode, null);

		String label = CorrectionMessages.LocalCorrectionsSubProcessor_removeunreachablecode_description;
		addRemoveProposal(context, rewrite, label, proposals);
	}

	private static void addRemoveIncludingConditionProposal(IInvocationContext context, ASTNode toRemove, ASTNode replacement, Collection<ProposalKindWrapper> proposals) {
		String label = CorrectionMessages.LocalCorrectionsSubProcessor_removeunreachablecode_including_condition_description;
		AST ast = toRemove.getAST();
		ASTRewrite rewrite = ASTRewrite.create(ast);
		ASTRewriteCorrectionProposalCore proposal = new ASTRewriteCorrectionProposalCore(label, context.getCompilationUnit(), rewrite, IProposalRelevance.REMOVE_UNREACHABLE_CODE_INCLUDING_CONDITION);

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

		proposals.add(CodeActionHandler.wrap(proposal, CodeActionKind.QuickFix));
	}

	private static void addRemoveProposal(IInvocationContext context, ASTRewrite rewrite, String label, Collection<ProposalKindWrapper> proposals) {
		ASTRewriteCorrectionProposalCore proposal = new ASTRewriteCorrectionProposalCore(label, context.getCompilationUnit(), rewrite, 10);
		proposals.add(CodeActionHandler.wrap(proposal, CodeActionKind.QuickFix));
	}

	public static void addConstructorFromSuperclassProposal(IInvocationContext context, IProblemLocation problem, Collection<ProposalKindWrapper> proposals) throws CoreException {
		new LocalCorrectionsSubProcessor().getConstructorFromSuperclassProposal(context, problem, proposals);
	}

	public static void addUnnecessaryCastProposal(IInvocationContext context, IProblemLocation problem, Collection<ProposalKindWrapper> proposals) {
		new LocalCorrectionsSubProcessor().getUnnecessaryCastProposal(context, problem, proposals);
	}

	public static void addUnqualifiedFieldAccessProposal(IInvocationContext context, IProblemLocation problem, Collection<ProposalKindWrapper> proposals) {
		new LocalCorrectionsSubProcessor().getUnqualifiedFieldAccessProposal(context, problem, proposals);
	}

	/*
	 * Fix instance accesses and indirect (static) accesses to static fields/methods
	 */
	public static void addCorrectAccessToStaticProposals(IInvocationContext context, IProblemLocation problem, Collection<ProposalKindWrapper> proposals) throws CoreException {
		IProposableFix fix = CodeStyleFixCore.createIndirectAccessToStaticFix(context.getASTRoot(), problem);
		if (fix != null) {

			Map<String, String> options = new HashMap<>();
			options.put(CleanUpConstants.MEMBER_ACCESSES_STATIC_QUALIFY_WITH_DECLARING_CLASS, CleanUpOptionsCore.TRUE);
			options.put(CleanUpConstants.MEMBER_ACCESSES_STATIC_QUALIFY_WITH_DECLARING_CLASS_SUBTYPE_ACCESS, CleanUpOptionsCore.TRUE);
			FixCorrectionProposalCore proposal = new FixCorrectionProposalCore(fix, new CodeStyleCleanUpCore(options), IProposalRelevance.CREATE_INDIRECT_ACCESS_TO_STATIC, context);
			//proposal.setCommandId(ADD_STATIC_ACCESS_ID);
			proposals.add(CodeActionHandler.wrap(proposal, CodeActionKind.QuickFix));
			return;
		}

		IProposableFix[] fixes = CodeStyleFixCore.createNonStaticAccessFixes(context.getASTRoot(), problem);
		if (fixes != null) {
			IProposableFix fix1 = fixes[0];
			Map<String, String> options = new HashMap<>();
			options.put(CleanUpConstants.MEMBER_ACCESSES_STATIC_QUALIFY_WITH_DECLARING_CLASS, CleanUpOptionsCore.TRUE);
			options.put(CleanUpConstants.MEMBER_ACCESSES_STATIC_QUALIFY_WITH_DECLARING_CLASS_INSTANCE_ACCESS, CleanUpOptionsCore.TRUE);
			FixCorrectionProposalCore proposal = new FixCorrectionProposalCore(fix1, new CodeStyleCleanUpCore(options), IProposalRelevance.CREATE_NON_STATIC_ACCESS_USING_DECLARING_TYPE, context);
			//proposal.setCommandId(ADD_STATIC_ACCESS_ID);
			proposals.add(CodeActionHandler.wrap(proposal, CodeActionKind.QuickFix));

			if (fixes.length > 1) {
				Map<String, String> options1 = new HashMap<>();
				options1.put(CleanUpConstants.MEMBER_ACCESSES_STATIC_QUALIFY_WITH_DECLARING_CLASS, CleanUpOptionsCore.TRUE);
				options1.put(CleanUpConstants.MEMBER_ACCESSES_STATIC_QUALIFY_WITH_DECLARING_CLASS_SUBTYPE_ACCESS, CleanUpOptionsCore.TRUE);
				options1.put(CleanUpConstants.MEMBER_ACCESSES_STATIC_QUALIFY_WITH_DECLARING_CLASS_INSTANCE_ACCESS, CleanUpOptionsCore.TRUE);
				IProposableFix fix2 = fixes[1];
				proposal = new FixCorrectionProposalCore(fix2, new CodeStyleCleanUpCore(options), IProposalRelevance.CREATE_NON_STATIC_ACCESS_USING_INSTANCE_TYPE, context);
				proposals.add(CodeActionHandler.wrap(proposal, CodeActionKind.QuickFix));
			}
		}
		ModifierCorrectionSubProcessor.addNonAccessibleReferenceProposal(context, problem, proposals, ModifierCorrectionSubProcessor.TO_NON_STATIC, IProposalRelevance.REMOVE_STATIC_MODIFIER);
	}

	public static void addRedundantSuperInterfaceProposal(IInvocationContext context, IProblemLocation problem, Collection<ProposalKindWrapper> proposals) {
		new LocalCorrectionsSubProcessor().getRedundantSuperInterfaceProposal(context, problem, proposals);
	}

	public static void addUnnecessaryInstanceofProposal(IInvocationContext context, IProblemLocation problem, Collection<ProposalKindWrapper> proposals) {
		new LocalCorrectionsSubProcessor().getUnnecessaryInstanceofProposal(context, problem, proposals);
	}

	public static void addIllegalQualifiedEnumConstantLabelProposal(IInvocationContext context, IProblemLocation problem, Collection<ProposalKindWrapper> proposals) {
		new LocalCorrectionsSubProcessor().getIllegalQualifiedEnumConstantLabelProposal(context, problem, proposals);
	}

	public static void addUnnecessaryElseProposals(IInvocationContext context, IProblemLocation problem, Collection<ProposalKindWrapper> proposals) {
		new LocalCorrectionsSubProcessor().getUnnecessaryElseProposalsBase(context, problem, proposals);
	}

	public static void addInterfaceExtendsClassProposals(IInvocationContext context, IProblemLocation problem, Collection<ProposalKindWrapper> proposals) {
		new LocalCorrectionsSubProcessor().getInterfaceExtendsClassProposalsBase(context, problem, proposals);
	}

	public static void addAssignmentHasNoEffectProposals(IInvocationContext context, IProblemLocation problem, Collection<ProposalKindWrapper> proposals) {
		new LocalCorrectionsSubProcessor().getAssignmentHasNoEffectProposalsBase(context, problem, proposals);
	}

	public static void addFallThroughProposals(IInvocationContext context, IProblemLocation problem, Collection<ProposalKindWrapper> proposals) {
		new LocalCorrectionsSubProcessor().getFallThroughProposals(context, problem, proposals);
	}

	public static void addDeprecatedFieldsToMethodsProposals(IInvocationContext context, IProblemLocation problem, Collection<ProposalKindWrapper> proposals) {
		new LocalCorrectionsSubProcessor().getDeprecatedFieldsToMethodsProposals(context, problem, proposals);
	}

	public static void addRemoveDefaultCaseProposal(IInvocationContext context, IProblemLocation problem, Collection<ProposalKindWrapper> proposals) {
		new LocalCorrectionsSubProcessor().removeDefaultCaseProposalBase(context, problem, proposals);
	}

	public static void addSuperfluousSemicolonProposal(IInvocationContext context, IProblemLocation problem, Collection<ProposalKindWrapper> proposals) {
		new LocalCorrectionsSubProcessor().getSuperfluousSemicolonProposal(context, problem, proposals);
	}

	public static void addServiceProviderConstructorProposals(IInvocationContext context, IProblemLocation problem, Collection<ProposalKindWrapper> proposals) throws CoreException {
		new LocalCorrectionsSubProcessor().getServiceProviderConstructorProposals(context, problem, proposals);
	}

	public static void addNewObjectProposal(IInvocationContext context, IProblemLocation problem, Collection<ProposalKindWrapper> proposals) throws CoreException {
		new LocalCorrectionsSubProcessor().getNewObjectProposal(context, problem, proposals);
	}

	public static void addObjectReferenceProposal(IInvocationContext context, IProblemLocation problem, Collection<ProposalKindWrapper> proposals) throws CoreException {
		new LocalCorrectionsSubProcessor().getObjectReferenceProposal(context, problem, proposals);
	}

	public static void addVariableReferenceProposal(IInvocationContext context, IProblemLocation problem, Collection<ProposalKindWrapper> proposals) throws CoreException {
		new LocalCorrectionsSubProcessor().getVariableReferenceProposal(context, problem, proposals);
	}

	public static void addUninitializedLocalVariableProposal(IInvocationContext context, IProblemLocation problem, Collection<ProposalKindWrapper> proposals) {
		new LocalCorrectionsSubProcessor().getUninitializedLocalVariableProposal(context, problem, proposals);
	}

	public static void addOverrideDefaultMethodProposal(IInvocationContext context, IProblemLocation problem, Collection<ProposalKindWrapper> proposals) {
		new LocalCorrectionsSubProcessor().getOverrideDefaultMethodProposal(context, problem, proposals);
	}

	public static void addUnusedTypeParameterProposal(IInvocationContext context, IProblemLocation problem, Collection<ProposalKindWrapper> proposals) {
		UnusedCodeFixCore fix = UnusedCodeFixCore.createUnusedTypeParameterFix(context.getASTRoot(), problem);
		if (fix != null) {
			FixCorrectionProposalCore proposal = new FixCorrectionProposalCore(fix, fix.getCleanUp(), IProposalRelevance.UNUSED_MEMBER, context);
			proposals.add(CodeActionHandler.wrap(proposal, CodeActionKind.QuickFix));
		}

		JavadocTagsSubProcessor.getUnusedAndUndocumentedParameterOrExceptionProposals(context, problem, proposals);
	}

	public static void getMissingEnumConstantCaseProposals(IInvocationContext context, IProblemLocation problem, Collection<ProposalKindWrapper> proposals) {
		for (ProposalKindWrapper proposal : proposals) {
			if (CorrectionMessages.LocalCorrectionsSubProcessor_add_missing_cases_description.equals(proposal.getProposal().getName())) {
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
	public static void createMissingCaseProposals(IInvocationContext context, ASTNode parent, ArrayList<String> enumConstNames, Collection<ProposalKindWrapper> proposals) {
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
			LinkedCorrectionProposalCore proposal = new LinkedCorrectionProposalCore(label, context.getCompilationUnit(), astRewrite, IProposalRelevance.ADD_MISSING_CASE_STATEMENTS);

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
			proposals.add(CodeActionHandler.wrap(proposal, CodeActionKind.QuickFix));
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

	public static void addMissingDefaultCaseProposal(IInvocationContext context, IProblemLocation problem, Collection<ProposalKindWrapper> proposals) {
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
	private static void createMissingDefaultProposal(IInvocationContext context, ASTNode parent, Collection<ProposalKindWrapper> proposals) {
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
		LinkedCorrectionProposalCore proposal = new LinkedCorrectionProposalCore(label, context.getCompilationUnit(), astRewrite, IProposalRelevance.ADD_MISSING_DEFAULT_CASE);

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

		proposals.add(CodeActionHandler.wrap(proposal, CodeActionKind.QuickFix));
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

	public static void addCasesOmittedProposals(IInvocationContext context, IProblemLocation problem, Collection<ProposalKindWrapper> proposals) {
		new LocalCorrectionsSubProcessor().getCasesOmittedProposals(context, problem, proposals);
	}

	public static void getTryWithResourceProposals(IInvocationContext context, IProblemLocation problem, Collection<ProposalKindWrapper> proposals) {
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

	public static void addTypeAsPermittedSubTypeProposal(IInvocationContext context, IProblemLocation problem, Collection<ProposalKindWrapper> proposals) {
		new LocalCorrectionsSubProcessor().getTypeAsPermittedSubTypeProposal(context, problem, proposals);
	}

	public static void addPermittedTypesProposal(IInvocationContext context, IProblemLocation problem, Collection<ProposalKindWrapper> proposals) {
		new LocalCorrectionsSubProcessor().getPermittedTypesProposal(context, problem, proposals);
	}

	public static void addSealedAsDirectSuperTypeProposal(IInvocationContext context, IProblemLocation problem, Collection<ProposalKindWrapper> proposals) {
		SealedClassFixCore fix = SealedClassFixCore.addSealedAsDirectSuperTypeProposal(context.getASTRoot(), problem);
		if (fix != null) {
			String label = fix.getDisplayString();
			CompilationUnitChange change;
			try {
				change = fix.createChange(null);

				ASTNode selectedNode = problem.getCoveringNode(context.getASTRoot());
				IType permittedType = SealedClassFixCore.getPermittedType(selectedNode);
				ICompilationUnit unit = permittedType.getCompilationUnit();
				CUCorrectionProposalCore proposal = new CUCorrectionProposalCore(label, unit, change, IProposalRelevance.DECLARE_SEALED_AS_DIRECT_SUPER_TYPE);
				proposals.add(CodeActionHandler.wrap(proposal, CodeActionKind.QuickFix));
			} catch (CoreException e) {
				// do nothing
			}
		}
	}

	public static void addValueForAnnotationProposals(IInvocationContext context, IProblemLocation problem, Collection<ProposalKindWrapper> proposals) {
		new LocalCorrectionsSubProcessor().getValueForAnnotationProposals(context, problem, proposals);
	}

	@Override
	protected ProposalKindWrapper refactoringCorrectionProposalToT(RefactoringCorrectionProposalCore core, int uid) {
		return CodeActionHandler.wrap(core, CodeActionKind.QuickFix);
	}

	@Override
	protected ProposalKindWrapper linkedCorrectionProposalToT(LinkedCorrectionProposalCore core, int uid) {
		return CodeActionHandler.wrap(core, CodeActionKind.QuickFix);
	}

	@Override
	protected ProposalKindWrapper changeMethodSignatureProposalToT(ChangeMethodSignatureProposalCore core, int uid) {
		return CodeActionHandler.wrap(core, CodeActionKind.QuickFix);
	}

	@Override
	protected ProposalKindWrapper fixCorrectionProposalToT(FixCorrectionProposalCore core, int uid) {
		return CodeActionHandler.wrap(core, CodeActionKind.QuickFix);
	}

	@Override
	protected ProposalKindWrapper constructorFromSuperClassProposalToT(ConstructorFromSuperclassProposalCore core, int uid) {
		return CodeActionHandler.wrap(core, CodeActionKind.QuickFix);
	}

	@Override
	protected ProposalKindWrapper createNewObjectProposalToT(CreateNewObjectProposalCore core, int uid) {
		return CodeActionHandler.wrap(core, CodeActionKind.QuickFix);
	}

	@Override
	protected ProposalKindWrapper createObjectReferenceProposalToT(CreateObjectReferenceProposalCore core, int uid) {
		return CodeActionHandler.wrap(core, CodeActionKind.QuickFix);
	}

	@Override
	protected ProposalKindWrapper createVariableReferenceProposalToT(CreateVariableReferenceProposalCore core, int uid) {
		return CodeActionHandler.wrap(core, CodeActionKind.QuickFix);
	}

	@Override
	protected ProposalKindWrapper astRewriteCorrectionProposalToT(ASTRewriteCorrectionProposalCore core, int uid) {
		return CodeActionHandler.wrap(core, CodeActionKind.QuickFix);
	}

	@Override
	protected ProposalKindWrapper replaceCorrectionProposalToT(ReplaceCorrectionProposalCore core, int uid) {
		return CodeActionHandler.wrap(core, CodeActionKind.QuickFix);
	}

	@Override
	protected ProposalKindWrapper cuCorrectionProposalToT(CUCorrectionProposalCore core, int uid) {
		return CodeActionHandler.wrap(core, CodeActionKind.QuickFix);
	}

	@Override
	protected ProposalKindWrapper newVariableCorrectionProposalToT(NewVariableCorrectionProposalCore core, int uid) {
		return CodeActionHandler.wrap(core, CodeActionKind.QuickFix);
	}

	@Override
	protected ProposalKindWrapper missingAnnotationAttributesProposalToT(MissingAnnotationAttributesProposalCore core, int uid) {
		return CodeActionHandler.wrap(core, CodeActionKind.QuickFix);
	}

	@Override
	protected ProposalKindWrapper newMethodCorrectionProposalToT(NewMethodCorrectionProposalCore core, int uid) {
		return CodeActionHandler.wrap(core, CodeActionKind.QuickFix);
	}

	@Override
	protected ProposalKindWrapper modifierChangeCorrectionProposalToT(ModifierChangeCorrectionProposalCore core, int uid) {
		return CodeActionHandler.wrap(core, CodeActionKind.QuickFix);
	}

}
