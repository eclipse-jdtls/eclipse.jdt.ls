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

import java.util.Arrays;

import org.eclipse.core.resources.IResourceChangeEvent;
import org.eclipse.core.resources.IncrementalProjectBuilder;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
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
import org.jboss.tools.vscode.internal.ipc.CancelMonitor;
import org.jboss.tools.vscode.internal.ipc.RequestHandler;
import org.jboss.tools.vscode.internal.ipc.ServiceStatus;
import org.jboss.tools.vscode.java.internal.JavaClientConnection;
import org.jboss.tools.vscode.java.internal.JavaLanguageServerPlugin;
import org.jboss.tools.vscode.java.internal.managers.ProjectsManager;

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
	public InitializeResult handle(InitializeParams param, CancelMonitor cm) {
		triggerInitialization(param.getRootPath());
		ResourcesPlugin.getWorkspace().addResourceChangeListener(new WorkspaceDiagnosticsHandler(connection), IResourceChangeEvent.POST_BUILD);
		JavaLanguageServerPlugin.getLanguageServer().setParentProcessId(param.getProcessId().longValue());
		InitializeResult result = new InitializeResult();
		ServerCapabilities capabilities = new ServerCapabilities();
		return result.withCapabilities(
				capabilities.withTextDocumentSync(2)
				.withCompletionProvider(new CompletionOptions().withResolveProvider(Boolean.TRUE).withTriggerCharacters(Arrays.asList(".","@","#")))
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
			@Override
			protected IStatus run(IProgressMonitor monitor) {
				connection.sendStatus(ServiceStatus.Starting, "Init...");
				IStatus status = projectsManager.initializeProjects(root, new ServerStatusMonitor());
				try {
					ResourcesPlugin.getWorkspace().build(IncrementalProjectBuilder.FULL_BUILD, monitor);
				} catch (CoreException e) {
					JavaLanguageServerPlugin.logException("Build failed ", e);
				}
				if (status.isOK()) {
					connection.sendStatus(ServiceStatus.Started, "Ready");
				} else {
					connection.sendStatus(ServiceStatus.Error, getMessage(status));
				}
				return Status.OK_STATUS;
			}

			private String getMessage(IStatus status) {
				String msg = status.getMessage();
				if  (msg != null && !msg.isEmpty()) {
					return msg;
				}
				msg = "Initialization failed";
				if (status.getException() != null && status.getException().getMessage() != null) {
					msg = msg + ": "+ status.getException().getMessage();
				}
				return msg;
			}
		};
		job.setPriority(Job.BUILD);
		job.setRule(ResourcesPlugin.getWorkspace().getRoot());
		job.schedule();
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
