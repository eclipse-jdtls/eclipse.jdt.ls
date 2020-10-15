/*******************************************************************************
 * Copyright (c) 2000, 2019 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Originally copied from org.eclipse.jdt.internal.ui.text.correction.QuickAssistProcessor;
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *     Sebastian Davids <sdavids@gmx.de> - Bug 37432 getInvertEqualsProposal
 *     Benjamin Muskalla <b.muskalla@gmx.net> - Bug 36350 convertToStringBufferPropsal
 *     Chris West (Faux) <eclipse@goeswhere.com> - [quick assist] "Use 'StringBuilder' for string concatenation" could fix existing misuses - https://bugs.eclipse.org/bugs/show_bug.cgi?id=282755
 *     Lukas Hanke <hanke@yatta.de> - Bug 241696 [quick fix] quickfix to iterate over a collection - https://bugs.eclipse.org/bugs/show_bug.cgi?id=241696
 *     Eugene Lucash <e.lucash@gmail.com> - [quick assist] Add key binding for Extract method Quick Assist - https://bugs.eclipse.org/424166
 *     Lukas Hanke <hanke@yatta.de> - Bug 430818 [1.8][quick fix] Quick fix for "for loop" is not shown for bare local variable/argument/field - https://bugs.eclipse.org/bugs/show_bug.cgi?id=430818
 *     Jeremie Bresson <dev@jmini.fr> - Bug 439912: [1.8][quick assist] Add quick assists to add and remove parentheses around single lambda parameter - https://bugs.eclipse.org/439912
 *******************************************************************************/

package org.eclipse.jdt.ls.core.internal.text.correction;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.jdt.core.IMethod;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ArrayCreation;
import org.eclipse.jdt.core.dom.ArrayType;
import org.eclipse.jdt.core.dom.Assignment;
import org.eclipse.jdt.core.dom.Block;
import org.eclipse.jdt.core.dom.BodyDeclaration;
import org.eclipse.jdt.core.dom.CatchClause;
import org.eclipse.jdt.core.dom.ClassInstanceCreation;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.CreationReference;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.ExpressionMethodReference;
import org.eclipse.jdt.core.dom.ExpressionStatement;
import org.eclipse.jdt.core.dom.IBinding;
import org.eclipse.jdt.core.dom.IMethodBinding;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.IVariableBinding;
import org.eclipse.jdt.core.dom.Initializer;
import org.eclipse.jdt.core.dom.LambdaExpression;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.MethodReference;
import org.eclipse.jdt.core.dom.Modifier;
import org.eclipse.jdt.core.dom.Name;
import org.eclipse.jdt.core.dom.NameQualifiedType;
import org.eclipse.jdt.core.dom.ParameterizedType;
import org.eclipse.jdt.core.dom.PostfixExpression;
import org.eclipse.jdt.core.dom.PrefixExpression;
import org.eclipse.jdt.core.dom.PrefixExpression.Operator;
import org.eclipse.jdt.core.dom.QualifiedType;
import org.eclipse.jdt.core.dom.ReturnStatement;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.core.dom.SimpleType;
import org.eclipse.jdt.core.dom.SingleVariableDeclaration;
import org.eclipse.jdt.core.dom.Statement;
import org.eclipse.jdt.core.dom.SuperMethodInvocation;
import org.eclipse.jdt.core.dom.SuperMethodReference;
import org.eclipse.jdt.core.dom.ThisExpression;
import org.eclipse.jdt.core.dom.TryStatement;
import org.eclipse.jdt.core.dom.Type;
import org.eclipse.jdt.core.dom.TypeDeclaration;
import org.eclipse.jdt.core.dom.TypeMethodReference;
import org.eclipse.jdt.core.dom.UnionType;
import org.eclipse.jdt.core.dom.VariableDeclaration;
import org.eclipse.jdt.core.dom.VariableDeclarationFragment;
import org.eclipse.jdt.core.dom.rewrite.ASTRewrite;
import org.eclipse.jdt.core.dom.rewrite.ImportRewrite;
import org.eclipse.jdt.core.dom.rewrite.ImportRewrite.ImportRewriteContext;
import org.eclipse.jdt.core.dom.rewrite.ImportRewrite.TypeLocation;
import org.eclipse.jdt.core.dom.rewrite.ListRewrite;
import org.eclipse.jdt.core.manipulation.CodeStyleConfiguration;
import org.eclipse.jdt.internal.core.manipulation.StubUtility;
import org.eclipse.jdt.internal.core.manipulation.dom.ASTResolving;
import org.eclipse.jdt.internal.corext.codemanipulation.ContextSensitiveImportRewriteContext;
import org.eclipse.jdt.internal.corext.codemanipulation.StubUtility2Core;
import org.eclipse.jdt.internal.corext.dom.ASTNodes;
import org.eclipse.jdt.internal.corext.dom.Bindings;
import org.eclipse.jdt.internal.corext.dom.GenericVisitor;
import org.eclipse.jdt.internal.corext.dom.JdtASTMatcher;
import org.eclipse.jdt.internal.corext.dom.ScopeAnalyzer;
import org.eclipse.jdt.internal.corext.fix.LinkedProposalModelCore;
import org.eclipse.jdt.internal.ui.text.correction.IProblemLocationCore;
import org.eclipse.jdt.ls.core.internal.corrections.CorrectionMessages;
import org.eclipse.jdt.ls.core.internal.corrections.IInvocationContext;
import org.eclipse.jdt.ls.core.internal.corrections.proposals.ASTRewriteCorrectionProposal;
import org.eclipse.jdt.ls.core.internal.corrections.proposals.AssignToVariableAssistProposal;
import org.eclipse.jdt.ls.core.internal.corrections.proposals.ChangeCorrectionProposal;
import org.eclipse.jdt.ls.core.internal.corrections.proposals.IProposalRelevance;
import org.eclipse.jdt.ls.core.internal.corrections.proposals.LinkedCorrectionProposal;
import org.eclipse.jdt.ls.core.internal.preferences.PreferenceManager;
import org.eclipse.jface.text.link.LinkedPositionGroup;
import org.eclipse.lsp4j.CodeActionKind;
import org.eclipse.lsp4j.CodeActionParams;

public class QuickAssistProcessor {

