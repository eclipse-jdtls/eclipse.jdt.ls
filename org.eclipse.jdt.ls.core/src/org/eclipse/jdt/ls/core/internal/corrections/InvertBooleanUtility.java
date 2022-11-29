/*******************************************************************************
 * Copyright (c) 2000, 2019 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Copied from org.eclipse.jdt.internal.ui.text.correction.AdvancedQuickAssistProcessor.java
 *
 * Contributors:
 *   Konstantin Scheglov (scheglov_ke@nlmk.ru) - initial API and implementation
 *          (reports 71244 & 74746: New Quick Assist's [quick assist])
 *   IBM Corporation - implementation
 *   Billy Huang <billyhuang31@gmail.com> - [quick assist] concatenate/merge string literals - https://bugs.eclipse.org/77632
 *   Microsoft Corporation - extract invert boolean logic to new file
 *******************************************************************************/

package org.eclipse.jdt.ls.core.internal.corrections;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;

import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.AssertStatement;
import org.eclipse.jdt.core.dom.Assignment;
import org.eclipse.jdt.core.dom.Block;
import org.eclipse.jdt.core.dom.BooleanLiteral;
import org.eclipse.jdt.core.dom.ClassInstanceCreation;
import org.eclipse.jdt.core.dom.ConditionalExpression;
import org.eclipse.jdt.core.dom.ConstructorInvocation;
import org.eclipse.jdt.core.dom.DoStatement;
import org.eclipse.jdt.core.dom.EnumConstantDeclaration;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.ForStatement;
import org.eclipse.jdt.core.dom.IBinding;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.IVariableBinding;
import org.eclipse.jdt.core.dom.IfStatement;
import org.eclipse.jdt.core.dom.InfixExpression;
import org.eclipse.jdt.core.dom.InfixExpression.Operator;
import org.eclipse.jdt.core.dom.InstanceofExpression;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.ParenthesizedExpression;
import org.eclipse.jdt.core.dom.PrefixExpression;
import org.eclipse.jdt.core.dom.QualifiedName;
import org.eclipse.jdt.core.dom.ReturnStatement;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.core.dom.SingleVariableDeclaration;
import org.eclipse.jdt.core.dom.Statement;
import org.eclipse.jdt.core.dom.StructuralPropertyDescriptor;
import org.eclipse.jdt.core.dom.SuperConstructorInvocation;
import org.eclipse.jdt.core.dom.SuperMethodInvocation;
import org.eclipse.jdt.core.dom.VariableDeclarationFragment;
import org.eclipse.jdt.core.dom.WhileStatement;
import org.eclipse.jdt.core.dom.rewrite.ASTRewrite;
import org.eclipse.jdt.internal.core.manipulation.dom.NecessaryParenthesesChecker;
import org.eclipse.jdt.internal.core.manipulation.dom.OperatorPrecedence;
import org.eclipse.jdt.internal.corext.dom.ASTNodes;
import org.eclipse.jdt.internal.corext.dom.LinkedNodeFinder;
import org.eclipse.jdt.ls.core.internal.Messages;
import org.eclipse.jdt.ls.core.internal.corrections.proposals.ASTRewriteCorrectionProposal;
import org.eclipse.jdt.ls.core.internal.corrections.proposals.ChangeCorrectionProposal;
import org.eclipse.jdt.ls.core.internal.corrections.proposals.IProposalRelevance;
import org.eclipse.jdt.ls.core.internal.corrections.proposals.LinkedCorrectionProposal;
import org.eclipse.jdt.ls.core.internal.text.correction.CUCorrectionCommandProposal;
import org.eclipse.jdt.ls.core.internal.text.correction.QuickAssistProcessor;
import org.eclipse.jdt.ls.core.internal.text.correction.RefactorProposalUtility;
import org.eclipse.lsp4j.CodeActionKind;
import org.eclipse.lsp4j.CodeActionParams;

/**
 * InvertBooleanSubProcessor
 */
public class InvertBooleanUtility {
	public static final String INVERT_VARIABLE_COMMAND = "invertVariable";

