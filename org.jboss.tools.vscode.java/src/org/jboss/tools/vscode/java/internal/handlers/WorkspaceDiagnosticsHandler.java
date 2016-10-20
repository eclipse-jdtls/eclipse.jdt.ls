/*******************************************************************************
 * Copyright (c) 2016 Red Hat Inc. and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Red Hat Inc. - initial API and implementation
 *******************************************************************************/
package org.jboss.tools.vscode.java.internal.handlers;

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
import org.eclipse.jdt.core.IJavaModelMarker;
import org.eclipse.jdt.core.JavaCore;
import org.jboss.tools.langs.Diagnostic;
import org.jboss.tools.langs.Position;
import org.jboss.tools.langs.PublishDiagnosticsParams;
import org.jboss.tools.langs.Range;
import org.jboss.tools.langs.base.LSPMethods;
import org.jboss.tools.langs.base.NotificationMessage;
import org.jboss.tools.vscode.java.internal.JDTUtils;
import org.jboss.tools.vscode.java.internal.JavaClientConnection;
import org.jboss.tools.vscode.java.internal.JavaLanguageServerPlugin;

/**
 * Listens to the resource change events and converts markers to diagnostics.
 *
 * @author Gorkem Ercan
 *
 */
final class WorkspaceDiagnosticsHandler implements IResourceChangeListener, IResourceDeltaVisitor {
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
		if(resource == null || !resource.isAccessible()) return false;
		// No marker changes continue to visit
		if((delta.getFlags() & IResourceDelta.MARKERS) == 0 ) return true;

		IFile file = resource.getAdapter(IFile.class);
		if (file == null ) return false;
		// Check if it is Java
		if(!JavaCore.isJavaLikeFileName(file.getName())) return true;

		IMarker[]  markers = resource.findMarkers(IJavaModelMarker.JAVA_MODEL_PROBLEM_MARKER, false,IResource.DEPTH_ONE);
		NotificationMessage<PublishDiagnosticsParams> message = new NotificationMessage<>();
		message.setMethod(LSPMethods.DOCUMENT_DIAGNOSTICS.getMethod());
		message.setParams(new PublishDiagnosticsParams().withUri(JDTUtils.getFileURI(resource))
				.withDiagnostics(toDiagnosticsArray(markers)));
		this.connection.send(message);
		return true;
	}
	/**
	 * @param markers
	 * @return
	 */
	private List<Diagnostic> toDiagnosticsArray(IMarker[] markers) {
		List<Diagnostic> diagnostics = new ArrayList<>();
		for(IMarker marker: markers){
			Diagnostic d = new Diagnostic();
			d.setSource(JavaLanguageServerPlugin.SERVER_SOURCE_ID);
			d.setMessage(marker.getAttribute(IMarker.MESSAGE,""));
			d.setCode(marker.getAttribute(IJavaModelMarker.ID,0));
			d.setSeverity(convertSeverity(marker.getAttribute(IMarker.SEVERITY,-1)));
			d.setRange(convertRange(marker));
			diagnostics.add(d);
		}

		return diagnostics;
	}

	/**
	 * @param marker
	 * @return
	 */
	private Range convertRange(IMarker marker) {
		int line = marker.getAttribute(IMarker.LINE_NUMBER,-1) -1;
		int cStart = marker.getAttribute(IMarker.CHAR_START, -1) -1 ;
		int cEnd = marker.getAttribute(IMarker.CHAR_END, -1) -1;

		return new Range().withStart(new Position().withLine(line).withCharacter(cStart))
				.withEnd(new Position().withLine(line).withCharacter(cEnd));
	}

	/**
	 * @param attribute
	 * @return
	 */
	private Integer convertSeverity(int severity) {
		if(severity == IMarker.SEVERITY_ERROR){
			return new Integer(1);
		}
		if(severity == IMarker.SEVERITY_WARNING){
			return new Integer(2);
		}
		return new Integer(3);
	}

}