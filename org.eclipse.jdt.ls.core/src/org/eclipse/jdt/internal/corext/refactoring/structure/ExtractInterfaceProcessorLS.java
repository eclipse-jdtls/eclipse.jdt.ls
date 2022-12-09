/*******************************************************************************
 * Copyright (c) 2000, 2021 IBM Corporation and others.
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
 *     Jerome Cambon <jerome.cambon@oracle.com> - [code style] don't generate redundant modifiers "public static final abstract" for interface members - https://bugs.eclipse.org/71627
 *     Microsoft Corporation - read formatting options from the compilation unit
 *******************************************************************************/
package org.eclipse.jdt.internal.corext.refactoring.structure;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.eclipse.core.runtime.Assert;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.core.runtime.OperationCanceledException;
import org.eclipse.core.runtime.SubProgressMonitor;

import org.eclipse.core.resources.IFile;

import org.eclipse.core.filebuffers.ITextFileBuffer;

import org.eclipse.text.edits.MalformedTreeException;
import org.eclipse.text.edits.TextEdit;

import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.Document;
import org.eclipse.jface.text.IDocument;

import org.eclipse.ltk.core.refactoring.Change;
import org.eclipse.ltk.core.refactoring.GroupCategory;
import org.eclipse.ltk.core.refactoring.GroupCategorySet;
import org.eclipse.ltk.core.refactoring.RefactoringDescriptor;
import org.eclipse.ltk.core.refactoring.RefactoringStatus;
import org.eclipse.ltk.core.refactoring.TextChange;
import org.eclipse.ltk.core.refactoring.TextEditBasedChange;
import org.eclipse.ltk.core.refactoring.participants.CheckConditionsContext;
import org.eclipse.ltk.core.refactoring.participants.RefactoringParticipant;
import org.eclipse.ltk.core.refactoring.participants.SharableParticipants;

import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IField;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IMember;
import org.eclipse.jdt.core.IMethod;
import org.eclipse.jdt.core.IPackageFragment;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.ITypeParameter;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.ASTRequestor;
import org.eclipse.jdt.core.dom.AbstractTypeDeclaration;
import org.eclipse.jdt.core.dom.Annotation;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.EnumDeclaration;
import org.eclipse.jdt.core.dom.FieldDeclaration;
import org.eclipse.jdt.core.dom.IBinding;
import org.eclipse.jdt.core.dom.IExtendedModifier;
import org.eclipse.jdt.core.dom.IMethodBinding;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.IVariableBinding;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.Modifier;
import org.eclipse.jdt.core.dom.NodeFinder;
import org.eclipse.jdt.core.dom.ParameterizedType;
import org.eclipse.jdt.core.dom.RecordDeclaration;
import org.eclipse.jdt.core.dom.SingleVariableDeclaration;
import org.eclipse.jdt.core.dom.Type;
import org.eclipse.jdt.core.dom.TypeDeclaration;
import org.eclipse.jdt.core.dom.VariableDeclarationFragment;
import org.eclipse.jdt.core.dom.rewrite.ASTRewrite;
import org.eclipse.jdt.core.dom.rewrite.ITrackedNodePosition;
import org.eclipse.jdt.core.dom.rewrite.ImportRewrite;
import org.eclipse.jdt.core.dom.rewrite.ListRewrite;
import org.eclipse.jdt.core.manipulation.CodeGeneration;
import org.eclipse.jdt.core.refactoring.IJavaRefactorings;
import org.eclipse.jdt.core.refactoring.descriptors.ExtractInterfaceDescriptor;
import org.eclipse.jdt.core.refactoring.descriptors.JavaRefactoringDescriptor;

import org.eclipse.jdt.internal.core.manipulation.JavaElementLabelsCore;
import org.eclipse.jdt.internal.core.manipulation.JavaManipulationPlugin;
import org.eclipse.jdt.internal.core.manipulation.StubUtility;
import org.eclipse.jdt.internal.core.manipulation.util.BasicElementLabels;
import org.eclipse.jdt.internal.core.manipulation.util.Strings;
import org.eclipse.jdt.internal.core.refactoring.descriptors.RefactoringSignatureDescriptorFactory;
import org.eclipse.jdt.internal.corext.codemanipulation.CodeGenerationSettings;
import org.eclipse.jdt.internal.corext.codemanipulation.StubUtility2Core;
import org.eclipse.jdt.internal.corext.dom.Bindings;
import org.eclipse.jdt.internal.corext.dom.IASTSharedValues;
import org.eclipse.jdt.internal.corext.dom.ModifierRewrite;
import org.eclipse.jdt.internal.corext.refactoring.Checks;
import org.eclipse.jdt.internal.corext.refactoring.JDTRefactoringDescriptorComment;
import org.eclipse.jdt.internal.corext.refactoring.JavaRefactoringArguments;
import org.eclipse.jdt.internal.corext.refactoring.JavaRefactoringDescriptorUtil;
import org.eclipse.jdt.internal.corext.refactoring.RefactoringCoreMessages;
import org.eclipse.jdt.internal.corext.refactoring.changes.CreateCompilationUnitChange;
import org.eclipse.jdt.internal.corext.refactoring.changes.DynamicValidationRefactoringChange;
import org.eclipse.jdt.internal.corext.refactoring.reorg.ASTNodeDeleteUtil;
import org.eclipse.jdt.internal.corext.refactoring.structure.constraints.SuperTypeConstraintsModel;
import org.eclipse.jdt.internal.corext.refactoring.structure.constraints.SuperTypeConstraintsSolver;
import org.eclipse.jdt.internal.corext.refactoring.structure.constraints.SuperTypeRefactoringProcessor;
import org.eclipse.jdt.internal.corext.refactoring.typeconstraints.CompilationUnitRange;
import org.eclipse.jdt.internal.corext.refactoring.typeconstraints.types.TType;
import org.eclipse.jdt.internal.corext.refactoring.typeconstraints2.ITypeConstraintVariable;
import org.eclipse.jdt.internal.corext.refactoring.util.JavaStatusContext;
import org.eclipse.jdt.internal.corext.refactoring.util.RefactoringASTParser;
import org.eclipse.jdt.internal.corext.refactoring.util.RefactoringFileBuffers;
import org.eclipse.jdt.internal.corext.refactoring.util.ResourceUtil;
import org.eclipse.jdt.internal.corext.refactoring.util.TextEditBasedChangeManager;
import org.eclipse.jdt.internal.corext.util.JavaModelUtil;
import org.eclipse.jdt.internal.corext.util.JdtFlags;
import org.eclipse.jdt.internal.corext.util.Messages;

import org.eclipse.jdt.internal.ui.preferences.JavaPreferencesSettings;

/**
 * Refactoring processor to extract interfaces.
 */
public final class ExtractInterfaceProcessorLS extends SuperTypeRefactoringProcessor {

	private static final String ATTRIBUTE_COMMENTS= "comments"; //$NON-NLS-1$

	/** The identifier of this processor */
	public static final String IDENTIFIER= "org.eclipse.jdt.ui.extractInterfaceProcessor"; //$NON-NLS-1$

