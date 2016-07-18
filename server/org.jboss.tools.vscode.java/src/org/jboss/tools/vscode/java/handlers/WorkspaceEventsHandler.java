package org.jboss.tools.vscode.java.handlers;

import java.util.List;

import org.eclipse.jdt.core.ICompilationUnit;
import org.jboss.tools.langs.DidChangeWatchedFilesParams;
import org.jboss.tools.langs.FileEvent;
import org.jboss.tools.langs.base.LSPMethods;
import org.jboss.tools.vscode.ipc.RequestHandler;
import org.jboss.tools.vscode.java.managers.ProjectsManager;
import org.jboss.tools.vscode.java.managers.ProjectsManager.CHANGE_TYPE;

public class WorkspaceEventsHandler extends AbstractRequestHandler implements RequestHandler<DidChangeWatchedFilesParams, Object> {

	private final ProjectsManager pm ;
	
	public WorkspaceEventsHandler(ProjectsManager projects) {
		this.pm = projects;
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
			ICompilationUnit unit = resolveCompilationUnit(fileEvent.getUri());
			if (unit.isWorkingCopy()) {
				continue;
			}
			pm.fileChanged(fileEvent.getUri(), toChangeType(fileEvent.getType()));			
		}
		return null;
	}
}
