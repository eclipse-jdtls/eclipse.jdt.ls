/*******************************************************************************
 * Copyright (c) 2019 Red Hat Inc. and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Red Hat Inc. - initial API and implementation
 *******************************************************************************/
package org.eclipse.jdt.ls.core.internal.javadoc;

import java.io.File;

import org.eclipse.core.runtime.IPath;
import org.eclipse.jdt.core.IClasspathEntry;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IPackageFragmentRoot;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.internal.corext.util.JavaModelUtil;

/**
 * @author Nikolas Komonen - nkomonen@redhat.com
 *
 */
public class SourceJarLocations {


	public static File getSourceJarPath(IJavaElement element) throws JavaModelException {
		IPackageFragmentRoot root = JavaModelUtil.getPackageFragmentRoot(element);

		if (root == null) {
			return null;
		}

		IClasspathEntry entry = root.getResolvedClasspathEntry();
		IPath sourceAttachment = entry.getSourceAttachmentPath();

		if (sourceAttachment == null) {
			return null; //No source jar could be found
		}

		return sourceAttachment.toFile();
	}
}