	/** The extract interface group category set */
	private static final GroupCategorySet SET_EXTRACT_INTERFACE= new GroupCategorySet(new GroupCategory("org.eclipse.jdt.internal.corext.extractInterface", //$NON-NLS-1$
			RefactoringCoreMessages.ExtractInterfaceProcessor_category_name, RefactoringCoreMessages.ExtractInterfaceProcessor_category_description));

	/**
	 * Is the specified member extractable from the type?
	 *
	 * @param member
	 *            the member to test
	 * @return <code>true</code> if the member is extractable,
	 *         <code>false</code> otherwise
	 * @throws JavaModelException
	 *             if an error occurs
	 */
	protected static boolean isExtractableMember(final IMember member) throws JavaModelException {
		Assert.isNotNull(member);
		switch (member.getElementType()) {
			case IJavaElement.METHOD:
				return JdtFlags.isPublic(member) && !JdtFlags.isStatic(member) && !((IMethod) member).isConstructor();
			case IJavaElement.FIELD:
				return JdtFlags.isPublic(member) && JdtFlags.isStatic(member) && JdtFlags.isFinal(member) && !JdtFlags.isEnum(member);
			default:
				return false;
		}
	}

	/** Should override annotations be generated? */
	private boolean fAnnotations= false;

	/** The text edit based change manager */
	private TextEditBasedChangeManager fChangeManager= null;

	/** Should comments be generated? */
	private boolean fComments= true;

	/** The members to extract */
	private IMember[] fMembers= null;

	/** The subtype where to extract the supertype */
	private IType fSubType;

	/** The supertype name */
	private String fSuperName;

	/** The supertype fragment */
	private IPackageFragment fFragment = null;

	/** The source of the new supertype */
	private String fSuperSource= null;

	/**
	 * Creates a new extract interface processor.
	 *
	 * @param type
	 *            the type where to extract the supertype, or <code>null</code>
	 *            if invoked by scripting
	 * @param settings
	 *            the code generation settings, or <code>null</code> if
	 *            invoked by scripting
	 */
	public ExtractInterfaceProcessorLS(final IType type, final CodeGenerationSettings settings) {
		super(settings);
		fSubType= type;
		if (fSubType != null)
			fSuperName= fSubType.getElementName();
	}

	/**
	 * Creates a new extract interface processor from refactoring arguments.
	 *
	 * @param arguments
	 *            the refactoring arguments
	 * @param status
	 *            the resulting status
	 */
	public ExtractInterfaceProcessorLS(JavaRefactoringArguments arguments, RefactoringStatus status) {
		super(null);
		RefactoringStatus initializeStatus= initialize(arguments);
		status.merge(initializeStatus);
	}

	/*
	 * @see org.eclipse.ltk.core.refactoring.participants.RefactoringProcessor#checkFinalConditions(org.eclipse.core.runtime.IProgressMonitor,org.eclipse.ltk.core.refactoring.participants.CheckConditionsContext)
	 */
	@Override
	public RefactoringStatus checkFinalConditions(final IProgressMonitor monitor, final CheckConditionsContext context) throws CoreException, OperationCanceledException {
		Assert.isNotNull(monitor);
		Assert.isNotNull(context);
		final RefactoringStatus status= new RefactoringStatus();
		fChangeManager= new TextEditBasedChangeManager();
		try {
			monitor.beginTask("", 1); //$NON-NLS-1$
			monitor.setTaskName(RefactoringCoreMessages.ExtractInterfaceProcessor_checking);
			status.merge(Checks.checkIfCuBroken(fSubType));
			if (!status.hasError()) {
				if (fSubType.isBinary() || fSubType.isReadOnly() || !fSubType.exists())
					status.merge(RefactoringStatus.createFatalErrorStatus(RefactoringCoreMessages.ExtractInterfaceProcessor_no_binary, JavaStatusContext.create(fSubType)));
				else if (fSubType.isAnonymous())
					status.merge(RefactoringStatus.createFatalErrorStatus(RefactoringCoreMessages.ExtractInterfaceProcessor_no_anonymous, JavaStatusContext.create(fSubType)));
				else if (fSubType.isAnnotation())
					status.merge(RefactoringStatus.createFatalErrorStatus(RefactoringCoreMessages.ExtractInterfaceProcessor_no_annotation, JavaStatusContext.create(fSubType)));
				else {
					status.merge(checkSuperType());
					if (!status.hasFatalError()) {
						fChangeManager= createChangeManager(new SubProgressMonitor(monitor, 1), status);
						if (!status.hasFatalError()) {
							Checks.addModifiedFilesToChecker(ResourceUtil.getFiles(fChangeManager.getAllCompilationUnits()), context);
						}
					}
				}
			}
		} finally {
			monitor.done();
		}
		return status;
	}

	/*
	 * @see org.eclipse.ltk.core.refactoring.participants.RefactoringProcessor#checkInitialConditions(org.eclipse.core.runtime.IProgressMonitor)
	 */
	@Override
	public RefactoringStatus checkInitialConditions(final IProgressMonitor monitor) throws CoreException, OperationCanceledException {
		Assert.isNotNull(monitor);
		final RefactoringStatus status= new RefactoringStatus();
		try {
			monitor.beginTask("", 1); //$NON-NLS-1$
			monitor.setTaskName(RefactoringCoreMessages.ExtractInterfaceProcessor_checking);
			status.merge(Checks.checkIfCuBroken(fSubType));
			monitor.worked(1);
		} finally {
			monitor.done();
		}
		return status;
	}

	/**
	 * Checks whether the supertype clashes with existing types.
	 *
	 * @return the status of the condition checking
	 * @throws JavaModelException
	 *             if an error occurs
	 */
	protected RefactoringStatus checkSuperType() throws JavaModelException {
		final IPackageFragment fragment= this.getPackageFragment();
		final IType type= Checks.findTypeInPackage(fragment, fSuperName);
		if (type != null && type.exists()) {
			if (fragment.isDefaultPackage())
				return RefactoringStatus.createFatalErrorStatus(Messages.format(RefactoringCoreMessages.ExtractInterfaceProcessor_existing_default_type, BasicElementLabels.getJavaElementName(fSuperName)));
			else {
				String packageLabel= JavaElementLabelsCore.getElementLabel(fragment, JavaElementLabelsCore.ALL_DEFAULT);
				return RefactoringStatus.createFatalErrorStatus(Messages.format(RefactoringCoreMessages.ExtractInterfaceProcessor_existing_type, new String[] { BasicElementLabels.getJavaElementName(fSuperName), packageLabel }));
			}
		}
		return new RefactoringStatus();
	}

