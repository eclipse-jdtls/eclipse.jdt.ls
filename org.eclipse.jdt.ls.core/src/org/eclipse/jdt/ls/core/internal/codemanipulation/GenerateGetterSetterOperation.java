/*******************************************************************************
* Copyright (c) 2018-2021 Microsoft Corporation and others.
* All rights reserved. This program and the accompanying materials
* are made available under the terms of the Eclipse Public License 2.0
* which accompanies this distribution, and is available at
* https://www.eclipse.org/legal/epl-2.0/
*
* SPDX-License-Identifier: EPL-2.0
*
* Contributors:
*     Microsoft Corporation - initial API and implementation
*******************************************************************************/

package org.eclipse.jdt.ls.core.internal.codemanipulation;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.eclipse.core.runtime.Assert;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.OperationCanceledException;
import org.eclipse.jdt.core.Flags;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IField;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.Signature;
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
import org.eclipse.jdt.internal.corext.codemanipulation.StubUtility2Core;
import org.eclipse.jdt.internal.corext.dom.ASTNodes;
import org.eclipse.jdt.internal.corext.util.CodeFormatterUtil;
import org.eclipse.text.edits.TextEdit;

public class GenerateGetterSetterOperation {
	private final static int generateVisibility = Modifier.PUBLIC;

	private final IType type;
	private CompilationUnit astRoot;
	private boolean generateComments = true;
	private IJavaElement insertPosition = null; // Insert to the last by default.

	public GenerateGetterSetterOperation(IType type, CompilationUnit astRoot, boolean generateComments) {
		this(type, astRoot, generateComments, null);
	}

	public GenerateGetterSetterOperation(IType type, CompilationUnit astRoot, boolean generateComments, IJavaElement insertPosition) {
		Assert.isNotNull(type);
		this.type = type;
		this.astRoot = astRoot;
		this.generateComments = generateComments;
		this.insertPosition = insertPosition;
	}

	public static boolean supportsGetterSetter(IType type) throws JavaModelException {
		if (type == null || type.isAnnotation() || type.isInterface() || type.getCompilationUnit() == null) {
			return false;
		}

		return true;
	}

	public static AccessorField[] getUnimplementedAccessors(IType type, AccessorKind kind) throws JavaModelException {
		if (!supportsGetterSetter(type)) {
			return new AccessorField[0];
		}

		List<AccessorField> unimplemented = new ArrayList<>();
		IField[] fields = type.isRecord() ? type.getRecordComponents() : type.getFields();
		for (IField field : fields) {
			int flags = field.getFlags();
			if (!Flags.isEnum(flags)) {
				boolean isStatic = Flags.isStatic(flags);
				boolean generateGetter = (GetterSetterUtil.getGetter(field) == null);
				boolean generateSetter = (!Flags.isFinal(flags) && GetterSetterUtil.getSetter(field) == null);
				switch (kind) {
					case BOTH:
						if (generateGetter || generateSetter) {
							unimplemented.add(new AccessorField(field.getElementName(), isStatic, generateGetter, generateSetter, Signature.getSignatureSimpleName(field.getTypeSignature())));
						}
						break;
					case GETTER:
						if (generateGetter) {
							unimplemented.add(new AccessorField(field.getElementName(), isStatic, generateGetter, false, Signature.getSignatureSimpleName(field.getTypeSignature())));
						}
						break;
					case SETTER:
						if (generateSetter) {
							unimplemented.add(new AccessorField(field.getElementName(), isStatic, false, generateSetter, Signature.getSignatureSimpleName(field.getTypeSignature())));
						}
						break;
					default:
						break;
				}
			}
		}

		return unimplemented.toArray(new AccessorField[0]);
	}

	// for test purpose
	public TextEdit createTextEdit(AccessorKind kind, IProgressMonitor monitor) throws OperationCanceledException, CoreException {
		AccessorField[] accessors = getUnimplementedAccessors(type, kind);
		return createTextEdit(monitor, accessors);
	}

	public TextEdit createTextEdit(IProgressMonitor monitor, AccessorField[] accessors) throws OperationCanceledException, CoreException {
		if (accessors == null || accessors.length == 0) {
			return null;
		}

		final ICompilationUnit unit = type.getCompilationUnit();
		if (astRoot == null) {
			astRoot = CoreASTProvider.getInstance().getAST(unit, CoreASTProvider.WAIT_YES, monitor);
		}
		if (astRoot == null) {
			return null;
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

		ASTNode insertion = StubUtility2Core.getNodeToInsertBefore(listRewriter, insertPosition);
		for (AccessorField accessor : accessors) {
			generateGetterSetterMethods(listRewriter, accessor, insertion);
		}

		return astRewrite.rewriteAST();
	}

	private void generateGetterSetterMethods(ListRewrite listRewriter, AccessorField accessor, ASTNode insertion) throws OperationCanceledException, CoreException {
		IField field = type.getField(accessor.fieldName);
		if (field == null) {
			return;
		}

		if (accessor.generateGetter && GetterSetterUtil.getGetter(field) == null) {
			insertMethod(field, listRewriter, AccessorKind.GETTER, insertion);
		}

		if (accessor.generateSetter && GetterSetterUtil.getSetter(field) == null) {
			insertMethod(field, listRewriter, AccessorKind.SETTER, insertion);
		}
	}

	private void insertMethod(IField field, ListRewrite rewrite, AccessorKind kind, ASTNode insertion) throws CoreException {
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

		Map<String, String> options = type.getCompilationUnit() != null ? type.getCompilationUnit().getOptions(true) : type.getJavaProject().getOptions(true);
		String formattedStub = CodeFormatterUtil.format(CodeFormatter.K_CLASS_BODY_DECLARATIONS, stub, 0, delimiter, options);
		MethodDeclaration declaration = (MethodDeclaration) rewrite.getASTRewrite().createStringPlaceholder(formattedStub, ASTNode.METHOD_DECLARATION);
		if (insertion != null) {
			rewrite.insertBefore(declaration, insertion, null);
		} else {
			rewrite.insertLast(declaration, null);
		}
	}

	public enum AccessorKind {
		GETTER, SETTER, BOTH
	}

	public static class AccessorField {
		public String fieldName;
		public boolean isStatic;
		public boolean generateGetter;
		public boolean generateSetter;
		public String typeName;

		public AccessorField(String fieldName, boolean isStatic, boolean generateGetter, boolean generateSetter, String typeName) {
			this.fieldName = fieldName;
			this.isStatic = isStatic;
			this.generateGetter = generateGetter;
			this.generateSetter = generateSetter;
			this.typeName = typeName;
		}
	}
}