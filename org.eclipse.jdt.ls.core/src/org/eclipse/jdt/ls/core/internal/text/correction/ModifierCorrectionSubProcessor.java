/*******************************************************************************
 * Copyright (c) 2000, 2016 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Originally copied from org.eclipse.jdt.internal.ui.text.correction.ModifierCorrectionSubProcessor
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *     Benjamin Muskalla <bmuskalla@innoopract.com> - [quick fix] 'Remove invalid modifiers' does not appear for enums and annotations - https://bugs.eclipse.org/bugs/show_bug.cgi?id=110589
 *     Benjamin Muskalla <b.muskalla@gmx.net> - [quick fix] Quick fix for missing synchronized modifier - https://bugs.eclipse.org/bugs/show_bug.cgi?id=245250
 *     Rabea Gransberger <rgransberger@gmx.de> - [quick fix] Fix several visibility issues - https://bugs.eclipse.org/394692
 *******************************************************************************/
package org.eclipse.jdt.ls.core.internal.text.correction;

import java.util.Collection;
import java.util.List;

import org.eclipse.core.runtime.Assert;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.compiler.IProblem;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.Block;
import org.eclipse.jdt.core.dom.ClassInstanceCreation;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.FieldAccess;
import org.eclipse.jdt.core.dom.IBinding;
import org.eclipse.jdt.core.dom.IExtendedModifier;
import org.eclipse.jdt.core.dom.IMethodBinding;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.IVariableBinding;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.Modifier;
import org.eclipse.jdt.core.dom.Modifier.ModifierKeyword;
import org.eclipse.jdt.core.dom.NameQualifiedType;
import org.eclipse.jdt.core.dom.QualifiedName;
import org.eclipse.jdt.core.dom.ReturnStatement;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.core.dom.SimpleType;
import org.eclipse.jdt.core.dom.SuperConstructorInvocation;
import org.eclipse.jdt.core.dom.SuperFieldAccess;
import org.eclipse.jdt.core.dom.SuperMethodInvocation;
import org.eclipse.jdt.core.dom.Type;
import org.eclipse.jdt.core.dom.TypeDeclaration;
import org.eclipse.jdt.core.dom.rewrite.ASTRewrite;
import org.eclipse.jdt.core.formatter.IndentManipulation;
import org.eclipse.jdt.internal.core.manipulation.dom.ASTResolving;
import org.eclipse.jdt.internal.core.manipulation.util.BasicElementLabels;
import org.eclipse.jdt.internal.corext.dom.ASTNodeFactory;
import org.eclipse.jdt.internal.corext.dom.ASTNodes;
import org.eclipse.jdt.internal.corext.dom.Bindings;
import org.eclipse.jdt.internal.corext.dom.ModifierRewrite;
import org.eclipse.jdt.internal.corext.fix.CompilationUnitRewriteOperationsFixCore.CompilationUnitRewriteOperation;
import org.eclipse.jdt.internal.corext.fix.LinkedProposalModelCore;
import org.eclipse.jdt.internal.corext.fix.LinkedProposalPositionGroupCore;
import org.eclipse.jdt.internal.corext.fix.UnimplementedCodeFixCore;
import org.eclipse.jdt.internal.corext.fix.UnimplementedCodeFixCore.MakeTypeAbstractOperation;
import org.eclipse.jdt.internal.corext.util.JdtFlags;
import org.eclipse.jdt.internal.ui.text.correction.IProposalRelevance;
import org.eclipse.jdt.internal.ui.text.correction.proposals.FixCorrectionProposalCore;
import org.eclipse.jdt.internal.ui.text.correction.proposals.ModifierChangeCorrectionProposalCore;
import org.eclipse.jdt.ls.core.internal.IConstants;
import org.eclipse.jdt.ls.core.internal.Messages;
import org.eclipse.jdt.ls.core.internal.corrections.CorrectionMessages;
import org.eclipse.jdt.ls.core.internal.corrections.ProposalKindWrapper;
import org.eclipse.jdt.ls.core.internal.corrections.proposals.UnresolvedElementsSubProcessor;
import org.eclipse.jdt.ls.core.internal.handlers.CodeActionHandler;
import org.eclipse.jdt.ui.text.java.IInvocationContext;
import org.eclipse.jdt.ui.text.java.IProblemLocation;
import org.eclipse.jdt.ui.text.java.correction.ASTRewriteCorrectionProposalCore;
import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.link.LinkedModeModel;
import org.eclipse.jface.text.link.LinkedPosition;
import org.eclipse.lsp4j.CodeActionKind;
import org.eclipse.text.edits.MultiTextEdit;
import org.eclipse.text.edits.ReplaceEdit;
import org.eclipse.text.edits.TextEdit;

/**
  */
public class ModifierCorrectionSubProcessor {

	public static final int TO_STATIC = 1;
	public static final int TO_VISIBLE = 2;
	public static final int TO_NON_PRIVATE = 3;
	public static final int TO_NON_STATIC = 4;
	public static final int TO_NON_FINAL = 5;