	/**
	 * Checks whether the type name is valid.
	 *
	 * @param name
	 *            the name to check
	 * @return the status of the condition checking
	 */
	public RefactoringStatus checkTypeName(final String name) {
		Assert.isNotNull(name);
		try {
			final RefactoringStatus result= Checks.checkTypeName(name, fSubType);
			if (result.hasFatalError())
				return result;
			final String unitName= JavaModelUtil.getRenamedCUName(fSubType.getCompilationUnit(), name);
			result.merge(Checks.checkCompilationUnitName(unitName, fSubType));
			if (result.hasFatalError())
				return result;
			final IPackageFragment fragment= this.getPackageFragment();
			if (fragment.getCompilationUnit(unitName).exists()) {
				result.addFatalError(Messages.format(RefactoringCoreMessages.ExtractInterfaceProcessor_existing_compilation_unit, new String[] { BasicElementLabels.getResourceName(unitName), JavaElementLabelsCore.getElementLabel(fragment, JavaElementLabelsCore.ALL_DEFAULT) }));
				return result;
			}
			result.merge(checkSuperType());
			return result;
		} catch (JavaModelException exception) {
			return RefactoringStatus.createFatalErrorStatus(RefactoringCoreMessages.ExtractInterfaceProcessor_internal_error);
		}
	}

	/*
	 * @see org.eclipse.ltk.core.refactoring.participants.RefactoringProcessor#createChange(org.eclipse.core.runtime.IProgressMonitor)
	 */
	@Override
	public Change createChange(final IProgressMonitor monitor) throws CoreException, OperationCanceledException {
		Assert.isNotNull(monitor);
		try {
			monitor.beginTask("", 1); //$NON-NLS-1$
			monitor.setTaskName(RefactoringCoreMessages.ExtractInterfaceProcessor_creating);
			final Map<String, String> arguments= new HashMap<>();
			String project= null;
			final IJavaProject javaProject= fSubType.getJavaProject();
			if (javaProject != null)
				project= javaProject.getElementName();
			int flags= JavaRefactoringDescriptor.JAR_MIGRATION | JavaRefactoringDescriptor.JAR_REFACTORING | RefactoringDescriptor.STRUCTURAL_CHANGE | RefactoringDescriptor.MULTI_CHANGE;
			try {
				if (fSubType.isLocal() || fSubType.isAnonymous())
					flags|= JavaRefactoringDescriptor.JAR_SOURCE_ATTACHMENT;
			} catch (JavaModelException exception) {
				JavaManipulationPlugin.log(exception);
			}
			final IPackageFragment fragment= this.getPackageFragment();
			final ICompilationUnit cu= fragment.getCompilationUnit(JavaModelUtil.getRenamedCUName(fSubType.getCompilationUnit(), fSuperName));
			final IType type= cu.getType(fSuperName);
			final String description= Messages.format(RefactoringCoreMessages.ExtractInterfaceProcessor_description_descriptor_short, BasicElementLabels.getJavaElementName(fSuperName));
			final String header= Messages.format(RefactoringCoreMessages.ExtractInterfaceProcessor_descriptor_description, new String[] { JavaElementLabelsCore.getElementLabel(type, JavaElementLabelsCore.ALL_FULLY_QUALIFIED), JavaElementLabelsCore.getElementLabel(fSubType, JavaElementLabelsCore.ALL_FULLY_QUALIFIED) });
			final JDTRefactoringDescriptorComment comment= new JDTRefactoringDescriptorComment(project, this, header);
			comment.addSetting(Messages.format(RefactoringCoreMessages.ExtractInterfaceProcessor_refactored_element_pattern, JavaElementLabelsCore.getElementLabel(type, JavaElementLabelsCore.ALL_FULLY_QUALIFIED)));
			final String[] settings= new String[fMembers.length];
			for (int index= 0; index < settings.length; index++)
				settings[index]= JavaElementLabelsCore.getElementLabel(fMembers[index], JavaElementLabelsCore.ALL_FULLY_QUALIFIED);
			comment.addSetting(JDTRefactoringDescriptorComment.createCompositeSetting(RefactoringCoreMessages.ExtractInterfaceProcessor_extracted_members_pattern, settings));
			addSuperTypeSettings(comment, true);
			final ExtractInterfaceDescriptor descriptor= RefactoringSignatureDescriptorFactory.createExtractInterfaceDescriptor(project, description, comment.asString(), arguments, flags);
			arguments.put(JavaRefactoringDescriptorUtil.ATTRIBUTE_INPUT, JavaRefactoringDescriptorUtil.elementToHandle(project, fSubType));
			arguments.put(JavaRefactoringDescriptorUtil.ATTRIBUTE_NAME, fSuperName);
			for (int index= 0; index < fMembers.length; index++)
				arguments.put(JavaRefactoringDescriptorUtil.ATTRIBUTE_ELEMENT + (index + 1), JavaRefactoringDescriptorUtil.elementToHandle(project, fMembers[index]));
			arguments.put(ATTRIBUTE_COMMENTS, Boolean.toString(fComments));
			arguments.put(ATTRIBUTE_REPLACE, Boolean.toString(fReplace));
			arguments.put(ATTRIBUTE_INSTANCEOF, Boolean.toString(fInstanceOf));
			final DynamicValidationRefactoringChange change= new DynamicValidationRefactoringChange(descriptor, RefactoringCoreMessages.ExtractInterfaceRefactoring_name, fChangeManager.getAllChanges());
			final IFile file= ResourceUtil.getFile(fSubType.getCompilationUnit());
			if (fSuperSource != null && fSuperSource.length() > 0)
				change.add(new CreateCompilationUnitChange(this.getPackageFragment().getCompilationUnit(JavaModelUtil.getRenamedCUName(fSubType.getCompilationUnit(), fSuperName)), fSuperSource, file.getCharset(false)));
			monitor.worked(1);
			return change;
		} finally {
			monitor.done();
		}
	}

	/**
	 * Creates the text change manager for this processor.
	 *
	 * @param monitor
	 *            the progress monitor to display progress
	 * @param status
	 *            the refactoring status
	 * @return the created text change manager
	 * @throws JavaModelException
	 *             if the method declaration could not be found
	 * @throws CoreException
	 *             if the changes could not be generated
	 */
	protected TextEditBasedChangeManager createChangeManager(final IProgressMonitor monitor, final RefactoringStatus status) throws JavaModelException, CoreException {
		Assert.isNotNull(status);
		Assert.isNotNull(monitor);
		try {
			monitor.beginTask("", 300); //$NON-NLS-1$
			monitor.setTaskName(RefactoringCoreMessages.ExtractInterfaceProcessor_creating);
			resetEnvironment();
			final TextEditBasedChangeManager manager= new TextEditBasedChangeManager();
			final CompilationUnitRewrite sourceRewrite= new CompilationUnitRewrite(fSubType.getCompilationUnit());
			final AbstractTypeDeclaration declaration= ASTNodeSearchUtil.getAbstractTypeDeclarationNode(fSubType, sourceRewrite.getRoot());
			if (declaration != null) {
				createTypeSignature(sourceRewrite, declaration, status, new SubProgressMonitor(monitor, 20));
				final IField[] fields= getExtractedFields(fSubType.getCompilationUnit());
				if (fields.length > 0)
					ASTNodeDeleteUtil.markAsDeleted(fields, sourceRewrite, sourceRewrite.createCategorizedGroupDescription(RefactoringCoreMessages.ExtractInterfaceProcessor_remove_field_label, SET_EXTRACT_INTERFACE));
				if (fSubType.isInterface()) {
					final IMethod[] methods= getExtractedMethods(fSubType.getCompilationUnit());
					if (methods.length > 0)
						ASTNodeDeleteUtil.markAsDeleted(methods, sourceRewrite, sourceRewrite.createCategorizedGroupDescription(RefactoringCoreMessages.ExtractInterfaceProcessor_remove_method_label, SET_EXTRACT_INTERFACE));
				}
				final String name= JavaModelUtil.getRenamedCUName(fSubType.getCompilationUnit(), fSuperName);
				final ICompilationUnit original= this.getPackageFragment().getCompilationUnit(name);
				final ICompilationUnit copy= getSharedWorkingCopy(original.getPrimary(), new SubProgressMonitor(monitor, 20));
				fSuperSource= createTypeSource(copy, fSubType, fSuperName, sourceRewrite, declaration, status, new SubProgressMonitor(monitor, 40));
				if (fSuperSource != null) {
					copy.getBuffer().setContents(fSuperSource);
					JavaModelUtil.reconcile(copy);
				}
				final Set<String> replacements= new HashSet<>();
				if (fReplace)
					rewriteTypeOccurrences(manager, sourceRewrite, copy, replacements, status, new SubProgressMonitor(monitor, 220));
				rewriteSourceMethods(sourceRewrite, replacements);
				manager.manage(fSubType.getCompilationUnit(), sourceRewrite.createChange(true));
			}
			return manager;
		} finally {
			monitor.done();
		}
	}

