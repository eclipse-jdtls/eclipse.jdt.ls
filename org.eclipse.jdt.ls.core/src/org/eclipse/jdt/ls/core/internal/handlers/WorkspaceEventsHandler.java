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
package org.eclipse.jdt.ls.core.internal.handlers;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IWorkspaceRunnable;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.manipulation.CoreASTProvider;
import org.eclipse.jdt.internal.core.JavaModelManager;
import org.eclipse.jdt.internal.core.OpenableElementInfo;
import org.eclipse.jdt.internal.core.PackageFragment;
import org.eclipse.jdt.ls.core.internal.JDTUtils;
import org.eclipse.jdt.ls.core.internal.JavaClientConnection;
import org.eclipse.jdt.ls.core.internal.JavaLanguageServerPlugin;
import org.eclipse.jdt.ls.core.internal.ResourceUtils;
import org.eclipse.jdt.ls.core.internal.managers.ProjectsManager;
import org.eclipse.jdt.ls.core.internal.managers.ProjectsManager.CHANGE_TYPE;
import org.eclipse.lsp4j.DidChangeWatchedFilesParams;
import org.eclipse.lsp4j.DidCloseTextDocumentParams;
import org.eclipse.lsp4j.FileChangeType;
import org.eclipse.lsp4j.FileEvent;
import org.eclipse.lsp4j.PublishDiagnosticsParams;
import org.eclipse.lsp4j.TextDocumentIdentifier;

public class WorkspaceEventsHandler {

	private final ProjectsManager pm;
	private final JavaClientConnection connection;
	private final BaseDocumentLifeCycleHandler handler;

	public WorkspaceEventsHandler(ProjectsManager projects, JavaClientConnection connection, BaseDocumentLifeCycleHandler handler) {
		this.pm = projects;
		this.connection = connection;
		this.handler = handler;
	}

	private CHANGE_TYPE toChangeType(FileChangeType vtype) {
		switch (vtype) {
			case Created:
				return CHANGE_TYPE.CREATED;
			case Changed:
				return CHANGE_TYPE.CHANGED;
			case Deleted:
				return CHANGE_TYPE.DELETED;
			default:
				throw new UnsupportedOperationException();
		}
	}

	public void didChangeWatchedFiles(DidChangeWatchedFilesParams param) {
		List<FileEvent> changes = param.getChanges().stream().distinct().collect(Collectors.toList());
		for (FileEvent fileEvent : changes) {
			CHANGE_TYPE changeType = toChangeType(fileEvent.getType());
			if (changeType == CHANGE_TYPE.DELETED) {
				cleanUpDiagnostics(fileEvent.getUri());
				handler.didClose(new DidCloseTextDocumentParams(new TextDocumentIdentifier(fileEvent.getUri())));
				discardWorkingCopies(fileEvent.getUri());
			}
			ICompilationUnit unit = JDTUtils.resolveCompilationUnit(fileEvent.getUri());
			if (unit != null && changeType == CHANGE_TYPE.CREATED && !unit.exists()) {
				final ICompilationUnit[] units = new ICompilationUnit[1];
				units[0] = unit;
				try {
					ResourcesPlugin.getWorkspace().run(new IWorkspaceRunnable() {
						@Override
						public void run(IProgressMonitor monitor) throws CoreException {
							units[0] = createCompilationUnit(units[0]);
						}
					}, new NullProgressMonitor());
				} catch (CoreException e) {
					JavaLanguageServerPlugin.logException(e.getMessage(), e);
				}
				unit = units[0];
			}
			if (unit != null) {
				if (unit.isWorkingCopy()) {
					try {
						IResource resource = unit.getUnderlyingResource();
						if (resource != null && resource.exists()) {
							resource.refreshLocal(IResource.DEPTH_ZERO, new NullProgressMonitor());
						}
					} catch (CoreException e) {
						JavaLanguageServerPlugin.logException(e.getMessage(), e);
					}
					continue;
				}
				if (changeType == CHANGE_TYPE.DELETED || changeType == CHANGE_TYPE.CHANGED) {
					if (unit.equals(CoreASTProvider.getInstance().getActiveJavaElement())) {
						CoreASTProvider.getInstance().disposeAST();
					}
				}
			}
			pm.fileChanged(fileEvent.getUri(), changeType);
		}
	}

	private ICompilationUnit createCompilationUnit(ICompilationUnit unit) {
		try {
			unit.getResource().refreshLocal(IResource.DEPTH_ONE, new NullProgressMonitor());
			if (unit.getResource().exists()) {
				IJavaElement parent = unit.getParent();
				if (parent instanceof PackageFragment pkg) {
					if (JavaModelManager.determineIfOnClasspath(unit.getResource(), unit.getJavaProject()) != null) {
						OpenableElementInfo elementInfo = (OpenableElementInfo) pkg.getElementInfo();
						elementInfo.addChild(unit);
					}
				}
			}
		} catch (CoreException e) {
			JavaLanguageServerPlugin.logException(e.getMessage(), e);
		}
		return unit;
	}

	private void cleanUpDiagnostics(String uri) {
		this.connection.publishDiagnostics(new PublishDiagnosticsParams(ResourceUtils.toClientUri(uri), Collections.emptyList()));
	}

	private void discardWorkingCopies(String parentUri) {
		IPath parentPath = ResourceUtils.filePathFromURI(parentUri);
		if (parentPath != null && !parentPath.lastSegment().endsWith(".java")) {
			ICompilationUnit[] workingCopies = JavaCore.getWorkingCopies(null);
			for (ICompilationUnit workingCopy : workingCopies) {
				IResource resource = workingCopy.getResource();
				if (resource == null) {
					continue;
				}

				IPath cuPath = resource.getRawLocation() != null ? resource.getRawLocation() : resource.getLocation();
				if (cuPath != null && parentPath.isPrefixOf(cuPath)) {
					try {
						workingCopy.discardWorkingCopy();
					} catch (JavaModelException e) {
						// do nothing.
					}
				}
			}
		}
	}
}
