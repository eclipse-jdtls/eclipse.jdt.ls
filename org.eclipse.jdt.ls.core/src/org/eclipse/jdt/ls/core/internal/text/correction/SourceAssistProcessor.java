/*******************************************************************************
* Copyright (c) 2017-2022 Microsoft Corporation and others.
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

package org.eclipse.jdt.ls.core.internal.text.correction;

import java.lang.reflect.Modifier;
import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.core.runtime.OperationCanceledException;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IField;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.ITypeRoot;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.compiler.IProblem;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.AbstractTypeDeclaration;
import org.eclipse.jdt.core.dom.AnonymousClassDeclaration;
import org.eclipse.jdt.core.dom.BodyDeclaration;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.FieldDeclaration;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.ImportDeclaration;
import org.eclipse.jdt.core.dom.Statement;
import org.eclipse.jdt.core.dom.TypeDeclaration;
import org.eclipse.jdt.core.dom.VariableDeclaration;
import org.eclipse.jdt.core.dom.VariableDeclarationStatement;
import org.eclipse.jdt.core.manipulation.CoreASTProvider;
import org.eclipse.jdt.core.util.CompilationUnitSorter;
import org.eclipse.jdt.internal.corext.dom.ASTNodes;
import org.eclipse.jdt.internal.corext.fix.IProposableFix;
import org.eclipse.jdt.internal.corext.fix.VariableDeclarationFixCore;
import org.eclipse.jdt.internal.corext.util.JdtFlags;
import org.eclipse.jdt.internal.ui.text.correction.IProblemLocationCore;
import org.eclipse.jdt.ls.core.internal.ChangeUtil;
import org.eclipse.jdt.ls.core.internal.JDTUtils;
import org.eclipse.jdt.ls.core.internal.JavaCodeActionKind;
import org.eclipse.jdt.ls.core.internal.JavaLanguageServerPlugin;
import org.eclipse.jdt.ls.core.internal.Messages;
import org.eclipse.jdt.ls.core.internal.TextEditConverter;
import org.eclipse.jdt.ls.core.internal.codemanipulation.DefaultJavaElementComparator;
import org.eclipse.jdt.ls.core.internal.codemanipulation.GenerateGetterSetterOperation;
import org.eclipse.jdt.ls.core.internal.codemanipulation.GenerateGetterSetterOperation.AccessorField;
import org.eclipse.jdt.ls.core.internal.codemanipulation.GenerateGetterSetterOperation.AccessorKind;
import org.eclipse.jdt.ls.core.internal.codemanipulation.PartialSortMembersOperation;
import org.eclipse.jdt.ls.core.internal.corrections.CorrectionMessages;
import org.eclipse.jdt.ls.core.internal.corrections.DiagnosticsHelper;
import org.eclipse.jdt.ls.core.internal.corrections.IInvocationContext;
import org.eclipse.jdt.ls.core.internal.corrections.InnovationContext;
import org.eclipse.jdt.ls.core.internal.corrections.proposals.FixCorrectionProposal;
import org.eclipse.jdt.ls.core.internal.corrections.proposals.IProposalRelevance;
import org.eclipse.jdt.ls.core.internal.handlers.CodeActionHandler;
import org.eclipse.jdt.ls.core.internal.handlers.CodeActionHandler.CodeActionData;
import org.eclipse.jdt.ls.core.internal.handlers.CodeActionProposal;
import org.eclipse.jdt.ls.core.internal.handlers.CodeGenerationUtils;
import org.eclipse.jdt.ls.core.internal.handlers.GenerateAccessorsHandler.AccessorCodeActionParams;
import org.eclipse.jdt.ls.core.internal.handlers.GenerateConstructorsHandler;
import org.eclipse.jdt.ls.core.internal.handlers.GenerateConstructorsHandler.CheckConstructorsResponse;
import org.eclipse.jdt.ls.core.internal.handlers.GenerateDelegateMethodsHandler;
import org.eclipse.jdt.ls.core.internal.handlers.GenerateToStringHandler;
import org.eclipse.jdt.ls.core.internal.handlers.HashCodeEqualsHandler;
import org.eclipse.jdt.ls.core.internal.handlers.JdtDomModels.LspVariableBinding;
import org.eclipse.jdt.ls.core.internal.handlers.OrganizeImportsHandler;
import org.eclipse.jdt.ls.core.internal.preferences.PreferenceManager;
import org.eclipse.lsp4j.CodeAction;
import org.eclipse.lsp4j.CodeActionContext;
import org.eclipse.lsp4j.CodeActionKind;
import org.eclipse.lsp4j.CodeActionParams;
import org.eclipse.lsp4j.Command;
import org.eclipse.lsp4j.WorkspaceEdit;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.eclipse.ltk.core.refactoring.CategorizedTextEditGroup;
import org.eclipse.ltk.core.refactoring.GroupCategory;
import org.eclipse.ltk.core.refactoring.GroupCategorySet;
import org.eclipse.text.edits.TextEdit;

import com.google.common.collect.Sets;

public class SourceAssistProcessor {

	private static final Set<String> UNSUPPORTED_RESOURCES = Sets.newHashSet("module-info.java", "package-info.java");

	public static final String COMMAND_ID_ACTION_OVERRIDEMETHODSPROMPT = "java.action.overrideMethodsPrompt";
	public static final String COMMAND_ID_ACTION_HASHCODEEQUALSPROMPT = "java.action.hashCodeEqualsPrompt";
	public static final String COMMAND_ID_ACTION_ORGANIZEIMPORTS = "java.action.organizeImports";
	public static final String COMMAND_ID_ACTION_GENERATETOSTRINGPROMPT = "java.action.generateToStringPrompt";
	public static final String COMMAND_ID_ACTION_GENERATEACCESSORSPROMPT = "java.action.generateAccessorsPrompt";
	public static final String COMMAND_ID_ACTION_GENERATECONSTRUCTORSPROMPT = "java.action.generateConstructorsPrompt";
	public static final String COMMAND_ID_ACTION_GENERATEDELEGATEMETHODSPROMPT = "java.action.generateDelegateMethodsPrompt";

	private PreferenceManager preferenceManager;

	public SourceAssistProcessor(PreferenceManager preferenceManager) {
		this.preferenceManager = preferenceManager;
	}

	public List<Either<Command, CodeAction>> getSourceActionCommands(CodeActionParams params, IInvocationContext context, IProblemLocationCore[] locations, IProgressMonitor monitor) {
		List<Either<Command, CodeAction>> $ = new ArrayList<>();
		ICompilationUnit cu = context.getCompilationUnit();
		IType type = getSelectionType(context);
		ArrayList<ASTNode> coveredNodes = QuickAssistProcessor.getFullyCoveredNodes(context, context.getCoveringNode());
		ASTNode coveringNode = context.getCoveringNode();
		boolean isInFieldDeclaration = CodeActionUtility.findASTNode(coveredNodes, coveringNode, FieldDeclaration.class) != null;
		ASTNode typeDeclaration = CodeActionUtility.findASTNode(coveredNodes, coveringNode, TypeDeclaration.class);
		boolean isInTypeDeclaration =  typeDeclaration != null;
		boolean isInImportDeclaration =  CodeActionUtility.findASTNode(coveredNodes, coveringNode, ImportDeclaration.class) != null;

		// Generate Constructor QuickAssist
		if (isInFieldDeclaration || isInTypeDeclaration) {
			Optional<Either<Command, CodeAction>> quickAssistGenerateConstructors = getGenerateConstructorsAction(params, context, type, JavaCodeActionKind.QUICK_ASSIST, monitor);
			addSourceActionCommand($, params.getContext(), quickAssistGenerateConstructors);
		}
		// Generate Constructor Source Action
		Optional<Either<Command, CodeAction>> sourceGenerateConstructors = getGenerateConstructorsAction(params, context, type, JavaCodeActionKind.SOURCE_GENERATE_CONSTRUCTORS, monitor);
		addSourceActionCommand($, params.getContext(), sourceGenerateConstructors);

		// Organize Imports
		CodeActionProposal organizeImportsProposal = (pm) -> {
			TextEdit edit = getOrganizeImportsTextEdit(context, false, preferenceManager.getClientPreferences().isAdvancedOrganizeImportsSupported(), pm);
			return convertToWorkspaceEdit(cu, edit);
		};
		// Generate QuickAssist
		if (isInImportDeclaration) {
			Optional<Either<Command, CodeAction>> sourceOrganizeImports = getCodeActionFromProposal(params.getContext(), context.getCompilationUnit(), CorrectionMessages.ReorgCorrectionsSubProcessor_organizeimports_description,
				JavaCodeActionKind.QUICK_ASSIST, organizeImportsProposal, CodeActionComparator.ORGANIZE_IMPORTS_PRIORITY);
			addSourceActionCommand($, params.getContext(), sourceOrganizeImports);
		}
		// Generate Source Action
		Optional<Either<Command, CodeAction>> sourceOrganizeImports = getCodeActionFromProposal(params.getContext(), context.getCompilationUnit(), CorrectionMessages.ReorgCorrectionsSubProcessor_organizeimports_description,
				CodeActionKind.SourceOrganizeImports, organizeImportsProposal, CodeActionComparator.ORGANIZE_IMPORTS_PRIORITY);
		addSourceActionCommand($, params.getContext(), sourceOrganizeImports);

		// Add All missing imports if there is any undefined type error
		boolean hasUndefinedTypeError = false;
		for (IProblem problem : context.getASTRoot().getProblems()) {
			if (problem.getID() == IProblem.UndefinedType || problem.getID() == IProblem.JavadocUndefinedType) {
				hasUndefinedTypeError = true;
				break;
			}
		}
		if (hasUndefinedTypeError) {
			CodeActionProposal allAllMissingImportsProposal = (pm) -> {
				TextEdit edit = getOrganizeImportsTextEdit(context, true, preferenceManager.getClientPreferences().isAdvancedOrganizeImportsSupported(), pm);
				return convertToWorkspaceEdit(cu, edit);
			};
			Optional<Either<Command, CodeAction>> sourceAddAllMissingImports = getCodeActionFromProposal(params.getContext(), context.getCompilationUnit(), CorrectionMessages.UnresolvedElementsSubProcessor_add_allMissing_imports_description,
				CodeActionKind.Source, allAllMissingImportsProposal, CodeActionComparator.ADD_ALL_MISSING_IMPORTS_PRIORITY);
			addSourceActionCommand($, params.getContext(), sourceAddAllMissingImports);
		}

		if (!UNSUPPORTED_RESOURCES.contains(cu.getResource().getName())) {
			// Override/Implement Methods QuickAssist
			if (isInTypeDeclaration) {
				Optional<Either<Command, CodeAction>> quickAssistOverrideMethods = getOverrideMethodsAction(params, JavaCodeActionKind.QUICK_ASSIST);
				addSourceActionCommand($, params.getContext(), quickAssistOverrideMethods);
			}
			// Override/Implement Methods Source Action
			Optional<Either<Command, CodeAction>> sourceOverrideMethods = getOverrideMethodsAction(params, JavaCodeActionKind.SOURCE_OVERRIDE_METHODS);
			addSourceActionCommand($, params.getContext(), sourceOverrideMethods);
		}

		List<String> fieldNames = CodeActionUtility.getFieldNames(coveredNodes, coveringNode);
		try {
			addGenerateAccessorsSourceActionCommand(params, context, $, type, fieldNames, isInTypeDeclaration);
		} catch (JavaModelException e) {
			JavaLanguageServerPlugin.logException("Failed to generate Getter and Setter source action", e);
		}

		boolean hashCodeAndEqualsExists = CodeActionUtility.hasMethod(type, HashCodeEqualsHandler.METHODNAME_HASH_CODE) && CodeActionUtility.hasMethod(type, HashCodeEqualsHandler.METHODNAME_EQUALS, Object.class);
		// Generate hashCode() and equals()
		if (supportsHashCodeEquals(context, type, monitor)) {
			// Generate QuickAssist
			if (isInTypeDeclaration && !hashCodeAndEqualsExists) {
				Optional<Either<Command, CodeAction>> quickAssistHashCodeEquals = getHashCodeEqualsAction(params, JavaCodeActionKind.QUICK_ASSIST);
				addSourceActionCommand($, params.getContext(), quickAssistHashCodeEquals);
			}

			// Generate Source Action
			Optional<Either<Command, CodeAction>> sourceActionHashCodeEquals = getHashCodeEqualsAction(params, JavaCodeActionKind.SOURCE_GENERATE_HASHCODE_EQUALS);
			addSourceActionCommand($, params.getContext(), sourceActionHashCodeEquals);

		}

		boolean toStringExists = CodeActionUtility.hasMethod(type, GenerateToStringHandler.METHODNAME_TOSTRING);
		// Generate toString()
		if (supportsGenerateToString(type)) {
			boolean nonStaticFields = true;
			try {
				nonStaticFields = hasFields(type, false);
			} catch (JavaModelException e) {
				// do nothing.
			}
			if (nonStaticFields) {
				// Generate QuickAssist
				if (isInTypeDeclaration && !toStringExists) {
					Optional<Either<Command, CodeAction>> generateToStringQuickAssist = getGenerateToStringAction(params, JavaCodeActionKind.QUICK_ASSIST);
					addSourceActionCommand($, params.getContext(), generateToStringQuickAssist);
				}
				// Generate Source Action
				Optional<Either<Command, CodeAction>> generateToStringCommand = getGenerateToStringAction(params, JavaCodeActionKind.SOURCE_GENERATE_TO_STRING);
				addSourceActionCommand($, params.getContext(), generateToStringCommand);
			} else {
				CodeActionProposal generateToStringProposal = (pm) -> {
					IJavaElement insertPosition = isInTypeDeclaration ? CodeGenerationUtils.findInsertElement(type, null) : CodeGenerationUtils.findInsertElement(type, context.getSelectionOffset());
					TextEdit edit = GenerateToStringHandler.generateToString(type, new LspVariableBinding[0], insertPosition, pm);
					return convertToWorkspaceEdit(cu, edit);
				};
				// Generate QuickAssist
				if (isInTypeDeclaration && !toStringExists) {
					Optional<Either<Command, CodeAction>> generateToStringQuickAssist = getCodeActionFromProposal(params.getContext(), context.getCompilationUnit(), ActionMessages.GenerateToStringAction_label,
							JavaCodeActionKind.QUICK_ASSIST, generateToStringProposal, CodeActionComparator.GENERATE_TOSTRING_PRIORITY);
					addSourceActionCommand($, params.getContext(), generateToStringQuickAssist);
				}
				// Generate Source Action
				Optional<Either<Command, CodeAction>> generateToStringCommand = getCodeActionFromProposal(params.getContext(), context.getCompilationUnit(), ActionMessages.GenerateToStringAction_label,
						JavaCodeActionKind.SOURCE_GENERATE_TO_STRING, generateToStringProposal, CodeActionComparator.GENERATE_TOSTRING_PRIORITY);
				addSourceActionCommand($, params.getContext(), generateToStringCommand);
			}
		}

		// Generate Delegate Methods
		Optional<Either<Command, CodeAction>> generateDelegateMethods = getGenerateDelegateMethodsAction(params, context, type);
		addSourceActionCommand($, params.getContext(), generateDelegateMethods);

		// Add final modifiers where possible
		Optional<Either<Command, CodeAction>> generateFinalModifiers = addFinalModifierWherePossibleAction(context);
		addSourceActionCommand($, params.getContext(), generateFinalModifiers);

		Optional<Either<Command, CodeAction>> generateFinalModifiersQuickAssist = addFinalModifierWherePossibleQuickAssist(context);
		addSourceActionCommand($, params.getContext(), generateFinalModifiersQuickAssist);

		Optional<Either<Command, CodeAction>> sortMembersAction = getSortMembersAction(context, params, JavaCodeActionKind.SOURCE_SORT_MEMBERS, preferenceManager.getPreferences().getAvoidVolatileChanges());
		addSourceActionCommand($, params.getContext(), sortMembersAction);

		if (isInTypeDeclaration && ((TypeDeclaration) typeDeclaration).isPackageMemberTypeDeclaration()) {
			Optional<Either<Command, CodeAction>> sortMembersQuickAssistForType = getSortMembersAction(context, params, JavaCodeActionKind.QUICK_ASSIST, preferenceManager.getPreferences().getAvoidVolatileChanges());
			addSourceActionCommand($, params.getContext(), sortMembersQuickAssistForType);
		}

		if (coveredNodes.size() > 0) {
			Optional<Either<Command, CodeAction>> sortMembersQuickAssistForSelection = getSortMembersForSelectionProposal(context, params, coveredNodes, preferenceManager.getPreferences().getAvoidVolatileChanges());
			addSourceActionCommand($, params.getContext(), sortMembersQuickAssistForSelection);
		}

		return $;
	}

	private void addGenerateAccessorsSourceActionCommand(CodeActionParams params, IInvocationContext context, List<Either<Command, CodeAction>> $, IType type, List<String> fieldNames, boolean isInTypeDeclaration) throws JavaModelException {
		AccessorField[] accessors = GenerateGetterSetterOperation.getUnimplementedAccessors(type, AccessorKind.BOTH);
		AccessorField[] getters = GenerateGetterSetterOperation.getUnimplementedAccessors(type, AccessorKind.GETTER);
		AccessorField[] setters = GenerateGetterSetterOperation.getUnimplementedAccessors(type, AccessorKind.SETTER);

		if (fieldNames.size() > 0) {
			List<AccessorField> accessorFields = Arrays.stream(accessors).filter(accessor -> fieldNames.contains(accessor.fieldName) && accessor.generateGetter && accessor.generateSetter).collect(Collectors.toList());
			if (accessorFields.size() > 0) {
				addSourceActionCommand($, params.getContext(), getGetterSetterAction(params, context, type, JavaCodeActionKind.QUICK_ASSIST, isInTypeDeclaration, accessorFields.toArray(new AccessorField[0]), AccessorKind.BOTH));
			}
			List<AccessorField> getterFields = Arrays.stream(getters).filter(getter -> fieldNames.contains(getter.fieldName)).collect(Collectors.toList());
			if (getterFields.size() > 0) {
				addSourceActionCommand($, params.getContext(), getGetterSetterAction(params, context, type, JavaCodeActionKind.QUICK_ASSIST, isInTypeDeclaration, getterFields.toArray(new AccessorField[0]), AccessorKind.GETTER));
			}
			List<AccessorField> setterFields = Arrays.stream(setters).filter(setter -> fieldNames.contains(setter.fieldName)).collect(Collectors.toList());
			if (setterFields.size() > 0) {
				addSourceActionCommand($, params.getContext(), getGetterSetterAction(params, context, type, JavaCodeActionKind.QUICK_ASSIST, isInTypeDeclaration, setterFields.toArray(new AccessorField[0]), AccessorKind.SETTER));
			}
		}

		if (getters.length > 0 && setters.length > 0) {
			// Generate Getter and Setter QuickAssist
			if (isInTypeDeclaration) {
				Optional<Either<Command, CodeAction>> quickAssistGetterSetter = getGetterSetterAction(params, context, type, JavaCodeActionKind.QUICK_ASSIST, isInTypeDeclaration, accessors, AccessorKind.BOTH);
				addSourceActionCommand($, params.getContext(), quickAssistGetterSetter);
			}
			// Generate Getter and Setter Source Action
			Optional<Either<Command, CodeAction>> sourceGetterSetter = getGetterSetterAction(params, context, type, JavaCodeActionKind.SOURCE_GENERATE_ACCESSORS, isInTypeDeclaration, accessors, AccessorKind.BOTH);
			addSourceActionCommand($, params.getContext(), sourceGetterSetter);
		}

		if (getters.length > 0) {
			// Generate Getter QuickAssist
			if (isInTypeDeclaration) {
				Optional<Either<Command, CodeAction>> quickAssistGetter = getGetterSetterAction(params, context, type, JavaCodeActionKind.QUICK_ASSIST, isInTypeDeclaration, getters, AccessorKind.GETTER);
				addSourceActionCommand($, params.getContext(), quickAssistGetter);
			}
			// Generate Getter Source Action
			Optional<Either<Command, CodeAction>> sourceGetter = getGetterSetterAction(params, context, type, JavaCodeActionKind.SOURCE_GENERATE_ACCESSORS, isInTypeDeclaration, getters, AccessorKind.GETTER);
			addSourceActionCommand($, params.getContext(), sourceGetter);
		}

		if (setters.length > 0) {
			// Generate Setter QuickAssist
			if (isInTypeDeclaration) {
				Optional<Either<Command, CodeAction>> quickAssistSetter = getGetterSetterAction(params, context, type, JavaCodeActionKind.QUICK_ASSIST, isInTypeDeclaration, setters, AccessorKind.SETTER);
				addSourceActionCommand($, params.getContext(), quickAssistSetter);
			}
			// Generate Setter Source Action
			Optional<Either<Command, CodeAction>> sourceSetter = getGetterSetterAction(params, context, type, JavaCodeActionKind.SOURCE_GENERATE_ACCESSORS, isInTypeDeclaration, setters, AccessorKind.SETTER);
			addSourceActionCommand($, params.getContext(), sourceSetter);
		}
	}

	private void addSourceActionCommand(List<Either<Command, CodeAction>> result, CodeActionContext context, Optional<Either<Command, CodeAction>> target) {
		if (!target.isPresent()) {
			return;
		}

		Either<Command, CodeAction> targetAction = target.get();
		if (context.getOnly() != null && !context.getOnly().isEmpty()) {
			Stream<String> acceptedActionKinds = context.getOnly().stream();
			String actionKind = targetAction.getLeft() == null ? targetAction.getRight().getKind() : targetAction.getLeft().getCommand();
			if (!acceptedActionKinds.filter(kind -> actionKind != null && actionKind.startsWith(kind)).findFirst().isPresent()) {
				return;
			}
		}

		result.add(targetAction);
	}

	private TextEdit getOrganizeImportsTextEdit(IInvocationContext context, boolean restoreExistingImports, boolean isAdvancedOrganizeImportsSupported, IProgressMonitor monitor) {
		ICompilationUnit cu = context.getCompilationUnit();
		if (cu == null) {
			return null;
		}
		IResource resource = cu.getResource();
		if (resource == null) {
			return null;
		}
		URI uri = resource.getLocationURI();
		if (uri == null) {
			return null;
		}
		return OrganizeImportsHandler.organizeImports(context.getCompilationUnit(), isAdvancedOrganizeImportsSupported ? OrganizeImportsHandler.getChooseImportsFunction(uri.toString(), restoreExistingImports) : null, restoreExistingImports, monitor);
	}

	private Optional<Either<Command, CodeAction>> getOverrideMethodsAction(CodeActionParams params, String kind) {
		if (!preferenceManager.getClientPreferences().isOverrideMethodsPromptSupported()) {
			return Optional.empty();
		}

		Command command = new Command(ActionMessages.OverrideMethodsAction_label, COMMAND_ID_ACTION_OVERRIDEMETHODSPROMPT, Collections.singletonList(params));
		if (preferenceManager.getClientPreferences().isSupportedCodeActionKind(JavaCodeActionKind.SOURCE_OVERRIDE_METHODS)) {
			CodeAction codeAction = new CodeAction(ActionMessages.OverrideMethodsAction_label);
			codeAction.setKind(kind);
			codeAction.setCommand(command);
			codeAction.setData(new CodeActionData(null, CodeActionComparator.GENERATE_OVERRIDE_IMPLEMENT_PRIORITY));
			codeAction.setDiagnostics(Collections.emptyList());
			return Optional.of(Either.forRight(codeAction));
		} else {
			return Optional.of(Either.forLeft(command));
		}
	}

	private Optional<Either<Command, CodeAction>> getGetterSetterAction(CodeActionParams params, IInvocationContext context, IType type, String kind, boolean isInTypeDeclaration, AccessorField[] accessors, AccessorKind accessorKind) {
		boolean isQuickAssist = kind.equals(JavaCodeActionKind.QUICK_ASSIST);
		try {
			if (accessors == null || accessors.length == 0) {
				return Optional.empty();
			} else if (isQuickAssist || accessors.length == 1 || !preferenceManager.getClientPreferences().isAdvancedGenerateAccessorsSupported()) {
				String actionMessage;
				switch (accessorKind) {
					case BOTH:
						actionMessage = isQuickAssist && accessors.length == 1 ? Messages.format(ActionMessages.GenerateGetterSetterAction_templateLabel, accessors[0].fieldName) : ActionMessages.GenerateGetterSetterAction_label;
						break;
					case GETTER:
						actionMessage = isQuickAssist && accessors.length == 1 ? Messages.format(ActionMessages.GenerateGetterAction_templateLabel, accessors[0].fieldName) : ActionMessages.GenerateGetterAction_label;
						break;
					case SETTER:
						actionMessage = isQuickAssist && accessors.length == 1 ? Messages.format(ActionMessages.GenerateSetterAction_templateLabel, accessors[0].fieldName) : ActionMessages.GenerateSetterAction_label;
						break;
					default:
						return Optional.empty();
				}
				CodeActionProposal getAccessorsProposal = (pm) -> {
					// If cursor position is not specified, then insert to the last by default.
					IJavaElement insertBefore = isInTypeDeclaration ? CodeGenerationUtils.findInsertElement(type, null) : CodeGenerationUtils.findInsertElement(type, params.getRange());
					GenerateGetterSetterOperation operation = new GenerateGetterSetterOperation(type, context.getASTRoot(), preferenceManager.getPreferences().isCodeGenerationTemplateGenerateComments(), insertBefore);
					TextEdit edit = operation.createTextEdit(pm, accessors);
					return convertToWorkspaceEdit(context.getCompilationUnit(), edit);
				};
				return getCodeActionFromProposal(params.getContext(), context.getCompilationUnit(), actionMessage, kind, getAccessorsProposal, CodeActionComparator.GENERATE_ACCESSORS_PRIORITY);
			} else {
				String actionMessage;
				switch (accessorKind) {
					case BOTH:
						actionMessage = ActionMessages.GenerateGetterSetterAction_ellipsisLabel;
						break;
					case GETTER:
						actionMessage = ActionMessages.GenerateGetterAction_ellipsisLabel;
						break;
					case SETTER:
						actionMessage = ActionMessages.GenerateSetterAction_ellipsisLabel;
						break;
					default:
						return Optional.empty();
				}
				AccessorCodeActionParams accessorParams = new AccessorCodeActionParams(params.getTextDocument(), params.getRange(), params.getContext(), accessorKind);
				Command command = new Command(actionMessage, COMMAND_ID_ACTION_GENERATEACCESSORSPROMPT, Collections.singletonList(accessorParams));
				if (preferenceManager.getClientPreferences().isSupportedCodeActionKind(JavaCodeActionKind.SOURCE_GENERATE_ACCESSORS)) {
					CodeAction codeAction = new CodeAction(actionMessage);
					codeAction.setKind(kind);
					codeAction.setCommand(command);
					codeAction.setData(new CodeActionData(null, CodeActionComparator.GENERATE_ACCESSORS_PRIORITY));
					codeAction.setDiagnostics(Collections.emptyList());
					return Optional.of(Either.forRight(codeAction));
				} else {
					return Optional.of(Either.forLeft(command));
				}
			}
		} catch (OperationCanceledException e) {
			JavaLanguageServerPlugin.logException("Failed to generate accessors source action", e);
			return Optional.empty();
		}
	}

	private boolean supportsHashCodeEquals(IInvocationContext context, IType type, IProgressMonitor monitor) {
		try {
			if (type == null || type.isAnnotation() || type.isInterface() || type.isEnum() || type.getCompilationUnit() == null) {
				return false;
			}

			CompilationUnit astRoot = context.getASTRoot();
			if (astRoot == null) {
				return false;
			}

			ITypeBinding typeBinding = ASTNodes.getTypeBinding(astRoot, type);
			return (typeBinding == null) ? false : Arrays.stream(typeBinding.getDeclaredFields()).filter(f -> !Modifier.isStatic(f.getModifiers())).findAny().isPresent();
		} catch (JavaModelException e) {
			return false;
		}
	}

	private Optional<Either<Command, CodeAction>> getHashCodeEqualsAction(CodeActionParams params, String kind) {
		if (!preferenceManager.getClientPreferences().isHashCodeEqualsPromptSupported()) {
			return Optional.empty();
		}
		Command command = new Command(ActionMessages.GenerateHashCodeEqualsAction_label, COMMAND_ID_ACTION_HASHCODEEQUALSPROMPT, Collections.singletonList(params));
		if (preferenceManager.getClientPreferences().isSupportedCodeActionKind(JavaCodeActionKind.SOURCE_GENERATE_HASHCODE_EQUALS)) {
			CodeAction codeAction = new CodeAction(ActionMessages.GenerateHashCodeEqualsAction_label);
			codeAction.setKind(kind);
			codeAction.setCommand(command);
			codeAction.setData(new CodeActionData(null, CodeActionComparator.GENERATE_HASHCODE_EQUALS_PRIORITY));
			codeAction.setDiagnostics(Collections.emptyList());
			return Optional.of(Either.forRight(codeAction));
		} else {
			return Optional.of(Either.forLeft(command));
		}
	}

	private boolean supportsGenerateToString(IType type) {
		try {
			if (type == null || type.isAnnotation() || type.isInterface() || type.isEnum() || type.isAnonymous() || type.getCompilationUnit() == null) {
				return false;
			}
		} catch (JavaModelException e) {
			// do nothing.
		}

		return true;
	}

	private Optional<Either<Command, CodeAction>> getGenerateToStringAction(CodeActionParams params, String kind) {
		if (!preferenceManager.getClientPreferences().isGenerateToStringPromptSupported()) {
			return Optional.empty();
		}
		Command command = new Command(ActionMessages.GenerateToStringAction_ellipsisLabel, COMMAND_ID_ACTION_GENERATETOSTRINGPROMPT, Collections.singletonList(params));
		if (preferenceManager.getClientPreferences().isSupportedCodeActionKind(JavaCodeActionKind.SOURCE_GENERATE_TO_STRING)) {
			CodeAction codeAction = new CodeAction(ActionMessages.GenerateToStringAction_ellipsisLabel);
			codeAction.setKind(kind);
			codeAction.setCommand(command);
			codeAction.setData(new CodeActionData(null, CodeActionComparator.GENERATE_TOSTRING_PRIORITY));
			codeAction.setDiagnostics(Collections.emptyList());
			return Optional.of(Either.forRight(codeAction));
		} else {
			return Optional.of(Either.forLeft(command));
		}
	}

	private Optional<Either<Command, CodeAction>> getGenerateConstructorsAction(CodeActionParams params, IInvocationContext context, IType type, String kind, IProgressMonitor monitor) {
		try {
			if (type == null || type.isAnnotation() || type.isInterface() || type.isAnonymous() || type.getCompilationUnit() == null) {
				return Optional.empty();
			}

			boolean hasNonStaticField = hasFields(type, false);
			if (!hasNonStaticField) {
				return Optional.empty();
			}

		} catch (JavaModelException e) {
			return Optional.empty();
		}

		if (preferenceManager.getClientPreferences().isGenerateConstructorsPromptSupported()) {
			CheckConstructorsResponse status = GenerateConstructorsHandler.checkConstructorStatus(type, params.getRange(), monitor);
			if (status.constructors.length == 0) {
				return Optional.empty();
			}
			if (status.constructors.length == 1 && status.fields.length == 0) {
				CodeActionProposal generateConstructorsProposal = (pm) -> {
					TextEdit edit = GenerateConstructorsHandler.generateConstructors(type, status.constructors, status.fields, params.getRange(), pm);
					return convertToWorkspaceEdit(type.getCompilationUnit(), edit);
				};
				return getCodeActionFromProposal(params.getContext(), type.getCompilationUnit(), ActionMessages.GenerateConstructorsAction_label, kind, generateConstructorsProposal, CodeActionComparator.GENERATE_CONSTRUCTORS_PRIORITY);
			}

			Command command = new Command(ActionMessages.GenerateConstructorsAction_ellipsisLabel, COMMAND_ID_ACTION_GENERATECONSTRUCTORSPROMPT, Collections.singletonList(params));
			if (preferenceManager.getClientPreferences().isSupportedCodeActionKind(JavaCodeActionKind.SOURCE_GENERATE_CONSTRUCTORS)) {
				CodeAction codeAction = new CodeAction(ActionMessages.GenerateConstructorsAction_ellipsisLabel);
				codeAction.setKind(kind);
				codeAction.setCommand(command);
				codeAction.setData(new CodeActionData(null, CodeActionComparator.GENERATE_CONSTRUCTORS_PRIORITY));
				codeAction.setDiagnostics(Collections.emptyList());
				return Optional.of(Either.forRight(codeAction));
			} else {
				return Optional.of(Either.forLeft(command));
			}
		}

		return Optional.empty();
	}

	private Optional<Either<Command, CodeAction>> getGenerateDelegateMethodsAction(CodeActionParams params, IInvocationContext context, IType type) {
		try {
			if (!preferenceManager.getClientPreferences().isGenerateDelegateMethodsPromptSupported() || !GenerateDelegateMethodsHandler.supportsGenerateDelegateMethods(type)) {
				return Optional.empty();
			}
		} catch (JavaModelException e) {
			return Optional.empty();
		}

		Command command = new Command(ActionMessages.GenerateDelegateMethodsAction_label, COMMAND_ID_ACTION_GENERATEDELEGATEMETHODSPROMPT, Collections.singletonList(params));
		if (preferenceManager.getClientPreferences().isSupportedCodeActionKind(JavaCodeActionKind.SOURCE_GENERATE_DELEGATE_METHODS)) {
			CodeAction codeAction = new CodeAction(ActionMessages.GenerateDelegateMethodsAction_label);
			codeAction.setKind(JavaCodeActionKind.SOURCE_GENERATE_DELEGATE_METHODS);
			codeAction.setCommand(command);
			codeAction.setData(new CodeActionData(null, CodeActionComparator.GENERATE_DELEGATE_METHOD_PRIORITY));
			codeAction.setDiagnostics(Collections.EMPTY_LIST);
			return Optional.of(Either.forRight(codeAction));
		} else {
			return Optional.of(Either.forLeft(command));
		}
	}

	private Optional<Either<Command, CodeAction>> addFinalModifierWherePossibleAction(IInvocationContext context) {
		IProposableFix fix = (IProposableFix) VariableDeclarationFixCore.createCleanUp(context.getASTRoot(), true, true, true);
		return getFinalModifierWherePossibleAction(context, fix, ActionMessages.GenerateFinalModifiersAction_label, JavaCodeActionKind.SOURCE_GENERATE_FINAL_MODIFIERS);
	}

	private Optional<Either<Command, CodeAction>> addFinalModifierWherePossibleQuickAssist(IInvocationContext context) {
		ASTNode coveringNode = context.getCoveringNode();
		List<ASTNode> coveredNodes = QuickAssistProcessor.getFullyCoveredNodes(context, coveringNode);
		List<ASTNode> possibleASTNodes = getPossibleASTNodesForFinalModifier(coveredNodes);
		if (possibleASTNodes.size() == 0) {
			possibleASTNodes = getPossibleASTNodesForFinalModifier(Arrays.asList(coveringNode));
		}
		Set<String> names = new HashSet<>();
		for (ASTNode node : possibleASTNodes) {
			names.addAll(CodeActionUtility.getVariableNamesFromASTNode(node));
		}
		String actionMessage = ActionMessages.GenerateFinalModifiersAction_selectionLabel;
		if (names.size() == 1) {
			actionMessage = Messages.format(ActionMessages.GenerateFinalModifiersAction_templateLabel, names.iterator().next());
		}
		IProposableFix fix = VariableDeclarationFixCore.createChangeModifierToFinalFix(context.getASTRoot(), possibleASTNodes.toArray(new ASTNode[0]));
		return getFinalModifierWherePossibleAction(context, fix, actionMessage, JavaCodeActionKind.QUICK_ASSIST);
	}

	private List<ASTNode> getPossibleASTNodesForFinalModifier(List<ASTNode> targetNodes) {
		List<ASTNode> results = new ArrayList<>();
		for (ASTNode targetNode : targetNodes) {
			ASTNode variableDeclaration = CodeActionUtility.inferASTNode(targetNode, VariableDeclaration.class);
			ASTNode fieldDeclaration = CodeActionUtility.inferASTNode(targetNode, FieldDeclaration.class);
			ASTNode variableDeclarationStatement = CodeActionUtility.inferASTNode(targetNode, VariableDeclarationStatement.class);
			if (variableDeclaration != null) {
				results.add(variableDeclaration);
			} else if (fieldDeclaration != null) {
				results.addAll(((FieldDeclaration) fieldDeclaration).fragments());
			} else if (variableDeclarationStatement != null) {
				results.add(variableDeclarationStatement);
			}
		}
		return results;
	}

	private Optional<Either<Command, CodeAction>> getFinalModifierWherePossibleAction(IInvocationContext context, IProposableFix fix, String actionMessage, String kind) {
		if (fix == null) {
			return Optional.empty();
		}
		FixCorrectionProposal proposal = new FixCorrectionProposal(fix, null, IProposalRelevance.MAKE_VARIABLE_DECLARATION_FINAL, context, kind);
		if (this.preferenceManager.getClientPreferences().isResolveCodeActionSupported()) {
			CodeAction codeAction = new CodeAction(actionMessage);
			codeAction.setKind(proposal.getKind());
			codeAction.setData(new CodeActionData(proposal, CodeActionComparator.CHANGE_MODIFIER_TO_FINAL_PRIORITY));
			codeAction.setDiagnostics(Collections.EMPTY_LIST);
			return Optional.of(Either.forRight(codeAction));
		} else {
			WorkspaceEdit edit;
			try {
				edit = ChangeUtil.convertToWorkspaceEdit(proposal.getChange());
			} catch (CoreException e) {
				JavaLanguageServerPlugin.logException("Problem converting proposal to code actions", e);
				return Optional.empty();
			}

			if (!ChangeUtil.hasChanges(edit)) {
				return Optional.empty();
			}
			Command command = new Command(actionMessage, CodeActionHandler.COMMAND_ID_APPLY_EDIT, Collections.singletonList(edit));
			if (preferenceManager.getClientPreferences().isSupportedCodeActionKind(proposal.getKind())) {
				CodeAction codeAction = new CodeAction(actionMessage);
				codeAction.setKind(proposal.getKind());
				codeAction.setCommand(command);
				codeAction.setData(new CodeActionData(null, CodeActionComparator.CHANGE_MODIFIER_TO_FINAL_PRIORITY));
				codeAction.setDiagnostics(Collections.EMPTY_LIST);
				return Optional.of(Either.forRight(codeAction));
			} else {
				return Optional.of(Either.forLeft(command));
			}
		}
	}

	private Optional<Either<Command, CodeAction>> getSortMembersProposal(IInvocationContext context, CodeActionParams params, String kind, String label, boolean avoidVolatileChanges) {
		CategorizedTextEditGroup group = new CategorizedTextEditGroup(label, new GroupCategorySet(new GroupCategory(label, label, label)));
		try {
			TextEdit edit = CompilationUnitSorter.sort(context.getASTRoot(), new DefaultJavaElementComparator(avoidVolatileChanges), 0, group, null);
			if (edit == null) {
				return Optional.empty();
			}
			CodeActionProposal sortMembersProposal = (pm) -> {
				return convertToWorkspaceEdit(context.getCompilationUnit(), edit);
			};
			return getCodeActionFromProposal(params.getContext(), context.getCompilationUnit(), label, kind, sortMembersProposal, CodeActionComparator.SORT_MEMBERS_PRIORITY);
		} catch (JavaModelException e) {
			return Optional.empty();
		}
	}

	private Optional<Either<Command, CodeAction>> getSortMembersForSelectionProposal(IInvocationContext context, CodeActionParams params, List<ASTNode> coveredNodes, boolean avoidVolatileChanges) {
		CategorizedTextEditGroup group = new CategorizedTextEditGroup(ActionMessages.SortMembers_selectionLabel, new GroupCategorySet(new GroupCategory(ActionMessages.SortMembers_selectionLabel, ActionMessages.SortMembers_selectionLabel, ActionMessages.SortMembers_selectionLabel)));
		PartialSortMembersOperation operation = new PartialSortMembersOperation(new IJavaElement[] { context.getASTRoot().getJavaElement() }, new DefaultJavaElementComparator(avoidVolatileChanges));
		try {
			TextEdit edit = operation.calculateEdit(context.getASTRoot(), coveredNodes, group);
			if (edit == null) {
				return Optional.empty();
			}
			CodeActionProposal sortMembersProposal = (pm) -> {
				return convertToWorkspaceEdit(context.getCompilationUnit(), edit);
			};
			return getCodeActionFromProposal(params.getContext(), context.getCompilationUnit(), ActionMessages.SortMembers_selectionLabel, JavaCodeActionKind.QUICK_ASSIST, sortMembersProposal, CodeActionComparator.SORT_MEMBERS_PRIORITY);
		} catch (JavaModelException e) {
			return Optional.empty();
		}
	}

	private Optional<Either<Command, CodeAction>> getSortMembersAction(IInvocationContext context, CodeActionParams params, String kind, boolean avoidVolatileChanges) {
		CompilationUnit unit = context.getASTRoot();
		if (unit != null) {
			ITypeRoot typeRoot = unit.getTypeRoot();
			if (typeRoot != null) {
				return getSortMembersProposal(context, params, kind, Messages.format(ActionMessages.SortMembers_templateLabel, typeRoot.getElementName()), avoidVolatileChanges);
			}
		}
		return Optional.empty();
	}

	private Optional<Either<Command, CodeAction>> getCodeActionFromProposal(CodeActionContext context, ICompilationUnit cu, String name, String kind, CodeActionProposal proposal, int priority) {
		if (preferenceManager.getClientPreferences().isResolveCodeActionSupported()) {
			CodeAction codeAction = new CodeAction(name);
			codeAction.setKind(kind);
			codeAction.setData(new CodeActionData(proposal, priority));
			codeAction.setDiagnostics(Collections.EMPTY_LIST);
			return Optional.of(Either.forRight(codeAction));
		}

		try {
			WorkspaceEdit edit =  proposal.resolveEdit(new NullProgressMonitor());
			if (!ChangeUtil.hasChanges(edit)) {
				return Optional.empty();
			}

			Command command = new Command(name, CodeActionHandler.COMMAND_ID_APPLY_EDIT, Collections.singletonList(edit));
			if (preferenceManager.getClientPreferences().isSupportedCodeActionKind(kind)) {
				CodeAction codeAction = new CodeAction(name);
				codeAction.setKind(kind);
				codeAction.setCommand(command);
				codeAction.setData(new CodeActionData(null, priority));
				codeAction.setDiagnostics(context.getDiagnostics());
				return Optional.of(Either.forRight(codeAction));
			} else {
				return Optional.of(Either.forLeft(command));
			}
		} catch (OperationCanceledException | CoreException e) {
			JavaLanguageServerPlugin.logException("Problem converting proposal to code actions", e);
		}

		return null;
	}

	private boolean hasFields(IType type, boolean includeStatic) throws JavaModelException {
		for (IField field : type.getFields()) {
			if (includeStatic || !JdtFlags.isStatic(field)) {
				return true;
			}
		}

		return false;
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
			if (node instanceof AbstractTypeDeclaration typeDecl) {
				typeBinding = typeDecl.resolveBinding();
				break;
			} else if (node instanceof AnonymousClassDeclaration anonymousClassDecl) { // Anonymous
				typeBinding = anonymousClassDecl.resolveBinding();
				break;
			}

			node = node.getParent();
		}

		if (typeBinding != null && typeBinding.getJavaElement() instanceof IType type) {
			return type;
		}

		return unit.findPrimaryType();
	}

	public static IType getSelectionType(CodeActionParams params) {
		return getSelectionType(params, new NullProgressMonitor());
	}

	public static IType getSelectionType(CodeActionParams params, IProgressMonitor monitor) {
		InnovationContext context = getInnovationContext(params, monitor);
		return (context == null) ? null : getSelectionType(context);
	}

	public static InnovationContext getInnovationContext(CodeActionParams params, IProgressMonitor monitor) {
		final ICompilationUnit unit = JDTUtils.resolveCompilationUnit(params.getTextDocument().getUri());
		if (unit == null) {
			return null;
		}
		int start = DiagnosticsHelper.getStartOffset(unit, params.getRange());
		int end = DiagnosticsHelper.getEndOffset(unit, params.getRange());
		InnovationContext context = new InnovationContext(unit, start, end - start);
		CompilationUnit astRoot = CoreASTProvider.getInstance().getAST(unit, CoreASTProvider.WAIT_YES, monitor);
		if (astRoot == null) {
			return null;
		}

		context.setASTRoot(astRoot);
		return context;
	}

	public static ICompilationUnit getCompilationUnit(CodeActionParams params) {
		return JDTUtils.resolveCompilationUnit(params.getTextDocument().getUri());
	}

	public static ASTNode getTypeDeclarationNode(ASTNode node) {
		if (node == null) {
			return null;
		}
		if (node instanceof BodyDeclaration) {
			return null;
		}
		while (node != null && !(node instanceof BodyDeclaration) && !(node instanceof Statement)) {
			node = node.getParent();
		}
		return node instanceof TypeDeclaration ? node : null;
	}
}
