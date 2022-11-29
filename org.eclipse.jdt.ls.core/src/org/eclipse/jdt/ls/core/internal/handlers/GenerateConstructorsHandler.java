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
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IField;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.IMethodBinding;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.IVariableBinding;
import org.eclipse.jdt.core.dom.Modifier;
import org.eclipse.jdt.core.dom.NodeFinder;
import org.eclipse.jdt.core.dom.VariableDeclarationFragment;
import org.eclipse.jdt.core.manipulation.CoreASTProvider;
import org.eclipse.jdt.internal.corext.codemanipulation.AddCustomConstructorOperation;
import org.eclipse.jdt.internal.corext.codemanipulation.CodeGenerationSettings;
import org.eclipse.jdt.internal.corext.codemanipulation.StubUtility2Core;
import org.eclipse.jdt.internal.corext.dom.ASTNodes;
import org.eclipse.jdt.internal.corext.dom.Bindings;
import org.eclipse.jdt.ls.core.internal.JavaLanguageServerPlugin;
import org.eclipse.jdt.ls.core.internal.corrections.DiagnosticsHelper;
import org.eclipse.jdt.ls.core.internal.corrections.InnovationContext;
import org.eclipse.jdt.ls.core.internal.handlers.JdtDomModels.LspMethodBinding;
import org.eclipse.jdt.ls.core.internal.handlers.JdtDomModels.LspVariableBinding;
import org.eclipse.jdt.ls.core.internal.preferences.Preferences;
import org.eclipse.jdt.ls.core.internal.text.correction.CodeActionUtility;
import org.eclipse.jdt.ls.core.internal.text.correction.QuickAssistProcessor;
import org.eclipse.jdt.ls.core.internal.text.correction.SourceAssistProcessor;
import org.eclipse.lsp4j.CodeActionParams;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.WorkspaceEdit;
import org.eclipse.text.edits.MultiTextEdit;
import org.eclipse.text.edits.TextEdit;

public class GenerateConstructorsHandler {
	// For test purpose
	public static CheckConstructorsResponse checkConstructorsStatus(CodeActionParams params) {
		return checkConstructorsStatus(params, new NullProgressMonitor());
	}

	public static CheckConstructorsResponse checkConstructorsStatus(CodeActionParams params, IProgressMonitor monitor) {
		IType type = SourceAssistProcessor.getSelectionType(params, monitor);
		return checkConstructorStatus(type, params.getRange(), monitor);
	}

	public static CheckConstructorsResponse checkConstructorStatus(IType type, Range range, IProgressMonitor monitor) {
		if (type == null || type.getCompilationUnit() == null) {
			return new CheckConstructorsResponse();
		}

		try {
			ICompilationUnit compilationUnit = type.getCompilationUnit();
			if (compilationUnit == null) {
				return new CheckConstructorsResponse();
			}

			CompilationUnit astRoot = CoreASTProvider.getInstance().getAST(type.getCompilationUnit(), CoreASTProvider.WAIT_YES, monitor);
			if (astRoot == null) {
				return new CheckConstructorsResponse();
			}

			ITypeBinding typeBinding = ASTNodes.getTypeBinding(astRoot, type);
			if (typeBinding == null) {
				return new CheckConstructorsResponse();
			}

			IMethodBinding[] superConstructors = getVisibleConstructors(astRoot, typeBinding);
			Map<IJavaElement, IVariableBinding> fieldsToBindings = new HashMap<>();
			for (IVariableBinding field : typeBinding.getDeclaredFields()) {
				if (field.isSynthetic() || Modifier.isStatic(field.getModifiers())) {
					continue;
				}

				if (Modifier.isFinal(field.getModifiers())) {
					ASTNode declaringNode = astRoot.findDeclaringNode(field);
					// Do not add final fields which have been set in the <clinit>
					if (declaringNode instanceof VariableDeclarationFragment variableDecl && variableDecl.getInitializer() != null) {
						continue;
					}
				}

				fieldsToBindings.put(field.getJavaElement(), field);
			}

			List<IVariableBinding> fields = new ArrayList<>();
			// Sort the fields by the order in which they appear in the source or class file.
			for (IField field : type.getFields()) {
				IVariableBinding fieldBinding = fieldsToBindings.remove(field);
				if (fieldBinding != null) {
					fields.add(fieldBinding);
				}
			}

			List<String> fieldNames = getFieldNames(compilationUnit, astRoot, range);
			//@formatter:off
			return new CheckConstructorsResponse(
				Arrays.stream(superConstructors).map(binding -> new LspMethodBinding(binding)).toArray(LspMethodBinding[]::new),
				fields.stream().map(binding -> fieldNames.contains(binding.getName()) ? new LspVariableBinding(binding, true) : new LspVariableBinding(binding)).toArray(LspVariableBinding[]::new)
			);
			//@formatter:on
		} catch (JavaModelException e) {
			JavaLanguageServerPlugin.logException("Failed to check constructor status", e);
		}

		return new CheckConstructorsResponse();
	}

	private static IMethodBinding getObjectConstructor(AST ast) {
		final ITypeBinding binding = ast.resolveWellKnownType("java.lang.Object");
		return Bindings.findMethodInType(binding, "Object", new ITypeBinding[0]);
	}

	private static IMethodBinding[] getVisibleConstructors(CompilationUnit astRoot, ITypeBinding typeBinding) {
		if (typeBinding.isEnum()) {
			return new IMethodBinding[] { getObjectConstructor(astRoot.getAST()) };
		} else {
			return StubUtility2Core.getVisibleConstructors(typeBinding, false, true);
		}
	}

