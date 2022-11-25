
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

import java.util.stream.Stream;

import org.eclipse.core.runtime.AssertionFailedException;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IPackageFragment;
import org.eclipse.jdt.ls.core.internal.ChangeUtil;
import org.eclipse.jdt.ls.core.internal.JDTUtils;
import org.eclipse.jdt.ls.core.internal.JavaLanguageServerPlugin;
import org.eclipse.jdt.ls.core.internal.corext.refactoring.rename.RenameSupport;
import org.eclipse.jdt.ls.core.internal.preferences.PreferenceManager;
import org.eclipse.lsp4j.RenameOptions;
import org.eclipse.lsp4j.RenameParams;
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

			RenameSupport renameSupport = RenameSupport.create(curr, params.getNewName(), RenameSupport.UPDATE_REFERENCES | RenameSupport.UPDATE_GETTER_METHOD | RenameSupport.UPDATE_SETTER_METHOD);
			if (renameSupport == null) {
				return edit;
			}
			RenameRefactoring renameRefactoring = renameSupport.getRenameRefactoring();

			CheckConditionsOperation check = new CheckConditionsOperation(renameRefactoring, CheckConditionsOperation.ALL_CONDITIONS);
			CreateChangeOperation create = new CreateChangeOperation(check, RefactoringStatus.FATAL);
			create.run(monitor);
			if (check.getStatus().getSeverity() >= RefactoringStatus.FATAL) {
				throw new ResponseErrorException(new ResponseError(ResponseErrorCode.InvalidRequest, check.getStatus().getMessageMatchingSeverity(RefactoringStatus.ERROR), null));
			}

			Change change = create.getChange();
			return ChangeUtil.convertToWorkspaceEdit(change);
		} catch (CoreException | AssertionFailedException ex) {
			JavaLanguageServerPlugin.logException("Problem with rename for " + params.getTextDocument().getUri(), ex);
		}

		return edit;
	}
}