	public static final String SPLIT_JOIN_VARIABLE_DECLARATION_ID = "org.eclipse.jdt.ls.correction.splitJoinVariableDeclaration.assist"; //$NON-NLS-1$
	public static final String CONVERT_FOR_LOOP_ID = "org.eclipse.jdt.ls.correction.convertForLoop.assist"; //$NON-NLS-1$
	public static final String ASSIGN_TO_LOCAL_ID = "org.eclipse.jdt.ls.correction.assignToLocal.assist"; //$NON-NLS-1$
	public static final String ASSIGN_TO_FIELD_ID = "org.eclipse.jdt.ls.correction.assignToField.assist"; //$NON-NLS-1$
	public static final String ASSIGN_PARAM_TO_FIELD_ID = "org.eclipse.jdt.ls.correction.assignParamToField.assist"; //$NON-NLS-1$
	public static final String ASSIGN_ALL_PARAMS_TO_NEW_FIELDS_ID = "org.eclipse.jdt.ls.correction.assignAllParamsToNewFields.assist"; //$NON-NLS-1$
	public static final String ADD_BLOCK_ID = "org.eclipse.jdt.ls.correction.addBlock.assist"; //$NON-NLS-1$
	public static final String EXTRACT_LOCAL_ID = "org.eclipse.jdt.ls.correction.extractLocal.assist"; //$NON-NLS-1$
	public static final String EXTRACT_LOCAL_NOT_REPLACE_ID = "org.eclipse.jdt.ls.correction.extractLocalNotReplaceOccurrences.assist"; //$NON-NLS-1$
	public static final String EXTRACT_CONSTANT_ID = "org.eclipse.jdt.ls.correction.extractConstant.assist"; //$NON-NLS-1$
	public static final String INLINE_LOCAL_ID = "org.eclipse.jdt.ls.correction.inlineLocal.assist"; //$NON-NLS-1$
	public static final String CONVERT_LOCAL_TO_FIELD_ID = "org.eclipse.jdt.ls.correction.convertLocalToField.assist"; //$NON-NLS-1$
	public static final String CONVERT_ANONYMOUS_TO_LOCAL_ID = "org.eclipse.jdt.ls.correction.convertAnonymousToLocal.assist"; //$NON-NLS-1$
	public static final String CONVERT_TO_STRING_BUFFER_ID = "org.eclipse.jdt.ls.correction.convertToStringBuffer.assist"; //$NON-NLS-1$
	public static final String CONVERT_TO_MESSAGE_FORMAT_ID = "org.eclipse.jdt.ls.correction.convertToMessageFormat.assist"; //$NON-NLS-1$;
	public static final String EXTRACT_METHOD_INPLACE_ID = "org.eclipse.jdt.ls.correction.extractMethodInplace.assist"; //$NON-NLS-1$;

	private PreferenceManager preferenceManager;

	public QuickAssistProcessor(PreferenceManager preferenceManager) {
		this.preferenceManager = preferenceManager;
	}

	public List<ChangeCorrectionProposal> getAssists(CodeActionParams params, IInvocationContext context, IProblemLocationCore[] locations) throws CoreException {
		ASTNode coveringNode = context.getCoveringNode();
		if (coveringNode != null) {
			// ArrayList<ASTNode> coveredNodes = getFullyCoveredNodes(context, coveringNode);
			ArrayList<ChangeCorrectionProposal> resultingCollections = new ArrayList<>();

			// quick assists that show up also if there is an error/warning
			//			getRenameLocalProposals(context, coveringNode, locations, resultingCollections);
			//			getRenameRefactoringProposal(context, coveringNode, locations, resultingCollections);
			//			getAssignToVariableProposals(context, coveringNode, locations, resultingCollections);
			getAssignParamToFieldProposals(context, coveringNode, resultingCollections);
			getAssignAllParamsToFieldsProposals(context, coveringNode, resultingCollections);
			//			getInferDiamondArgumentsProposal(context, coveringNode, locations, resultingCollections);
			//			getGenerateForLoopProposals(context, coveringNode, locations, resultingCollections);

			// boolean noErrorsAtLocation = noErrorsAtLocation(locations);
			// if (noErrorsAtLocation) {
				// boolean problemsAtLocation = locations.length != 0;
				//				getCatchClauseToThrowsProposals(context, coveringNode, resultingCollections);
				//				getPickoutTypeFromMulticatchProposals(context, coveringNode, coveredNodes, resultingCollections);
				//				getConvertToMultiCatchProposals(context, coveringNode, resultingCollections);
				//				getUnrollMultiCatchProposals(context, coveringNode, resultingCollections);
				//				getUnWrapProposals(context, coveringNode, resultingCollections);
				//				getJoinVariableProposals(context, coveringNode, resultingCollections);
				//				getSplitVariableProposals(context, coveringNode, resultingCollections);
				//				getAddFinallyProposals(context, coveringNode, resultingCollections);
				//				getAddElseProposals(context, coveringNode, resultingCollections);
				//				getAddBlockProposals(context, coveringNode, resultingCollections);
				//				getInvertEqualsProposal(context, coveringNode, resultingCollections);
				//				getArrayInitializerToArrayCreation(context, coveringNode, resultingCollections);
				//				getCreateInSuperClassProposals(context, coveringNode, resultingCollections);
				//				getConvertLocalToFieldProposal(context, coveringNode, resultingCollections);
				//				getChangeLambdaBodyToBlockProposal(context, coveringNode, resultingCollections);
				//				getChangeLambdaBodyToExpressionProposal(context, coveringNode, resultingCollections);
				//				getAddInferredLambdaParameterTypes(context, coveringNode, resultingCollections);
			getConvertMethodReferenceToLambdaProposal(context, coveringNode, resultingCollections);
			getConvertLambdaToMethodReferenceProposal(context, coveringNode, resultingCollections);
				//				getFixParenthesesInLambdaExpression(context, coveringNode, resultingCollections);
				//				if (!getConvertForLoopProposal(context, coveringNode, resultingCollections)) {
				//					getConvertIterableLoopProposal(context, coveringNode, resultingCollections);
				//				}
				//				getConvertEnhancedForLoopProposal(context, coveringNode, resultingCollections);
				//				getRemoveBlockProposals(context, coveringNode, resultingCollections);
				//				getConvertStringConcatenationProposals(context, resultingCollections);
				//				getMissingCaseStatementProposals(context, coveringNode, resultingCollections);
			// }
			return resultingCollections;
		}
		return Collections.emptyList();
	}

	private static boolean getAssignParamToFieldProposals(IInvocationContext context, ASTNode node, Collection<ChangeCorrectionProposal> resultingCollections) {
		node = ASTNodes.getNormalizedNode(node);
		ASTNode parent = node.getParent();
		if (!(parent instanceof SingleVariableDeclaration) || !(parent.getParent() instanceof MethodDeclaration)) {
			return false;
		}
		SingleVariableDeclaration paramDecl = (SingleVariableDeclaration) parent;
		IVariableBinding binding = paramDecl.resolveBinding();

		MethodDeclaration methodDecl = (MethodDeclaration) parent.getParent();
		if (binding == null || methodDecl.getBody() == null) {
			return false;
		}
		ITypeBinding typeBinding = binding.getType();
		if (typeBinding == null) {
			return false;
		}

		if (resultingCollections == null) {
			return true;
		}

		ITypeBinding parentType = Bindings.getBindingOfParentType(node);
		if (parentType != null) {
			if (parentType.isInterface()) {
				return false;
			}
			// assign to existing fields
			CompilationUnit root = context.getASTRoot();
			IVariableBinding[] declaredFields = parentType.getDeclaredFields();
			boolean isStaticContext = ASTResolving.isInStaticContext(node);
			for (int i = 0; i < declaredFields.length; i++) {
				IVariableBinding curr = declaredFields[i];
				if (isStaticContext == Modifier.isStatic(curr.getModifiers()) && typeBinding.isAssignmentCompatible(curr.getType())) {
					ASTNode fieldDeclFrag = root.findDeclaringNode(curr);
					if (fieldDeclFrag instanceof VariableDeclarationFragment) {
						VariableDeclarationFragment fragment = (VariableDeclarationFragment) fieldDeclFrag;
						if (fragment.getInitializer() == null) {
							resultingCollections.add(new AssignToVariableAssistProposal(context.getCompilationUnit(), paramDecl, fragment, typeBinding, IProposalRelevance.ASSIGN_PARAM_TO_EXISTING_FIELD));
						}
					}
				}
			}
		}

		AssignToVariableAssistProposal fieldProposal = new AssignToVariableAssistProposal(context.getCompilationUnit(), paramDecl, null, typeBinding, IProposalRelevance.ASSIGN_PARAM_TO_NEW_FIELD);
		resultingCollections.add(fieldProposal);
		return true;
	}