	public static void addNonAccessibleReferenceProposal(IInvocationContext context, IProblemLocation problem, Collection<ProposalKindWrapper> proposals, int kind, int relevance) throws CoreException {
		ICompilationUnit cu = context.getCompilationUnit();

		ASTNode selectedNode = problem.getCoveringNode(context.getASTRoot());
		if (selectedNode == null) {
			return;
		}

		IBinding binding = null;
		switch (selectedNode.getNodeType()) {
			case ASTNode.SIMPLE_NAME:
				binding = ((SimpleName) selectedNode).resolveBinding();
				break;
			case ASTNode.QUALIFIED_NAME:
				binding = ((QualifiedName) selectedNode).resolveBinding();
				break;
			case ASTNode.SIMPLE_TYPE:
				binding = ((SimpleType) selectedNode).resolveBinding();
				break;
			case ASTNode.NAME_QUALIFIED_TYPE:
				binding = ((NameQualifiedType) selectedNode).resolveBinding();
				break;
			case ASTNode.METHOD_INVOCATION:
				binding = ((MethodInvocation) selectedNode).getName().resolveBinding();
				break;
			case ASTNode.SUPER_METHOD_INVOCATION:
				binding = ((SuperMethodInvocation) selectedNode).getName().resolveBinding();
				break;
			case ASTNode.FIELD_ACCESS:
				binding = ((FieldAccess) selectedNode).getName().resolveBinding();
				break;
			case ASTNode.SUPER_FIELD_ACCESS:
				binding = ((SuperFieldAccess) selectedNode).getName().resolveBinding();
				break;
			case ASTNode.CLASS_INSTANCE_CREATION:
				binding = ((ClassInstanceCreation) selectedNode).resolveConstructorBinding();
				break;
			case ASTNode.SUPER_CONSTRUCTOR_INVOCATION:
				binding = ((SuperConstructorInvocation) selectedNode).resolveConstructorBinding();
				break;
			default:
				return;
		}
		ITypeBinding typeBinding = null;
		String name;
		IBinding bindingDecl;
		boolean isLocalVar = false;
		if (binding instanceof IVariableBinding variableBinding && problem.getProblemId() == IProblem.NotVisibleType) {
			binding = variableBinding.getType();
		}
		if (binding instanceof IMethodBinding methodBinding && problem.getProblemId() == IProblem.NotVisibleType) {
			binding = methodBinding.getReturnType();
		}
		if (binding instanceof IMethodBinding methodDecl) {
			if (methodDecl.isDefaultConstructor()) {
				UnresolvedElementsSubProcessor.getConstructorProposals(context, problem, proposals);
				return;
			}
			bindingDecl = methodDecl.getMethodDeclaration();
			typeBinding = methodDecl.getDeclaringClass();
			name = BasicElementLabels.getJavaElementName(methodDecl.getName() + "()"); //$NON-NLS-1$
		} else if (binding instanceof IVariableBinding varDecl) {
			typeBinding = varDecl.getDeclaringClass();
			name = BasicElementLabels.getJavaElementName(binding.getName());
			isLocalVar = !varDecl.isField();
			bindingDecl = varDecl.getVariableDeclaration();
		} else if (binding instanceof ITypeBinding localTypeBinding) {
			typeBinding = localTypeBinding;
			bindingDecl = typeBinding.getTypeDeclaration();
			name = BasicElementLabels.getJavaElementName(binding.getName());
		} else {
			return;
		}
		if (typeBinding != null && typeBinding.isFromSource() || isLocalVar) {
			int includedModifiers = 0;
			int excludedModifiers = 0;
			String label;
			switch (kind) {
				case TO_VISIBLE:
					excludedModifiers = Modifier.PRIVATE | Modifier.PROTECTED | Modifier.PUBLIC;
					includedModifiers = getNeededVisibility(selectedNode, typeBinding, binding);
					label = Messages.format(CorrectionMessages.ModifierCorrectionSubProcessor_changevisibility_description, new String[] { name, getVisibilityString(includedModifiers) });
					break;
				case TO_STATIC:
					label = Messages.format(CorrectionMessages.ModifierCorrectionSubProcessor_changemodifiertostatic_description, name);
					includedModifiers = Modifier.STATIC;
					if (bindingDecl.getKind() == IBinding.METHOD) {
						excludedModifiers = Modifier.DEFAULT | Modifier.ABSTRACT;
					}
					break;
				case TO_NON_STATIC:
					if (typeBinding != null && typeBinding.isInterface()) {
						return;
					}
					label = Messages.format(CorrectionMessages.ModifierCorrectionSubProcessor_changemodifiertononstatic_description, name);
					excludedModifiers = Modifier.STATIC;
					break;
				case TO_NON_PRIVATE:
					int visibility;
					if (cu.getParent().getElementName().equals(typeBinding.getPackage().getName())) {
						visibility = Modifier.NONE;
						excludedModifiers = Modifier.PRIVATE;
					} else {
						visibility = Modifier.PUBLIC;
						includedModifiers = Modifier.PUBLIC;
						excludedModifiers = Modifier.PRIVATE | Modifier.PROTECTED | Modifier.PUBLIC;
					}
					label = Messages.format(CorrectionMessages.ModifierCorrectionSubProcessor_changevisibility_description, new String[] { name, getVisibilityString(visibility) });
					break;
				case TO_NON_FINAL:
					if (typeBinding != null && typeBinding.isInterface()) {
						return;
					}
					label = Messages.format(CorrectionMessages.ModifierCorrectionSubProcessor_changemodifiertononfinal_description, name);
					excludedModifiers = Modifier.FINAL;
					break;
				default:
					throw new IllegalArgumentException("not supported"); //$NON-NLS-1$
			}
			ICompilationUnit targetCU = isLocalVar ? cu : ASTResolving.findCompilationUnitForBinding(cu, context.getASTRoot(), typeBinding.getTypeDeclaration());
			if (targetCU != null) {
				ModifierChangeCorrectionProposalCore p = new ModifierChangeCorrectionProposalCore(label, targetCU, bindingDecl, selectedNode, includedModifiers, excludedModifiers, relevance);
				proposals.add(CodeActionHandler.wrap(p, CodeActionKind.QuickFix));
			}
		}
		if (kind == TO_VISIBLE && bindingDecl.getKind() == IBinding.VARIABLE) {
			UnresolvedElementsSubProcessor.getVariableProposals(context, problem, (IVariableBinding) bindingDecl, proposals);
		}
	}