	/*
	 * @see org.eclipse.jdt.internal.corext.refactoring.structure.constraints.SuperTypeRefactoringProcessor#createContraintSolver(org.eclipse.jdt.internal.corext.refactoring.structure.constraints.SuperTypeConstraintsModel)
	 */
	@Override
	protected SuperTypeConstraintsSolver createContraintSolver(final SuperTypeConstraintsModel model) {
		return new ExtractInterfaceConstraintsSolver(model, fSuperName);
	}

	/**
	 * Creates a target field declaration.
	 *
	 * @param sourceRewrite
	 *            the source compilation unit rewrite
	 * @param targetRewrite
	 *            the target rewrite
	 * @param targetDeclaration
	 *            the target type declaration
	 * @param fragment
	 *            the source variable declaration fragment
	 * @throws CoreException
	 *             if a buffer could not be retrieved
	 */
	protected void createFieldDeclaration(final CompilationUnitRewrite sourceRewrite, final ASTRewrite targetRewrite, final AbstractTypeDeclaration targetDeclaration, final VariableDeclarationFragment fragment) throws CoreException {
		Assert.isNotNull(targetDeclaration);
		Assert.isNotNull(sourceRewrite);
		Assert.isNotNull(targetRewrite);
		Assert.isNotNull(fragment);
		final FieldDeclaration field= (FieldDeclaration) fragment.getParent();
		ImportRewriteUtil.collectImports(fSubType.getJavaProject(), field, fTypeBindings, fStaticBindings, false);
		final ASTRewrite rewrite= ASTRewrite.create(field.getAST());
		final ITrackedNodePosition position= rewrite.track(field);
		final ListRewrite rewriter= rewrite.getListRewrite(field, FieldDeclaration.FRAGMENTS_PROPERTY);
		VariableDeclarationFragment current= null;
		for (final Iterator<VariableDeclarationFragment> iterator= field.fragments().iterator(); iterator.hasNext();) {
			current= iterator.next();
			if (!current.getName().getIdentifier().equals(fragment.getName().getIdentifier()))
				rewriter.remove(current, null);
		}
		int modifiers= field.getModifiers();
		modifiers= JdtFlags.clearAccessModifiers(modifiers);
		modifiers= JdtFlags.clearFlag(Modifier.ABSTRACT | Modifier.STATIC | Modifier.FINAL, modifiers);
		ModifierRewrite.create(rewrite, field).setModifiers(modifiers, null);
		final ICompilationUnit unit= sourceRewrite.getCu();
		final ITextFileBuffer buffer= RefactoringFileBuffers.acquire(unit);
		try {
			final IDocument document= new Document(buffer.getDocument().get());
			try {
				rewrite.rewriteAST(document, unit.getOptions(true)).apply(document, TextEdit.UPDATE_REGIONS);
				targetRewrite.getListRewrite(targetDeclaration, targetDeclaration.getBodyDeclarationsProperty()).insertFirst(targetRewrite.createStringPlaceholder(normalizeText(document.get(position.getStartPosition(), position.getLength())), ASTNode.FIELD_DECLARATION), null);
			} catch (MalformedTreeException | BadLocationException exception) {
				JavaManipulationPlugin.log(exception);
			}
		} finally {
			RefactoringFileBuffers.release(unit);
		}
	}

	@Override
	protected void createMemberDeclarations(final CompilationUnitRewrite sourceRewrite, final ASTRewrite targetRewrite, final AbstractTypeDeclaration targetDeclaration) throws CoreException {
		Assert.isNotNull(sourceRewrite);
		Assert.isNotNull(targetRewrite);
		Assert.isNotNull(targetDeclaration);
		Arrays.sort(fMembers, (first, second) -> {
			try {
				return first.getSourceRange().getOffset() - second.getSourceRange().getOffset();
			} catch (JavaModelException exception) {
				return first.hashCode() - second.hashCode();
			}
		});
		fTypeBindings.clear();
		fStaticBindings.clear();
		if (fMembers.length > 0) {
			IMember member= null;
			for (int index= fMembers.length - 1; index >= 0; index--) {
				member= fMembers[index];
				if (member instanceof IField) {
					createFieldDeclaration(sourceRewrite, targetRewrite, targetDeclaration, ASTNodeSearchUtil.getFieldDeclarationFragmentNode((IField) member, sourceRewrite.getRoot()));
				} else if (member instanceof IMethod) {
					createMethodDeclaration(sourceRewrite, targetRewrite, targetDeclaration, ASTNodeSearchUtil.getMethodDeclarationNode((IMethod) member, sourceRewrite.getRoot()));
				}
			}
		}
	}

