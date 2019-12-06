/*******************************************************************************
 * Copyright (c) 2000, 2008 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Originally copied from org.eclipse.search.internal.core.text.FilesOfScopeCalculator
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.jdt.ls.core.internal.search.text;

import java.util.ArrayList;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IResourceProxy;
import org.eclipse.core.resources.IResourceProxyVisitor;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.MultiStatus;

public class FilesOfScopeCalculator implements IResourceProxyVisitor {

	private final TextSearchScope fScope;
	private final MultiStatus fStatus;
	private ArrayList<IResource> fFiles;

	public FilesOfScopeCalculator(TextSearchScope scope, MultiStatus status) {
		fScope= scope;
		fStatus= status;
	}

	@Override
	public boolean visit(IResourceProxy proxy) {
		boolean inScope= fScope.contains(proxy);

		if (inScope && proxy.getType() == IResource.FILE) {
			fFiles.add(proxy.requestResource());
		}
		return inScope;
	}

	public IFile[] process() {
		fFiles= new ArrayList<>();
		try {
			IResource[] roots= fScope.getRoots();
			for (IResource resource : roots) {
				try {
					if (resource.isAccessible()) {
						resource.accept(this, 0);
					}
				} catch (CoreException ex) {
					// report and ignore
					fStatus.add(ex.getStatus());
				}
			}
			return fFiles.toArray(new IFile[fFiles.size()]);
		} finally {
			fFiles= null;
		}
	}
}