	public static void addChangeOverriddenModifierProposal(IInvocationContext context, IProblemLocation problem, Collection<ProposalKindWrapper> proposals, int kind) throws JavaModelException {
		ICompilationUnit cu = context.getCompilationUnit();

		ASTNode selectedNode = problem.getCoveringNode(context.getASTRoot());
		if (!(selectedNode instanceof MethodDeclaration)) {
			return;
		}

		IMethodBinding method = ((MethodDeclaration) selectedNode).resolveBinding();
		ITypeBinding curr = method.getDeclaringClass();

		if (kind == TO_VISIBLE && problem.getProblemId() != IProblem.OverridingNonVisibleMethod) {
			// e.g. IProblem.InheritedMethodReducesVisibility, IProblem.MethodReducesVisibility
			List<IMethodBinding> methods = Bindings.findOverriddenMethods(method, false, false);
			if (!methods.isEmpty()) {
				int includedModifiers = 0;
				for (IMethodBinding binding : methods) {
					int temp = JdtFlags.getVisibilityCode(binding);
					includedModifiers = JdtFlags.getHigherVisibility(temp, includedModifiers);
				}
				int excludedModifiers = Modifier.PRIVATE | Modifier.PROTECTED | Modifier.PUBLIC;
				String label = Messages.format(CorrectionMessages.ModifierCorrectionSubProcessor_changemethodvisibility_description, new String[] { getVisibilityString(includedModifiers) });
				ModifierChangeCorrectionProposalCore p = new ModifierChangeCorrectionProposalCore(label, cu, method, selectedNode, includedModifiers, excludedModifiers, IProposalRelevance.CHANGE_OVERRIDDEN_MODIFIER_1);
				proposals.add(CodeActionHandler.wrap(p, CodeActionKind.QuickFix));
			}
		}

		IMethodBinding overriddenInClass = null;
		while (overriddenInClass == null && curr.getSuperclass() != null) {
			curr = curr.getSuperclass();
			overriddenInClass = Bindings.findOverriddenMethodInType(curr, method);
		}
		if (overriddenInClass != null) {
			final IMethodBinding overriddenDecl = overriddenInClass.getMethodDeclaration();
			final ICompilationUnit overriddenMethodCU = ASTResolving.findCompilationUnitForBinding(cu, context.getASTRoot(), overriddenDecl.getDeclaringClass());

			if (overriddenMethodCU != null) {
				//target method and compilation unit for the quick fix
				IMethodBinding targetMethod = overriddenDecl;
				ICompilationUnit targetCU = overriddenMethodCU;

				String label;
				int excludedModifiers;
				int includedModifiers;
				switch (kind) {
					case TO_VISIBLE:
						if (JdtFlags.isPrivate(method)) {
							// Propose to increase the visibility of this method, because decreasing to private is not possible.
							targetMethod = method;
							targetCU = cu;

							excludedModifiers = Modifier.PRIVATE | Modifier.PROTECTED | Modifier.PUBLIC;
							includedModifiers = JdtFlags.getVisibilityCode(overriddenDecl);
						} else if (JdtFlags.isPackageVisible(method) && !overriddenDecl.getDeclaringClass().getPackage().isEqualTo(method.getDeclaringClass().getPackage())) {
							// method is package visible but not in the same package as overridden method
							// propose to make the method protected

							excludedModifiers = Modifier.PRIVATE;
							includedModifiers = Modifier.PROTECTED;

							// if it is already protected, ignore it
							if (JdtFlags.isProtected(overriddenDecl)) {
								return;
							}
						} else {
							excludedModifiers = Modifier.PRIVATE | Modifier.PROTECTED | Modifier.PUBLIC;
							includedModifiers = JdtFlags.getVisibilityCode(method);

							if (JdtFlags.getVisibilityCode(overriddenDecl) == JdtFlags.getVisibilityCode(method)) {
								// don't propose the same visibility it already has
								return;
							}
						}

						label = Messages.format(CorrectionMessages.ModifierCorrectionSubProcessor_changeoverriddenvisibility_description, new String[] { getMethodLabel(targetMethod), getVisibilityString(includedModifiers) });
						break;
					case TO_NON_FINAL:
						label = Messages.format(CorrectionMessages.ModifierCorrectionSubProcessor_changemethodtononfinal_description, getMethodLabel(targetMethod));
						excludedModifiers = Modifier.FINAL;
						includedModifiers = 0;
						break;
					case TO_NON_STATIC:
						label = Messages.format(CorrectionMessages.ModifierCorrectionSubProcessor_changemethodtononstatic_description, getMethodLabel(targetMethod));
						excludedModifiers = Modifier.STATIC;
						includedModifiers = 0;
						break;
					default:
						Assert.isTrue(false, "not supported"); //$NON-NLS-1$
						return;
				}
				ModifierChangeCorrectionProposalCore p = new ModifierChangeCorrectionProposalCore(label, targetCU, targetMethod, selectedNode, includedModifiers, excludedModifiers, IProposalRelevance.CHANGE_OVERRIDDEN_MODIFIER_2);
				proposals.add(CodeActionHandler.wrap(p, CodeActionKind.QuickFix));
			}
		}
	}
	//
	//	public static void addNonFinalLocalProposal(IInvocationContext context, IProblemLocation problem, Collection<ICommandAccess> proposals) {
	//		ICompilationUnit cu = context.getCompilationUnit();
	//
	//		ASTNode selectedNode = problem.getCoveringNode(context.getASTRoot());
	//		if (!(selectedNode instanceof SimpleName)) {
	//			return;
	//		}
	//
	//		IBinding binding = ((SimpleName) selectedNode).resolveBinding();
	//		if (binding instanceof IVariableBinding) {
	//			binding = ((IVariableBinding) binding).getVariableDeclaration();
	//			Image image = JavaPluginImages.get(JavaPluginImages.IMG_CORRECTION_CHANGE);
	//			String label = Messages.format(CorrectionMessages.ModifierCorrectionSubProcessor_changemodifiertofinal_description, BasicElementLabels.getJavaElementName(binding.getName()));
	//			proposals.add(new ModifierChangeCorrectionProposal(label, cu, binding, selectedNode, Modifier.FINAL, 0, IProposalRelevance.CHANGE_MODIFIER_TO_FINAL, image));
	//		}
	//	}
	//
	public static void addRemoveInvalidModifiersProposal(IInvocationContext context, IProblemLocation problem, Collection<ProposalKindWrapper> proposals, int relevance) {
		ICompilationUnit cu = context.getCompilationUnit();

		ASTNode selectedNode = problem.getCoveringNode(context.getASTRoot());
		if (selectedNode instanceof MethodDeclaration methodDeclaration) {
			selectedNode = methodDeclaration.getName();
		}

		if (!(selectedNode instanceof SimpleName)) {
			return;
		}

		IBinding binding = ((SimpleName) selectedNode).resolveBinding();
		if (binding != null) {
			String methodName = BasicElementLabels.getJavaElementName(binding.getName());
			String label = null;
			int problemId = problem.getProblemId();

			int excludedModifiers = 0;
			int includedModifiers = 0;

			switch (problemId) {
				case IProblem.CannotHideAnInstanceMethodWithAStaticMethod:
				case IProblem.UnexpectedStaticModifierForMethod:
					excludedModifiers = Modifier.STATIC;
					label = Messages.format(CorrectionMessages.ModifierCorrectionSubProcessor_changemethodtononstatic_description, methodName);
					break;
				case IProblem.UnexpectedStaticModifierForField:
					excludedModifiers = Modifier.STATIC;
					label = Messages.format(CorrectionMessages.ModifierCorrectionSubProcessor_changefieldmodifiertononstatic_description, methodName);
					break;
				case IProblem.IllegalModifierCombinationFinalVolatileForField:
					excludedModifiers = Modifier.VOLATILE;
					label = CorrectionMessages.ModifierCorrectionSubProcessor_removevolatile_description;
					break;
				case IProblem.IllegalModifierForInterfaceMethod:
					excludedModifiers = ~(Modifier.PUBLIC | Modifier.ABSTRACT);
					break;
				case IProblem.IllegalModifierForInterfaceMethod18:
					excludedModifiers = ~(Modifier.PUBLIC | Modifier.ABSTRACT | Modifier.STRICTFP | Modifier.DEFAULT | Modifier.STATIC);
					if (Modifier.isAbstract(binding.getModifiers())) {
						excludedModifiers = excludedModifiers | Modifier.STRICTFP;
					}
					break;
				case IProblem.IllegalModifierForInterface:
					excludedModifiers = ~(Modifier.PUBLIC | Modifier.ABSTRACT | Modifier.STRICTFP);
					break;
				case IProblem.IllegalModifierForClass:
					excludedModifiers = ~(Modifier.PUBLIC | Modifier.ABSTRACT | Modifier.FINAL | Modifier.STRICTFP);
					break;
				case IProblem.IllegalModifierForInterfaceField:
					excludedModifiers = ~(Modifier.PUBLIC | Modifier.STATIC | Modifier.FINAL);
					break;
				case IProblem.IllegalModifierForMemberInterface:
				case IProblem.IllegalVisibilityModifierForInterfaceMemberType:
					excludedModifiers = ~(Modifier.PUBLIC | Modifier.STATIC | Modifier.STRICTFP);
					break;
				case IProblem.IllegalModifierForMemberClass:
					excludedModifiers = ~(Modifier.PUBLIC | Modifier.PROTECTED | Modifier.PRIVATE | Modifier.STATIC | Modifier.ABSTRACT | Modifier.FINAL | Modifier.STRICTFP);
					break;
				case IProblem.IllegalModifierForLocalClass:
					excludedModifiers = ~(Modifier.ABSTRACT | Modifier.FINAL | Modifier.STRICTFP);
					break;
				case IProblem.IllegalModifierForArgument:
					excludedModifiers = ~Modifier.FINAL;
					break;
				case IProblem.IllegalModifierForField:
					excludedModifiers = ~(Modifier.PUBLIC | Modifier.PROTECTED | Modifier.PRIVATE | Modifier.STATIC | Modifier.FINAL | Modifier.VOLATILE | Modifier.TRANSIENT);
					break;
				case IProblem.IllegalModifierForMethod:
					excludedModifiers = ~(Modifier.PUBLIC | Modifier.PROTECTED | Modifier.PRIVATE | Modifier.STATIC | Modifier.ABSTRACT | Modifier.FINAL | Modifier.NATIVE | Modifier.STRICTFP | Modifier.SYNCHRONIZED);
					break;
				case IProblem.IllegalModifierForConstructor:
					excludedModifiers = ~(Modifier.PUBLIC | Modifier.PROTECTED | Modifier.PRIVATE);
					break;
				case IProblem.IllegalModifierForVariable:
					excludedModifiers = ~Modifier.FINAL;
					break;
				case IProblem.IllegalModifierForEnum:
					excludedModifiers = ~(Modifier.PUBLIC | Modifier.STRICTFP);
					break;
				case IProblem.IllegalModifierForEnumConstant:
					excludedModifiers = ~Modifier.NONE;
					break;
				case IProblem.IllegalModifierForEnumConstructor:
					excludedModifiers = ~Modifier.PRIVATE;
					break;
				case IProblem.IllegalModifierForMemberEnum:
					excludedModifiers = ~(Modifier.PUBLIC | Modifier.PRIVATE | Modifier.PROTECTED | Modifier.STATIC | Modifier.STRICTFP);
					break;
				default:
					Assert.isTrue(false, "not supported"); //$NON-NLS-1$
					return;
			}

			if (label == null) {
				label = Messages.format(CorrectionMessages.ModifierCorrectionSubProcessor_removeinvalidmodifiers_description, methodName);
			}

			ModifierChangeCorrectionProposalCore p1 = new ModifierChangeCorrectionProposalCore(label, cu, binding, selectedNode, includedModifiers, excludedModifiers, relevance);
			proposals.add(CodeActionHandler.wrap(p1, CodeActionKind.QuickFix));

			if (problemId == IProblem.IllegalModifierCombinationFinalVolatileForField) {
				ModifierChangeCorrectionProposalCore p2 = new ModifierChangeCorrectionProposalCore(CorrectionMessages.ModifierCorrectionSubProcessor_removefinal_description, cu, binding, selectedNode, 0, Modifier.FINAL, relevance + 1);
				proposals.add(CodeActionHandler.wrap(p2, CodeActionKind.QuickFix));
			}

			if (problemId == IProblem.UnexpectedStaticModifierForField && binding instanceof IVariableBinding variableBinding) {
				ITypeBinding declClass = variableBinding.getDeclaringClass();
				if (declClass.isMember()) {
					ModifierChangeCorrectionProposalCore p3 = new ModifierChangeCorrectionProposalCore(CorrectionMessages.ModifierCorrectionSubProcessor_changemodifiertostaticfinal_description, cu, binding, selectedNode, Modifier.FINAL,
							Modifier.VOLATILE, relevance + 1);
					proposals.add(CodeActionHandler.wrap(p3, CodeActionKind.QuickFix));
					ASTNode parentType = context.getASTRoot().findDeclaringNode(declClass);
					if (parentType != null) {
						ModifierChangeCorrectionProposalCore p4 = new ModifierChangeCorrectionProposalCore(CorrectionMessages.ModifierCorrectionSubProcessor_addstatictoparenttype_description, cu, declClass, parentType, Modifier.STATIC, 0,
								relevance - 1);
						proposals.add(CodeActionHandler.wrap(p4, CodeActionKind.QuickFix));
					}
				}
			}
			if (problemId == IProblem.UnexpectedStaticModifierForMethod && binding instanceof IMethodBinding methodBinding) {
				ITypeBinding declClass = methodBinding.getDeclaringClass();
				if (declClass.isMember()) {
					ASTNode parentType = context.getASTRoot().findDeclaringNode(declClass);
					if (parentType != null) {
						ModifierChangeCorrectionProposalCore p5 = new ModifierChangeCorrectionProposalCore(CorrectionMessages.ModifierCorrectionSubProcessor_addstatictoparenttype_description, cu, declClass, parentType, Modifier.STATIC, 0,
								relevance - 1);
						proposals.add(CodeActionHandler.wrap(p5, CodeActionKind.QuickFix));
					}
				}
			}
		}
	}

