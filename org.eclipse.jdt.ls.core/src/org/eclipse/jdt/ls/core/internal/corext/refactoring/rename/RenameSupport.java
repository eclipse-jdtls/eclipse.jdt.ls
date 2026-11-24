/*******************************************************************************
 * Copyright (c) 2000, 2011 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Originally copied from org.eclipse.jdt.ui.refactoring.RenameSupport
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.jdt.ls.core.internal.corext.refactoring.rename;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.core.runtime.Status;
import org.eclipse.jdt.core.Flags;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IField;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.ILocalVariable;
import org.eclipse.jdt.core.IMethod;
import org.eclipse.jdt.core.IPackageFragment;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.ITypeParameter;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.IMethodBinding;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.manipulation.CoreASTProvider;
import org.eclipse.jdt.core.refactoring.descriptors.RenameJavaElementDescriptor;
import org.eclipse.jdt.internal.corext.refactoring.changes.DynamicValidationRefactoringChange;
import org.eclipse.jdt.internal.corext.refactoring.changes.RenameCompilationUnitChange;
import org.eclipse.jdt.internal.corext.refactoring.rename.JavaRenameProcessor;
import org.eclipse.jdt.internal.corext.refactoring.rename.MethodChecks;
import org.eclipse.jdt.internal.corext.refactoring.rename.RenameCompilationUnitProcessor;
import org.eclipse.jdt.internal.corext.refactoring.rename.RenameEnumConstProcessor;
import org.eclipse.jdt.internal.corext.refactoring.rename.RenameFieldProcessor;
import org.eclipse.jdt.internal.corext.refactoring.rename.RenameLocalVariableProcessor;
import org.eclipse.jdt.internal.corext.refactoring.rename.RenameNonVirtualMethodProcessor;
import org.eclipse.jdt.internal.corext.refactoring.rename.RenamePackageProcessor;
import org.eclipse.jdt.internal.corext.refactoring.rename.RenameTypeParameterProcessor;
import org.eclipse.jdt.internal.corext.refactoring.rename.RenameTypeProcessor;
import org.eclipse.jdt.internal.corext.refactoring.rename.RenameVirtualMethodProcessor;
import org.eclipse.jdt.internal.corext.refactoring.structure.ASTNodeSearchUtil;
import org.eclipse.jdt.internal.corext.refactoring.tagging.INameUpdating;
import org.eclipse.jdt.internal.corext.refactoring.tagging.IReferenceUpdating;
import org.eclipse.jdt.internal.corext.refactoring.tagging.ITextUpdating;
import org.eclipse.jdt.internal.corext.refactoring.util.TextChangeManager;
import org.eclipse.jdt.internal.corext.util.JavaModelUtil;
import org.eclipse.jdt.internal.corext.util.JdtFlags;
import org.eclipse.jdt.ls.core.internal.JavaLanguageServerPlugin;
import org.eclipse.ltk.core.refactoring.RefactoringStatus;
import org.eclipse.ltk.core.refactoring.participants.RenameRefactoring;

/**
 * Central access point to execute rename refactorings.
 * <p>
 * Note: this class is not intended to be subclassed or instantiated.
 * </p>
 * @since 2.1
 *
 * @noinstantiate This class is not intended to be instantiated by clients.
 * @noextend This class is not intended to be subclassed by clients.
 */
public class RenameSupport {

	private RenameRefactoring fRefactoring;
	private RefactoringStatus fPreCheckStatus;

	/**
	 * Executes some light weight precondition checking. If the returned status
	 * is an error then the refactoring can't be executed at all. However,
	 * returning an OK status doesn't guarantee that the refactoring can be
	 * executed. It may still fail while performing the exhaustive precondition
	 * checking done inside the methods <code>openDialog</code> or
	 * <code>perform</code>.
	 *
	 * The method is mainly used to determine enable/disablement of actions.
	 *
	 * @return the result of the light weight precondition checking.
	 *
	 * @throws CoreException if an unexpected exception occurs while performing the checking.
	 *
	 * @see #openDialog(Shell)
	 * @see #perform(Shell, IRunnableContext)
	 */
	public IStatus preCheck() throws CoreException {
		//ensureChecked();
		if (fPreCheckStatus.hasFatalError()) {
			return fPreCheckStatus.getEntryMatchingSeverity(RefactoringStatus.FATAL).toStatus();
		} else {
			return Status.OK_STATUS;
		}
	}