	private static boolean getAssignAllParamsToFieldsProposals(IInvocationContext context, ASTNode node, Collection<ChangeCorrectionProposal> resultingCollections) {
		node = ASTNodes.getNormalizedNode(node);
		ASTNode parent = node.getParent();
		if (!(parent instanceof SingleVariableDeclaration) || !(parent.getParent() instanceof MethodDeclaration)) {
			return false;
		}
		MethodDeclaration methodDecl = (MethodDeclaration) parent.getParent();
		if (methodDecl.getBody() == null) {
			return false;
		}
		List<SingleVariableDeclaration> parameters = methodDecl.parameters();
		if (parameters.size() <= 1) {
			return false;
		}
		ITypeBinding parentType = Bindings.getBindingOfParentType(node);
		if (parentType == null || parentType.isInterface()) {
			return false;
		}
		for (SingleVariableDeclaration param : parameters) {
			IVariableBinding binding = param.resolveBinding();
			if (binding == null || binding.getType() == null) {
				return false;
			}
		}
		if (resultingCollections == null) {
			return true;
		}

		AssignToVariableAssistProposal fieldProposal = new AssignToVariableAssistProposal(context.getCompilationUnit(), parameters, IProposalRelevance.ASSIGN_ALL_PARAMS_TO_NEW_FIELDS);
		resultingCollections.add(fieldProposal);
		return true;
	}

	public static ArrayList<ASTNode> getFullyCoveredNodes(IInvocationContext context, ASTNode coveringNode) {
		final ArrayList<ASTNode> coveredNodes = new ArrayList<>();
		final int selectionBegin = context.getSelectionOffset();
		final int selectionEnd = selectionBegin + context.getSelectionLength();
		coveringNode.accept(new GenericVisitor() {
			@Override
			protected boolean visitNode(ASTNode node) {
				int nodeStart = node.getStartPosition();
				int nodeEnd = nodeStart + node.getLength();
				// if node does not intersects with selection, don't visit children
				if (nodeEnd < selectionBegin || selectionEnd < nodeStart) {
					return false;
				}
				// if node is fully covered, we don't need to visit children
				if (isCovered(node)) {
					ASTNode parent = node.getParent();
					if (parent == null || !isCovered(parent)) {
						coveredNodes.add(node);
						return false;
					}
				}
				// if node only partly intersects with selection, we try to find fully covered children
				return true;
			}

			private boolean isCovered(ASTNode node) {
				int begin = node.getStartPosition();
				int end = begin + node.getLength();
				return begin >= selectionBegin && end <= selectionEnd;
			}
		});
		return coveredNodes;
	}

	/**
	 * Returns the functional interface method being implemented by the given method
	 * reference.
	 *
	 * @param methodReference
	 *            the method reference to get the functional method
	 * @return the functional interface method being implemented by
	 *         <code>methodReference</code> or <code>null</code> if it could not be
	 *         derived
	 */
	public static IMethodBinding getFunctionalMethodForMethodReference(MethodReference methodReference) {
		ITypeBinding targetTypeBinding = ASTNodes.getTargetType(methodReference);
		if (targetTypeBinding == null) {
			return null;
		}

		IMethodBinding functionalMethod = targetTypeBinding.getFunctionalInterfaceMethod();
		if (functionalMethod.isSynthetic()) {
			functionalMethod = Bindings.findOverriddenMethodInType(functionalMethod.getDeclaringClass(), functionalMethod);
		}
		return functionalMethod;
	}

