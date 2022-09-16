/*******************************************************************************
 * Copyright (c) 2019-2021 Microsoft Corporation and others.
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

package org.eclipse.jdt.ls.core.internal.handlers;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.IMethodBinding;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.IVariableBinding;
import org.eclipse.jdt.core.dom.NodeFinder;
import org.eclipse.jdt.core.manipulation.CoreASTProvider;
import org.eclipse.jdt.internal.corext.codemanipulation.CodeGenerationSettings;
import org.eclipse.jdt.internal.corext.codemanipulation.GenerateHashCodeEqualsOperation;
import org.eclipse.jdt.internal.corext.dom.ASTNodes;
import org.eclipse.jdt.ls.core.internal.JavaLanguageServerPlugin;
import org.eclipse.jdt.ls.core.internal.corrections.DiagnosticsHelper;
import org.eclipse.jdt.ls.core.internal.handlers.JdtDomModels.LspVariableBinding;
import org.eclipse.jdt.ls.core.internal.preferences.Preferences;
import org.eclipse.jdt.ls.core.internal.text.correction.SourceAssistProcessor;
import org.eclipse.lsp4j.CodeActionParams;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.WorkspaceEdit;
import org.eclipse.text.edits.TextEdit;

public class HashCodeEqualsHandler {
	public static final String METHODNAME_HASH_CODE = "hashCode";
	public static final String METHODNAME_EQUALS = "equals";

	// For test purpose
	public static CheckHashCodeEqualsResponse checkHashCodeEqualsStatus(CodeActionParams params) {
		return checkHashCodeEqualsStatus(params, new NullProgressMonitor());
	}

	public static CheckHashCodeEqualsResponse checkHashCodeEqualsStatus(CodeActionParams params, IProgressMonitor monitor) {
		IType type = SourceAssistProcessor.getSelectionType(params, monitor);
		return checkHashCodeEqualsStatus(type, monitor);
	}

	public static CheckHashCodeEqualsResponse checkHashCodeEqualsStatus(IType type, IProgressMonitor monitor) {
		CheckHashCodeEqualsResponse response = new CheckHashCodeEqualsResponse();
		if (type == null) {
			return response;
		}
		try {
			CompilationUnit astRoot = CoreASTProvider.getInstance().getAST(type.getCompilationUnit(), CoreASTProvider.WAIT_YES, monitor);
			if (astRoot == null) {
				return response;
			}

			ITypeBinding typeBinding = ASTNodes.getTypeBinding(astRoot, type);
			if (typeBinding != null) {
				response.type = type.getTypeQualifiedName();
				response.fields = JdtDomModels.getDeclaredFields(typeBinding, false);
				response.existingMethods = findExistingMethods(typeBinding);
			}
		} catch (JavaModelException e) {
			JavaLanguageServerPlugin.logException("Check hashCode and equals status", e);
		}
		return response;
	}

	public static WorkspaceEdit generateHashCodeEquals(GenerateHashCodeEqualsParams params, IProgressMonitor monitor) {
		IType type = SourceAssistProcessor.getSelectionType(params.context, monitor);
		Preferences preferences = JavaLanguageServerPlugin.getPreferencesManager().getPreferences();
		boolean useJava7Objects = preferences.isHashCodeEqualsTemplateUseJava7Objects();
		boolean useInstanceof = preferences.isHashCodeEqualsTemplateUseInstanceof();
		boolean useBlocks = preferences.isCodeGenerationTemplateUseBlocks();
		boolean generateComments = preferences.isCodeGenerationTemplateGenerateComments();
		TextEdit edit = generateHashCodeEqualsTextEdit(type, params.fields, params.regenerate, useJava7Objects, useInstanceof, useBlocks, generateComments, params.context.getRange(), monitor);
		return (edit == null) ? null : SourceAssistProcessor.convertToWorkspaceEdit(type.getCompilationUnit(), edit);
	}

	// For test purpose
	public static TextEdit generateHashCodeEqualsTextEdit(GenerateHashCodeEqualsParams params, boolean useJava7Objects, boolean useInstanceof, boolean useBlocks, boolean generateComments) {
		return generateHashCodeEqualsTextEdit(params, useJava7Objects, useInstanceof, useBlocks, generateComments, new NullProgressMonitor());
	}

	public static TextEdit generateHashCodeEqualsTextEdit(GenerateHashCodeEqualsParams params, boolean useJava7Objects, boolean useInstanceof, boolean useBlocks, boolean generateComments, IProgressMonitor monitor) {
		IType type = SourceAssistProcessor.getSelectionType(params.context, monitor);
		return generateHashCodeEqualsTextEdit(type, params.fields, params.regenerate, useJava7Objects, useInstanceof, useBlocks, generateComments, params.context.getRange(), monitor);
	}

	public static TextEdit generateHashCodeEqualsTextEdit(IType type, LspVariableBinding[] fields, boolean regenerate, boolean useJava7Objects, boolean useInstanceof, boolean useBlocks, boolean generateComments, Range cursor, IProgressMonitor monitor) {
		if (type == null) {
			return null;
		}
		try {
			CompilationUnit astRoot = CoreASTProvider.getInstance().getAST(type.getCompilationUnit(), CoreASTProvider.WAIT_YES, monitor);
			if (astRoot == null) {
				return null;
			}

			ITypeBinding typeBinding = ASTNodes.getTypeBinding(astRoot, type);
			if (typeBinding != null) {
				IVariableBinding[] variableBindings = JdtDomModels.convertToVariableBindings(typeBinding, fields);
				CodeGenerationSettings codeGenSettings = new CodeGenerationSettings();
				codeGenSettings.createComments = generateComments;
				codeGenSettings.overrideAnnotation = true;
				ASTNode node = NodeFinder.perform(astRoot, DiagnosticsHelper.getStartOffset(type.getCompilationUnit(), cursor), DiagnosticsHelper.getLength(type.getCompilationUnit(), cursor));
				ASTNode declarationNode = SourceAssistProcessor.getTypeDeclarationNode(node);
				// If cursor position is not specified, then insert to the last by default.
				IJavaElement insertPosition = (declarationNode != null) ? CodeGenerationUtils.findInsertElement(type, null) : CodeGenerationUtils.findInsertElement(type, cursor);
				GenerateHashCodeEqualsOperation operation = new GenerateHashCodeEqualsOperation(typeBinding, variableBindings, astRoot, insertPosition, codeGenSettings, useInstanceof, useJava7Objects, regenerate, false, false);
				operation.setUseBlocksForThen(useBlocks);
				operation.run(null);
				return operation.getResultingEdit();
			}
		} catch (CoreException e) {
			JavaLanguageServerPlugin.logException("Generate hashCode and equals methods", e);
		}
		return null;
	}

	private static String[] findExistingMethods(ITypeBinding typeBinding) {
		List<String> existingMethods = new ArrayList<>();
		IMethodBinding[] declaredMethods = typeBinding.getDeclaredMethods();
		for (IMethodBinding method : declaredMethods) {
			if (method.getName().equals(METHODNAME_EQUALS)) {
				ITypeBinding[] b = method.getParameterTypes();
				if ((b.length == 1) && (b[0].getQualifiedName().equals("java.lang.Object"))) {
					existingMethods.add(METHODNAME_EQUALS);
				}
			} else if (method.getName().equals(METHODNAME_HASH_CODE) && method.getParameterTypes().length == 0) {
				existingMethods.add(METHODNAME_HASH_CODE);
			}
			if (existingMethods.size() == 2) {
				break;
			}
		}
		return existingMethods.toArray(new String[0]);
	}

	public static class CheckHashCodeEqualsResponse {
		public String type;
		public LspVariableBinding[] fields;
		public String[] existingMethods;
	}

	public static class GenerateHashCodeEqualsParams {
		public CodeActionParams context;
		public LspVariableBinding[] fields;
		public boolean regenerate;
	}
}