	public static WorkspaceEdit generateConstructors(GenerateConstructorsParams params, IProgressMonitor monitor) {
		IType type = SourceAssistProcessor.getSelectionType(params.context, monitor);
		TextEdit edit = generateConstructors(type, params.constructors, params.fields, params.context.getRange(), monitor);
		return (edit == null) ? null : SourceAssistProcessor.convertToWorkspaceEdit(type.getCompilationUnit(), edit);
	}

	public static TextEdit generateConstructors(IType type, LspMethodBinding[] constructors, LspVariableBinding[] fields, Range cursor, IProgressMonitor monitor) {
		Preferences preferences = JavaLanguageServerPlugin.getPreferencesManager().getPreferences();
		CodeGenerationSettings settings = new CodeGenerationSettings();
		settings.createComments = preferences.isCodeGenerationTemplateGenerateComments();
		return generateConstructors(type, constructors, fields, settings, cursor, monitor);
	}

	public static TextEdit generateConstructors(IType type, LspMethodBinding[] constructors, LspVariableBinding[] fields, CodeGenerationSettings settings, Range cursor, IProgressMonitor monitor) {
		if (type == null || type.getCompilationUnit() == null || constructors == null || constructors.length == 0) {
			return null;
		}

		try {
			CompilationUnit astRoot = CoreASTProvider.getInstance().getAST(type.getCompilationUnit(), CoreASTProvider.WAIT_YES, monitor);
			if (astRoot == null) {
				return null;
			}

			ITypeBinding typeBinding = ASTNodes.getTypeBinding(astRoot, type);
			if (typeBinding != null) {

				ASTNode declarationNode = null;
				if (cursor != null) {
					ASTNode node = NodeFinder.perform(astRoot, DiagnosticsHelper.getStartOffset(type.getCompilationUnit(), cursor), DiagnosticsHelper.getLength(type.getCompilationUnit(), cursor));
					declarationNode = SourceAssistProcessor.getTypeDeclarationNode(node);
				}
				// If cursor position is not specified, then insert to the last by default.
				IJavaElement insertPosition = (declarationNode != null) ? CodeGenerationUtils.findInsertElementAfterLastField(type) : CodeGenerationUtils.findInsertElement(type, cursor);

				Map<String, IVariableBinding> fieldBindings = new HashMap<>();
				for (IVariableBinding binding : typeBinding.getDeclaredFields()) {
					fieldBindings.put(binding.getKey(), binding);
				}
				IVariableBinding[] selectedFields = Arrays.stream(fields).map(field -> fieldBindings.get(field.bindingKey)).filter(binding -> binding != null).toArray(IVariableBinding[]::new);
				IMethodBinding[] superConstructors = getVisibleConstructors(astRoot, typeBinding);
				TextEdit textEdit = new MultiTextEdit();
				for (LspMethodBinding constructor : constructors) {
					Optional<IMethodBinding> selectedSuperConstructor = Arrays.stream(superConstructors).filter(superConstructor -> compareConstructor(superConstructor, constructor)).findAny();
					if (selectedSuperConstructor.isPresent()) {
						IMethodBinding superConstructor = selectedSuperConstructor.get();
						AddCustomConstructorOperation constructorOperation = new AddCustomConstructorOperation(astRoot, typeBinding, selectedFields, superConstructor, insertPosition, settings, false, false);
						constructorOperation.setOmitSuper(superConstructor.getParameterTypes().length == 0);
						constructorOperation.setVisibility(typeBinding.isEnum() ? Modifier.PRIVATE : Modifier.PUBLIC);
						constructorOperation.run(null);
						textEdit.addChild(constructorOperation.getResultingEdit());
					}
				}

				return textEdit;
			}
		} catch (CoreException e) {
			JavaLanguageServerPlugin.logException("Failed to generate constructors", e);
		}

		return null;
	}

	private static boolean compareConstructor(IMethodBinding binding, LspMethodBinding lspBinding) {
		if (lspBinding == null) {
			return binding == null;
		}

		if (binding == null) {
			return lspBinding == null;
		}

		String[] parameters = Arrays.stream(binding.getParameterTypes()).map(type -> type.getName()).toArray(String[]::new);
		return Arrays.equals(parameters, lspBinding.parameters);
	}

	private static List<String> getFieldNames(ICompilationUnit unit, CompilationUnit astRoot, Range range) {
		if (range == null) {
			return Collections.emptyList();
		}
		InnovationContext context = CodeActionHandler.getContext(unit, astRoot, range);
		ArrayList<ASTNode> coveredNodes = QuickAssistProcessor.getFullyCoveredNodes(context, context.getCoveringNode());
		return CodeActionUtility.getFieldNames(coveredNodes, context.getCoveringNode());
	}

	public static class CheckConstructorsResponse {
		public LspMethodBinding[] constructors = new LspMethodBinding[0];
		public LspVariableBinding[] fields = new LspVariableBinding[0];

		public CheckConstructorsResponse() {
		}

		public CheckConstructorsResponse(LspMethodBinding[] constructors, LspVariableBinding[] fields) {
			this.constructors = constructors;
			this.fields = fields;
		}
	}

	public static class GenerateConstructorsParams {
		public CodeActionParams context;
		public LspMethodBinding[] constructors;
		public LspVariableBinding[] fields;
	}
}