	/**
	 * Converts and replaces the given method reference with corresponding lambda
	 * expression in the given ASTRewrite.
	 *
	 * @param methodReference
	 *            the method reference to convert
	 * @param functionalMethod
	 *            the non-generic functional interface method to be implemented by
	 *            the lambda expression
	 * @param astRoot
	 *            the AST root
	 * @param rewrite
	 *            the ASTRewrite
	 * @param linkedProposalModel
	 *            to create linked proposals for lambda's parameters or
	 *            <code>null</code> if linked proposals are not required
	 * @param createBlockBody
	 *            <code>true</code> if lambda expression's body should be a block
	 *
	 * @return lambda expression used to replace the method reference in the given
	 *         ASTRewrite
	 * @throws JavaModelException
	 *             if an exception occurs while accessing the Java element
	 *             corresponding to the <code>functionalMethod</code>
	 */
	public static LambdaExpression convertMethodRefernceToLambda(MethodReference methodReference, IMethodBinding functionalMethod, CompilationUnit astRoot, ASTRewrite rewrite, LinkedProposalModelCore linkedProposalModel,
			boolean createBlockBody) throws JavaModelException {

		AST ast = astRoot.getAST();
		LambdaExpression lambda = ast.newLambdaExpression();

		String[] lambdaParamNames = getUniqueParameterNames(methodReference, functionalMethod);
		List<VariableDeclaration> lambdaParameters = lambda.parameters();
		for (int i = 0; i < lambdaParamNames.length; i++) {
			String paramName = lambdaParamNames[i];
			VariableDeclarationFragment lambdaParameter = ast.newVariableDeclarationFragment();
			SimpleName name = ast.newSimpleName(paramName);
			lambdaParameter.setName(name);
			lambdaParameters.add(lambdaParameter);
			if (linkedProposalModel != null) {
				linkedProposalModel.getPositionGroup(name.getIdentifier(), true).addPosition(rewrite.track(name), i == 0);
			}
		}

		int noOfLambdaParameters = lambdaParamNames.length;
		lambda.setParentheses(noOfLambdaParameters != 1);

		ITypeBinding returnTypeBinding = functionalMethod.getReturnType();
		IMethodBinding referredMethodBinding = methodReference.resolveMethodBinding(); // too often null, see bug 440000, bug 440344, bug 333665

		if (methodReference instanceof CreationReference) {
			CreationReference creationRef = (CreationReference) methodReference;
			Type type = creationRef.getType();
			if (type instanceof ArrayType) {
				ArrayCreation arrayCreation = ast.newArrayCreation();
				if (createBlockBody) {
					Block blockBody = getBlockBodyForLambda(arrayCreation, returnTypeBinding, ast);
					lambda.setBody(blockBody);
				} else {
					lambda.setBody(arrayCreation);
				}

				ArrayType arrayType = (ArrayType) type;
				Type copiedElementType = (Type) rewrite.createCopyTarget(arrayType.getElementType());
				arrayCreation.setType(ast.newArrayType(copiedElementType, arrayType.getDimensions()));
				SimpleName name = ast.newSimpleName(lambdaParamNames[0]);
				arrayCreation.dimensions().add(name);
				if (linkedProposalModel != null) {
					linkedProposalModel.getPositionGroup(name.getIdentifier(), false).addPosition(rewrite.track(name), LinkedPositionGroup.NO_STOP);
				}
			} else {
				ClassInstanceCreation cic = ast.newClassInstanceCreation();
				if (createBlockBody) {
					Block blockBody = getBlockBodyForLambda(cic, returnTypeBinding, ast);
					lambda.setBody(blockBody);
				} else {
					lambda.setBody(cic);
				}

				ITypeBinding typeBinding = type.resolveBinding();
				if (!(type instanceof ParameterizedType) && typeBinding != null && typeBinding.getTypeDeclaration().isGenericType()) {
					cic.setType(ast.newParameterizedType((Type) rewrite.createCopyTarget(type)));
				} else {
					cic.setType((Type) rewrite.createCopyTarget(type));
				}
				List<SimpleName> invocationArgs = getInvocationArguments(ast, 0, noOfLambdaParameters, lambdaParamNames);
				cic.arguments().addAll(invocationArgs);
				if (linkedProposalModel != null) {
					for (SimpleName name : invocationArgs) {
						linkedProposalModel.getPositionGroup(name.getIdentifier(), false).addPosition(rewrite.track(name), LinkedPositionGroup.NO_STOP);
					}
				}
				cic.typeArguments().addAll(getCopiedTypeArguments(rewrite, methodReference.typeArguments()));
			}

		} else if (referredMethodBinding != null && Modifier.isStatic(referredMethodBinding.getModifiers())) {
			MethodInvocation methodInvocation = ast.newMethodInvocation();
			if (createBlockBody) {
				Block blockBody = getBlockBodyForLambda(methodInvocation, returnTypeBinding, ast);
				lambda.setBody(blockBody);
			} else {
				lambda.setBody(methodInvocation);
			}

			Expression expr = null;
			boolean hasConflict = hasConflict(methodReference.getStartPosition(), referredMethodBinding, ScopeAnalyzer.METHODS | ScopeAnalyzer.CHECK_VISIBILITY, astRoot);
			if (hasConflict || !Bindings.isSuperType(referredMethodBinding.getDeclaringClass(), ASTNodes.getEnclosingType(methodReference)) || methodReference.typeArguments().size() != 0) {
				if (methodReference instanceof ExpressionMethodReference) {
					ExpressionMethodReference expressionMethodReference = (ExpressionMethodReference) methodReference;
					expr = (Expression) rewrite.createCopyTarget(expressionMethodReference.getExpression());
				} else if (methodReference instanceof TypeMethodReference) {
					Type type = ((TypeMethodReference) methodReference).getType();
					ITypeBinding typeBinding = type.resolveBinding();
					if (typeBinding != null) {
						ImportRewrite importRewrite = CodeStyleConfiguration.createImportRewrite(astRoot, true);
						expr = ast.newName(importRewrite.addImport(typeBinding));
					}
				}
			}
			methodInvocation.setExpression(expr);
			SimpleName methodName = getMethodInvocationName(methodReference);
			methodInvocation.setName((SimpleName) rewrite.createCopyTarget(methodName));
			List<SimpleName> invocationArgs = getInvocationArguments(ast, 0, noOfLambdaParameters, lambdaParamNames);
			methodInvocation.arguments().addAll(invocationArgs);
			if (linkedProposalModel != null) {
				for (SimpleName name : invocationArgs) {
					linkedProposalModel.getPositionGroup(name.getIdentifier(), false).addPosition(rewrite.track(name), LinkedPositionGroup.NO_STOP);
				}
			}
			methodInvocation.typeArguments().addAll(getCopiedTypeArguments(rewrite, methodReference.typeArguments()));

		} else if (methodReference instanceof SuperMethodReference) {
			SuperMethodInvocation superMethodInvocation = ast.newSuperMethodInvocation();
			if (createBlockBody) {
				Block blockBody = getBlockBodyForLambda(superMethodInvocation, returnTypeBinding, ast);
				lambda.setBody(blockBody);
			} else {
				lambda.setBody(superMethodInvocation);
			}

			Name superQualifier = ((SuperMethodReference) methodReference).getQualifier();
			if (superQualifier != null) {
				superMethodInvocation.setQualifier((Name) rewrite.createCopyTarget(superQualifier));
			}
			SimpleName methodName = getMethodInvocationName(methodReference);
			superMethodInvocation.setName((SimpleName) rewrite.createCopyTarget(methodName));
			List<SimpleName> invocationArgs = getInvocationArguments(ast, 0, noOfLambdaParameters, lambdaParamNames);
			superMethodInvocation.arguments().addAll(invocationArgs);
			if (linkedProposalModel != null) {
				for (SimpleName name : invocationArgs) {
					linkedProposalModel.getPositionGroup(name.getIdentifier(), false).addPosition(rewrite.track(name), LinkedPositionGroup.NO_STOP);
				}
			}
			superMethodInvocation.typeArguments().addAll(getCopiedTypeArguments(rewrite, methodReference.typeArguments()));

		} else {
			MethodInvocation methodInvocation = ast.newMethodInvocation();
			if (createBlockBody) {
				Block blockBody = getBlockBodyForLambda(methodInvocation, returnTypeBinding, ast);
				lambda.setBody(blockBody);
			} else {
				lambda.setBody(methodInvocation);
			}

			boolean isTypeReference = isTypeReferenceToInstanceMethod(methodReference);
			if (isTypeReference) {
				SimpleName name = ast.newSimpleName(lambdaParamNames[0]);
				methodInvocation.setExpression(name);
				if (linkedProposalModel != null) {
					linkedProposalModel.getPositionGroup(name.getIdentifier(), false).addPosition(rewrite.track(name), LinkedPositionGroup.NO_STOP);
				}
			} else {
				Expression expr = ((ExpressionMethodReference) methodReference).getExpression();
				if (!(expr instanceof ThisExpression && methodReference.typeArguments().size() == 0)) {
					methodInvocation.setExpression((Expression) rewrite.createCopyTarget(expr));
				}
			}
			SimpleName methodName = getMethodInvocationName(methodReference);
			methodInvocation.setName((SimpleName) rewrite.createCopyTarget(methodName));
			List<SimpleName> invocationArgs = getInvocationArguments(ast, isTypeReference ? 1 : 0, noOfLambdaParameters, lambdaParamNames);
			methodInvocation.arguments().addAll(invocationArgs);
			if (linkedProposalModel != null) {
				for (SimpleName name : invocationArgs) {
					linkedProposalModel.getPositionGroup(name.getIdentifier(), false).addPosition(rewrite.track(name), LinkedPositionGroup.NO_STOP);
				}
			}
			methodInvocation.typeArguments().addAll(getCopiedTypeArguments(rewrite, methodReference.typeArguments()));
		}

		rewrite.replace(methodReference, lambda, null);
		return lambda;
	}

	private static boolean hasConflict(int startPosition, IMethodBinding referredMethodBinding, int flags, CompilationUnit cu) {
		ScopeAnalyzer analyzer = new ScopeAnalyzer(cu);
		IBinding[] declarationsInScope = analyzer.getDeclarationsInScope(startPosition, flags);
		for (int i = 0; i < declarationsInScope.length; i++) {
			IBinding decl = declarationsInScope[i];
			if (decl.getName().equals(referredMethodBinding.getName()) && !referredMethodBinding.getMethodDeclaration().isEqualTo(decl)) {
				return true;
			}
		}
		return false;
	}

