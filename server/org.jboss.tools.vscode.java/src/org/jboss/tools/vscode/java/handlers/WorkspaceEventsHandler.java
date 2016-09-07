package org.jboss.tools.vscode.java.handlers;

import java.util.Collections;
import java.util.List;

import org.eclipse.jdt.core.ICompilationUnit;
import org.jboss.tools.langs.DidChangeWatchedFilesParams;
import org.jboss.tools.langs.FileEvent;
import org.jboss.tools.langs.PublishDiagnosticsParams;
import org.jboss.tools.langs.base.LSPMethods;
import org.jboss.tools.langs.base.NotificationMessage;
import org.jboss.tools.vscode.ipc.RequestHandler;
import org.jboss.tools.vscode.java.JavaClientConnection;
import org.jboss.tools.vscode.java.managers.ProjectsManager;
import org.jboss.tools.vscode.java.managers.ProjectsManager.CHANGE_TYPE;

public class WorkspaceEventsHandler extends AbstractRequestHandler implements RequestHandler<DidChangeWatchedFilesParams, Object> {

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

	private CHANGE_TYPE toChangeType(Number vtype){
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
	public Object handle(DidChangeWatchedFilesParams param) {
		List<FileEvent> changes = param.getChanges();
		for (FileEvent fileEvent : changes) {
			Double eventType = fileEvent.getType();
			ICompilationUnit unit = resolveCompilationUnit(fileEvent.getUri());
			if(toChangeType(eventType)==CHANGE_TYPE.DELETED){
				cleanUpDiagnostics(fileEvent.getUri());
			}
			if (unit.isWorkingCopy()) {
				continue;
			}
			pm.fileChanged(fileEvent.getUri(), toChangeType(eventType));			
		}
		return null;
	}
	
	private void cleanUpDiagnostics(String uri){
		NotificationMessage<PublishDiagnosticsParams> message = new NotificationMessage<PublishDiagnosticsParams>();
		message.setMethod(LSPMethods.DOCUMENT_DIAGNOSTICS.getMethod());
		message.setParams(new PublishDiagnosticsParams().withUri(uri)
			.withDiagnostics(Collections.emptyList()));
		this.connection.send(message);
	}
	
}
