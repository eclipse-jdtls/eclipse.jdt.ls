package org.jboss.tools.vscode.java.handlers;

import java.util.List;
import java.util.Map;

import org.eclipse.core.resources.ProjectScope;
import org.jboss.tools.vscode.ipc.RequestHandler;
import org.jboss.tools.vscode.java.managers.DocumentsManager;
import org.jboss.tools.vscode.java.managers.ProjectsManager;
import org.jboss.tools.vscode.java.managers.ProjectsManager.CHANGE_TYPE;

import com.thetransactioncompany.jsonrpc2.JSONRPC2Notification;
import com.thetransactioncompany.jsonrpc2.JSONRPC2Request;
import com.thetransactioncompany.jsonrpc2.JSONRPC2Response;

public class WorkspaceEventsHandler implements RequestHandler {

	private static final String REQ_FILE_CHANGE = "workspace/didChangeWatchedFiles";
	private static final int CHANGE_TYPE_CREATED = 1;
	private static final int CHANGE_TYPE_CHANGED = 2;
	private static final int CHANGE_TYPE_DELETED = 3;
	private final DocumentsManager dm ;
	private final ProjectsManager pm ;
	
	public WorkspaceEventsHandler(ProjectsManager projects, DocumentsManager documents) {
		this.dm = documents;
		this.pm = projects;
	}
	
	@Override
	public boolean canHandle(String request) {
		return REQ_FILE_CHANGE.equals(request);
	}

	@Override
	public JSONRPC2Response process(JSONRPC2Request request) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public void process(JSONRPC2Notification request) {
		List<Map<String,Object>> changes =  (List<Map<String, Object>>) request.getNamedParams().get("changes");
		for (Map<String, Object> obj : changes) {
			String uri = (String) obj.get("uri");
			Number type = (Number) obj.get("type");
			if(this.dm.isOpen(uri))//Open Java file
				continue;
			pm.fileChanged(uri, toChangeType(type));
			
		}
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

}