	private static String getMethodLabel(IMethodBinding targetMethod) {
		return BasicElementLabels.getJavaElementName(targetMethod.getDeclaringClass().getName() + '.' + targetMethod.getName());
	}

		private static String getVisibilityString(int code) {
			if (Modifier.isPublic(code)) {
				return "public"; //$NON-NLS-1$
			} else if (Modifier.isProtected(code)) {
				return "protected"; //$NON-NLS-1$
			} else if (Modifier.isPrivate(code)) {
				return "private"; //$NON-NLS-1$
			}
			return CorrectionMessages.ModifierCorrectionSubProcessor_default;
		}

	private static int getNeededVisibility(ASTNode currNode, ITypeBinding targetType, IBinding binding) {
		ITypeBinding currNodeBinding = Bindings.getBindingOfParentType(currNode);
		if (currNodeBinding == null) { // import
			return Modifier.PUBLIC;
		}

		if (Bindings.isSuperType(targetType, currNodeBinding)) {
			if (binding != null && (JdtFlags.isProtected(binding) || binding.getKind() == IBinding.TYPE)) {
				return Modifier.PUBLIC;
			}
			return Modifier.PROTECTED;
		}

		if (currNodeBinding.getPackage().getKey().equals(targetType.getPackage().getKey())) {
			return 0;
		}
		return Modifier.PUBLIC;
	}