	public static ChangeCorrectionProposal getInvertVariableProposal(CodeActionParams params, IInvocationContext context, ASTNode covering, boolean returnAsCommand) {
		// cursor should be placed on variable name
		if (!(covering instanceof SimpleName)) {
			return null;
		}
		SimpleName coveringName = (SimpleName) covering;
		if (!coveringName.isDeclaration()) {
			return null;
		}
		// prepare bindings
		final IBinding variableBinding = coveringName.resolveBinding();
		if (!(variableBinding instanceof IVariableBinding)) {
			return null;
		}
		IVariableBinding binding = (IVariableBinding) variableBinding;
		if (binding.isField()) {
			return null;
		}
		// we operate only on boolean variable
		if (!isBoolean(coveringName)) {
			return null;
		}

		String label = CorrectionMessages.AdvancedQuickAssistProcessor_inverseBooleanVariable;
		if (returnAsCommand) {
			return new CUCorrectionCommandProposal(label, CodeActionKind.Refactor, context.getCompilationUnit(), IProposalRelevance.INVERSE_BOOLEAN_VARIABLE, RefactorProposalUtility.APPLY_REFACTORING_COMMAND_ID,
					Arrays.asList(INVERT_VARIABLE_COMMAND, params));
		}

		final AST ast = covering.getAST();
		// find linked nodes
		final MethodDeclaration method = ASTResolving.findParentMethodDeclaration(covering);
		SimpleName[] linkedNodes = LinkedNodeFinder.findByBinding(method, variableBinding);
		//
		final ASTRewrite rewrite = ASTRewrite.create(ast);
		// create proposal
		final String KEY_NAME = "name"; //$NON-NLS-1$
		final LinkedCorrectionProposal proposal = new LinkedCorrectionProposal(label, CodeActionKind.Refactor, context.getCompilationUnit(), rewrite, IProposalRelevance.INVERSE_BOOLEAN_VARIABLE);
		// prepare new variable identifier
		final String oldIdentifier = coveringName.getIdentifier();
		final String notString = Messages.format(CorrectionMessages.AdvancedQuickAssistProcessor_negatedVariableName, ""); //$NON-NLS-1$
		final String newIdentifier;
		if (oldIdentifier.startsWith(notString)) {
			int notLength = notString.length();
			if (oldIdentifier.length() > notLength) {
				newIdentifier = Character.toLowerCase(oldIdentifier.charAt(notLength)) + oldIdentifier.substring(notLength + 1);
			} else {
				newIdentifier = oldIdentifier;
			}
		} else {
			newIdentifier = Messages.format(CorrectionMessages.AdvancedQuickAssistProcessor_negatedVariableName, Character.toUpperCase(oldIdentifier.charAt(0)) + oldIdentifier.substring(1));
		}
		//
		proposal.addLinkedPositionProposal(KEY_NAME, newIdentifier);
		proposal.addLinkedPositionProposal(KEY_NAME, oldIdentifier);
		// iterate over linked nodes and replace variable references with negated reference
		final HashSet<SimpleName> renamedNames = new HashSet<>();
		for (int i = 0; i < linkedNodes.length; i++) {
			SimpleName name = linkedNodes[i];
			if (renamedNames.contains(name)) {
				continue;
			}
			// prepare new name with new identifier
			SimpleName newName = ast.newSimpleName(newIdentifier);
			proposal.addLinkedPosition(rewrite.track(newName), name == coveringName, KEY_NAME);
			//
			StructuralPropertyDescriptor location = name.getLocationInParent();
			if (location == SingleVariableDeclaration.NAME_PROPERTY) {
				// set new name
				rewrite.replace(name, newName, null);
			} else if (location == Assignment.LEFT_HAND_SIDE_PROPERTY) {
				Assignment assignment = (Assignment) name.getParent();
				Expression expression = assignment.getRightHandSide();
				int exStart = expression.getStartPosition();
				int exEnd = exStart + expression.getLength();
				// collect all names that are used in assignments
				HashSet<SimpleName> overlapNames = new HashSet<>();
				for (int j = 0; j < linkedNodes.length; j++) {
					SimpleName name2 = linkedNodes[j];
					if (name2 == null) {
						continue;
					}
					int name2Start = name2.getStartPosition();
					if (exStart <= name2Start && name2Start < exEnd) {
						overlapNames.add(name2);
					}
				}
				// prepare inverted expression
				SimpleNameRenameProvider provider = new SimpleNameRenameProvider() {
					@Override
					public SimpleName getRenamed(SimpleName simpleName) {
						if (simpleName.resolveBinding() == variableBinding) {
							renamedNames.add(simpleName);
							return ast.newSimpleName(newIdentifier);
						}
						return null;
					}
				};
				Expression inversedExpression = getInversedExpression(rewrite, expression, provider);
				// if any name was not renamed during expression inverting, we can not already rename it, so fail to create assist
				for (Iterator<SimpleName> iter = overlapNames.iterator(); iter.hasNext();) {
					Object o = iter.next();
					if (!renamedNames.contains(o)) {
						return null;
					}
				}
				// check operator and replace if needed
				Assignment.Operator operator = assignment.getOperator();
				if (operator == Assignment.Operator.BIT_AND_ASSIGN) {
					Assignment newAssignment = ast.newAssignment();
					newAssignment.setLeftHandSide(newName);
					newAssignment.setRightHandSide(inversedExpression);
					newAssignment.setOperator(Assignment.Operator.BIT_OR_ASSIGN);
					rewrite.replace(assignment, newAssignment, null);
				} else if (operator == Assignment.Operator.BIT_OR_ASSIGN) {
					Assignment newAssignment = ast.newAssignment();
					newAssignment.setLeftHandSide(newName);
					newAssignment.setRightHandSide(inversedExpression);
					newAssignment.setOperator(Assignment.Operator.BIT_AND_ASSIGN);
					rewrite.replace(assignment, newAssignment, null);
				} else {
					rewrite.replace(expression, inversedExpression, null);
					// set new name
					rewrite.replace(name, newName, null);
				}
			} else if (location == VariableDeclarationFragment.NAME_PROPERTY) {
				// replace initializer for variable
				VariableDeclarationFragment vdf = (VariableDeclarationFragment) name.getParent();
				Expression expression = vdf.getInitializer();
				if (expression != null) {
					rewrite.replace(expression, getInversedExpression(rewrite, expression), null);
				}
				// set new name
				rewrite.replace(name, newName, null);
			} else if (name.getParent() instanceof PrefixExpression prefixExpression && prefixExpression.getOperator() == PrefixExpression.Operator.NOT) {
				rewrite.replace(name.getParent(), newName, null);
			} else {
				PrefixExpression expression = ast.newPrefixExpression();
				expression.setOperator(PrefixExpression.Operator.NOT);
				expression.setOperand(newName);
				rewrite.replace(name, expression, null);
			}
		}

		return proposal;
	}

