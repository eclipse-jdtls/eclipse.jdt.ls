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
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;

import org.apache.commons.lang3.StringUtils;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.jdt.core.IField;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IMember;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.IBinding;
import org.eclipse.jdt.core.dom.IMethodBinding;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.IVariableBinding;
import org.eclipse.jdt.core.dom.Modifier;
import org.eclipse.jdt.core.dom.NodeFinder;
import org.eclipse.jdt.core.manipulation.CoreASTProvider;
import org.eclipse.jdt.internal.corext.codemanipulation.tostringgeneration.GenerateToStringOperation;
import org.eclipse.jdt.internal.corext.codemanipulation.tostringgeneration.ToStringGenerationSettingsCore;
import org.eclipse.jdt.internal.corext.codemanipulation.tostringgeneration.ToStringGenerationSettingsCore.CustomBuilderSettings;
import org.eclipse.jdt.internal.corext.dom.ASTNodes;
import org.eclipse.jdt.internal.corext.util.JavaModelUtil;
import org.eclipse.jdt.ls.core.internal.JavaLanguageServerPlugin;
import org.eclipse.jdt.ls.core.internal.corrections.DiagnosticsHelper;
import org.eclipse.jdt.ls.core.internal.handlers.JdtDomModels.BindingComparator;
import org.eclipse.jdt.ls.core.internal.handlers.JdtDomModels.LspVariableBinding;
import org.eclipse.jdt.ls.core.internal.preferences.Preferences;
import org.eclipse.jdt.ls.core.internal.text.correction.CodeActionUtility;
import org.eclipse.jdt.ls.core.internal.text.correction.SourceAssistProcessor;
import org.eclipse.lsp4j.CodeActionParams;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.WorkspaceEdit;
import org.eclipse.text.edits.TextEdit;

public class GenerateToStringHandler {
	public static final String METHODNAME_TOSTRING = "toString";
	public static final String DEFAULT_TEMPLATE = "${object.className} [${member.name()}=${member.value}, ${otherMembers}]";

	// For test purpose
	public static CheckToStringResponse checkToStringStatus(CodeActionParams params) {
		return checkToStringStatus(params, new NullProgressMonitor());
	}

	public static CheckToStringResponse checkToStringStatus(CodeActionParams params, IProgressMonitor monitor) {
		IType type = SourceAssistProcessor.getSelectionType(params, monitor);
		return checkToStringStatus(type, monitor);
	}

	// For test purpose
	public static CheckToStringResponse checkToStringStatus(IType type) {
		return checkToStringStatus(type, new NullProgressMonitor());
	}

