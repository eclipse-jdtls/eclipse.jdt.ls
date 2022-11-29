/*******************************************************************************
 * Copyright (c) 2016-2017 Red Hat Inc. and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *     Red Hat Inc. - initial API and implementation
 *******************************************************************************/
package org.eclipse.jdt.ls.core.internal;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IResource;
import org.eclipse.jdt.core.IBuffer;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IProblemRequestor;
import org.eclipse.jdt.core.WorkingCopyOwner;
import org.eclipse.jdt.ls.core.internal.handlers.DiagnosticsHandler;

/**
 * {@link WorkingCopyOwner} implementation for LanguageServer
 *
 * @author Gorkem Ercan
 *
 */
public final class LanguageServerWorkingCopyOwner extends WorkingCopyOwner {

	private final JavaClientConnection connection;
	/**
	 * @param javaLanguageServerPlugin
	 */
	public LanguageServerWorkingCopyOwner(JavaClientConnection connection) {
		this.connection= connection;
	}

	@Override
	public IBuffer createBuffer(ICompilationUnit workingCopy) {
		ICompilationUnit original= workingCopy.getPrimary();
		IResource resource= original.getResource();
		if (resource instanceof IFile file) {
			return new DocumentAdapter(workingCopy, file);
		}
		return DocumentAdapter.Null;
	}

	/* (non-Javadoc)
	 * @see org.eclipse.jdt.core.WorkingCopyOwner#getProblemRequestor(org.eclipse.jdt.core.ICompilationUnit)
	 */
	@Override
	public IProblemRequestor getProblemRequestor(ICompilationUnit cu) {
		return new DiagnosticsHandler(connection, cu);
	}
}