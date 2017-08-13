/*******************************************************************************
 * Copyright (c) 2017 Microsoft Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Microsoft Corporation - initial API and implementation
 *******************************************************************************/

package org.eclipse.jdt.ls.core.internal.corrections.proposals;

import java.util.List;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.jdt.core.Flags;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IField;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.Signature;
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
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.core.dom.SingleVariableDeclaration;
import org.eclipse.jdt.core.dom.Type;
import org.eclipse.jdt.core.dom.rewrite.ASTRewrite;
import org.eclipse.jdt.core.dom.rewrite.ImportRewrite.ImportRewriteContext;
import org.eclipse.jdt.core.dom.rewrite.ImportRewrite.TypeLocation;
import org.eclipse.jdt.core.dom.rewrite.ListRewrite;
import org.eclipse.jdt.ls.core.internal.JavaLanguageServerPlugin;
import org.eclipse.jdt.ls.core.internal.corext.codemanipulation.CodeGeneration;
import org.eclipse.jdt.ls.core.internal.corext.codemanipulation.ContextSensitiveImportRewriteContext;
import org.eclipse.jdt.ls.core.internal.corext.codemanipulation.GetterSetterUtil;
import org.eclipse.jdt.ls.core.internal.corext.codemanipulation.StubUtility;
import org.eclipse.jdt.ls.core.internal.corext.dom.ASTNodes;
import org.eclipse.jdt.ls.core.internal.corrections.ASTResolving;

public class SelfEncapsulateFieldProposal extends ASTRewriteCorrectionProposal {

	private static final String[] EMPTY = new String[0];

	private boolean isGetter;
	private IField fField;
	private IVariableBinding fVariableBinding;
	private ASTNode fNode;
	private ITypeBinding fSenderBinding;

	public SelfEncapsulateFieldProposal(String label, ICompilationUnit targetCU, ASTNode invocationNode, IVariableBinding binding, IField field, int relevance) {
		super(label, targetCU, null, relevance);

		this.fField = field;
		this.fVariableBinding = binding;
		this.fSenderBinding = binding.getDeclaringClass();
		this.fNode = invocationNode;
	}

	@Override
	protected ASTRewrite getRewrite() throws CoreException {
		CompilationUnit astRoot = ASTResolving.findParentCompilationUnit(fNode);
		ASTNode typeDecl = astRoot.findDeclaringNode(fSenderBinding);
		ASTNode newTypeDecl = null;
		if (typeDecl != null) {
			newTypeDecl = typeDecl;
		} else {
			astRoot = ASTResolving.createQuickFixAST(getCompilationUnit(), null);
			newTypeDecl = astRoot.findDeclaringNode(fSenderBinding.getKey());
		}
		createImportRewrite(astRoot);

		if (newTypeDecl != null) {
			ASTRewrite rewrite = ASTRewrite.create(astRoot.getAST());

			ChildListPropertyDescriptor property = ASTNodes.getBodyDeclarationsProperty(newTypeDecl);
			List<BodyDeclaration> members = ASTNodes.getBodyDeclarations(newTypeDecl);

			int insertIndex = members.size();
			ListRewrite listRewriter = rewrite.getListRewrite(newTypeDecl, property);

			isGetter = true;
			MethodDeclaration newStub = getStub(rewrite, newTypeDecl);
			listRewriter.insertAt(newStub, insertIndex, null);

			isGetter = false;
			newStub = getStub(rewrite, newTypeDecl);
			listRewriter.insertAt(newStub, insertIndex + 1, null);

			return rewrite;
		}
		return null;
	}

