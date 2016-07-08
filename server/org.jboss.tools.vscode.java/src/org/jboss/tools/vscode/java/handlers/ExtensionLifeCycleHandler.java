package org.jboss.tools.vscode.java.handlers;

import java.util.HashMap;
import java.util.Map;

import org.jboss.tools.vscode.ipc.RequestHandler;
import org.jboss.tools.vscode.java.JavaLanguageServerPlugin;
import org.jboss.tools.vscode.java.managers.ProjectsManager;

import com.thetransactioncompany.jsonrpc2.JSONRPC2Notification;
import com.thetransactioncompany.jsonrpc2.JSONRPC2Request;
import com.thetransactioncompany.jsonrpc2.JSONRPC2Response;

/**
 * Handler for the VS Code extension life cycle events.
 * 
 * @author Gorkem Ercan
 *
 */
final public class ExtensionLifeCycleHandler implements RequestHandler {
	
	private static final String REQ_INIT = "initialize";
	private static final String REQ_SHUTDOWN  = "shutdown";
	private ProjectsManager projectsManager;

	public ExtensionLifeCycleHandler(ProjectsManager manager) {
		this.projectsManager = manager;
	}

	@Override
	public boolean canHandle(final String request) {
		return request != null && (REQ_INIT.equals(request) || REQ_SHUTDOWN.equals(request));
	}

	@Override
	public JSONRPC2Response process(JSONRPC2Request request) {
		JavaLanguageServerPlugin.logInfo("ExtensionLifeCycleHandler process: " + request.toJSONString());
		if(REQ_INIT.equals(request.getMethod()))
			return handleInit(request);
		if(REQ_SHUTDOWN.equals(request.getMethod()))
			return handleShutdown(request);
		
		return JsonRpcHelpers.methodNotFound(request);
	}
	
	private JSONRPC2Response handleInit(JSONRPC2Request request){
		JSONRPC2Response $ =  new JSONRPC2Response(request.getID());
		final String root = (String) request.getNamedParams().get("rootPath");
		projectsManager.createProject(root);
		
		Map<String, Object> capabilities = new HashMap<String,Object>();
		//textDocumentSync
		capabilities.put("textDocumentSync",new Integer(2));//2=Incremental
		//completionProvider
		Map<String, Object> completionProvider = new HashMap<String, Object>();
		completionProvider.put("resolveProvider", Boolean.FALSE);
		capabilities.put("completionProvider",completionProvider);
		//Hover
		capabilities.put("hoverProvider",Boolean.TRUE);
		//Goto Definition
		capabilities.put("definitionProvider",Boolean.TRUE);
		//Outline
		capabilities.put("documentSymbolProvider", Boolean.TRUE);
		//Open Type
		capabilities.put("workspaceSymbolProvider", Boolean.TRUE);
		capabilities.put("referencesProvider", Boolean.TRUE);
		capabilities.put("documentHighlightProvider", Boolean.TRUE);
		
		
		Map<String, Object> result = new HashMap<String, Object>();
		result.put("capabilities",capabilities);
		$.setResult(result);
		return $;
	}
	
	private JSONRPC2Response handleShutdown(JSONRPC2Request request){
		JavaLanguageServerPlugin.logInfo("Exiting Java Language Server");
		System.exit(0);
		return new JSONRPC2Response(request.getID());
	}

	@Override
	public void  process(JSONRPC2Notification request) {
	}
	
}
