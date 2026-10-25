/*******************************************************************************
 * Copyright (c) 2026 Red Hat Inc. and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *     Arcadiy Ivanov - initial API and implementation
 *******************************************************************************/
package org.eclipse.jdt.ls.core.internal.handlers;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.Path;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.search.SearchDocument;
import org.eclipse.jdt.core.search.SearchParticipant;
import org.eclipse.jdt.internal.core.util.Util;

public class TestDerivedSearchDocument extends SearchDocument {

	private IFile file;

	public TestDerivedSearchDocument(String documentPath, SearchParticipant participant) {
		super(documentPath, participant);
	}

	@Override
	public byte[] getByteContents() {
		try {
			return Util.getResourceContentsAsByteArray(getFile());
		} catch (JavaModelException e) {
			return null;
		}
	}

	@Override
	public char[] getCharContents() {
		try {
			return Util.getResourceContentsAsCharArray(getFile());
		} catch (JavaModelException e) {
			return null;
		}
	}

	@Override
	public String getEncoding() {
		IFile resource = getFile();
		if (resource != null) {
			try {
				return resource.getCharset();
			} catch (CoreException e) {
				// fall through
			}
		}
		return null;
	}

	private IFile getFile() {
		if (this.file == null) {
			this.file = ResourcesPlugin.getWorkspace().getRoot().getFile(new Path(getPath()));
		}
		return this.file;
	}
}
