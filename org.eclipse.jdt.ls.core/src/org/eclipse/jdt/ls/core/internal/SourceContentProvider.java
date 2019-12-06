/*******************************************************************************
 * Copyright (c) 2017 David Gileadi and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *     David Gileadi - initial API
 *     Red Hat Inc. - initial implementation
 *******************************************************************************/
package org.eclipse.jdt.ls.core.internal;

import java.net.URI;
import java.util.Optional;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.jdt.core.IBuffer;
import org.eclipse.jdt.core.IClassFile;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.ls.core.internal.managers.IBuildSupport;
import org.eclipse.jdt.ls.core.internal.managers.ProjectsManager;

public class SourceContentProvider implements IDecompiler {

	@Override
	public String getContent(URI uri, IProgressMonitor monitor) throws CoreException {
		IClassFile classFile = JDTUtils.resolveClassFile(uri);
		if (classFile != null) {
			return getSource(classFile, monitor);
		}
		return null;
	}

	@Override
	public String getSource(IClassFile classFile, IProgressMonitor monitor) throws CoreException {
		String source = null;
		try {
			IBuffer buffer = classFile.getBuffer();
			if (buffer == null) {
				ProjectsManager projectsManager = JavaLanguageServerPlugin.getProjectsManager();
				if (projectsManager != null) {
					Optional<IBuildSupport> bs = projectsManager.getBuildSupport(classFile.getJavaProject().getProject());
					if (bs.isPresent()) {
						bs.get().discoverSource(classFile, monitor);
					}
				}
				buffer = classFile.getBuffer();
			}
			if (buffer != null) {
				source = buffer.getContents();
				JavaLanguageServerPlugin.logInfo("ClassFile contents request completed");
			}
		} catch (JavaModelException e) {
			JavaLanguageServerPlugin.logException("Exception getting java element ", e);
		}
		return source;
	}

}
