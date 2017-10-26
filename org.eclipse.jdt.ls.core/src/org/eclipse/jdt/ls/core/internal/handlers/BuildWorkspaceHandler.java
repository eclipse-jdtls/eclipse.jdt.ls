/*******************************************************************************
 * Copyright (c) 2017 Microsoft Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Microsoft Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.jdt.ls.core.internal.handlers;

import static org.eclipse.jdt.ls.core.internal.JavaLanguageServerPlugin.logException;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IMarker;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IncrementalProjectBuilder;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.OperationCanceledException;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.ls.core.internal.BuildWorkspaceStatus;
import org.eclipse.jdt.ls.core.internal.JDTUtils;
import org.eclipse.jdt.ls.core.internal.JavaClientConnection;
import org.eclipse.jdt.ls.core.internal.JavaLanguageServerPlugin;
import org.eclipse.jdt.ls.core.internal.ResourceUtils;
import org.eclipse.jdt.ls.core.internal.managers.ProjectsManager;
import org.eclipse.jface.text.IDocument;
import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.PublishDiagnosticsParams;

/**
 * @author xuzho
 *
 */
public class BuildWorkspaceHandler {
	private JavaClientConnection connection;
	private final ProjectsManager projectsManager;

	public BuildWorkspaceHandler(JavaClientConnection connection, ProjectsManager projectsManager) {
		this.connection = connection;
		this.projectsManager = projectsManager;
	}

	public BuildWorkspaceStatus buildWorkspace(boolean forceReBuild, IProgressMonitor monitor) {
		try {
			ResourcesPlugin.getWorkspace().build(forceReBuild ? IncrementalProjectBuilder.FULL_BUILD : IncrementalProjectBuilder.INCREMENTAL_BUILD, monitor);
			List<IMarker> problemMarkers = getProblemMarkers(monitor);
			publishDiagnostics(problemMarkers);
			List<IMarker> errors = problemMarkers.stream().filter(m -> m.getAttribute(IMarker.SEVERITY, 0) == IMarker.SEVERITY_ERROR).collect(Collectors.toList());
			if (errors.isEmpty()) {
				return BuildWorkspaceStatus.SUCCEED;
			} else {
				return BuildWorkspaceStatus.WITH_ERROR;
			}
		} catch (CoreException e) {
			logException("Failed to build workspace.", e);
			return BuildWorkspaceStatus.FAILED;
		} catch (OperationCanceledException e) {
			return BuildWorkspaceStatus.CANCELLED;
		}
	}

	private static List<IMarker> getProblemMarkers(IProgressMonitor monitor) throws CoreException {
		IProject[] projects = ResourcesPlugin.getWorkspace().getRoot().getProjects();
		List<IMarker> markers = new ArrayList<>();
		for (IProject project : projects) {
			if (monitor.isCanceled()) {
				throw new OperationCanceledException();
			}
			markers.addAll(Arrays.asList(project.findMarkers(IMarker.PROBLEM, true, IResource.DEPTH_INFINITE)));
		}
		return markers;
	}

	private void publishDiagnostics(List<IMarker> markers) {
		Map<IResource, List<IMarker>> map = markers.stream().collect(Collectors.groupingBy(IMarker::getResource));
		for (Map.Entry<IResource, List<IMarker>> entry : map.entrySet()) {
			IResource resource = entry.getKey();
			// ignore problems caused by standalone files
			if (JavaLanguageServerPlugin.getProjectsManager().getDefaultProject().equals(resource.getProject())) {
				continue;
			}
			IFile file = resource.getAdapter(IFile.class);
			if (file == null) {
				continue;
			}
			IDocument document = null;
			String uri = JDTUtils.getFileURI(resource);
			if (JavaCore.isJavaLikeFileName(file.getName())) {
				ICompilationUnit cu = JDTUtils.resolveCompilationUnit(uri);
				try {
					document = JsonRpcHelpers.toDocument(cu.getBuffer());
				} catch (JavaModelException e) {
					logException("Failed to publish diagnostics.", e);
				}
			}
			else if (projectsManager.isBuildFile(file)) {
				document = JsonRpcHelpers.toDocument(file);
			}

			if (document != null) {
				List<Diagnostic> diagnostics = WorkspaceDiagnosticsHandler.toDiagnosticsArray(document, entry.getValue().toArray(new IMarker[0]));
				connection.publishDiagnostics(new PublishDiagnosticsParams(ResourceUtils.toClientUri(uri), diagnostics));
			}
		}


	}
}
