/*******************************************************************************
 * Copyright (c) 2000, 2011 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Originally copied from org.eclipse.jdt.internal.corext.refactoring.changes.PackageFragmentRootReorgChange
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.jdt.ls.core.internal.corext.refactoring.changes;

import org.eclipse.core.resources.IContainer;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.resources.mapping.ResourceMapping;
import org.eclipse.core.runtime.Assert;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.OperationCanceledException;
import org.eclipse.core.runtime.SubProgressMonitor;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IPackageFragmentRoot;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.ls.core.internal.corext.refactoring.reorg.INewNameQuery;
import org.eclipse.jdt.ls.core.internal.corext.refactoring.reorg.IPackageFragmentRootManipulationQuery;
import org.eclipse.jdt.ls.core.internal.corext.util.JavaElementResourceMapping;
import org.eclipse.jdt.ls.core.internal.corext.util.JavaElementUtil;
import org.eclipse.ltk.core.refactoring.Change;
import org.eclipse.ltk.core.refactoring.participants.ReorgExecutionLog;
import org.eclipse.ltk.core.refactoring.resource.ResourceChange;

public abstract class PackageFragmentRootReorgChange extends ResourceChange {

	private final String fRootHandle;
	private final INewNameQuery fNewNameQuery;
	private final IPackageFragmentRootManipulationQuery fUpdateClasspathQuery;
	private final IContainer fDestination;

	PackageFragmentRootReorgChange(IPackageFragmentRoot root, IContainer destination, INewNameQuery newNameQuery,
			IPackageFragmentRootManipulationQuery updateClasspathQuery) {
		Assert.isTrue(! root.isExternal());
		fRootHandle= root.getHandleIdentifier();
		fNewNameQuery= newNameQuery;
		fUpdateClasspathQuery= updateClasspathQuery;
		fDestination= destination;

		// we already ask for confirmation of move read only
		// resources. Furthermore we don't do a validate
		// edit since move source folders doesn't change
		// an content
		setValidationMethod(VALIDATE_DEFAULT);
	}


	@Override
	public final Change perform(IProgressMonitor pm) throws CoreException, OperationCanceledException {
		pm.beginTask(getName(), 2);
		try {
			String newName= getNewResourceName();
			IPackageFragmentRoot root= getRoot();
			ResourceMapping mapping= JavaElementResourceMapping.create(root);
			final Change result= doPerformReorg(getDestinationProjectPath().append(newName), new SubProgressMonitor(pm, 1));
			markAsExecuted(root, mapping);
			return result;
		} finally {
			pm.done();
		}
	}

	protected abstract Change doPerformReorg(IPath destinationPath, IProgressMonitor pm) throws JavaModelException;

	@Override
	public Object getModifiedElement() {
		return getRoot();
	}

	@Override
	public IResource getModifiedResource() {
		IPackageFragmentRoot root= getRoot();
		if (root != null) {
			return root.getResource();
		}
		return null;
	}

	public IPackageFragmentRoot getRoot() {
		return (IPackageFragmentRoot)JavaCore.create(fRootHandle);
	}

	public IPath getDestinationProjectPath() {
		return fDestination.getFullPath().removeFirstSegments(ResourcesPlugin.getWorkspace().getRoot().getFullPath().segmentCount());
	}

	public IContainer getDestination() {
		return fDestination;
	}

	public String getNewResourceName() throws OperationCanceledException {
		if (fNewNameQuery == null) {
			return getRoot().getElementName();
		}
		String name= fNewNameQuery.getNewName();
		if (name == null) {
			return getRoot().getElementName();
		}
		return name;
	}

	protected int getUpdateModelFlags(boolean isCopy) throws JavaModelException{
		final int destination= IPackageFragmentRoot.DESTINATION_PROJECT_CLASSPATH;
		final int replace= IPackageFragmentRoot.REPLACE;
		final int originating;
		final int otherProjects;
		if (isCopy){
			originating= 0; //ORIGINATING_PROJECT_CLASSPATH does not apply to copy
			otherProjects= 0;//OTHER_REFERRING_PROJECTS_CLASSPATH does not apply to copy
		} else{
			originating= IPackageFragmentRoot.ORIGINATING_PROJECT_CLASSPATH;
			otherProjects= IPackageFragmentRoot.OTHER_REFERRING_PROJECTS_CLASSPATH;
		}

		IJavaElement javaElement= JavaCore.create(getDestination());
		if (javaElement == null || !javaElement.exists()) {
			return replace | originating;
		}

		if (fUpdateClasspathQuery == null) {
			return replace | originating | destination;
		}

		IJavaProject[] referencingProjects= JavaElementUtil.getReferencingProjects(getRoot());
		if (referencingProjects.length <= 1) {
			return replace | originating | destination;
		}

		boolean updateOtherProjectsToo= fUpdateClasspathQuery.confirmManipulation(getRoot(), referencingProjects);
		if (updateOtherProjectsToo) {
			return replace | originating | destination | otherProjects;
		} else {
			return replace | originating | destination;
		}
	}

	protected int getResourceUpdateFlags(){
		return IResource.KEEP_HISTORY | IResource.SHALLOW;
	}

	private void markAsExecuted(IPackageFragmentRoot root, ResourceMapping mapping) {
		ReorgExecutionLog log= getAdapter(ReorgExecutionLog.class);
		if (log != null) {
			log.markAsProcessed(root);
			log.markAsProcessed(mapping);
		}
	}
}
