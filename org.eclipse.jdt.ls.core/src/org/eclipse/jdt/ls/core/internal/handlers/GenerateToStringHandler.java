/*******************************************************************************
 * Copyright (c) 2019 Microsoft Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Microsoft Corporation - initial API and implementation
 *******************************************************************************/

package org.eclipse.jdt.ls.core.internal.handlers;

import java.util.stream.Stream;

import org.apache.commons.lang3.StringUtils;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.IVariableBinding;
import org.eclipse.jdt.internal.corext.codemanipulation.tostringgeneration.GenerateToStringOperation;
import org.eclipse.jdt.internal.corext.codemanipulation.tostringgeneration.ToStringGenerationSettingsCore;
import org.eclipse.jdt.internal.corext.codemanipulation.tostringgeneration.ToStringGenerationSettingsCore.CustomBuilderSettings;
import org.eclipse.jdt.internal.corext.dom.ASTNodes;
import org.eclipse.jdt.internal.corext.dom.IASTSharedValues;
import org.eclipse.jdt.internal.corext.refactoring.util.RefactoringASTParser;
import org.eclipse.jdt.ls.core.internal.JavaLanguageServerPlugin;
import org.eclipse.jdt.ls.core.internal.handlers.JdtDomModels.LspVariableBinding;
import org.eclipse.jdt.ls.core.internal.preferences.Preferences;
import org.eclipse.jdt.ls.core.internal.text.correction.SourceAssistProcessor;
import org.eclipse.lsp4j.CodeActionParams;
import org.eclipse.lsp4j.WorkspaceEdit;
import org.eclipse.text.edits.TextEdit;

public class GenerateToStringHandler {
	private static final String METHODNAME_TOSTRING = "toString";
	public static final String DEFAULT_TEMPLATE = "${object.className} [${member.name()}=${member.value}, ${otherMembers}]";

	public static CheckToStringResponse checkToStringStatus(CodeActionParams params) {
		IType type = SourceAssistProcessor.getSelectionType(params);
		return checkToStringStatus(type);
	}

	public static CheckToStringResponse checkToStringStatus(IType type) {
		CheckToStringResponse response = new CheckToStringResponse();
		if (type == null) {
			return response;
		}

		try {
			RefactoringASTParser astParser = new RefactoringASTParser(IASTSharedValues.SHARED_AST_LEVEL);
			CompilationUnit astRoot = astParser.parse(type.getCompilationUnit(), true);
			ITypeBinding typeBinding = ASTNodes.getTypeBinding(astRoot, type);
			if (typeBinding != null) {
				response.type = type.getTypeQualifiedName();
				response.fields = JdtDomModels.getDeclaredFields(typeBinding, false);
				response.existed = Stream.of(typeBinding.getDeclaredMethods()).anyMatch(method -> method.getName().equals(METHODNAME_TOSTRING) && method.getParameterTypes().length == 0);
			}
		} catch (JavaModelException e) {
			JavaLanguageServerPlugin.logException("Failed to check toString status", e);
		}

		return response;
	}

	public static WorkspaceEdit generateToString(GenerateToStringParams params) {
		Preferences preferences = JavaLanguageServerPlugin.getPreferencesManager().getPreferences();
		ToStringGenerationSettingsCore settings = new ToStringGenerationSettingsCore();
		settings.overrideAnnotation = true;
		settings.createComments = preferences.isCodeGenerationTemplateGenerateComments();
		settings.useBlocks = preferences.isCodeGenerationTemplateUseBlocks();
		settings.stringFormatTemplate = StringUtils.isBlank(preferences.getGenerateToStringTemplate()) ? DEFAULT_TEMPLATE : preferences.getGenerateToStringTemplate();
		settings.toStringStyle = getToStringStyle(preferences.getGenerateToStringCodeStyle());
		settings.skipNulls = preferences.isGenerateToStringSkipNullValues();
		settings.customArrayToString = preferences.isGenerateToStringListArrayContents();
		settings.limitElements = preferences.getGenerateToStringLimitElements() > 0;
		settings.limitValue = Math.max(preferences.getGenerateToStringLimitElements(), 0);
		settings.customBuilderSettings = new CustomBuilderSettings();
		TextEdit edit = generateToString(params, settings);
		return (edit == null) ? null : SourceAssistProcessor.convertToWorkspaceEdit(SourceAssistProcessor.getCompilationUnit(params.context), edit);
	}

	public static TextEdit generateToString(GenerateToStringParams params, ToStringGenerationSettingsCore settings) {
		IType type = SourceAssistProcessor.getSelectionType(params.context);
		if (type == null) {
			return null;
		}

		return generateToString(type, params.fields, settings);
	}

	public static TextEdit generateToString(IType type, LspVariableBinding[] fields, ToStringGenerationSettingsCore settings) {
		if (type == null) {
			return null;
		}

		try {
			RefactoringASTParser astParser = new RefactoringASTParser(IASTSharedValues.SHARED_AST_LEVEL);
			CompilationUnit astRoot = astParser.parse(type.getCompilationUnit(), true);
			ITypeBinding typeBinding = ASTNodes.getTypeBinding(astRoot, type);
			if (typeBinding != null) {
				IVariableBinding[] selectedFields = JdtDomModels.convertToVariableBindings(typeBinding, fields);
				GenerateToStringOperation operation = GenerateToStringOperation.createOperation(typeBinding, selectedFields, astRoot, null, settings, false, false);
				operation.run(null);
				return operation.getResultingEdit();
			}
		} catch (CoreException e) {
			JavaLanguageServerPlugin.logException("Failed to generate toString()", e);
		}
		return null;
	}

	private static int getToStringStyle(String codeStyle) {
		switch (codeStyle) {
			case "STRING_CONCATENATION":
				return GenerateToStringOperation.STRING_CONCATENATION;
			case "STRING_BUILDER":
				return GenerateToStringOperation.STRING_BUILDER;
			case "STRING_BUILDER_CHAINED":
				return GenerateToStringOperation.STRING_BUILDER_CHAINED;
			case "STRING_FORMAT":
				return GenerateToStringOperation.STRING_FORMAT;
			default:
				return GenerateToStringOperation.STRING_CONCATENATION;
		}
	}

	public static class CheckToStringResponse {
		public String type;
		public LspVariableBinding[] fields;
		public boolean existed;
	}

	public static class GenerateToStringParams {
		public CodeActionParams context;
		public LspVariableBinding[] fields;
	}
}