	public static boolean getInverseConditionProposals(CodeActionParams params, IInvocationContext context, ASTNode covering, Collection<ChangeCorrectionProposal> proposals) {
		ArrayList<ASTNode> coveredNodes = QuickAssistProcessor.getFullyCoveredNodes(context, covering);
		return getInverseConditionProposals(params, context, covering, coveredNodes, proposals);

	}

	private static boolean getInverseConditionProposals(CodeActionParams params, IInvocationContext context, ASTNode covering, ArrayList<ASTNode> coveredNodes, Collection<ChangeCorrectionProposal> proposals) {
		if (proposals == null) {
			return false;
		}

		final AST ast;
		final ASTRewrite rewrite;

		if (context.getSelectionLength() == 0) {
			Expression foundExpression = null;
			while (covering instanceof Expression) {
				Expression booleanExpression = getBooleanExpression(covering);
				if (booleanExpression != null) {
					foundExpression = getBooleanExpression(covering);
				}
				covering = covering.getParent();
			}
			if (foundExpression == null) {
				return false;
			}

			ast = foundExpression.getAST();
			rewrite = ASTRewrite.create(ast);

			Expression inversedExpression = getInversedExpression(rewrite, foundExpression);
			rewrite.replace(foundExpression, inversedExpression, null);
		} else {
			if (coveredNodes.isEmpty()) {
				return false;
			}
			ast = covering.getAST();
			rewrite = ASTRewrite.create(ast);
			// check sub-expressions in fully covered nodes
			boolean hasChanges = false;
			for (Iterator<ASTNode> iter = coveredNodes.iterator(); iter.hasNext();) {
				ASTNode covered = iter.next();
				Expression coveredExpression = getBooleanExpression(covered);
				if (coveredExpression != null) {
					Expression inversedExpression = getInversedExpression(rewrite, coveredExpression);
					rewrite.replace(coveredExpression, inversedExpression, null);
					hasChanges = true;
				}
			}

			if (!hasChanges) {
				return false;
			}
		}

		// add correction proposal
		String label = CorrectionMessages.AdvancedQuickAssistProcessor_inverseConditions_description;
		ASTRewriteCorrectionProposal proposal = new ASTRewriteCorrectionProposal(label, CodeActionKind.Refactor, context.getCompilationUnit(), rewrite, IProposalRelevance.INVERSE_CONDITIONS);
		proposals.add(proposal);
		return true;
	}