	/** Flag indication that no additional update is to be performed. */
	public static final int NONE = 0;

	/** Flag indicating that references are to be updated as well. */
	public static final int UPDATE_REFERENCES = 1 << 0;

	/**
	 * Flag indicating that Javadoc comments are to be updated as well.
	 *
	 * @deprecated use UPDATE_REFERENCES or UPDATE_TEXTUAL_MATCHES or both.
	 */
	@Deprecated
	public static final int UPDATE_JAVADOC_COMMENTS = 1 << 1;
	/**
	 * Flag indicating that regular comments are to be updated as well.
	 *
	 * @deprecated use UPDATE_TEXTUAL_MATCHES
	 */
	@Deprecated
	public static final int UPDATE_REGULAR_COMMENTS = 1 << 2;
	/**
	 * Flag indicating that string literals are to be updated as well.
	 *
	 * @deprecated use UPDATE_TEXTUAL_MATCHES
	 */
	@Deprecated
	public static final int UPDATE_STRING_LITERALS = 1 << 3;

	/**
	 * Flag indicating that textual matches in comments and in string literals are
	 * to be updated as well.
	 *
	 * @since 3.0
	 */
	public static final int UPDATE_TEXTUAL_MATCHES = 1 << 6;

	/** Flag indicating that the getter method is to be updated as well. */
	public static final int UPDATE_GETTER_METHOD = 1 << 4;

	/** Flag indicating that the setter method is to be updated as well. */
	public static final int UPDATE_SETTER_METHOD = 1 << 5;

	private RenameSupport(RenameJavaElementDescriptor descriptor) throws CoreException {
		RefactoringStatus refactoringStatus= new RefactoringStatus();
		fRefactoring= (RenameRefactoring) descriptor.createRefactoring(refactoringStatus);
		if (refactoringStatus.hasFatalError()) {
			fPreCheckStatus= refactoringStatus;
		} else {
			preCheck();
			refactoringStatus.merge(fPreCheckStatus);
			fPreCheckStatus= refactoringStatus;
		}
	}

	/**
	 * Creates a new rename support for the given
	 * {@link RenameJavaElementDescriptor}.
	 *
	 * @param descriptor the {@link RenameJavaElementDescriptor} to create a
	 *        {@link RenameSupport} for. The caller is responsible for
	 *        configuring the descriptor before it is passed.
	 * @return the {@link RenameSupport}.
	 * @throws CoreException if an unexpected error occurred while creating the
	 *         {@link RenameSupport}.
	 * @since 3.3
	 */
	public static RenameSupport create(RenameJavaElementDescriptor descriptor) throws CoreException {
		return new RenameSupport(descriptor);
	}

	/**
	 * Creates a new rename support for the given element, whatever its kind.
	 * The accessor of a record component, and the parameter of its canonical
	 * constructor, are renamed as the component is.
	 *
	 * @param element
	 *            the element to be renamed
	 * @param newName
	 *            the new name of the element
	 * @param flags
	 *            flags controlling additional parameters, see the other
	 *            <code>create</code> methods
	 * @return the {@link RenameSupport}, or <code>null</code> when the kind of
	 *         the element cannot be renamed
	 * @throws CoreException
	 *             if an unexpected error occurred while creating the
	 *             {@link RenameSupport}
	 */
	public static RenameSupport create(IJavaElement element, String newName, int flags) throws CoreException {
		return switch (element.getElementType()) {
			case IJavaElement.JAVA_PROJECT, IJavaElement.PACKAGE_FRAGMENT_ROOT -> null;
			case IJavaElement.PACKAGE_FRAGMENT -> RenameSupport.create((IPackageFragment) element, newName, flags);
			case IJavaElement.COMPILATION_UNIT -> RenameSupport.create((ICompilationUnit) element, newName, flags);
			case IJavaElement.TYPE -> RenameSupport.create((IType) element, newName, flags);
			case IJavaElement.FIELD -> RenameSupport.create((IField) element, newName, flags);
			case IJavaElement.TYPE_PARAMETER -> RenameSupport.create((ITypeParameter) element, newName, flags);
			case IJavaElement.METHOD -> {
				final IMethod method = (IMethod) element;
				if (method.isConstructor()) {
					yield create(method.getDeclaringType(), newName, flags);
				}
				IField accessedComponent = getAccessedRecordComponent(method);
				yield accessedComponent != null
						? RenameSupport.create(accessedComponent, newName, flags)
						: RenameSupport.create(method, newName, flags);
			}
			case IJavaElement.LOCAL_VARIABLE -> {
				final ILocalVariable variable = (ILocalVariable) element;
				IField parameterComponent = getConstructorParameterComponent(variable);
				yield parameterComponent != null
						? RenameSupport.create(parameterComponent, newName, flags)
						: RenameSupport.create(variable, newName, flags);
			}
			default -> null;
		};
	}

