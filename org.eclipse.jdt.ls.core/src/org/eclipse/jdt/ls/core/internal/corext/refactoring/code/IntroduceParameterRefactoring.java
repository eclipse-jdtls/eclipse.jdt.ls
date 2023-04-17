/*******************************************************************************
 * Copyright (c) 2000, 2011 IBM Corporation and others.
 *
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Originally copied from org.eclipse.jdt.internal.corext.refactoring.code.IntroduceParameterRefactoring
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.jdt.ls.core.internal.corext.refactoring.code;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.StringTokenizer;

import org.eclipse.core.runtime.Assert;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.SubProgressMonitor;
import org.eclipse.jdt.core.IClassFile;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IMethod;
import org.eclipse.jdt.core.ISourceRange;
import org.eclipse.jdt.core.ISourceReference;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.SourceRange;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.Annotation;
import org.eclipse.jdt.core.dom.ArrayInitializer;
import org.eclipse.jdt.core.dom.Assignment;
import org.eclipse.jdt.core.dom.ClassInstanceCreation;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.FieldAccess;
import org.eclipse.jdt.core.dom.IBinding;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.Name;
import org.eclipse.jdt.core.dom.NodeFinder;
import org.eclipse.jdt.core.dom.NullLiteral;
import org.eclipse.jdt.core.dom.ParameterizedType;
import org.eclipse.jdt.core.dom.ParenthesizedExpression;
import org.eclipse.jdt.core.dom.QualifiedName;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.core.dom.Type;
import org.eclipse.jdt.core.dom.rewrite.ImportRewrite;
import org.eclipse.jdt.core.dom.rewrite.ImportRewrite.ImportRewriteContext;
import org.eclipse.jdt.core.refactoring.IJavaRefactorings;
import org.eclipse.jdt.core.refactoring.descriptors.ChangeMethodSignatureDescriptor;
import org.eclipse.jdt.core.refactoring.descriptors.IntroduceParameterDescriptor;
import org.eclipse.jdt.internal.core.manipulation.StubUtility;
import org.eclipse.jdt.internal.core.manipulation.util.BasicElementLabels;
import org.eclipse.jdt.internal.core.refactoring.descriptors.RefactoringSignatureDescriptorFactory;
import org.eclipse.jdt.internal.corext.CorextCore;
import org.eclipse.jdt.internal.corext.codemanipulation.ContextSensitiveImportRewriteContext;
import org.eclipse.jdt.internal.corext.dom.ASTNodes;
import org.eclipse.jdt.internal.corext.dom.Bindings;
import org.eclipse.jdt.internal.corext.dom.ScopeAnalyzer;
import org.eclipse.jdt.internal.corext.dom.fragments.ASTFragmentFactory;
import org.eclipse.jdt.internal.corext.dom.fragments.IASTFragment;
import org.eclipse.jdt.internal.corext.dom.fragments.IExpressionFragment;
import org.eclipse.jdt.internal.corext.fix.LinkedProposalModelCore;
import org.eclipse.jdt.internal.corext.fix.LinkedProposalPositionGroupCore;
import org.eclipse.jdt.internal.corext.refactoring.Checks;
import org.eclipse.jdt.internal.corext.refactoring.JDTRefactoringDescriptorComment;
import org.eclipse.jdt.internal.corext.refactoring.JavaRefactoringArguments;
import org.eclipse.jdt.internal.corext.refactoring.JavaRefactoringDescriptorUtil;
import org.eclipse.jdt.internal.corext.refactoring.ParameterInfo;
import org.eclipse.jdt.internal.corext.refactoring.RefactoringCoreMessages;
import org.eclipse.jdt.internal.corext.refactoring.base.RefactoringStatusCodes;
import org.eclipse.jdt.internal.corext.refactoring.code.CodeRefactoringUtil;
import org.eclipse.jdt.internal.corext.refactoring.structure.BodyUpdater;
import org.eclipse.jdt.internal.corext.refactoring.structure.ChangeSignatureProcessor;
import org.eclipse.jdt.internal.corext.refactoring.structure.CompilationUnitRewrite;
import org.eclipse.jdt.internal.corext.refactoring.tagging.IDelegateUpdating;
import org.eclipse.jdt.internal.corext.util.JavaModelUtil;
import org.eclipse.jdt.internal.corext.util.Messages;
import org.eclipse.jdt.ls.core.internal.IConstants;
import org.eclipse.jdt.ls.core.internal.JavaLanguageServerPlugin;
import org.eclipse.jdt.ls.core.internal.corext.refactoring.RefactoringAvailabilityTester;
import org.eclipse.jdt.ls.core.internal.handlers.FormatterHandler;
import org.eclipse.jdt.ls.core.internal.hover.JavaElementLabels;
import org.eclipse.ltk.core.refactoring.Change;
import org.eclipse.ltk.core.refactoring.Refactoring;
import org.eclipse.ltk.core.refactoring.RefactoringContribution;
import org.eclipse.ltk.core.refactoring.RefactoringCore;
import org.eclipse.ltk.core.refactoring.RefactoringStatus;
import org.eclipse.ltk.core.refactoring.RefactoringStatusEntry;
import org.eclipse.ltk.core.refactoring.participants.ProcessorBasedRefactoring;


public class IntroduceParameterRefactoring extends Refactoring implements IDelegateUpdating {

	private static final String ATTRIBUTE_ARGUMENT= "argument"; //$NON-NLS-1$

	private static final String[] KNOWN_METHOD_NAME_PREFIXES= {"get", "is"}; //$NON-NLS-2$ //$NON-NLS-1$

	private ICompilationUnit fSourceCU;
	private int fSelectionStart;
	private int fSelectionLength;

	private IMethod fMethod;
	private Refactoring fChangeSignatureRefactoring;
	private ChangeSignatureProcessor fChangeSignatureProcessor;
	private ParameterInfo fParameter;
	private String fParameterName;
	private JavaRefactoringArguments fArguments;

	private Expression fSelectedExpression;
	private String[] fExcludedParameterNames;
	private LinkedProposalModelCore fLinkedProposalModel;

	/**
	 * Creates a new introduce parameter refactoring.
	 * @param unit the compilation unit, or <code>null</code> if invoked by scripting
	 * @param selectionStart start
	 * @param selectionLength length
	 */
	public IntroduceParameterRefactoring(ICompilationUnit unit, int selectionStart, int selectionLength) {
		Assert.isTrue(selectionStart >= 0);
		Assert.isTrue(selectionLength >= 0);
		fSourceCU= unit;
		fSelectionStart= selectionStart;
		fSelectionLength= selectionLength;
		fLinkedProposalModel = null;
	}

	public IntroduceParameterRefactoring(JavaRefactoringArguments arguments, RefactoringStatus status) {
		this(null, 0, 0);
		RefactoringStatus initializeStatus = initialize(arguments);
		status.merge(initializeStatus);
	}

	// ------------------- IDelegateUpdating ----------------------

	@Override
	public boolean canEnableDelegateUpdating() {
		return true;
	}

	@Override
	public boolean getDelegateUpdating() {
		return (fChangeSignatureProcessor != null) ? fChangeSignatureProcessor.getDelegateUpdating() : false;
	}

	@Override
	public void setDelegateUpdating(boolean updating) {
		if (fChangeSignatureProcessor != null) {
			fChangeSignatureProcessor.setDelegateUpdating(updating);
		}
	}

	@Override
	public void setDeprecateDelegates(boolean deprecate) {
		if (fChangeSignatureProcessor != null) {
			fChangeSignatureProcessor.setDeprecateDelegates(deprecate);
		}
	}

	@Override
	public boolean getDeprecateDelegates() {
		return (fChangeSignatureProcessor != null) ? fChangeSignatureProcessor.getDeprecateDelegates() : false;
	}

	// ------------------- /IDelegateUpdating ---------------------

	@Override
	public String getName() {
		return RefactoringCoreMessages.IntroduceParameterRefactoring_name;
	}

	//--- checkActivation

	@Override
	public RefactoringStatus checkInitialConditions(IProgressMonitor pm) throws CoreException {
		try {
			pm.beginTask("", 7); //$NON-NLS-1$

			if (! fSourceCU.isStructureKnown()) {
				return RefactoringStatus.createFatalErrorStatus(RefactoringCoreMessages.IntroduceParameterRefactoring_syntax_error);
			}

			IJavaElement enclosingElement = resolveEnclosingElement(fSourceCU, fSelectionStart, fSelectionLength);
			if (! (enclosingElement instanceof IMethod)) {
				return RefactoringStatus.createFatalErrorStatus(RefactoringCoreMessages.IntroduceParameterRefactoring_expression_in_method);
			}

			fMethod= (IMethod) enclosingElement;
			pm.worked(1);

			RefactoringStatus result= new RefactoringStatus();
			if (fArguments != null) {
				// invoked by script
				fChangeSignatureProcessor= new ChangeSignatureProcessor(fArguments, result);
				if (!result.hasFatalError()) {
					fChangeSignatureRefactoring= new ProcessorBasedRefactoring(fChangeSignatureProcessor);
					fChangeSignatureRefactoring.setValidationContext(getValidationContext());
					result.merge(fChangeSignatureProcessor.checkInitialConditions(new SubProgressMonitor(pm, 2)));
					if (result.hasFatalError()) {
						return result;
					}
				} else {
					pm.worked(2);
					return result;
				}
			} else {
				// first try:
				fChangeSignatureProcessor= RefactoringAvailabilityTester.isChangeSignatureAvailable(fMethod) ? new ChangeSignatureProcessor(fMethod) : null;
				if (fChangeSignatureProcessor == null) {
					return RefactoringStatus.createFatalErrorStatus(RefactoringCoreMessages.IntroduceParameterRefactoring_expression_in_method);
				}
				fChangeSignatureRefactoring= new ProcessorBasedRefactoring(fChangeSignatureProcessor);
				fChangeSignatureRefactoring.setValidationContext(getValidationContext());
				result.merge(fChangeSignatureProcessor.checkInitialConditions(new SubProgressMonitor(pm, 1)));
				if (result.hasFatalError()) {
					RefactoringStatusEntry entry= result.getEntryMatchingSeverity(RefactoringStatus.FATAL);
					if (entry.getCode() == RefactoringStatusCodes.OVERRIDES_ANOTHER_METHOD || entry.getCode() == RefactoringStatusCodes.METHOD_DECLARED_IN_INTERFACE) {
						// second try:
						IMethod method= (IMethod) entry.getData();
						fChangeSignatureProcessor= RefactoringAvailabilityTester.isChangeSignatureAvailable(method) ? new ChangeSignatureProcessor(method) : null;
						if (fChangeSignatureProcessor == null) {
							String msg= Messages.format(RefactoringCoreMessages.IntroduceParameterRefactoring_cannot_introduce, entry.getMessage());
							return RefactoringStatus.createFatalErrorStatus(msg);
						}
						fChangeSignatureRefactoring= new ProcessorBasedRefactoring(fChangeSignatureProcessor);
						fChangeSignatureRefactoring.setValidationContext(getValidationContext());
						result= fChangeSignatureProcessor.checkInitialConditions(new SubProgressMonitor(pm, 1));
						if (result.hasFatalError()) {
							return result;
						}
					} else {
						return result;
					}
				} else {
					pm.worked(1);
				}
			}

			CompilationUnitRewrite cuRewrite= fChangeSignatureProcessor.getBaseCuRewrite();
			if (! cuRewrite.getCu().equals(fSourceCU))
			 {
				cuRewrite= new CompilationUnitRewrite(fSourceCU); // TODO: should try to avoid throwing away this AST
			}

			initializeSelectedExpression(cuRewrite);
			pm.worked(1);

			result.merge(checkSelection(cuRewrite, new SubProgressMonitor(pm, 3)));
			if (result.hasFatalError()) {
				return result;
			}

			initializeExcludedParameterNames(cuRewrite);

			addParameterInfo(cuRewrite);

			fChangeSignatureProcessor.setBodyUpdater(new BodyUpdater() {
				@Override
				public void updateBody(MethodDeclaration methodDeclaration, CompilationUnitRewrite rewrite, RefactoringStatus updaterResult) {
					replaceSelectedExpression(rewrite);
				}
			});

			return result;
		} finally {
			pm.done();
			if (fChangeSignatureRefactoring != null) {
				fChangeSignatureRefactoring.setValidationContext(null);
			}
		}
	}

	private IJavaElement resolveEnclosingElement(ICompilationUnit input, int offset, int length) throws JavaModelException {
		IJavaElement atOffset = null;
		if (input instanceof ICompilationUnit) {
			ICompilationUnit cunit = input;
			JavaModelUtil.reconcile(cunit);
			atOffset = cunit.getElementAt(offset);
		} else if (input instanceof IClassFile) {
			IClassFile cfile = (IClassFile) input;
			atOffset = cfile.getElementAt(offset);
		} else {
			return null;
		}
		if (atOffset == null) {
			return input;
		} else {
			int selectionEnd = offset + length;
			IJavaElement result = atOffset;
			if (atOffset instanceof ISourceReference) {
				ISourceRange range = ((ISourceReference) atOffset).getSourceRange();
				while (range.getOffset() + range.getLength() < selectionEnd) {
					result = result.getParent();
					if (!(result instanceof ISourceReference)) {
						result = input;
						break;
					}
					range = ((ISourceReference) result).getSourceRange();
				}
			}
			return result;
		}
	}

	private void addParameterInfo(CompilationUnitRewrite cuRewrite) throws JavaModelException {
		ITypeBinding typeBinding= Bindings.normalizeForDeclarationUse(fSelectedExpression.resolveTypeBinding(), fSelectedExpression.getAST());
		String name= fParameterName != null ? fParameterName : guessedParameterName();
		Expression expression= ASTNodes.getUnparenthesedExpression(fSelectedExpression);

		ImportRewrite importRewrite= cuRewrite.getImportRewrite();
		ImportRewriteContext importRewriteContext= new ContextSensitiveImportRewriteContext(fSelectedExpression, importRewrite);
		String typeName= importRewrite.addImport(typeBinding, importRewriteContext);

		String defaultValue= null;
		if (expression instanceof ClassInstanceCreation && typeBinding.isParameterizedType()) {
			ClassInstanceCreation classInstanceCreation= (ClassInstanceCreation) expression;
			Type cicType= classInstanceCreation.getType();
			if (cicType instanceof ParameterizedType && ((ParameterizedType) cicType).typeArguments().size() == 0) {
				// expand the diamond:
				AST ast= cuRewrite.getAST();
				Type type= importRewrite.addImport(typeBinding, ast, importRewriteContext);
				classInstanceCreation.setType(type);    // Should not touch the original AST ...
				Map<String, String> settings = FormatterHandler.getCombinedDefaultFormatterSettings();
				defaultValue= ASTNodes.asFormattedString(classInstanceCreation, 0, StubUtility.getLineDelimiterUsed(cuRewrite.getCu()),
						settings /* FormatterProfileManager.getProjectSettings(cuRewrite.getCu().getJavaProject()) */);
				classInstanceCreation.setType(cicType); // ... so let's restore it right away.
			}
		}

		if (defaultValue == null) {
			defaultValue= fSourceCU.getBuffer().getText(expression.getStartPosition(), expression.getLength());
		}
		fParameter= ParameterInfo.createInfoForAddedParameter(typeBinding, typeName, name, defaultValue);
		if (fArguments == null) {
			List<ParameterInfo> parameterInfos= fChangeSignatureProcessor.getParameterInfos();
			int parametersCount= parameterInfos.size();
			if (parametersCount > 0 && parameterInfos.get(parametersCount - 1).isOldVarargs()) {
				parameterInfos.add(parametersCount - 1, fParameter);
			} else {
				parameterInfos.add(fParameter);
			}
		}
	}

	private void replaceSelectedExpression(CompilationUnitRewrite cuRewrite) {
		if (! fSourceCU.equals(cuRewrite.getCu()))
		 {
			return;
		// TODO: do for all methodDeclarations and replace matching fragments?
		}

		// cannot use fSelectedExpression here, since it could be from another AST (if method was replaced by overridden):
		Expression expression= (Expression) NodeFinder.perform(cuRewrite.getRoot(), fSelectedExpression.getStartPosition(), fSelectedExpression.getLength());

		ASTNode newExpression= cuRewrite.getRoot().getAST().newSimpleName(fParameter.getNewName());
		String description= RefactoringCoreMessages.IntroduceParameterRefactoring_replace;
		cuRewrite.getASTRewrite().replace(expression.getParent() instanceof ParenthesizedExpression
				? expression.getParent() : expression, newExpression, cuRewrite.createGroupDescription(description));
		if (fLinkedProposalModel != null) {
			LinkedProposalPositionGroupCore nameGroup = fLinkedProposalModel.getPositionGroup(fParameter.getNewName(), true);
			nameGroup.addPosition(cuRewrite.getASTRewrite().track(newExpression), false);
		}
	}

	private void initializeSelectedExpression(CompilationUnitRewrite cuRewrite) throws JavaModelException {
		IASTFragment fragment= ASTFragmentFactory.createFragmentForSourceRange(
				new SourceRange(fSelectionStart, fSelectionLength), cuRewrite.getRoot(), cuRewrite.getCu());

		if (! (fragment instanceof IExpressionFragment)) {
			return;
		}

		//TODO: doesn't handle selection of partial Expressions
		Expression expression= ((IExpressionFragment) fragment).getAssociatedExpression();
		if (fragment.getStartPosition() != expression.getStartPosition()
				|| fragment.getLength() != expression.getLength()) {
			return;
		}

		if (Checks.isInsideJavadoc(expression)) {
			return;
		}
		//TODO: exclude invalid selections
		if (Checks.isEnumCase(expression.getParent())) {
			return;
		}

		fSelectedExpression= expression;
	}

	private RefactoringStatus checkSelection(CompilationUnitRewrite cuRewrite, IProgressMonitor pm) {
		try {
			if (fSelectedExpression == null){
				String message= RefactoringCoreMessages.IntroduceParameterRefactoring_select;
				return CodeRefactoringUtil.checkMethodSyntaxErrors(fSelectionStart, fSelectionLength, cuRewrite.getRoot(), message);
			}

			MethodDeclaration methodDeclaration= ASTNodes.getParent(fSelectedExpression, MethodDeclaration.class);
			if (methodDeclaration == null || ASTNodes.getParent(fSelectedExpression, Annotation.class) != null) {
				return RefactoringStatus.createFatalErrorStatus(RefactoringCoreMessages.IntroduceParameterRefactoring_expression_in_method);
			}
			if (methodDeclaration.resolveBinding() == null)
			 {
				return RefactoringStatus.createFatalErrorStatus(RefactoringCoreMessages.IntroduceParameterRefactoring_no_binding);
			//TODO: check for rippleMethods -> find matching fragments, consider callers of all rippleMethods
			}

			RefactoringStatus result= new RefactoringStatus();
			result.merge(checkExpression());
			if (result.hasFatalError()) {
				return result;
			}

			result.merge(checkExpressionBinding());
			if (result.hasFatalError()) {
				return result;
			}

//			if (isUsedInForInitializerOrUpdater(getSelectedExpression().getAssociatedExpression()))
//				return RefactoringStatus.createFatalErrorStatus(RefactoringCoreMessages.getString("ExtractTempRefactoring.for_initializer_updater")); //$NON-NLS-1$
//			pm.worked(1);
//
//			if (isReferringToLocalVariableFromFor(getSelectedExpression().getAssociatedExpression()))
//				return RefactoringStatus.createFatalErrorStatus(RefactoringCoreMessages.getString("ExtractTempRefactoring.refers_to_for_variable")); //$NON-NLS-1$
//			pm.worked(1);

			return result;
		} finally {
			if (pm != null) {
				pm.done();
			}
		}
	}

	private RefactoringStatus checkExpression() {
		//TODO: adjust error messages (or generalize for all refactorings on expression-selections?)
		Expression selectedExpression= fSelectedExpression;

		if (selectedExpression instanceof Name && selectedExpression.getLocationInParent() == ClassInstanceCreation.TYPE_PROPERTY)
		 {
			return RefactoringStatus.createFatalErrorStatus(RefactoringCoreMessages.ExtractTempRefactoring_name_in_new);
			//TODO: let's just take the CIC automatically (no ambiguity -> no problem -> no dialog ;-)
		}

		if (selectedExpression instanceof NullLiteral) {
			return RefactoringStatus.createFatalErrorStatus(RefactoringCoreMessages.ExtractTempRefactoring_null_literals);
		} else if (selectedExpression instanceof ArrayInitializer) {
			return RefactoringStatus.createFatalErrorStatus(RefactoringCoreMessages.ExtractTempRefactoring_array_initializer);
		} else if (selectedExpression instanceof Assignment) {
			if (selectedExpression.getParent() instanceof Expression) {
				return RefactoringStatus.createFatalErrorStatus(RefactoringCoreMessages.ExtractTempRefactoring_assignment);
			} else {
				return null;
			}

		} else if (selectedExpression instanceof SimpleName){
			if ((((SimpleName)selectedExpression)).isDeclaration()) {
				return RefactoringStatus.createFatalErrorStatus(RefactoringCoreMessages.ExtractTempRefactoring_names_in_declarations);
			}
			if (selectedExpression.getParent() instanceof QualifiedName && selectedExpression.getLocationInParent() == QualifiedName.NAME_PROPERTY
					|| selectedExpression.getParent() instanceof FieldAccess && selectedExpression.getLocationInParent() == FieldAccess.NAME_PROPERTY) {
				return RefactoringStatus.createFatalErrorStatus(RefactoringCoreMessages.ExtractTempRefactoring_select_expression);
			}
		}

		return null;
	}

	private RefactoringStatus checkExpressionBinding() {
		return checkExpressionFragmentIsRValue();
	}

	// !! +/- same as in ExtractConstantRefactoring & ExtractTempRefactoring
	private RefactoringStatus checkExpressionFragmentIsRValue() {
		switch(Checks.checkExpressionIsRValue(fSelectedExpression)) {
			case Checks.IS_RVALUE_GUESSED:
			case Checks.NOT_RVALUE_MISC:
				return RefactoringStatus.createStatus(RefactoringStatus.FATAL, RefactoringCoreMessages.IntroduceParameterRefactoring_select, null, CorextCore.getPluginId(), RefactoringStatusCodes.EXPRESSION_NOT_RVALUE, null);
			case Checks.NOT_RVALUE_VOID:
				return RefactoringStatus.createStatus(RefactoringStatus.FATAL, RefactoringCoreMessages.IntroduceParameterRefactoring_no_void, null, CorextCore.getPluginId(), RefactoringStatusCodes.EXPRESSION_NOT_RVALUE_VOID, null);
			case Checks.IS_RVALUE:
				return new RefactoringStatus();
			default:
				Assert.isTrue(false); return null;
		}
	}

	public List<ParameterInfo> getParameterInfos() {
		return fChangeSignatureProcessor.getParameterInfos();
	}

	public ParameterInfo getAddedParameterInfo() {
		return fParameter;
	}

	public String getMethodSignaturePreview() throws JavaModelException {
		return fChangeSignatureProcessor.getNewMethodSignature();
	}