	private static Expression getInversedNotExpression(ASTRewrite rewrite, Expression expression, AST ast) {
		PrefixExpression prefixExpression = ast.newPrefixExpression();
		prefixExpression.setOperator(PrefixExpression.Operator.NOT);
		ParenthesizedExpression parenthesizedExpression = getParenthesizedExpression(ast, (Expression) rewrite.createCopyTarget(expression));
		prefixExpression.setOperand(parenthesizedExpression);
		return prefixExpression;
	}

	private static boolean isBoolean(Expression expression) {
		ITypeBinding typeBinding = expression.resolveTypeBinding();
		AST ast = expression.getAST();
		return typeBinding == ast.resolveWellKnownType("boolean") //$NON-NLS-1$
				|| typeBinding == ast.resolveWellKnownType("java.lang.Boolean"); //$NON-NLS-1$
	}

	private static Expression getBooleanExpression(ASTNode node) {
		if (!(node instanceof Expression)) {
			return null;
		}

		// check if the node is a location where it can be negated
		StructuralPropertyDescriptor locationInParent = node.getLocationInParent();
		if (locationInParent == QualifiedName.NAME_PROPERTY) {
			node = node.getParent();
			locationInParent = node.getLocationInParent();
		}
		while (locationInParent == ParenthesizedExpression.EXPRESSION_PROPERTY) {
			node = node.getParent();
			locationInParent = node.getLocationInParent();
		}
		Expression expression = (Expression) node;
		if (!isBoolean(expression)) {
			return null;
		}
		if (expression.getParent() instanceof InfixExpression) {
			return expression;
		}
		if (locationInParent == Assignment.RIGHT_HAND_SIDE_PROPERTY || locationInParent == IfStatement.EXPRESSION_PROPERTY || locationInParent == WhileStatement.EXPRESSION_PROPERTY || locationInParent == DoStatement.EXPRESSION_PROPERTY
				|| locationInParent == ReturnStatement.EXPRESSION_PROPERTY || locationInParent == ForStatement.EXPRESSION_PROPERTY || locationInParent == AssertStatement.EXPRESSION_PROPERTY
				|| locationInParent == MethodInvocation.ARGUMENTS_PROPERTY || locationInParent == ConstructorInvocation.ARGUMENTS_PROPERTY || locationInParent == SuperMethodInvocation.ARGUMENTS_PROPERTY
				|| locationInParent == EnumConstantDeclaration.ARGUMENTS_PROPERTY || locationInParent == SuperConstructorInvocation.ARGUMENTS_PROPERTY || locationInParent == ClassInstanceCreation.ARGUMENTS_PROPERTY
				|| locationInParent == ConditionalExpression.EXPRESSION_PROPERTY || locationInParent == PrefixExpression.OPERAND_PROPERTY) {
			return expression;
		}
		return null;
	}

	private static Expression getInversedInfixExpression(ASTRewrite rewrite, InfixExpression expression, InfixExpression.Operator newOperator, SimpleNameRenameProvider provider) {
		InfixExpression newExpression = rewrite.getAST().newInfixExpression();
		newExpression.setOperator(newOperator);
		newExpression.setLeftOperand(getRenamedNameCopy(provider, rewrite, expression.getLeftOperand()));
		newExpression.setRightOperand(getRenamedNameCopy(provider, rewrite, expression.getRightOperand()));
		return newExpression;
	}