	private static String[] getUniqueParameterNames(MethodReference methodReference, IMethodBinding functionalMethod) throws JavaModelException {
		String[] parameterNames = ((IMethod) functionalMethod.getJavaElement()).getParameterNames();
		List<String> oldNames = new ArrayList<>(Arrays.asList(parameterNames));
		String[] newNames = new String[oldNames.size()];
		List<String> excludedNames = new ArrayList<>(ASTNodes.getVisibleLocalVariablesInScope(methodReference));

		for (int i = 0; i < oldNames.size(); i++) {
			String paramName = oldNames.get(i);
			List<String> allNamesToExclude = new ArrayList<>(excludedNames);
			allNamesToExclude.addAll(oldNames.subList(0, i));
			allNamesToExclude.addAll(oldNames.subList(i + 1, oldNames.size()));
			if (allNamesToExclude.contains(paramName)) {
				String newParamName = createName(paramName, allNamesToExclude);
				excludedNames.add(newParamName);
				newNames[i] = newParamName;
			} else {
				newNames[i] = paramName;
			}
		}
		return newNames;
	}

	private static String createName(String candidate, List<String> excludedNames) {
		int i = 1;
		String result = candidate;
		while (excludedNames.contains(result)) {
			result = candidate + i++;
		}
		return result;
	}

	private static boolean isTypeReferenceToInstanceMethod(MethodReference methodReference) {
		if (methodReference instanceof TypeMethodReference) {
			return true;
		}
		if (methodReference instanceof ExpressionMethodReference) {
			Expression expression = ((ExpressionMethodReference) methodReference).getExpression();
			if (expression instanceof Name) {
				IBinding nameBinding = ((Name) expression).resolveBinding();
				if (nameBinding != null && nameBinding instanceof ITypeBinding) {
					return true;
				}
			}
		}
		return false;
	}

	private static List<SimpleName> getInvocationArguments(AST ast, int begIndex, int noOfLambdaParameters, String[] lambdaParamNames) {
		List<SimpleName> args = new ArrayList<>();
		for (int i = begIndex; i < noOfLambdaParameters; i++) {
			args.add(ast.newSimpleName(lambdaParamNames[i]));
		}
		return args;
	}

	private static List<Type> getCopiedTypeArguments(ASTRewrite rewrite, List<Type> typeArguments) {
		List<Type> copiedTypeArgs = new ArrayList<>();
		for (Type typeArg : typeArguments) {
			copiedTypeArgs.add((Type) rewrite.createCopyTarget(typeArg));
		}
		return copiedTypeArgs;
	}

	private static SimpleName getMethodInvocationName(MethodReference methodReference) {
		SimpleName name = null;
		if (methodReference instanceof ExpressionMethodReference) {
			name = ((ExpressionMethodReference) methodReference).getName();
		} else if (methodReference instanceof TypeMethodReference) {
			name = ((TypeMethodReference) methodReference).getName();
		} else if (methodReference instanceof SuperMethodReference) {
			name = ((SuperMethodReference) methodReference).getName();
		}
		return name;
	}

	public static void changeLambdaBodyToBlock(LambdaExpression lambda, AST ast, ASTRewrite rewrite) {
		Expression bodyExpr = (Expression) rewrite.createMoveTarget(lambda.getBody());
		Block blockBody = getBlockBodyForLambda(bodyExpr, lambda.resolveMethodBinding().getReturnType(), ast);
		rewrite.set(lambda, LambdaExpression.BODY_PROPERTY, blockBody, null);
	}

	private static Block getBlockBodyForLambda(Expression bodyExpr, ITypeBinding returnTypeBinding, AST ast) {
		Statement statementInBlockBody;
		if (ast.resolveWellKnownType("void").isEqualTo(returnTypeBinding)) { //$NON-NLS-1$
			ExpressionStatement expressionStatement = ast.newExpressionStatement(bodyExpr);
			statementInBlockBody = expressionStatement;
		} else {
			ReturnStatement returnStatement = ast.newReturnStatement();
			returnStatement.setExpression(bodyExpr);
			statementInBlockBody = returnStatement;
		}
		Block blockBody = ast.newBlock();
		blockBody.statements().add(statementInBlockBody);
		return blockBody;
	}

	public static boolean getCatchClauseToThrowsProposals(IInvocationContext context, ASTNode node, Collection<ChangeCorrectionProposal> resultingCollections) {
		if (resultingCollections == null) {
			return true;
		}

		CatchClause catchClause = (CatchClause) ASTResolving.findAncestor(node, ASTNode.CATCH_CLAUSE);
		if (catchClause == null) {
			return false;
		}

		Statement statement = ASTResolving.findParentStatement(node);
		if (statement != catchClause.getParent() && statement != catchClause.getBody()) {
			return false; // selection is in a statement inside the body
		}

		Type type = catchClause.getException().getType();
		if (!type.isSimpleType() && !type.isUnionType() && !type.isNameQualifiedType()) {
			return false;
		}

		BodyDeclaration bodyDeclaration = ASTResolving.findParentBodyDeclaration(catchClause);
		if (!(bodyDeclaration instanceof MethodDeclaration) && !(bodyDeclaration instanceof Initializer)) {
			return false;
		}

		AST ast = bodyDeclaration.getAST();

		Type selectedMultiCatchType = null;
		if (type.isUnionType() && node instanceof Name) {
			Name topMostName = ASTNodes.getTopMostName((Name) node);
			ASTNode parent = topMostName.getParent();
			if (parent instanceof SimpleType) {
				selectedMultiCatchType = (SimpleType) parent;
			} else if (parent instanceof NameQualifiedType) {
				selectedMultiCatchType = (NameQualifiedType) parent;
			}
		}

		if (bodyDeclaration instanceof MethodDeclaration) {
			MethodDeclaration methodDeclaration = (MethodDeclaration) bodyDeclaration;

			ASTRewrite rewrite = ASTRewrite.create(ast);
			if (selectedMultiCatchType != null) {
				removeException(rewrite, (UnionType) type, selectedMultiCatchType);
				addExceptionToThrows(ast, methodDeclaration, rewrite, selectedMultiCatchType);
				String label = CorrectionMessages.QuickAssistProcessor_exceptiontothrows_description;
				ASTRewriteCorrectionProposal proposal = new ASTRewriteCorrectionProposal(label, CodeActionKind.QuickFix, context.getCompilationUnit(), rewrite, IProposalRelevance.REPLACE_EXCEPTION_WITH_THROWS);
				resultingCollections.add(proposal);
			} else {
				removeCatchBlock(rewrite, catchClause);
				if (type.isUnionType()) {
					UnionType unionType = (UnionType) type;
					List<Type> types = unionType.types();
					for (Type elementType : types) {
						if (!(elementType instanceof SimpleType || elementType instanceof NameQualifiedType)) {
							return false;
						}
						addExceptionToThrows(ast, methodDeclaration, rewrite, elementType);
					}
				} else {
					addExceptionToThrows(ast, methodDeclaration, rewrite, type);
				}
				String label = CorrectionMessages.QuickAssistProcessor_catchclausetothrows_description;
				ASTRewriteCorrectionProposal proposal = new ASTRewriteCorrectionProposal(label, CodeActionKind.QuickFix, context.getCompilationUnit(), rewrite, IProposalRelevance.REPLACE_CATCH_CLAUSE_WITH_THROWS);
				resultingCollections.add(proposal);
			}
		}
		{ // for initializers or method declarations
			ASTRewrite rewrite = ASTRewrite.create(ast);
			if (selectedMultiCatchType != null) {
				removeException(rewrite, (UnionType) type, selectedMultiCatchType);
				String label = CorrectionMessages.QuickAssistProcessor_removeexception_description;
				ASTRewriteCorrectionProposal proposal = new ASTRewriteCorrectionProposal(label, CodeActionKind.QuickFix, context.getCompilationUnit(), rewrite, IProposalRelevance.REMOVE_EXCEPTION);
				resultingCollections.add(proposal);
			} else {
				removeCatchBlock(rewrite, catchClause);
				String label = CorrectionMessages.QuickAssistProcessor_removecatchclause_description;
				ASTRewriteCorrectionProposal proposal = new ASTRewriteCorrectionProposal(label, CodeActionKind.QuickFix, context.getCompilationUnit(), rewrite, IProposalRelevance.REMOVE_CATCH_CLAUSE);
				resultingCollections.add(proposal);
			}
		}

		return true;
	}