	public static void addAbstractMethodProposals(IInvocationContext context, IProblemLocation problem, Collection<ProposalKindWrapper> proposals) {
		ICompilationUnit cu = context.getCompilationUnit();

		CompilationUnit astRoot = context.getASTRoot();

		ASTNode selectedNode = problem.getCoveringNode(astRoot);
		if (selectedNode == null) {
			return;
		}
		MethodDeclaration decl;
		if (selectedNode instanceof SimpleName) {
			decl = (MethodDeclaration) selectedNode.getParent();
		} else if (selectedNode instanceof MethodDeclaration methodDeclaration) {
			decl = methodDeclaration;
		} else {
			return;
		}

		ASTNode parentType = ASTResolving.findParentType(decl);
		TypeDeclaration parentTypeDecl = null;
		boolean parentIsAbstractClass = false;
		boolean parentIsInterface = false;
		if (parentType instanceof TypeDeclaration typeDecl) {
			parentTypeDecl = typeDecl;
			parentIsAbstractClass = !parentTypeDecl.isInterface() && Modifier.isAbstract(parentTypeDecl.getModifiers());
			parentIsInterface = parentTypeDecl.isInterface();
		}
		boolean hasNoBody = decl.getBody() == null;

		int id = problem.getProblemId();
		if (id == IProblem.AbstractMethodInAbstractClass || id == IProblem.EnumAbstractMethodMustBeImplemented || id == IProblem.AbstractMethodInEnum || id == IProblem.BodyForAbstractMethod || parentIsAbstractClass) {
			AST ast = astRoot.getAST();
			ASTRewrite rewrite = ASTRewrite.create(ast);

			removeModifier(decl, rewrite, Modifier.ABSTRACT);

			if (hasNoBody) {
				Block newBody = ast.newBlock();
				rewrite.set(decl, MethodDeclaration.BODY_PROPERTY, newBody, null);

				Type returnType = decl.getReturnType2();
				if (returnType != null) {
					Expression expr = ASTNodeFactory.newDefaultExpression(ast, returnType, decl.getExtraDimensions());
					if (expr != null) {
						ReturnStatement returnStatement = ast.newReturnStatement();
						returnStatement.setExpression(expr);
						newBody.statements().add(returnStatement);
					}
				}
			}

			String label = CorrectionMessages.ModifierCorrectionSubProcessor_removeabstract_description;
			ASTRewriteCorrectionProposalCore proposal = new ASTRewriteCorrectionProposalCore(label, cu, rewrite, IProposalRelevance.REMOVE_ABSTRACT_MODIFIER);
			proposals.add(CodeActionHandler.wrap(proposal, CodeActionKind.QuickFix));
		}

		if (!hasNoBody && id == IProblem.BodyForAbstractMethod) {
			AST ast = decl.getAST();
			{
				ASTRewrite rewrite = ASTRewrite.create(ast);
				rewrite.remove(decl.getBody(), null);

				int excluded;
				if (parentIsInterface) {
					excluded = ~(Modifier.PUBLIC | Modifier.ABSTRACT);
				} else {
					excluded = ~(Modifier.PUBLIC | Modifier.PROTECTED | Modifier.ABSTRACT);
				}
				ModifierRewrite.create(rewrite, decl).setModifiers(0, excluded, null);

				String label = CorrectionMessages.ModifierCorrectionSubProcessor_removebody_description;
				ASTRewriteCorrectionProposalCore proposal = new ASTRewriteCorrectionProposalCore(label, cu, rewrite, IProposalRelevance.REMOVE_METHOD_BODY);
				proposals.add(CodeActionHandler.wrap(proposal, CodeActionKind.QuickFix));
			}

			if (parentIsInterface) {
				{
					// insert proposal to add static modifier
					String label = Messages.format(CorrectionMessages.ModifierCorrectionSubProcessor_changemodifiertostatic_description, decl.getName());
					int included = Modifier.STATIC;
					int excluded = Modifier.ABSTRACT | Modifier.DEFAULT;
					ModifierChangeCorrectionProposalCore proposal = new ModifierChangeCorrectionProposalCore(label, cu, decl.resolveBinding(), decl, included, excluded, IProposalRelevance.ADD_STATIC_MODIFIER);
					proposals.add(CodeActionHandler.wrap(proposal, CodeActionKind.QuickFix));
				}

				{
					// insert proposal to add default modifier
					String label = Messages.format(CorrectionMessages.ModifierCorrectionSubProcessor_changemodifiertodefault_description, decl.getName());
					int included = Modifier.DEFAULT;
					int excluded = Modifier.ABSTRACT | Modifier.STATIC;
					ModifierChangeCorrectionProposalCore proposal = new ModifierChangeCorrectionProposalCore(label, cu, decl.resolveBinding(), decl, included, excluded, IProposalRelevance.ADD_DEFAULT_MODIFIER);
					proposals.add(CodeActionHandler.wrap(proposal, CodeActionKind.QuickFix));
				}
			}
		}

		if (id == IProblem.AbstractMethodInAbstractClass && parentTypeDecl != null) {
			addMakeTypeAbstractProposal(context, parentTypeDecl, proposals);
		}

	}

	private static Modifier removeModifier(final MethodDeclaration decl, final ASTRewrite rewrite, final int modifier) {
		Modifier modifierNode = ASTNodes.findModifierNode(modifier, decl.modifiers());
		if (modifierNode != null) {
			rewrite.remove(modifierNode, null);
		}
		return modifierNode;
	}

	private static void addMakeTypeAbstractProposal(IInvocationContext context, TypeDeclaration parentTypeDecl, Collection<ProposalKindWrapper> proposals) {
		MakeTypeAbstractOperation operation = new UnimplementedCodeFixCore.MakeTypeAbstractOperation(parentTypeDecl);

		String label = Messages.format(CorrectionMessages.ModifierCorrectionSubProcessor_addabstract_description, BasicElementLabels.getJavaElementName(parentTypeDecl.getName().getIdentifier()));
		UnimplementedCodeFixCore fix = new UnimplementedCodeFixCore(label, context.getASTRoot(), new CompilationUnitRewriteOperation[] { operation });

		FixCorrectionProposalCore proposal = new FixCorrectionProposalCore(fix, null, IProposalRelevance.MAKE_TYPE_ABSTRACT_FIX, context);
		proposals.add(CodeActionHandler.wrap(proposal, CodeActionKind.QuickFix));
	}

	public static void addAbstractTypeProposals(IInvocationContext context, IProblemLocation problem, Collection<ProposalKindWrapper> proposals) {
		CompilationUnit astRoot = context.getASTRoot();

		ASTNode selectedNode = problem.getCoveringNode(astRoot);
		if (selectedNode == null) {
			return;
		}

		TypeDeclaration parentTypeDecl = null;
		if (selectedNode instanceof SimpleName) {
			ASTNode parent = selectedNode.getParent();
			if (parent != null) {
				parentTypeDecl = (TypeDeclaration) parent;
			}
		} else if (selectedNode instanceof TypeDeclaration typeDecl) {
			parentTypeDecl = typeDecl;
		}

		if (parentTypeDecl == null) {
			return;
		}

		addMakeTypeAbstractProposal(context, parentTypeDecl, proposals);
	}

