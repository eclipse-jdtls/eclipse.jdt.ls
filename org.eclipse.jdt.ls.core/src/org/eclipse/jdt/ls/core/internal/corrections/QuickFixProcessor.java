/*******************************************************************************
 * Copyright (c) 2000, 2017 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Copied from /org.eclipse.jdt.ui/src/org/eclipse/jdt/internal/ui/text/correction/QuickFixProcessor.java
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *     Benjamin Muskalla <b.muskalla@gmx.net> - [quick fix] Quick fix for missing synchronized modifier - https://bugs.eclipse.org/bugs/show_bug.cgi?id=245250
 *     Stephan Herrmann - Contributions for
 *								[quick fix] Add quick fixes for null annotations - https://bugs.eclipse.org/337977
 *								[quick fix] The fix change parameter type to @Nonnull generated a null change - https://bugs.eclipse.org/400668
 *								[quick fix] don't propose null annotations when those are disabled - https://bugs.eclipse.org/405086
 *								[quickfix] Update null annotation quick fixes for bug 388281 - https://bugs.eclipse.org/395555
 *     Lukas Hanke <hanke@yatta.de> - Bug 241696 [quick fix] quickfix to iterate over a collection - https://bugs.eclipse.org/bugs/show_bug.cgi?id=241696
 *     Sandra Lions <sandra.lions-piron@oracle.com> - [quick fix] for qualified enum constants in switch-case labels - https://bugs.eclipse.org/bugs/90140
 *******************************************************************************/
package org.eclipse.jdt.ls.core.internal.corrections;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.jdt.core.IBuffer;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.compiler.IProblem;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.manipulation.CUCorrectionProposalCore;
import org.eclipse.jdt.core.manipulation.ChangeCorrectionProposalCore;
import org.eclipse.jdt.internal.corext.util.JavaModelUtil;
import org.eclipse.jdt.internal.ui.text.correction.IProposalRelevance;
import org.eclipse.jdt.internal.ui.text.correction.UnInitializedFinalFieldBaseSubProcessor;
import org.eclipse.jdt.internal.ui.text.correction.proposals.AddImportCorrectionProposalCore;
import org.eclipse.jdt.internal.ui.text.correction.proposals.ReplaceCorrectionProposalCore;
import org.eclipse.jdt.ls.core.internal.JavaLanguageServerPlugin;
import org.eclipse.jdt.ls.core.internal.corrections.proposals.GetterSetterCorrectionSubProcessor;
import org.eclipse.jdt.ls.core.internal.corrections.proposals.GradleCompatibilityProcessor;
import org.eclipse.jdt.ls.core.internal.corrections.proposals.JavadocTagsSubProcessor;
import org.eclipse.jdt.ls.core.internal.corrections.proposals.LocalCorrectionsSubProcessor;
import org.eclipse.jdt.ls.core.internal.corrections.proposals.ReorgCorrectionsSubProcessor;
import org.eclipse.jdt.ls.core.internal.corrections.proposals.TypeMismatchSubProcessor;
import org.eclipse.jdt.ls.core.internal.corrections.proposals.UnresolvedElementsSubProcessor;
import org.eclipse.jdt.ls.core.internal.handlers.CodeActionHandler;
import org.eclipse.jdt.ls.core.internal.handlers.OrganizeImportsHandler;
import org.eclipse.jdt.ls.core.internal.text.correction.ModifierCorrectionSubProcessor;
import org.eclipse.jdt.ui.text.java.IInvocationContext;
import org.eclipse.jdt.ui.text.java.IProblemLocation;
import org.eclipse.lsp4j.CodeActionKind;
import org.eclipse.lsp4j.CodeActionParams;

/**
 */
public class QuickFixProcessor {

	private static int moveBack(int offset, int start, String ignoreCharacters, ICompilationUnit cu) {
		try {
			IBuffer buf = cu.getBuffer();
			while (offset >= start) {
				if (ignoreCharacters.indexOf(buf.getChar(offset - 1)) == -1) {
					return offset;
				}
				offset--;
			}
		} catch (JavaModelException e) {
			// use start
		}
		return start;
	}

	public List<ProposalKindWrapper> getCorrections(CodeActionParams params, IInvocationContext context, IProblemLocation[] locations) throws CoreException {
		if (locations == null || locations.length == 0) {
			return Collections.emptyList();
		}
		ArrayList<ProposalKindWrapper> resultingCollections = new ArrayList<>();
		Set<Integer> handledProblems = new HashSet<>(locations.length);
		for (int i = 0; i < locations.length; i++) {
			IProblemLocation curr = locations[i];
			if (handledProblems(curr, locations, handledProblems)) {
				process(params, context, curr, resultingCollections);
			}
		}
		return resultingCollections;
	}

