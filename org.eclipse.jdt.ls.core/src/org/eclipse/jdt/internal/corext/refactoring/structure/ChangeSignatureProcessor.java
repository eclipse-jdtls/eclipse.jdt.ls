/*******************************************************************************
 * Copyright (c) 2000, 2018 IBM Corporation and others.
 *
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.jdt.internal.corext.refactoring.structure;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.StringTokenizer;

import org.eclipse.core.runtime.Assert;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.core.runtime.OperationCanceledException;
import org.eclipse.core.runtime.SubProgressMonitor;

import org.eclipse.core.resources.IFile;

import org.eclipse.text.edits.TextEditGroup;

import org.eclipse.ltk.core.refactoring.Change;
import org.eclipse.ltk.core.refactoring.RefactoringDescriptor;
import org.eclipse.ltk.core.refactoring.RefactoringStatus;
import org.eclipse.ltk.core.refactoring.RefactoringStatusContext;
import org.eclipse.ltk.core.refactoring.RefactoringStatusEntry;
import org.eclipse.ltk.core.refactoring.TextChange;
import org.eclipse.ltk.core.refactoring.participants.CheckConditionsContext;
import org.eclipse.ltk.core.refactoring.participants.RefactoringParticipant;
import org.eclipse.ltk.core.refactoring.participants.RefactoringProcessor;
import org.eclipse.ltk.core.refactoring.participants.SharableParticipants;

import org.eclipse.jdt.core.Flags;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IMethod;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.ITypeHierarchy;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.Signature;
import org.eclipse.jdt.core.compiler.IProblem;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.AbstractTypeDeclaration;
import org.eclipse.jdt.core.dom.Block;
import org.eclipse.jdt.core.dom.BodyDeclaration;
import org.eclipse.jdt.core.dom.ClassInstanceCreation;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.ConstructorInvocation;
import org.eclipse.jdt.core.dom.Dimension;
import org.eclipse.jdt.core.dom.EnumConstantDeclaration;
import org.eclipse.jdt.core.dom.EnumDeclaration;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.ExpressionMethodReference;
import org.eclipse.jdt.core.dom.IMethodBinding;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.ImportDeclaration;
import org.eclipse.jdt.core.dom.Javadoc;
import org.eclipse.jdt.core.dom.LambdaExpression;
import org.eclipse.jdt.core.dom.MemberRef;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.MethodRef;
import org.eclipse.jdt.core.dom.MethodRefParameter;
import org.eclipse.jdt.core.dom.Modifier;
import org.eclipse.jdt.core.dom.Name;
import org.eclipse.jdt.core.dom.NodeFinder;
import org.eclipse.jdt.core.dom.PrimitiveType;
import org.eclipse.jdt.core.dom.QualifiedName;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.core.dom.SingleVariableDeclaration;
import org.eclipse.jdt.core.dom.SuperConstructorInvocation;
import org.eclipse.jdt.core.dom.SuperMethodInvocation;
import org.eclipse.jdt.core.dom.TagElement;
import org.eclipse.jdt.core.dom.TextElement;
import org.eclipse.jdt.core.dom.Type;
import org.eclipse.jdt.core.dom.VariableDeclaration;
import org.eclipse.jdt.core.dom.VariableDeclarationFragment;
import org.eclipse.jdt.core.dom.rewrite.ASTRewrite;
import org.eclipse.jdt.core.dom.rewrite.ImportRewrite;
import org.eclipse.jdt.core.dom.rewrite.ImportRewrite.ImportRewriteContext;
import org.eclipse.jdt.core.dom.rewrite.ListRewrite;
import org.eclipse.jdt.core.refactoring.IJavaRefactorings;
import org.eclipse.jdt.core.refactoring.descriptors.ChangeMethodSignatureDescriptor;
import org.eclipse.jdt.core.refactoring.descriptors.JavaRefactoringDescriptor;
import org.eclipse.jdt.core.refactoring.participants.ChangeMethodSignatureArguments;
import org.eclipse.jdt.core.refactoring.participants.ChangeMethodSignatureArguments.Parameter;
import org.eclipse.jdt.core.refactoring.participants.ChangeMethodSignatureArguments.ThrownException;
import org.eclipse.jdt.core.refactoring.participants.IRefactoringProcessorIds;
import org.eclipse.jdt.core.refactoring.participants.JavaParticipantManager;
import org.eclipse.jdt.core.search.IJavaSearchConstants;
import org.eclipse.jdt.core.search.IJavaSearchScope;
import org.eclipse.jdt.core.search.MethodReferenceMatch;
import org.eclipse.jdt.core.search.SearchEngine;
import org.eclipse.jdt.core.search.SearchMatch;
import org.eclipse.jdt.core.search.SearchPattern;

import org.eclipse.jdt.internal.core.manipulation.JavaElementLabelsCore;
import org.eclipse.jdt.internal.core.manipulation.JavaManipulationPlugin;
import org.eclipse.jdt.internal.core.manipulation.StubUtility;
import org.eclipse.jdt.internal.core.manipulation.util.BasicElementLabels;
import org.eclipse.jdt.internal.core.refactoring.descriptors.RefactoringSignatureDescriptorFactory;
import org.eclipse.jdt.internal.corext.CorextCore;
import org.eclipse.jdt.internal.corext.SourceRangeFactory;
import org.eclipse.jdt.internal.corext.codemanipulation.ContextSensitiveImportRewriteContext;
import org.eclipse.jdt.internal.corext.dom.ASTNodeFactory;
import org.eclipse.jdt.internal.corext.dom.ASTNodes;
import org.eclipse.jdt.internal.corext.dom.Bindings;
import org.eclipse.jdt.internal.corext.dom.IASTSharedValues;
import org.eclipse.jdt.internal.corext.dom.ModifierRewrite;
import org.eclipse.jdt.internal.corext.dom.Selection;
import org.eclipse.jdt.internal.corext.dom.SelectionAnalyzer;
import org.eclipse.jdt.internal.corext.refactoring.Checks;
import org.eclipse.jdt.internal.corext.refactoring.CuCollectingSearchRequestor;
import org.eclipse.jdt.internal.corext.refactoring.ExceptionInfo;
import org.eclipse.jdt.internal.corext.refactoring.JDTRefactoringDescriptorComment;
import org.eclipse.jdt.internal.corext.refactoring.JavaRefactoringArguments;
import org.eclipse.jdt.internal.corext.refactoring.JavaRefactoringDescriptorUtil;
import org.eclipse.jdt.internal.corext.refactoring.ParameterInfo;
import org.eclipse.jdt.internal.corext.refactoring.RefactoringCoreMessages;
import org.eclipse.jdt.internal.corext.refactoring.RefactoringScopeFactory;
import org.eclipse.jdt.internal.corext.refactoring.RefactoringSearchEngine;
import org.eclipse.jdt.internal.corext.refactoring.ReturnTypeInfo;
import org.eclipse.jdt.internal.corext.refactoring.SearchResultGroup;
import org.eclipse.jdt.internal.corext.refactoring.StubTypeContext;
import org.eclipse.jdt.internal.corext.refactoring.TypeContextChecker;
import org.eclipse.jdt.internal.corext.refactoring.base.JavaStringStatusContext;
import org.eclipse.jdt.internal.corext.refactoring.base.RefactoringStatusCodes;
import org.eclipse.jdt.internal.corext.refactoring.base.ReferencesInBinaryContext;
import org.eclipse.jdt.internal.corext.refactoring.changes.DynamicValidationRefactoringChange;
import org.eclipse.jdt.internal.corext.refactoring.code.Invocations;
import org.eclipse.jdt.internal.corext.refactoring.delegates.DelegateMethodCreator;
import org.eclipse.jdt.internal.corext.refactoring.participants.JavaProcessors;
import org.eclipse.jdt.internal.corext.refactoring.rename.MethodChecks;
import org.eclipse.jdt.internal.corext.refactoring.rename.RefactoringAnalyzeUtil;
import org.eclipse.jdt.internal.corext.refactoring.rename.RippleMethodFinder2;
import org.eclipse.jdt.internal.corext.refactoring.rename.TempOccurrenceAnalyzer;
import org.eclipse.jdt.internal.corext.refactoring.tagging.IDelegateUpdating;
import org.eclipse.jdt.internal.corext.refactoring.util.JavaElementUtil;
import org.eclipse.jdt.internal.corext.refactoring.util.JavaStatusContext;
import org.eclipse.jdt.internal.corext.refactoring.util.JavadocUtil;
import org.eclipse.jdt.internal.corext.refactoring.util.RefactoringASTParser;
import org.eclipse.jdt.internal.corext.refactoring.util.ResourceUtil;
import org.eclipse.jdt.internal.corext.refactoring.util.TextChangeManager;
import org.eclipse.jdt.internal.corext.refactoring.util.TightSourceRangeComputer;
import org.eclipse.jdt.internal.corext.util.JdtFlags;
import org.eclipse.jdt.internal.corext.util.Messages;
import org.eclipse.jdt.internal.corext.util.SearchUtils;
import org.eclipse.jdt.ls.core.internal.corext.refactoring.RefactoringAvailabilityTester;


public class ChangeSignatureProcessor extends RefactoringProcessor implements IDelegateUpdating {

	private static final String ATTRIBUTE_RETURN= "return"; //$NON-NLS-1$
	private static final String ATTRIBUTE_VISIBILITY= "visibility"; //$NON-NLS-1$
	private static final String ATTRIBUTE_PARAMETER= "parameter"; //$NON-NLS-1$
	private static final String ATTRIBUTE_DEFAULT= "default"; //$NON-NLS-1$
	private static final String ATTRIBUTE_KIND= "kind"; //$NON-NLS-1$
	private static final String ATTRIBUTE_DELEGATE= "delegate"; //$NON-NLS-1$
	private static final String ATTRIBUTE_DEPRECATE= "deprecate"; //$NON-NLS-1$

	private List<ParameterInfo> fParameterInfos;

	private CompilationUnitRewrite fBaseCuRewrite;
	private List<ExceptionInfo> fExceptionInfos;
	private TextChangeManager fChangeManager;

	private IMethod fMethod;
	private IMethod fTopMethod;
	private IMethod[] fRippleMethods;
	private SearchResultGroup[] fOccurrences;
	private ReturnTypeInfo fReturnTypeInfo;
	private String fMethodName;
	private int fVisibility;
	private static final String CONST_CLASS_DECL = "class A{";//$NON-NLS-1$
	private static final String CONST_ASSIGN = " i=";		//$NON-NLS-1$
	private static final String CONST_CLOSE = ";}";			//$NON-NLS-1$

	private StubTypeContext fContextCuStartEnd;
	private int fOldVarargIndex; // initialized in checkVarargs()

	private BodyUpdater fBodyUpdater;
	private IDefaultValueAdvisor fDefaultValueAdvisor;

	private ITypeHierarchy fCachedTypeHierarchy= null;
	private boolean fDelegateUpdating;
	private boolean fDelegateDeprecation;

	public ChangeSignatureProcessor(JavaRefactoringArguments arguments, RefactoringStatus status) throws JavaModelException {
		this((IMethod) null);
		status.merge(initialize(arguments));
	}

	/**
	 * Creates a new change signature refactoring.
	 * @param method the method, or <code>null</code> if invoked by scripting framework
	 * @throws JavaModelException if something's wrong with the given method
	 */
	public ChangeSignatureProcessor(IMethod method) throws JavaModelException {
		fMethod= method;
		fOldVarargIndex= -1;
		fDelegateUpdating= false;
		fDelegateDeprecation= true;
		if (fMethod != null) {
			fParameterInfos= createParameterInfoList(method);
			// fExceptionInfos is created in checkInitialConditions
			fReturnTypeInfo= new ReturnTypeInfo(Signature.toString(Signature.getReturnType(fMethod.getSignature())));
			fMethodName= fMethod.getElementName();
			fVisibility= JdtFlags.getVisibilityCode(fMethod);
		}
	}

	private static List<ParameterInfo> createParameterInfoList(IMethod method) {
		try {
			String[] typeNames= method.getParameterTypes();
			String[] oldNames= method.getParameterNames();
			List<ParameterInfo> result= new ArrayList<>(typeNames.length);
			for (int i= 0; i < oldNames.length; i++){
				ParameterInfo parameterInfo;
				if (i == oldNames.length - 1 && Flags.isVarargs(method.getFlags())) {
					String varargSignature= typeNames[i];
					int arrayCount= Signature.getArrayCount(varargSignature);
					String baseSignature= Signature.getElementType(varargSignature);
					if (arrayCount > 1)
						baseSignature= Signature.createArraySignature(baseSignature, arrayCount - 1);
					parameterInfo= new ParameterInfo(Signature.toString(baseSignature) + ParameterInfo.ELLIPSIS, oldNames[i], i);
				} else {
					parameterInfo= new ParameterInfo(Signature.toString(typeNames[i]), oldNames[i], i);
				}
				result.add(parameterInfo);
			}
			return result;
		} catch(JavaModelException e) {
			JavaManipulationPlugin.log(e);
			return new ArrayList<>(0);
		}
	}

	@Override
	public String getProcessorName() {
		return RefactoringCoreMessages.ChangeSignatureRefactoring_modify_Parameters;
	}

	public IMethod getMethod() {
		return fMethod;
	}

	public String getMethodName() {
		return fMethodName;
	}

	public String getReturnTypeString() {
		return fReturnTypeInfo.getNewTypeName();
	}

	public void setNewMethodName(String newMethodName){
		Assert.isNotNull(newMethodName);
		fMethodName= newMethodName;
	}

	public void setNewReturnTypeName(String newReturnTypeName){
		Assert.isNotNull(newReturnTypeName);
		fReturnTypeInfo.setNewTypeName(newReturnTypeName);
	}

	public boolean canChangeNameAndReturnType(){
		try {
			return ! fMethod.isConstructor();
		} catch (JavaModelException e) {
			JavaManipulationPlugin.log(e);
			return false;
		}
	}

	/**
	 * @return visibility
	 * @see org.eclipse.jdt.core.dom.Modifier
	 */
	public int getVisibility(){
		return fVisibility;
	}

	/**
	 * @param visibility new visibility
	 * @see org.eclipse.jdt.core.dom.Modifier
	 */
	public void setVisibility(int visibility){
		Assert.isTrue(	visibility == Modifier.PUBLIC ||
						visibility == Modifier.PROTECTED ||
						visibility == Modifier.NONE ||
						visibility == Modifier.PRIVATE);
		fVisibility= visibility;
	}

	/*
	 * @see JdtFlags
	 */
	public int[] getAvailableVisibilities() throws JavaModelException{
		if (fTopMethod.getDeclaringType().isInterface())
			return new int[]{Modifier.PUBLIC};
		else if (fTopMethod.getDeclaringType().isEnum() && fTopMethod.isConstructor())
			return new int[]{	Modifier.NONE,
								Modifier.PRIVATE};
		else
			return new int[]{	Modifier.PUBLIC,
								Modifier.PROTECTED,
								Modifier.NONE,
								Modifier.PRIVATE};
	}

	/**
	 *
	 * @return List of <code>ParameterInfo</code> objects.
	 */
	public List<ParameterInfo> getParameterInfos(){
		return fParameterInfos;
	}

	/**
	 * @return List of <code>ExceptionInfo</code> objects.
	 */
	public List<ExceptionInfo> getExceptionInfos(){
		return fExceptionInfos;
	}

	public void setBodyUpdater(BodyUpdater bodyUpdater) {
		fBodyUpdater= bodyUpdater;
	}

	public CompilationUnitRewrite getBaseCuRewrite() {
		return fBaseCuRewrite;
	}

	//------------------- IDelegateUpdating ----------------------

	@Override
	public boolean canEnableDelegateUpdating() {
		return true;
	}

	@Override
	public boolean getDelegateUpdating() {
		return fDelegateUpdating;
	}

	@Override
	public void setDelegateUpdating(boolean updating) {
		fDelegateUpdating= updating;
	}

	@Override
	public void setDeprecateDelegates(boolean deprecate) {
		fDelegateDeprecation= deprecate;
	}

	@Override
	public boolean getDeprecateDelegates() {
		return fDelegateDeprecation;
	}

	@Override
	public String getDelegateUpdatingTitle(boolean plural) {
		if (plural)
			return RefactoringCoreMessages.DelegateCreator_keep_original_changed_plural;
		else
			return RefactoringCoreMessages.DelegateCreator_keep_original_changed_singular;
	}

	//------------------- /IDelegateUpdating ---------------------

	public RefactoringStatus checkSignature() {
		return checkSignature(false);
	}

	private RefactoringStatus checkSignature(boolean resolveBindings) {
		RefactoringStatus result= new RefactoringStatus();
		checkMethodName(result);
		if (result.hasFatalError())
			return result;

		checkParameterNamesAndValues(result);
		if (result.hasFatalError())
			return result;

		checkForDuplicateParameterNames(result);
		if (result.hasFatalError())
			return result;

		try {
			RefactoringStatus[] typeStati;
			if (resolveBindings)
				typeStati= TypeContextChecker.checkAndResolveMethodTypes(fMethod, getStubTypeContext(), getNotDeletedInfos(), fReturnTypeInfo);
			else
				typeStati= TypeContextChecker.checkMethodTypesSyntax(fMethod, getNotDeletedInfos(), fReturnTypeInfo);
			for (RefactoringStatus status : typeStati) {
				result.merge(status);
			}

			result.merge(checkVarargs());
		} catch (CoreException e) {
			//cannot do anything here
			throw new RuntimeException(e);
		}

		//checkExceptions() unnecessary (IType always ok)
		return result;
	}

	public boolean isSignatureSameAsInitial() throws JavaModelException {
		if (! isVisibilitySameAsInitial())
			return false;
		if (! isMethodNameSameAsInitial())
			return false;
		if (! isReturnTypeSameAsInitial())
			return false;
		if (! areExceptionsSameAsInitial())
			return false;

		if (fMethod.getNumberOfParameters() == 0 && fParameterInfos.isEmpty())
			return true;

		if (areNamesSameAsInitial() && isOrderSameAsInitial() && areParameterTypesSameAsInitial())
			return true;

		return false;
	}

	/**
	 * @return true if the new method cannot coexist with the old method since
	 *         the signatures are too much alike
	 */
	public boolean isSignatureClashWithInitial() {

		if (!isMethodNameSameAsInitial())
			return false; // name has changed.

		if (fMethod.getNumberOfParameters() == 0 && fParameterInfos.isEmpty())
			return true; // name is equal and both parameter lists are empty

		// name is equal and there are some parameters.
		// check if there are more or less parameters than before

		int no= getNotDeletedInfos().size();

		if (fMethod.getNumberOfParameters() != no)
			return false;

		// name is equal and parameter count is equal.
		// check whether types remained the same

		if (isOrderSameAsInitial())
			return areParameterTypesSameAsInitial();
		else
			return false; // could be more specific here
	}

	private boolean areParameterTypesSameAsInitial() {
		for (ParameterInfo info : fParameterInfos) {
			if (! info.isAdded() && ! info.isDeleted() && info.isTypeNameChanged())
				return false;
		}
		return true;
	}

	private boolean isReturnTypeSameAsInitial() {
		return ! fReturnTypeInfo.isTypeNameChanged();
	}

	private boolean isMethodNameSameAsInitial() {
		return fMethodName.equals(fMethod.getElementName());
	}

	private boolean areExceptionsSameAsInitial() {
		for (ExceptionInfo info : fExceptionInfos) {
			if (! info.isOld())
				return false;
		}
		return true;
	}

	private void checkParameterNamesAndValues(RefactoringStatus result) {
		int i= 1;
		for (Iterator<ParameterInfo> iter= fParameterInfos.iterator(); iter.hasNext(); i++) {
			ParameterInfo info= iter.next();
			if (info.isDeleted())
				continue;
			checkParameterName(result, info, i);
			if (result.hasFatalError())
				return;
			if (info.isAdded())	{
				checkParameterDefaultValue(result, info);
				if (result.hasFatalError())
					return;
			}
		}
	}

	private void checkParameterName(RefactoringStatus result, ParameterInfo info, int position) {
		if (info.getNewName().trim().length() == 0) {
			result.addFatalError(Messages.format(
					RefactoringCoreMessages.ChangeSignatureRefactoring_param_name_not_empty, Integer.toString(position)));
		} else {
			result.merge(Checks.checkTempName(info.getNewName(), fMethod));
		}
	}

	private void checkMethodName(RefactoringStatus result) {
		if (isMethodNameSameAsInitial() || ! canChangeNameAndReturnType())
			return;
		if ("".equals(fMethodName.trim())) { //$NON-NLS-1$
			String msg= RefactoringCoreMessages.ChangeSignatureRefactoring_method_name_not_empty;
			result.addFatalError(msg);
			return;
		}
		if (fMethodName.equals(fMethod.getDeclaringType().getElementName())) {
			String msg= RefactoringCoreMessages.ChangeSignatureRefactoring_constructor_name;
			result.addWarning(msg);
		}
		result.merge(Checks.checkMethodName(fMethodName, fMethod));
	}

	private void checkParameterDefaultValue(RefactoringStatus result, ParameterInfo info) {
		if (fDefaultValueAdvisor != null)
			return;
		if (info.isNewVarargs()) {
			if (! isValidVarargsExpression(info.getDefaultValue())){
				String msg= Messages.format(RefactoringCoreMessages.ChangeSignatureRefactoring_invalid_expression, new String[]{info.getDefaultValue()});
				result.addFatalError(msg);
			}
			return;
		}

		if (info.getDefaultValue().trim().isEmpty()){
			String msg= Messages.format(RefactoringCoreMessages.ChangeSignatureRefactoring_default_value, BasicElementLabels.getJavaElementName(info.getNewName()));
			result.addFatalError(msg);
			return;
		}
		if (! isValidExpression(info.getDefaultValue())){
			String msg= Messages.format(RefactoringCoreMessages.ChangeSignatureRefactoring_invalid_expression, new String[]{info.getDefaultValue()});
			result.addFatalError(msg);
		}
	}

	private RefactoringStatus checkVarargs() throws JavaModelException {
		RefactoringStatus result= checkOriginalVarargs();
		if (result != null)
			return result;

		if (fRippleMethods != null) {
			for (IMethod rippleMethod : fRippleMethods) {
				if (! JdtFlags.isVarargs(rippleMethod))
					continue;

				// Vararg method can override method that takes an array as last argument
				fOldVarargIndex= rippleMethod.getNumberOfParameters() - 1;
				List<ParameterInfo> notDeletedInfos= getNotDeletedInfos();
				for (ParameterInfo info : notDeletedInfos) {
					if (fOldVarargIndex != -1 && info.getOldIndex() == fOldVarargIndex && ! info.isNewVarargs()) {
						String rippleMethodType= rippleMethod.getDeclaringType().getFullyQualifiedName('.');
						String message= Messages.format(RefactoringCoreMessages.ChangeSignatureRefactoring_ripple_cannot_convert_vararg, new Object[] { BasicElementLabels.getJavaElementName(info.getNewName()), BasicElementLabels.getJavaElementName(rippleMethodType)});
						return RefactoringStatus.createFatalErrorStatus(message, JavaStatusContext.create(rippleMethod));
					}
				}
			}
		}

		return null;
	}

	private RefactoringStatus checkOriginalVarargs() throws JavaModelException {
		if (JdtFlags.isVarargs(fMethod))
			fOldVarargIndex= fMethod.getNumberOfParameters() - 1;
		List<ParameterInfo> notDeletedInfos= getNotDeletedInfos();
		for (int i= 0; i < notDeletedInfos.size(); i++) {
			ParameterInfo info= notDeletedInfos.get(i);
			if (info.isOldVarargs() && ! info.isNewVarargs())
				return RefactoringStatus.createFatalErrorStatus(Messages.format(RefactoringCoreMessages.ChangeSignatureRefactoring_cannot_convert_vararg, BasicElementLabels.getJavaElementName(info.getNewName())));
			if (i != notDeletedInfos.size() - 1) {
				// not the last parameter
				if (info.isNewVarargs())
					return RefactoringStatus.createFatalErrorStatus(Messages.format(RefactoringCoreMessages.ChangeSignatureRefactoring_vararg_must_be_last, BasicElementLabels.getJavaElementName(info.getNewName())));
			}
		}
		return null;
	}

	private RefactoringStatus checkTypeVariables() {
		if (fRippleMethods.length == 1)
			return null;

		RefactoringStatus result= new RefactoringStatus();
		if (fReturnTypeInfo.isTypeNameChanged() && fReturnTypeInfo.getNewTypeBinding() != null) {
			HashSet<ITypeBinding> typeVariablesCollector= new HashSet<>();
			collectTypeVariables(fReturnTypeInfo.getNewTypeBinding(), typeVariablesCollector);
			if (!typeVariablesCollector.isEmpty()) {
				ITypeBinding first= typeVariablesCollector.iterator().next();
				String msg= Messages.format(RefactoringCoreMessages.ChangeSignatureRefactoring_return_type_contains_type_variable, new String[] {BasicElementLabels.getJavaElementName(fReturnTypeInfo.getNewTypeName()), BasicElementLabels.getJavaElementName(first.getName())});
				result.addError(msg);
			}
		}

		for (ParameterInfo info : getNotDeletedInfos()) {
			if (info.isTypeNameChanged() && info.getNewTypeBinding() != null) {
				HashSet<ITypeBinding> typeVariablesCollector= new HashSet<>();
				collectTypeVariables(info.getNewTypeBinding(), typeVariablesCollector);
				if (!typeVariablesCollector.isEmpty()) {
					ITypeBinding first= typeVariablesCollector.iterator().next();
					String msg= Messages.format(RefactoringCoreMessages.ChangeSignatureRefactoring_parameter_type_contains_type_variable, new String[] {BasicElementLabels.getJavaElementName(info.getNewTypeName()), BasicElementLabels.getJavaElementName(info.getNewName()), BasicElementLabels.getJavaElementName(first.getName())});
					result.addError(msg);
				}
			}
		}
		return result;
	}

	private void collectTypeVariables(ITypeBinding typeBinding, Set<ITypeBinding> typeVariablesCollector) {
		if (typeBinding.isTypeVariable()) {
			typeVariablesCollector.add(typeBinding);
			for (ITypeBinding typeBound : typeBinding.getTypeBounds()) {
				collectTypeVariables(typeBound, typeVariablesCollector);
			}

		} else if (typeBinding.isArray()) {
			collectTypeVariables(typeBinding.getElementType(), typeVariablesCollector);

		} else if (typeBinding.isParameterizedType()) {
			for (ITypeBinding typeArgument : typeBinding.getTypeArguments()) {
				collectTypeVariables(typeArgument, typeVariablesCollector);
			}

		} else if (typeBinding.isWildcardType()) {
			ITypeBinding bound= typeBinding.getBound();
			if (bound != null) {
				collectTypeVariables(bound, typeVariablesCollector);
			}
		}
	}

	public static boolean isValidExpression(String string){
		String trimmed= string.trim();
		if ("".equals(trimmed)) //speed up for a common case //$NON-NLS-1$
			return false;
		StringBuilder cuBuff= new StringBuilder();
		cuBuff.append(CONST_CLASS_DECL)
			  .append("Object") //$NON-NLS-1$
			  .append(CONST_ASSIGN);
		int offset= cuBuff.length();
		cuBuff.append(trimmed)
			  .append(CONST_CLOSE);
		ASTParser p= ASTParser.newParser(IASTSharedValues.SHARED_AST_LEVEL);
		p.setSource(cuBuff.toString().toCharArray());
		CompilationUnit cu= (CompilationUnit) p.createAST(null);
		Selection selection= Selection.createFromStartLength(offset, trimmed.length());
		SelectionAnalyzer analyzer= new SelectionAnalyzer(selection, false);
		cu.accept(analyzer);
		ASTNode selected= analyzer.getFirstSelectedNode();
		return (selected instanceof Expression) &&
				trimmed.equals(cuBuff.substring(cu.getExtendedStartPosition(selected), cu.getExtendedStartPosition(selected) + cu.getExtendedLength(selected)));
	}

	public static boolean isValidVarargsExpression(String string) {
		String trimmed= string.trim();
		if ("".equals(trimmed)) //speed up for a common case //$NON-NLS-1$
			return true;
		StringBuilder cuBuff= new StringBuilder();
		cuBuff.append("class A{ {m("); //$NON-NLS-1$
		int offset= cuBuff.length();
		cuBuff.append(trimmed)
			  .append(");}}"); //$NON-NLS-1$
		ASTParser p= ASTParser.newParser(IASTSharedValues.SHARED_AST_LEVEL);
		p.setSource(cuBuff.toString().toCharArray());
		CompilationUnit cu= (CompilationUnit) p.createAST(null);
		Selection selection= Selection.createFromStartLength(offset, trimmed.length());
		SelectionAnalyzer analyzer= new SelectionAnalyzer(selection, false);
		cu.accept(analyzer);
		ASTNode[] selectedNodes= analyzer.getSelectedNodes();
		if (selectedNodes.length == 0)
			return false;
		for (ASTNode selectedNode : selectedNodes) {
			if (!(selectedNode instanceof Expression)) {
				return false;
			}
		}
		return true;
	}

	public StubTypeContext getStubTypeContext() {
		try {
			if (fContextCuStartEnd == null)
				fContextCuStartEnd= TypeContextChecker.createStubTypeContext(getCu(), fBaseCuRewrite.getRoot(), fMethod.getSourceRange().getOffset());
		} catch (CoreException e) {
			//cannot do anything here
			throw new RuntimeException(e);
		}
		return fContextCuStartEnd;
	}

	private ITypeHierarchy getCachedTypeHierarchy(IProgressMonitor monitor) throws JavaModelException {
		if (fCachedTypeHierarchy == null)
			fCachedTypeHierarchy= fMethod.getDeclaringType().newTypeHierarchy(new SubProgressMonitor(monitor, 1));
		return fCachedTypeHierarchy;
	}

	@Override
	public RefactoringStatus checkInitialConditions(IProgressMonitor monitor) throws CoreException {
		try {
			monitor.beginTask("", 5); //$NON-NLS-1$
			RefactoringStatus result= Checks.checkIfCuBroken(fMethod);
			if (result.hasFatalError())
				return result;
			if (fMethod == null || !fMethod.exists()) {
				String message= Messages.format(RefactoringCoreMessages.ChangeSignatureRefactoring_method_deleted, BasicElementLabels.getFileName(getCu()));
				return RefactoringStatus.createFatalErrorStatus(message);
			}
			if (fMethod.getDeclaringType().isInterface()) {
				fTopMethod= MethodChecks.overridesAnotherMethod(fMethod, fMethod.getDeclaringType().newSupertypeHierarchy(new SubProgressMonitor(monitor, 1)));
				monitor.worked(1);
			} else if (MethodChecks.isVirtual(fMethod)) {
				ITypeHierarchy hierarchy= getCachedTypeHierarchy(new SubProgressMonitor(monitor, 1));
				fTopMethod= MethodChecks.isDeclaredInInterface(fMethod, hierarchy, new SubProgressMonitor(monitor, 1));
				if (fTopMethod == null)
					fTopMethod= MethodChecks.overridesAnotherMethod(fMethod, hierarchy);
			}
			if (fTopMethod == null)
				fTopMethod= fMethod;
			if (! fTopMethod.equals(fMethod)) {
				if (fTopMethod.getDeclaringType().isInterface()) {
					RefactoringStatusContext context= JavaStatusContext.create(fTopMethod);
					String message= Messages.format(RefactoringCoreMessages.MethodChecks_implements,
							new String[]{JavaElementUtil.createMethodSignature(fTopMethod), BasicElementLabels.getJavaElementName(fTopMethod.getDeclaringType().getFullyQualifiedName('.'))});
					return RefactoringStatus.createStatus(RefactoringStatus.FATAL, message, context, CorextCore.getPluginId(), RefactoringStatusCodes.METHOD_DECLARED_IN_INTERFACE, fTopMethod);
				} else {
					RefactoringStatusContext context= JavaStatusContext.create(fTopMethod);
					String message= Messages.format(RefactoringCoreMessages.MethodChecks_overrides,
							new String[]{JavaElementUtil.createMethodSignature(fTopMethod), BasicElementLabels.getJavaElementName(fTopMethod.getDeclaringType().getFullyQualifiedName('.'))});
					return RefactoringStatus.createStatus(RefactoringStatus.FATAL, message, context, CorextCore.getPluginId(), RefactoringStatusCodes.OVERRIDES_ANOTHER_METHOD, fTopMethod);
				}
			}

			if (monitor.isCanceled())
				throw new OperationCanceledException();

			if (fBaseCuRewrite == null || !fBaseCuRewrite.getCu().equals(getCu())) {
				fBaseCuRewrite= new CompilationUnitRewrite(getCu());
				fBaseCuRewrite.getASTRewrite().setTargetSourceRangeComputer(new TightSourceRangeComputer());
			}
			for (RefactoringStatus status : TypeContextChecker.checkMethodTypesSyntax(fMethod, getParameterInfos(), fReturnTypeInfo)) {
				result.merge(status);
			}
			monitor.worked(1);
			result.merge(createExceptionInfoList());
			monitor.worked(1);
			return result;
		} finally {
			monitor.done();
		}
	}

	private RefactoringStatus createExceptionInfoList() {
		if (fExceptionInfos == null || fExceptionInfos.isEmpty()) {
			fExceptionInfos= new ArrayList<>(0);
			try {
				ASTNode nameNode= NodeFinder.perform(fBaseCuRewrite.getRoot(), fMethod.getNameRange());
				if (nameNode == null || !(nameNode instanceof Name) || !(nameNode.getParent() instanceof MethodDeclaration))
					return null;
				MethodDeclaration methodDeclaration= (MethodDeclaration) nameNode.getParent();
				List<Type> exceptions= methodDeclaration.thrownExceptionTypes();
				List<ExceptionInfo> result= new ArrayList<>(exceptions.size());
				for (Type type : exceptions) {
					ITypeBinding typeBinding= type.resolveBinding();
					if (typeBinding == null)
						return RefactoringStatus.createFatalErrorStatus(RefactoringCoreMessages.ChangeSignatureRefactoring_no_exception_binding);
					IJavaElement element= typeBinding.getJavaElement();
					result.add(ExceptionInfo.createInfoForOldException(element, typeBinding));
				}
				fExceptionInfos= result;
			} catch (JavaModelException e) {
				JavaManipulationPlugin.log(e);
			}
		}
		return null;
	}

	@Override
	public RefactoringStatus checkFinalConditions(IProgressMonitor pm, CheckConditionsContext context) throws CoreException, OperationCanceledException {
		try {
			pm.beginTask(RefactoringCoreMessages.ChangeSignatureRefactoring_checking_preconditions, 8);
			RefactoringStatus result= new RefactoringStatus();
			clearManagers();
			fBaseCuRewrite.clearASTAndImportRewrites();
			fBaseCuRewrite.getASTRewrite().setTargetSourceRangeComputer(new TightSourceRangeComputer());

			if (isSignatureSameAsInitial())
				return RefactoringStatus.createFatalErrorStatus(RefactoringCoreMessages.ChangeSignatureRefactoring_unchanged);
			result.merge(checkSignature(true));
			if (result.hasFatalError())
				return result;

			if (fDelegateUpdating && isSignatureClashWithInitial())
				result.merge(RefactoringStatus.createErrorStatus(RefactoringCoreMessages.ChangeSignatureRefactoring_old_and_new_signatures_not_sufficiently_different ));

			String binaryRefsDescription= Messages.format(RefactoringCoreMessages.ReferencesInBinaryContext_ref_in_binaries_description , BasicElementLabels.getJavaElementName(getMethodName()));
			ReferencesInBinaryContext binaryRefs= new ReferencesInBinaryContext(binaryRefsDescription);

			fRippleMethods= RippleMethodFinder2.getRelatedMethods(fMethod, binaryRefs, new SubProgressMonitor(pm, 1), null);
			result.merge(checkVarargs());
			if (result.hasFatalError())
				return result;

			fOccurrences= findOccurrences(new SubProgressMonitor(pm, 1), binaryRefs, result);
			binaryRefs.addErrorIfNecessary(result);

			result.merge(checkVisibilityChanges());
			result.merge(checkTypeVariables());

			//TODO:
			// We need a common way of dealing with possible compilation errors for all occurrences,
			// including visibility problems, shadowing and missing throws declarations.

			if (! isOrderSameAsInitial())
				result.merge(checkReorderings(new SubProgressMonitor(pm, 1)));
			else
				pm.worked(1);

			//TODO (bug 58616): check whether changed signature already exists somewhere in the ripple,
			// - error if exists
			// - warn if exists with different parameter types (may cause overloading)

			if (! areNamesSameAsInitial())
				result.merge(checkRenamings(new SubProgressMonitor(pm, 1)));
			else
				pm.worked(1);
			if (result.hasFatalError())
				return result;

//			resolveTypesWithoutBindings(new SubProgressMonitor(pm, 1)); // already done in checkSignature(true)

			createChangeManager(new SubProgressMonitor(pm, 1), result);
			fCachedTypeHierarchy= null;

			if (mustAnalyzeAstOfDeclaringCu())
				result.merge(checkCompilationofDeclaringCu()); //TODO: should also check in ripple methods (move into createChangeManager)
			if (result.hasFatalError())
				return result;

			Checks.addModifiedFilesToChecker(getAllFilesToModify(), context);
			return result;
		} finally {
			pm.done();
		}
	}

	protected void clearManagers() {
		fChangeManager= null;
	}

	private RefactoringStatus checkVisibilityChanges() throws JavaModelException {
		if (isVisibilitySameAsInitial())
			return null;
		if (fRippleMethods.length == 1)
			return null;
		Assert.isTrue(JdtFlags.getVisibilityCode(fMethod) != Modifier.PRIVATE);
		if (fVisibility == Modifier.PRIVATE)
			return RefactoringStatus.createWarningStatus(RefactoringCoreMessages.ChangeSignatureRefactoring_non_virtual);
		return null;
	}

	public String getOldMethodSignature() throws JavaModelException{
		StringBuilder buff= new StringBuilder();

		int flags= getMethod().getFlags();
		buff.append(getVisibilityString(flags));
		if (Flags.isStatic(flags)) {
			buff.append("static "); //$NON-NLS-1$
		} else if (Flags.isDefaultMethod(flags)) {
			buff.append("default "); //$NON-NLS-1$
		}
		if (! getMethod().isConstructor())
			buff.append(fReturnTypeInfo.getOldTypeName())
				.append(' ');

		buff.append(JavaElementLabelsCore.getElementLabel(fMethod.getParent(), JavaElementLabelsCore.ALL_FULLY_QUALIFIED));
		buff.append('.');
		buff.append(fMethod.getElementName())
			.append(Signature.C_PARAM_START)
			.append(getOldMethodParameters())
			.append(Signature.C_PARAM_END);

		buff.append(getOldMethodThrows());

		return BasicElementLabels.getJavaCodeString(buff.toString());
	}

	public String getNewMethodSignature() throws JavaModelException{
		StringBuilder buff= new StringBuilder();

		buff.append(getVisibilityString(fVisibility));
		int flags= getMethod().getFlags();
		if (Flags.isStatic(flags)) {
			buff.append("static "); //$NON-NLS-1$
		} else if (Flags.isDefaultMethod(flags)) {
			buff.append("default "); //$NON-NLS-1$
		}
		if (! getMethod().isConstructor())
			buff.append(getReturnTypeString())
				.append(' ');

		buff.append(getMethodName())
			.append(Signature.C_PARAM_START)
			.append(getMethodParameters())
			.append(Signature.C_PARAM_END);

		buff.append(getMethodThrows());

		return BasicElementLabels.getJavaCodeString(buff.toString());
	}

	private String getVisibilityString(int visibility) {
		String visibilityString= JdtFlags.getVisibilityString(visibility);
		if ("".equals(visibilityString)) //$NON-NLS-1$
			return visibilityString;
		return visibilityString + ' ';
	}

	private String getMethodThrows() {
		final String throwsString= " throws "; //$NON-NLS-1$
		StringBuilder buff= new StringBuilder(throwsString);
		for (ExceptionInfo info : fExceptionInfos) {
			if (! info.isDeleted()) {
				buff.append(info.getElement().getElementName());
				buff.append(", "); //$NON-NLS-1$
			}
		}
		if (buff.length() == throwsString.length())
			return ""; //$NON-NLS-1$
		buff.delete(buff.length() - 2, buff.length());
		return buff.toString();
	}

	private String getOldMethodThrows() {
		final String throwsString= " throws "; //$NON-NLS-1$
		StringBuilder buff= new StringBuilder(throwsString);
		for (ExceptionInfo info : fExceptionInfos) {
			if (! info.isAdded()) {
				buff.append(info.getElement().getElementName());
				buff.append(", "); //$NON-NLS-1$
			}
		}
		if (buff.length() == throwsString.length())
			return ""; //$NON-NLS-1$
		buff.delete(buff.length() - 2, buff.length());
		return buff.toString();
	}

	private void checkForDuplicateParameterNames(RefactoringStatus result){
		Set<String> found= new HashSet<>();
		Set<String> doubled= new HashSet<>();
		for (ParameterInfo info : getNotDeletedInfos()) {
			String newName= info.getNewName();
			if (found.contains(newName) && !doubled.contains(newName)){
				result.addFatalError(Messages.format(RefactoringCoreMessages.ChangeSignatureRefactoring_duplicate_name, BasicElementLabels.getJavaElementName(newName)));
				doubled.add(newName);
			} else {
				found.add(newName);
			}
		}
	}

	private ICompilationUnit getCu() {
		return fMethod.getCompilationUnit();
	}

	private boolean mustAnalyzeAstOfDeclaringCu() throws JavaModelException{
		return !JdtFlags.isAbstract(getMethod()) && !JdtFlags.isNative(getMethod()) && !getMethod().getDeclaringType().isInterface();
	}

	private RefactoringStatus checkCompilationofDeclaringCu() throws CoreException {
		ICompilationUnit cu= getCu();
		TextChange change= fChangeManager.get(cu);
		String newCuSource= change.getPreviewContent(new NullProgressMonitor());
		CompilationUnit newCUNode= new RefactoringASTParser(IASTSharedValues.SHARED_AST_LEVEL).parse(newCuSource, cu, true, false, null);
		RefactoringStatus result= new RefactoringStatus();
		for (IProblem problem : RefactoringAnalyzeUtil.getIntroducedCompileProblems(newCUNode, fBaseCuRewrite.getRoot())) {
			if (shouldReport(problem, newCUNode))
				result.addEntry(new RefactoringStatusEntry((problem.isError() ? RefactoringStatus.ERROR : RefactoringStatus.WARNING), problem.getMessage(), new JavaStringStatusContext(newCuSource, SourceRangeFactory.create(problem))));
		}
		return result;
	}

	/**
	 * Evaluates if a problem needs to be reported.
	 * @param problem the problem
	 * @param cu the AST containing the new source
	 * @return return <code>true</code> if the problem needs to be reported
	 */
	protected boolean shouldReport(IProblem problem, CompilationUnit cu) {
		if (! problem.isError())
			return false;
		if (problem.getID() == IProblem.UndefinedType) //reported when trying to import
			return false;
		return true;
	}

	private String getOldMethodParameters() {
		StringBuilder buff= new StringBuilder();
		int i= 0;
		for (Iterator<ParameterInfo> iter= getNotAddedInfos().iterator(); iter.hasNext(); i++) {
			ParameterInfo info= iter.next();
			if (i != 0 )
				buff.append(", ");  //$NON-NLS-1$
			buff.append(createDeclarationString(info));
		}
		return buff.toString();
	}

	private String getMethodParameters() {
		StringBuilder buff= new StringBuilder();
		int i= 0;
		for (Iterator<ParameterInfo> iter= getNotDeletedInfos().iterator(); iter.hasNext(); i++) {
			ParameterInfo info= iter.next();
			if (i != 0 )
				buff.append(", ");  //$NON-NLS-1$
			buff.append(createDeclarationString(info));
		}
		return buff.toString();
	}

	private List<ParameterInfo> getAddedInfos(){
		List<ParameterInfo> result= new ArrayList<>(1);
		for (ParameterInfo info : fParameterInfos) {
			if (info.isAdded())
				result.add(info);
		}
		return result;
	}

	private List<ParameterInfo> getDeletedInfos(){
		List<ParameterInfo> result= new ArrayList<>(1);
		for (ParameterInfo info : fParameterInfos) {
			if (info.isDeleted())
				result.add(info);
		}
		return result;
	}

	private List<ParameterInfo> getNotAddedInfos(){
		List<ParameterInfo> all= new ArrayList<>(fParameterInfos);
		all.removeAll(getAddedInfos());
		return all;
	}

	private List<ParameterInfo> getNotDeletedInfos(){
		List<ParameterInfo> all= new ArrayList<>(fParameterInfos);
		all.removeAll(getDeletedInfos());
		return all;
	}

	private boolean areNamesSameAsInitial() {
		for (ParameterInfo info : fParameterInfos) {
			if (info.isRenamed())
				return false;
		}
		return true;
	}

	private boolean isOrderSameAsInitial(){
		int i= 0;
		for (Iterator<ParameterInfo> iter= fParameterInfos.iterator(); iter.hasNext(); i++) {
			ParameterInfo info= iter.next();
			if (info.getOldIndex() != i) // includes info.isAdded()
				return false;
			if (info.isDeleted())
				return false;
		}
		return true;
	}

	private RefactoringStatus checkReorderings(IProgressMonitor pm) throws JavaModelException {
		try{
			pm.beginTask(RefactoringCoreMessages.ChangeSignatureRefactoring_checking_preconditions, 1);
			return checkNativeMethods();
		} finally{
			pm.done();
		}
	}

	private RefactoringStatus checkRenamings(IProgressMonitor pm) throws JavaModelException {
		try{
			pm.beginTask(RefactoringCoreMessages.ChangeSignatureRefactoring_checking_preconditions, 1);
			return checkParameterNamesInRippleMethods();
		} finally{
			pm.done();
		}
	}

	private RefactoringStatus checkParameterNamesInRippleMethods() throws JavaModelException {
		RefactoringStatus result= new RefactoringStatus();
		Set<String> newParameterNames= getNewParameterNamesList();
		for (IMethod rippleMethod : fRippleMethods) {
			for (String paramName : rippleMethod.getParameterNames()) {
				if (newParameterNames.contains(paramName)) {
					String[] args= new String[]{JavaElementUtil.createMethodSignature(rippleMethod), BasicElementLabels.getJavaElementName(paramName)};
					String msg= Messages.format(RefactoringCoreMessages.ChangeSignatureRefactoring_already_has, args);
					RefactoringStatusContext context= JavaStatusContext.create(rippleMethod.getCompilationUnit(), rippleMethod.getNameRange());
					result.addError(msg, context);
				}
			}
		}
		return result;
	}

	private Set<String> getNewParameterNamesList() {
		Set<String> oldNames= getOriginalParameterNames();
		Set<String> currentNames= getNamesOfNotDeletedParameters();
		currentNames.removeAll(oldNames);
		return currentNames;
	}

	private Set<String> getNamesOfNotDeletedParameters() {
		Set<String> result= new HashSet<>();
		for (ParameterInfo info : getNotDeletedInfos()) {
			result.add(info.getNewName());
		}
		return result;
	}

	private Set<String> getOriginalParameterNames() {
		Set<String> result= new HashSet<>();
		for (ParameterInfo info : fParameterInfos) {
			if (! info.isAdded())
				result.add(info.getOldName());
		}
		return result;
	}

	private RefactoringStatus checkNativeMethods() throws JavaModelException{
		RefactoringStatus result= new RefactoringStatus();
		for (IMethod rippleMethod : fRippleMethods) {
			if (JdtFlags.isNative(rippleMethod)) {
				String message= Messages.format(RefactoringCoreMessages.ChangeSignatureRefactoring_native, new String[]{JavaElementUtil.createMethodSignature(rippleMethod), BasicElementLabels.getJavaElementName(rippleMethod.getDeclaringType().getFullyQualifiedName('.'))});
				result.addError(message, JavaStatusContext.create(rippleMethod));
			}
		}
		return result;
	}

	private IFile[] getAllFilesToModify(){
		return ResourceUtil.getFiles(fChangeManager.getAllCompilationUnits());
	}

	public Change[] getAllChanges() {
		return fChangeManager.getAllChanges();
	}

	@Override
	public Change createChange(IProgressMonitor pm) {
		pm.beginTask("", 1); //$NON-NLS-1$
		try {
			return new DynamicValidationRefactoringChange(createDescriptor(), doGetRefactoringChangeName(), getAllChanges());
		} finally {
			clearManagers();
			pm.done();
		}
	}

	private ChangeMethodSignatureArguments getParticipantArguments() {
		ArrayList<Parameter> parameterList= new ArrayList<>();
		String[] originalParameterTypeSigs= fMethod.getParameterTypes();

		for (ParameterInfo pi : getParameterInfos()) {
			if (!pi.isDeleted()) {
				int oldIndex= pi.isAdded() ? -1 : pi.getOldIndex();
				String newName= pi.getNewName();
				String typeSig;
				if (pi.isTypeNameChanged()) {
					String newType= pi.getNewTypeName();
					if (pi.isNewVarargs()) {
						newType= ParameterInfo.stripEllipsis(newType) + "[]"; //$NON-NLS-1$
					}
					typeSig= Signature.createTypeSignature(newType, false);
				} else {
					typeSig= originalParameterTypeSigs[pi.getOldIndex()];
				}
				String defaultValue= pi.getDefaultValue();
				parameterList.add(new Parameter(oldIndex, newName, typeSig, defaultValue));
			}
		}
		Parameter[] parameters= parameterList.toArray(new Parameter[parameterList.size()]);

		ArrayList<ThrownException> exceptionList= new ArrayList<>();
		List<ExceptionInfo> exceptionInfos= getExceptionInfos();
		for (int i= 0; i < exceptionInfos.size(); i++) {
			ExceptionInfo ei= exceptionInfos.get(i);
			if (!ei.isDeleted()) {
				int oldIndex= ei.isAdded() ? -1 : i;
				String qualifiedTypeName= ei.getFullyQualifiedName();
				String newTypeSig= Signature.createTypeSignature(qualifiedTypeName, true);
				exceptionList.add(new ThrownException(oldIndex, newTypeSig));
			}
		}
		ThrownException[] exceptions= exceptionList.toArray(new ThrownException[exceptionList.size()]);
		String returnTypeSig;
		if (fReturnTypeInfo.isTypeNameChanged()) {
			returnTypeSig= Signature.createTypeSignature(fReturnTypeInfo.getNewTypeName(), false);
		} else {
			try {
				returnTypeSig= fMethod.getReturnType();
			} catch (JavaModelException e) {
				returnTypeSig= Signature.createTypeSignature(fReturnTypeInfo.getNewTypeName(), false);
			}
		}
		return new ChangeMethodSignatureArguments(fMethodName, returnTypeSig, fVisibility, parameters, exceptions, fDelegateUpdating);
	}


	public JavaRefactoringDescriptor createDescriptor() {
		final Map<String, String> arguments= new HashMap<>();
		String project= null;
		IJavaProject javaProject= fMethod.getJavaProject();
		if (javaProject != null)
			project= javaProject.getElementName();
		ChangeMethodSignatureDescriptor descriptor= null;
		try {
			final String description= Messages.format(RefactoringCoreMessages.ChangeSignatureRefactoring_descriptor_description_short, BasicElementLabels.getJavaElementName(fMethod.getElementName()));
			final String header= Messages.format(RefactoringCoreMessages.ChangeSignatureRefactoring_descriptor_description, new String[] { getOldMethodSignature(), getNewMethodSignature()});
			final JDTRefactoringDescriptorComment comment= createComment(project, header);
			descriptor= RefactoringSignatureDescriptorFactory.createChangeMethodSignatureDescriptor(project, description, comment.asString(), arguments, getDescriptorFlags());
			arguments.put(JavaRefactoringDescriptorUtil.ATTRIBUTE_INPUT, JavaRefactoringDescriptorUtil.elementToHandle(project,fMethod));
			arguments.put(JavaRefactoringDescriptorUtil.ATTRIBUTE_NAME, fMethodName);
			arguments.put(ATTRIBUTE_DELEGATE, Boolean.toString(fDelegateUpdating));
			arguments.put(ATTRIBUTE_DEPRECATE, Boolean.toString(fDelegateDeprecation));
			if (fReturnTypeInfo.isTypeNameChanged())
				arguments.put(ATTRIBUTE_RETURN, fReturnTypeInfo.getNewTypeName());
			try {
				if (!isVisibilitySameAsInitial())
					arguments.put(ATTRIBUTE_VISIBILITY, Integer.toString(fVisibility));
			} catch (JavaModelException exception) {
				JavaManipulationPlugin.log(exception);
			}
			int count= 1;
			for (ParameterInfo info : fParameterInfos) {
				final StringBuilder buffer= new StringBuilder(64);
				if (info.isAdded())
					buffer.append("{added}"); //$NON-NLS-1$
				else
					 buffer.append(info.getOldTypeName());
				buffer.append(" "); //$NON-NLS-1$
				if (info.isAdded())
					buffer.append("{added}"); //$NON-NLS-1$
				else
					 buffer.append(info.getOldName());
				buffer.append(" "); //$NON-NLS-1$
				buffer.append(info.getOldIndex());
				buffer.append(" "); //$NON-NLS-1$
				if (info.isDeleted())
					buffer.append("{deleted}"); //$NON-NLS-1$
				else
					buffer.append(info.getNewTypeName().replace(" ", ""));  //$NON-NLS-1$//$NON-NLS-2$
				buffer.append(" "); //$NON-NLS-1$
				if (info.isDeleted())
					buffer.append("{deleted}"); //$NON-NLS-1$
				else
					buffer.append(info.getNewName());
				buffer.append(" "); //$NON-NLS-1$
				buffer.append(info.isDeleted());
				arguments.put(ATTRIBUTE_PARAMETER + count, buffer.toString());
				final String value= info.getDefaultValue();
				if (value != null && !"".equals(value)) //$NON-NLS-1$
					arguments.put(ATTRIBUTE_DEFAULT + count, value);
				count++;
			}
			count= 1;
			for (ExceptionInfo info : fExceptionInfos) {
				arguments.put(JavaRefactoringDescriptorUtil.ATTRIBUTE_ELEMENT + count, JavaRefactoringDescriptorUtil.elementToHandle(project,info.getElement()));
				arguments.put(ATTRIBUTE_KIND + count, Integer.toString(info.getKind()));
				count++;
			}
		} catch (JavaModelException exception) {
			JavaManipulationPlugin.log(exception);
			return null;
		}
		return descriptor;
	}

	protected int getDescriptorFlags() {
		int flags= JavaRefactoringDescriptor.JAR_MIGRATION | JavaRefactoringDescriptor.JAR_REFACTORING | RefactoringDescriptor.STRUCTURAL_CHANGE;
		try {
			if (!Flags.isPrivate(fMethod.getFlags()))
				flags|= RefactoringDescriptor.MULTI_CHANGE;
			final IType declaring= fMethod.getDeclaringType();
			if (declaring.isAnonymous() || declaring.isLocal())
				flags|= JavaRefactoringDescriptor.JAR_SOURCE_ATTACHMENT;
		} catch (JavaModelException exception) {
			JavaManipulationPlugin.log(exception);
		}
		return flags;
	}

	private JDTRefactoringDescriptorComment createComment(String project, final String header) throws JavaModelException {
		final JDTRefactoringDescriptorComment comment= new JDTRefactoringDescriptorComment(project, this, header);
		if (!fMethod.getElementName().equals(fMethodName))
			comment.addSetting(Messages.format(RefactoringCoreMessages.ChangeSignatureRefactoring_new_name_pattern, BasicElementLabels.getJavaElementName(fMethodName)));
		if (!isVisibilitySameAsInitial()) {
			String visibility= JdtFlags.getVisibilityString(fVisibility);
			if ("".equals(visibility)) //$NON-NLS-1$
				visibility= RefactoringCoreMessages.ChangeSignatureRefactoring_default_visibility;
			comment.addSetting(Messages.format(RefactoringCoreMessages.ChangeSignatureRefactoring_new_visibility_pattern, visibility));
		}
		if (fReturnTypeInfo.isTypeNameChanged())
			comment.addSetting(Messages.format(RefactoringCoreMessages.ChangeSignatureRefactoring_new_return_type_pattern, BasicElementLabels.getJavaElementName(fReturnTypeInfo.getNewTypeName())));
		List<String> deleted= new ArrayList<>();
		List<String> added= new ArrayList<>();
		List<String> changed= new ArrayList<>();
		for (ParameterInfo info : fParameterInfos) {
			if (info.isDeleted())
				deleted.add(Messages.format(RefactoringCoreMessages.ChangeSignatureRefactoring_deleted_parameter_pattern, new String[] { BasicElementLabels.getJavaElementName(info.getOldTypeName()), BasicElementLabels.getJavaElementName(info.getOldName())}));
			else if (info.isAdded())
				added.add(Messages.format(RefactoringCoreMessages.ChangeSignatureRefactoring_added_parameter_pattern, new String[] { BasicElementLabels.getJavaElementName(info.getNewTypeName()), BasicElementLabels.getJavaElementName(info.getNewName())}));
			else if (info.isRenamed() || info.isTypeNameChanged() || info.isVarargChanged())
				changed.add(Messages.format(RefactoringCoreMessages.ChangeSignatureRefactoring_changed_parameter_pattern, new String[] { BasicElementLabels.getJavaElementName(info.getOldTypeName()), BasicElementLabels.getJavaElementName(info.getOldName())}));
		}
		if (!added.isEmpty())
			comment.addSetting(JDTRefactoringDescriptorComment.createCompositeSetting(RefactoringCoreMessages.ChangeSignatureRefactoring_added_parameters, added.toArray(new String[added.size()])));
		if (!deleted.isEmpty())
			comment.addSetting(JDTRefactoringDescriptorComment.createCompositeSetting(RefactoringCoreMessages.ChangeSignatureRefactoring_removed_parameters, deleted.toArray(new String[deleted.size()])));
		if (!changed.isEmpty())
			comment.addSetting(JDTRefactoringDescriptorComment.createCompositeSetting(RefactoringCoreMessages.ChangeSignatureRefactoring_changed_parameters, changed.toArray(new String[changed.size()])));
		added.clear();
		deleted.clear();
		changed.clear();
		for (ExceptionInfo info : fExceptionInfos) {
			if (info.isAdded())
				added.add(info.getElement().getElementName());
			else if (info.isDeleted())
				deleted.add(info.getElement().getElementName());
		}
		if (!added.isEmpty())
			comment.addSetting(JDTRefactoringDescriptorComment.createCompositeSetting(RefactoringCoreMessages.ChangeSignatureRefactoring_added_exceptions, added.toArray(new String[added.size()])));
		if (!deleted.isEmpty())
			comment.addSetting(JDTRefactoringDescriptorComment.createCompositeSetting(RefactoringCoreMessages.ChangeSignatureRefactoring_removed_exceptions, deleted.toArray(new String[deleted.size()])));
		return comment;
	}

	protected String doGetRefactoringChangeName() {
		return RefactoringCoreMessages.ChangeSignatureRefactoring_restructure_parameters;
	}

	private TextChangeManager createChangeManager(IProgressMonitor pm, RefactoringStatus result) throws CoreException {
		pm.beginTask(RefactoringCoreMessages.ChangeSignatureRefactoring_preview, 2);
		fChangeManager= new TextChangeManager();
		boolean isNoArgConstructor= isNoArgConstructor();
		Map<ICompilationUnit, Set<IType>> namedSubclassMapping= null;
		if (isNoArgConstructor){
			//create only when needed;
			namedSubclassMapping= createNamedSubclassMapping(new SubProgressMonitor(pm, 1));
		}else{
			pm.worked(1);
		}
		for (SearchResultGroup occurrence : fOccurrences) {
			if (pm.isCanceled())
				throw new OperationCanceledException();
			SearchResultGroup group= occurrence;
			ICompilationUnit cu= group.getCompilationUnit();
			if (cu == null)
				continue;
			CompilationUnitRewrite cuRewrite;
			if (cu.equals(getCu())) {
				cuRewrite= fBaseCuRewrite;
			} else {
				cuRewrite= new CompilationUnitRewrite(cu);
				cuRewrite.getASTRewrite().setTargetSourceRangeComputer(new TightSourceRangeComputer());
			}
			//IntroduceParameterObjectRefactoring needs to update declarations first:
			List<OccurrenceUpdate<? extends ASTNode>> deferredUpdates= new ArrayList<>();
			for (ASTNode node : ASTNodeSearchUtil.findNodes(group.getSearchResults(), cuRewrite.getRoot())) {
				OccurrenceUpdate<? extends ASTNode> update= createOccurrenceUpdate(node, cuRewrite, result);
				if (update instanceof DeclarationUpdate) {
					update.updateNode();
				} else {
					deferredUpdates.add(update);
				}
			}
			for (OccurrenceUpdate<? extends ASTNode> occurrenceUpdate : deferredUpdates) {
				occurrenceUpdate.updateNode();
			}

			if (isNoArgConstructor && namedSubclassMapping.containsKey(cu)){
				//only non-anonymous subclasses may have noArgConstructors to modify - see bug 43444
				for (IType subtype : namedSubclassMapping.get(cu)) {
					AbstractTypeDeclaration subtypeNode= ASTNodeSearchUtil.getAbstractTypeDeclarationNode(subtype, cuRewrite.getRoot());
					if (subtypeNode != null)
						modifyImplicitCallsToNoArgConstructor(subtypeNode, cuRewrite);
				}
			}
			TextChange change= cuRewrite.createChange(true);
			if (change != null)
				fChangeManager.manage(cu, change);
		}

		pm.done();
		return fChangeManager;
	}

	private Map<ICompilationUnit, Set<IType>> createNamedSubclassMapping(IProgressMonitor pm) throws JavaModelException{
		Map<ICompilationUnit, Set<IType>> result= new HashMap<>();
		for (IType subclass : getCachedTypeHierarchy(new SubProgressMonitor(pm, 1)).getSubclasses(fMethod.getDeclaringType())) {
			if (subclass.isAnonymous())
				continue;
			ICompilationUnit cu= subclass.getCompilationUnit();
			if (! result.containsKey(cu))
				result.put(cu, new HashSet<IType>());
			result.get(cu).add(subclass);
		}
		return result;
	}

	private void modifyImplicitCallsToNoArgConstructor(AbstractTypeDeclaration subclass, CompilationUnitRewrite cuRewrite) {
		MethodDeclaration[] constructors= getAllConstructors(subclass);
		if (constructors.length == 0){
			addNewConstructorToSubclass(subclass, cuRewrite);
		} else {
			for (MethodDeclaration constructor : constructors) {
				if (!containsImplicitCallToSuperConstructor(constructor)) {
					continue;
				}
				addExplicitSuperConstructorCall(constructor, cuRewrite);
			}
		}
	}

	private void addExplicitSuperConstructorCall(MethodDeclaration constructor, CompilationUnitRewrite cuRewrite) {
		SuperConstructorInvocation superCall= constructor.getAST().newSuperConstructorInvocation();
		addArgumentsToNewSuperConstructorCall(superCall, cuRewrite);
		String msg= RefactoringCoreMessages.ChangeSignatureRefactoring_add_super_call;
		TextEditGroup description= cuRewrite.createGroupDescription(msg);
		cuRewrite.getASTRewrite().getListRewrite(constructor.getBody(), Block.STATEMENTS_PROPERTY).insertFirst(superCall, description);
	}

	private void addArgumentsToNewSuperConstructorCall(SuperConstructorInvocation superCall, CompilationUnitRewrite cuRewrite) {
		Iterator<ParameterInfo> iter= getNotDeletedInfos().iterator();
		while (iter.hasNext()) {
			ParameterInfo info= iter.next();
			Expression newExpression= createNewExpression(info, getParameterInfos(), superCall.arguments(), cuRewrite, ASTNodes.getParent(superCall, MethodDeclaration.class));
			if (newExpression != null)
				superCall.arguments().add(newExpression);
		}
	}

	private static boolean containsImplicitCallToSuperConstructor(MethodDeclaration constructor) {
		Assert.isTrue(constructor.isConstructor());
		Block body= constructor.getBody();
		if (body == null)
			return false;
		if (body.statements().size() == 0)
			return true;
		if (body.statements().get(0) instanceof ConstructorInvocation)
			return false;
		if (body.statements().get(0) instanceof SuperConstructorInvocation)
			return false;
		return true;
	}

	private void addNewConstructorToSubclass(AbstractTypeDeclaration subclass, CompilationUnitRewrite cuRewrite) {
		AST ast= subclass.getAST();
		MethodDeclaration newConstructor= ast.newMethodDeclaration();
		newConstructor.setName(ast.newSimpleName(subclass.getName().getIdentifier()));
		newConstructor.setConstructor(true);
		newConstructor.setJavadoc(null);
		newConstructor.modifiers().addAll(ASTNodeFactory.newModifiers(ast, getAccessModifier(subclass)));
		newConstructor.setReturnType2(ast.newPrimitiveType(PrimitiveType.VOID));
		Block body= ast.newBlock();
		newConstructor.setBody(body);
		SuperConstructorInvocation superCall= ast.newSuperConstructorInvocation();
		addArgumentsToNewSuperConstructorCall(superCall, cuRewrite);
		body.statements().add(superCall);

		String msg= RefactoringCoreMessages.ChangeSignatureRefactoring_add_constructor;
		TextEditGroup description= cuRewrite.createGroupDescription(msg);
		cuRewrite.getASTRewrite().getListRewrite(subclass, subclass.getBodyDeclarationsProperty()).insertFirst(newConstructor, description);

		// TODO use AbstractTypeDeclaration
	}

	private static int getAccessModifier(AbstractTypeDeclaration subclass) {
		int modifiers= subclass.getModifiers();
		if (Modifier.isPublic(modifiers))
			return Modifier.PUBLIC;
		else if (Modifier.isProtected(modifiers))
			return Modifier.PROTECTED;
		else if (Modifier.isPrivate(modifiers))
			return Modifier.PRIVATE;
		else
			return Modifier.NONE;
	}

	private MethodDeclaration[] getAllConstructors(AbstractTypeDeclaration typeDeclaration) {
		BodyDeclaration decl;
		List<BodyDeclaration> result= new ArrayList<>(1);
		for (Iterator<BodyDeclaration> it = typeDeclaration.bodyDeclarations().listIterator(); it.hasNext(); ) {
			decl= it.next();
			if (decl instanceof MethodDeclaration && ((MethodDeclaration) decl).isConstructor())
				result.add(decl);
		}
		return result.toArray(new MethodDeclaration[result.size()]);
	}

	private boolean isNoArgConstructor() throws JavaModelException {
		return fMethod.isConstructor() && fMethod.getNumberOfParameters() == 0;
	}

	private Expression createNewExpression(ParameterInfo info, List<ParameterInfo> parameterInfos, List<Expression> nodes, CompilationUnitRewrite cuRewrite, MethodDeclaration method) {
		if (info.isNewVarargs() && info.getDefaultValue().trim().length() == 0)
			return null;
		else {
			if (fDefaultValueAdvisor == null)
				return (Expression) cuRewrite.getASTRewrite().createStringPlaceholder(info.getDefaultValue(), ASTNode.METHOD_INVOCATION);
			else
				return fDefaultValueAdvisor.createDefaultExpression(nodes, info, parameterInfos, method, false, cuRewrite);
		}
	}

	private boolean isVisibilitySameAsInitial() throws JavaModelException {
		return fVisibility == JdtFlags.getVisibilityCode(fMethod);
	}

	private IJavaSearchScope createRefactoringScope()  throws JavaModelException{
		return RefactoringScopeFactory.create(fMethod, true, false);
	}

	private SearchResultGroup[] findOccurrences(IProgressMonitor pm, ReferencesInBinaryContext binaryRefs, RefactoringStatus status) throws JavaModelException{
		final boolean isConstructor= fMethod.isConstructor();
		CuCollectingSearchRequestor requestor= new CuCollectingSearchRequestor(binaryRefs) {
			@Override
			protected void acceptSearchMatch(ICompilationUnit unit, SearchMatch match) throws CoreException {
				// workaround for https://bugs.eclipse.org/bugs/show_bug.cgi?id=27236 :
				if (isConstructor && match instanceof MethodReferenceMatch) {
					MethodReferenceMatch mrm= (MethodReferenceMatch) match;
					if (mrm.isSynthetic()) {
						return;
					}
				}
				collectMatch(match);
			}
		};

		SearchPattern pattern;
		if (isConstructor) {

//			// workaround for https://bugs.eclipse.org/bugs/show_bug.cgi?id=226151 : don't find binary refs for constructors for now
//			return ConstructorReferenceFinder.getConstructorOccurrences(fMethod, pm, status);

//			SearchPattern occPattern= SearchPattern.createPattern(fMethod, IJavaSearchConstants.ALL_OCCURRENCES, SearchUtils.GENERICS_AGNOSTIC_MATCH_RULE);
			SearchPattern declPattern= SearchPattern.createPattern(fMethod, IJavaSearchConstants.DECLARATIONS, SearchUtils.GENERICS_AGNOSTIC_MATCH_RULE);
			if (declPattern == null) {
				return new SearchResultGroup[0];
			}
			SearchPattern refPattern= SearchPattern.createPattern(fMethod, IJavaSearchConstants.REFERENCES, SearchUtils.GENERICS_AGNOSTIC_MATCH_RULE);
			if (refPattern == null) {
				return new SearchResultGroup[0];
			}
//			pattern= SearchPattern.createOrPattern(declPattern, refPattern);
//			pattern= occPattern;

			// workaround for https://bugs.eclipse.org/bugs/show_bug.cgi?id=226151 : do two searches
			try {
				SearchEngine engine= new SearchEngine();
				engine.search(declPattern, SearchUtils.getDefaultSearchParticipants(), createRefactoringScope(), requestor, new NullProgressMonitor());
				engine.search(refPattern, SearchUtils.getDefaultSearchParticipants(), createRefactoringScope(), requestor, pm);
			} catch (CoreException e) {
				throw new JavaModelException(e);
			}
			return RefactoringSearchEngine.groupByCu(requestor.getResults(), status);

		} else {
			pattern= RefactoringSearchEngine.createOrPattern(fRippleMethods, IJavaSearchConstants.ALL_OCCURRENCES);
		}
		return RefactoringSearchEngine.search(pattern, createRefactoringScope(), requestor, pm, status);
	}

	private static String createDeclarationString(ParameterInfo info) {
		String newTypeName= info.getNewTypeName();
		int varargsIndex= newTypeName.lastIndexOf("..."); //$NON-NLS-1$
		if (varargsIndex != -1) {
			newTypeName= newTypeName.substring(0, varargsIndex);
		}
		int index= newTypeName.lastIndexOf('.');
		if (index != -1) {
			newTypeName= newTypeName.substring(index+1);
		}
		return newTypeName + (varargsIndex != -1 ? "..." : "") + " " + info.getNewName(); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
	}

	private static final boolean BUG_89686= true; //see bug 83693: Search for References to methods/constructors: do ranges include parameter lists?

	private OccurrenceUpdate<? extends ASTNode> createOccurrenceUpdate(ASTNode node, CompilationUnitRewrite cuRewrite, RefactoringStatus result) {
		if (BUG_89686 && node instanceof SimpleName && node.getParent() instanceof EnumConstantDeclaration)
			node= node.getParent();

		if (Invocations.isInvocationWithArguments(node))
			return new ReferenceUpdate(node, cuRewrite, result);

		else if (node instanceof SimpleName && node.getParent() instanceof MethodDeclaration)
			return new DeclarationUpdate((MethodDeclaration) node.getParent(), cuRewrite, result);

		else if (node instanceof MemberRef || node instanceof MethodRef)
			return new DocReferenceUpdate(node, cuRewrite, result);

		else if (ASTNodes.getParent(node, ImportDeclaration.class) != null)
			return new StaticImportUpdate(ASTNodes.getParent(node, ImportDeclaration.class), cuRewrite, result);

		else if (node instanceof LambdaExpression)
			return new LambdaExpressionUpdate((LambdaExpression) node, cuRewrite, result);

		else if (node.getLocationInParent() == ExpressionMethodReference.NAME_PROPERTY)
			return new ExpressionMethodRefUpdate((ExpressionMethodReference) node.getParent(), cuRewrite, result);

		else
			return new NullOccurrenceUpdate(node, cuRewrite, result);
	}

	abstract class OccurrenceUpdate<N extends ASTNode> {
		protected final CompilationUnitRewrite fCuRewrite;
		protected final TextEditGroup fDescription;
		protected RefactoringStatus fResult;

		protected OccurrenceUpdate(CompilationUnitRewrite cuRewrite, TextEditGroup description, RefactoringStatus result) {
			fCuRewrite= cuRewrite;
			fDescription= description;
			fResult= result;
		}

		protected final ASTRewrite getASTRewrite() {
			return fCuRewrite.getASTRewrite();
		}

		protected final ImportRewrite getImportRewrite() {
			return fCuRewrite.getImportRewrite();
		}

		protected final ImportRemover getImportRemover() {
			return fCuRewrite.getImportRemover();
		}

		protected final CompilationUnitRewrite getCompilationUnitRewrite() {
			return fCuRewrite;
		}

		protected int getStartPosition() {
			return getMethodNameNode().getStartPosition();
		}

		public abstract void updateNode() throws CoreException;

		protected void registerImportRemoveNode(ASTNode node) {
			getImportRemover().registerRemovedNode(node);
		}

		protected final void reshuffleElements() {
			if (isOrderSameAsInitial())
				return;

			//varargs; method(p1, p2, .., pn), call(a1, a2, .., ax) :
			// if (method_was_vararg) {
			//     assert fOldVarargIndex != -1
			//     if (vararg_retained) {
			//         assert vararg_is_last_non_deleted (pn)
			//         assert no_other_varargs
			//         => reshuffle [1..n-1] then append remaining nodes [n..x], possibly none
			//
			//     } else (vararg_deleted) {
			//         assert all_are_non_vararg
			//         => reshuffle [1..n-1], drop all remaining nodes [n..x], possibly none
			//     }
			//
			// } else if (method_became_vararg) {
			//     assert n == x
			//     assert fOldVarargIndex == -1
			//     => reshuffle [1..n]
			//
			// } else (JLS2_case) {
			//     assert n == x
			//     assert fOldVarargIndex == -1
			//     => reshuffle [1..n]
			// }

			ListRewrite listRewrite= getParamgumentsRewrite();
			Map<N, N> newOldMap= new LinkedHashMap<>();
			List<N> nodes= listRewrite.getRewrittenList();
			Iterator<N> rewriteIter= nodes.iterator();
			List<N> original= listRewrite.getOriginalList();
			for (N n : original) {
				newOldMap.put(rewriteIter.next(),n);
			}
			List<N> newNodes= new ArrayList<>();
			// register removed nodes, and collect nodes in new sequence:
			for (ParameterInfo info : fParameterInfos) {
				int oldIndex= info.getOldIndex();

				if (info.isDeleted()) {
					if (oldIndex != fOldVarargIndex) {
						registerImportRemoveNode(nodes.get(oldIndex));
					} else {
						//vararg deleted -> remove all remaining nodes:
						for (int n= oldIndex; n < nodes.size(); n++) {
							registerImportRemoveNode(nodes.get(n));
						}
					}

				} else if (info.isAdded()) {
					N newParamgument= createNewParamgument(info, fParameterInfos, nodes);
					if (newParamgument != null)
						newNodes.add(newParamgument);

				} else /* parameter stays */ {
					if (oldIndex != fOldVarargIndex) {
						N oldNode= nodes.get(oldIndex);
						N movedNode= moveNode(oldNode, getASTRewrite());
						newNodes.add(movedNode);
					} else {
						//vararg stays and is last parameter -> copy all remaining nodes:
						for (int n= oldIndex; n < nodes.size(); n++) {
							N oldNode= nodes.get(n);
							N movedNode= moveNode(oldNode, getASTRewrite());
							newNodes.add(movedNode);
						}
					}
				}
			}

			Iterator<N> nodesIter= nodes.iterator();
			Iterator<N> newIter= newNodes.iterator();
			//replace existing nodes with new ones:
			while (nodesIter.hasNext() && newIter.hasNext()) {
				ASTNode node= nodesIter.next();
				ASTNode newNode= newIter.next();
				if (!ASTNodes.isExistingNode(node)) //XXX:should better be addressed in ListRewriteEvent.replaceEntry(ASTNode, ASTNode)
					listRewrite.replace(newOldMap.get(node), newNode, fDescription);
				else
					listRewrite.replace(node, newNode, fDescription);
			}
			//remove remaining existing nodes:
			while (nodesIter.hasNext()) {
				ASTNode node= nodesIter.next();
				if (!ASTNodes.isExistingNode(node))
					listRewrite.remove(newOldMap.get(node), fDescription);
				else
					listRewrite.remove(node, fDescription);
			}
			//add additional new nodes:
			while (newIter.hasNext()) {
				ASTNode node= newIter.next();
				listRewrite.insertLast(node, fDescription);
			}
		}

		/**
		 * @return ListRewrite of parameters or arguments
		 */
		protected abstract ListRewrite getParamgumentsRewrite();

		protected final void changeParamguments() {
			for (ParameterInfo info : getParameterInfos()) {
				if (info.isAdded() || info.isDeleted())
					continue;

				if (info.isRenamed())
					changeParamgumentName(info);

				if (info.isTypeNameChanged())
					changeParamgumentType(info);
			}
		}

		/**
		 * @param info the parameter info
		 */
		protected void changeParamgumentName(ParameterInfo info) {
			// no-op
		}

		/**
		 * @param info the parameter info
		 */
		protected void changeParamgumentType(ParameterInfo info) {
			// no-op
		}

		protected final void replaceTypeNode(Type typeNode, String newTypeName, ITypeBinding newTypeBinding){
			Type newTypeNode= createNewTypeNode(newTypeName, newTypeBinding);
			getASTRewrite().replace(typeNode, newTypeNode, fDescription);
			registerImportRemoveNode(typeNode);
			getTightSourceRangeComputer().addTightSourceNode(typeNode);
		}

		/**
		 * @param info TODO
		 * @param parameterInfos TODO
		 * @param nodes TODO
		 * @return a new method parameter or argument, or <code>null</code> for an empty vararg argument
		 */
		protected abstract N createNewParamgument(ParameterInfo info, List<ParameterInfo> parameterInfos, List<N> nodes);

		protected abstract SimpleName getMethodNameNode();

		protected final void changeMethodName() {
			if (! isMethodNameSameAsInitial()) {
				SimpleName nameNode= getMethodNameNode();
				if (nameNode != null) {
					SimpleName newNameNode= nameNode.getAST().newSimpleName(fMethodName);
					getASTRewrite().replace(nameNode, newNameNode, fDescription);
					registerImportRemoveNode(nameNode);
					getTightSourceRangeComputer().addTightSourceNode(nameNode);
				}
			}
		}

		protected final Type createNewTypeNode(String newTypeName, ITypeBinding newTypeBinding) {
			Type newTypeNode;
			if (newTypeBinding == null) {
				if (fDefaultValueAdvisor != null)
					newTypeNode= fDefaultValueAdvisor.createType(newTypeName, getStartPosition(), getCompilationUnitRewrite());
				else
					newTypeNode= (Type) getASTRewrite().createStringPlaceholder(newTypeName, ASTNode.SIMPLE_TYPE);
				//Don't import if not resolved.
			} else {
				ImportRewriteContext importRewriteContext= new ContextSensitiveImportRewriteContext(fCuRewrite.getRoot(), getStartPosition(), getImportRewrite());
				newTypeNode= getImportRewrite().addImport(newTypeBinding, fCuRewrite.getAST(), importRewriteContext);
				getImportRemover().registerAddedImports(newTypeNode);
			}
			return newTypeNode;
		}

		protected final TightSourceRangeComputer getTightSourceRangeComputer() {
			return (TightSourceRangeComputer) fCuRewrite.getASTRewrite().getExtendedSourceRangeComputer();
		}
	}

	class ReferenceUpdate extends OccurrenceUpdate<Expression> {
		/** isReferenceNode(fNode) */
		private ASTNode fNode;

		protected ReferenceUpdate(ASTNode node, CompilationUnitRewrite cuRewrite, RefactoringStatus result) {
			super(cuRewrite, cuRewrite.createGroupDescription(RefactoringCoreMessages.ChangeSignatureRefactoring_update_reference), result);
			fNode= node; //holds: Assert.isTrue(isReferenceNode(node));
		}

		@Override
		public void updateNode() {
			reshuffleElements();
			changeMethodName();
		}

		/** @return {@inheritDoc} (element type: Expression) */
		@Override
		protected ListRewrite getParamgumentsRewrite() {
			return getASTRewrite().getListRewrite(fNode, Invocations.getArgumentsProperty(fNode));
		}

		@Override
		protected Expression createNewParamgument(ParameterInfo info, List<ParameterInfo> parameterInfos, List<Expression> nodes) {
			CompilationUnitRewrite cuRewrite= getCompilationUnitRewrite();
			MethodDeclaration declaration= ASTNodes.getParent(fNode, MethodDeclaration.class);
			if (isRecursiveReference()) {
				return createNewExpressionRecursive(info, parameterInfos, nodes, cuRewrite, declaration);
			} else
				return createNewExpression(info, parameterInfos, nodes, cuRewrite, declaration);
		}

		private Expression createNewExpressionRecursive(ParameterInfo info, List<ParameterInfo> parameterInfos, List<Expression> nodes, CompilationUnitRewrite cuRewrite, MethodDeclaration methodDeclaration) {
			if (fDefaultValueAdvisor != null && info.isAdded()) {
				return fDefaultValueAdvisor.createDefaultExpression(nodes, info, parameterInfos, methodDeclaration, true, cuRewrite);
			}
			return (Expression) getASTRewrite().createStringPlaceholder(info.getNewName(), ASTNode.METHOD_INVOCATION);
		}

		@Override
		protected SimpleName getMethodNameNode() {
			if (fNode instanceof MethodInvocation)
				return ((MethodInvocation)fNode).getName();

			if (fNode instanceof SuperMethodInvocation)
				return ((SuperMethodInvocation)fNode).getName();

			return null;
		}

		private boolean isRecursiveReference() {
			MethodDeclaration enclosingMethodDeclaration= ASTNodes.getParent(fNode, MethodDeclaration.class);
			if (enclosingMethodDeclaration == null)
				return false;

			IMethodBinding enclosingMethodBinding= enclosingMethodDeclaration.resolveBinding();
			if (enclosingMethodBinding == null)
				return false;

			if (fNode instanceof MethodInvocation)
				return enclosingMethodBinding == ((MethodInvocation)fNode).resolveMethodBinding();

			if (fNode instanceof SuperMethodInvocation) {
				IMethodBinding methodBinding= ((SuperMethodInvocation)fNode).resolveMethodBinding();
				return isSameMethod(methodBinding, enclosingMethodBinding);
			}

			if (fNode instanceof ClassInstanceCreation)
				return enclosingMethodBinding == ((ClassInstanceCreation)fNode).resolveConstructorBinding();

			if (fNode instanceof ConstructorInvocation)
				return enclosingMethodBinding == ((ConstructorInvocation)fNode).resolveConstructorBinding();

			if (fNode instanceof SuperConstructorInvocation) {
				return false; //Constructors don't override -> enclosing has not been changed -> no recursion
			}

			if (fNode instanceof EnumConstantDeclaration) {
				return false; //cannot define enum constant inside enum constructor
			}

			Assert.isTrue(false);
			return false;
		}

		/**
		 * @param m1 method 1
		 * @param m2 method 2
		 * @return true iff
		 * 		<ul><li>the methods are both constructors with same argument types, or</li>
		 *	 		<li>the methods have the same name and the same argument types</li></ul>
		 */
		private boolean isSameMethod(IMethodBinding m1, IMethodBinding m2) {
			if (m1.isConstructor()) {
				if (! m2.isConstructor())
					return false;
			} else {
				if (! m1.getName().equals(m2.getName()))
					return false;
			}

			ITypeBinding[] m1Parameters= m1.getParameterTypes();
			ITypeBinding[] m2Parameters= m2.getParameterTypes();
			if (m1Parameters.length != m2Parameters.length)
				return false;
			for (int i= 0; i < m1Parameters.length; i++) {
				if (m1Parameters[i].getErasure() != m2Parameters[i].getErasure())
					return false;
			}
			return true;
		}

	}


	class ExpressionMethodRefUpdate extends OccurrenceUpdate<VariableDeclaration> {
		private ExpressionMethodReference fMethodRef;

		protected ExpressionMethodRefUpdate(ExpressionMethodReference decl, CompilationUnitRewrite cuRewrite, RefactoringStatus result) {
			super(cuRewrite, cuRewrite.createGroupDescription(RefactoringCoreMessages.ChangeSignatureRefactoring_change_signature), result);
			fMethodRef= decl;
		}

		@Override
		public void updateNode() throws CoreException {
			if (canChangeNameAndReturnType())
				changeMethodName();
		}

		@Override
		protected SimpleName getMethodNameNode() {
			return fMethodRef.getName();
		}

		@Override
		protected ListRewrite getParamgumentsRewrite() {
			return null;
		}

		@Override
		protected VariableDeclaration createNewParamgument(ParameterInfo info, List<ParameterInfo> parameterInfos, List<VariableDeclaration> nodes) {
			return null;
		}

	}

	/**
	 * Abstraction for handling MethodDeclaration and LambdaExpression updates.
	 * @param <N> type of the parameter nodes
	 */
	abstract class AbstractDeclarationUpdate<N extends VariableDeclaration> extends OccurrenceUpdate<N> {

		protected AbstractDeclarationUpdate(CompilationUnitRewrite cuRewrite, TextEditGroup description, RefactoringStatus result) {
			super(cuRewrite, description, result);
		}

		protected abstract ASTNode getNode();

		protected abstract VariableDeclaration getParameter(int index);

		@Override
		protected void changeParamgumentName(ParameterInfo info) {
			VariableDeclaration param= getParameter(info.getOldIndex());
			if (!info.getOldName().equals(param.getName().getIdentifier()))
				return; //don't change if original parameter name != name in rippleMethod

			String msg= RefactoringCoreMessages.ChangeSignatureRefactoring_update_parameter_references;
			TextEditGroup description= fCuRewrite.createGroupDescription(msg);
			TempOccurrenceAnalyzer analyzer= new TempOccurrenceAnalyzer(param, false);
			analyzer.perform();
			// @param tags are updated in changeJavaDocTags()
			for (SimpleName occurrence : analyzer.getReferenceAndDeclarationNodes()) {
				getASTRewrite().set(occurrence, SimpleName.IDENTIFIER_PROPERTY, info.getNewName(), description);
			}
		}

		@Override
		protected void changeParamgumentType(ParameterInfo info) {
			VariableDeclaration oldParam= getParameter(info.getOldIndex());
			if (oldParam instanceof SingleVariableDeclaration) {
				getASTRewrite().set(oldParam, SingleVariableDeclaration.VARARGS_PROPERTY, Boolean.valueOf(info.isNewVarargs()), fDescription);
				SingleVariableDeclaration oldSVDParam= (SingleVariableDeclaration) oldParam;
				replaceTypeNode(oldSVDParam.getType(), ParameterInfo.stripEllipsis(info.getNewTypeName()), info.getNewTypeBinding());
				removeExtraDimensions(oldSVDParam);
			}
		}

		private void removeExtraDimensions(SingleVariableDeclaration oldParam) {
			ListRewrite listRewrite= getASTRewrite().getListRewrite(oldParam, SingleVariableDeclaration.EXTRA_DIMENSIONS2_PROPERTY);
			for (Dimension dimension : (List<Dimension>) oldParam.extraDimensions()) {
				listRewrite.remove(dimension, fDescription);
			}
		}

		//TODO: already reported as compilation error -> don't report there?
		protected void checkIfDeletedParametersUsed() {
			for (ParameterInfo info : getDeletedInfos()) {
				VariableDeclaration paramDecl= getParameter(info.getOldIndex());
				TempOccurrenceAnalyzer analyzer= new TempOccurrenceAnalyzer(paramDecl, false);
				analyzer.perform();
				SimpleName[] paramRefs= analyzer.getReferenceNodes();

				if (paramRefs.length > 0) {
					RefactoringStatusContext context= JavaStatusContext.create(fCuRewrite.getCu(), paramRefs[0]);
					String typeName= getFullTypeName();
					Object[] keys= new String[] { BasicElementLabels.getJavaElementName(paramDecl.getName().getIdentifier()),
						BasicElementLabels.getJavaElementName(getMethod().getElementName()),
						BasicElementLabels.getJavaElementName(typeName) };
					String msg= Messages.format(RefactoringCoreMessages.ChangeSignatureRefactoring_parameter_used, keys);
					fResult.addError(msg, context);
				}
			}
		}

		protected String getFullTypeName() {
			ASTNode node= getNode();
			while (true) {
				node= node.getParent();
				if (node instanceof AbstractTypeDeclaration) {
					String typeName= ((AbstractTypeDeclaration) node).getName().getIdentifier();
					if (getNode() instanceof LambdaExpression) {
						return Messages.format(RefactoringCoreMessages.ChangeSignatureRefactoring_lambda_expression, typeName);
					}
					return typeName;
				} else if (node instanceof ClassInstanceCreation) {
					ClassInstanceCreation cic= (ClassInstanceCreation) node;
					return Messages.format(RefactoringCoreMessages.ChangeSignatureRefactoring_anonymous_subclass, BasicElementLabels.getJavaElementName(ASTNodes.asString(cic.getType())));
				} else if (node instanceof EnumConstantDeclaration) {
					EnumDeclaration ed= (EnumDeclaration) node.getParent();
					return Messages.format(RefactoringCoreMessages.ChangeSignatureRefactoring_anonymous_subclass, BasicElementLabels.getJavaElementName(ASTNodes.asString(ed.getName())));
				}
			}
		}

		protected SingleVariableDeclaration createNewSingleVariableDeclaration(ParameterInfo info) {
			SingleVariableDeclaration newP= getASTRewrite().getAST().newSingleVariableDeclaration();
			newP.setName(getASTRewrite().getAST().newSimpleName(info.getNewName()));
			newP.setType(createNewTypeNode(ParameterInfo.stripEllipsis(info.getNewTypeName()), info.getNewTypeBinding()));
			newP.setVarargs(info.isNewVarargs());
			return newP;
		}
	}

	class LambdaExpressionUpdate extends AbstractDeclarationUpdate<VariableDeclaration> {
		private LambdaExpression fLambdaDecl;

		protected LambdaExpressionUpdate(LambdaExpression decl, CompilationUnitRewrite cuRewrite, RefactoringStatus result) {
			super(cuRewrite, cuRewrite.createGroupDescription(RefactoringCoreMessages.ChangeSignatureRefactoring_change_signature), result);
			fLambdaDecl= decl;
		}

		@Override
		public void updateNode() throws CoreException {
			changeParamguments();

			reshuffleElements();

			if (fBodyUpdater == null || fBodyUpdater.needsParameterUsedCheck())
				checkIfDeletedParametersUsed();
		}

		/** @return {@inheritDoc} (element type: VariableDeclaration) */
		@Override
		protected ListRewrite getParamgumentsRewrite() {
			return getASTRewrite().getListRewrite(fLambdaDecl, LambdaExpression.PARAMETERS_PROPERTY);
		}

		@Override
		protected VariableDeclaration createNewParamgument(ParameterInfo info, List<ParameterInfo> parameterInfos, List<VariableDeclaration> nodes) {
			List<VariableDeclaration> parameters= fLambdaDecl.parameters();
			if (!parameters.isEmpty() && parameters.get(0) instanceof SingleVariableDeclaration) {
				return createNewSingleVariableDeclaration(info);
			}
			VariableDeclarationFragment newP= getASTRewrite().getAST().newVariableDeclarationFragment();
			newP.setName(getASTRewrite().getAST().newSimpleName(info.getNewName()));
			return newP;
		}

		@Override
		protected int getStartPosition() {
			return fLambdaDecl.getStartPosition();
		}

		@Override
		protected ASTNode getNode() {
			return fLambdaDecl;
		}

		@Override
		protected VariableDeclaration getParameter(int index) {
			return (VariableDeclaration) fLambdaDecl.parameters().get(index);
		}

		@Override
		protected SimpleName getMethodNameNode() {
			return null;
		}
	}

	class DeclarationUpdate extends AbstractDeclarationUpdate<SingleVariableDeclaration> {
		private MethodDeclaration fMethDecl;

		protected DeclarationUpdate(MethodDeclaration decl, CompilationUnitRewrite cuRewrite, RefactoringStatus result) {
			super(cuRewrite, cuRewrite.createGroupDescription(RefactoringCoreMessages.ChangeSignatureRefactoring_change_signature), result);
			fMethDecl= decl;
		}

		// Prevent import removing if delegate is created.
		@Override
		protected void registerImportRemoveNode(ASTNode node) {
			if (!fDelegateUpdating)
				super.registerImportRemoveNode(node);
		}

		@Override
		public void updateNode() throws CoreException {
			changeParamguments();

			if (canChangeNameAndReturnType()) {
				changeMethodName();
				changeReturnType();
			}

			if (needsVisibilityUpdate())
				changeVisibility();
			reshuffleElements();
			changeExceptions();

			changeJavadocTags();

			if (fBodyUpdater == null || fBodyUpdater.needsParameterUsedCheck())
				checkIfDeletedParametersUsed();

			if (fBodyUpdater != null)
				fBodyUpdater.updateBody(fMethDecl, fCuRewrite, fResult);

			if (fDelegateUpdating)
				addDelegate();
		}

		private void addDelegate() throws JavaModelException {

			DelegateMethodCreator creator= new DelegateMethodCreator();
			creator.setDeclaration(fMethDecl);
			creator.setDeclareDeprecated(fDelegateDeprecation);
			creator.setSourceRewrite(fCuRewrite);
			creator.prepareDelegate();

			/*
			 * The delegate now contains a call and a javadoc reference to the
			 * old method (i.e., to itself).
			 *
			 * Use ReferenceUpdate() / DocReferenceUpdate() to update these
			 * references like any other reference.
			 */
			final ASTNode delegateInvocation= creator.getDelegateInvocation();
			if (delegateInvocation != null)
				// may be null if the delegate is an interface method or
				// abstract -> no body
				new ReferenceUpdate(delegateInvocation, creator.getDelegateRewrite(), fResult).updateNode();
			MethodRef javadocReference= creator.getJavadocReference();
			if (javadocReference != null)
				new DocReferenceUpdate(javadocReference, creator.getDelegateRewrite(), fResult).updateNode();

			creator.createEdit();
		}

		/** @return {@inheritDoc} (element type: SingleVariableDeclaration) */
		@Override
		protected ListRewrite getParamgumentsRewrite() {
			return getASTRewrite().getListRewrite(fMethDecl, MethodDeclaration.PARAMETERS_PROPERTY);
		}

		private void changeReturnType() {
			if (isReturnTypeSameAsInitial())
				return;
			replaceTypeNode(fMethDecl.getReturnType2(), fReturnTypeInfo.getNewTypeName(), fReturnTypeInfo.getNewTypeBinding());
			removeExtraDimensions(fMethDecl);
			//Remove expression from return statement when changed to void? No, would lose information!
			//Could add return statement with default value and add todo comment, but compile error is better.
		}

		private void removeExtraDimensions(MethodDeclaration methDecl) {
			ListRewrite listRewrite= getASTRewrite().getListRewrite(methDecl, MethodDeclaration.EXTRA_DIMENSIONS2_PROPERTY);
			for (Dimension dimension : (List<Dimension>) methDecl.extraDimensions()) {
				listRewrite.remove(dimension, fDescription);
			}
		}

		private boolean needsVisibilityUpdate() throws JavaModelException {
			if (isVisibilitySameAsInitial())
				return false;
			if (isIncreasingVisibility())
				return JdtFlags.isHigherVisibility(fVisibility, JdtFlags.getVisibilityCode(fMethDecl));
			else
				return JdtFlags.isHigherVisibility(JdtFlags.getVisibilityCode(fMethDecl), fVisibility);
		}

		private boolean isIncreasingVisibility() throws JavaModelException{
			return JdtFlags.isHigherVisibility(fVisibility, JdtFlags.getVisibilityCode(fMethod));
		}

		private void changeVisibility() {
			ModifierRewrite.create(getASTRewrite(), fMethDecl).setVisibility(fVisibility, fDescription);
		}

		private void changeExceptions() {
			for (ExceptionInfo info : fExceptionInfos) {
				if (info.isOld())
					continue;
				if (info.isDeleted())
					removeExceptionFromNodeList(info, fMethDecl.thrownExceptionTypes());
				else
					addExceptionToNodeList(info, getASTRewrite().getListRewrite(fMethDecl, MethodDeclaration.THROWN_EXCEPTION_TYPES_PROPERTY));
			}
		}

		private void removeExceptionFromNodeList(ExceptionInfo toRemove, List<Type> list) {
			ITypeBinding typeToRemove= toRemove.getTypeBinding();
			for (Type currentExcType : list) {
				ITypeBinding currentType= currentExcType.resolveBinding();
				/* Maybe remove all subclasses of typeToRemove too.
				 * Problem:
				 * - B extends A;
				 * - A.m() throws IOException, Exception;
				 * - B.m() throws IOException, AWTException;
				 * Removing Exception should remove AWTException,
				 * but NOT remove IOException (or a subclass of JavaModelException). */
				 // if (Bindings.isSuperType(typeToRemove, currentType))
				if (currentType == null)
					continue; // newly added or unresolvable type
				if (Bindings.equals(currentType, typeToRemove) || toRemove.getElement().getElementName().equals(currentType.getName())) {
					getASTRewrite().remove(currentExcType, fDescription);
					registerImportRemoveNode(currentExcType);
				}
			}
		}

		private void addExceptionToNodeList(ExceptionInfo exceptionInfo, ListRewrite exceptionListRewrite) {
			String fullyQualified= exceptionInfo.getFullyQualifiedName();
			for (Iterator<? extends ASTNode> iter= exceptionListRewrite.getOriginalList().iterator(); iter.hasNext(); ) {
				Type exType= (Type) iter.next();
				//XXX: existing superclasses of the added exception are redundant and could be removed
				ITypeBinding typeBinding= exType.resolveBinding();
				if (typeBinding == null)
					continue; // newly added or unresolvable type
				if (typeBinding.getQualifiedName().equals(fullyQualified))
					return; // don't add it again
			}
			String importedType= getImportRewrite().addImport(exceptionInfo.getFullyQualifiedName());
			getImportRemover().registerAddedImport(importedType);
			ASTNode exNode= getASTRewrite().createStringPlaceholder(importedType, ASTNode.SIMPLE_TYPE);
			exceptionListRewrite.insertLast(exNode, fDescription);
		}

		private void changeJavadocTags() throws JavaModelException {
			//update tags in javadoc: @param, @return, @exception, @throws, ...
			Javadoc javadoc= fMethDecl.getJavadoc();
			if (javadoc == null)
				return;

			ITypeBinding typeBinding= Bindings.getBindingOfParentType(fMethDecl);
			if (typeBinding == null)
				return;
			IMethodBinding methodBinding= fMethDecl.resolveBinding();
			if (methodBinding == null)
				return;

			boolean isTopOfRipple= (Bindings.findOverriddenMethod(methodBinding, false) == null);
			//add tags: only iff top of ripple; change and remove: always.
			//TODO: should have preference for adding tags in (overriding) methods (with template: todo, inheritDoc, ...)

			List<TagElement> tags= javadoc.tags();
			ListRewrite tagsRewrite= getASTRewrite().getListRewrite(javadoc, Javadoc.TAGS_PROPERTY);

			if (! isReturnTypeSameAsInitial()) {
				if (PrimitiveType.VOID.toString().equals(fReturnTypeInfo.getNewTypeName())) {
					for (TagElement tag : tags) {
						if (TagElement.TAG_RETURN.equals(tag.getTagName())) {
							getASTRewrite().remove(tag, fDescription);
							registerImportRemoveNode(tag);
						}
					}
				} else if (isTopOfRipple && Signature.SIG_VOID.equals(fMethod.getReturnType())){
					TagElement returnNode= createReturnTag();
					TagElement previousTag= findTagElementToInsertAfter(tags, TagElement.TAG_RETURN);
					insertTag(returnNode, previousTag, tagsRewrite);
					tags= tagsRewrite.getRewrittenList();
				}
			}

			if (!areNamesSameAsInitial() || !isOrderSameAsInitial()) {
				ArrayList<TagElement> paramTags= new ArrayList<>(); // <TagElement>, only not deleted tags with simpleName
				// delete & rename:
				for (TagElement tag : tags) {
					String tagName= tag.getTagName();
					List<? extends ASTNode> fragments= tag.fragments();
					if (!TagElement.TAG_PARAM.equals(tagName)
							|| fragments.isEmpty()
							|| !(fragments.get(0) instanceof SimpleName))
						continue;
					SimpleName simpleName= (SimpleName) fragments.get(0);
					String identifier= simpleName.getIdentifier();
					boolean removed= false;
					for (ParameterInfo info : fParameterInfos) {
						if (identifier.equals(info.getOldName())) {
							if (info.isDeleted()) {
								getASTRewrite().remove(tag, fDescription);
								registerImportRemoveNode(tag);
								removed= true;
							} else if (info.isRenamed()) {
								SimpleName newName= simpleName.getAST().newSimpleName(info.getNewName());
								getASTRewrite().replace(simpleName, newName, fDescription);
								registerImportRemoveNode(tag);
							}
							break;
						}
					}
					if (! removed)
						paramTags.add(tag);
				}
				tags= tagsRewrite.getRewrittenList();

				if (! isOrderSameAsInitial()) {
					// reshuffle (sort in declaration sequence) & add (only add to top of ripple):
					TagElement previousTag= findTagElementToInsertAfter(tags, TagElement.TAG_PARAM);
					boolean first= true; // workaround for bug 92111: preserve first tag if possible
					// reshuffle:
					for (ParameterInfo info : fParameterInfos) {
						String oldName= info.getOldName();
						String newName= info.getNewName();
						if (info.isAdded()) {
							first= false;
							if (! isTopOfRipple)
								continue;
							TagElement paramNode= JavadocUtil.createParamTag(newName, fCuRewrite.getRoot().getAST(), fCuRewrite.getCu().getJavaProject());
							insertTag(paramNode, previousTag, tagsRewrite);
							previousTag= paramNode;
						} else {
							for (Iterator<TagElement> tagIter= paramTags.iterator(); tagIter.hasNext();) {
								TagElement tag= tagIter.next();
								SimpleName tagName= (SimpleName) tag.fragments().get(0);
								if (oldName.equals(tagName.getIdentifier())) {
									tagIter.remove();
									if (first) {
										previousTag= tag;
									} else {
										TagElement movedTag= (TagElement) getASTRewrite().createMoveTarget(tag);
										getASTRewrite().remove(tag, fDescription);
										insertTag(movedTag, previousTag, tagsRewrite);
										previousTag= movedTag;
									}
								}
								first= false;
							}
						}
					}
					// params with bad names:
					for (TagElement tag : paramTags) {
						TagElement movedTag= (TagElement) getASTRewrite().createMoveTarget(tag);
						getASTRewrite().remove(tag, fDescription);
						insertTag(movedTag, previousTag, tagsRewrite);
						previousTag= movedTag;
					}
				}
				tags= tagsRewrite.getRewrittenList();
			}

			if (! areExceptionsSameAsInitial()) {
				// collect exceptionTags and remove deleted:
				ArrayList<TagElement> exceptionTags= new ArrayList<>(); // <TagElement>, only not deleted tags with name
				for (TagElement tag : tags) {
					if (! TagElement.TAG_THROWS.equals(tag.getTagName()) && ! TagElement.TAG_EXCEPTION.equals(tag.getTagName()))
						continue;
					if (tag.fragments().isEmpty() || !(tag.fragments().get(0) instanceof Name))
						continue;
					boolean tagDeleted= false;
					Name name= (Name) tag.fragments().get(0);
					for (ExceptionInfo info : fExceptionInfos) {
						if (info.isDeleted()) {
							boolean remove= false;
							final ITypeBinding nameBinding= name.resolveTypeBinding();
							if (nameBinding != null) {
								final ITypeBinding infoBinding= info.getTypeBinding();
								if ((infoBinding != null && Bindings.equals(infoBinding, nameBinding))
										|| info.getElement().getElementName().equals(nameBinding.getName())) {
									remove= true;
								}
								if (remove) {
									getASTRewrite().remove(tag, fDescription);
									registerImportRemoveNode(tag);
									tagDeleted= true;
									break;
								}
							}
						}
					}
					if (! tagDeleted)
						exceptionTags.add(tag);
				}
				// reshuffle:
				tags= tagsRewrite.getRewrittenList();
				TagElement previousTag= findTagElementToInsertAfter(tags, TagElement.TAG_THROWS);
				for (ExceptionInfo info : fExceptionInfos) {
					if (info.isAdded()) {
						if (!isTopOfRipple)
							continue;
						TagElement excptNode= createExceptionTag(info.getElement().getElementName());
						insertTag(excptNode, previousTag, tagsRewrite);
						previousTag= excptNode;
					} else {
						for (Iterator<TagElement> tagIter= exceptionTags.iterator(); tagIter.hasNext();) {
							TagElement tag= tagIter.next();
							Name tagName= (Name) tag.fragments().get(0);
							final ITypeBinding nameBinding= tagName.resolveTypeBinding();
							if (nameBinding != null) {
								boolean process= false;
								final ITypeBinding infoBinding= info.getTypeBinding();
								if ((infoBinding != null && Bindings.equals(infoBinding, nameBinding))
										|| info.getElement().getElementName().equals(nameBinding.getName())) {
									process= true;
								}
								if (process) {
									tagIter.remove();
									TagElement movedTag= (TagElement) getASTRewrite().createMoveTarget(tag);
									getASTRewrite().remove(tag, fDescription);
									insertTag(movedTag, previousTag, tagsRewrite);
									previousTag= movedTag;
								}
							}
						}
					}
				}
				// exceptions with bad names:
				for (TagElement tag : exceptionTags) {
					TagElement movedTag= (TagElement) getASTRewrite().createMoveTarget(tag);
					getASTRewrite().remove(tag, fDescription);
					insertTag(movedTag, previousTag, tagsRewrite);
					previousTag= movedTag;
				}
			}
		}

		private TagElement createReturnTag() {
			TagElement returnNode= getASTRewrite().getAST().newTagElement();
			returnNode.setTagName(TagElement.TAG_RETURN);

			TextElement textElement= getASTRewrite().getAST().newTextElement();
			String text= StubUtility.getTodoTaskTag(fCuRewrite.getCu().getJavaProject());
			if (text != null)
				textElement.setText(text); //TODO: use template with {@todo} ...
			returnNode.fragments().add(textElement);

			return returnNode;
		}

		private TagElement createExceptionTag(String simpleName) {
			TagElement excptNode= getASTRewrite().getAST().newTagElement();
			excptNode.setTagName(TagElement.TAG_THROWS);

			SimpleName nameNode= getASTRewrite().getAST().newSimpleName(simpleName);
			excptNode.fragments().add(nameNode);

			TextElement textElement= getASTRewrite().getAST().newTextElement();
			String text= StubUtility.getTodoTaskTag(fCuRewrite.getCu().getJavaProject());
			if (text != null)
				textElement.setText(text); //TODO: use template with {@todo} ...
			excptNode.fragments().add(textElement);

			return excptNode;
		}

		private void insertTag(TagElement tag, TagElement previousTag, ListRewrite tagsRewrite) {
			if (previousTag == null)
				tagsRewrite.insertFirst(tag, fDescription);
			else
				tagsRewrite.insertAfter(tag, previousTag, fDescription);
		}

		/**
		 * @param tags existing tags
		 * @param tagName name of tag to add
		 * @return the <code>TagElement<code> just before a new <code>TagElement</code> with name
		 *         <code>tagName</code>, or <code>null</code>.
		 */
		private TagElement findTagElementToInsertAfter(List<TagElement> tags, String tagName) {
			List<String> tagOrder= Arrays.asList(
					TagElement.TAG_AUTHOR,
					TagElement.TAG_VERSION,
					TagElement.TAG_PARAM,
					TagElement.TAG_RETURN,
					TagElement.TAG_THROWS,
					TagElement.TAG_EXCEPTION,
					TagElement.TAG_SEE,
					TagElement.TAG_SINCE,
					TagElement.TAG_SERIAL,
					TagElement.TAG_SERIALFIELD,
					TagElement.TAG_SERIALDATA,
					TagElement.TAG_DEPRECATED,
					TagElement.TAG_VALUE
			);
			int goalOrdinal= tagOrder.indexOf(tagName);
			if (goalOrdinal == -1) // unknown tag -> to end
				return (tags.isEmpty()) ? null : (TagElement) tags.get(tags.size());
			for (int i= 0; i < tags.size(); i++) {
				int tagOrdinal= tagOrder.indexOf(tags.get(i).getTagName());
				if (tagOrdinal >= goalOrdinal)
					return (i == 0) ? null : (TagElement) tags.get(i - 1);
			}
			return (tags.isEmpty()) ? null : (TagElement) tags.get(tags.size() - 1);
		}


		@Override
		protected SingleVariableDeclaration createNewParamgument(ParameterInfo info, List<ParameterInfo> parameterInfos, List<SingleVariableDeclaration> nodes) {
			return createNewSingleVariableDeclaration(info);
		}

		@Override
		protected ASTNode getNode() {
			return fMethDecl;
		}

		@Override
		protected VariableDeclaration getParameter(int index) {
			return (VariableDeclaration) fMethDecl.parameters().get(index);
		}

		@Override
		protected SimpleName getMethodNameNode() {
			return fMethDecl.getName();
		}

	}

	class DocReferenceUpdate extends OccurrenceUpdate<MethodRefParameter> {
		/** instanceof MemberRef || MethodRef */
		private ASTNode fNode;

		protected DocReferenceUpdate(ASTNode node, CompilationUnitRewrite cuRewrite, RefactoringStatus result) {
			super(cuRewrite, cuRewrite.createGroupDescription(RefactoringCoreMessages.ChangeSignatureRefactoring_update_javadoc_reference), result);
			fNode= node;
		}

		@Override
		public void updateNode() {
			if (fNode instanceof MethodRef) {
				changeParamguments();
				reshuffleElements();
			}
			if (canChangeNameAndReturnType())
				changeMethodName();
		}

		@Override
		protected MethodRefParameter createNewParamgument(ParameterInfo info, List<ParameterInfo> parameterInfos, List<MethodRefParameter> nodes) {
			return createNewMethodRefParameter(info);
		}

		private MethodRefParameter createNewMethodRefParameter(ParameterInfo info) {
			MethodRefParameter newP= getASTRewrite().getAST().newMethodRefParameter();

			// only add name iff first parameter already has a name:
			List<? extends ASTNode> parameters= getParamgumentsRewrite().getOriginalList();
			if (parameters.size() > 0)
				if (((MethodRefParameter) parameters.get(0)).getName() != null)
					newP.setName(getASTRewrite().getAST().newSimpleName(info.getNewName()));

			newP.setType(createNewDocRefType(info));
			newP.setVarargs(info.isNewVarargs());
			return newP;
		}

		private Type createNewDocRefType(ParameterInfo info) {
			String newTypeName= ParameterInfo.stripEllipsis(info.getNewTypeName());
			ITypeBinding newTypeBinding= info.getNewTypeBinding();
			if (newTypeBinding != null)
				newTypeBinding= newTypeBinding.getErasure(); //see bug 83127: Javadoc references are raw (erasures)
			return createNewTypeNode(newTypeName, newTypeBinding);
		}

		@Override
		protected SimpleName getMethodNameNode() {
			if (fNode instanceof MemberRef)
				return ((MemberRef) fNode).getName();

			if (fNode instanceof MethodRef)
				return ((MethodRef) fNode).getName();

			return null;
		}

		/** @return {@inheritDoc} (element type: MethodRefParameter) */
		@Override
		protected ListRewrite getParamgumentsRewrite() {
			return getASTRewrite().getListRewrite(fNode, MethodRef.PARAMETERS_PROPERTY);
		}

		@Override
		protected void changeParamgumentName(ParameterInfo info) {
			if (! (fNode instanceof MethodRef))
				return;

			MethodRefParameter oldParam= (MethodRefParameter) ((MethodRef) fNode).parameters().get(info.getOldIndex());
			SimpleName oldParamName= oldParam.getName();
			if (oldParamName != null)
				getASTRewrite().set(oldParamName, SimpleName.IDENTIFIER_PROPERTY, info.getNewName(), fDescription);
		}

		@Override
		protected void changeParamgumentType(ParameterInfo info) {
			if (! (fNode instanceof MethodRef))
				return;

			MethodRefParameter oldParam= (MethodRefParameter) ((MethodRef) fNode).parameters().get(info.getOldIndex());
			Type oldTypeNode= oldParam.getType();
			Type newTypeNode= createNewDocRefType(info);
			if (info.isNewVarargs()) {
				if (info.isOldVarargs() && ! oldParam.isVarargs()) {
					// leave as array reference if old reference was not vararg
					newTypeNode= ASTNodeFactory.newArrayType(newTypeNode);
				} else {
					getASTRewrite().set(oldParam, MethodRefParameter.VARARGS_PROPERTY, Boolean.TRUE, fDescription);
				}
			} else {
				if (oldParam.isVarargs()) {
					getASTRewrite().set(oldParam, MethodRefParameter.VARARGS_PROPERTY, Boolean.FALSE, fDescription);
				}
			}

			getASTRewrite().replace(oldTypeNode, newTypeNode, fDescription);
			registerImportRemoveNode(oldTypeNode);
		}
	}

	class StaticImportUpdate extends OccurrenceUpdate<ASTNode> {

		private final ImportDeclaration fImportDecl;

		public StaticImportUpdate(ImportDeclaration importDecl, CompilationUnitRewrite cuRewrite, RefactoringStatus result) {
			super(cuRewrite, null, result);
			fImportDecl= importDecl;
		}

		@Override
		public void updateNode() throws JavaModelException {
			ImportRewrite importRewrite= fCuRewrite.getImportRewrite();
			QualifiedName name= (QualifiedName) fImportDecl.getName();
			//will be removed by importRemover if not used elsewhere ... importRewrite.removeStaticImport(name.getFullyQualifiedName());
			importRewrite.addStaticImport(name.getQualifier().getFullyQualifiedName(), fMethodName, false);
		}

		@Override
		protected ListRewrite getParamgumentsRewrite() {
			return null;
		}

		@Override
		protected ASTNode createNewParamgument(ParameterInfo info, List<ParameterInfo> parameterInfos, List<ASTNode> nodes) {
			return null;
		}

		@Override
		protected SimpleName getMethodNameNode() {
			return null;
		}
	}

	class NullOccurrenceUpdate extends OccurrenceUpdate<ASTNode> {
		private ASTNode fNode;
		protected NullOccurrenceUpdate(ASTNode node, CompilationUnitRewrite cuRewrite, RefactoringStatus result) {
			super(cuRewrite, null, result);
			fNode= node;
		}
		@Override
		public void updateNode() throws JavaModelException {
			int start= fNode.getStartPosition();
			int length= fNode.getLength();
			String msg= "Cannot update found node: nodeType=" + fNode.getNodeType() + "; "  //$NON-NLS-1$//$NON-NLS-2$
					+ fNode.toString() + "[" + start + ", " + length + "] in " + fCuRewrite.getCu();  //$NON-NLS-1$//$NON-NLS-2$//$NON-NLS-3$
			JavaManipulationPlugin.log(new Exception(msg + ":\n" + fCuRewrite.getCu().getSource().substring(start, start + length))); //$NON-NLS-1$
			fResult.addError(msg, JavaStatusContext.create(fCuRewrite.getCu(), fNode));
		}
		@Override
		protected ListRewrite getParamgumentsRewrite() {
			return null;
		}
		@Override
		protected ASTNode createNewParamgument(ParameterInfo info, List<ParameterInfo> parameterInfos, List<ASTNode> nodes) {
			return null;
		}
		@Override
		protected SimpleName getMethodNameNode() {
			return null;
		}
	}

	private RefactoringStatus initialize(JavaRefactoringArguments arguments) {
		final String handle= arguments.getAttribute(JavaRefactoringDescriptorUtil.ATTRIBUTE_INPUT);
		if (handle != null) {
			final IJavaElement element= JavaRefactoringDescriptorUtil.handleToElement(arguments.getProject(), handle, false);
			if (element == null || !element.exists() || element.getElementType() != IJavaElement.METHOD)
				return JavaRefactoringDescriptorUtil.createInputFatalStatus(element, getProcessorName(), IJavaRefactorings.CHANGE_METHOD_SIGNATURE);
			else {
				fMethod= (IMethod) element;
				fMethodName= fMethod.getElementName();
				try {
					fVisibility= JdtFlags.getVisibilityCode(fMethod);
					fReturnTypeInfo= new ReturnTypeInfo(Signature.toString(Signature.getReturnType(fMethod.getSignature())));
				} catch (JavaModelException exception) {
					return RefactoringStatus.createFatalErrorStatus(Messages.format(RefactoringCoreMessages.InitializableRefactoring_illegal_argument, new Object[] { Integer.valueOf(fVisibility),
							ATTRIBUTE_VISIBILITY }));
				}
			}
		} else
			return RefactoringStatus.createFatalErrorStatus(Messages.format(RefactoringCoreMessages.InitializableRefactoring_argument_not_exist, JavaRefactoringDescriptorUtil.ATTRIBUTE_INPUT));
		final String name= arguments.getAttribute(JavaRefactoringDescriptorUtil.ATTRIBUTE_NAME);
		if (name != null) {
			fMethodName= name;
			final RefactoringStatus status= Checks.checkMethodName(fMethodName, fMethod);
			if (status.hasError())
				return status;
		} else
			return RefactoringStatus.createFatalErrorStatus(Messages.format(RefactoringCoreMessages.InitializableRefactoring_argument_not_exist, JavaRefactoringDescriptorUtil.ATTRIBUTE_NAME));
		final String type= arguments.getAttribute(ATTRIBUTE_RETURN);
		if (type != null && !"".equals(type)) //$NON-NLS-1$
			fReturnTypeInfo.setNewTypeName(type);
		final String visibility= arguments.getAttribute(ATTRIBUTE_VISIBILITY);
		if (visibility != null && !"".equals(visibility)) {//$NON-NLS-1$
			int flag= 0;
			try {
				flag= Integer.parseInt(visibility);
			} catch (NumberFormatException exception) {
				return RefactoringStatus.createFatalErrorStatus(Messages.format(RefactoringCoreMessages.InitializableRefactoring_argument_not_exist, ATTRIBUTE_VISIBILITY));
			}
			fVisibility= flag;
		}
		int count= 1;
		String attribute= ATTRIBUTE_PARAMETER + count;
		String value= null;
		fParameterInfos= new ArrayList<>(3);
		while ((value= arguments.getAttribute(attribute)) != null) {
			StringTokenizer tokenizer= new StringTokenizer(value, " "); //$NON-NLS-1$
			if (tokenizer.countTokens() < 6)
				return RefactoringStatus.createFatalErrorStatus(Messages.format(RefactoringCoreMessages.InitializableRefactoring_illegal_argument, new Object[] { value, ATTRIBUTE_PARAMETER }));
			String oldTypeName= tokenizer.nextToken();
			String oldName= tokenizer.nextToken();
			String oldIndex= tokenizer.nextToken();
			String newTypeName= tokenizer.nextToken();
			String newName= tokenizer.nextToken();
			String deleted= tokenizer.nextToken();
			ParameterInfo info= null;
			try {
				int index= Integer.parseInt(oldIndex);
				if (index == -1) {
					String result= arguments.getAttribute(ATTRIBUTE_DEFAULT + count);
					if (result == null)
						result= ""; //$NON-NLS-1$
					info= ParameterInfo.createInfoForAddedParameter(newTypeName, newName, result);
				} else {
					info= new ParameterInfo(oldTypeName, oldName, index);
					info.setNewTypeName(newTypeName);
					info.setNewName(newName);
					if (Boolean.parseBoolean(deleted))
						info.markAsDeleted();
				}
				fParameterInfos.add(info);
			} catch (NumberFormatException exception) {
				return RefactoringStatus.createFatalErrorStatus(Messages.format(RefactoringCoreMessages.InitializableRefactoring_illegal_argument, new Object[] { value, ATTRIBUTE_PARAMETER }));
			}
			count++;
			attribute= ATTRIBUTE_PARAMETER + count;
		}
		count= 1;
		fExceptionInfos= new ArrayList<>(2);
		attribute= JavaRefactoringDescriptorUtil.ATTRIBUTE_ELEMENT + count;
		while ((value= arguments.getAttribute(attribute)) != null) {
			ExceptionInfo info= null;
			final String kind= arguments.getAttribute(ATTRIBUTE_KIND + count);
			if (kind != null) {
				final IJavaElement element= JavaRefactoringDescriptorUtil.handleToElement(arguments.getProject(), value, false);
				if (element == null || !element.exists())
					return JavaRefactoringDescriptorUtil.createInputFatalStatus(element, getProcessorName(), IJavaRefactorings.CHANGE_METHOD_SIGNATURE);
				else {
					try {
						info= new ExceptionInfo(element, Integer.parseInt(kind), null);
					} catch (NumberFormatException exception) {
						return RefactoringStatus.createFatalErrorStatus(Messages.format(RefactoringCoreMessages.InitializableRefactoring_illegal_argument, new Object[] { kind, ATTRIBUTE_KIND }));
					}
				}
			} else
				return RefactoringStatus.createFatalErrorStatus(Messages.format(RefactoringCoreMessages.InitializableRefactoring_illegal_argument, new Object[] { kind, ATTRIBUTE_KIND }));
			fExceptionInfos.add(info);
			count++;
			attribute= JavaRefactoringDescriptorUtil.ATTRIBUTE_ELEMENT + count;
		}
		final String deprecate= arguments.getAttribute(ATTRIBUTE_DEPRECATE);
		if (deprecate != null) {
			fDelegateDeprecation= Boolean.parseBoolean(deprecate);
		} else
			return RefactoringStatus.createFatalErrorStatus(Messages.format(RefactoringCoreMessages.InitializableRefactoring_argument_not_exist, ATTRIBUTE_DEPRECATE));
		final String delegate= arguments.getAttribute(ATTRIBUTE_DELEGATE);
		if (delegate != null) {
			fDelegateUpdating= Boolean.parseBoolean(delegate);
		} else
			return RefactoringStatus.createFatalErrorStatus(Messages.format(RefactoringCoreMessages.InitializableRefactoring_argument_not_exist, ATTRIBUTE_DELEGATE));
		return new RefactoringStatus();
	}

	/**
	 * If this occurrence update is called from within a declaration update
	 * (i.e., to update the call inside the newly created delegate), the old
	 * node does not yet exist and therefore cannot be a move target.
	 *
	 * Normally, always use createMoveTarget as this has the advantage of
	 * being able to add changes inside changed nodes (for example, a method
	 * call within a method call, see test case #4) and preserving comments
	 * inside calls.
	 * @param oldNode original node
	 * @param rewrite an AST rewrite
	 * @return the node to insert at the target location
	 */
	protected <T extends ASTNode> T moveNode(T oldNode, ASTRewrite rewrite) {
		T movedNode;
		if (ASTNodes.isExistingNode(oldNode))
			movedNode= ASTNodes.createMoveTarget(rewrite, oldNode); //node must be one of ast
		else
			movedNode= ASTNodes.copySubtree(rewrite.getAST(), oldNode);
		return movedNode;
	}

	public IDefaultValueAdvisor getDefaultValueAdvisor() {
		return fDefaultValueAdvisor;
	}

	public void setDefaultValueAdvisor(IDefaultValueAdvisor defaultValueAdvisor) {
		fDefaultValueAdvisor= defaultValueAdvisor;
	}

	@Override
	public Object[] getElements() {
		return new Object[] { fMethod };
	}

	@Override
	public String getIdentifier() {
		return IRefactoringProcessorIds.CHANGE_METHOD_SIGNATURE_PROCESSOR;
	}

	@Override
	public boolean isApplicable() throws CoreException {
		return RefactoringAvailabilityTester.isChangeSignatureAvailable(fMethod);
	}

	@Override
	public RefactoringParticipant[] loadParticipants(RefactoringStatus status, SharableParticipants sharedParticipants) throws CoreException {
		String[] affectedNatures= JavaProcessors.computeAffectedNatures(fMethod);
		return JavaParticipantManager.loadChangeMethodSignatureParticipants(status, this, fMethod, getParticipantArguments(), null, affectedNatures, sharedParticipants);
	}
}