	//	public static void addNativeMethodProposals(IInvocationContext context, IProblemLocation problem, Collection<ICommandAccess> proposals) {
	//		ICompilationUnit cu = context.getCompilationUnit();
	//
	//		CompilationUnit astRoot = context.getASTRoot();
	//
	//		ASTNode selectedNode = problem.getCoveringNode(astRoot);
	//		if (selectedNode == null) {
	//			return;
	//		}
	//		MethodDeclaration decl;
	//		if (selectedNode instanceof SimpleName) {
	//			decl = (MethodDeclaration) selectedNode.getParent();
	//		} else if (selectedNode instanceof MethodDeclaration) {
	//			decl = (MethodDeclaration) selectedNode;
	//		} else {
	//			return;
	//		}
	//
	//		{
	//			AST ast = astRoot.getAST();
	//			ASTRewrite rewrite = ASTRewrite.create(ast);
	//
	//			removeModifier(decl, rewrite, Modifier.NATIVE);
	//
	//			Block newBody = ast.newBlock();
	//			rewrite.set(decl, MethodDeclaration.BODY_PROPERTY, newBody, null);
	//
	//			Type returnType = decl.getReturnType2();
	//			if (returnType != null) {
	//				Expression expr = ASTNodeFactory.newDefaultExpression(ast, returnType, decl.getExtraDimensions());
	//				if (expr != null) {
	//					ReturnStatement returnStatement = ast.newReturnStatement();
	//					returnStatement.setExpression(expr);
	//					newBody.statements().add(returnStatement);
	//				}
	//			}
	//
	//			String label = CorrectionMessages.ModifierCorrectionSubProcessor_removenative_description;
	//			Image image = JavaPluginImages.get(JavaPluginImages.IMG_CORRECTION_CHANGE);
	//			ASTRewriteCorrectionProposal proposal = new ASTRewriteCorrectionProposal(label, cu, rewrite, IProposalRelevance.REMOVE_NATIVE, image);
	//			proposals.add(proposal);
	//		}
	//
	//		if (decl.getBody() != null) {
	//			ASTRewrite rewrite = ASTRewrite.create(decl.getAST());
	//			rewrite.remove(decl.getBody(), null);
	//
	//			String label = CorrectionMessages.ModifierCorrectionSubProcessor_removebody_description;
	//			Image image = JavaPluginImages.get(JavaPluginImages.IMG_CORRECTION_CHANGE);
	//			ASTRewriteCorrectionProposal proposal2 = new ASTRewriteCorrectionProposal(label, cu, rewrite, IProposalRelevance.REMOVE_METHOD_BODY, image);
	//			proposals.add(proposal2);
	//		}
	//
	//	}
	//
	//	public static void addMethodRequiresBodyProposals(IInvocationContext context, IProblemLocation problem, Collection<ICommandAccess> proposals) {
	//		ICompilationUnit cu = context.getCompilationUnit();
	//		AST ast = context.getASTRoot().getAST();
	//
	//		ASTNode selectedNode = problem.getCoveringNode(context.getASTRoot());
	//		if (!(selectedNode instanceof MethodDeclaration)) {
	//			return;
	//		}
	//		MethodDeclaration decl = (MethodDeclaration) selectedNode;
	//		Modifier modifierNode;
	//		{
	//			ASTRewrite rewrite = ASTRewrite.create(ast);
	//
	//			modifierNode = removeModifier(decl, rewrite, Modifier.ABSTRACT);
	//
	//			Block body = ast.newBlock();
	//			rewrite.set(decl, MethodDeclaration.BODY_PROPERTY, body, null);
	//
	//			if (!decl.isConstructor()) {
	//				Type returnType = decl.getReturnType2();
	//				if (returnType != null) {
	//					Expression expression = ASTNodeFactory.newDefaultExpression(ast, returnType, decl.getExtraDimensions());
	//					if (expression != null) {
	//						ReturnStatement returnStatement = ast.newReturnStatement();
	//						returnStatement.setExpression(expression);
	//						body.statements().add(returnStatement);
	//					}
	//				}
	//			}
	//
	//			String label = CorrectionMessages.ModifierCorrectionSubProcessor_addmissingbody_description;
	//			Image image = JavaPluginImages.get(JavaPluginImages.IMG_CORRECTION_CHANGE);
	//			ASTRewriteCorrectionProposal proposal = new ASTRewriteCorrectionProposal(label, cu, rewrite, IProposalRelevance.ADD_MISSING_BODY, image);
	//
	//			proposals.add(proposal);
	//		}
	//
	//		IMethodBinding binding = decl.resolveBinding();
	//		if (modifierNode == null && binding != null) {
	//			String label = Messages.format(CorrectionMessages.ModifierCorrectionSubProcessor_changemodifiertoabstract_description, getMethodLabel(binding));
	//			Image image = JavaPluginImages.get(JavaPluginImages.IMG_CORRECTION_CHANGE);
	//			int included = binding.getDeclaringClass().isInterface() ? Modifier.NONE : Modifier.ABSTRACT;
	//			int excluded = Modifier.STATIC | Modifier.DEFAULT;
	//			ModifierChangeCorrectionProposal proposal = new ModifierChangeCorrectionProposal(label, cu, binding, decl, included, excluded, IProposalRelevance.ADD_ABSTRACT_MODIFIER, image);
	//
	//			proposals.add(proposal);
	//		}
	//
	//	}
	//
	//	public static void addNeedToEmulateProposal(IInvocationContext context, IProblemLocation problem, Collection<ModifierChangeCorrectionProposal> proposals) {
	//		ICompilationUnit cu = context.getCompilationUnit();
	//
	//		ASTNode selectedNode = problem.getCoveringNode(context.getASTRoot());
	//		if (!(selectedNode instanceof SimpleName)) {
	//			return;
	//		}
	//
	//		IBinding binding = ((SimpleName) selectedNode).resolveBinding();
	//		if (binding instanceof IVariableBinding) {
	//			binding = ((IVariableBinding) binding).getVariableDeclaration();
	//			Image image = JavaPluginImages.get(JavaPluginImages.IMG_CORRECTION_CHANGE);
	//			String label = Messages.format(CorrectionMessages.ModifierCorrectionSubProcessor_changemodifiertofinal_description, BasicElementLabels.getJavaElementName(binding.getName()));
	//			proposals.add(new ModifierChangeCorrectionProposal(label, cu, binding, selectedNode, Modifier.FINAL, 0, IProposalRelevance.CHANGE_MODIFIER_OF_VARIABLE_TO_FINAL, image));
	//		}
	//	}
	//
	//	public static void addOverrideAnnotationProposal(IInvocationContext context, IProblemLocation problem, Collection<ICommandAccess> proposals) {
	//		IProposableFix fix = Java50Fix.createAddOverrideAnnotationFix(context.getASTRoot(), problem);
	//		if (fix != null) {
	//			Image image = JavaPluginImages.get(JavaPluginImages.IMG_CORRECTION_CHANGE);
	//			Map<String, String> options = new Hashtable<>();
	//			options.put(CleanUpConstants.ADD_MISSING_ANNOTATIONS, CleanUpOptions.TRUE);
	//			options.put(CleanUpConstants.ADD_MISSING_ANNOTATIONS_OVERRIDE, CleanUpOptions.TRUE);
	//			options.put(CleanUpConstants.ADD_MISSING_ANNOTATIONS_OVERRIDE_FOR_INTERFACE_METHOD_IMPLEMENTATION, CleanUpOptions.TRUE);
	//			FixCorrectionProposal proposal = new FixCorrectionProposal(fix, new Java50CleanUp(options), IProposalRelevance.ADD_OVERRIDE_ANNOTATION, image, context);
	//			proposals.add(proposal);
	//		}
	//	}
	//
	//	public static void addDeprecatedAnnotationProposal(IInvocationContext context, IProblemLocation problem, Collection<ICommandAccess> proposals) {
	//		IProposableFix fix = Java50Fix.createAddDeprectatedAnnotation(context.getASTRoot(), problem);
	//		if (fix != null) {
	//			Image image = JavaPluginImages.get(JavaPluginImages.IMG_CORRECTION_CHANGE);
	//			Map<String, String> options = new Hashtable<>();
	//			options.put(CleanUpConstants.ADD_MISSING_ANNOTATIONS, CleanUpOptions.TRUE);
	//			options.put(CleanUpConstants.ADD_MISSING_ANNOTATIONS_DEPRECATED, CleanUpOptions.TRUE);
	//			FixCorrectionProposal proposal = new FixCorrectionProposal(fix, new Java50CleanUp(options), IProposalRelevance.ADD_DEPRECATED_ANNOTATION, image, context);
	//			proposals.add(proposal);
	//		}
	//	}
	//
	//	public static void addOverridingDeprecatedMethodProposal(IInvocationContext context, IProblemLocation problem, Collection<ICommandAccess> proposals) {
	//
	//		ICompilationUnit cu = context.getCompilationUnit();
	//
	//		ASTNode selectedNode = problem.getCoveringNode(context.getASTRoot());
	//		if (!(selectedNode instanceof MethodDeclaration)) {
	//			return;
	//		}
	//		boolean is50OrHigher = JavaModelUtil.is50OrHigher(cu.getJavaProject());
	//		MethodDeclaration methodDecl = (MethodDeclaration) selectedNode;
	//		AST ast = methodDecl.getAST();
	//		ASTRewrite rewrite = ASTRewrite.create(ast);
	//		if (is50OrHigher) {
	//			Annotation annot = ast.newMarkerAnnotation();
	//			annot.setTypeName(ast.newName("Deprecated")); //$NON-NLS-1$
	//			rewrite.getListRewrite(methodDecl, methodDecl.getModifiersProperty()).insertFirst(annot, null);
	//		}
	//		Javadoc javadoc = methodDecl.getJavadoc();
	//		if (javadoc != null || !is50OrHigher) {
	//			if (!is50OrHigher) {
	//				javadoc = ast.newJavadoc();
	//				rewrite.set(methodDecl, MethodDeclaration.JAVADOC_PROPERTY, javadoc, null);
	//			}
	//			TagElement newTag = ast.newTagElement();
	//			newTag.setTagName(TagElement.TAG_DEPRECATED);
	//			JavadocTagsSubProcessor.insertTag(rewrite.getListRewrite(javadoc, Javadoc.TAGS_PROPERTY), newTag, null);
	//		}
	//
	//		String label = CorrectionMessages.ModifierCorrectionSubProcessor_overrides_deprecated_description;
	//		Image image = JavaPluginImages.get(JavaPluginImages.IMG_CORRECTION_CHANGE);
	//		ASTRewriteCorrectionProposal proposal = new ASTRewriteCorrectionProposal(label, cu, rewrite, IProposalRelevance.OVERRIDES_DEPRECATED, image);
	//		proposals.add(proposal);
	//	}
	//
	//	public static void removeOverrideAnnotationProposal(IInvocationContext context, IProblemLocation problem, Collection<ICommandAccess> proposals) throws CoreException {
	//		ICompilationUnit cu = context.getCompilationUnit();
	//
	//		ASTNode selectedNode = problem.getCoveringNode(context.getASTRoot());
	//		if (!(selectedNode instanceof MethodDeclaration)) {
	//			return;
	//		}
	//		MethodDeclaration methodDecl = (MethodDeclaration) selectedNode;
	//		Annotation annot = StubUtility2.findAnnotation("java.lang.Override", methodDecl.modifiers()); //$NON-NLS-1$
	//		if (annot != null) {
	//			ASTRewrite rewrite = ASTRewrite.create(annot.getAST());
	//			rewrite.remove(annot, null);
	//			String label = CorrectionMessages.ModifierCorrectionSubProcessor_remove_override;
	//			Image image = JavaPluginImages.get(JavaPluginImages.IMG_CORRECTION_CHANGE);
	//			ASTRewriteCorrectionProposal proposal = new ASTRewriteCorrectionProposal(label, cu, rewrite, IProposalRelevance.REMOVE_OVERRIDE, image);
	//			proposals.add(proposal);
	//
	//			QuickAssistProcessor.getCreateInSuperClassProposals(context, methodDecl.getName(), proposals);
	//		}
	//	}
	//
	//	public static void addSynchronizedMethodProposal(IInvocationContext context, IProblemLocation problem, Collection<ICommandAccess> proposals) {
	//		addAddMethodModifierProposal(context, problem, proposals, Modifier.SYNCHRONIZED, CorrectionMessages.ModifierCorrectionSubProcessor_addsynchronized_description);
	//	}
	//
	//	public static void addStaticMethodProposal(IInvocationContext context, IProblemLocation problem, Collection<ICommandAccess> proposals) {
	//		addAddMethodModifierProposal(context, problem, proposals, Modifier.STATIC, CorrectionMessages.ModifierCorrectionSubProcessor_addstatic_description);
	//	}
	//
	//	private static void addAddMethodModifierProposal(IInvocationContext context, IProblemLocation problem, Collection<ICommandAccess> proposals, int modifier, String label) {
	//		ICompilationUnit cu = context.getCompilationUnit();
	//
	//		ASTNode selectedNode = problem.getCoveringNode(context.getASTRoot());
	//		if (!(selectedNode instanceof MethodDeclaration)) {
	//			return;
	//		}
	//
	//		IBinding binding = ((MethodDeclaration) selectedNode).resolveBinding();
	//		if (binding instanceof IMethodBinding) {
	//			binding = ((IMethodBinding) binding).getMethodDeclaration();
	//			Image image = JavaPluginImages.get(JavaPluginImages.IMG_CORRECTION_CHANGE);
	//			proposals.add(new ModifierChangeCorrectionProposal(label, cu, binding, selectedNode, modifier, 0, IProposalRelevance.ADD_METHOD_MODIFIER, image));
	//		}
	//	}
	//
	public static final String KEY_MODIFIER = "modifier"; //$NON-NLS-1$