	private static Expression parenthesizeIfRequired(Expression operand, int newOperatorPrecedence) {
		if (newOperatorPrecedence > OperatorPrecedence.getExpressionPrecedence(operand)) {
			return getParenthesizedExpression(operand.getAST(), operand);
		}
		return operand;
	}

	private static ParenthesizedExpression getParenthesizedExpression(AST ast, Expression expression) {
		ParenthesizedExpression parenthesizedExpression = ast.newParenthesizedExpression();
		parenthesizedExpression.setExpression(expression);
		return parenthesizedExpression;
	}

	private static Expression getInversedAndOrExpression(ASTRewrite rewrite, InfixExpression infixExpression, Operator newOperator, SimpleNameRenameProvider provider) {
		InfixExpression newExpression = rewrite.getAST().newInfixExpression();
		newExpression.setOperator(newOperator);

		int newOperatorPrecedence = OperatorPrecedence.getOperatorPrecedence(newOperator);

		Expression leftOperand = getInversedExpression(rewrite, infixExpression.getLeftOperand(), provider);
		newExpression.setLeftOperand(parenthesizeIfRequired(leftOperand, newOperatorPrecedence));

		Expression rightOperand = getInversedExpression(rewrite, infixExpression.getRightOperand(), provider);
		newExpression.setRightOperand(parenthesizeIfRequired(rightOperand, newOperatorPrecedence));

		List<Expression> extraOperands = infixExpression.extendedOperands();
		List<Expression> newExtraOperands = newExpression.extendedOperands();
		for (int i = 0; i < extraOperands.size(); i++) {
			Expression extraOperand = getInversedExpression(rewrite, extraOperands.get(i), provider);
			newExtraOperands.add(parenthesizeIfRequired(extraOperand, newOperatorPrecedence));
		}
		return newExpression;
	}

	private interface SimpleNameRenameProvider {
		SimpleName getRenamed(SimpleName name);
	}

	private static Expression getRenamedNameCopy(SimpleNameRenameProvider provider, ASTRewrite rewrite, Expression expression) {
		if (provider != null) {
			if (expression instanceof SimpleName name) {
				SimpleName newName = provider.getRenamed(name);
				if (newName != null) {
					return newName;
				}
			}
		}
		return (Expression) rewrite.createCopyTarget(expression);
	}


	private static Expression getInversedExpression(ASTRewrite rewrite, Expression expression) {
		return getInversedExpression(rewrite, expression, null);
	}