//--- Input setting/validation

	public void setParameterName(String name) {
		Assert.isNotNull(name);
		fParameter.setNewName(name);
	}

	/**
	 * must only be called <i>after</i> checkActivation()
	 * @return guessed parameter name
	 */
	public String guessedParameterName() {
		String[] proposals= guessParameterNames();
		if (proposals.length == 0) {
			return ""; //$NON-NLS-1$
		} else {
			return proposals[0];
		}
	}

// --- TODO: copied from ExtractTempRefactoring - should extract ------------------------------

	/**
	 * Must only be called <i>after</i> checkActivation().
	 * The first proposal should be used as "best guess" (if it exists).
	 * @return proposed variable names (may be empty, but not null).
	 */
	public String[] guessParameterNames() {
		LinkedHashSet<String> proposals= new LinkedHashSet<>(); //retain ordering, but prevent duplicates
		if (fSelectedExpression instanceof MethodInvocation){
			proposals.addAll(guessTempNamesFromMethodInvocation((MethodInvocation) fSelectedExpression, fExcludedParameterNames));
		}
		proposals.addAll(guessTempNamesFromExpression(fSelectedExpression, fExcludedParameterNames));
		return proposals.toArray(new String[proposals.size()]);
	}

	private List<String> guessTempNamesFromMethodInvocation(MethodInvocation selectedMethodInvocation, String[] excludedVariableNames) {
		String methodName= selectedMethodInvocation.getName().getIdentifier();
		for (String prefix : KNOWN_METHOD_NAME_PREFIXES) {
			if (! methodName.startsWith(prefix))
			 {
				continue; //not this prefix
			}
			if (methodName.length() == prefix.length())
			 {
				return Collections.emptyList(); // prefix alone -> don't take method name
			}
			char firstAfterPrefix= methodName.charAt(prefix.length());
			if (! Character.isUpperCase(firstAfterPrefix))
			 {
				continue; //not uppercase after prefix
			}
			//found matching prefix
			String proposal= Character.toLowerCase(firstAfterPrefix) + methodName.substring(prefix.length() + 1);
			methodName= proposal;
			break;
		}
		String[] proposals= StubUtility.getLocalNameSuggestions(fSourceCU.getJavaProject(), methodName, 0, excludedVariableNames);
		return Arrays.asList(proposals);
	}

	private List<String> guessTempNamesFromExpression(Expression selectedExpression, String[] excluded) {
		ITypeBinding expressionBinding= Bindings.normalizeForDeclarationUse(
			selectedExpression.resolveTypeBinding(),
			selectedExpression.getAST());
		String typeName= getQualifiedName(expressionBinding);
		if (typeName.length() == 0) {
			typeName= expressionBinding.getName();
		}
		if (typeName.length() == 0) {
			return Collections.emptyList();
		}
		int typeParamStart= typeName.indexOf('<');
		if (typeParamStart != -1) {
			typeName= typeName.substring(0, typeParamStart);
		}
		String[] proposals= StubUtility.getLocalNameSuggestions(fSourceCU.getJavaProject(), typeName, expressionBinding.getDimensions(), excluded);
		return Arrays.asList(proposals);
	}

