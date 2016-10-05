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

import java.util.Collections;
import java.util.List;

import org.eclipse.jdt.core.ICompilationUnit;
import org.jboss.tools.langs.DidChangeWatchedFilesParams;
import org.jboss.tools.langs.FileEvent;
import org.jboss.tools.langs.PublishDiagnosticsParams;
import org.jboss.tools.langs.base.LSPMethods;
import org.jboss.tools.langs.base.NotificationMessage;
import org.jboss.tools.vscode.internal.ipc.CancelMonitor;
import org.jboss.tools.vscode.internal.ipc.RequestHandler;
import org.jboss.tools.vscode.java.internal.JDTUtils;
import org.jboss.tools.vscode.java.internal.JavaClientConnection;
import org.jboss.tools.vscode.java.internal.managers.ProjectsManager;
import org.jboss.tools.vscode.java.internal.managers.ProjectsManager.CHANGE_TYPE;

public class WorkspaceEventsHandler implements RequestHandler<DidChangeWatchedFilesParams, Object> {

	private final ProjectsManager pm ;
	private final JavaClientConnection connection;

	public WorkspaceEventsHandler(ProjectsManager projects, JavaClientConnection connection ) {
		this.pm = projects;
		this.connection = connection;
	}

	@Override
	public boolean canHandle(String request) {
		return LSPMethods.WORKSPACE_CHANGED_FILES.getMethod().equals(request);
	}

	private CHANGE_TYPE toChangeType(Integer vtype){
		switch (vtype.intValue()) {
		case 1:
			return CHANGE_TYPE.CREATED;
		case 2:
			return CHANGE_TYPE.CHANGED;
		case 3:
			return CHANGE_TYPE.DELETED;
		default:
			throw new UnsupportedOperationException();
		}
	}

	@Override
	public Object handle(DidChangeWatchedFilesParams param, CancelMonitor cm) {
		List<FileEvent> changes = param.getChanges();
		for (FileEvent fileEvent : changes) {
			CHANGE_TYPE changeType = toChangeType(fileEvent.getType());
			if(changeType==CHANGE_TYPE.DELETED){
				cleanUpDiagnostics(fileEvent.getUri());
			}
			ICompilationUnit unit = JDTUtils.resolveCompilationUnit(fileEvent.getUri());
			if (unit != null && unit.isWorkingCopy()) {
				continue;
			}
			pm.fileChanged(fileEvent.getUri(), changeType);
		}
		return null;
	}

	private void cleanUpDiagnostics(String uri){
		NotificationMessage<PublishDiagnosticsParams> message = new NotificationMessage<>();
		message.setMethod(LSPMethods.DOCUMENT_DIAGNOSTICS.getMethod());
		message.setParams(new PublishDiagnosticsParams().withUri(uri)
				.withDiagnostics(Collections.emptyList()));
		this.connection.send(message);
	}

}
