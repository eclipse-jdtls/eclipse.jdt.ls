package org.jboss.tools.vscode.java.handlers;

import java.util.ArrayList;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.Job;
import org.jboss.tools.langs.CodeLensOptions;
import org.jboss.tools.langs.CompletionOptions;
import org.jboss.tools.langs.InitializeParams;
import org.jboss.tools.langs.InitializeResult;
import org.jboss.tools.langs.ServerCapabilities;
import org.jboss.tools.langs.base.LSPMethods;
import org.jboss.tools.vscode.ipc.RequestHandler;
import org.jboss.tools.vscode.ipc.ServiceStatus;
import org.jboss.tools.vscode.java.JavaClientConnection;
import org.jboss.tools.vscode.java.JavaLanguageServerPlugin;
import org.jboss.tools.vscode.java.managers.ProjectsManager;

/**
 * Handler for the VS Code extension life cycle events.
 * 
 * @author Gorkem Ercan
 * @author IBM Corporation (Markus Keller)
 *
 */
final public class InitHandler implements RequestHandler<InitializeParams, InitializeResult> {
	
	private ProjectsManager projectsManager;
	private JavaClientConnection connection;

	public InitHandler(ProjectsManager manager, JavaClientConnection connection) {
		this.projectsManager = manager;
		this.connection = connection;
	}

	@Override
	public boolean canHandle(final String request) {
		return LSPMethods.INITIALIZE.getMethod().equals(request);
	}

	
	@Override
	public InitializeResult handle(InitializeParams param) {
		triggerInitialization(param.getRootPath());
		JavaLanguageServerPlugin.getLanguageServer().setParentProcessId(param.getProcessId().longValue());
		InitializeResult result = new InitializeResult();
		ServerCapabilities capabilities = new ServerCapabilities();
		return result.withCapabilities(
				capabilities.withTextDocumentSync(new Double(2))
				.withCompletionProvider(new CompletionOptions().withResolveProvider(Boolean.FALSE))
				.withHoverProvider(Boolean.TRUE)
				.withDefinitionProvider(Boolean.TRUE)
				.withDocumentSymbolProvider(Boolean.TRUE)
				.withWorkspaceSymbolProvider(Boolean.TRUE)
				.withReferencesProvider(Boolean.TRUE)
				.withDocumentHighlightProvider(Boolean.TRUE)
				.withDocumentFormattingProvider(Boolean.TRUE)
				.withDocumentRangeFormattingProvider(Boolean.TRUE)
				.withCodeLensProvider(new CodeLensOptions().withResolveProvider(Boolean.TRUE))
		);
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