	/**
	 * Returns the component of a record that is renamed when the given element is
	 * renamed, as the Java editor does: the accessor of the component, and the
	 * parameter of the canonical constructor (compact or not) that has its name.
	 *
	 * @param element
	 *            the element at the position of the rename
	 * @return the component, or <code>null</code> when the element is not an
	 *         accessor or a canonical constructor parameter of a record
	 * @throws CoreException
	 *             if an unexpected error occurred while reading the record
	 */
	public static IField getRecordComponent(IJavaElement element) throws CoreException {
		if (element instanceof IMethod method) {
			return getAccessedRecordComponent(method);
		} else if (element instanceof ILocalVariable variable) {
			return getConstructorParameterComponent(variable);
		}
		return null;
	}

	/**
	 * Returns the component of a record that the method is the accessor of, or
	 * <code>null</code> when the method is not an accessor of a record.
	 */
	private static IField getAccessedRecordComponent(IMethod accessor) throws CoreException {
		IType type = accessor.getDeclaringType();
		if (isSourceRecord(type) && accessor.getNumberOfParameters() == 0
				&& !accessor.isConstructor() && !Flags.isStatic(accessor.getFlags())) {
			return getInstanceComponent(type, accessor.getElementName());
		}
		return null;
	}

	/**
	 * Returns the component of a record that the parameter of its canonical
	 * constructor (compact or not) has the name of, or <code>null</code> when the
	 * variable is not such a parameter.
	 */
	private static IField getConstructorParameterComponent(ILocalVariable parameter) throws CoreException {
		if (parameter.getParent() instanceof IMethod constructor && constructor.isConstructor()) {
			IType type = constructor.getDeclaringType();
			if (isSourceRecord(type) && isCanonicalConstructor(constructor)) {
				return getInstanceComponent(type, parameter.getElementName());
			}
		}
		return null;
	}

	/**
	 * Tells whether the type is a record that has a source, whose components can
	 * be renamed.
	 */
	private static boolean isSourceRecord(IType type) throws CoreException {
		return type != null && !type.isBinary() && type.isRecord();
	}

	/**
	 * Returns the component of the record that has the name, or
	 * <code>null</code> when there is none or when it is a static field.
	 */
	private static IField getInstanceComponent(IType record, String name) throws CoreException {
		IField component = record.getRecordComponent(name);
		return component != null && component.exists() && !Flags.isStatic(component.getFlags()) ? component : null;
	}

	/**
	 * Tells whether the constructor of a record is its canonical constructor,
	 * compact or not. The AST is needed: the model has no parameter types for a
	 * compact constructor.
	 */
	private static boolean isCanonicalConstructor(IMethod constructor) throws CoreException {
		CompilationUnit ast = CoreASTProvider.getInstance().getAST(constructor.getCompilationUnit(), CoreASTProvider.WAIT_YES, new NullProgressMonitor());
		if (ast == null) {
			return false;
		}
		MethodDeclaration declaration = ASTNodeSearchUtil.getMethodDeclarationNode(constructor, ast);
		IMethodBinding binding = declaration == null ? null : declaration.resolveBinding();
		return binding != null && (binding.isCanonicalConstructor() || binding.isCompactConstructor());
	}

	private RenameSupport(JavaRenameProcessor processor, String newName, int flags) {
		fRefactoring= new RenameRefactoring(processor);
		initialize(processor, newName, flags);
	}

	public JavaRenameProcessor getJavaRenameProcessor() {
		return (JavaRenameProcessor) fRefactoring.getProcessor();
	}

	public RenameRefactoring getRenameRefactoring() {
		return fRefactoring;
	}

