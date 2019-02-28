/*******************************************************************************
* Copyright (c) 2017-2019 Microsoft Corporation and others.
* All rights reserved. This program and the accompanying materials
* are made available under the terms of the Eclipse Public License v1.0
* which accompanies this distribution, and is available at
* http://www.eclipse.org/legal/epl-v10.html
*
* Contributors:
*     Microsoft Corporation - initial API and implementation
*******************************************************************************/

package org.eclipse.jdt.ls.core.internal.text.correction;

import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.core.runtime.OperationCanceledException;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.AnonymousClassDeclaration;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.IVariableBinding;
import org.eclipse.jdt.core.dom.TypeDeclaration;
import org.eclipse.jdt.core.manipulation.CoreASTProvider;
import org.eclipse.jdt.core.manipulation.OrganizeImportsOperation;
import org.eclipse.jdt.internal.corext.dom.ASTNodes;
import org.eclipse.jdt.internal.corext.dom.IASTSharedValues;
import org.eclipse.jdt.internal.corext.refactoring.util.RefactoringASTParser;
import org.eclipse.jdt.internal.ui.text.correction.IProblemLocationCore;
import org.eclipse.jdt.ls.core.internal.ChangeUtil;
import org.eclipse.jdt.ls.core.internal.JDTUtils;
import org.eclipse.jdt.ls.core.internal.JavaCodeActionKind;
import org.eclipse.jdt.ls.core.internal.JavaLanguageServerPlugin;
import org.eclipse.jdt.ls.core.internal.TextEditConverter;
import org.eclipse.jdt.ls.core.internal.codemanipulation.GenerateGetterSetterOperation;
import org.eclipse.jdt.ls.core.internal.corrections.CorrectionMessages;
import org.eclipse.jdt.ls.core.internal.corrections.DiagnosticsHelper;
import org.eclipse.jdt.ls.core.internal.corrections.IInvocationContext;
import org.eclipse.jdt.ls.core.internal.corrections.InnovationContext;
import org.eclipse.jdt.ls.core.internal.handlers.CodeActionHandler;
import org.eclipse.jdt.ls.core.internal.preferences.PreferenceManager;
import org.eclipse.lsp4j.CodeAction;
import org.eclipse.lsp4j.CodeActionContext;
import org.eclipse.lsp4j.CodeActionKind;
import org.eclipse.lsp4j.CodeActionParams;
import org.eclipse.lsp4j.Command;
import org.eclipse.lsp4j.WorkspaceEdit;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.eclipse.text.edits.TextEdit;

import com.google.common.collect.Sets;

public class SourceAssistProcessor {

	private static final Set<String> UNSUPPORTED_RESOURCES = Sets.newHashSet("module-info.java", "package-info.java");

	public static final String COMMAND_ID_ACTION_OVERRIDEMETHODSPROMPT = "java.action.overrideMethodsPrompt";
	public static final String COMMAND_ID_ACTION_HASHCODEEQUALSPROMPT = "java.action.hashCodeEqualsPrompt";

	private PreferenceManager preferenceManager;

	public SourceAssistProcessor(PreferenceManager preferenceManager) {
		this.preferenceManager = preferenceManager;
	}

	public List<Either<Command, CodeAction>> getSourceActionCommands(CodeActionParams params, IInvocationContext context, IProblemLocationCore[] locations) {
		List<Either<Command, CodeAction>> $ = new ArrayList<>();
		ICompilationUnit cu = context.getCompilationUnit();
		IType type = getSelectionType(context);

		// Organize Imports
		TextEdit organizeImportsEdit = getOrganizeImportsProposal(context);
		Optional<Either<Command, CodeAction>> organizeImports = convertToWorkspaceEditAction(params.getContext(), context.getCompilationUnit(), CorrectionMessages.ReorgCorrectionsSubProcessor_organizeimports_description,
				CodeActionKind.SourceOrganizeImports, organizeImportsEdit);
		addSourceActionCommand($, params.getContext(), organizeImports);

		if (!UNSUPPORTED_RESOURCES.contains(cu.getResource().getName())) {
			// Override/Implement Methods
			Optional<Either<Command, CodeAction>> overrideMethods = getOverrideMethodsAction(params);
			addSourceActionCommand($, params.getContext(), overrideMethods);
		}

		// Generate Getter and Setter
		TextEdit getterSetterEdit = getGetterSetterProposal(context, type);
		Optional<Either<Command, CodeAction>> getterSetter = convertToWorkspaceEditAction(params.getContext(), context.getCompilationUnit(), ActionMessages.GenerateGetterSetterAction_label,
				JavaCodeActionKind.SOURCE_GENERATE_ACCESSORS,
				getterSetterEdit);
		addSourceActionCommand($, params.getContext(), getterSetter);

		// Generate hashCode() and equals()
		if (supportsHashCodeEquals(context, type)) {
			Optional<Either<Command, CodeAction>> hashCodeEquals = getHashCodeEqualsAction(params);
			addSourceActionCommand($, params.getContext(), hashCodeEquals);
		}

		return $;
	}

