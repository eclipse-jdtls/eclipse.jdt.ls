/*******************************************************************************
 * Copyright (c) 2000, 2011 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Originally copied from org.eclipse.jdt.internal.corext.refactoring.changes.PackageReorgChange
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.jdt.ls.core.internal.corext.refactoring.changes;

import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.mapping.ResourceMapping;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.OperationCanceledException;
import org.eclipse.jdt.core.IPackageFragment;
import org.eclipse.jdt.core.IPackageFragmentRoot;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.ls.core.internal.corext.refactoring.reorg.INewNameQuery;
import org.eclipse.jdt.ls.core.internal.corext.util.JavaElementResourceMapping;
import org.eclipse.ltk.core.refactoring.Change;
import org.eclipse.ltk.core.refactoring.participants.ReorgExecutionLog;
import org.eclipse.ltk.core.refactoring.resource.ResourceChange;

public abstract class PackageReorgChange extends ResourceChange {

	private String fPackageHandle;
	private String fDestinationHandle;
	private INewNameQuery fNameQuery;

	PackageReorgChange(IPackageFragment pack, IPackageFragmentRoot dest, INewNameQuery nameQuery) {
		fPackageHandle= pack.getHandleIdentifier();
		fDestinationHandle= dest.getHandleIdentifier();
		fNameQuery= nameQuery;

		// it is enough to check the package only since package reorg changes
		// are not undoable. Don't check for read only here since
		// we already ask for user confirmation and moving a read
		// only package doesn't go thorugh validate edit (no
		// file content is modified).
		setValidationMethod(VALIDATE_DEFAULT);
	}

	abstract Change doPerformReorg(IProgressMonitor pm) throws JavaModelException, OperationCanceledException;

	@Override
	public final Change perform(IProgressMonitor pm) throws CoreException, OperationCanceledException {
		pm.beginTask(getName(), 1);
		try {
			IPackageFragment pack= getPackage();
			ResourceMapping mapping= JavaElementResourceMapping.create(pack);
			final Change result= doPerformReorg(pm);
			markAsExecuted(pack, mapping);
			return result;
		} finally {
			pm.done();
		}
	}

	@Override
	public Object getModifiedElement() {
		return getPackage();
	}

	@Override
	protected IResource getModifiedResource() {
		IPackageFragment pack= getPackage();
		if (pack != null) {
			return pack.getResource();
		}
		return null;
	}

	public IPackageFragmentRoot getDestination() {
		return (IPackageFragmentRoot)JavaCore.create(fDestinationHandle);
	}

	public IPackageFragment getPackage() {
		return (IPackageFragment)JavaCore.create(fPackageHandle);
	}

	public String getNewName() throws OperationCanceledException {
		if (fNameQuery == null) {
			return null;
		}
		return fNameQuery.getNewName();
	}

	private void markAsExecuted(IPackageFragment pack, ResourceMapping mapping) {
		ReorgExecutionLog log= getAdapter(ReorgExecutionLog.class);
		if (log != null) {
			log.markAsProcessed(pack);
			log.markAsProcessed(mapping);
		}
	}
}
