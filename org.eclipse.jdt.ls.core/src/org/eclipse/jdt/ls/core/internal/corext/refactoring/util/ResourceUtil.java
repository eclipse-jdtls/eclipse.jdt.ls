/*******************************************************************************
 * Copyright (c) 2000, 2011 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Originally copied from org.eclipse.jdt.internal.corext.refactoring.util.ResourceUtil
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.jdt.ls.core.internal.corext.refactoring.util;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.core.resources.IContainer;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IFolder;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IOpenable;

public class ResourceUtil {

	private ResourceUtil() {
	}

	public static IFile[] getFiles(ICompilationUnit[] cus) {
		List<IResource> files = new ArrayList<>(cus.length);
		for (int i = 0; i < cus.length; i++) {
			IResource resource = cus[i].getResource();
			if (resource != null && resource.getType() == IResource.FILE) {
				files.add(resource);
			}
		}
		return files.toArray(new IFile[files.size()]);
	}

	public static IFile getFile(ICompilationUnit cu) {
		IResource resource = cu.getResource();
		if (resource != null && resource.getType() == IResource.FILE) {
			return (IFile) resource;
		} else {
			return null;
		}
	}

	//----- other ------------------------------

	public static IResource getResource(Object o) {
		if (o instanceof IResource) {
			return (IResource) o;
		}
		if (o instanceof IJavaElement) {
			return getResource((IJavaElement) o);
		}
		return null;
	}

	/**
	 * Creates a folder and all parent folders if not existing. Project must exist.
	 * <code> org.eclipse.ui.dialogs.ContainerGenerator</code> is too heavy (creates
	 * a runnable)
	 *
	 * @param folder
	 *            the folder to create
	 * @param force
	 *            a flag controlling how to deal with resources that are not in sync
	 *            with the local file system
	 * @param local
	 *            a flag controlling whether or not the folder will be local after
	 *            the creation
	 * @param monitor
	 *            the progress monitor
	 * @throws CoreException
	 *             thrown if the creation failed
	 */
	public static void createFolder(IFolder folder, boolean force, boolean local, IProgressMonitor monitor) throws CoreException {
		if (!folder.exists()) {
			IContainer parent = folder.getParent();
			if (parent instanceof IFolder) {
				createFolder((IFolder) parent, force, local, null);
			}
			folder.create(force, local, monitor);
		}
	}

	private static IResource getResource(IJavaElement element) {
		if (element.getElementType() == IJavaElement.COMPILATION_UNIT) {
			return ((ICompilationUnit) element).getResource();
		} else if (element instanceof IOpenable) {
			return element.getResource();
		} else {
			return null;
		}
	}
}
