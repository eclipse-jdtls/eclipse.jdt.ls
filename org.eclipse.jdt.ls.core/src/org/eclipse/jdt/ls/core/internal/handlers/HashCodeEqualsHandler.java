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

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.IMethodBinding;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.IVariableBinding;
import org.eclipse.jdt.core.dom.Modifier;
import org.eclipse.jdt.internal.corext.codemanipulation.CodeGenerationSettings;
import org.eclipse.jdt.internal.corext.codemanipulation.GenerateHashCodeEqualsOperation;
import org.eclipse.jdt.internal.corext.dom.ASTNodes;
import org.eclipse.jdt.internal.corext.dom.IASTSharedValues;
import org.eclipse.jdt.internal.corext.refactoring.util.RefactoringASTParser;
import org.eclipse.jdt.ls.core.internal.JavaLanguageServerPlugin;
import org.eclipse.jdt.ls.core.internal.text.correction.SourceAssistProcessor;
import org.eclipse.lsp4j.CodeActionParams;
import org.eclipse.lsp4j.WorkspaceEdit;
import org.eclipse.text.edits.TextEdit;

public class HashCodeEqualsHandler {
	private static final String METHODNAME_HASH_CODE = "hashCode";
	private static final String METHODNAME_EQUALS = "equals";
	private static final boolean useBlocks = false;
	private static final boolean useInstanceOf = false;

	public static CheckHashCodeEqualsResponse checkHashCodeEqualsStatus(CodeActionParams params) {
		IType type = SourceAssistProcessor.getSelectionType(params);
		return checkHashCodeEqualsStatus(type);
	}

	public static CheckHashCodeEqualsResponse checkHashCodeEqualsStatus(IType type) {
		CheckHashCodeEqualsResponse response = new CheckHashCodeEqualsResponse();
		if (type == null) {
			return response;
		}

		try {
			RefactoringASTParser astParser = new RefactoringASTParser(IASTSharedValues.SHARED_AST_LEVEL);
			CompilationUnit astRoot = astParser.parse(type.getCompilationUnit(), true);
			ITypeBinding typeBinding = ASTNodes.getTypeBinding(astRoot, type);
			if (typeBinding == null) {
				return response;
			}

			IVariableBinding[] fields = typeBinding.getDeclaredFields();
			List<VariableField> validFields = new ArrayList<>();
			for (IVariableBinding field : fields) {
				if (!Modifier.isStatic(field.getModifiers())) {
					VariableField variableField = new VariableField();
					variableField.bindingKey = field.getKey();
					variableField.name = field.getName();
					variableField.type = field.getType().getName();
					validFields.add(variableField);
				}
			}

			response.type = type.getTypeQualifiedName();
			response.fields = validFields.toArray(new VariableField[0]);
			response.existingMethods = findExistingMethods(typeBinding);
		} catch (JavaModelException e) {
			JavaLanguageServerPlugin.logException("Check hashCode and equals status", e);
		}

		return response;
	}

	public static WorkspaceEdit generateHashCodeEquals(GenerateHashCodeEqualsParams params) {
		IType type = SourceAssistProcessor.getSelectionType(params.context);
		boolean useJ7HashEquals = JavaLanguageServerPlugin.getPreferencesManager().getPreferences().isCodeGenUseJ7HashEquals();
		TextEdit edit = generateHashCodeEqualsTextEdit(type, params.fields, params.regenerate, useJ7HashEquals);
		if (edit == null) {
			return null;
		}

		return SourceAssistProcessor.convertToWorkspaceEdit(type.getCompilationUnit(), edit);
	}

	public static TextEdit generateHashCodeEqualsTextEdit(GenerateHashCodeEqualsParams params, boolean useJ7HashEquals) {
		IType type = SourceAssistProcessor.getSelectionType(params.context);
		return generateHashCodeEqualsTextEdit(type, params.fields, params.regenerate, useJ7HashEquals);
	}

	public static TextEdit generateHashCodeEqualsTextEdit(IType type, VariableField[] fields, boolean regenerate, boolean useJ7HashEquals) {
		if (type == null) {
			return null;
		}

		try {
			RefactoringASTParser astParser = new RefactoringASTParser(IASTSharedValues.SHARED_AST_LEVEL);
			CompilationUnit astRoot = astParser.parse(type.getCompilationUnit(), true);
			ITypeBinding typeBinding = ASTNodes.getTypeBinding(astRoot, type);
			if (typeBinding == null) {
				return null;
			}

			IVariableBinding[] variableBindings = convertToVariableBindings(typeBinding, fields);
			CodeGenerationSettings codeGenSettings = new CodeGenerationSettings();
			codeGenSettings.createComments = false;
			codeGenSettings.overrideAnnotation = true;
			GenerateHashCodeEqualsOperation operation = new GenerateHashCodeEqualsOperation(typeBinding, variableBindings, astRoot, null, codeGenSettings, useInstanceOf, useJ7HashEquals, regenerate, false, false);
			operation.setUseBlocksForThen(useBlocks);
			operation.run(null);
			return operation.getResultingEdit();
		} catch (CoreException e) {
			JavaLanguageServerPlugin.logException("Generate hashCode and equals methods", e);
			return null;
		}
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
			}

			if (method.getName().equals(METHODNAME_HASH_CODE) && method.getParameterTypes().length == 0) {
				existingMethods.add(METHODNAME_HASH_CODE);
			}

			if (existingMethods.size() == 2) {
				break;
			}
		}

		return existingMethods.toArray(new String[0]);
	}

	private static IVariableBinding[] convertToVariableBindings(ITypeBinding typeBinding, VariableField[] fields) {
		Set<String> bindingKeys = Stream.of(fields).map((field) -> field.bindingKey).collect(Collectors.toSet());
		List<IVariableBinding> bindings = new ArrayList<>();
		for (IVariableBinding declaredField : typeBinding.getDeclaredFields()) {
			if (bindingKeys.contains(declaredField.getKey())) {
				bindings.add(declaredField);
			}
		}

		return bindings.toArray(new IVariableBinding[0]);
	}

	public static class VariableField {
		public String bindingKey;
		public String name;
		public String type;
	}

	public static class CheckHashCodeEqualsResponse {
		public String type;
		public VariableField[] fields;
		public String[] existingMethods;
	}

	public static class GenerateHashCodeEqualsParams {
		public CodeActionParams context;
		public VariableField[] fields;
		public boolean regenerate;
	}
}
