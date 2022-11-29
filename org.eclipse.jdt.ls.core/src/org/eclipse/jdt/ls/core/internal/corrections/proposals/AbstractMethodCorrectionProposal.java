/*******************************************************************************
 * Copyright (c) 2000, 2017 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Copied from /org.eclipse.jdt.ui/src/org/eclipse/jdt/internal/ui/text/correction/proposals/AbstractMethodCorrectionProposal.java
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *     Benjamin Muskalla - [quick fix] Create Method in void context should 'box' void. - https://bugs.eclipse.org/bugs/show_bug.cgi?id=107985
 *     Jerome Cambon <jerome.cambon@oracle.com> - [code style] don't generate redundant modifiers "public static final abstract" for interface members - https://bugs.eclipse.org/71627
 *******************************************************************************/

package org.eclipse.jdt.ls.core.internal.corrections.proposals;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.core.runtime.Assert;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.jdt.core.Flags;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.Block;
import org.eclipse.jdt.core.dom.BodyDeclaration;
import org.eclipse.jdt.core.dom.ChildListPropertyDescriptor;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.IExtendedModifier;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.IVariableBinding;
import org.eclipse.jdt.core.dom.Javadoc;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.Modifier;
import org.eclipse.jdt.core.dom.PrimitiveType;
import org.eclipse.jdt.core.dom.ReturnStatement;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.core.dom.SingleVariableDeclaration;
import org.eclipse.jdt.core.dom.Type;
import org.eclipse.jdt.core.dom.TypeParameter;
import org.eclipse.jdt.core.dom.rewrite.ASTRewrite;
import org.eclipse.jdt.core.dom.rewrite.ImportRewrite.ImportRewriteContext;
import org.eclipse.jdt.core.dom.rewrite.ListRewrite;
import org.eclipse.jdt.core.manipulation.CodeGeneration;
import org.eclipse.jdt.internal.core.manipulation.dom.ASTResolving;
import org.eclipse.jdt.internal.corext.codemanipulation.CodeGenerationSettings;
import org.eclipse.jdt.internal.corext.codemanipulation.ContextSensitiveImportRewriteContext;
import org.eclipse.jdt.internal.corext.dom.ASTNodeFactory;
import org.eclipse.jdt.internal.corext.dom.ASTNodes;
import org.eclipse.jdt.internal.corext.dom.Bindings;
import org.eclipse.jdt.ls.core.internal.preferences.PreferenceManager;
import org.eclipse.lsp4j.CodeActionKind;


public abstract class AbstractMethodCorrectionProposal extends ASTRewriteCorrectionProposal {

	private ASTNode fNode;
	private ITypeBinding fSenderBinding;

	public AbstractMethodCorrectionProposal(String label, ICompilationUnit targetCU, ASTNode invocationNode,
			ITypeBinding binding, int relevance) {
		super(label, CodeActionKind.QuickFix, targetCU, null, relevance);

		Assert.isTrue(binding != null && Bindings.isDeclarationBinding(binding));

		fNode= invocationNode;
		fSenderBinding= binding;
	}

	protected ASTNode getInvocationNode() {
		return fNode;
	}

	/**
	 * @return The binding of the type declaration (generic type)
	 */
	protected ITypeBinding getSenderBinding() {
		return fSenderBinding;
	}

	@Override
	protected ASTRewrite getRewrite() throws CoreException {
		CompilationUnit astRoot= ASTResolving.findParentCompilationUnit(fNode);
		ASTNode typeDecl= astRoot.findDeclaringNode(fSenderBinding);
		ASTNode newTypeDecl= null;
		boolean isInDifferentCU;
		if (typeDecl != null) {
			isInDifferentCU= false;
			newTypeDecl= typeDecl;
		} else {
			isInDifferentCU= true;
			astRoot= ASTResolving.createQuickFixAST(getCompilationUnit(), null);
			newTypeDecl= astRoot.findDeclaringNode(fSenderBinding.getKey());
		}
		createImportRewrite(astRoot);

		if (newTypeDecl != null) {
			ASTRewrite rewrite= ASTRewrite.create(astRoot.getAST());

			MethodDeclaration newStub= getStub(rewrite, newTypeDecl);

			ChildListPropertyDescriptor property= ASTNodes.getBodyDeclarationsProperty(newTypeDecl);
			List<BodyDeclaration> members= ASTNodes.getBodyDeclarations(newTypeDecl);

			int insertIndex;
			if (isConstructor()) {
				insertIndex= findConstructorInsertIndex(members);
			} else if (!isInDifferentCU) {
				insertIndex= findMethodInsertIndex(members, fNode.getStartPosition());
			} else {
				insertIndex= members.size();
			}
			ListRewrite listRewriter= rewrite.getListRewrite(newTypeDecl, property);
			listRewriter.insertAt(newStub, insertIndex, null);

			return rewrite;
		}
		return null;
	}

