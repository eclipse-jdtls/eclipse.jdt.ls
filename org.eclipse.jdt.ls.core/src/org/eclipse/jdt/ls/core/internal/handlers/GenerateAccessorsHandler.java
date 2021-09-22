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

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.core.runtime.OperationCanceledException;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.NodeFinder;
import org.eclipse.jdt.core.dom.TypeDeclaration;
import org.eclipse.jdt.core.manipulation.CoreASTProvider;
import org.eclipse.jdt.ls.core.internal.JavaLanguageServerPlugin;
import org.eclipse.jdt.ls.core.internal.codemanipulation.GenerateGetterSetterOperation;
import org.eclipse.jdt.ls.core.internal.codemanipulation.GenerateGetterSetterOperation.AccessorField;
import org.eclipse.jdt.ls.core.internal.corrections.DiagnosticsHelper;
import org.eclipse.jdt.ls.core.internal.preferences.Preferences;
import org.eclipse.jdt.ls.core.internal.text.correction.SourceAssistProcessor;
import org.eclipse.lsp4j.CodeActionParams;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.WorkspaceEdit;
import org.eclipse.text.edits.TextEdit;

public class GenerateAccessorsHandler {
	public static AccessorField[] getUnimplementedAccessors(CodeActionParams params) {
		IType type = SourceAssistProcessor.getSelectionType(params);
		return getUnimplementedAccessors(type);
	}

	public static AccessorField[] getUnimplementedAccessors(IType type) {
		try {
			return GenerateGetterSetterOperation.getUnimplementedAccessors(type);
		} catch (JavaModelException e) {
			JavaLanguageServerPlugin.logException("Failed to resolve the unimplemented accessors.", e);
			return new AccessorField[0];
		}
	}

	public static WorkspaceEdit generateAccessors(GenerateAccessorsParams params, IProgressMonitor monitor) {
		IType type = SourceAssistProcessor.getSelectionType(params.context);
		if (type == null || type.getCompilationUnit() == null) {
			return null;
		}

		Preferences preferences = JavaLanguageServerPlugin.getPreferencesManager().getPreferences();
		TextEdit edit = generateAccessors(type, params.accessors, preferences.isCodeGenerationTemplateGenerateComments(), params.context.getRange(), monitor);
		return (edit == null) ? null : SourceAssistProcessor.convertToWorkspaceEdit(type.getCompilationUnit(), edit);
	}

	public static TextEdit generateAccessors(IType type, AccessorField[] accessors, boolean generateComments, Range cursor, IProgressMonitor monitor) {
		if (type == null || type.getCompilationUnit() == null) {
			return null;
		}

		try {
			ASTNode declarationNode = null;
			CompilationUnit astRoot = CoreASTProvider.getInstance().getAST(type.getCompilationUnit(), CoreASTProvider.WAIT_YES, monitor);
			if (astRoot != null && cursor != null) {
				ASTNode node = NodeFinder.perform(astRoot, DiagnosticsHelper.getStartOffset(type.getCompilationUnit(), cursor), DiagnosticsHelper.getLength(type.getCompilationUnit(), cursor));
				declarationNode = SourceAssistProcessor.getDeclarationNode(node);
			}
			// If cursor position is not specified, then insert to the last by default.
			IJavaElement insertPosition = (declarationNode instanceof TypeDeclaration) ? CodeGenerationUtils.findInsertElement(type, null) : CodeGenerationUtils.findInsertElement(type, cursor);
			GenerateGetterSetterOperation operation = new GenerateGetterSetterOperation(type, null, generateComments, insertPosition);
			return operation.createTextEdit(null, accessors);
		} catch (OperationCanceledException | CoreException e) {
			JavaLanguageServerPlugin.logException("Failed to generate the accessors.", e);
			return null;
		}
	}

	// For test purpose
	public static TextEdit generateAccessors(IType type, AccessorField[] accessors, boolean generateComments, Range cursor) {
		return generateAccessors(type, accessors, generateComments, cursor, new NullProgressMonitor());
	}

	public static class GenerateAccessorsParams {
		CodeActionParams context;
		AccessorField[] accessors;
	}
}
