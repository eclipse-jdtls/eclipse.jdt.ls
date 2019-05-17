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
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.jdt.core.IField;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.Signature;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.IMethodBinding;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.IVariableBinding;
import org.eclipse.jdt.internal.corext.codemanipulation.AddDelegateMethodsOperation;
import org.eclipse.jdt.internal.corext.codemanipulation.AddDelegateMethodsOperation.DelegateEntry;
import org.eclipse.jdt.internal.corext.codemanipulation.CodeGenerationSettings;
import org.eclipse.jdt.internal.corext.codemanipulation.StubUtility2Core;
import org.eclipse.jdt.internal.corext.dom.ASTNodes;
import org.eclipse.jdt.internal.corext.dom.IASTSharedValues;
import org.eclipse.jdt.internal.corext.refactoring.util.RefactoringASTParser;
import org.eclipse.jdt.ls.core.internal.JavaLanguageServerPlugin;
import org.eclipse.jdt.ls.core.internal.corext.util.JdtFlags;
import org.eclipse.jdt.ls.core.internal.handlers.JdtDomModels.LspMethodBinding;
import org.eclipse.jdt.ls.core.internal.handlers.JdtDomModels.LspVariableBinding;
import org.eclipse.jdt.ls.core.internal.preferences.Preferences;
import org.eclipse.jdt.ls.core.internal.text.correction.SourceAssistProcessor;
import org.eclipse.lsp4j.CodeActionParams;
import org.eclipse.lsp4j.WorkspaceEdit;
import org.eclipse.text.edits.TextEdit;

public class GenerateDelegateMethodsHandler {
	public static boolean supportsGenerateDelegateMethods(IType type) throws JavaModelException {
		if (type == null || type.getCompilationUnit() == null || type.isAnnotation() || type.isInterface()) {
			return false;
		}

		IField[] fields = type.getFields();
		int count = 0;
		for (IField field : fields) {
			if (!JdtFlags.isEnum(field) && !hasPrimitiveType(field) && !isArray(field)) {
				count++;
			}
		}

		return count > 0;
	}

	private static boolean hasPrimitiveType(IField field) throws JavaModelException {
		String signature = field.getTypeSignature();
		char first = Signature.getElementType(signature).charAt(0);
		return (first != Signature.C_RESOLVED && first != Signature.C_UNRESOLVED);
	}

	private static boolean isArray(IField field) throws JavaModelException {
		return Signature.getArrayCount(field.getTypeSignature()) > 0;
	}

	public static CheckDelegateMethodsResponse checkDelegateMethodsStatus(CodeActionParams params) {
		IType type = SourceAssistProcessor.getSelectionType(params);
		if (type == null || type.getCompilationUnit() == null) {
			return new CheckDelegateMethodsResponse();
		}

		try {
			RefactoringASTParser astParser = new RefactoringASTParser(IASTSharedValues.SHARED_AST_LEVEL);
			CompilationUnit astRoot = astParser.parse(type.getCompilationUnit(), true);
			ITypeBinding typeBinding = ASTNodes.getTypeBinding(astRoot, type);
			if (typeBinding == null) {
				return new CheckDelegateMethodsResponse();
			}

			DelegateEntry[] delegateEntries = StubUtility2Core.getDelegatableMethods(typeBinding);
			Map<IVariableBinding, List<IMethodBinding>> fieldToMethods = new LinkedHashMap<>();
			for (DelegateEntry delegateEntry : delegateEntries) {
				List<IMethodBinding> methods = fieldToMethods.getOrDefault(delegateEntry.field, new ArrayList<>());
				methods.add(delegateEntry.delegateMethod);
				fieldToMethods.put(delegateEntry.field, methods);
			}

			//@formatter:off
			return new CheckDelegateMethodsResponse(fieldToMethods.entrySet().stream()
					.map(entry -> new LspDelegateField(entry.getKey(), entry.getValue().toArray(new IMethodBinding[0])))
					.toArray(LspDelegateField[]::new));
			//@formatter:on
		} catch (JavaModelException e) {
			JavaLanguageServerPlugin.logException("Failed to check delegate methods status", e);
		}

		return new CheckDelegateMethodsResponse();
	}

