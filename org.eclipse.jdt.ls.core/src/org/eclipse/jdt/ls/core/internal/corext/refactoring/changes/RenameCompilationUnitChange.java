/*******************************************************************************
 * Copyright (c) 2000, 2016 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Originally copied from org.eclipse.jdt.internal.corext.refactoring.changes.RenameCompilationUnitChange
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.jdt.ls.core.internal.corext.refactoring.changes;

import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.Assert;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.internal.core.manipulation.util.BasicElementLabels;
import org.eclipse.jdt.ls.core.internal.Messages;
import org.eclipse.jdt.ls.core.internal.corext.refactoring.AbstractJavaElementRenameChange;
import org.eclipse.jdt.ls.core.internal.corext.refactoring.RefactoringCoreMessages;
import org.eclipse.ltk.core.refactoring.Change;

public final class RenameCompilationUnitChange extends AbstractJavaElementRenameChange {

	public RenameCompilationUnitChange(ICompilationUnit unit, String newName) {
		this(unit.getResource().getFullPath(), unit.getElementName(), newName, IResource.NULL_STAMP);
		Assert.isTrue(!unit.isReadOnly(), "compilation unit must not be read-only"); //$NON-NLS-1$
	}

	private RenameCompilationUnitChange(IPath resourcePath, String oldName, String newName, long stampToRestore) {
		super(resourcePath, oldName, newName, stampToRestore);

		setValidationMethod(VALIDATE_NOT_READ_ONLY | SAVE_IF_DIRTY);
	}

	@Override
	protected IPath createNewPath() {
		final IPath path= getResourcePath();
		if (path.getFileExtension() != null) {
			return path.removeFileExtension().removeLastSegments(1).append(getNewName());
		} else {
			return path.removeLastSegments(1).append(getNewName());
		}
	}

	@Override
	protected Change createUndoChange(long stampToRestore) throws JavaModelException {
		return new RenameCompilationUnitChange(createNewPath(), getNewName(), getOldName(), stampToRestore);
	}

	@Override
	protected void doRename(IProgressMonitor pm) throws CoreException {
		ICompilationUnit cu= (ICompilationUnit) getModifiedElement();
		if (cu != null) {
			cu.rename(getNewName(), false, pm);
		}
	}

	@Override
	public String getName() {
		String[] keys= new String[] { BasicElementLabels.getJavaElementName(getOldName()), BasicElementLabels.getJavaElementName(getNewName())};
		return Messages.format(RefactoringCoreMessages.RenameCompilationUnitChange_name, keys);
	}
}