	private void addSourceActionCommand(List<Either<Command, CodeAction>> result, CodeActionContext context, Optional<Either<Command, CodeAction>> target) {
		if (!target.isPresent()) {
			return;
		}

		Either<Command, CodeAction> targetAction = target.get();
		if (context.getOnly() != null && !context.getOnly().isEmpty()) {
			String kind = targetAction.getLeft() == null ? targetAction.getRight().getKind() : targetAction.getLeft().getCommand();
			if (!context.getOnly().contains(kind)) {
				return;
			}
		}

		result.add(targetAction);
	}

	private TextEdit getOrganizeImportsProposal(IInvocationContext context) {
		ICompilationUnit unit = context.getCompilationUnit();
		CompilationUnit astRoot = context.getASTRoot();
		OrganizeImportsOperation op = new OrganizeImportsOperation(unit, astRoot, true, false, true, null);
		try {
			return op.createTextEdit(null);
		} catch (OperationCanceledException | CoreException e) {
			JavaLanguageServerPlugin.logException("Resolve organize imports source action", e);
		}

		return null;
	}

	private Optional<Either<Command, CodeAction>> getOverrideMethodsAction(CodeActionParams params) {
		if (!preferenceManager.getClientPreferences().isOverrideMethodsPromptSupported()) {
			return Optional.empty();
		}

		Command command = new Command(ActionMessages.OverrideMethodsAction_label, COMMAND_ID_ACTION_OVERRIDEMETHODSPROMPT, Collections.singletonList(params));
		if (preferenceManager.getClientPreferences().isSupportedCodeActionKind(JavaCodeActionKind.SOURCE_OVERRIDE_METHODS)) {
			CodeAction codeAction = new CodeAction(ActionMessages.OverrideMethodsAction_label);
			codeAction.setKind(JavaCodeActionKind.SOURCE_OVERRIDE_METHODS);
			codeAction.setCommand(command);
			codeAction.setDiagnostics(Collections.EMPTY_LIST);
			return Optional.of(Either.forRight(codeAction));
		} else {
			return Optional.of(Either.forLeft(command));
		}
	}

	private TextEdit getGetterSetterProposal(IInvocationContext context, IType type) {
		try {
			if (!GenerateGetterSetterOperation.supportsGetterSetter(type)) {
				return null;
			}
		} catch (JavaModelException e) {
			return null;
		}

		CompilationUnit astRoot = context.getASTRoot();
		GenerateGetterSetterOperation operation = new GenerateGetterSetterOperation(type, astRoot);
		try {
			return operation.createTextEdit(null);
		} catch (OperationCanceledException | CoreException e) {
			JavaLanguageServerPlugin.logException("Resolve Getter and Setter source action", e);
		}

		return null;
	}

	private boolean supportsHashCodeEquals(IInvocationContext context, IType type) {
		try {
			if (type == null || type.isAnnotation() || type.isInterface() || type.isEnum() || type.getCompilationUnit() == null) {
				return false;
			}

			RefactoringASTParser astParser = new RefactoringASTParser(IASTSharedValues.SHARED_AST_LEVEL);
			CompilationUnit astRoot = astParser.parse(type.getCompilationUnit(), true);
			ITypeBinding typeBinding = ASTNodes.getTypeBinding(astRoot, type);
			if (typeBinding == null) {
				return false;
			}

			IVariableBinding[] fields = typeBinding.getDeclaredFields();
			List<IVariableBinding> validFields = new ArrayList<>();
			for (IVariableBinding field : fields) {
				if (!Modifier.isStatic(field.getModifiers())) {
					validFields.add(field);
				}
			}

			if (validFields.isEmpty()) {
				return false;
			}
		} catch (JavaModelException e) {
			return false;
		}

		return true;
	}

