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

import java.util.ArrayList;
import java.util.List;

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
import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.IDocument;
import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.DiagnosticSeverity;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.PublishDiagnosticsParams;
import org.eclipse.lsp4j.Range;

/**
 * Listens to the resource change events and converts markers to diagnostics.
 *
 * @author Gorkem Ercan
 *
 */
public final class WorkspaceDiagnosticsHandler implements IResourceChangeListener, IResourceDeltaVisitor {
	private final JavaClientConnection connection;

	public WorkspaceDiagnosticsHandler(JavaClientConnection connection) {
		this.connection = connection;
	}

	@Override
	public void resourceChanged(IResourceChangeEvent event) {
		try {
			IResourceDelta delta = event.getDelta();
			delta.accept(this);
		} catch (CoreException e) {
			JavaLanguageServerPlugin.logException("failed to send diagnostics",e);
		}

	}

	/* (non-Javadoc)
	 * @see org.eclipse.core.resources.IResourceDeltaVisitor#visit(org.eclipse.core.resources.IResourceDelta)
	 */
	@Override
	public boolean visit(IResourceDelta delta) throws CoreException {
		IResource resource = delta.getResource();
		// Check if resource is accessible.
		// We do not deal with the markers for deleted files here
		// WorkspaceEventsHandler removes the diagnostics for deleted resources.
		if(resource == null || !resource.isAccessible()
				//ignore problems caused by standalone files
				|| JavaLanguageServerPlugin.getProjectsManager().getDefaultProject().equals(resource.getProject())) {
			return false;
		}
		// No marker changes continue to visit
		if((delta.getFlags() & IResourceDelta.MARKERS) == 0 ) return true;

		IFile file = resource.getAdapter(IFile.class);
		if (file == null ) return false;
		// Check if it is Java
		if(!JavaCore.isJavaLikeFileName(file.getName())) return true;

		IMarker[]  markers = resource.findMarkers(IJavaModelMarker.JAVA_MODEL_PROBLEM_MARKER, false,IResource.DEPTH_ONE);
		String uri = JDTUtils.getFileURI(resource);
		ICompilationUnit cu = JDTUtils.resolveCompilationUnit(uri);
		if (cu != null) {
			IDocument document = JsonRpcHelpers.toDocument(cu.getBuffer());
			this.connection.publishDiagnostics(
					new PublishDiagnosticsParams(uri,
							toDiagnosticsArray(document, markers)));
		}
		return true;
	}

	/**
	 * @param markers
	 * @param document
	 * @return
	 */
	public List<Diagnostic> toDiagnosticsArray(IDocument document, IMarker[] markers) {
		List<Diagnostic> diagnostics = new ArrayList<>();
		for(IMarker marker: markers){
			Diagnostic d = new Diagnostic();
			d.setSource(JavaLanguageServerPlugin.SERVER_SOURCE_ID);
			d.setMessage(marker.getAttribute(IMarker.MESSAGE,""));
			d.setCode(marker.getAttribute(IJavaModelMarker.ID,"0"));
			d.setSeverity(convertSeverity(marker.getAttribute(IMarker.SEVERITY,-1)));
			d.setRange(convertRange(document, marker));
			diagnostics.add(d);
		}
		return diagnostics;
	}

	/**
	 * @param marker
	 * @return
	 */
	private Range convertRange(IDocument document, IMarker marker) {
		int line = marker.getAttribute(IMarker.LINE_NUMBER,-1) -1;
		int lineOffset = 0;
		try {
			lineOffset = document.getLineOffset(line);
		} catch (BadLocationException unlikelyException) {
			JavaLanguageServerPlugin.logException(unlikelyException.getMessage(), unlikelyException);
			return new Range();
		}
		int cStart = marker.getAttribute(IMarker.CHAR_START, -1) -lineOffset ;
		int cEnd = marker.getAttribute(IMarker.CHAR_END, -1) -lineOffset;

		return new Range(new Position(line,cStart),
				new Position(line,cEnd));
	}

	/**
	 * @param attribute
	 * @return
	 */
	private DiagnosticSeverity convertSeverity(int severity) {
		if(severity == IMarker.SEVERITY_ERROR){
			return DiagnosticSeverity.Error;
		}
		if(severity == IMarker.SEVERITY_WARNING){
			return DiagnosticSeverity.Warning;
		}
		return DiagnosticSeverity.Information;
	}

}