	protected MethodDeclaration getStub(ASTRewrite rewrite, ASTNode targetTypeDecl) throws CoreException {
		ImportRewriteContext context = new ContextSensitiveImportRewriteContext(targetTypeDecl, getImportRewrite());

		AST ast = targetTypeDecl.getAST();
		MethodDeclaration decl = ast.newMethodDeclaration();

		SimpleName newNameNode = getNewName(rewrite);
		decl.setName(newNameNode);

		addNewModifiers(rewrite, targetTypeDecl, decl.modifiers());

		String bodyStatement = ""; //$NON-NLS-1$
		boolean isAbstractMethod = Modifier.isAbstract(decl.getModifiers()) || (fSenderBinding.isInterface() && !Modifier.isStatic(decl.getModifiers()) && !Modifier.isDefault(decl.getModifiers()));
		Type returnType = getNewMethodType(rewrite, context);
		decl.setReturnType2(returnType);

		addNewParameters(rewrite, decl.parameters(), context);

		String lineDelim = "\n"; // Use default line delimiter, as generated stub has to be formatted anyway
		String name = getFunctionName();
		if (!isAbstractMethod) {
			if (isGetter) {
				bodyStatement = CodeGeneration.getGetterMethodBodyContent(fField.getCompilationUnit(), fField.getDeclaringType().getTypeQualifiedName('.'), name, fField.getElementName(), lineDelim);
			} else {
				String fieldName = fField.getElementName();
				boolean isStatic = Flags.isStatic(decl.getModifiers());
				String argname = getArgumentName();
				if (argname.equals(fieldName) || !isStatic) {
					if (isStatic) {
						fieldName = fField.getDeclaringType().getElementName() + '.' + fieldName;
					} else {
						fieldName = "this." + fieldName; //$NON-NLS-1$
					}
				}
				bodyStatement = CodeGeneration.getSetterMethodBodyContent(fField.getCompilationUnit(), fField.getDeclaringType().getTypeQualifiedName('.'), name, fieldName, argname, lineDelim);
			}
		}
		bodyStatement = bodyStatement.substring(0, bodyStatement.lastIndexOf(lineDelim));

		Block body = null;
		if (!isAbstractMethod && !Flags.isAbstract(decl.getModifiers())) {
			body = ast.newBlock();
			if (bodyStatement.length() > 0) {
				ASTNode bodyNode = rewrite.createStringPlaceholder(bodyStatement, ASTNode.RETURN_STATEMENT);
				body.statements().add(bodyNode);
			}
		}
		decl.setBody(body);

		addNewJavadoc(rewrite, decl, context);

		return decl;
	}

	private void addNewJavadoc(ASTRewrite rewrite, MethodDeclaration decl, ImportRewriteContext context) throws CoreException {
		IType parentType = fField.getDeclaringType();

		String typeName = Signature.toString(fField.getTypeSignature());
		String accessorName = StubUtility.getBaseName(fField);
		String lineDelim = "\n";

		String comment = null;
		String name = getFunctionName();
		if (isGetter) {
			comment = CodeGeneration.getGetterComment(fField.getCompilationUnit(), parentType.getTypeQualifiedName('.'), name, fField.getElementName(), typeName, accessorName, lineDelim);
		} else {
			String argname = getArgumentName();
			comment = CodeGeneration.getSetterComment(fField.getCompilationUnit(), parentType.getTypeQualifiedName('.'), name, fField.getElementName(), typeName, argname, accessorName, lineDelim);
		}
		comment = comment.substring(0, comment.lastIndexOf(lineDelim));

		if (comment != null) {
			Javadoc javadoc = (Javadoc) rewrite.createStringPlaceholder(comment, ASTNode.JAVADOC);
			decl.setJavadoc(javadoc);
		}
	}

	private int getFlags() {
		int flags = Flags.AccPublic;
		try {
			flags = Flags.AccPublic | (fField.getFlags() & Flags.AccStatic);
		} catch (CoreException e) {
			JavaLanguageServerPlugin.logException("Get flags for getter/setter ", e);
		}
		return flags;
	}

	private void addNewModifiers(ASTRewrite rewrite, ASTNode targetTypeDecl, List<IExtendedModifier> modifiers) {
		modifiers.addAll(rewrite.getAST().newModifiers(getFlags()));
	}

	private void addNewParameters(ASTRewrite rewrite, List<SingleVariableDeclaration> params, ImportRewriteContext context) throws CoreException {
		if (!isGetter) {
			AST ast = rewrite.getAST();

			SingleVariableDeclaration param = ast.newSingleVariableDeclaration();
			Type type = getImportRewrite().addImport(fVariableBinding.getType(), ast, context, TypeLocation.PARAMETER);
			param.setType(type);
			param.setName(ast.newSimpleName(getArgumentName()));
			params.add(param);
		}
	}

	private String getFunctionName() throws CoreException {
		return isGetter ? GetterSetterUtil.getGetterName(fField, null) : GetterSetterUtil.getSetterName(fField, null);
	}

	private String getArgumentName() throws CoreException {
		String accessorName = StubUtility.getBaseName(fField);
		return StubUtility.suggestArgumentName(fField.getJavaProject(), accessorName, EMPTY);
	}

	private SimpleName getNewName(ASTRewrite rewrite) {
		try {
			AST ast = rewrite.getAST();
			SimpleName newNameNode = ast.newSimpleName(getFunctionName());
			return newNameNode;
		} catch (CoreException e) {
			JavaLanguageServerPlugin.logException("Get newname for getter/setter ", e);
		}
		return null;
	}

	private Type getNewMethodType(ASTRewrite rewrite, ImportRewriteContext context) throws CoreException {
		AST ast = rewrite.getAST();
		Type newTypeNode = null;
		if (isGetter) {
			newTypeNode = getImportRewrite().addImport(fVariableBinding.getType(), ast, context, TypeLocation.RETURN_TYPE);
		} else {
			newTypeNode = ast.newPrimitiveType(PrimitiveType.VOID);
		}
		return newTypeNode;
	}
}