	private static class ModifierLinkedModeProposal extends LinkedProposalPositionGroupCore.ProposalCore {

		private final int fModifier;

		public ModifierLinkedModeProposal(int modifier, int relevance) {
			super(null, relevance);
			fModifier = modifier;
		}

		@Override
		public String getAdditionalProposalInfo() {
			return getDisplayString();
		}

		@Override
		public String getDisplayString() {
			if (fModifier == 0) {
				return CorrectionMessages.ModifierCorrectionSubProcessor_default_visibility_label;
			} else {
				return ModifierKeyword.fromFlagValue(fModifier).toString();
			}
		}

		@Override
		public TextEdit computeEdits(int offset, LinkedPosition currentPosition, char trigger, int stateMask, LinkedModeModel model) throws CoreException {
			try {
				IDocument document = currentPosition.getDocument();
				MultiTextEdit edit = new MultiTextEdit();
				int documentLen = document.getLength();
				if (fModifier == 0) {
					int end = currentPosition.offset + currentPosition.length; // current end position
					int k = end;
					while (k < documentLen && IndentManipulation.isIndentChar(document.getChar(k))) {
						k++;
					}
					// first remove space then replace range (remove space can destroy empty position)
					edit.addChild(new ReplaceEdit(end, k - end, new String())); // remove extra spaces
					edit.addChild(new ReplaceEdit(currentPosition.offset, currentPosition.length, new String()));
				} else {
					// first then replace range the insert space (insert space can destroy empty position)
					edit.addChild(new ReplaceEdit(currentPosition.offset, currentPosition.length, ModifierKeyword.fromFlagValue(fModifier).toString()));
					int end = currentPosition.offset + currentPosition.length; // current end position
					if (end < documentLen && !Character.isWhitespace(document.getChar(end))) {
						edit.addChild(new ReplaceEdit(end, 0, String.valueOf(' '))); // insert extra space
					}
				}
				return edit;
			} catch (BadLocationException e) {
				throw new CoreException(new Status(IStatus.ERROR, IConstants.PLUGIN_ID, IStatus.ERROR, e.getMessage(), e));
			}
		}
	}

