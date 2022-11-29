/*******************************************************************************
 * Copyright (c) 2000, 2017 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Copied from /org.eclipse.jdt.ui/src/org/eclipse/jdt/internal/ui/text/correction/proposals/NewMethodCorrectionProposal.java
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *     Benjamin Muskalla - [quick fix] Create Method in void context should 'box' void. - https://bugs.eclipse.org/bugs/show_bug.cgi?id=107985
 *******************************************************************************/
package org.eclipse.jdt.ls.core.internal.corrections.proposals;

import java.util.List;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.NamingConventions;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.AnonymousClassDeclaration;
import org.eclipse.jdt.core.dom.BodyDeclaration;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.ExpressionStatement;
import org.eclipse.jdt.core.dom.IBinding;
import org.eclipse.jdt.core.dom.IExtendedModifier;
import org.eclipse.jdt.core.dom.IMethodBinding;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.Modifier;
import org.eclipse.jdt.core.dom.Name;
import org.eclipse.jdt.core.dom.PrimitiveType;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.core.dom.SingleVariableDeclaration;
import org.eclipse.jdt.core.dom.SuperMethodInvocation;
import org.eclipse.jdt.core.dom.Type;
import org.eclipse.jdt.core.dom.TypeDeclaration;
import org.eclipse.jdt.core.dom.TypeParameter;
import org.eclipse.jdt.core.dom.rewrite.ASTRewrite;
import org.eclipse.jdt.core.dom.rewrite.ImportRewrite.ImportRewriteContext;
import org.eclipse.jdt.core.dom.rewrite.ImportRewrite.TypeLocation;
import org.eclipse.jdt.internal.core.manipulation.StubUtility;
import org.eclipse.jdt.internal.core.manipulation.dom.ASTResolving;
import org.eclipse.jdt.internal.corext.dom.ASTNodes;
import org.eclipse.jdt.internal.corext.dom.Bindings;
import org.eclipse.jdt.internal.corext.util.JavaModelUtil;
import org.eclipse.jdt.internal.corext.util.JdtFlags;


public class NewMethodCorrectionProposal extends AbstractMethodCorrectionProposal {

	private static final String KEY_NAME= "name"; //$NON-NLS-1$
	private static final String KEY_TYPE= "type"; //$NON-NLS-1$

	private List<Expression> fArguments;

	//	invocationNode is MethodInvocation, ConstructorInvocation, SuperConstructorInvocation, ClassInstanceCreation, SuperMethodInvocation
	public NewMethodCorrectionProposal(String label, ICompilationUnit targetCU, ASTNode invocationNode,  List<Expression> arguments, ITypeBinding binding, int relevance) {
		super(label, targetCU, invocationNode, binding, relevance);
		fArguments= arguments;
	}

	private int evaluateModifiers(ASTNode targetTypeDecl) {
		if (getSenderBinding().isAnnotation()) {
			return 0;
		}
		boolean isTargetInterface= getSenderBinding().isInterface();
		if (isTargetInterface && !JavaModelUtil.is1d8OrHigher(getCompilationUnit().getJavaProject())) {
			// only abstract methods are allowed for interface present in less than Java 1.8
			return getInterfaceMethodModifiers(targetTypeDecl, true);
		}
		ASTNode invocationNode= getInvocationNode();
		if (invocationNode instanceof MethodInvocation methodInvocation) {
			int modifiers= 0;
			Expression expression = methodInvocation.getExpression();
			if (expression != null) {
				if (expression instanceof Name name && name.resolveBinding().getKind() == IBinding.TYPE) {
					modifiers |= Modifier.STATIC;
				}
			} else if (ASTResolving.isInStaticContext(invocationNode)) {
				modifiers |= Modifier.STATIC;
			}
			ASTNode node= ASTResolving.findParentType(invocationNode);
			boolean isParentInterface = node instanceof TypeDeclaration typeDecl && typeDecl.isInterface();
			if (isTargetInterface || isParentInterface) {
				if (expression == null && !targetTypeDecl.equals(node)) {
					modifiers|= Modifier.STATIC;
					if (isTargetInterface) {
						modifiers|= getInterfaceMethodModifiers(targetTypeDecl, false);
					} else {
						modifiers|= Modifier.PROTECTED;
					}
				} else if (modifiers == Modifier.STATIC) {
					modifiers= getInterfaceMethodModifiers(targetTypeDecl, false) | Modifier.STATIC;
				} else {
					modifiers= getInterfaceMethodModifiers(targetTypeDecl, true);
				}
			} else if (targetTypeDecl.equals(node)) {
				modifiers |= Modifier.PRIVATE;
			} else if (node instanceof AnonymousClassDeclaration && ASTNodes.isParent(node, targetTypeDecl)) {
				modifiers |= Modifier.PROTECTED;
				if (ASTResolving.isInStaticContext(node) && expression == null) {
					modifiers |= Modifier.STATIC;
				}
			} else {
				modifiers |= Modifier.PUBLIC;
			}
			return modifiers;
		}
		return Modifier.PUBLIC;
	}

