/*******************************************************************************
 * Copyright (c) 2016-2017 Red Hat Inc. and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Red Hat Inc. - initial API and implementation
 *******************************************************************************/
package org.eclipse.jdt.ls.core.internal.handlers;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IMarker;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IResourceChangeEvent;
import org.eclipse.core.resources.IResourceChangeListener;
import org.eclipse.core.resources.IResourceDelta;
import org.eclipse.core.resources.IResourceDeltaVisitor;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaModelMarker;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.ls.core.internal.JDTUtils;
import org.eclipse.jdt.ls.core.internal.JavaClientConnection;
import org.eclipse.jdt.ls.core.internal.JavaLanguageServerPlugin;
import org.eclipse.jdt.ls.core.internal.managers.ProjectsManager;
import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.IDocument;
import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.DiagnosticSeverity;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.PublishDiagnosticsParams;
import org.eclipse.lsp4j.Range;
import org.eclipse.m2e.core.internal.IMavenConstants;

/**
 * Listens to the resource change events and converts {@link IMarker}s to {@link Diagnostic}s.
 *
 * @author Gorkem Ercan
 *
 */
@SuppressWarnings("restriction")
public final class WorkspaceDiagnosticsHandler implements IResourceChangeListener, IResourceDeltaVisitor {
	private final JavaClientConnection connection;
	private final ProjectsManager projectsManager;

	public WorkspaceDiagnosticsHandler(JavaClientConnection connection, ProjectsManager projectsManager) {
		this.connection = connection;
		this.projectsManager = projectsManager;
	}

	@Override
	public void resourceChanged(IResourceChangeEvent event) {
		try {
			IResourceDelta delta = event.getDelta();
			delta.accept(this);
		} catch (CoreException e) {
			JavaLanguageServerPlugin.logException("failed to send diagnostics", e);
		}

	}

	/*
	 * (non-Javadoc)
	 *
	 * @see
	 * org.eclipse.core.resources.IResourceDeltaVisitor#visit(org.eclipse.core.
	 * resources.IResourceDelta)
	 */
	@Override
	public boolean visit(IResourceDelta delta) throws CoreException {
		IResource resource = delta.getResource();
		// Check if resource is accessible.
		// We do not deal with the markers for deleted files here
		// WorkspaceEventsHandler removes the diagnostics for deleted resources.
		if (resource == null || !resource.isAccessible()
				// ignore problems caused by standalone files
				|| JavaLanguageServerPlugin.getProjectsManager().getDefaultProject().equals(resource.getProject())) {
			return false;
		}

		// No marker changes continue to visit
		if ((delta.getFlags() & IResourceDelta.MARKERS) == 0) {
			return true;
		}

		IFile file = resource.getAdapter(IFile.class);
		if (file == null) {
			return true;
		}

		String uri = JDTUtils.getFileURI(resource);
		IDocument document = null;
		IMarker[] markers = null;
		// Check if it is a Java ...
		if (JavaCore.isJavaLikeFileName(file.getName())) {
			markers = resource.findMarkers(IJavaModelMarker.JAVA_MODEL_PROBLEM_MARKER, false, IResource.DEPTH_ONE);
			ICompilationUnit cu = JDTUtils.resolveCompilationUnit(uri);
			document = JsonRpcHelpers.toDocument(cu.getBuffer());

		} // or a build file
		else if (projectsManager.isBuildFile(file)) {
			//all errors on that build file should be relevant
			markers = file.findMarkers(null, true, 1);
			document = JsonRpcHelpers.toDocument(file);
		}
		if (document != null) {
			this.connection
			.publishDiagnostics(new PublishDiagnosticsParams(uri, toDiagnosticsArray(document, markers)));
		}
		return true;
	}

	/**
	 * Transforms {@link IMarker}s of a {@link IDocument} into a list of {@link Diagnostic}s.
	 *
	 * @param document
	 * @param markers
	 * @return a list of {@link Diagnostic}s
	 */
	public List<Diagnostic> toDiagnosticsArray(IDocument document, IMarker[] markers) {
		List<Diagnostic> diagnostics = Stream.of(markers)
				.map(m -> toDiagnostic(document, m))
				.filter(d -> d != null)
				.collect(Collectors.toList());
		return diagnostics;
	}

	private Diagnostic toDiagnostic(IDocument document, IMarker marker) {
		if (marker == null || !marker.exists()) {
			return null;
		}
		Diagnostic d = new Diagnostic();
		d.setSource(JavaLanguageServerPlugin.SERVER_SOURCE_ID);
		d.setMessage(marker.getAttribute(IMarker.MESSAGE, ""));
		d.setCode(String.valueOf(marker.getAttribute(IJavaModelMarker.ID, 0)));
		d.setSeverity(convertSeverity(marker.getAttribute(IMarker.SEVERITY, -1)));
		d.setRange(convertRange(document, marker));
		return d;
	}

	/**
	 * @param marker
	 * @return
	 */
	private Range convertRange(IDocument document, IMarker marker) {
		int line = marker.getAttribute(IMarker.LINE_NUMBER, -1) - 1;
		int cStart = 0;
		int cEnd = 0;
		try {
			//Buildship doesn't provide markers for gradle files, Maven does
			if (marker.isSubtypeOf(IMavenConstants.MARKER_ID)) {
				cStart = marker.getAttribute(IMavenConstants.MARKER_COLUMN_START, -1);
				cEnd = marker.getAttribute(IMavenConstants.MARKER_COLUMN_END, -1);
			} else {
				int lineOffset = 0;
				try {
					lineOffset = document.getLineOffset(line);
				} catch (BadLocationException unlikelyException) {
					JavaLanguageServerPlugin.logException(unlikelyException.getMessage(), unlikelyException);
					return new Range(new Position(line, 0), new Position(line, 0));
				}
				cEnd = marker.getAttribute(IMarker.CHAR_END, -1) - lineOffset;
				cStart = marker.getAttribute(IMarker.CHAR_START, -1) - lineOffset;
			}
		} catch (CoreException e) {
			JavaLanguageServerPlugin.logException(e.getMessage(), e);
		}
		cStart = Math.max(0, cStart);
		cEnd = Math.max(0, cEnd);

		return new Range(new Position(line, cStart), new Position(line, cEnd));
	}

	/**
	 * @param attribute
	 * @return
	 */
	private DiagnosticSeverity convertSeverity(int severity) {
		if (severity == IMarker.SEVERITY_ERROR) {
			return DiagnosticSeverity.Error;
		}
		if (severity == IMarker.SEVERITY_WARNING) {
			return DiagnosticSeverity.Warning;
		}
		return DiagnosticSeverity.Information;
	}
}