	private Optional<Either<Command, CodeAction>> getHashCodeEqualsAction(CodeActionParams params) {
		if (!preferenceManager.getClientPreferences().isHashCodeEqualsPromptSupported()) {
			return Optional.empty();
		}

		Command command = new Command(ActionMessages.GenerateHashCodeEqualsAction_label, COMMAND_ID_ACTION_HASHCODEEQUALSPROMPT, Collections.singletonList(params));
		if (preferenceManager.getClientPreferences().isSupportedCodeActionKind(JavaCodeActionKind.SOURCE_GENERATE_HASHCODE_EQUALS)) {
			CodeAction codeAction = new CodeAction(ActionMessages.GenerateHashCodeEqualsAction_label);
			codeAction.setKind(JavaCodeActionKind.SOURCE_GENERATE_HASHCODE_EQUALS);
			codeAction.setCommand(command);
			codeAction.setDiagnostics(Collections.EMPTY_LIST);
			return Optional.of(Either.forRight(codeAction));
		} else {
			return Optional.of(Either.forLeft(command));
		}
	}

	private Optional<Either<Command, CodeAction>> convertToWorkspaceEditAction(CodeActionContext context, ICompilationUnit cu, String name, String kind, TextEdit edit) {
		WorkspaceEdit workspaceEdit = convertToWorkspaceEdit(cu, edit);
		if (!ChangeUtil.hasChanges(workspaceEdit)) {
			return Optional.empty();
		}

		Command command = new Command(name, CodeActionHandler.COMMAND_ID_APPLY_EDIT, Collections.singletonList(workspaceEdit));
		if (preferenceManager.getClientPreferences().isSupportedCodeActionKind(kind)) {
			CodeAction codeAction = new CodeAction(name);
			codeAction.setKind(kind);
			codeAction.setCommand(command);
			codeAction.setDiagnostics(context.getDiagnostics());
			return Optional.of(Either.forRight(codeAction));
		} else {
			return Optional.of(Either.forLeft(command));
		}
	}

	public static WorkspaceEdit convertToWorkspaceEdit(ICompilationUnit cu, TextEdit edit) {
		if (cu == null || edit == null) {
			return null;
		}

		WorkspaceEdit workspaceEdit = new WorkspaceEdit();
		TextEditConverter converter = new TextEditConverter(cu, edit);
		String uri = JDTUtils.toURI(cu);
		workspaceEdit.getChanges().put(uri, converter.convert());
		return workspaceEdit;
	}

	public static IType getSelectionType(IInvocationContext context) {
		ICompilationUnit unit = context.getCompilationUnit();
		ASTNode node = context.getCoveredNode();
		if (node == null) {
			node = context.getCoveringNode();
		}

		ITypeBinding typeBinding = null;
		while (node != null && !(node instanceof CompilationUnit)) {
			if (node instanceof TypeDeclaration) {
				typeBinding = ((TypeDeclaration) node).resolveBinding();
				break;
			} else if (node instanceof AnonymousClassDeclaration) { // Anonymous
				typeBinding = ((AnonymousClassDeclaration) node).resolveBinding();
				break;
			}

			node = node.getParent();
		}

		if (typeBinding != null && typeBinding.getJavaElement() instanceof IType) {
			return (IType) typeBinding.getJavaElement();
		}

		return unit.findPrimaryType();
	}

	public static IType getSelectionType(CodeActionParams params) {
		InnovationContext context = getInnovationContext(params);
		if (context == null) {
			return null;
		}

		return getSelectionType(context);
	}

	public static InnovationContext getInnovationContext(CodeActionParams params) {
		final ICompilationUnit unit = JDTUtils.resolveCompilationUnit(params.getTextDocument().getUri());
		if (unit == null) {
			return null;
		}

		int start = DiagnosticsHelper.getStartOffset(unit, params.getRange());
		int end = DiagnosticsHelper.getEndOffset(unit, params.getRange());
		InnovationContext context = new InnovationContext(unit, start, end - start);
		CompilationUnit astRoot = CoreASTProvider.getInstance().getAST(unit, CoreASTProvider.WAIT_YES, new NullProgressMonitor());
		context.setASTRoot(astRoot);
		return context;
	}
}