	/**
	 * Creates the method comment for the specified declaration.
	 *
	 * @param sourceRewrite
	 *            the compilation unit rewrite
	 * @param declaration
	 *            the method declaration
	 * @param replacements
	 *            the set of variable binding keys of formal parameters which
	 *            must be replaced
	 * @param javadoc
	 *            <code>true</code> if javadoc comments are processed,
	 *            <code>false</code> otherwise
	 * @throws CoreException
	 *             if an error occurs
	 */
	protected void createMethodComment(final CompilationUnitRewrite sourceRewrite, final MethodDeclaration declaration, final Set<String> replacements, final boolean javadoc) throws CoreException {
		Assert.isNotNull(sourceRewrite);
		Assert.isNotNull(declaration);
		Assert.isNotNull(replacements);
		final IMethodBinding binding= declaration.resolveBinding();
		if (binding != null) {
			IVariableBinding variable= null;
			SingleVariableDeclaration argument= null;
			final IPackageFragment fragment= this.getPackageFragment();
			final String string= fragment.isDefaultPackage() ? fSuperName : fragment.getElementName() + "." + fSuperName; //$NON-NLS-1$
			final ITypeBinding[] bindings= binding.getParameterTypes();
			final String[] names= new String[bindings.length];
			for (int offset= 0; offset < names.length; offset++) {
				argument= (SingleVariableDeclaration) declaration.parameters().get(offset);
				variable= argument.resolveBinding();
				if (variable != null) {
					if (replacements.contains(variable.getKey()))
						names[offset]= string;
					else {
						if (binding.isVarargs() && bindings[offset].isArray() && offset == names.length - 1)
							names[offset]= Bindings.getFullyQualifiedName(bindings[offset].getElementType());
						else
							names[offset]= Bindings.getFullyQualifiedName(bindings[offset]);
					}
				}
			}
			final String comment= CodeGeneration.getMethodComment(fSubType.getCompilationUnit(), fSubType.getElementName(), declaration, false, binding.getName(), string, names, StubUtility.getLineDelimiterUsed(fSubType.getJavaProject()));
			if (comment != null) {
				final ASTRewrite rewrite= sourceRewrite.getASTRewrite();
				if (declaration.getJavadoc() != null) {
					rewrite.replace(declaration.getJavadoc(), rewrite.createStringPlaceholder(comment, ASTNode.JAVADOC), sourceRewrite.createCategorizedGroupDescription(RefactoringCoreMessages.ExtractInterfaceProcessor_rewrite_comment, SET_EXTRACT_INTERFACE));
				} else if (javadoc) {
					rewrite.set(declaration, MethodDeclaration.JAVADOC_PROPERTY, rewrite.createStringPlaceholder(comment, ASTNode.JAVADOC), sourceRewrite.createCategorizedGroupDescription(RefactoringCoreMessages.ExtractInterfaceProcessor_add_comment, SET_EXTRACT_INTERFACE));
				}
			}
		}
	}

	/**
	 * Rewrites the extracted methods in the source type.
	 * Adds/removes method annotations and adds method comments.
	 *
	 * @param sourceRewrite
	 *            the source compilation unit rewrite
	 * @param replacements
	 *            the set of variable binding keys of formal parameters which
	 *            must be replaced
	 * @throws CoreException
	 *             if an error occurs
	 */
	private void rewriteSourceMethods(final CompilationUnitRewrite sourceRewrite, final Set<String> replacements) throws CoreException {
		Assert.isNotNull(sourceRewrite);
		Assert.isNotNull(replacements);
		if (fMembers.length > 0) {
			IJavaProject project= fSubType.getJavaProject();
			boolean annotations= fAnnotations && !JavaModelUtil.isVersionLessThan(project.getOption(JavaCore.COMPILER_SOURCE, true), JavaCore.VERSION_1_6);
			boolean inheritNullAnnotations= JavaCore.ENABLED.equals(project.getOption(JavaCore.COMPILER_INHERIT_NULL_ANNOTATIONS, true));
			boolean javadoc= JavaCore.ENABLED.equals(project.getOption(JavaCore.COMPILER_DOC_COMMENT_SUPPORT, true));
			for (IMember member : fMembers) {
				if (member instanceof IMethod) {
					MethodDeclaration declaration= ASTNodeSearchUtil.getMethodDeclarationNode((IMethod) member, sourceRewrite.getRoot());
					if (inheritNullAnnotations) {
						for (IExtendedModifier extended : (List<IExtendedModifier>) declaration.modifiers()) {
							if (extended.isAnnotation()) {
								Annotation annotation= (Annotation) extended;
								ITypeBinding binding= annotation.resolveTypeBinding();
								if (binding != null && Bindings.isAnyNullAnnotation(binding, project)) {
									ASTRewrite rewrite= sourceRewrite.getASTRewrite();
									rewrite.getListRewrite(declaration, MethodDeclaration.MODIFIERS2_PROPERTY).remove(annotation, null);
								}
							}
						}
						for (SingleVariableDeclaration parameter : (List<SingleVariableDeclaration>) declaration.parameters()) {
							for (IExtendedModifier extended : (List<IExtendedModifier>) parameter.modifiers()) {
								if (extended.isAnnotation()) {
									Annotation annotation= (Annotation) extended;
									ITypeBinding binding= annotation.resolveTypeBinding();
									if (binding != null && Bindings.isAnyNullAnnotation(binding, project)) {
										ASTRewrite rewrite= sourceRewrite.getASTRewrite();
										rewrite.getListRewrite(parameter, SingleVariableDeclaration.MODIFIERS2_PROPERTY).remove(annotation, null);
									}
								}
							}
						}
					}
					if (annotations) {
						StubUtility2Core.createOverrideAnnotation(sourceRewrite.getASTRewrite(), sourceRewrite.getImportRewrite(), declaration, null);
					}
					if (fComments)
						createMethodComment(sourceRewrite, declaration, replacements, javadoc);
				}
			}
		}
	}

	/**
	 * Creates a target method declaration.
	 *
	 * @param sourceRewrite
	 *            the source compilation unit rewrite
	 * @param targetRewrite
	 *            the target rewrite
	 * @param targetDeclaration
	 *            the target type declaration
	 * @param declaration
	 *            the source method declaration
	 * @throws CoreException
	 *             if a buffer could not be retrieved
	 */
	protected void createMethodDeclaration(final CompilationUnitRewrite sourceRewrite, final ASTRewrite targetRewrite, final AbstractTypeDeclaration targetDeclaration, final MethodDeclaration declaration) throws CoreException {
		Assert.isNotNull(targetDeclaration);
		Assert.isNotNull(sourceRewrite);
		Assert.isNotNull(targetRewrite);
		Assert.isNotNull(declaration);
		IJavaProject sourceProject= fSubType.getJavaProject();
		ImportRewriteUtil.collectImports(sourceProject, declaration, fTypeBindings, fStaticBindings, true);
		ASTRewrite rewrite= ASTRewrite.create(declaration.getAST());
		ITrackedNodePosition position= rewrite.track(declaration);
		if (declaration.getBody() != null)
			rewrite.remove(declaration.getBody(), null);
		ListRewrite list= rewrite.getListRewrite(declaration, declaration.getModifiersProperty());
		Annotation annotation= null;
		for (IExtendedModifier extended : (List<IExtendedModifier>) declaration.modifiers()) {
			if (!extended.isAnnotation()) {
				Modifier modifier= (Modifier) extended;
				list.remove(modifier, null);
			} else if (extended.isAnnotation()) {
				annotation= (Annotation) extended;
				ITypeBinding binding= annotation.resolveTypeBinding();
				if (binding != null && "java.lang.Override".equals(binding.getQualifiedName()) || ! Bindings.isAnyNullAnnotation(binding, sourceProject)) //$NON-NLS-1$
					list.remove(annotation, null);
			}
		}

		for (SingleVariableDeclaration param : (List<SingleVariableDeclaration>) declaration.parameters()) {
			ListRewrite modifierRewrite= rewrite.getListRewrite(param, SingleVariableDeclaration.MODIFIERS2_PROPERTY);
			for (IExtendedModifier extended : (List<IExtendedModifier>) param.modifiers()) {
				if (!extended.isAnnotation()) {
					Modifier modifier= (Modifier) extended;
					modifierRewrite.remove(modifier, null);
				} else if (extended.isAnnotation()) {
					annotation= (Annotation) extended;
					ITypeBinding binding= annotation.resolveTypeBinding();
					if (binding != null && ! Bindings.isAnyNullAnnotation(binding, sourceProject))
						modifierRewrite.remove(annotation, null);
				}
			}
		}

		ICompilationUnit unit= sourceRewrite.getCu();
		ITextFileBuffer buffer= RefactoringFileBuffers.acquire(unit);
		try {
			IDocument document= new Document(buffer.getDocument().get());
			try {
				rewrite.rewriteAST(document, unit.getOptions(true)).apply(document, TextEdit.UPDATE_REGIONS);
				targetRewrite.getListRewrite(targetDeclaration, targetDeclaration.getBodyDeclarationsProperty()).insertFirst(targetRewrite.createStringPlaceholder(normalizeText(document.get(position.getStartPosition(), position.getLength())), ASTNode.METHOD_DECLARATION), null);
			} catch (MalformedTreeException | BadLocationException exception) {
				JavaManipulationPlugin.log(exception);
			}
		} finally {
			RefactoringFileBuffers.release(unit);
		}
	}

