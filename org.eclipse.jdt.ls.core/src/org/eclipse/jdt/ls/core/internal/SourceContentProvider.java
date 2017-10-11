/*******************************************************************************
 * Copyright (c) 2017 David Gileadi and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     David Gileadi - initial API
 *     Red Hat Inc. - initial implementation
 *******************************************************************************/
package org.eclipse.jdt.ls.core.internal;

import java.net.URI;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.jdt.core.IBuffer;
import org.eclipse.jdt.core.IClassFile;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.ls.core.internal.preferences.Preferences;

public class SourceContentProvider implements IDecompiler {

	@Override
	public void setPreferences(Preferences preferences) {
	}

	@Override
	public String getContent(URI uri, IProgressMonitor monitor) throws CoreException {
		IClassFile classFile = JDTUtils.resolveClassFile(uri);
		if (classFile != null) {
			return decompile(classFile, monitor);
		}
		return null;
	}

	@Override
	public String decompile(IClassFile classFile, IProgressMonitor monitor) throws CoreException {
		String source = null;
		try {
			IBuffer buffer = classFile.getBuffer();
			if (buffer != null) {
				if (monitor.isCanceled()) {
					return null;
				}
				source = buffer.getContents();
				JavaLanguageServerPlugin.logInfo("ClassFile contents request completed");
			}
		} catch (JavaModelException e) {
			JavaLanguageServerPlugin.logException("Exception getting java element ", e);
		}
		return source;
	}

}