	//	/**
	//	 * Creates a new rename support for the given {@link IJavaProject}.
	//	 *
	//	 * @param project the {@link IJavaProject} to be renamed.
	//	 * @param newName the project's new name. <code>null</code> is a valid
	//	 * value indicating that no new name is provided.
	//	 * @param flags flags controlling additional parameters. Valid flags are
	//	 * <code>UPDATE_REFERENCES</code> or <code>NONE</code>.
	//	 * @return the {@link RenameSupport}.
	//	 * @throws CoreException if an unexpected error occurred while creating
	//	 * the {@link RenameSupport}.
	//	 */
	//	public static RenameSupport create(IJavaProject project, String newName, int flags) throws CoreException {
	//		JavaRenameProcessor processor= new RenameJavaProjectProcessor(project);
	//		return new RenameSupport(processor, newName, flags);
	//	}

	//	/**
	//	 * Creates a new rename support for the given {@link IPackageFragmentRoot}.
	//	 *
	//	 * @param root the {@link IPackageFragmentRoot} to be renamed.
	//	 * @param newName the package fragment root's new name. <code>null</code> is
	//	 * a valid value indicating that no new name is provided.
	//	 * @return the {@link RenameSupport}.
	//	 * @throws CoreException if an unexpected error occurred while creating
	//	 * the {@link RenameSupport}.
	//	 */
	//	public static RenameSupport create(IPackageFragmentRoot root, String newName) throws CoreException {
	//		JavaRenameProcessor processor= new RenameSourceFolderProcessor(root);
	//		return new RenameSupport(processor, newName, 0);
	//	}

	/**
	 * Creates a new rename support for the given {@link IPackageFragment}.
	 *
	 * @param fragment
	 *            the {@link IPackageFragment} to be renamed.
	 * @param newName
	 *            the package fragment's new name. <code>null</code> is a valid
	 *            value indicating that no new name is provided.
	 * @param flags
	 *            flags controlling additional parameters. Valid flags are
	 *            <code>UPDATE_REFERENCES</code>, and
	 *            <code>UPDATE_TEXTUAL_MATCHES</code>, or their bitwise OR, or
	 *            <code>NONE</code>.
	 * @return the {@link RenameSupport}.
	 * @throws CoreException
	 *             if an unexpected error occurred while creating the
	 *             {@link RenameSupport}.
	 */
	public static RenameSupport create(IPackageFragment fragment, String newName, int flags) throws CoreException {
		JavaRenameProcessor processor = new RenamePackageProcessor(fragment);
		return new RenameSupport(processor, newName, flags);
	}

	/**
	 * Creates a new rename support for the given {@link ICompilationUnit}.
	 *
	 * @param unit the {@link ICompilationUnit} to be renamed.
	 * @param newName the compilation unit's new name. <code>null</code> is a
	 * valid value indicating that no new name is provided.
	 * @param flags flags controlling additional parameters. Valid flags are
	 * <code>UPDATE_REFERENCES</code>, and <code>UPDATE_TEXTUAL_MATCHES</code>,
	 * or their bitwise OR, or <code>NONE</code>.
	 * @return the {@link RenameSupport}.
	 * @throws CoreException if an unexpected error occurred while creating
	 * the {@link RenameSupport}.
	 */
	public static RenameSupport create(ICompilationUnit unit, String newName, int flags) throws CoreException {
		JavaRenameProcessor processor= new RenameCompilationUnitProcessor(unit);
		return new RenameSupport(processor, newName, flags);
	}

	/**
	 * Creates a new rename support for the given {@link IType}.
	 *
	 * @param type the {@link IType} to be renamed.
	 * @param newName the type's new name. <code>null</code> is a valid value
	 * indicating that no new name is provided.
	 * @param flags flags controlling additional parameters. Valid flags are
	 * <code>UPDATE_REFERENCES</code>, and <code>UPDATE_TEXTUAL_MATCHES</code>,
	 * or their bitwise OR, or <code>NONE</code>.
	 * @return the {@link RenameSupport}.
	 * @throws CoreException if an unexpected error occurred while creating
	 * the {@link RenameSupport}.
	 */
	public static RenameSupport create(IType type, String newName, int flags) throws CoreException {
		JavaRenameProcessor processor = new RenameTypeProcessor(type) {
			@Override
			protected void createChangeForRenamedCUStandardResource(IType type, TextChangeManager changeManager, DynamicValidationRefactoringChange result) {
				try {
					addTypeDeclarationUpdate(changeManager);
					addConstructorRenames(changeManager);
					result.addAll(changeManager.getAllChanges());
					String renamedCUName = JavaModelUtil.getRenamedCUName(type.getCompilationUnit(), getNewElementName());
					result.add(new RenameCompilationUnitChange(type.getCompilationUnit(), renamedCUName));
				} catch (CoreException e) {
					JavaLanguageServerPlugin.log(e);
				}
			}

		};
		return new RenameSupport(processor, newName, flags);
	}