	public static void installLinkedVisibilityProposals(LinkedProposalModelCore linkedProposalModel, ASTRewrite rewrite, List<IExtendedModifier> modifiers, boolean inInterface, String groupId) {
		ASTNode modifier = findVisibilityModifier(modifiers);
		if (modifier != null) {
			int selected = ((Modifier) modifier).getKeyword().toFlagValue();

			LinkedProposalPositionGroupCore positionGroup = linkedProposalModel.getPositionGroup(groupId, true);
			positionGroup.addPosition(rewrite.track(modifier), false);
			positionGroup.addProposal(new ModifierLinkedModeProposal(selected, 10));

			// add all others
			int[] flagValues = inInterface ? new int[] { Modifier.PUBLIC, 0 } : new int[] { Modifier.PUBLIC, 0, Modifier.PROTECTED, Modifier.PRIVATE };
			for (int i = 0; i < flagValues.length; i++) {
				if (flagValues[i] != selected) {
					positionGroup.addProposal(new ModifierLinkedModeProposal(flagValues[i], 9 - i));
				}
			}
		}
	}

	public static void installLinkedVisibilityProposals(LinkedProposalModelCore linkedProposalModel, ASTRewrite rewrite, List<IExtendedModifier> modifiers, boolean inInterface) {
		ModifierCorrectionSubProcessor.installLinkedVisibilityProposals(linkedProposalModel, rewrite, modifiers, inInterface, KEY_MODIFIER);
	}

	private static Modifier findVisibilityModifier(List<IExtendedModifier> modifiers) {
		for (int i = 0; i < modifiers.size(); i++) {
			IExtendedModifier curr = modifiers.get(i);
			if (curr instanceof Modifier modifier) {
				ModifierKeyword keyword = modifier.getKeyword();
				if (keyword == ModifierKeyword.PUBLIC_KEYWORD || keyword == ModifierKeyword.PROTECTED_KEYWORD || keyword == ModifierKeyword.PRIVATE_KEYWORD) {
					return modifier;
				}
			}
		}
		return null;
	}

	public static void addSealedMissingModifierProposal(IInvocationContext context, IProblemLocation problem, Collection<ProposalKindWrapper> proposals) {
		if (proposals == null) {
			return;
		}
		ASTNode selectedNode = problem.getCoveringNode(context.getASTRoot());
		if (!(selectedNode instanceof SimpleName)) {
			return;
		}
		if (!(selectedNode.getParent() instanceof TypeDeclaration)) {
			return;
		}
		TypeDeclaration typeDecl = (TypeDeclaration) selectedNode.getParent();
		boolean isInterface = typeDecl.isInterface();

		ICompilationUnit cu = context.getCompilationUnit();
		ITypeBinding typeDeclBinding = typeDecl.resolveBinding();
		int relevance = IProposalRelevance.CHANGE_MODIFIER_TO_FINAL;
		String label;

		if (!isInterface) {
			// Add final modifier
			label = Messages.format(CorrectionMessages.ModifierCorrectionSubProcessor_changemodifierto_final_description, typeDecl.getName());
			ModifierChangeCorrectionProposalCore p = new ModifierChangeCorrectionProposalCore(label, cu, typeDeclBinding, typeDecl, Modifier.FINAL, 0, relevance);
			proposals.add(CodeActionHandler.wrap(p, CodeActionKind.QuickFix));
		}

		// Add sealed modifier
		label = Messages.format(CorrectionMessages.ModifierCorrectionSubProcessor_changemodifierto_sealed_description, typeDecl.getName());
		ModifierChangeCorrectionProposalCore p1 = new ModifierChangeCorrectionProposalCore(label, cu, typeDeclBinding, typeDecl, Modifier.SEALED, 0, relevance);
		proposals.add(CodeActionHandler.wrap(p1, CodeActionKind.QuickFix));

		// Add non-sealed modifier
		label = Messages.format(CorrectionMessages.ModifierCorrectionSubProcessor_changemodifierto_nonsealed_description, typeDecl.getName());
		ModifierChangeCorrectionProposalCore p2 = new ModifierChangeCorrectionProposalCore(label, cu, typeDeclBinding, typeDecl, Modifier.NON_SEALED, 0, relevance);
		proposals.add(CodeActionHandler.wrap(p2, CodeActionKind.QuickFix));
	}

}
