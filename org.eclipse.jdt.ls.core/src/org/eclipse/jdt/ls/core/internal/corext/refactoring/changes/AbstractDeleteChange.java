/*******************************************************************************
 * Copyright (c) 2000, 2011 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Originally copied from org.eclipse.jdt.internal.corext.refactoring.changes.AbstractDeleteChange
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.jdt.ls.core.internal.corext.refactoring.changes;

import org.eclipse.core.filebuffers.FileBuffers;
import org.eclipse.core.filebuffers.ITextFileBuffer;
import org.eclipse.core.filebuffers.LocationKind;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.SubProgressMonitor;
import org.eclipse.ltk.core.refactoring.Change;
import org.eclipse.ltk.core.refactoring.resource.ResourceChange;

abstract class AbstractDeleteChange extends ResourceChange {

	protected abstract Change doDelete(IProgressMonitor pm) throws CoreException;

	/* non java-doc
	 * @see IChange#perform(ChangeContext, IProgressMonitor)
	 */
	@Override
	public final Change perform(IProgressMonitor pm) throws CoreException {
		try {
			Change undo= doDelete(pm);
			return undo;
		} finally {
			pm.done();
		}
	}

	protected static void saveFileIfNeeded(IFile file, IProgressMonitor pm) throws CoreException {
		ITextFileBuffer buffer= FileBuffers.getTextFileBufferManager().getTextFileBuffer(file.getFullPath(), LocationKind.IFILE);
		if (buffer != null && buffer.isDirty() &&  buffer.isStateValidated() && buffer.isSynchronized()) {
			pm.beginTask("", 2); //$NON-NLS-1$
			buffer.commit(new SubProgressMonitor(pm, 1), false);
			file.refreshLocal(IResource.DEPTH_ONE, new SubProgressMonitor(pm, 1));
		}
		pm.done();
	}
}

