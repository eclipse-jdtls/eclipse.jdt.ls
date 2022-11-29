/*******************************************************************************
 * Copyright (c) 2000, 2016 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Copied from /org.eclipse.jdt.ui/src/org/eclipse/jdt/internal/ui/text/correction/proposals/NewAnnotationMemberProposal.java
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.jdt.ls.core.internal.corrections.proposals;

import java.util.List;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.AnnotationTypeDeclaration;
import org.eclipse.jdt.core.dom.AnnotationTypeMemberDeclaration;
import org.eclipse.jdt.core.dom.BodyDeclaration;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.MemberValuePair;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.core.dom.Type;
import org.eclipse.jdt.core.dom.rewrite.ASTRewrite;
import org.eclipse.jdt.core.dom.rewrite.ImportRewrite.ImportRewriteContext;
import org.eclipse.jdt.core.dom.rewrite.ListRewrite;
import org.eclipse.jdt.internal.core.manipulation.dom.ASTResolving;
import org.eclipse.jdt.internal.corext.codemanipulation.ContextSensitiveImportRewriteContext;
import org.eclipse.jdt.internal.corext.dom.ASTNodeFactory;
import org.eclipse.lsp4j.CodeActionKind;


public class NewAnnotationMemberProposal extends ASTRewriteCorrectionProposal {

	private final ASTNode fInvocationNode;
	private final ITypeBinding fSenderBinding;

	public NewAnnotationMemberProposal(String label, ICompilationUnit targetCU, ASTNode invocationNode,
			ITypeBinding binding, int relevance) {
		super(label, CodeActionKind.QuickFix, targetCU, null, relevance);
		fInvocationNode= invocationNode;
		fSenderBinding= binding;
	}

	@Override
	protected ASTRewrite getRewrite() throws CoreException {
		CompilationUnit astRoot= ASTResolving.findParentCompilationUnit(fInvocationNode);
		ASTNode typeDecl= astRoot.findDeclaringNode(fSenderBinding);
		ASTNode newTypeDecl= null;
		if (typeDecl != null) {
			newTypeDecl= typeDecl;
		} else {
			astRoot= ASTResolving.createQuickFixAST(getCompilationUnit(), null);
			newTypeDecl= astRoot.findDeclaringNode(fSenderBinding.getKey());
		}
		createImportRewrite(astRoot);

		if (newTypeDecl instanceof AnnotationTypeDeclaration newAnnotationTypeDecl) {
			ASTRewrite rewrite= ASTRewrite.create(astRoot.getAST());

			AnnotationTypeMemberDeclaration newStub= getStub(rewrite, newAnnotationTypeDecl);

			List<BodyDeclaration> members= newAnnotationTypeDecl.bodyDeclarations();
			int insertIndex= members.size();

			ListRewrite listRewriter= rewrite.getListRewrite(newAnnotationTypeDecl, AnnotationTypeDeclaration.BODY_DECLARATIONS_PROPERTY);
			listRewriter.insertAt(newStub, insertIndex, null);

			return rewrite;
		}
		return null;
	}

	private AnnotationTypeMemberDeclaration getStub(ASTRewrite rewrite, AnnotationTypeDeclaration targetTypeDecl) {
		AST ast= targetTypeDecl.getAST();

		AnnotationTypeMemberDeclaration decl= ast.newAnnotationTypeMemberDeclaration();

		SimpleName newNameNode= getNewName(rewrite);

		decl.modifiers().addAll(ASTNodeFactory.newModifiers(ast, evaluateModifiers(targetTypeDecl)));

		decl.setName(newNameNode);

		Type returnType= getNewType(rewrite);
		decl.setType(returnType);
		return decl;
	}

	private Type getNewType(ASTRewrite rewrite) {
		AST ast= rewrite.getAST();
		Type newTypeNode= null;
		ITypeBinding binding= null;
		if (fInvocationNode.getLocationInParent() == MemberValuePair.NAME_PROPERTY) {
			Expression value= ((MemberValuePair) fInvocationNode.getParent()).getValue();
			binding= value.resolveTypeBinding();
		} else if (fInvocationNode instanceof Expression expression) {
			binding = expression.resolveTypeBinding();
		}
		if (binding != null) {
			ImportRewriteContext importRewriteContext= new ContextSensitiveImportRewriteContext(fInvocationNode, getImportRewrite());
			newTypeNode= getImportRewrite().addImport(binding, ast, importRewriteContext);
		}
		if (newTypeNode == null) {
			newTypeNode= ast.newSimpleType(ast.newSimpleName("String")); //$NON-NLS-1$
		}
		return newTypeNode;
	}

	private int evaluateModifiers(AnnotationTypeDeclaration targetTypeDecl) {
		List<BodyDeclaration> methodDecls= targetTypeDecl.bodyDeclarations();
		for (int i= 0; i < methodDecls.size(); i++) {
			Object curr= methodDecls.get(i);
			if (curr instanceof AnnotationTypeMemberDeclaration annotation) {
				return annotation.getModifiers();
			}
		}
		return 0;
	}

	private SimpleName getNewName(ASTRewrite rewrite) {
		AST ast= rewrite.getAST();
		String name;
		if (fInvocationNode.getLocationInParent() == MemberValuePair.NAME_PROPERTY) {
			name= ((SimpleName) fInvocationNode).getIdentifier();
		} else {
			name= "value"; //$NON-NLS-1$
		}


		SimpleName newNameNode= ast.newSimpleName(name);
		return newNameNode;
	}

}