	private static Expression getInversedExpression(ASTRewrite rewrite, Expression expression, SimpleNameRenameProvider provider) {
		AST ast = rewrite.getAST();

		if (expression instanceof BooleanLiteral booleanLiteral) {
			return ast.newBooleanLiteral(!booleanLiteral.booleanValue());
		}
		if (expression instanceof InfixExpression infixExpression) {
			InfixExpression.Operator operator = infixExpression.getOperator();
			if (operator == InfixExpression.Operator.LESS) {
				return getInversedInfixExpression(rewrite, infixExpression, InfixExpression.Operator.GREATER_EQUALS, provider);
			}
			if (operator == InfixExpression.Operator.GREATER) {
				return getInversedInfixExpression(rewrite, infixExpression, InfixExpression.Operator.LESS_EQUALS, provider);
			}
			if (operator == InfixExpression.Operator.LESS_EQUALS) {
				return getInversedInfixExpression(rewrite, infixExpression, InfixExpression.Operator.GREATER, provider);
			}
			if (operator == InfixExpression.Operator.GREATER_EQUALS) {
				return getInversedInfixExpression(rewrite, infixExpression, InfixExpression.Operator.LESS, provider);
			}
			if (operator == InfixExpression.Operator.EQUALS) {
				return getInversedInfixExpression(rewrite, infixExpression, InfixExpression.Operator.NOT_EQUALS, provider);
			}
			if (operator == InfixExpression.Operator.NOT_EQUALS) {
				return getInversedInfixExpression(rewrite, infixExpression, InfixExpression.Operator.EQUALS, provider);
			}
			if (operator == InfixExpression.Operator.CONDITIONAL_AND) {
				return getInversedAndOrExpression(rewrite, infixExpression, InfixExpression.Operator.CONDITIONAL_OR, provider);
			}
			if (operator == InfixExpression.Operator.CONDITIONAL_OR) {
				return getInversedAndOrExpression(rewrite, infixExpression, InfixExpression.Operator.CONDITIONAL_AND, provider);
			}
			if (operator == InfixExpression.Operator.AND) {
				return getInversedAndOrExpression(rewrite, infixExpression, InfixExpression.Operator.OR, provider);
			}
			if (operator == InfixExpression.Operator.OR) {
				return getInversedAndOrExpression(rewrite, infixExpression, InfixExpression.Operator.AND, provider);
			}
			if (operator == InfixExpression.Operator.XOR) {
				return getInversedNotExpression(rewrite, expression, ast);
			}
		}
		if (expression instanceof PrefixExpression prefixExpression) {
			if (prefixExpression.getOperator() == PrefixExpression.Operator.NOT) {
				Expression operand = prefixExpression.getOperand();
				if (operand instanceof ParenthesizedExpression parenthesizedExpression && NecessaryParenthesesChecker.canRemoveParentheses(operand, expression.getParent(), expression.getLocationInParent())) {
					operand = parenthesizedExpression.getExpression();
				}
				Expression renamedNameCopy = getRenamedNameCopy(provider, rewrite, operand);
				if (renamedNameCopy instanceof InfixExpression infixExpression) {
					infixExpression.setOperator(((InfixExpression) operand).getOperator());
				}
				return renamedNameCopy;
			}
		}
		if (expression instanceof InstanceofExpression) {
			return getInversedNotExpression(rewrite, expression, ast);
		}
		if (expression instanceof ParenthesizedExpression parenthesizedExpression) {
			Expression innerExpression = parenthesizedExpression.getExpression();
			while (innerExpression instanceof ParenthesizedExpression innerParenthesizedExpression) {
				innerExpression = innerParenthesizedExpression.getExpression();
			}
			if (innerExpression instanceof InstanceofExpression) {
				return getInversedExpression(rewrite, innerExpression, provider);
			}
			parenthesizedExpression = getParenthesizedExpression(ast, getInversedExpression(rewrite, innerExpression, provider));
			return parenthesizedExpression;
		}
		if (expression instanceof ConditionalExpression conditionalExpression) {
			ConditionalExpression newExpression = ast.newConditionalExpression();
			newExpression.setExpression((Expression) rewrite.createCopyTarget(conditionalExpression.getExpression()));
			newExpression.setThenExpression(getInversedExpression(rewrite, conditionalExpression.getThenExpression()));
			newExpression.setElseExpression(getInversedExpression(rewrite, conditionalExpression.getElseExpression()));
			return newExpression;
		}

		PrefixExpression prefixExpression = ast.newPrefixExpression();
		prefixExpression.setOperator(PrefixExpression.Operator.NOT);
		Expression renamedNameCopy = getRenamedNameCopy(provider, rewrite, expression);
		if (NecessaryParenthesesChecker.needsParentheses(renamedNameCopy, prefixExpression, PrefixExpression.OPERAND_PROPERTY)) {
			renamedNameCopy = getParenthesizedExpression(ast, renamedNameCopy);
		}
		prefixExpression.setOperand(renamedNameCopy);
		return prefixExpression;
	}