	/**
	 * Creates the new signature of the source type.
	 *
	 * @param rewrite
	 *            the source compilation unit rewrite
	 * @param declaration
	 *            the type declaration
	 * @param status
	 *            the refactoring status
	 * @param monitor
	 *            the progress monitor to use
	 * @throws JavaModelException
	 *             if the type parameters cannot be retrieved
	 */
	protected void createTypeSignature(final CompilationUnitRewrite rewrite, final AbstractTypeDeclaration declaration, final RefactoringStatus status, final IProgressMonitor monitor) throws JavaModelException {
		Assert.isNotNull(rewrite);
		Assert.isNotNull(declaration);
		Assert.isNotNull(status);
		Assert.isNotNull(monitor);
		try {
			monitor.beginTask("", 1); //$NON-NLS-1$
			monitor.setTaskName(RefactoringCoreMessages.ExtractInterfaceProcessor_creating);
			final AST ast= declaration.getAST();
			final ITypeParameter[] parameters= fSubType.getTypeParameters();
			Type type= ast.newSimpleType(ast.newSimpleName(fSuperName));
			if (parameters.length > 0) {
				final ParameterizedType parameterized= ast.newParameterizedType(type);
				for (ITypeParameter parameter : parameters) {
					parameterized.typeArguments().add(ast.newSimpleType(ast.newSimpleName(parameter.getElementName())));
				}
				type= parameterized;
			}
			final ASTRewrite rewriter= rewrite.getASTRewrite();
			if (declaration instanceof TypeDeclaration)
				rewriter.getListRewrite(declaration, TypeDeclaration.SUPER_INTERFACE_TYPES_PROPERTY).insertLast(type, rewrite.createCategorizedGroupDescription(RefactoringCoreMessages.ExtractInterfaceProcessor_add_super_interface, SET_EXTRACT_INTERFACE));
			else if (declaration instanceof EnumDeclaration)
				rewriter.getListRewrite(declaration, EnumDeclaration.SUPER_INTERFACE_TYPES_PROPERTY).insertLast(type, rewrite.createCategorizedGroupDescription(RefactoringCoreMessages.ExtractInterfaceProcessor_add_super_interface, SET_EXTRACT_INTERFACE));
			else if (declaration instanceof RecordDeclaration)
				rewriter.getListRewrite(declaration, RecordDeclaration.SUPER_INTERFACE_TYPES_PROPERTY).insertLast(type, rewrite.createCategorizedGroupDescription(RefactoringCoreMessages.ExtractInterfaceProcessor_add_super_interface, SET_EXTRACT_INTERFACE));
			monitor.worked(1);
		} finally {
			monitor.done();
		}
	}

	/*
	 * @see org.eclipse.ltk.core.refactoring.participants.RefactoringProcessor#getElements()
	 */
	@Override
	public Object[] getElements() {
		return new Object[] { fSubType };
	}

	/**
	 * Returns the list of extractable members from the type.
	 *
	 * @return the list of extractable members
	 * @throws JavaModelException
	 *             if an error occurs
	 */
	public IMember[] getExtractableMembers() throws JavaModelException {
		final List<IJavaElement> list= new ArrayList<>();
		for (IJavaElement child : fSubType.getChildren()) {
			if (child instanceof IMember && isExtractableMember((IMember) child)) {
				list.add(child);
			}
		}
		final IMember[] members= new IMember[list.size()];
		list.toArray(members);
		return members;
	}

	/**
	 * Returns the extracted fields from the compilation unit.
	 *
	 * @param unit
	 *            the compilation unit
	 * @return the extracted fields
	 */
	protected IField[] getExtractedFields(final ICompilationUnit unit) {
		Assert.isNotNull(unit);
		final List<IJavaElement> list= new ArrayList<>();
		for (IMember member : fMembers) {
			if (member instanceof IField) {
				final IJavaElement element= JavaModelUtil.findInCompilationUnit(unit, member);
				if (element instanceof IField)
					list.add(element);
			}
		}
		final IField[] fields= new IField[list.size()];
		list.toArray(fields);
		return fields;
	}

	/**
	 * Returns the extracted methods from the compilation unit except the default methods.
	 *
	 * @param unit the compilation unit
	 * @return the extracted methods except the default method
	 * @throws JavaModelException if the element does not exist
	 */
	protected IMethod[] getExtractedMethods(final ICompilationUnit unit) throws JavaModelException {
		Assert.isNotNull(unit);
		final List<IJavaElement> list= new ArrayList<>();
		for (IMember member : fMembers) {
			if (member instanceof IMethod) {
				final IJavaElement element= JavaModelUtil.findInCompilationUnit(unit, member);
				if (element instanceof IMethod && !JdtFlags.isDefaultMethod((IMethod) element))
					list.add(element);
			}
		}
		return list.toArray(new IMethod[list.size()]);
	}

	/*
	 * @see org.eclipse.ltk.core.refactoring.participants.RefactoringProcessor#getIdentifier()
	 */
	@Override
	public String getIdentifier() {
		return IDENTIFIER;
	}

	/*
	 * @see org.eclipse.ltk.core.refactoring.participants.RefactoringProcessor#getProcessorName()
	 */
	@Override
	public String getProcessorName() {
		return RefactoringCoreMessages.ExtractInterfaceProcessor_name;
	}

	/**
	 * Returns the type where to extract an interface.
	 *
	 * @return the type where to extract an interface
	 */
	public IType getType() {
		return fSubType;
	}

	/**
	 * Returns the new interface name.
	 *
	 * @return the new interface name
	 */
	public String getTypeName() {
		return fSuperName;
	}

	/**
	 * Should override annotations be generated?
	 *
	 * @return <code>true</code> if annotations should be generated, <code>false</code> otherwise
	 */
	public boolean isAnnotations() {
		return fAnnotations;
	}

