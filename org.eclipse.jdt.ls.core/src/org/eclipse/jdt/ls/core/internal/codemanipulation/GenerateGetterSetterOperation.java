/*******************************************************************************
* Copyright (c) 2018 Microsoft Corporation and others.
* All rights reserved. This program and the accompanying materials
* are made available under the terms of the Eclipse Public License v1.0
* which accompanies this distribution, and is available at
* http://www.eclipse.org/legal/epl-v10.html
*
* Contributors:
*     Microsoft Corporation - initial API and implementation
*******************************************************************************/

package org.eclipse.jdt.ls.core.internal.codemanipulation;

import org.eclipse.core.runtime.Assert;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.OperationCanceledException;
import org.eclipse.jdt.core.Flags;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IField;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.AbstractTypeDeclaration;
import org.eclipse.jdt.core.dom.AnonymousClassDeclaration;
import org.eclipse.jdt.core.dom.ClassInstanceCreation;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.Modifier;
import org.eclipse.jdt.core.dom.NodeFinder;
import org.eclipse.jdt.core.dom.rewrite.ASTRewrite;
import org.eclipse.jdt.core.dom.rewrite.ListRewrite;
import org.eclipse.jdt.core.formatter.CodeFormatter;
import org.eclipse.jdt.core.manipulation.CoreASTProvider;
import org.eclipse.jdt.internal.core.manipulation.StubUtility;
import org.eclipse.jdt.internal.corext.codemanipulation.GetterSetterUtil;
import org.eclipse.jdt.internal.corext.dom.ASTNodes;
import org.eclipse.jdt.internal.corext.util.CodeFormatterUtil;
import org.eclipse.text.edits.TextEdit;

public class GenerateGetterSetterOperation {
	private final static int generateVisibility = Modifier.PUBLIC;
	private final static boolean generateComments = true;

	private final IType type;
	private CompilationUnit astRoot;

	public GenerateGetterSetterOperation(IType type, CompilationUnit astRoot) {
		Assert.isNotNull(type);
		this.type = type;
		this.astRoot = astRoot;
	}

	public static boolean supportsGetterSetter(IType type) throws JavaModelException {
		if (type == null || type.isAnnotation() || type.isInterface() || type.getCompilationUnit() == null) {
			return false;
		}

		return true;
	}

	public TextEdit createTextEdit(IProgressMonitor monitor) throws OperationCanceledException, CoreException {
		if (!supportsGetterSetter(type)) {
			return null;
		}

		final ICompilationUnit unit = type.getCompilationUnit();
		if (astRoot == null) {
			astRoot = CoreASTProvider.getInstance().getAST(unit, CoreASTProvider.WAIT_YES, monitor);
		}

		final ASTRewrite astRewrite = ASTRewrite.create(astRoot.getAST());
		ListRewrite listRewriter = null;
		if (type.isAnonymous()) {
			final ClassInstanceCreation creation = ASTNodes.getParent(NodeFinder.perform(astRoot, type.getNameRange()), ClassInstanceCreation.class);
			if (creation != null) {
				final AnonymousClassDeclaration declaration = creation.getAnonymousClassDeclaration();
				if (declaration != null) {
					listRewriter = astRewrite.getListRewrite(declaration, AnonymousClassDeclaration.BODY_DECLARATIONS_PROPERTY);
				}
			}
		} else {
			final AbstractTypeDeclaration declaration = ASTNodes.getParent(NodeFinder.perform(astRoot, type.getNameRange()), AbstractTypeDeclaration.class);
			if (declaration != null) {
				listRewriter = astRewrite.getListRewrite(declaration, declaration.getBodyDeclarationsProperty());
			}
		}

		if (listRewriter == null) {
			return null;
		}

		generateGetterSetterMethods(listRewriter);
		return astRewrite.rewriteAST();
	}

	private void generateGetterSetterMethods(ListRewrite listRewriter) throws OperationCanceledException, CoreException {
		IField[] fields = type.getFields();
		for (IField field : fields) {
			int flags = field.getFlags();
			if (!Flags.isEnum(flags) && !Flags.isStatic(flags)) {
				if (GetterSetterUtil.getGetter(field) == null) {
					insertMethod(field, listRewriter, AccessorKind.GETTER);
				}

				if (!Flags.isFinal(flags) && GetterSetterUtil.getSetter(field) == null) {
					insertMethod(field, listRewriter, AccessorKind.SETTER);
				}
			}
		}
	}

	private void insertMethod(IField field, ListRewrite rewrite, AccessorKind kind) throws CoreException {
		IType type = field.getDeclaringType();
		String delimiter = StubUtility.getLineDelimiterUsed(type);
		int flags = generateVisibility | (field.getFlags() & Flags.AccStatic);
		String stub;
		if (kind == AccessorKind.GETTER) {
			String name = GetterSetterUtil.getGetterName(field, null);
			stub = GetterSetterUtil.getGetterStub(field, name, generateComments, flags);
		} else {
			String name = GetterSetterUtil.getSetterName(field, null);
			stub = GetterSetterUtil.getSetterStub(field, name, generateComments, flags);
		}

		String formattedStub = CodeFormatterUtil.format(CodeFormatter.K_CLASS_BODY_DECLARATIONS, stub, 0, delimiter, type.getJavaProject().getOptions(true));
		MethodDeclaration declaration = (MethodDeclaration) rewrite.getASTRewrite().createStringPlaceholder(formattedStub, ASTNode.METHOD_DECLARATION);
		rewrite.insertLast(declaration, null);
	}

	enum AccessorKind {
		GETTER, SETTER
	}
}