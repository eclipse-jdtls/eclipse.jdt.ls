/*******************************************************************************
 * Copyright (c) 2021 IBM Corporation and others.
 *
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Originally copied from org.eclipse.jdt.internal.corext.refactoring.rename.RenameModuleProcessor
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.jdt.ls.core.internal.corext.refactoring.rename;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.runtime.Assert;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.OperationCanceledException;
import org.eclipse.core.runtime.SubProgressMonitor;
import org.eclipse.jdt.core.Flags;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IModuleDescription;
import org.eclipse.jdt.core.ISourceRange;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.WorkingCopyOwner;
import org.eclipse.jdt.core.refactoring.IJavaRefactorings;
import org.eclipse.jdt.core.refactoring.descriptors.JavaRefactoringDescriptor;
import org.eclipse.jdt.core.refactoring.descriptors.RenameJavaElementDescriptor;
import org.eclipse.jdt.core.search.IJavaSearchConstants;
import org.eclipse.jdt.core.search.IJavaSearchScope;
import org.eclipse.jdt.core.search.SearchMatch;
import org.eclipse.jdt.core.search.SearchPattern;
import org.eclipse.jdt.internal.core.manipulation.util.BasicElementLabels;
import org.eclipse.jdt.internal.core.refactoring.descriptors.RefactoringSignatureDescriptorFactory;
import org.eclipse.jdt.internal.corext.refactoring.Checks;
import org.eclipse.jdt.internal.corext.refactoring.CollectingSearchRequestor;
import org.eclipse.jdt.internal.corext.refactoring.CuCollectingSearchRequestor;
import org.eclipse.jdt.internal.corext.refactoring.JDTRefactoringDescriptorComment;
import org.eclipse.jdt.internal.corext.refactoring.JavaRefactoringArguments;
import org.eclipse.jdt.internal.corext.refactoring.JavaRefactoringDescriptorUtil;
import org.eclipse.jdt.internal.corext.refactoring.RefactoringCoreMessages;
import org.eclipse.jdt.internal.corext.refactoring.RefactoringScopeFactory;
import org.eclipse.jdt.internal.corext.refactoring.RefactoringSearchEngine;
import org.eclipse.jdt.internal.corext.refactoring.SearchResultGroup;
import org.eclipse.jdt.internal.corext.refactoring.base.ReferencesInBinaryContext;
import org.eclipse.jdt.internal.corext.refactoring.changes.DynamicValidationRefactoringChange;
import org.eclipse.jdt.internal.corext.refactoring.changes.TextChangeCompatibility;
import org.eclipse.jdt.internal.corext.refactoring.participants.JavaProcessors;
import org.eclipse.jdt.internal.corext.refactoring.rename.RenameAnalyzeUtil;
import org.eclipse.jdt.internal.corext.refactoring.tagging.IReferenceUpdating;
import org.eclipse.jdt.internal.corext.refactoring.tagging.ITextUpdating;
import org.eclipse.jdt.internal.corext.refactoring.util.ResourceUtil;
import org.eclipse.jdt.internal.corext.refactoring.util.TextChangeManager;
import org.eclipse.jdt.internal.corext.util.Messages;
import org.eclipse.jdt.ls.core.internal.JavaLanguageServerPlugin;
import org.eclipse.jdt.ls.core.internal.corext.refactoring.RefactoringAvailabilityTester;
import org.eclipse.ltk.core.refactoring.Change;
import org.eclipse.ltk.core.refactoring.GroupCategorySet;
import org.eclipse.ltk.core.refactoring.RefactoringDescriptor;
import org.eclipse.ltk.core.refactoring.RefactoringStatus;
import org.eclipse.ltk.core.refactoring.TextChange;
import org.eclipse.ltk.core.refactoring.participants.CheckConditionsContext;
import org.eclipse.ltk.core.refactoring.participants.RenameArguments;
import org.eclipse.text.edits.ReplaceEdit;
import org.eclipse.text.edits.TextEdit;

public class RenameModuleProcessor extends JavaRenameProcessor implements IReferenceUpdating, ITextUpdating {

	protected IModuleDescription fModule;

	private IJavaProject fJavaProject;

	protected boolean fUpdateReferences;

	private SearchResultGroup[] fReferences;

	private TextChangeManager fChangeManager;

	/**
	 * Creates a new rename module processor.
	 *
	 * @param moduleDesc
	 *            the module to be renamed
	 */
	public RenameModuleProcessor(IModuleDescription moduleDesc) {
		this(moduleDesc, new TextChangeManager(true), null);
	}

	/**
	 * Creates a new rename module processor.
	 *
	 * @param arguments
	 *            the arguments
	 *
	 * @param status
	 *            the status
	 */
	public RenameModuleProcessor(JavaRefactoringArguments arguments, RefactoringStatus status) {
		this(null);
		RefactoringStatus initializeStatus= initialize(arguments);
		status.merge(initializeStatus);
	}

	private void assignModule(IModuleDescription moduleDesc) {
		fModule= moduleDesc;
		if (fModule != null) {
			fJavaProject= fModule.getJavaProject();
		}
		fUpdateReferences= true;
	}

	/**
	 * Creates a new rename module processor.
	 * <p>
	 * This constructor is only used by <code>RenameTypeProcessor</code>.
	 * </p>
	 * @param module the module
	 * @param manager the change manager
	 * @param categorySet the group category set
	 */
	RenameModuleProcessor(IModuleDescription module, TextChangeManager manager, GroupCategorySet categorySet) {
		assignModule(module);
		fChangeManager= manager;
	}

	private RefactoringStatus initialize(JavaRefactoringArguments extended) {
		final String handle= extended.getAttribute(JavaRefactoringDescriptorUtil.ATTRIBUTE_INPUT);
		if (handle != null) {
			final IJavaElement element= JavaRefactoringDescriptorUtil.handleToElement(extended.getProject(), handle, false);
			if (element == null || !element.exists() || element.getElementType() != IJavaElement.JAVA_MODULE) {
				return JavaRefactoringDescriptorUtil.createInputFatalStatus(element, getProcessorName(), IJavaRefactorings.RENAME_MODULE);
			} else {
				assignModule((IModuleDescription) element);
			}
		} else {
			return RefactoringStatus.createFatalErrorStatus(Messages.format(RefactoringCoreMessages.InitializableRefactoring_argument_not_exist, JavaRefactoringDescriptorUtil.ATTRIBUTE_INPUT));
		}
		final String name= extended.getAttribute(JavaRefactoringDescriptorUtil.ATTRIBUTE_NAME);
		if (name != null && !"".equals(name)) { //$NON-NLS-1$
			setNewElementName(name);
		} else {
			return RefactoringStatus.createFatalErrorStatus(Messages.format(RefactoringCoreMessages.InitializableRefactoring_argument_not_exist, JavaRefactoringDescriptorUtil.ATTRIBUTE_NAME));
		}
		final String references= extended.getAttribute(JavaRefactoringDescriptorUtil.ATTRIBUTE_REFERENCES);
		if (references != null) {
			fUpdateReferences= Boolean.parseBoolean(references);
		} else {
			return RefactoringStatus.createFatalErrorStatus(Messages.format(RefactoringCoreMessages.InitializableRefactoring_argument_not_exist, JavaRefactoringDescriptorUtil.ATTRIBUTE_REFERENCES));
		}
		return new RefactoringStatus();
	}

	@Override
	public Object getNewElement() throws CoreException {
		return super.getNewElementName();
	}

	@Override
	public RefactoringStatus checkNewElementName(String newName) throws CoreException {
		Assert.isNotNull(newName, "new name"); //$NON-NLS-1$
		RefactoringStatus result= Checks.checkModuleName(newName, fModule);
		return result;
	}

	@Override
	public boolean canEnableTextUpdating() {
		return false;
	}

	@Override
	public boolean getUpdateTextualMatches() {
		return false;
	}

	@Override
	public void setUpdateTextualMatches(boolean update) {
		// do nothing
	}

	@Override
	public String getCurrentElementName() {
		return fModule.getElementName();
	}

	@Override
	public String getCurrentElementQualifier() {
		return ""; //$NON-NLS-1$
	}

	@Override
	public void setUpdateReferences(boolean update) {
		fUpdateReferences= update;
	}

	@Override
	public boolean getUpdateReferences() {
		return fUpdateReferences;
	}

	@Override
	protected RenameModifications computeRenameModifications() throws CoreException {
		RenameModifications result= new RenameModifications();
		result.rename(fModule, new RenameArguments(getNewElementName(), getUpdateReferences()));
		return result;
	}

	@Override
	protected RefactoringStatus doCheckFinalConditions(IProgressMonitor pm, CheckConditionsContext context) throws CoreException {
		try{
			pm.beginTask("", 7); //$NON-NLS-1$
			pm.setTaskName(RefactoringCoreMessages.RenameFieldRefactoring_checking);
			RefactoringStatus result= new RefactoringStatus();
			result.merge(Checks.checkIfCuBroken(fModule));
			if (result.hasFatalError()) {
				return result;
			}
			result.merge(checkNewElementName(getNewElementName()));
			pm.worked(1);


			if (fUpdateReferences){
				pm.setTaskName(RefactoringCoreMessages.RenameFieldRefactoring_searching);
				fReferences= getReferences(new SubProgressMonitor(pm, 3), result);
				pm.setTaskName(RefactoringCoreMessages.RenameFieldRefactoring_checking);
			} else {
				fReferences= new SearchResultGroup[0];
				pm.worked(3);
			}

			if (fUpdateReferences) {
				result.merge(analyzeAffectedCompilationUnits());
			} else {
				Checks.checkCompileErrorsInAffectedFile(result, fModule.getResource());
			}

			result.merge(createChanges(new SubProgressMonitor(pm, 4)));
			if (result.hasFatalError()) {
				return result;
			}

			return result;
		} finally{
			pm.done();
		}
	}

	private RefactoringStatus createChanges(IProgressMonitor pm) throws CoreException {
		pm.beginTask(RefactoringCoreMessages.RenameFieldRefactoring_checking, 10);
		RefactoringStatus result= new RefactoringStatus();

		addDeclarationUpdate();

		if (fUpdateReferences) {
			addReferenceUpdates(new SubProgressMonitor(pm, 1));
			result.merge(analyzeRenameChanges(new SubProgressMonitor(pm, 2)));
			if (result.hasFatalError()) {
				return result;
			}
		} else {
			pm.worked(3);
		}

		pm.done();
		return result;
	}

	//----------------
	private RefactoringStatus analyzeRenameChanges(IProgressMonitor pm) throws CoreException {
		ICompilationUnit[] newWorkingCopies= null;
		WorkingCopyOwner newWCOwner= new WorkingCopyOwner() { /* must subclass */ };
		try {
			pm.beginTask("", 2); //$NON-NLS-1$
			RefactoringStatus result= new RefactoringStatus();
			SearchResultGroup[] oldReferences= fReferences;

			List<ICompilationUnit> compilationUnitsToModify= new ArrayList<>();
			compilationUnitsToModify.addAll(Arrays.asList(fChangeManager.getAllCompilationUnits()));
			newWorkingCopies= RenameAnalyzeUtil.createNewWorkingCopies(compilationUnitsToModify.toArray(new ICompilationUnit[compilationUnitsToModify.size()]),
					fChangeManager, newWCOwner, new SubProgressMonitor(pm, 1));
			SearchResultGroup[] newReferences= getNewReferences(new SubProgressMonitor(pm, 1), result, newWCOwner, newWorkingCopies);
			result.merge(RenameAnalyzeUtil.analyzeRenameChanges2(fChangeManager, oldReferences, newReferences, getNewElementName()));
			return result;
		} finally{
			pm.done();
			if (newWorkingCopies != null){
				for (ICompilationUnit newWorkingCopy : newWorkingCopies) {
					newWorkingCopy.discardWorkingCopy();
				}
			}
		}
	}

	private SearchResultGroup[] getNewReferences(IProgressMonitor pm, RefactoringStatus status, WorkingCopyOwner owner, ICompilationUnit[] newWorkingCopies) throws CoreException {
		pm.beginTask("", 2); //$NON-NLS-1$
		ICompilationUnit declaringCuWorkingCopy= RenameAnalyzeUtil.findWorkingCopyForCu(newWorkingCopies, fModule.getCompilationUnit());
		if (declaringCuWorkingCopy == null) {
			return new SearchResultGroup[0];
		}

		IModuleDescription module= getModuleInWorkingCopy();
		if (module == null || ! module.exists()) {
			return new SearchResultGroup[0];
		}

		CollectingSearchRequestor requestor= new CollectingSearchRequestor();

		SearchPattern newPattern= SearchPattern.createPattern(module, IJavaSearchConstants.REFERENCES);
		if (newPattern == null) {
			return new SearchResultGroup[0];
		}
		IJavaSearchScope scope= RefactoringScopeFactory.create(fModule, true, true);
		return RefactoringSearchEngine.search(newPattern, owner, scope, requestor, new SubProgressMonitor(pm, 1), status);
	}

	private IModuleDescription getModuleInWorkingCopy() {
		if (fJavaProject != null) {
			try {
				return fJavaProject.getModuleDescription();
			} catch (JavaModelException e) {
				JavaLanguageServerPlugin.logException(e);
			}
		}
		return null;
	}

	/*
	 * Analyzes all compilation units in which module is referenced
	 */
	private RefactoringStatus analyzeAffectedCompilationUnits() throws CoreException{
		RefactoringStatus result= new RefactoringStatus();
		fReferences= Checks.excludeCompilationUnits(fReferences, result);
		if (result.hasFatalError()) {
			return result;
		}

		result.merge(Checks.checkCompileErrorsInAffectedFiles(fReferences));
		return result;
	}

	@Override
	protected IFile[] getChangedFiles() {
		return ResourceUtil.getFiles(fChangeManager.getAllCompilationUnits());
	}

	@Override
	protected String[] getAffectedProjectNatures() throws CoreException {
		return JavaProcessors.computeAffectedNatures(fModule);
	}

	@Override
	public int getSaveMode() {
		return IRefactoringProcessorIds.SAVE_REFACTORING;
	}

	@Override
	public Object[] getElements() {
		return new Object[] { fModule};
	}

	@Override
	public String getIdentifier() {
		return IRefactoringProcessorIds.RENAME_MODULE_PROCESSOR;
	}

	@Override
	public String getProcessorName() {
		return RefactoringCoreMessages.RenameModuleRefactoring_name;
	}

	@Override
	public boolean isApplicable() throws CoreException {
		return RefactoringAvailabilityTester.isRenameModuleAvailable(fModule);
	}

	@Override
	public RefactoringStatus checkInitialConditions(IProgressMonitor pm) throws CoreException, OperationCanceledException {
		IModuleDescription primary= (IModuleDescription) fModule.getPrimaryElement();
		if (primary == null || !primary.exists()) {
			String message= Messages.format(RefactoringCoreMessages.RenameModuleRefactoring_deleted, BasicElementLabels.getFileName(fModule.getCompilationUnit()));
			return RefactoringStatus.createFatalErrorStatus(message);
		}
		assignModule(primary);

		return Checks.checkIfCuBroken(fModule);
	}

	@Override
	public Change createChange(IProgressMonitor monitor) throws CoreException, OperationCanceledException {
		try {
			monitor.beginTask(RefactoringCoreMessages.RenameFieldRefactoring_checking, 1);
			TextChange[] changes= fChangeManager.getAllChanges();
			RenameJavaElementDescriptor descriptor= createRefactoringDescriptor();
			return new DynamicValidationRefactoringChange(descriptor, getProcessorName(), changes);
		} finally {
			monitor.done();
		}
	}

	/**
	 * Overridden by subclasses.
	 * @return return the refactoring descriptor for this refactoring
	 */
	protected RenameJavaElementDescriptor createRefactoringDescriptor() {
		String project= null;
		IJavaProject javaProject= fModule.getJavaProject();
		if (javaProject != null) {
			project= javaProject.getElementName();
		}
		int flags= JavaRefactoringDescriptor.JAR_MIGRATION | JavaRefactoringDescriptor.JAR_REFACTORING | RefactoringDescriptor.STRUCTURAL_CHANGE;
		try {
			if (!Flags.isPrivate(fModule.getFlags())) {
				flags|= RefactoringDescriptor.MULTI_CHANGE;
			}
		} catch (JavaModelException exception) {
			JavaLanguageServerPlugin.logException(exception);
		}

		final String description= Messages.format(RefactoringCoreMessages.RenameModuleRefactoring_descriptor_description_short, BasicElementLabels.getJavaElementName(fModule.getElementName()));
		final String header= Messages.format(RefactoringCoreMessages.RenameModuleProcessor_descriptor_description, new String[] { BasicElementLabels.getJavaElementName(fModule.getElementName()), project, getNewElementName()});
		final JDTRefactoringDescriptorComment comment= new JDTRefactoringDescriptorComment(project, this, header);
		final RenameJavaElementDescriptor descriptor= RefactoringSignatureDescriptorFactory.createRenameJavaElementDescriptor(IJavaRefactorings.RENAME_MODULE);
		descriptor.setProject(project);
		descriptor.setDescription(description);
		descriptor.setComment(comment.asString());
		descriptor.setFlags(flags);
		descriptor.setJavaElement(fModule);
		descriptor.setNewName(getNewElementName());
		descriptor.setUpdateReferences(fUpdateReferences);
		descriptor.setUpdateTextualOccurrences(false);
		return descriptor;
	}

	private void addTextEdit(TextChange change, String groupName, TextEdit textEdit) {
		TextChangeCompatibility.addTextEdit(change, groupName, textEdit);
	}

	private SearchResultGroup[] getReferences(IProgressMonitor pm, RefactoringStatus status) throws CoreException{
		String binaryRefsDescription= Messages.format(RefactoringCoreMessages.ReferencesInBinaryContext_ref_in_binaries_description , BasicElementLabels.getJavaElementName(getCurrentElementName()));
		ReferencesInBinaryContext binaryRefs= new ReferencesInBinaryContext(binaryRefsDescription);

		SearchPattern searchPattern= createSearchPattern();
		if (searchPattern == null) {
			return new SearchResultGroup[0];
		}
		SearchResultGroup[] result= RefactoringSearchEngine.search(searchPattern, createRefactoringScope(),
				new CuCollectingSearchRequestor(binaryRefs), pm, status);
		binaryRefs.addErrorIfNecessary(status);
		return result;
	}

	private SearchPattern createSearchPattern(){
		return SearchPattern.createPattern(fModule, IJavaSearchConstants.REFERENCES);
	}

	private IJavaSearchScope createRefactoringScope() throws CoreException{
		return RefactoringScopeFactory.create(fModule, true, false);
	}

	private void addReferenceUpdates(IProgressMonitor pm) {
		pm.beginTask("", fReferences.length); //$NON-NLS-1$
		String editName= RefactoringCoreMessages.RenameModuleRefactoring_Update_module_reference;
		for (SearchResultGroup reference : fReferences) {
			ICompilationUnit cu= reference.getCompilationUnit();
			if (cu == null) {
				continue;
			}
			for (SearchMatch result : reference.getSearchResults()) {
				addTextEdit(fChangeManager.get(cu), editName, createTextChange(result));
			}
			pm.worked(1);
		}
	}

	private TextEdit createTextChange(SearchMatch match) {
		return new ReplaceEdit(match.getOffset(), match.getLength(), getNewElementName());
	}

	private void addDeclarationUpdate() throws CoreException {
		ISourceRange nameRange= fModule.getNameRange();
		TextEdit textEdit= new ReplaceEdit(nameRange.getOffset(), nameRange.getLength(), getNewElementName());
		ICompilationUnit cu= fModule.getCompilationUnit();
		String groupName= RefactoringCoreMessages.RenameModuleRefactoring_Update_module_declaration;
		addTextEdit(fChangeManager.get(cu), groupName, textEdit);
	}
}