	public static boolean getSplitAndConditionProposals(IInvocationContext context, ASTNode node, Collection<ChangeCorrectionProposal> resultingCollections) {
		Operator andOperator = InfixExpression.Operator.CONDITIONAL_AND;
		// check that user invokes quick assist on infix expression
		if (!(node instanceof InfixExpression)) {
			return false;
		}
		InfixExpression infixExpression = (InfixExpression) node;
		if (infixExpression.getOperator() != andOperator) {
			return false;
		}
		int offset = isOperatorSelected(infixExpression, context.getSelectionOffset(), context.getSelectionLength());
		if (offset == -1) {
			return false;
		}

		// check that infix expression belongs to IfStatement
		Statement statement = ASTResolving.findParentStatement(node);
		if (!(statement instanceof IfStatement)) {
			return false;
		}
		IfStatement ifStatement = (IfStatement) statement;

		// check that infix expression is part of first level && condition of IfStatement
		InfixExpression topInfixExpression = infixExpression;
		while (topInfixExpression.getParent() instanceof InfixExpression parentInfixExpression && parentInfixExpression.getOperator() == andOperator) {
			topInfixExpression = parentInfixExpression;
		}
		if (ifStatement.getExpression() != topInfixExpression) {
			return false;
		}
		//
		if (resultingCollections == null) {
			return true;
		}
		AST ast = ifStatement.getAST();
		ASTRewrite rewrite = ASTRewrite.create(ast);

		// prepare left and right conditions
		Expression[] newOperands = { null, null };
		breakInfixOperationAtOperation(rewrite, topInfixExpression, andOperator, offset, true, newOperands);

		Expression leftCondition = newOperands[0];
		Expression rightCondition = newOperands[1];

		// replace conditions in outer IfStatement
		rewrite.set(ifStatement, IfStatement.EXPRESSION_PROPERTY, leftCondition, null);

		// prepare inner IfStatement
		IfStatement innerIf = ast.newIfStatement();

		innerIf.setExpression(rightCondition);
		innerIf.setThenStatement((Statement) rewrite.createMoveTarget(ifStatement.getThenStatement()));
		Block innerBlock = ast.newBlock();
		innerBlock.statements().add(innerIf);

		Statement elseStatement = ifStatement.getElseStatement();
		if (elseStatement != null) {
			innerIf.setElseStatement((Statement) rewrite.createCopyTarget(elseStatement));
		}

		// replace outer thenStatement
		rewrite.replace(ifStatement.getThenStatement(), innerBlock, null);

		// add correction proposal
		String label = CorrectionMessages.AdvancedQuickAssistProcessor_splitAndCondition_description;
		ASTRewriteCorrectionProposal proposal = new ASTRewriteCorrectionProposal(label, CodeActionKind.QuickFix, context.getCompilationUnit(), rewrite, IProposalRelevance.SPLIT_AND_CONDITION);
		resultingCollections.add(proposal);
		return true;
	}

	public static boolean getSplitOrConditionProposals(IInvocationContext context, ASTNode node, Collection<ChangeCorrectionProposal> resultingCollections) {
		Operator orOperator = InfixExpression.Operator.CONDITIONAL_OR;
		// check that user invokes quick assist on infix expression
		if (!(node instanceof InfixExpression)) {
			return false;
		}
		InfixExpression infixExpression = (InfixExpression) node;
		if (infixExpression.getOperator() != orOperator) {
			return false;
		}
		int offset = isOperatorSelected(infixExpression, context.getSelectionOffset(), context.getSelectionLength());
		if (offset == -1) {
			return false;
		}
		// check that infix expression belongs to IfStatement
		Statement statement = ASTResolving.findParentStatement(node);
		if (!(statement instanceof IfStatement)) {
			return false;
		}
		IfStatement ifStatement = (IfStatement) statement;

		// check that infix expression is part of first level || condition of IfStatement
		InfixExpression topInfixExpression = infixExpression;
		while (topInfixExpression.getParent() instanceof InfixExpression && ((InfixExpression) topInfixExpression.getParent()).getOperator() == orOperator) {
			topInfixExpression = (InfixExpression) topInfixExpression.getParent();
		}
		if (ifStatement.getExpression() != topInfixExpression) {
			return false;
		}
		//
		if (resultingCollections == null) {
			return true;
		}
		AST ast = ifStatement.getAST();
		ASTRewrite rewrite = ASTRewrite.create(ast);

		// prepare left and right conditions
		Expression[] newOperands = { null, null };
		breakInfixOperationAtOperation(rewrite, topInfixExpression, orOperator, offset, true, newOperands);

		Expression leftCondition = newOperands[0];
		Expression rightCondition = newOperands[1];

		// prepare first statement
		rewrite.replace(ifStatement.getExpression(), leftCondition, null);

		IfStatement secondIf = ast.newIfStatement();
		secondIf.setExpression(rightCondition);
		secondIf.setThenStatement((Statement) rewrite.createCopyTarget(ifStatement.getThenStatement()));

		Statement elseStatement = ifStatement.getElseStatement();
		if (elseStatement == null) {
			rewrite.set(ifStatement, IfStatement.ELSE_STATEMENT_PROPERTY, secondIf, null);
		} else {
			rewrite.replace(elseStatement, secondIf, null);
			secondIf.setElseStatement((Statement) rewrite.createMoveTarget(elseStatement));
		}

		// add correction proposal
		String label = CorrectionMessages.AdvancedQuickAssistProcessor_splitOrCondition_description;
		ASTRewriteCorrectionProposal proposal = new ASTRewriteCorrectionProposal(label, CodeActionKind.QuickFix, context.getCompilationUnit(), rewrite, IProposalRelevance.SPLIT_OR_CONDITION);
		resultingCollections.add(proposal);
		return true;
	}