	private RefactoringStatus initialize(JavaRefactoringArguments extended) {
		String handle= extended.getAttribute(JavaRefactoringDescriptorUtil.ATTRIBUTE_INPUT);
		if (handle != null) {
			final IJavaElement element= JavaRefactoringDescriptorUtil.handleToElement(extended.getProject(), handle, false);
			if (element == null || !element.exists() || element.getElementType() != IJavaElement.TYPE)
				return JavaRefactoringDescriptorUtil.createInputFatalStatus(element, getProcessorName(), IJavaRefactorings.EXTRACT_INTERFACE);
			else
				fSubType= (IType) element;
		} else
			return RefactoringStatus.createFatalErrorStatus(Messages.format(RefactoringCoreMessages.InitializableRefactoring_argument_not_exist, JavaRefactoringDescriptorUtil.ATTRIBUTE_INPUT));
		final String name= extended.getAttribute(JavaRefactoringDescriptorUtil.ATTRIBUTE_NAME);
		if (name != null) {
			fSuperName= name;
			final RefactoringStatus status= checkTypeName(name);
			if (status.hasError())
				return status;
		} else
			return RefactoringStatus.createFatalErrorStatus(Messages.format(RefactoringCoreMessages.InitializableRefactoring_argument_not_exist, JavaRefactoringDescriptorUtil.ATTRIBUTE_NAME));
		final String comment= extended.getAttribute(ATTRIBUTE_COMMENTS);
		if (comment != null) {
			fComments= Boolean.parseBoolean(comment);
		} else
			return RefactoringStatus.createFatalErrorStatus(Messages.format(RefactoringCoreMessages.InitializableRefactoring_argument_not_exist, ATTRIBUTE_COMMENTS));
		final String instance= extended.getAttribute(ATTRIBUTE_INSTANCEOF);
		if (instance != null) {
			fInstanceOf= Boolean.parseBoolean(instance);
		} else
			return RefactoringStatus.createFatalErrorStatus(Messages.format(RefactoringCoreMessages.InitializableRefactoring_argument_not_exist, ATTRIBUTE_INSTANCEOF));
		final String replace= extended.getAttribute(ATTRIBUTE_REPLACE);
		if (replace != null) {
			fReplace= Boolean.parseBoolean(replace);
		} else
			return RefactoringStatus.createFatalErrorStatus(Messages.format(RefactoringCoreMessages.InitializableRefactoring_argument_not_exist, ATTRIBUTE_REPLACE));
		int count= 1;
		final List<IJavaElement> elements= new ArrayList<>();
		String attribute= JavaRefactoringDescriptorUtil.ATTRIBUTE_ELEMENT + count;
		final RefactoringStatus status= new RefactoringStatus();
		while ((handle= extended.getAttribute(attribute)) != null) {
			final IJavaElement element= JavaRefactoringDescriptorUtil.handleToElement(extended.getProject(), handle, false);
			if (element == null || !element.exists())
				status.merge(JavaRefactoringDescriptorUtil.createInputWarningStatus(element, getProcessorName(), IJavaRefactorings.EXTRACT_INTERFACE));
			else
				elements.add(element);
			count++;
			attribute= JavaRefactoringDescriptorUtil.ATTRIBUTE_ELEMENT + count;
		}
		fMembers= elements.toArray(new IMember[elements.size()]);
		fSettings= JavaPreferencesSettings.getCodeGenerationSettings(fSubType.getCompilationUnit());
		if (!status.isOK())
			return status;
		return new RefactoringStatus();
	}

	/*
	 * @see org.eclipse.ltk.core.refactoring.participants.RefactoringProcessor#isApplicable()
	 */
	@Override
	public boolean isApplicable() throws CoreException {
		return Checks.isAvailable(fSubType) && !fSubType.isBinary() && !fSubType.isReadOnly() && !fSubType.isAnnotation() && !fSubType.isAnonymous();
	}

	/**
	 * Should comments be generated?
	 *
	 * @return <code>true</code> if comments should be generated,
	 *         <code>false</code> otherwise
	 */
	public boolean isComments() {
		return fComments;
	}

	/*
	 * @see org.eclipse.ltk.core.refactoring.participants.RefactoringProcessor#loadParticipants(org.eclipse.ltk.core.refactoring.RefactoringStatus,org.eclipse.ltk.core.refactoring.participants.SharableParticipants)
	 */
	@Override
	public RefactoringParticipant[] loadParticipants(final RefactoringStatus status, final SharableParticipants sharedParticipants) throws CoreException {
		return new RefactoringParticipant[0];
	}

	/**
	 * Normalizes the indentation of the specified text.
	 *
	 * @param code
	 *            the text to normalize
	 * @return the normalized text
	 * @throws JavaModelException
	 *             if an error occurs
	 */
	protected String normalizeText(final String code) throws JavaModelException {
		Assert.isNotNull(code);
		final String[] lines= Strings.convertIntoLines(code);
		final IJavaProject project= fSubType.getJavaProject();
		Strings.trimIndentation(lines, fSubType.getCompilationUnit(), false);
		return Strings.concatenate(lines, StubUtility.getLineDelimiterUsed(project));
	}

	/**
	 * Resets the environment.
	 */
	protected void resetEnvironment() {
		fSuperSource= null;
		resetWorkingCopies();
	}

	@Override
	protected void rewriteTypeOccurrences(final TextEditBasedChangeManager manager, final ASTRequestor requestor, final CompilationUnitRewrite rewrite, final ICompilationUnit unit, final CompilationUnit node, final Set<String> replacements, final IProgressMonitor monitor) throws CoreException {
		try {
			monitor.beginTask("", 100); //$NON-NLS-1$
			monitor.setTaskName(RefactoringCoreMessages.ExtractInterfaceProcessor_creating);
			CompilationUnitRewrite currentRewrite= null;
			final boolean isSubUnit= rewrite.getCu().equals(unit.getPrimary());
			if (isSubUnit)
				currentRewrite= rewrite;
			else
				currentRewrite= new CompilationUnitRewrite(unit, node);
			final Collection<ITypeConstraintVariable> collection= fTypeOccurrences.get(unit);
			if (collection != null && !collection.isEmpty()) {
				final IProgressMonitor subMonitor= new SubProgressMonitor(monitor, 100);
				try {
					subMonitor.beginTask("", collection.size() * 10); //$NON-NLS-1$
					subMonitor.setTaskName(RefactoringCoreMessages.ExtractInterfaceProcessor_creating);
					TType estimate= null;
					for (ITypeConstraintVariable constraint : collection) {
						if (constraint != null) {
							estimate= (TType) constraint.getData(SuperTypeConstraintsSolver.DATA_TYPE_ESTIMATE);
							if (estimate != null) {
								final CompilationUnitRange range= constraint.getRange();
								if (isSubUnit)
									rewriteTypeOccurrence(range, estimate, requestor, currentRewrite, node, replacements, currentRewrite.createCategorizedGroupDescription(RefactoringCoreMessages.SuperTypeRefactoringProcessor_update_type_occurrence, SET_SUPER_TYPE));
								else {
									final ASTNode result= NodeFinder.perform(node, range.getSourceRange());
									if (result != null)
										rewriteTypeOccurrence(estimate, currentRewrite, result, currentRewrite.createCategorizedGroupDescription(RefactoringCoreMessages.SuperTypeRefactoringProcessor_update_type_occurrence, SET_SUPER_TYPE));
								}
								subMonitor.worked(10);
							}
						}
					}
				} finally {
					subMonitor.done();
				}
			}
			if (!isSubUnit) {
				final TextChange change= currentRewrite.createChange(true);
				if (change != null)
					manager.manage(unit, change);
			}
		} finally {
			monitor.done();
		}
	}