	/**
	 * Creates a new rename support for the given {@link IMethod}.
	 *
	 * @param method the {@link IMethod} to be renamed.
	 * @param newName the method's new name. <code>null</code> is a valid value
	 * indicating that no new name is provided.
	 * @param flags flags controlling additional parameters. Valid flags are
	 * <code>UPDATE_REFERENCES</code> or <code>NONE</code>.
	 * @return the {@link RenameSupport}.
	 * @throws CoreException if an unexpected error occurred while creating
	 * the {@link RenameSupport}.
	 */
	public static RenameSupport create(IMethod method, String newName, int flags) throws CoreException {
		JavaRenameProcessor processor;
		if (MethodChecks.isVirtual(method)) {
			processor = new RenameVirtualMethodProcessor(method) {
				@Override
				protected void initializeWithTopMostImplementation(IMethod topmost) {
					String newName = getNewElementName();
					initialize(topmost);
					setNewElementName(newName);
				}
			};
		} else {
			processor= new RenameNonVirtualMethodProcessor(method);
		}
		return new RenameSupport(processor, newName, flags);
	}

	/**
	 * Creates a new rename support for the given {@link IField}.
	 *
	 * @param field
	 *            the {@link IField} to be renamed.
	 * @param newName
	 *            the field's new name. <code>null</code> is a valid value
	 *            indicating that no new name is provided.
	 * @param flags
	 *            flags controlling additional parameters. Valid flags are
	 *            <code>UPDATE_REFERENCES</code>,
	 *            <code>UPDATE_TEXTUAL_MATCHES</code>,
	 *            <code>UPDATE_GETTER_METHOD</code>, and
	 *            <code>UPDATE_SETTER_METHOD</code>, or their bitwise OR, or
	 *            <code>NONE</code>.
	 * @return the {@link RenameSupport}.
	 * @throws CoreException
	 *             if an unexpected error occurred while creating the
	 *             {@link RenameSupport}.
	 */
	public static RenameSupport create(IField field, String newName, int flags) throws CoreException {
		if (JdtFlags.isEnum(field)) {
			return new RenameSupport(new RenameEnumConstProcessor(field), newName, flags);
		} else if (field.isRecordComponent()) {
			return createForRecordComponent(field, newName, flags);
		} else {
			final RenameFieldProcessor processor = new RenameFieldProcessor(field);
			processor.setRenameGetter(updateGetterMethod(flags));
			processor.setRenameSetter(updateSetterMethod(flags));
			return new RenameSupport(processor, newName, flags);
		}
	}

	/**
	 * Creates a new rename support for the given component of a record. The
	 * accessor of the component and the parameter of the canonical constructor are
	 * renamed with it.
	 */
	private static RenameSupport createForRecordComponent(IField component, String newName, int flags)
			throws CoreException {
		final RenameFieldProcessor processor = new RenameFieldProcessor(component);
		// The processor builds the rename of the parameter of the canonical
		// constructor when it knows the field is a record component, which it does
		// only after its initial conditions are checked: without it, the rename of the
		// parameter has no change manager (the Java editor checks them before it
		// asks for the new name too).
		processor.checkInitialConditions(new NullProgressMonitor());
		// The accessor of a record component is renamed with the component, it is not a getter
		// that follows the naming of the getters (value() would become getAmount()).
		processor.setRenameGetter(false);
		processor.setRenameSetter(false);
		return new RenameSupport(processor, newName, flags);
	}

