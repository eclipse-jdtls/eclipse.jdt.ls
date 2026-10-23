
/*******************************************************************************
* Copyright (c) 2017 Microsoft Corporation and others.
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
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import org.eclipse.core.runtime.AssertionFailedException;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IField;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IMethod;
import org.eclipse.jdt.core.IPackageFragment;
import org.eclipse.jdt.core.IAnnotation;
import org.eclipse.jdt.core.ISourceRange;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.search.IJavaSearchConstants;
import org.eclipse.jdt.core.search.IJavaSearchScope;
import org.eclipse.jdt.core.search.SearchEngine;
import org.eclipse.jdt.core.search.SearchMatch;
import org.eclipse.jdt.core.search.SearchParticipant;
import org.eclipse.jdt.core.search.SearchPattern;
import org.eclipse.jdt.core.search.SearchRequestor;
import org.eclipse.jdt.ls.core.internal.ChangeUtil;
import org.eclipse.jdt.ls.core.internal.JDTUtils;
import org.eclipse.jdt.ls.core.internal.JavaLanguageServerPlugin;
import org.eclipse.jdt.ls.core.internal.corext.refactoring.rename.RenameSupport;
import org.eclipse.jdt.ls.core.internal.preferences.PreferenceManager;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.RenameOptions;
import org.eclipse.lsp4j.RenameParams;
import org.eclipse.lsp4j.TextEdit;
import org.eclipse.lsp4j.WorkspaceEdit;
import org.eclipse.lsp4j.jsonrpc.ResponseErrorException;
import org.eclipse.lsp4j.jsonrpc.messages.ResponseError;
import org.eclipse.lsp4j.jsonrpc.messages.ResponseErrorCode;
import org.eclipse.ltk.core.refactoring.Change;
import org.eclipse.ltk.core.refactoring.CheckConditionsOperation;
import org.eclipse.ltk.core.refactoring.CreateChangeOperation;
import org.eclipse.ltk.core.refactoring.RefactoringStatus;
import org.eclipse.ltk.core.refactoring.participants.RenameRefactoring;

public class RenameHandler {

	public static RenameOptions createOptions() {
		RenameOptions renameOptions = new RenameOptions();
		renameOptions.setPrepareProvider(true);
		return renameOptions;
	}

	private PreferenceManager preferenceManager;

	public RenameHandler(PreferenceManager preferenceManager) {
		this.preferenceManager = preferenceManager;
	}

	public WorkspaceEdit rename(RenameParams params, IProgressMonitor monitor) {
		WorkspaceEdit edit = new WorkspaceEdit();
		if (!preferenceManager.getPreferences().isRenameEnabled()) {
			return edit;
		}
		try {
			final ICompilationUnit unit = JDTUtils.resolveCompilationUnit(params.getTextDocument().getUri());

			IJavaElement[] elements = JDTUtils.findElementsAtSelection(unit, params.getPosition().getLine(), params.getPosition().getCharacter(), this.preferenceManager, monitor);
			if (elements == null || elements.length == 0) {
				return edit;
			}
			IJavaElement curr = null;
			if (elements.length != 1) {
				// they could be package fragments.
				// We need to select the one that matches the package fragment of the current unit
				IPackageFragment packageFragment = (IPackageFragment) unit.getParent();
				IJavaElement found = Stream.of(elements).filter(e -> e.equals(packageFragment)).findFirst().orElse(null);
				if (found == null) {
					// this would be a binary package fragment
					curr = elements[0];
				} else {
					curr = found;
				}
			} else {
				curr = elements[0];
			}

			if (curr.getElementType() == IJavaElement.FIELD && hasGeneratedAccessors((IField) curr)) {
				return renameFieldWithGeneratedAccessors((IField) curr, params.getNewName(), monitor);
			}

			try {
				RenameSupport renameSupport = RenameSupport.create(curr, params.getNewName(), RenameSupport.UPDATE_REFERENCES | RenameSupport.UPDATE_GETTER_METHOD | RenameSupport.UPDATE_SETTER_METHOD);
				if (renameSupport == null) {
					return edit;
				}
				Change change = performRename(renameSupport, monitor);
				return ChangeUtil.convertToWorkspaceEdit(change);
			} catch (AssertionFailedException ex) {
				if (curr.getElementType() == IJavaElement.FIELD) {
					return renameFieldWithGeneratedAccessors((IField) curr, params.getNewName(), monitor);
				}
				JavaLanguageServerPlugin.logException("Problem with rename for " + params.getTextDocument().getUri(), ex);
			}
		} catch (CoreException | AssertionFailedException ex) {
			JavaLanguageServerPlugin.logException("Problem with rename for " + params.getTextDocument().getUri(), ex);
		}

		return edit;
	}

	private boolean hasGeneratedAccessors(IField field) throws JavaModelException {
		IType type = field.getDeclaringType();
		if (type == null) {
			return false;
		}
		if (!hasLombokAccessorAnnotation(type, field)) {
			return false;
		}
		ICompilationUnit cu = type.getCompilationUnit();
		if (cu == null) {
			return false;
		}
		String fieldName = field.getElementName();
		String getterName = "get" + Character.toUpperCase(fieldName.charAt(0)) + fieldName.substring(1);
		if ("boolean".equals(field.getTypeSignature()) || "Z".equals(field.getTypeSignature())) {
			getterName = "is" + Character.toUpperCase(fieldName.charAt(0)) + fieldName.substring(1);
		}
		IMethod getter = type.getMethod(getterName, new String[0]);
		if (getter != null && getter.exists() && !isGeneratedMethod(getter, cu)) {
			return false;
		}
		return true;
	}

	private boolean hasLombokAccessorAnnotation(IType type, IField field) throws JavaModelException {
		for (IAnnotation ann : type.getAnnotations()) {
			String name = ann.getElementName();
			if ("Data".equals(name) || "lombok.Data".equals(name)
					|| "Value".equals(name) || "lombok.Value".equals(name)
					|| "Getter".equals(name) || "lombok.Getter".equals(name)
					|| "Setter".equals(name) || "lombok.Setter".equals(name)) {
				return true;
			}
		}
		for (IAnnotation ann : field.getAnnotations()) {
			String name = ann.getElementName();
			if ("Getter".equals(name) || "lombok.Getter".equals(name)
					|| "Setter".equals(name) || "lombok.Setter".equals(name)) {
				return true;
			}
		}
		return false;
	}

	private boolean isGeneratedMethod(IMethod method, ICompilationUnit cu) throws JavaModelException {
		ISourceRange nameRange = method.getNameRange();
		if (nameRange == null || nameRange.getOffset() < 0) {
			return true;
		}
		String source = cu.getSource();
		if (source == null) {
			return false;
		}
		String methodName = method.getElementName();
		int offset = nameRange.getOffset();
		if (offset + methodName.length() > source.length()) {
			return true;
		}
		String nameAtPos = source.substring(offset, offset + methodName.length());
		return !nameAtPos.equals(methodName);
	}

	private Change performRename(RenameSupport renameSupport, IProgressMonitor monitor) throws CoreException {
		RenameRefactoring renameRefactoring = renameSupport.getRenameRefactoring();
		CheckConditionsOperation check = new CheckConditionsOperation(renameRefactoring, CheckConditionsOperation.ALL_CONDITIONS);
		CreateChangeOperation create = new CreateChangeOperation(check, RefactoringStatus.FATAL);
		create.run(monitor);
		if (check.getStatus().getSeverity() >= RefactoringStatus.FATAL) {
			throw new ResponseErrorException(new ResponseError(ResponseErrorCode.InvalidRequest, check.getStatus().getMessageMatchingSeverity(RefactoringStatus.ERROR), null));
		}
		return create.getChange();
	}

	private WorkspaceEdit renameFieldWithGeneratedAccessors(IField field, String newName, IProgressMonitor monitor) throws CoreException {
		RenameSupport renameSupport = RenameSupport.create(field, newName, RenameSupport.UPDATE_REFERENCES);
		if (renameSupport == null) {
			return new WorkspaceEdit();
		}
		Change change = performRename(renameSupport, monitor);
		WorkspaceEdit edit = ChangeUtil.convertToWorkspaceEdit(change);
		IType declaringType = field.getDeclaringType();
		String oldFieldName = field.getElementName();
		String oldGetterName = "get" + Character.toUpperCase(oldFieldName.charAt(0)) + oldFieldName.substring(1);
		String oldSetterName = "set" + Character.toUpperCase(oldFieldName.charAt(0)) + oldFieldName.substring(1);
		String newGetterName = "get" + Character.toUpperCase(newName.charAt(0)) + newName.substring(1);
		String newSetterName = "set" + Character.toUpperCase(newName.charAt(0)) + newName.substring(1);
		if ("boolean".equals(field.getTypeSignature()) || "Z".equals(field.getTypeSignature())) {
			oldGetterName = "is" + Character.toUpperCase(oldFieldName.charAt(0)) + oldFieldName.substring(1);
			newGetterName = "is" + Character.toUpperCase(newName.charAt(0)) + newName.substring(1);
		}
		IMethod getter = declaringType.getMethod(oldGetterName, new String[0]);
		addAccessorReferenceEdits(edit, getter, newGetterName, monitor);
		IMethod setter = declaringType.getMethod(oldSetterName, new String[] { field.getTypeSignature() });
		addAccessorReferenceEdits(edit, setter, newSetterName, monitor);
		return edit;
	}

	private void addAccessorReferenceEdits(WorkspaceEdit edit, IMethod accessor, String newName, IProgressMonitor monitor) {
		try {
			SearchPattern pattern = SearchPattern.createPattern(accessor, IJavaSearchConstants.REFERENCES);
			if (pattern == null) {
				String qualifiedName = accessor.getDeclaringType().getFullyQualifiedName() + "." + accessor.getElementName();
				pattern = SearchPattern.createPattern(qualifiedName, IJavaSearchConstants.METHOD, IJavaSearchConstants.REFERENCES,
						SearchPattern.R_EXACT_MATCH | SearchPattern.R_CASE_SENSITIVE);
			}
			if (pattern == null) {
				return;
			}
			IJavaSearchScope scope = SearchEngine.createWorkspaceScope();
			List<SearchMatch> matches = new ArrayList<>();
			SearchEngine engine = new SearchEngine();
			engine.search(pattern, new SearchParticipant[] { SearchEngine.getDefaultSearchParticipant() }, scope, new SearchRequestor() {
				@Override
				public void acceptSearchMatch(SearchMatch match) {
					matches.add(match);
				}
			}, new NullProgressMonitor());

			int nameLength = accessor.getElementName().length();
			for (SearchMatch match : matches) {
				Object element = match.getElement();
				if (element instanceof IJavaElement javaElement) {
					ICompilationUnit cu = (ICompilationUnit) javaElement.getAncestor(IJavaElement.COMPILATION_UNIT);
					if (cu != null) {
						String uri = JDTUtils.toURI(cu);
						Range range = JDTUtils.toRange(cu, match.getOffset(), nameLength);
						TextEdit textEdit = new TextEdit(range, newName);
						Map<String, List<TextEdit>> changes = edit.getChanges();
						if (changes != null) {
							changes.computeIfAbsent(uri, k -> new ArrayList<>()).add(textEdit);
						}
					}
				}
			}
		} catch (CoreException e) {
			JavaLanguageServerPlugin.logException("Problem adding accessor references", e);
		}
	}
}