// ----------------------------------------------------------------------

	private static String getQualifiedName(ITypeBinding typeBinding) {
		if (typeBinding.isAnonymous()) {
			return getQualifiedName(typeBinding.getSuperclass());
		}
		if (! typeBinding.isArray()) {
			return typeBinding.getQualifiedName();
		} else {
			return typeBinding.getElementType().getQualifiedName();
		}
	}

	private void initializeExcludedParameterNames(CompilationUnitRewrite cuRewrite) {
		IBinding[] bindings= new ScopeAnalyzer(cuRewrite.getRoot()).getDeclarationsInScope(
				fSelectedExpression.getStartPosition(), ScopeAnalyzer.VARIABLES);
		fExcludedParameterNames= new String[bindings.length];
		for (int i= 0; i < fExcludedParameterNames.length; i++) {
			fExcludedParameterNames[i]= bindings[i].getName();
		}
	}

	public RefactoringStatus validateInput() {
		return fChangeSignatureProcessor.checkSignature();
	}

//--- checkInput

	@Override
	public RefactoringStatus checkFinalConditions(IProgressMonitor pm) throws CoreException {
		fChangeSignatureRefactoring.setValidationContext(getValidationContext());
		try {
			return fChangeSignatureRefactoring.checkFinalConditions(pm);
		} finally {
			fChangeSignatureRefactoring.setValidationContext(null);
		}
	}

	@Override
	public Change createChange(IProgressMonitor pm) throws CoreException {
		fChangeSignatureRefactoring.setValidationContext(getValidationContext());
		try {
			Change[] changes= fChangeSignatureProcessor.getAllChanges();
			if (changes.length < 1) {
				return null;
			}
			return changes[0];
		} finally {
			fChangeSignatureRefactoring.setValidationContext(null);
			pm.done();
		}
	}

	private IntroduceParameterDescriptor getRefactoringDescriptor() {
		ChangeMethodSignatureDescriptor extended= (ChangeMethodSignatureDescriptor) fChangeSignatureProcessor.createDescriptor();
		RefactoringContribution contribution = RefactoringCore.getRefactoringContribution(IConstants.CHANGE_METHOD_SIGNATURE);

		Map<String, String> argumentsMap= contribution.retrieveArgumentMap(extended);

		final Map<String, String> arguments= new HashMap<>();
		arguments.put(ATTRIBUTE_ARGUMENT, fParameter.getNewName());
		arguments.put(JavaRefactoringDescriptorUtil.ATTRIBUTE_SELECTION, Integer.toString(fSelectionStart) + " " + Integer.toString(fSelectionLength)); //$NON-NLS-1$
		arguments.putAll(argumentsMap);
		String signature= fChangeSignatureProcessor.getMethodName();
		try {
			signature= fChangeSignatureProcessor.getOldMethodSignature();
		} catch (JavaModelException exception) {
			JavaLanguageServerPlugin.log(exception);
		}
		final String description= Messages.format(RefactoringCoreMessages.IntroduceParameterRefactoring_descriptor_description_short, BasicElementLabels.getJavaElementName(fChangeSignatureProcessor.getMethod().getElementName()));
		final String header= Messages.format(RefactoringCoreMessages.IntroduceParameterRefactoring_descriptor_description, new String[] { BasicElementLabels.getJavaElementName(fParameter.getNewName()), signature, BasicElementLabels.getJavaCodeString(ASTNodes.asString(fSelectedExpression))});
		final JDTRefactoringDescriptorComment comment= new JDTRefactoringDescriptorComment(extended.getProject(), this, header);
		comment.addSetting(Messages.format(RefactoringCoreMessages.IntroduceParameterRefactoring_original_pattern, JavaElementLabels.getTextLabel(fChangeSignatureProcessor.getMethod(),
				JavaElementLabels.ALL_FULLY_QUALIFIED)));
		comment.addSetting(Messages.format(RefactoringCoreMessages.IntroduceParameterRefactoring_expression_pattern, BasicElementLabels.getJavaCodeString(ASTNodes.asString(fSelectedExpression))));
		comment.addSetting(Messages.format(RefactoringCoreMessages.IntroduceParameterRefactoring_parameter_pattern, BasicElementLabels.getJavaElementName(getAddedParameterInfo().getNewName())));
		return RefactoringSignatureDescriptorFactory.createIntroduceParameterDescriptor(extended.getProject(), description, comment.asString(), arguments, extended.getFlags());
	}

	private RefactoringStatus initialize(JavaRefactoringArguments arguments) {
		fArguments= arguments;
		final String selection= arguments.getAttribute(JavaRefactoringDescriptorUtil.ATTRIBUTE_SELECTION);
		if (selection != null) {
			int offset= -1;
			int length= -1;
			final StringTokenizer tokenizer= new StringTokenizer(selection);
			if (tokenizer.hasMoreTokens()) {
				offset= Integer.parseInt(tokenizer.nextToken());
			}
			if (tokenizer.hasMoreTokens()) {
				length= Integer.parseInt(tokenizer.nextToken());
			}
			if (offset >= 0 && length >= 0) {
				fSelectionStart= offset;
				fSelectionLength= length;
			} else {
				return RefactoringStatus.createFatalErrorStatus(Messages.format(RefactoringCoreMessages.InitializableRefactoring_illegal_argument, new Object[] { selection, JavaRefactoringDescriptorUtil.ATTRIBUTE_SELECTION}));
			}
		} else {
			return RefactoringStatus.createFatalErrorStatus(Messages.format(RefactoringCoreMessages.InitializableRefactoring_argument_not_exist, JavaRefactoringDescriptorUtil.ATTRIBUTE_SELECTION));
		}
		final String handle= arguments.getAttribute(JavaRefactoringDescriptorUtil.ATTRIBUTE_INPUT);
		if (handle != null) {
			final IJavaElement element= JavaRefactoringDescriptorUtil.handleToElement(arguments.getProject(), handle, false);
			if (element == null || !element.exists() || element.getElementType() != IJavaElement.COMPILATION_UNIT) {
				return JavaRefactoringDescriptorUtil.createInputFatalStatus(element, getName(), IJavaRefactorings.INTRODUCE_PARAMETER);
			} else {
				fSourceCU= (ICompilationUnit)element;
			}
		} else {
			return RefactoringStatus.createFatalErrorStatus(Messages.format(RefactoringCoreMessages.InitializableRefactoring_argument_not_exist, JavaRefactoringDescriptorUtil.ATTRIBUTE_INPUT));
		}
		final String name= arguments.getAttribute(ATTRIBUTE_ARGUMENT);
		if (name != null && !"".equals(name)) {
			fParameterName= name;
		} else {
			return RefactoringStatus.createFatalErrorStatus(Messages.format(RefactoringCoreMessages.InitializableRefactoring_argument_not_exist, ATTRIBUTE_ARGUMENT));
		}
		return new RefactoringStatus();
	}

	@Override
	public String getDelegateUpdatingTitle(boolean plural) {
		if (plural) {
			return RefactoringCoreMessages.DelegateCreator_keep_original_changed_plural;
		} else {
			return RefactoringCoreMessages.DelegateCreator_keep_original_changed_singular;
		}
	}

	public void setLinkedProposalModel(LinkedProposalModelCore linkedProposalModel) {
		fLinkedProposalModel = linkedProposalModel;
	}
}