	private static void removeException(ASTRewrite rewrite, UnionType unionType, Type exception) {
		ListRewrite listRewrite = rewrite.getListRewrite(unionType, UnionType.TYPES_PROPERTY);
		List<Type> types = unionType.types();
		for (Iterator<Type> iterator = types.iterator(); iterator.hasNext();) {
			Type type = iterator.next();
			if (type.equals(exception)) {
				listRewrite.remove(type, null);
			}
		}
	}

	private static void addExceptionToThrows(AST ast, MethodDeclaration methodDeclaration, ASTRewrite rewrite, Type type2) {
		ITypeBinding binding = type2.resolveBinding();
		if (binding == null || isNotYetThrown(binding, methodDeclaration.thrownExceptionTypes())) {
			Type newType = (Type) ASTNode.copySubtree(ast, type2);

			ListRewrite listRewriter = rewrite.getListRewrite(methodDeclaration, MethodDeclaration.THROWN_EXCEPTION_TYPES_PROPERTY);
			listRewriter.insertLast(newType, null);
		}
	}

	private static void removeCatchBlock(ASTRewrite rewrite, CatchClause catchClause) {
		TryStatement tryStatement = (TryStatement) catchClause.getParent();
		if (tryStatement.catchClauses().size() > 1 || tryStatement.getFinally() != null || !tryStatement.resources().isEmpty()) {
			rewrite.remove(catchClause, null);
		} else {
			Block block = tryStatement.getBody();
			List<Statement> statements = block.statements();
			int nStatements = statements.size();
			if (nStatements == 1) {
				ASTNode first = statements.get(0);
				rewrite.replace(tryStatement, rewrite.createCopyTarget(first), null);
			} else if (nStatements > 1) {
				ListRewrite listRewrite = rewrite.getListRewrite(block, Block.STATEMENTS_PROPERTY);
				ASTNode first = statements.get(0);
				ASTNode last = statements.get(statements.size() - 1);
				ASTNode newStatement = listRewrite.createCopyTarget(first, last);
				if (ASTNodes.isControlStatementBody(tryStatement.getLocationInParent())) {
					Block newBlock = rewrite.getAST().newBlock();
					newBlock.statements().add(newStatement);
					newStatement = newBlock;
				}
				rewrite.replace(tryStatement, newStatement, null);
			} else {
				rewrite.remove(tryStatement, null);
			}
		}
	}

	private static boolean isNotYetThrown(ITypeBinding binding, List<Type> thrownExceptions) {
		for (Type thrownException : thrownExceptions) {
			ITypeBinding elem = thrownException.resolveBinding();
			if (elem != null) {
				if (Bindings.isSuperType(elem, binding)) { // existing exception is base class of new
					return false;
				}
			}
		}
		return true;
	}

	private static boolean getConvertMethodReferenceToLambdaProposal(IInvocationContext context, ASTNode covering, Collection<ChangeCorrectionProposal> resultingCollections) throws JavaModelException {
		MethodReference methodReference;
		if (covering instanceof MethodReference) {
			methodReference = (MethodReference) covering;
		} else if (covering.getParent() instanceof MethodReference) {
			methodReference = (MethodReference) covering.getParent();
		} else {
			return false;
		}

		IMethodBinding functionalMethod = getFunctionalMethodForMethodReference(methodReference);
		if (functionalMethod == null || functionalMethod.isGenericMethod()) { // generic lambda expressions are not allowed
			return false;
		}

		if (resultingCollections == null) {
			return true;
		}

		ASTRewrite rewrite = ASTRewrite.create(methodReference.getAST());
		LinkedProposalModelCore linkedProposalModel = new LinkedProposalModelCore();

		LambdaExpression lambda = convertMethodRefernceToLambda(methodReference, functionalMethod, context.getASTRoot(), rewrite, linkedProposalModel, false);

		// add proposal
		String label = CorrectionMessages.QuickAssistProcessor_convert_to_lambda_expression;
		LinkedCorrectionProposal proposal = new LinkedCorrectionProposal(label, CodeActionKind.QuickFix, context.getCompilationUnit(), rewrite, IProposalRelevance.CONVERT_METHOD_REFERENCE_TO_LAMBDA);
		proposal.setLinkedProposalModel(linkedProposalModel);
		proposal.setEndPosition(rewrite.track(lambda));
		resultingCollections.add(proposal);
		return true;
	}