	/**
	 * Creates the necessary text edits to replace the subtype occurrences by a
	 * supertype.
	 *
	 * @param manager
	 *            the text change manager
	 * @param sourceRewrite
	 *            the compilation unit of the subtype (not in working copy mode)
	 * @param superUnit
	 *            the compilation unit of the supertype (in working copy mode)
	 * @param replacements
	 *            the set of variable binding keys of formal parameters which
	 *            must be replaced
	 * @param status
	 *            the refactoring status
	 * @param monitor
	 *            the progress monitor to display progress
	 * @throws CoreException
	 *             if an error occurs
	 */
	protected void rewriteTypeOccurrences(final TextEditBasedChangeManager manager, final CompilationUnitRewrite sourceRewrite, final ICompilationUnit superUnit, final Set<String> replacements, final RefactoringStatus status, final IProgressMonitor monitor) throws CoreException {
		Assert.isNotNull(manager);
		Assert.isNotNull(sourceRewrite);
		Assert.isNotNull(superUnit);
		Assert.isNotNull(replacements);
		Assert.isNotNull(status);
		Assert.isNotNull(monitor);
		try {
			monitor.beginTask("", 300); //$NON-NLS-1$
			monitor.setTaskName(RefactoringCoreMessages.ExtractInterfaceProcessor_creating);
			final ICompilationUnit subUnit= getSharedWorkingCopy(fSubType.getCompilationUnit().getPrimary(), new SubProgressMonitor(monitor, 20));
			final ITextFileBuffer buffer= RefactoringFileBuffers.acquire(fSubType.getCompilationUnit());
			final ASTRewrite rewrite= sourceRewrite.getASTRewrite();
			try {
				final IDocument document= new Document(buffer.getDocument().get());
				try {
					rewrite.rewriteAST(document, fSubType.getCompilationUnit().getOptions(true)).apply(document, TextEdit.UPDATE_REGIONS);
				} catch (MalformedTreeException | BadLocationException exception) {
					JavaManipulationPlugin.log(exception);
				}
				subUnit.getBuffer().setContents(document.get());
				// if the destination package is not the current package, we should add the import from the super interface first so that the subtype binding can be resolved correctly
				IPackageFragment fragment = this.getPackageFragment();
				if (fragment != null && !fragment.equals(this.fSubType.getPackageFragment())) {
					ImportRewrite importRewrite = sourceRewrite.getImportRewrite();
					if (importRewrite != null) {
						importRewrite.addImport(superUnit.findPrimaryType().getFullyQualifiedName());
						subUnit.applyTextEdit(sourceRewrite.getImportRewrite().rewriteImports(monitor), monitor);
					}
				}
			} finally {
				RefactoringFileBuffers.release(fSubType.getCompilationUnit());
			}
			JavaModelUtil.reconcile(subUnit);
			final IJavaProject project= subUnit.getJavaProject();
			final ASTParser parser= ASTParser.newParser(IASTSharedValues.SHARED_AST_LEVEL);
			parser.setWorkingCopyOwner(fOwner);
			parser.setResolveBindings(true);
			parser.setProject(project);
			parser.setCompilerOptions(RefactoringASTParser.getCompilerOptions(project));
			parser.createASTs(new ICompilationUnit[] { subUnit}, new String[0], new ASTRequestor() {

				@Override
				public final void acceptAST(final ICompilationUnit unit, final CompilationUnit node) {
					try {
						final IType subType= (IType) JavaModelUtil.findInCompilationUnit(unit, fSubType);
						final AbstractTypeDeclaration subDeclaration= ASTNodeSearchUtil.getAbstractTypeDeclarationNode(subType, node);
						if (subDeclaration != null) {
							final ITypeBinding subBinding= subDeclaration.resolveBinding();
							if (subBinding != null) {
								String name= null;
								ITypeBinding superBinding= null;
								for (ITypeBinding binding : subBinding.getInterfaces()) {
									name= binding.getName();
									if (name.startsWith(fSuperName) && binding.getTypeArguments().length == subBinding.getTypeParameters().length) {
										superBinding= binding;
									}
								}
								if (superBinding != null) {
									solveSuperTypeConstraints(unit, node, subType, subBinding, superBinding, new SubProgressMonitor(monitor, 80), status);
									if (!status.hasFatalError()) {
										rewriteTypeOccurrences(manager, this, sourceRewrite, unit, node, replacements, status, new SubProgressMonitor(monitor, 200));
										if (manager.containsChangesIn(superUnit)) {
											final TextEditBasedChange change= manager.get(superUnit);
											if (change instanceof TextChange) {
												final TextEdit edit= ((TextChange) change).getEdit();
												if (edit != null) {
													final IDocument document= new Document(superUnit.getBuffer().getContents());
													try {
														edit.apply(document, TextEdit.UPDATE_REGIONS);
													} catch (MalformedTreeException | BadLocationException exception) {
														JavaManipulationPlugin.log(exception);
														status.merge(RefactoringStatus.createFatalErrorStatus(RefactoringCoreMessages.ExtractInterfaceProcessor_internal_error));
													}
													fSuperSource= document.get();
													manager.remove(superUnit);
												}
											}
										}
									}
								}
							}
						}
					} catch (JavaModelException exception) {
						JavaManipulationPlugin.log(exception);
						status.merge(RefactoringStatus.createFatalErrorStatus(RefactoringCoreMessages.ExtractInterfaceProcessor_internal_error));
					}
				}

				@Override
				public final void acceptBinding(final String key, final IBinding binding) {
					// Do nothing
				}
			}, new NullProgressMonitor());
		} finally {
			monitor.done();
		}
	}

	protected IPackageFragment getPackageFragment() {
		return fFragment == null ? fSubType.getPackageFragment() : fFragment;
	}

	/**
	 * Determines whether override annotations should be generated.
	 *
	 * @param annotations <code>true</code> to generate override annotations, <code>false</code> otherwise
	 */
	public void setAnnotations(final boolean annotations) {
		fAnnotations= annotations;
	}

	/**
	 * Determines whether comments should be generated.
	 *
	 * @param comments
	 *            <code>true</code> to generate comments, <code>false</code>
	 *            otherwise
	 */
	public void setComments(final boolean comments) {
		fComments= comments;
	}

	/**
	 * Sets the members to be extracted.
	 *
	 * @param members
	 *            the members to be extracted
	 * @throws JavaModelException
	 *             if an error occurs
	 */
	public void setExtractedMembers(final IMember[] members) throws JavaModelException {
		fMembers= members;
	}

	/**
	 * Sets the new interface name.
	 *
	 * @param name
	 *            the new interface name
	 */
	public void setTypeName(final String name) {
		Assert.isNotNull(name);
		fSuperName= name;
	}

	public void setPackageFragment(IPackageFragment fragment) {
		fFragment = fragment;
	}
}
