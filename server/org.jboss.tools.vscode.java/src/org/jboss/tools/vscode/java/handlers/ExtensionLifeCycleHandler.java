package org.jboss.tools.vscode.java.handlers;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.Job;
import org.jboss.tools.vscode.ipc.RequestHandler;
import org.jboss.tools.vscode.ipc.ServiceStatus;
import org.jboss.tools.vscode.java.JavaClientConnection;
import org.jboss.tools.vscode.java.JavaLanguageServerPlugin;
import org.jboss.tools.vscode.java.managers.ProjectsManager;

import com.thetransactioncompany.jsonrpc2.JSONRPC2Notification;
import com.thetransactioncompany.jsonrpc2.JSONRPC2Request;
import com.thetransactioncompany.jsonrpc2.JSONRPC2Response;

/**
 * Handler for the VS Code extension life cycle events.
 * 
 * @author Gorkem Ercan
 * @author IBM Corporation (Markus Keller)
 *
 */
final public class ExtensionLifeCycleHandler implements RequestHandler {
	
	private static final String REQ_INIT = "initialize";
	private static final String REQ_SHUTDOWN  = "shutdown";
	private static final String REQ_EXIT  = "exit";
	private ProjectsManager projectsManager;
	private JavaClientConnection connection;

	public ExtensionLifeCycleHandler(ProjectsManager manager, JavaClientConnection connection) {
		this.projectsManager = manager;
		this.connection = connection;
	}

	@Override
	public boolean canHandle(final String request) {
		return REQ_INIT.equals(request)
				|| REQ_SHUTDOWN.equals(request)
				|| REQ_EXIT.equals(request);
	}

	@Override
	public JSONRPC2Response process(JSONRPC2Request request) {
		JavaLanguageServerPlugin.logInfo("ExtensionLifeCycleHandler process: " + request.toJSONString());
		switch (request.getMethod()) {
		case REQ_INIT:
			return handleInit(request);
		case REQ_SHUTDOWN:
			return handleShutdown(request);
		case REQ_EXIT:
			return handleExit(request);
		}

		return JsonRpcHelpers.methodNotFound(request);
	}
	
	private void triggerInitialization(String root) {
	  Job job = new Job("Initialize Workspace") {
	     protected IStatus run(IProgressMonitor monitor) {
			connection.sendStatus(ServiceStatus.Starting, "Init...");
			IStatus status = projectsManager.createProject(root, new ArrayList<IProject>(), new ServerStatusMonitor());
			if (status.isOK()) {
				connection.sendStatus(ServiceStatus.Started, "Ready");
			} else {
				connection.sendStatus(ServiceStatus.Error, "Initialization Failed");
			}
			return Status.OK_STATUS;
	     }
	  };
	  job.setPriority(Job.BUILD);
	  job.setRule(ResourcesPlugin.getWorkspace().getRoot());
	  job.schedule(); // small delay to not start sending status before initialize message has arrived
	}
	
	
	private JSONRPC2Response handleInit(JSONRPC2Request request) {
		
		String root = (String) request.getNamedParams().get("rootPath");
		triggerInitialization(root);
		
		Long parentProcess = (Long) request.getNamedParams().get("processId");
		JavaLanguageServerPlugin.getLanguageServer().setParentProcessId(parentProcess.longValue());
		
		JSONRPC2Response $ =  new JSONRPC2Response(request.getID());
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
		//Format
		capabilities.put("documentFormattingProvider", Boolean.TRUE);
		capabilities.put("documentRangeFormattingProvider", Boolean.TRUE);
		
		Map<String, Object> codeLensProvider = new HashMap<String, Object>();
		codeLensProvider.put("resolveProvider", Boolean.TRUE);
		capabilities.put("codeLensProvider", codeLensProvider);
		
		Map<String, Object> result = new HashMap<String, Object>();
		result.put("capabilities",capabilities);
		$.setResult(result);
		return $;
	}
	
	private JSONRPC2Response handleShutdown(JSONRPC2Request request) {
		JavaLanguageServerPlugin.logInfo("Shutting down Java Language Server");
		JavaLanguageServerPlugin.getLanguageServer().shutdown();
		return new JSONRPC2Response(request.getID());
	}

	private JSONRPC2Response handleExit(JSONRPC2Request request) {
		JavaLanguageServerPlugin.logInfo("Exiting Java Language Server");
		System.exit(0);
		return new JSONRPC2Response(request.getID());
	}
	
	@Override
	public void  process(JSONRPC2Notification request) {
	}
	
	private class ServerStatusMonitor extends NullProgressMonitor{
		private double totalWork;
		private double progress;
		@Override
		public void beginTask(String arg0, int totalWork) {
			this.totalWork = totalWork; 
		}

		@Override
		public void worked(int work) {
			progress += work;
			
			connection.sendStatus(ServiceStatus.Starting,  String.format( "%.0f%%", progress/totalWork * 100));
		}
		
	}
}