	private static boolean getConvertLambdaToMethodReferenceProposal(IInvocationContext context, ASTNode coveringNode, Collection<ChangeCorrectionProposal> resultingCollections) {
		LambdaExpression lambda;
		if (coveringNode instanceof LambdaExpression) {
			lambda = (LambdaExpression) coveringNode;
		} else if (coveringNode.getLocationInParent() == LambdaExpression.BODY_PROPERTY) {
			lambda = (LambdaExpression) coveringNode.getParent();
		} else {
			lambda = ASTResolving.findEnclosingLambdaExpression(coveringNode);
			if (lambda == null) {
				return false;
			}
		}

		ASTNode lambdaBody = lambda.getBody();
		Expression exprBody;
		if (lambdaBody instanceof Block) {
			exprBody = getSingleExpressionFromLambdaBody((Block) lambdaBody);
		} else {
			exprBody = (Expression) lambdaBody;
		}
		exprBody = ASTNodes.getUnparenthesedExpression(exprBody);
		if (exprBody == null || !isValidLambdaReferenceToMethod(exprBody)) {
			return false;
		}

		if (!ASTNodes.isParent(exprBody, coveringNode) && !representsDefiningNode(coveringNode, exprBody)) {
			return false;
		}

		List<Expression> lambdaParameters = new ArrayList<>();
		for (VariableDeclaration param : (List<VariableDeclaration>) lambda.parameters()) {
			lambdaParameters.add(param.getName());
		}
		if (exprBody instanceof ClassInstanceCreation) {
			ClassInstanceCreation cic = (ClassInstanceCreation) exprBody;
			if (cic.getExpression() != null || cic.getAnonymousClassDeclaration() != null) {
				return false;
			}
			if (!matches(lambdaParameters, cic.arguments())) {
				return false;
			}
		} else if (exprBody instanceof ArrayCreation) {
			List<Expression> dimensions = ((ArrayCreation) exprBody).dimensions();
			if (dimensions.size() != 1) {
				return false;
			}
			if (!matches(lambdaParameters, dimensions)) {
				return false;
			}
		} else if (exprBody instanceof SuperMethodInvocation) {
			SuperMethodInvocation superMethodInvocation = (SuperMethodInvocation) exprBody;
			IMethodBinding methodBinding = superMethodInvocation.resolveMethodBinding();
			if (methodBinding == null) {
				return false;
			}
			if (Modifier.isStatic(methodBinding.getModifiers())) {
				ITypeBinding invocationTypeBinding = ASTNodes.getInvocationType(superMethodInvocation, methodBinding, superMethodInvocation.getQualifier());
				if (invocationTypeBinding == null) {
					return false;
				}
			}
			if (!matches(lambdaParameters, superMethodInvocation.arguments())) {
				return false;
			}
		} else { // MethodInvocation
			MethodInvocation methodInvocation = (MethodInvocation) exprBody;
			IMethodBinding methodBinding = methodInvocation.resolveMethodBinding();
			if (methodBinding == null) {
				return false;
			}

			Expression invocationExpr = methodInvocation.getExpression();
			if (Modifier.isStatic(methodBinding.getModifiers())) {
				ITypeBinding invocationTypeBinding = ASTNodes.getInvocationType(methodInvocation, methodBinding, invocationExpr);
				if (invocationTypeBinding == null) {
					return false;
				}
				if (!matches(lambdaParameters, methodInvocation.arguments())) {
					return false;
				}
			} else if ((lambda.parameters().size() - methodInvocation.arguments().size()) == 1) {
				if (invocationExpr == null) {
					return false;
				}
				ITypeBinding invocationTypeBinding = invocationExpr.resolveTypeBinding();
				if (invocationTypeBinding == null) {
					return false;
				}
				IMethodBinding lambdaMethodBinding = lambda.resolveMethodBinding();
				if (lambdaMethodBinding == null) {
					return false;
				}
				ITypeBinding firstParamType = lambdaMethodBinding.getParameterTypes()[0];
				if ((!Bindings.equals(invocationTypeBinding, firstParamType) && !Bindings.isSuperType(invocationTypeBinding, firstParamType)) || !JdtASTMatcher.doNodesMatch(lambdaParameters.get(0), invocationExpr)
						|| !matches(lambdaParameters.subList(1, lambdaParameters.size()), methodInvocation.arguments())) {
					return false;
				}
			} else if (!matches(lambdaParameters, methodInvocation.arguments())) {
				return false;
			}
		}

		if (resultingCollections == null) {
			return true;
		}

		AST ast = lambda.getAST();
		ASTRewrite rewrite = ASTRewrite.create(ast);
		ImportRewrite importRewrite = null;
		MethodReference replacement;

		if (exprBody instanceof ClassInstanceCreation) {
			CreationReference creationReference = ast.newCreationReference();
			replacement = creationReference;

			ClassInstanceCreation cic = (ClassInstanceCreation) exprBody;
			Type type = cic.getType();
			if (type.isParameterizedType() && ((ParameterizedType) type).typeArguments().size() == 0) {
				type = ((ParameterizedType) type).getType();
			}
			creationReference.setType((Type) rewrite.createCopyTarget(type));
			creationReference.typeArguments().addAll(getCopiedTypeArguments(rewrite, cic.typeArguments()));
		} else if (exprBody instanceof ArrayCreation) {
			CreationReference creationReference = ast.newCreationReference();
			replacement = creationReference;

			ArrayType arrayType = ((ArrayCreation) exprBody).getType();
			Type copiedElementType = (Type) rewrite.createCopyTarget(arrayType.getElementType());
			creationReference.setType(ast.newArrayType(copiedElementType, arrayType.getDimensions()));
		} else if (exprBody instanceof SuperMethodInvocation) {
			SuperMethodInvocation superMethodInvocation = (SuperMethodInvocation) exprBody;
			IMethodBinding methodBinding = superMethodInvocation.resolveMethodBinding();
			Name superQualifier = superMethodInvocation.getQualifier();

			if (Modifier.isStatic(methodBinding.getModifiers())) {
				TypeMethodReference typeMethodReference = ast.newTypeMethodReference();
				replacement = typeMethodReference;

				typeMethodReference.setName((SimpleName) rewrite.createCopyTarget(superMethodInvocation.getName()));
				importRewrite = StubUtility.createImportRewrite(context.getASTRoot(), true);
				ITypeBinding invocationTypeBinding = ASTNodes.getInvocationType(superMethodInvocation, methodBinding, superQualifier);
				typeMethodReference.setType(importRewrite.addImport(invocationTypeBinding.getTypeDeclaration(), ast));
				typeMethodReference.typeArguments().addAll(getCopiedTypeArguments(rewrite, superMethodInvocation.typeArguments()));
			} else {
				SuperMethodReference superMethodReference = ast.newSuperMethodReference();
				replacement = superMethodReference;

				if (superQualifier != null) {
					superMethodReference.setQualifier((Name) rewrite.createCopyTarget(superQualifier));
				}
				superMethodReference.setName((SimpleName) rewrite.createCopyTarget(superMethodInvocation.getName()));
				superMethodReference.typeArguments().addAll(getCopiedTypeArguments(rewrite, superMethodInvocation.typeArguments()));
			}
		} else { // MethodInvocation
			MethodInvocation methodInvocation = (MethodInvocation) exprBody;
			IMethodBinding methodBinding = methodInvocation.resolveMethodBinding();
			Expression invocationQualifier = methodInvocation.getExpression();

			boolean isStaticMethod = Modifier.isStatic(methodBinding.getModifiers());
			boolean isTypeRefToInstanceMethod = methodInvocation.arguments().size() != lambda.parameters().size();

			if (isStaticMethod || isTypeRefToInstanceMethod) {
				TypeMethodReference typeMethodReference = ast.newTypeMethodReference();
				replacement = typeMethodReference;

				typeMethodReference.setName((SimpleName) rewrite.createCopyTarget(methodInvocation.getName()));
				importRewrite = StubUtility.createImportRewrite(context.getASTRoot(), true);
				ITypeBinding invocationTypeBinding = ASTNodes.getInvocationType(methodInvocation, methodBinding, invocationQualifier);
				invocationTypeBinding = StubUtility2Core.replaceWildcardsAndCaptures(invocationTypeBinding);
				ImportRewriteContext importRewriteContext = new ContextSensitiveImportRewriteContext(lambda, importRewrite);
				typeMethodReference.setType(importRewrite.addImport(invocationTypeBinding, ast, importRewriteContext, TypeLocation.OTHER));
				typeMethodReference.typeArguments().addAll(getCopiedTypeArguments(rewrite, methodInvocation.typeArguments()));

			} else {
				ExpressionMethodReference exprMethodReference = ast.newExpressionMethodReference();
				replacement = exprMethodReference;

				exprMethodReference.setName((SimpleName) rewrite.createCopyTarget(methodInvocation.getName()));
				if (invocationQualifier != null) {
					exprMethodReference.setExpression((Expression) rewrite.createCopyTarget(invocationQualifier));
				} else {
					// check if method is in class scope or in super/nested class scope
					TypeDeclaration lambdaParentType = (TypeDeclaration) ASTResolving.findParentType(lambda);
					ITypeBinding lambdaMethodInvokingClass = lambdaParentType.resolveBinding();
					ITypeBinding lambdaMethodDeclaringClass = methodBinding.getDeclaringClass();

					ThisExpression newThisExpression = ast.newThisExpression();

					ITypeBinding nestedRootClass = getNestedRootClass(lambdaMethodInvokingClass);
					boolean isSuperClass = isSuperClass(lambdaMethodDeclaringClass, lambdaMethodInvokingClass);
					boolean isNestedClass = isNestedClass(lambdaMethodDeclaringClass, lambdaMethodInvokingClass);

					if (lambdaMethodDeclaringClass == lambdaMethodInvokingClass) {
						// use this::
					} else if (Modifier.isDefault(methodBinding.getModifiers())) {
						boolean nestedInterfaceClass = isNestedInterfaceClass(ast, lambdaMethodDeclaringClass, lambdaMethodInvokingClass);
						if (isNestedClass) {
							// use this::
						} else if (nestedInterfaceClass && !isNestedClass && !isSuperClass) {
							// use this::
						} else if (!nestedInterfaceClass || (nestedRootClass != lambdaMethodInvokingClass)) {
							newThisExpression.setQualifier(ast.newName(nestedRootClass.getName()));
						}
					} else if (lambdaMethodDeclaringClass.isInterface()) {
						if (isSuperClass) {
							// use this::
						} else {
							newThisExpression.setQualifier(ast.newName(nestedRootClass.getName()));
						}
					} else if (isSuperClass) {
						// use this::
					} else {
						newThisExpression.setQualifier(ast.newName(nestedRootClass.getName()));
					}
					exprMethodReference.setExpression(newThisExpression);
				}
				exprMethodReference.typeArguments().addAll(getCopiedTypeArguments(rewrite, methodInvocation.typeArguments()));
			}
		}

		rewrite.replace(lambda, replacement, null);

		// add correction proposal
		String label = CorrectionMessages.QuickAssistProcessor_convert_to_method_reference;
		ASTRewriteCorrectionProposal proposal = new ASTRewriteCorrectionProposal(label, CodeActionKind.QuickFix, context.getCompilationUnit(), rewrite, IProposalRelevance.CONVERT_TO_METHOD_REFERENCE);
		if (importRewrite != null) {
			proposal.setImportRewrite(importRewrite);
		}
		resultingCollections.add(proposal);
		return true;
	}

