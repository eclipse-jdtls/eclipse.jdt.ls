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

import java.util.Collections;
import java.util.List;

import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.ls.core.internal.JDTUtils;
import org.eclipse.jdt.ls.core.internal.JavaClientConnection;
import org.eclipse.jdt.ls.core.internal.managers.ProjectsManager;
import org.eclipse.jdt.ls.core.internal.managers.ProjectsManager.CHANGE_TYPE;
import org.eclipse.lsp4j.DidChangeWatchedFilesParams;
import org.eclipse.lsp4j.FileChangeType;
import org.eclipse.lsp4j.FileEvent;
import org.eclipse.lsp4j.PublishDiagnosticsParams;

public class WorkspaceEventsHandler {

	private final ProjectsManager pm ;
	private final JavaClientConnection connection;

	public WorkspaceEventsHandler(ProjectsManager projects, JavaClientConnection connection ) {
		this.pm = projects;
		this.connection = connection;
	}

	private CHANGE_TYPE toChangeType(FileChangeType vtype){
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

	void didChangeWatchedFiles(DidChangeWatchedFilesParams param){
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
	}

	private void cleanUpDiagnostics(String uri){
		this.connection.publishDiagnostics(new PublishDiagnosticsParams(uri, Collections.emptyList()));
	}

}