	public static WorkspaceEdit generateDelegateMethods(GenerateDelegateMethodsParams params) {
		IType type = SourceAssistProcessor.getSelectionType(params.context);
		Preferences preferences = JavaLanguageServerPlugin.getPreferencesManager().getPreferences();
		CodeGenerationSettings settings = new CodeGenerationSettings();
		settings.createComments = preferences.isCodeGenerationTemplateGenerateComments();
		TextEdit edit = generateDelegateMethods(type, params.delegateEntries, settings);
		return (edit == null) ? null : SourceAssistProcessor.convertToWorkspaceEdit(type.getCompilationUnit(), edit);
	}

	public static TextEdit generateDelegateMethods(IType type, LspDelegateEntry[] delegateEntries, CodeGenerationSettings settings) {
		if (type == null || type.getCompilationUnit() == null || delegateEntries == null || delegateEntries.length == 0) {
			return null;
		}

		try {
			RefactoringASTParser astParser = new RefactoringASTParser(IASTSharedValues.SHARED_AST_LEVEL);
			CompilationUnit astRoot = astParser.parse(type.getCompilationUnit(), true);
			ITypeBinding typeBinding = ASTNodes.getTypeBinding(astRoot, type);
			if (typeBinding == null) {
				return null;
			}

			DelegateEntry[] methodEntries = StubUtility2Core.getDelegatableMethods(typeBinding);
			Map<String, DelegateEntry> delegateEntryMap = new HashMap<>();
			for (DelegateEntry methodEntry : methodEntries) {
				delegateEntryMap.put(methodEntry.field.getKey() + "#" + methodEntry.delegateMethod.getKey(), methodEntry);
			}

			//@formatter:off
			DelegateEntry[] selectedDelegateEntries = Arrays.stream(delegateEntries)
				.map(delegateEntry -> delegateEntryMap.get(delegateEntry.field.bindingKey + "#" + delegateEntry.delegateMethod.bindingKey))
				.filter(delegateEntry -> delegateEntry != null)
				.toArray(DelegateEntry[]::new);
			//@formatter:on
			AddDelegateMethodsOperation operation = new AddDelegateMethodsOperation(astRoot, selectedDelegateEntries, null, settings, false, false);
			operation.run(null);
			return operation.getResultingEdit();
		} catch (CoreException e) {
			JavaLanguageServerPlugin.logException("Failed to generate delegate methods", e);
		}

		return null;
	}

	public static class CheckDelegateMethodsResponse {
		public LspDelegateField[] delegateFields = new LspDelegateField[0];

		public CheckDelegateMethodsResponse() {
		}

		public CheckDelegateMethodsResponse(LspDelegateField[] delegateField) {
			this.delegateFields = delegateField;
		}
	}

	public static class LspDelegateField {
		public LspVariableBinding field;
		public LspMethodBinding[] delegateMethods = new LspMethodBinding[0];

		public LspDelegateField(IVariableBinding field, IMethodBinding[] methods) {
			this.field = new LspVariableBinding(field);
			this.delegateMethods = Arrays.stream(methods).map(method -> new LspMethodBinding(method)).toArray(LspMethodBinding[]::new);
		}
	}

	public static class LspDelegateEntry {
		public LspVariableBinding field;
		public LspMethodBinding delegateMethod;

		public LspDelegateEntry(LspVariableBinding field, LspMethodBinding delegateMethod) {
			this.field = field;
			this.delegateMethod = delegateMethod;
		}
	}

	public static class GenerateDelegateMethodsParams {
		public CodeActionParams context;
		public LspDelegateEntry[] delegateEntries = new LspDelegateEntry[0];
	}
}