	private static boolean handledProblems(IProblemLocation location, IProblemLocation[] locations, Set<Integer> handledProblems) {
		int problemId = location.getProblemId();
		if (handledProblems.contains(problemId)) {
			return false;
		}
		if (problemId == IProblem.UndefinedName) {
			// skip different problems with the same resolution
			for (IProblemLocation l : locations) {
				if (l.getProblemId() == IProblem.UndefinedType && Arrays.deepEquals(l.getProblemArguments(), location.getProblemArguments())) {
					handledProblems.add(problemId);
					return false;
				}
			}
		}
		return handledProblems.add(problemId);
	}

	private void process(CodeActionParams params, IInvocationContext context, IProblemLocation problem, Collection<ProposalKindWrapper> proposals) throws CoreException {
		int id = problem.getProblemId();
		if (id == 0) { // no proposals for none-problem locations
			return;
		}
		switch (id) {
			case IProblem.UnterminatedString:
				String quoteLabel = CorrectionMessages.JavaCorrectionProcessor_addquote_description;
				int pos = moveBack(problem.getOffset() + problem.getLength(), problem.getOffset(), "\n\r", //$NON-NLS-1$
						context.getCompilationUnit());
				ChangeCorrectionProposalCore prop = new ReplaceCorrectionProposalCore(quoteLabel, context.getCompilationUnit(), pos, 0, "\"", //$NON-NLS-1$
						IProposalRelevance.ADD_QUOTE);
				proposals.add(CodeActionHandler.wrap(prop, CodeActionKind.QuickFix));
				break;
			case IProblem.UnusedImport:
			case IProblem.DuplicateImport:
			case IProblem.CannotImportPackage:
			case IProblem.ConflictingImport:
			case IProblem.ImportNotFound:
				ReorgCorrectionsSubProcessor.removeImportStatementProposals(context, problem, proposals);
				break;
			case IProblem.PublicClassMustMatchFileName:
				ReorgCorrectionsSubProcessor.getWrongTypeNameProposals(context, problem, proposals);
				break;
			case IProblem.PackageIsNotExpectedPackage:
				ReorgCorrectionsSubProcessor.getWrongPackageDeclNameProposals(context, problem, proposals);
				break;

			case IProblem.UndefinedMethod:
				UnresolvedElementsSubProcessor.getMethodProposals(context, problem, false, proposals);
				break;
			case IProblem.UndefinedConstructor:
				UnresolvedElementsSubProcessor.getConstructorProposals(context, problem, proposals);
				break;
			case IProblem.UndefinedAnnotationMember:
				UnresolvedElementsSubProcessor.getAnnotationMemberProposals(context, problem, proposals);
				break;
			case IProblem.ParameterMismatch:
				UnresolvedElementsSubProcessor.getMethodProposals(context, problem, true, proposals);
				break;
			// case IProblem.MethodButWithConstructorName:
			// ReturnTypeSubProcessor.addMethodWithConstrNameProposals(context,
			// problem, proposals);
			// break;
			case IProblem.UndefinedField:
			case IProblem.UndefinedName:
			case IProblem.UnresolvedVariable:
				UnresolvedElementsSubProcessor.getVariableProposals(context, problem, null, proposals);
				break;
			case IProblem.UninitializedBlankFinalField:
				UnInitializedFinalFieldBaseSubProcessor<ProposalKindWrapper> processor = new UnInitializedFinalFieldSubProcessor();
				processor.addProposals(context, problem, proposals);
				break;
			case IProblem.AmbiguousType:
			case IProblem.JavadocAmbiguousType:
				UnresolvedElementsSubProcessor.getAmbiguousTypeReferenceProposals(context, problem, proposals);
				break;
			case IProblem.UndefinedType:
			case IProblem.JavadocUndefinedType:
				UnresolvedElementsSubProcessor.getTypeProposals(context, problem, proposals);
				break;
			case IProblem.TypeMismatch:
			case IProblem.ReturnTypeMismatch:
				TypeMismatchSubProcessor.addTypeMismatchProposals(context, problem, proposals);
				break;
			case IProblem.IncompatibleTypesInForeach:
				TypeMismatchSubProcessor.addTypeMismatchInForEachProposals(context, problem, proposals);
				break;
			case IProblem.IncompatibleReturnType:
				TypeMismatchSubProcessor.addIncompatibleReturnTypeProposals(context, problem, proposals);
				break;
			case IProblem.IncompatibleExceptionInThrowsClause:
				TypeMismatchSubProcessor.addIncompatibleThrowsProposals(context, problem, proposals);
				break;
			case IProblem.UnhandledException:
			case IProblem.UnhandledExceptionOnAutoClose:
				LocalCorrectionsSubProcessor.addUncaughtExceptionProposals(context, problem, proposals);
				break;
			case IProblem.UnreachableCatch:
			case IProblem.InvalidCatchBlockSequence:
			case IProblem.InvalidUnionTypeReferenceSequence:
				LocalCorrectionsSubProcessor.addUnreachableCatchProposals(context, problem, proposals);
				break;
			case IProblem.RedundantSuperinterface:
				LocalCorrectionsSubProcessor.addRedundantSuperInterfaceProposal(context, problem, proposals);
				break;
			case IProblem.VoidMethodReturnsValue:
				ReturnTypeSubProcessor.addVoidMethodReturnsProposals(context, problem, proposals);
				break;
			case IProblem.MethodReturnsVoid:
				ReturnTypeSubProcessor.addMethodReturnsVoidProposals(context, problem, proposals);
				break;
			case IProblem.MissingReturnType:
				ReturnTypeSubProcessor.addMissingReturnTypeProposals(context, problem, proposals);
				break;
			case IProblem.ShouldReturnValue:
			case IProblem.ShouldReturnValueHintMissingDefault:
				ReturnTypeSubProcessor.addMissingReturnStatementProposals(context, problem, proposals);
				break;
			// case IProblem.NonExternalizedStringLiteral:
			// LocalCorrectionsSubProcessor.addNLSProposals(context, problem,
			// proposals);
			// break;
			// case IProblem.UnnecessaryNLSTag:
			// LocalCorrectionsSubProcessor.getUnnecessaryNLSTagProposals(context,
			// problem, proposals);
			// break;
			case IProblem.NonStaticAccessToStaticField:
			case IProblem.NonStaticAccessToStaticMethod:
			case IProblem.NonStaticOrAlienTypeReceiver:
			case IProblem.IndirectAccessToStaticField:
			case IProblem.IndirectAccessToStaticMethod:
				LocalCorrectionsSubProcessor.addCorrectAccessToStaticProposals(context, problem, proposals);
				break;
			case IProblem.SealedMissingClassModifier:
			case IProblem.SealedMissingInterfaceModifier:
				ModifierCorrectionSubProcessor.addSealedMissingModifierProposal(context, problem, proposals);
				break;
			case IProblem.SealedNotDirectSuperInterface:
			case IProblem.SealedNotDirectSuperClass:
				LocalCorrectionsSubProcessor.addSealedAsDirectSuperTypeProposal(context, problem, proposals);
			case IProblem.SealedSuperClassDoesNotPermit:
			case IProblem.SealedSuperInterfaceDoesNotPermit:
				LocalCorrectionsSubProcessor.addTypeAsPermittedSubTypeProposal(context, problem, proposals);
			case IProblem.StaticMethodRequested:
			case IProblem.NonStaticFieldFromStaticInvocation:
			case IProblem.InstanceMethodDuringConstructorInvocation:
			case IProblem.InstanceFieldDuringConstructorInvocation:
				ModifierCorrectionSubProcessor.addNonAccessibleReferenceProposal(context, problem, proposals, ModifierCorrectionSubProcessor.TO_STATIC, IProposalRelevance.CHANGE_MODIFIER_TO_STATIC);
				break;
		    case IProblem.NonBlankFinalLocalAssignment:
		    case IProblem.DuplicateFinalLocalInitialization:
		    case IProblem.FinalFieldAssignment:
		    case IProblem.DuplicateBlankFinalFieldInitialization:
		    case IProblem.AnonymousClassCannotExtendFinalClass:
		    case IProblem.ClassExtendFinalClass:
				ModifierCorrectionSubProcessor.addNonAccessibleReferenceProposal(context, problem, proposals, ModifierCorrectionSubProcessor.TO_NON_FINAL, IProposalRelevance.REMOVE_FINAL_MODIFIER);
				break;
			case IProblem.InheritedMethodReducesVisibility:
			case IProblem.MethodReducesVisibility:
			case IProblem.OverridingNonVisibleMethod:
				ModifierCorrectionSubProcessor.addChangeOverriddenModifierProposal(context, problem, proposals, ModifierCorrectionSubProcessor.TO_VISIBLE);
				break;
			case IProblem.FinalMethodCannotBeOverridden:
				ModifierCorrectionSubProcessor.addChangeOverriddenModifierProposal(context, problem, proposals, ModifierCorrectionSubProcessor.TO_NON_FINAL);
				break;
			case IProblem.CannotOverrideAStaticMethodWithAnInstanceMethod:
				ModifierCorrectionSubProcessor.addChangeOverriddenModifierProposal(context, problem, proposals, ModifierCorrectionSubProcessor.TO_NON_STATIC);
				break;
			case IProblem.CannotHideAnInstanceMethodWithAStaticMethod:
			case IProblem.IllegalModifierForInterfaceMethod:
			case IProblem.IllegalModifierForInterface:
			case IProblem.IllegalModifierForClass:
			case IProblem.IllegalModifierForInterfaceField:
			case IProblem.UnexpectedStaticModifierForField:
			case IProblem.IllegalModifierCombinationFinalVolatileForField:
			case IProblem.IllegalModifierForMemberInterface:
			case IProblem.IllegalModifierForMemberClass:
			case IProblem.IllegalModifierForLocalClass:
			case IProblem.IllegalModifierForArgument:
			case IProblem.IllegalModifierForField:
			case IProblem.IllegalModifierForMethod:
			case IProblem.IllegalModifierForConstructor:
			case IProblem.IllegalModifierForVariable:
			case IProblem.IllegalModifierForEnum:
			case IProblem.IllegalModifierForEnumConstant:
			case IProblem.IllegalModifierForEnumConstructor:
			case IProblem.IllegalModifierForMemberEnum:
			case IProblem.IllegalVisibilityModifierForInterfaceMemberType:
			case IProblem.UnexpectedStaticModifierForMethod:
			case IProblem.IllegalModifierForInterfaceMethod18:
				ModifierCorrectionSubProcessor.addRemoveInvalidModifiersProposal(context, problem, proposals, IProposalRelevance.REMOVE_INVALID_MODIFIERS);
				break;
			case IProblem.NotVisibleField:
				GetterSetterCorrectionSubProcessor.addGetterSetterProposal(context, problem, proposals, IProposalRelevance.GETTER_SETTER_NOT_VISIBLE_FIELD);
				ModifierCorrectionSubProcessor.addNonAccessibleReferenceProposal(context, problem, proposals, ModifierCorrectionSubProcessor.TO_VISIBLE, IProposalRelevance.CHANGE_VISIBILITY);
				break;
			case IProblem.NotVisibleMethod:
			case IProblem.NotVisibleConstructor:
			case IProblem.NotVisibleType:
			case IProblem.JavadocNotVisibleType:
				ModifierCorrectionSubProcessor.addNonAccessibleReferenceProposal(context, problem, proposals, ModifierCorrectionSubProcessor.TO_VISIBLE, IProposalRelevance.CHANGE_VISIBILITY);
				break;
			case IProblem.BodyForAbstractMethod:
			case IProblem.AbstractMethodInAbstractClass:
			case IProblem.AbstractMethodInEnum:
			case IProblem.EnumAbstractMethodMustBeImplemented:
				ModifierCorrectionSubProcessor.addAbstractMethodProposals(context, problem, proposals);
				break;
			case IProblem.AbstractMethodsInConcreteClass:
				ModifierCorrectionSubProcessor.addAbstractTypeProposals(context, problem, proposals);
				break;
			case IProblem.AbstractMethodMustBeImplemented:
			case IProblem.EnumConstantMustImplementAbstractMethod:
				LocalCorrectionsSubProcessor.addUnimplementedMethodsProposals(context, problem, proposals);
				break;
			// case IProblem.ShouldImplementHashcode:
			// LocalCorrectionsSubProcessor.addMissingHashCodeProposals(context,
			// problem, proposals);
			// break;
			case IProblem.MissingValueForAnnotationMember:
				LocalCorrectionsSubProcessor.addValueForAnnotationProposals(context, problem, proposals);
				break;
			// case IProblem.BodyForNativeMethod:
			// ModifierCorrectionSubProcessor.addNativeMethodProposals(context,
			// problem, proposals);
			// break;
			// case IProblem.MethodRequiresBody:
			// ModifierCorrectionSubProcessor.addMethodRequiresBodyProposals(context,
			// problem, proposals);
			// break;
			// case IProblem.OuterLocalMustBeFinal:
			// ModifierCorrectionSubProcessor.addNonFinalLocalProposal(context,
			// problem, proposals);
			// break;
			// case IProblem.UninitializedLocalVariable:
			// case IProblem.UninitializedLocalVariableHintMissingDefault:
			// LocalCorrectionsSubProcessor.addUninitializedLocalVariableProposal(context,
			// problem, proposals);
			// break;
			case IProblem.UnhandledExceptionInDefaultConstructor:
			case IProblem.UndefinedConstructorInDefaultConstructor:
			case IProblem.NotVisibleConstructorInDefaultConstructor:
				LocalCorrectionsSubProcessor.addConstructorFromSuperclassProposal(context, problem, proposals);
				break;
			case IProblem.UnusedPrivateMethod:
			case IProblem.UnusedPrivateConstructor:
			case IProblem.UnusedPrivateType:
			case IProblem.LocalVariableIsNeverUsed:
			case IProblem.ArgumentIsNeverUsed:
			case IProblem.LambdaParameterIsNeverUsed:
			case IProblem.UnusedPrivateField:
				LocalCorrectionsSubProcessor.addUnusedMemberProposal(context, problem, proposals);
				break;
			// case IProblem.NeedToEmulateFieldReadAccess:
			// case IProblem.NeedToEmulateFieldWriteAccess:
			// case IProblem.NeedToEmulateMethodAccess:
			// case IProblem.NeedToEmulateConstructorAccess:
			// ModifierCorrectionSubProcessor.addNonAccessibleReferenceProposal(context,
			// problem, proposals, ModifierCorrectionSubProcessor.TO_NON_PRIVATE,
			// IProposalRelevance.CHANGE_VISIBILITY_TO_NON_PRIVATE);
			// break;
			// case IProblem.SuperfluousSemicolon:
			// LocalCorrectionsSubProcessor.addSuperfluousSemicolonProposal(context,
			// problem, proposals);
			// break;
			case IProblem.UnnecessaryCast:
				LocalCorrectionsSubProcessor.addUnnecessaryCastProposal(context, problem, proposals);
				break;
			// case IProblem.UnnecessaryInstanceof:
			// LocalCorrectionsSubProcessor.addUnnecessaryInstanceofProposal(context,
			// problem, proposals);
			// break;
			// case IProblem.UnusedMethodDeclaredThrownException:
			// case IProblem.UnusedConstructorDeclaredThrownException:
			// LocalCorrectionsSubProcessor.addUnnecessaryThrownExceptionProposal(context,
			// problem, proposals);
			// break;
			// case IProblem.UnqualifiedFieldAccess:
			// GetterSetterCorrectionSubProcessor.addGetterSetterProposal(context,
			// problem, proposals,
			// IProposalRelevance.GETTER_SETTER_UNQUALIFIED_FIELD_ACCESS);
			// LocalCorrectionsSubProcessor.addUnqualifiedFieldAccessProposal(context,
			// problem, proposals);
			// break;
			// case IProblem.Task:
			// proposals.add(new TaskMarkerProposal(context.getCompilationUnit(),
			// problem, 10));
			// break;
			case IProblem.JavadocMissing:
				ASTNode node = problem.getCoveringNode(context.getASTRoot());
				JavadocTagsSubProcessor.getMissingJavadocCommentProposals(context, node, proposals, CodeActionKind.QuickFix);
				break;
			case IProblem.JavadocMissingParamTag:
			case IProblem.JavadocMissingReturnTag:
			case IProblem.JavadocMissingThrowsTag:
				JavadocTagsSubProcessor.getMissingJavadocTagProposals(context, problem, proposals);
				break;
			case IProblem.JavadocInvalidThrowsClassName:
			case IProblem.JavadocDuplicateThrowsClassName:
			case IProblem.JavadocDuplicateReturnTag:
			case IProblem.JavadocDuplicateParamName:
			case IProblem.JavadocInvalidParamName:
			case IProblem.JavadocUnexpectedTag:
			case IProblem.JavadocInvalidTag:
				JavadocTagsSubProcessor.getRemoveJavadocTagProposals(context, problem, proposals);
				break;
			case IProblem.JavadocInvalidMemberTypeQualification:
				JavadocTagsSubProcessor.getInvalidQualificationProposals(context, problem, proposals);
				break;
			//
			// case IProblem.LocalVariableHidingLocalVariable:
			// case IProblem.LocalVariableHidingField:
			// case IProblem.FieldHidingLocalVariable:
			// case IProblem.FieldHidingField:
			// case IProblem.ArgumentHidingLocalVariable:
			// case IProblem.ArgumentHidingField:
			// case IProblem.UseAssertAsAnIdentifier:
			// case IProblem.UseEnumAsAnIdentifier:
			// case IProblem.RedefinedLocal:
			// case IProblem.RedefinedArgument:
			// case IProblem.DuplicateField:
			// case IProblem.DuplicateMethod:
			// case IProblem.DuplicateTypeVariable:
			// case IProblem.DuplicateNestedType:
			// LocalCorrectionsSubProcessor.addInvalidVariableNameProposals(context,
			// problem, proposals);
			// break;
			case IProblem.NoMessageSendOnArrayType:
				UnresolvedElementsSubProcessor.getArrayAccessProposals(context, problem, proposals);
				break;
			// case IProblem.InvalidOperator:
			// LocalCorrectionsSubProcessor.getInvalidOperatorProposals(context,
			// problem, proposals);
			// break;
			case IProblem.MissingSerialVersion:
				SerialVersionSubProcessor.getSerialVersionProposals(context, problem, proposals);
				break;
			// case IProblem.UnnecessaryElse:
			// LocalCorrectionsSubProcessor.getUnnecessaryElseProposals(context,
			// problem, proposals);
			// break;
			// case IProblem.SuperclassMustBeAClass:
			// LocalCorrectionsSubProcessor.getInterfaceExtendsClassProposals(context,
			// problem, proposals);
			// break;
			case IProblem.CodeCannotBeReached:
			case IProblem.DeadCode:
				LocalCorrectionsSubProcessor.getUnreachableCodeProposals(context, problem, proposals);
				break;
			// case IProblem.InvalidUsageOfTypeParameters:
			// case IProblem.InvalidUsageOfStaticImports:
			// case IProblem.InvalidUsageOfForeachStatements:
			// case IProblem.InvalidUsageOfTypeArguments:
			// case IProblem.InvalidUsageOfEnumDeclarations:
			// case IProblem.InvalidUsageOfVarargs:
			// case IProblem.InvalidUsageOfAnnotations:
			// case IProblem.InvalidUsageOfAnnotationDeclarations:
			// ReorgCorrectionsSubProcessor.getNeedHigherComplianceProposals(context,
			// problem, proposals, JavaCore.VERSION_1_5);
			// break;
			// case IProblem.DiamondNotBelow17:
			// TypeArgumentMismatchSubProcessor.getInferDiamondArgumentsProposal(context,
			// problem, proposals);
			// //$FALL-THROUGH$
			// case IProblem.AutoManagedResourceNotBelow17:
			// case IProblem.MultiCatchNotBelow17:
			// case IProblem.PolymorphicMethodNotBelow17:
			// case IProblem.BinaryLiteralNotBelow17:
			// case IProblem.UnderscoresInLiteralsNotBelow17:
			// case IProblem.SwitchOnStringsNotBelow17:
			// ReorgCorrectionsSubProcessor.getNeedHigherComplianceProposals(context,
			// problem, proposals, JavaCore.VERSION_1_7);
			// break;
			// case IProblem.LambdaExpressionNotBelow18:
			// LocalCorrectionsSubProcessor.getConvertLambdaToAnonymousClassCreationsProposals(context,
			// problem, proposals);
			// //$FALL-THROUGH$
			// case IProblem.ExplicitThisParameterNotBelow18:
			// case IProblem.DefaultMethodNotBelow18:
			// case IProblem.StaticInterfaceMethodNotBelow18:
			// case IProblem.MethodReferenceNotBelow18:
			// case IProblem.ConstructorReferenceNotBelow18:
			// case IProblem.IntersectionCastNotBelow18:
			// case IProblem.InvalidUsageOfTypeAnnotations:
			// ReorgCorrectionsSubProcessor.getNeedHigherComplianceProposals(context,
			// problem, proposals, JavaCore.VERSION_1_8);
			// break;
			// case IProblem.NonGenericType:
			// TypeArgumentMismatchSubProcessor.removeMismatchedArguments(context,
			// problem, proposals);
			// break;
			// case IProblem.MissingOverrideAnnotation:
			// case
			// IProblem.MissingOverrideAnnotationForInterfaceMethodImplementation:
			// ModifierCorrectionSubProcessor.addOverrideAnnotationProposal(context,
			// problem, proposals);
			// break;
			// case IProblem.MethodMustOverride:
			// case IProblem.MethodMustOverrideOrImplement:
			// ModifierCorrectionSubProcessor.removeOverrideAnnotationProposal(context,
			// problem, proposals);
			// break;
			// case IProblem.FieldMissingDeprecatedAnnotation:
			// case IProblem.MethodMissingDeprecatedAnnotation:
			// case IProblem.TypeMissingDeprecatedAnnotation:
			// ModifierCorrectionSubProcessor.addDeprecatedAnnotationProposal(context,
			// problem, proposals);
			// break;
			// case IProblem.OverridingDeprecatedMethod:
			// ModifierCorrectionSubProcessor.addOverridingDeprecatedMethodProposal(context,
			// problem, proposals);
			// break;
			// case IProblem.IsClassPathCorrect:
			// ReorgCorrectionsSubProcessor.getIncorrectBuildPathProposals(context,
			// problem, proposals);
			// break;
			// case IProblem.ForbiddenReference:
			// case IProblem.DiscouragedReference:
			// ReorgCorrectionsSubProcessor.getAccessRulesProposals(context,
			// problem, proposals);
			// break;
			// case IProblem.AssignmentHasNoEffect:
			// LocalCorrectionsSubProcessor.getAssignmentHasNoEffectProposals(context,
			// problem, proposals);
			// break;
			// case IProblem.UnsafeTypeConversion:
			// case IProblem.RawTypeReference:
			// case IProblem.UnsafeRawMethodInvocation:
			// LocalCorrectionsSubProcessor.addDeprecatedFieldsToMethodsProposals(context,
			// problem, proposals);
			// //$FALL-THROUGH$
			// case IProblem.UnsafeElementTypeConversion:
			// LocalCorrectionsSubProcessor.addTypePrametersToRawTypeReference(context,
			// problem, proposals);
			// break;
			// case IProblem.RedundantSpecificationOfTypeArguments:
			// LocalCorrectionsSubProcessor.addRemoveRedundantTypeArgumentsProposals(context,
			// problem, proposals);
			// break;
			// case IProblem.FallthroughCase:
			// LocalCorrectionsSubProcessor.addFallThroughProposals(context,
			// problem, proposals);
			// break;
			// case IProblem.UnhandledWarningToken:
			// SuppressWarningsSubProcessor.addUnknownSuppressWarningProposals(context, problem, proposals);
			// break;
			case IProblem.ProblemNotAnalysed:
			case IProblem.UnusedWarningToken:
				SuppressWarningsSubProcessor.addRemoveUnusedSuppressWarningProposals(context, problem, proposals);
				break;
			case IProblem.MissingEnumConstantCase:
			case IProblem.MissingEnumDefaultCase:
			case IProblem.SwitchExpressionsYieldMissingEnumConstantCase:
				LocalCorrectionsSubProcessor.getMissingEnumConstantCaseProposals(context, problem, proposals);
				break;
			case IProblem.EnhancedSwitchMissingDefault:
			case IProblem.SwitchExpressionsYieldMissingDefaultCase:
				LocalCorrectionsSubProcessor.addPermittedTypesProposal(context, problem, proposals);
				break;
			case IProblem.MissingDefaultCase:
				LocalCorrectionsSubProcessor.addMissingDefaultCaseProposal(context, problem, proposals);
				break;
			case IProblem.MissingEnumConstantCaseDespiteDefault:
				LocalCorrectionsSubProcessor.getMissingEnumConstantCaseProposals(context, problem, proposals);
				LocalCorrectionsSubProcessor.addCasesOmittedProposals(context, problem, proposals);
				break;
			case IProblem.UnclosedCloseable:
			case IProblem.PotentiallyUnclosedCloseable:
				LocalCorrectionsSubProcessor.getTryWithResourceProposals(context, problem, proposals);
				break;
			case IProblem.NotAccessibleType:
				GradleCompatibilityProcessor.getGradleCompatibilityProposals(context, problem, proposals);
				break;
			// case IProblem.MissingSynchronizedModifierInInheritedMethod:
			// ModifierCorrectionSubProcessor.addSynchronizedMethodProposal(context,
			// problem, proposals);
			// break;
			// case IProblem.UnusedObjectAllocation:
			// LocalCorrectionsSubProcessor.getUnusedObjectAllocationProposals(context,
			// problem, proposals);
			// break;
			// case IProblem.MethodCanBeStatic:
			// case IProblem.MethodCanBePotentiallyStatic:
			// ModifierCorrectionSubProcessor.addStaticMethodProposal(context,
			// problem, proposals);
			// break;
			// case IProblem.PotentialHeapPollutionFromVararg :
			// VarargsWarningsSubProcessor.addAddSafeVarargsProposals(context,
			// problem, proposals);
			// break;
			// case IProblem.UnsafeGenericArrayForVarargs:
			// VarargsWarningsSubProcessor.addAddSafeVarargsToDeclarationProposals(context,
			// problem, proposals);
			// break;
			// case IProblem.SafeVarargsOnFixedArityMethod :
			// case IProblem.SafeVarargsOnNonFinalInstanceMethod:
			// VarargsWarningsSubProcessor.addRemoveSafeVarargsProposals(context,
			// problem, proposals);
			// break;
			// case IProblem.IllegalReturnNullityRedefinition:
			// case IProblem.IllegalDefinitionToNonNullParameter:
			// case IProblem.IllegalRedefinitionToNonNullParameter:
			// boolean isArgProblem = id !=
			// IProblem.IllegalReturnNullityRedefinition;
			// NullAnnotationsCorrectionProcessor.addNullAnnotationInSignatureProposal(context,
			// problem, proposals, ChangeKind.LOCAL, isArgProblem);
			// NullAnnotationsCorrectionProcessor.addNullAnnotationInSignatureProposal(context,
			// problem, proposals, ChangeKind.OVERRIDDEN, isArgProblem);
			// break;
			// case IProblem.RequiredNonNullButProvidedSpecdNullable:
			// case IProblem.RequiredNonNullButProvidedUnknown:
			// NullAnnotationsCorrectionProcessor.addExtractCheckedLocalProposal(context,
			// problem, proposals);
			// //$FALL-THROUGH$
			// case IProblem.RequiredNonNullButProvidedNull:
			// case IProblem.RequiredNonNullButProvidedPotentialNull:
			// case IProblem.ParameterLackingNonNullAnnotation:
			// case IProblem.ParameterLackingNullableAnnotation:
			// NullAnnotationsCorrectionProcessor.addReturnAndArgumentTypeProposal(context,
			// problem, ChangeKind.LOCAL, proposals);
			// NullAnnotationsCorrectionProcessor.addReturnAndArgumentTypeProposal(context,
			// problem, ChangeKind.TARGET, proposals);
			// break;
			// case IProblem.RedundantNullCheckAgainstNonNullType:
			// case IProblem.SpecdNonNullLocalVariableComparisonYieldsFalse:
			// case IProblem.RedundantNullCheckOnSpecdNonNullLocalVariable:
			// IJavaProject prj = context.getCompilationUnit().getJavaProject();
			// if (prj != null &&
			// JavaCore.ENABLED.equals(prj.getOption(JavaCore.COMPILER_ANNOTATION_NULL_ANALYSIS,
			// true))) {
			// NullAnnotationsCorrectionProcessor.addReturnAndArgumentTypeProposal(context,
			// problem, ChangeKind.LOCAL, proposals);
			// }
			// break;
			// case IProblem.RedundantNullAnnotation:
			// case IProblem.RedundantNullDefaultAnnotationPackage:
			// case IProblem.RedundantNullDefaultAnnotationType:
			// case IProblem.RedundantNullDefaultAnnotationMethod:
			// case IProblem.RedundantNullDefaultAnnotationLocal:
			// case IProblem.RedundantNullDefaultAnnotationField:
			// NullAnnotationsCorrectionProcessor.addRemoveRedundantAnnotationProposal(context,
			// problem, proposals);
			// break;
			// case IProblem.UnusedTypeParameter:
			// LocalCorrectionsSubProcessor.addUnusedTypeParameterProposal(context,
			// problem, proposals);
			// break;
			// case IProblem.NullableFieldReference:
			// NullAnnotationsCorrectionProcessor.addExtractCheckedLocalProposal(context,
			// problem, proposals);
			// break;
			// case IProblem.ConflictingNullAnnotations:
			// case IProblem.ConflictingInheritedNullAnnotations:
			// NullAnnotationsCorrectionProcessor.addReturnAndArgumentTypeProposal(context,
			// problem, ChangeKind.LOCAL, proposals);
			// NullAnnotationsCorrectionProcessor.addReturnAndArgumentTypeProposal(context,
			// problem, ChangeKind.INVERSE, proposals);
			// NullAnnotationsCorrectionProcessor.addReturnAndArgumentTypeProposal(context,
			// problem, ChangeKind.OVERRIDDEN, proposals);
			// break;
			// case IProblem.IllegalQualifiedEnumConstantLabel:
			// LocalCorrectionsSubProcessor.addIllegalQualifiedEnumConstantLabelProposal(context,
			// problem, proposals);
			// break;
			// case IProblem.DuplicateInheritedDefaultMethods:
			// case IProblem.InheritedDefaultMethodConflictsWithOtherInherited:
			// LocalCorrectionsSubProcessor.addOverrideDefaultMethodProposal(context,
			// problem, proposals);
			// break;
			// case IProblem.PotentialNullLocalVariableReference:
			// IJavaProject prj2= context.getCompilationUnit().getJavaProject();
			// if (prj2 != null &&
			// JavaCore.ENABLED.equals(prj2.getOption(JavaCore.COMPILER_ANNOTATION_NULL_ANALYSIS,
			// true))) {
			// NullAnnotationsCorrectionProcessor.addLocalVariableAnnotationProposal(context,
			// problem, proposals);
			// }
			// break;
			// case IProblem.TypeAnnotationAtQualifiedName:
			// case IProblem.IllegalTypeAnnotationsInStaticMemberAccess:
			// case IProblem.NullAnnotationAtQualifyingType:
			// case IProblem.IllegalAnnotationForBaseType:
			// TypeAnnotationSubProcessor.addMoveTypeAnnotationToTypeProposal(context,
			// problem, proposals);
			// break;

			default:
				String str = problem.toString();
				System.out.println(str);
		}
		if (JavaModelUtil.is50OrHigher(context.getCompilationUnit().getJavaProject())) {
			SuppressWarningsSubProcessor.addSuppressWarningsProposals(context, problem, proposals);
		}
		// ConfigureProblemSeveritySubProcessor.addConfigureProblemSeverityProposal(context,
		// problem, proposals);
	}

	public void addAddAllMissingImportsProposal(IInvocationContext context, Collection<ProposalKindWrapper> proposals) {
		if (proposals.size() == 0) {
			return;
		}

		Optional<Integer> minRelevance = proposals.stream().filter(w -> w.getProposal() instanceof AddImportCorrectionProposalCore).map((w) -> w.getProposal().getRelevance()).min(Comparator.naturalOrder());
		if (minRelevance.isPresent()) {
			CUCorrectionProposalCore proposal = OrganizeImportsHandler.getOrganizeImportsProposal(CorrectionMessages.UnresolvedElementsSubProcessor_add_allMissing_imports_description, context.getCompilationUnit(),
					minRelevance.get() - 1, context.getASTRoot(), JavaLanguageServerPlugin.getPreferencesManager().getClientPreferences().isAdvancedOrganizeImportsSupported(), true);
			if (proposal != null) {
				proposals.add(CodeActionHandler.wrap(proposal, CodeActionKind.QuickFix));
			}
		}
	}
}