	/**
	 * Creates a new rename support for the given {@link ITypeParameter}.
	 *
	 * @param parameter the {@link ITypeParameter} to be renamed.
	 * @param newName the parameter's new name. <code>null</code> is a valid value
	 * indicating that no new name is provided.
	 * @param flags flags controlling additional parameters. Valid flags are
	 * <code>UPDATE_REFERENCES</code>, or <code>NONE</code>.
	 * @return the {@link RenameSupport}.
	 * @throws CoreException if an unexpected error occurred while creating
	 * the {@link RenameSupport}.
	 * @since 3.1
	 */
	public static RenameSupport create(ITypeParameter parameter, String newName, int flags) throws CoreException {
		RenameTypeParameterProcessor processor= new RenameTypeParameterProcessor(parameter);
		processor.setUpdateReferences(updateReferences(flags));
		return new RenameSupport(processor, newName, flags);
	}

	/**
	 * Creates a new rename support for the given {@link ILocalVariable}.
	 *
	 * @param variable the {@link ILocalVariable} to be renamed.
	 * @param newName the variable's new name. <code>null</code> is a valid value
	 * indicating that no new name is provided.
	 * @param flags flags controlling additional parameters. Valid flags are
	 * <code>UPDATE_REFERENCES</code>, or <code>NONE</code>.
	 * @return the {@link RenameSupport}.
	 * @throws CoreException if an unexpected error occurred while creating
	 * the {@link RenameSupport}.
	 * @since 3.1
	 */
	public static RenameSupport create(ILocalVariable variable, String newName, int flags) throws CoreException {
		RenameLocalVariableProcessor processor= new RenameLocalVariableProcessor(variable);
		processor.setUpdateReferences(updateReferences(flags));
		return new RenameSupport(processor, newName, flags);
	}

	private static void initialize(JavaRenameProcessor processor, String newName, int flags) {

		setNewName(processor, newName);
		if (processor instanceof IReferenceUpdating) {
			IReferenceUpdating reference= (IReferenceUpdating) processor;
			reference.setUpdateReferences(updateReferences(flags));
		}

		if (processor instanceof ITextUpdating) {
			ITextUpdating text= (ITextUpdating) processor;
			text.setUpdateTextualMatches(updateTextualMatches(flags));
		}
	}

	private static void setNewName(INameUpdating refactoring, String newName) {
		if (newName != null) {
			refactoring.setNewElementName(newName);
		}
	}

	private static boolean updateReferences(int flags) {
		return (flags & UPDATE_REFERENCES) != 0;
	}

	private static boolean updateTextualMatches(int flags) {
		int TEXT_UPDATES= UPDATE_TEXTUAL_MATCHES | UPDATE_REGULAR_COMMENTS | UPDATE_STRING_LITERALS;
		return (flags & TEXT_UPDATES) != 0;
	}

	private static boolean updateGetterMethod(int flags) {
		return (flags & UPDATE_GETTER_METHOD) != 0;
	}

	private static boolean updateSetterMethod(int flags) {
		return (flags & UPDATE_SETTER_METHOD) != 0;
	}

	//	private void ensureChecked() throws CoreException {
	//		if (fPreCheckStatus == null) {
	//			if (!fRefactoring.isApplicable()) {
	//				fPreCheckStatus= RefactoringStatus.createFatalErrorStatus(JavaUIMessages.RenameSupport_not_available);
	//			} else {
	//				fPreCheckStatus= new RefactoringStatus();
	//			}
	//		}
	//	}

	//	private void showInformation(Shell parent, RefactoringStatus status) {
	//		String message= status.getMessageMatchingSeverity(RefactoringStatus.FATAL);
	//		MessageDialog.openInformation(parent, JavaUIMessages.RenameSupport_dialog_title, message);
	//	}
	//
	//	private RenameSelectionState createSelectionState() {
	//		RenameProcessor processor= (RenameProcessor) fRefactoring.getProcessor();
	//		Object[] elements= processor.getElements();
	//		RenameSelectionState state= elements.length == 1 ? new RenameSelectionState(elements[0]) : null;
	//		return state;
	//	}
	//
	//	private void restoreSelectionState(RenameSelectionState state) throws CoreException {
	//		INameUpdating nameUpdating= fRefactoring.getAdapter(INameUpdating.class);
	//		if (nameUpdating != null && state != null) {
	//			Object newElement= nameUpdating.getNewElement();
	//			if (newElement != null) {
	//				state.restore(newElement);
	//			}
	//		}
	//	}
}