	private static boolean isSelectingOperator(ASTNode n1, ASTNode n2, int offset, int length) {
		// between the nodes
		if (offset + length <= n2.getStartPosition() && offset >= ASTNodes.getExclusiveEnd(n1)) {
			return true;
		}
		// or exactly select the node (but not with infix expressions)
		if (n1.getStartPosition() == offset && ASTNodes.getExclusiveEnd(n2) == offset + length) {
			if (n1 instanceof InfixExpression || n2 instanceof InfixExpression) {
				return false;
			}
			return true;
		}
		return false;
	}

	private static int isOperatorSelected(InfixExpression infixExpression, int offset, int length) {
		ASTNode left = infixExpression.getLeftOperand();
		ASTNode right = infixExpression.getRightOperand();

		if (isSelectingOperator(left, right, offset, length)) {
			return ASTNodes.getExclusiveEnd(left);
		}
		List<Expression> extended = infixExpression.extendedOperands();
		for (int i = 0; i < extended.size(); i++) {
			left = right;
			right = extended.get(i);
			if (isSelectingOperator(left, right, offset, length)) {
				return ASTNodes.getExclusiveEnd(left);
			}
		}
		return -1;
	}

	/*
	 * Breaks an infix operation with possible extended operators at the given operator and returns the new left and right operands.
	 * a & b & c   ->  [[a' & b' ] & c' ]   (c' == copy of c)
	 */
	private static void breakInfixOperationAtOperation(ASTRewrite rewrite, Expression expression, Operator operator, int operatorOffset, boolean removeParentheses, Expression[] res) {
		if (expression.getStartPosition() + expression.getLength() <= operatorOffset) {
			// add to the left
			res[0] = combineOperands(rewrite, res[0], expression, removeParentheses, operator);
			return;
		}
		if (operatorOffset <= expression.getStartPosition()) {
			// add to the right
			res[1] = combineOperands(rewrite, res[1], expression, removeParentheses, operator);
			return;
		}
		if (!(expression instanceof InfixExpression)) {
			throw new IllegalArgumentException("Cannot break up non-infix expression"); //$NON-NLS-1$
		}
		InfixExpression infixExpression = (InfixExpression) expression;
		if (infixExpression.getOperator() != operator) {
			throw new IllegalArgumentException("Incompatible operator"); //$NON-NLS-1$
		}
		breakInfixOperationAtOperation(rewrite, infixExpression.getLeftOperand(), operator, operatorOffset, removeParentheses, res);
		breakInfixOperationAtOperation(rewrite, infixExpression.getRightOperand(), operator, operatorOffset, removeParentheses, res);

		List<Expression> extended = infixExpression.extendedOperands();
		for (int i = 0; i < extended.size(); i++) {
			breakInfixOperationAtOperation(rewrite, extended.get(i), operator, operatorOffset, removeParentheses, res);
		}
	}

	private static Expression combineOperands(ASTRewrite rewrite, Expression existing, Expression originalNode, boolean removeParentheses, Operator operator) {
		if (existing == null && removeParentheses) {
			while (originalNode instanceof ParenthesizedExpression parenthesizedExpression) {
				originalNode = parenthesizedExpression.getExpression();
			}
		}
		Expression newRight = (Expression) rewrite.createMoveTarget(originalNode);
		if (originalNode instanceof InfixExpression infixExpression) {
			((InfixExpression) newRight).setOperator(infixExpression.getOperator());
		}

		if (existing == null) {
			return newRight;
		}
		AST ast = rewrite.getAST();
		InfixExpression infix = ast.newInfixExpression();
		infix.setOperator(operator);
		infix.setLeftOperand(existing);
		infix.setRightOperand(newRight);
		return infix;
	}

}