	private int getInterfaceMethodModifiers(ASTNode targetTypeDecl, boolean createAbstractMethod) {
		// for interface and annotation members copy the modifiers from an existing member
		if (targetTypeDecl instanceof TypeDeclaration type) {
			MethodDeclaration[] methodDecls= type.getMethods();
			if (methodDecls.length > 0) {
				if (createAbstractMethod) {
					for (MethodDeclaration methodDeclaration : methodDecls) {
						IMethodBinding methodBinding= methodDeclaration.resolveBinding();
						if (methodBinding != null && JdtFlags.isAbstract(methodBinding)) {
							return methodDeclaration.getModifiers();
						}
					}
				}
				return methodDecls[0].getModifiers() & Modifier.PUBLIC;
			}
			List<BodyDeclaration> bodyDecls= type.bodyDeclarations();
			if (bodyDecls.size() > 0) {
				return bodyDecls.get(0).getModifiers() & Modifier.PUBLIC;
			}
		}
		return 0;
	}

	@Override
	protected void addNewModifiers(ASTRewrite rewrite, ASTNode targetTypeDecl, List<IExtendedModifier> modifiers) {
		modifiers.addAll(rewrite.getAST().newModifiers(evaluateModifiers(targetTypeDecl)));
	}

	@Override
	protected boolean isConstructor() {
		ASTNode node= getInvocationNode();

		return node.getNodeType() != ASTNode.METHOD_INVOCATION && node.getNodeType() != ASTNode.SUPER_METHOD_INVOCATION;
	}

	@Override
	protected SimpleName getNewName(ASTRewrite rewrite) {
		ASTNode invocationNode= getInvocationNode();
		String name;
		if (invocationNode instanceof MethodInvocation methodInvocation) {
			name = methodInvocation.getName().getIdentifier();
		} else if (invocationNode instanceof SuperMethodInvocation superInvocation) {
			name = superInvocation.getName().getIdentifier();
		} else {
			name= getSenderBinding().getName(); // name of the class
		}
		AST ast= rewrite.getAST();
		SimpleName newNameNode= ast.newSimpleName(name);
		return newNameNode;
	}

	@Override
	protected Type getNewMethodType(ASTRewrite rewrite, ImportRewriteContext importRewriteContext) throws CoreException {
		ASTNode node= getInvocationNode();
		AST ast= rewrite.getAST();

		Type newTypeNode= null;

		if (node.getParent() instanceof MethodInvocation parent) {
			if (parent.getExpression() == node) {
				ITypeBinding[] bindings= ASTResolving.getQualifierGuess(node.getRoot(), parent.getName().getIdentifier(), parent.arguments(), getSenderBinding());
				if (bindings.length > 0) {
					newTypeNode= getImportRewrite().addImport(bindings[0], ast, importRewriteContext, TypeLocation.RETURN_TYPE);
				}
			}
		}
		if (newTypeNode == null) {
			ITypeBinding binding= ASTResolving.guessBindingForReference(node);
			if (binding != null && binding.isWildcardType()) {
				binding= ASTResolving.normalizeWildcardType(binding, false, ast);
			}
			if (binding != null) {
				newTypeNode= getImportRewrite().addImport(binding, ast, importRewriteContext, TypeLocation.RETURN_TYPE);
			} else {
				ASTNode parent= node.getParent();
				if (parent instanceof ExpressionStatement) {
					newTypeNode= ast.newPrimitiveType(PrimitiveType.VOID);
				} else {
					newTypeNode = org.eclipse.jdt.ls.core.internal.corrections.ASTResolving.guessTypeForReference(ast, node);
					if (newTypeNode == null) {
						newTypeNode= ast.newSimpleType(ast.newSimpleName("Object")); //$NON-NLS-1$
					}
				}
			}
		}

		return newTypeNode;
	}

	@Override
	protected void addNewParameters(ASTRewrite rewrite, List<String> takenNames, List<SingleVariableDeclaration> params, ImportRewriteContext context) throws CoreException {
		AST ast= rewrite.getAST();

		List<Expression> arguments= fArguments;

		for (int i= 0; i < arguments.size(); i++) {
			Expression elem= arguments.get(i);
			SingleVariableDeclaration param= ast.newSingleVariableDeclaration();

			// argument type
			String argTypeKey= "arg_type_" + i; //$NON-NLS-1$
			Type type= evaluateParameterType(ast, elem, argTypeKey, context);
			param.setType(type);

			// argument name
			String argNameKey= "arg_name_" + i; //$NON-NLS-1$
			String name= evaluateParameterName(takenNames, elem, type, argNameKey);
			param.setName(ast.newSimpleName(name));

			params.add(param);
		}
	}

	private Type evaluateParameterType(AST ast, Expression elem, String key, ImportRewriteContext context) {
		ITypeBinding binding= Bindings.normalizeTypeBinding(elem.resolveTypeBinding());
		if (binding != null && binding.isWildcardType()) {
			binding= ASTResolving.normalizeWildcardType(binding, true, ast);
		}
		if (binding != null) {
			return getImportRewrite().addImport(binding, ast, context, TypeLocation.PARAMETER);
		}
		return ast.newSimpleType(ast.newSimpleName("Object")); //$NON-NLS-1$
	}

	private String evaluateParameterName(List<String> takenNames, Expression argNode, Type type, String key) {
		IJavaProject project= getCompilationUnit().getJavaProject();
		String[] names = StubUtility.getVariableNameSuggestions(NamingConventions.VK_PARAMETER, project, type, argNode,
				takenNames);
		String favourite= names[0];
		takenNames.add(favourite);
		return favourite;
	}

	@Override
	protected void addNewExceptions(ASTRewrite rewrite, List<Type> exceptions, ImportRewriteContext context) throws CoreException {
	}

	@Override
	protected void addNewTypeParameters(ASTRewrite rewrite, List<String> takenNames, List<TypeParameter> params, ImportRewriteContext context) throws CoreException {
	}
}
