/*******************************************************************************
 * Copyright (c) 2016-2021 Red Hat Inc. and others.
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
package org.eclipse.jdt.ls.core.internal.handlers;

import java.util.Collection;
import java.util.Optional;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.ls.core.internal.JDTUtils;
import org.eclipse.jdt.ls.core.internal.JavaClientConnection;
import org.eclipse.jdt.ls.core.internal.JavaLanguageServerPlugin;
import org.eclipse.jdt.ls.core.internal.ResourceUtils;
import org.eclipse.jdt.ls.core.internal.managers.InvisibleProjectImporter;
import org.eclipse.jdt.ls.core.internal.managers.ProjectsManager;
import org.eclipse.jdt.ls.core.internal.preferences.PreferenceManager;

public class DocumentLifeCycleHandler extends BaseDocumentLifeCycleHandler {

	private JavaClientConnection connection;

	public DocumentLifeCycleHandler(JavaClientConnection connection, PreferenceManager preferenceManager, ProjectsManager projectsManager, boolean delayValidation) {
		super(preferenceManager, delayValidation);
		this.connection = connection;
	}

	@Override
	public BaseDiagnosticsHandler createDiagnosticsHandler(ICompilationUnit unit) {
		return new DiagnosticsHandler(connection, unit);
	}

	@Override
	public boolean isSyntaxMode(ICompilationUnit unit) {
		return JDTUtils.isDefaultProject(unit) || !JDTUtils.isOnClassPath(unit);
	}

	@Override
	public ICompilationUnit resolveCompilationUnit(String uri) {
		ICompilationUnit unit = null;
		IFile resource = JDTUtils.findFile(uri);
		if (resource != null) { // Open the files already managed by the jdt workspace.
			unit = JDTUtils.resolveCompilationUnit(resource);
		} else { // Open the standalone files.
			IPath filePath = ResourceUtils.canonicalFilePathFromURI(uri);
			if (filePath != null) {
				Collection<IPath> rootPaths = preferenceManager.getPreferences().getRootPaths();
				Optional<IPath> belongedRootPath = rootPaths.stream().filter(rootPath -> rootPath.isPrefixOf(filePath)).findFirst();
				boolean invisibleProjectEnabled = false;
				if (belongedRootPath.isPresent()) {
					IPath rootPath = belongedRootPath.get();
					try {
						invisibleProjectEnabled = InvisibleProjectImporter.loadInvisibleProject(filePath, rootPath, false, new NullProgressMonitor());
					} catch (CoreException e) {
						JavaLanguageServerPlugin.logException("Failed to load invisible project", e);
					}
					if (invisibleProjectEnabled) {
						unit = JDTUtils.resolveCompilationUnit(uri);
					}
				}
				if (!invisibleProjectEnabled) {
					unit = JDTUtils.getFakeCompilationUnit(uri);
				}
			}
		}

		return unit;
	}
}