	private static Expression getSingleExpressionFromLambdaBody(Block lambdaBody) {
		if (lambdaBody.statements().size() != 1) {
			return null;
		}
		Statement singleStatement = (Statement) lambdaBody.statements().get(0);
		if (singleStatement instanceof ReturnStatement) {
			return ((ReturnStatement) singleStatement).getExpression();
		} else if (singleStatement instanceof ExpressionStatement) {
			Expression expression = ((ExpressionStatement) singleStatement).getExpression();
			if (isValidLambdaExpressionBody(expression)) {
				return expression;
			}
		}
		return null;
	}

	private static boolean isValidLambdaExpressionBody(Expression expression) {
		if (expression instanceof Assignment || expression instanceof ClassInstanceCreation || expression instanceof MethodInvocation || expression instanceof PostfixExpression || expression instanceof SuperMethodInvocation) {
			return true;
		}
		if (expression instanceof PrefixExpression) {
			Operator operator = ((PrefixExpression) expression).getOperator();
			if (operator == Operator.INCREMENT || operator == Operator.DECREMENT) {
				return true;
			}
		}
		return false;
	}

	private static boolean isValidLambdaReferenceToMethod(Expression expression) {
		return expression instanceof ClassInstanceCreation || expression instanceof ArrayCreation || expression instanceof SuperMethodInvocation || expression instanceof MethodInvocation;
	}

	private static boolean matches(List<Expression> expected, List<Expression> toMatch) {
		if (toMatch.size() != expected.size()) {
			return false;
		}
		for (int i = 0; i < toMatch.size(); i++) {
			if (!JdtASTMatcher.doNodesMatch(expected.get(i), toMatch.get(i))) {
				return false;
			}
		}
		return true;
	}

	private static boolean representsDefiningNode(ASTNode innerNode, ASTNode definingNode) {
		// Example: We want to enable the proposal when the method invocation node or
		// the method name is near the caret. But not when the caret is on an argument of the method invocation.
		if (innerNode == definingNode) {
			return true;
		}

		switch (definingNode.getNodeType()) {
			// types from isValidLambdaReferenceToMethod():
			case ASTNode.CLASS_INSTANCE_CREATION:
				return representsDefiningNode(innerNode, ((ClassInstanceCreation) definingNode).getType());
			case ASTNode.ARRAY_CREATION:
				return representsDefiningNode(innerNode, ((ArrayCreation) definingNode).getType());
			case ASTNode.SUPER_METHOD_INVOCATION:
				return innerNode == ((SuperMethodInvocation) definingNode).getName();
			case ASTNode.METHOD_INVOCATION:
				return innerNode == ((MethodInvocation) definingNode).getName();

			// subtypes of Type:
			case ASTNode.NAME_QUALIFIED_TYPE:
				return innerNode == ((NameQualifiedType) definingNode).getName();
			case ASTNode.QUALIFIED_TYPE:
				return innerNode == ((QualifiedType) definingNode).getName();
			case ASTNode.SIMPLE_TYPE:
				return innerNode == ((SimpleType) definingNode).getName();
			case ASTNode.ARRAY_TYPE:
				return representsDefiningNode(innerNode, ((ArrayType) definingNode).getElementType());
			case ASTNode.PARAMETERIZED_TYPE:
				return representsDefiningNode(innerNode, ((ParameterizedType) definingNode).getType());

			default:
				return false;
		}
	}

	private static boolean isNestedInterfaceClass(AST ast, ITypeBinding lambdaMethodDeclaringClass, ITypeBinding lambdaMethodInvokingClass) {
		ITypeBinding[] methodNarrowingTypes = ASTResolving.getRelaxingTypes(ast, lambdaMethodDeclaringClass);
		ITypeBinding[] lambdaNarrowingTypes = ASTResolving.getRelaxingTypes(ast, lambdaMethodInvokingClass);

		if (methodNarrowingTypes.length != 1) {
			return false;
		}
		ITypeBinding methodNarrowingType = methodNarrowingTypes[0];
		for (ITypeBinding lambdaNarrowingType : lambdaNarrowingTypes) {
			if (methodNarrowingType == lambdaNarrowingType) {
				return true;
			}
		}
		return false;
	}

	/*
	 * return TRUE if lambda declaration class is nested class of method declaration class
	 */
	private static boolean isNestedClass(ITypeBinding methodDeclarationType, ITypeBinding lambdaDeclarationType) {
		ITypeBinding parent = lambdaDeclarationType;
		while (parent.isNested()) {
			parent = parent.getDeclaringClass();
			if (parent == methodDeclarationType) {
				return true;
			}
		}
		return false;
	}

	private static ITypeBinding getNestedRootClass(ITypeBinding lambdaDeclarationType) {
		ITypeBinding parent = lambdaDeclarationType;
		while (parent.isNested()) {
			parent = parent.getDeclaringClass();
		}
		return parent;
	}

	/*
	 * return TRUE if method declaration class is super class of lambda declaration class
	 */
	private static boolean isSuperClass(ITypeBinding methodDeclarationType, ITypeBinding lambdaDeclarationType) {
		ITypeBinding parent = lambdaDeclarationType.getSuperclass();
		while (parent != null) {
			if (parent == methodDeclarationType) {
				return true;
			}
			parent = parent.getSuperclass();
		}
		return false;
	}
}