	private MethodDeclaration getStub(ASTRewrite rewrite, ASTNode targetTypeDecl) throws CoreException {
		ImportRewriteContext context=new ContextSensitiveImportRewriteContext(targetTypeDecl, getImportRewrite());

		AST ast= targetTypeDecl.getAST();
		MethodDeclaration decl= ast.newMethodDeclaration();

		SimpleName newNameNode= getNewName(rewrite);

		decl.setConstructor(isConstructor());

		addNewModifiers(rewrite, targetTypeDecl, decl.modifiers());

		ArrayList<String> takenNames= new ArrayList<>();
		addNewTypeParameters(rewrite, takenNames, decl.typeParameters(), context);

		decl.setName(newNameNode);

		IVariableBinding[] declaredFields= fSenderBinding.getDeclaredFields();
		for (int i= 0; i < declaredFields.length; i++) { // avoid to take parameter names that are equal to field names
			takenNames.add(declaredFields[i].getName());
		}

		String bodyStatement= ""; //$NON-NLS-1$
		boolean isAbstractMethod= Modifier.isAbstract(decl.getModifiers()) || (fSenderBinding.isInterface() && !Modifier.isStatic(decl.getModifiers()) && !Modifier.isDefault(decl.getModifiers()));
		if (!isConstructor()) {
			Type returnType= getNewMethodType(rewrite, context);
			decl.setReturnType2(returnType);

			boolean isVoid = returnType instanceof PrimitiveType primitiveType && PrimitiveType.VOID.equals(primitiveType.getPrimitiveTypeCode());
			if (!isAbstractMethod && !isVoid) {
				ReturnStatement returnStatement= ast.newReturnStatement();
				returnStatement.setExpression(ASTNodeFactory.newDefaultExpression(ast, returnType, 0));
				bodyStatement= ASTNodes.asFormattedString(returnStatement, 0, String.valueOf('\n'), getCompilationUnit().getOptions(true));
			}
		}

		addNewParameters(rewrite, takenNames, decl.parameters(), context);
		addNewExceptions(rewrite, decl.thrownExceptionTypes(), context);

		Block body= null;
		if (!isAbstractMethod && !Flags.isAbstract(decl.getModifiers())) {
			body= ast.newBlock();
			if (bodyStatement.length() > 0) {
				ReturnStatement todoNode = (ReturnStatement) rewrite.createStringPlaceholder(bodyStatement,
						ASTNode.RETURN_STATEMENT);
				body.statements().add(todoNode);
			}
		}
		decl.setBody(body);

		CodeGenerationSettings settings = PreferenceManager.getCodeGenerationSettings(getCompilationUnit());
		if (settings.createComments && !fSenderBinding.isAnonymous()) {
			String string = CodeGeneration.getMethodComment(getCompilationUnit(), fSenderBinding.getName(), decl, null,
					String.valueOf('\n'));
			if (string != null) {
				Javadoc javadoc= (Javadoc) rewrite.createStringPlaceholder(string, ASTNode.JAVADOC);
				decl.setJavadoc(javadoc);
			}
		}
		return decl;
	}

	private int findMethodInsertIndex(List<BodyDeclaration> decls, int currPos) {
		int nDecls= decls.size();
		for (int i= 0; i < nDecls; i++) {
			BodyDeclaration curr= decls.get(i);
			if (curr instanceof MethodDeclaration && currPos < curr.getStartPosition() + curr.getLength()) {
				return i + 1;
			}
		}
		return nDecls;
	}

	private int findConstructorInsertIndex(List<BodyDeclaration> decls) {
		int nDecls= decls.size();
		int lastMethod= 0;
		for (int i= nDecls - 1; i >= 0; i--) {
			BodyDeclaration curr= decls.get(i);
			if (curr instanceof MethodDeclaration method) {
				if (method.isConstructor()) {
					return i + 1;
				}
				lastMethod= i;
			}
		}
		return lastMethod;
	}

	protected abstract boolean isConstructor();

	protected abstract void addNewModifiers(ASTRewrite rewrite, ASTNode targetTypeDecl, List<IExtendedModifier> modifiers);
	protected abstract void addNewTypeParameters(ASTRewrite rewrite, List<String> takenNames, List<TypeParameter> params, ImportRewriteContext context) throws CoreException;
	protected abstract void addNewParameters(ASTRewrite rewrite, List<String> takenNames, List<SingleVariableDeclaration> params, ImportRewriteContext context) throws CoreException;
	protected abstract void addNewExceptions(ASTRewrite rewrite, List<Type> exceptions, ImportRewriteContext context) throws CoreException;

	protected abstract SimpleName getNewName(ASTRewrite rewrite);
	protected abstract Type getNewMethodType(ASTRewrite rewrite, ImportRewriteContext context) throws CoreException;


}