	public static CheckToStringResponse checkToStringStatus(IType type, IProgressMonitor monitor) {
		CheckToStringResponse response = new CheckToStringResponse();
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
				IVariableBinding[] fields = typeBinding.getDeclaredFields();
				HashMap<IJavaElement, IVariableBinding> fieldsToBindingsMap = new HashMap<>();
				HashMap<IJavaElement, IVariableBinding> selectedFieldsToBindingsMap = new HashMap<>();
				for (IVariableBinding variableBinding : fields) {
					if (!Modifier.isStatic(variableBinding.getModifiers())) {
						fieldsToBindingsMap.put(variableBinding.getJavaElement(), variableBinding);
						if (!Modifier.isTransient(variableBinding.getModifiers())) {
							selectedFieldsToBindingsMap.put(variableBinding.getJavaElement(), variableBinding);
						}
					}
				}
				final IField[] allFields;
				if (type.isRecord()) {
					allFields = type.getRecordComponents();
				} else {
					allFields = type.getFields();
				}
				List<IVariableBinding> fieldsToBindings = new ArrayList<>();
				List<IVariableBinding> selectedFieldsToBindings = new ArrayList<>();
				for (IMember member : allFields) {
					IVariableBinding memberBinding = selectedFieldsToBindingsMap.remove(member);
					if (memberBinding != null) {
						selectedFieldsToBindings.add(memberBinding);
						fieldsToBindingsMap.remove(member);
					}
				}
				for (IMember member : allFields) {
					IVariableBinding memberBinding = fieldsToBindingsMap.remove(member);
					if (memberBinding != null) {
						fieldsToBindings.add(memberBinding);
					}
				}
				selectedFieldsToBindings.sort(new BindingComparator());
				fieldsToBindings.sort(new BindingComparator());
				List<IVariableBinding> inheritedFieldsToBindings = new ArrayList<>();
				ITypeBinding superTypeBinding = typeBinding;
				while ((superTypeBinding = superTypeBinding.getSuperclass()) != null) {
					for (IVariableBinding candidateField : superTypeBinding.getDeclaredFields()) {
						if (!Modifier.isPrivate(candidateField.getModifiers()) && !Modifier.isStatic(candidateField.getModifiers()) && !JdtDomModels.contains(fieldsToBindings, candidateField)) {
							inheritedFieldsToBindings.add(candidateField);
						}
					}
				}
				inheritedFieldsToBindings.sort(new BindingComparator());
				List<IMethodBinding> methodsToBindings = new ArrayList<>();
				for (IMethodBinding candidateMethod : typeBinding.getDeclaredMethods()) {
					if (!Modifier.isStatic(candidateMethod.getModifiers()) && candidateMethod.getParameterTypes().length == 0 && !"void".equals(candidateMethod.getReturnType().getName()) && !"toString".equals(candidateMethod.getName()) //$NON-NLS-1$//$NON-NLS-2$
							&& !"clone".equals(candidateMethod.getName())) { //$NON-NLS-1$
						methodsToBindings.add(candidateMethod);
					}
				}
				methodsToBindings.sort(new BindingComparator());
				superTypeBinding = typeBinding;
				List<IMethodBinding> inheritedMethodsToBindings = new ArrayList<>();
				while ((superTypeBinding = superTypeBinding.getSuperclass()) != null) {
					for (IMethodBinding candidateMethod : superTypeBinding.getDeclaredMethods()) {
						if (!Modifier.isPrivate(candidateMethod.getModifiers()) && !Modifier.isStatic(candidateMethod.getModifiers()) && candidateMethod.getParameterTypes().length == 0
								&& !"void".equals(candidateMethod.getReturnType().getName()) && !JdtDomModels.contains(methodsToBindings, candidateMethod) && !"clone".equals(candidateMethod.getName())) { //$NON-NLS-1$ //$NON-NLS-2$
							inheritedMethodsToBindings.add(candidateMethod);
						}
					}
				}
				inheritedMethodsToBindings.sort(new BindingComparator());
				List<LspVariableBinding> result = new LinkedList<>();
				result.addAll(selectedFieldsToBindings.stream().map(f -> new LspVariableBinding(f, true)).toList());
				result.addAll(fieldsToBindings.stream().map(f -> new LspVariableBinding(f)).toList());
				result.addAll(inheritedFieldsToBindings.stream().map(f -> new LspVariableBinding(f)).toList());
				result.addAll(methodsToBindings.stream().map(f -> new LspVariableBinding(f)).toList());
				result.addAll(inheritedMethodsToBindings.stream().map(f -> new LspVariableBinding(f)).toList());
				response.fields = result.toArray(new LspVariableBinding[0]);
				response.exists = CodeActionUtility.hasMethod(type, METHODNAME_TOSTRING);
			}
		} catch (JavaModelException e) {
			JavaLanguageServerPlugin.logException("Failed to check toString status", e);
		}
		return response;
	}

	public static WorkspaceEdit generateToString(GenerateToStringParams params, IProgressMonitor monitor) {
		IType type = SourceAssistProcessor.getSelectionType(params.context, monitor);
		if (type == null || type.getCompilationUnit() == null) {
			return null;
		}
		Range range = params.context.getRange();
		ASTNode declarationNode = null;
		CompilationUnit astRoot = CoreASTProvider.getInstance().getAST(type.getCompilationUnit(), CoreASTProvider.WAIT_YES, monitor);
		if (astRoot != null && range != null) {
			ASTNode node = NodeFinder.perform(astRoot, DiagnosticsHelper.getStartOffset(type.getCompilationUnit(), range), DiagnosticsHelper.getLength(type.getCompilationUnit(), range));
			declarationNode = SourceAssistProcessor.getTypeDeclarationNode(node);
		}
		// If cursor position is not specified, then insert to the last by default.
		IJavaElement insertPosition = (declarationNode != null) ? CodeGenerationUtils.findInsertElement(type, null) : CodeGenerationUtils.findInsertElement(type, range);
		TextEdit edit = generateToString(type, params.fields, insertPosition, monitor);
		return (edit == null) ? null : SourceAssistProcessor.convertToWorkspaceEdit(type.getCompilationUnit(), edit);
	}

	public static TextEdit generateToString(IType type, LspVariableBinding[] fields, IJavaElement insertPosition, IProgressMonitor monitor) {
		if (type == null || type.getCompilationUnit() == null) {
			return null;
		}

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
		if (type.getCompilationUnit().getJavaProject() != null) {
			String version = type.getCompilationUnit().getJavaProject().getOption(JavaCore.COMPILER_SOURCE, true);
			settings.is50orHigher = !JavaModelUtil.isVersionLessThan(version, JavaCore.VERSION_1_5);
			settings.is60orHigher = !JavaModelUtil.isVersionLessThan(version, JavaCore.VERSION_1_6);
		}

		return generateToString(type, fields, settings, insertPosition, monitor);
	}

	public static TextEdit generateToString(IType type, LspVariableBinding[] fields, ToStringGenerationSettingsCore settings, IJavaElement insertPosition, IProgressMonitor monitor) {
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
				IBinding[] selectedFields = JdtDomModels.convertToBindings(typeBinding, fields);
				GenerateToStringOperation operation = GenerateToStringOperation.createOperation(typeBinding, selectedFields, astRoot, insertPosition, settings, false, false);
				operation.run(null);
				return operation.getResultingEdit();
			}
		} catch (CoreException e) {
			JavaLanguageServerPlugin.logException("Failed to generate toString()", e);
		}
		return null;
	}

	private static int getToStringStyle(String codeStyle) {
		if (StringUtils.isBlank(codeStyle)) {
			return GenerateToStringOperation.STRING_CONCATENATION;
		}

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
		public boolean exists;
	}

	public static class GenerateToStringParams {
		public CodeActionParams context;
		public LspVariableBinding[] fields;
	}
}
