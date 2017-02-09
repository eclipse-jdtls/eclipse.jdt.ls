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

import static org.eclipse.jdt.ls.core.internal.JavaLanguageServerPlugin.logInfo;

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
import org.eclipse.jdt.ls.core.internal.JavaClientConnection;
import org.eclipse.jdt.ls.core.internal.JavaLanguageServerPlugin;
import org.eclipse.jdt.ls.core.internal.ServiceStatus;
import org.eclipse.jdt.ls.core.internal.managers.ProjectsManager;
import org.eclipse.jdt.ls.core.internal.preferences.PreferenceManager;
import org.eclipse.lsp4j.CodeLensOptions;
import org.eclipse.lsp4j.CompletionOptions;
import org.eclipse.lsp4j.InitializeParams;
import org.eclipse.lsp4j.InitializeResult;
import org.eclipse.lsp4j.ServerCapabilities;
import org.eclipse.lsp4j.TextDocumentSyncKind;

/**
 * Handler for the VS Code extension life cycle events.
 *
 * @author Gorkem Ercan
 * @author IBM Corporation (Markus Keller)
 *
 */
final public class InitHandler {

	private ProjectsManager projectsManager;
	private JavaClientConnection connection;
	private PreferenceManager preferenceManager;

	public InitHandler(ProjectsManager manager, PreferenceManager preferenceManager, JavaClientConnection connection) {
		this.projectsManager = manager;
		this.connection = connection;
		this.preferenceManager = preferenceManager;
	}


	InitializeResult initialize(InitializeParams param){
		logInfo("Initializing Java Language Server "+JavaLanguageServerPlugin.getVersion());
		triggerInitialization(param.getRootUri() == null? param.getRootPath():param.getRootUri());
		ResourcesPlugin.getWorkspace().addResourceChangeListener(new WorkspaceDiagnosticsHandler(connection), IResourceChangeEvent.POST_BUILD);
		JavaLanguageServerPlugin.getLanguageServer().setParentProcessId(param.getProcessId().longValue());
		InitializeResult result = new InitializeResult();
		ServerCapabilities capabilities = new ServerCapabilities();
		capabilities.setTextDocumentSync(TextDocumentSyncKind.Incremental);
		capabilities.setCompletionProvider(new CompletionOptions(Boolean.TRUE, Arrays.asList(".","@","#")));
		capabilities.setHoverProvider(Boolean.TRUE);
		capabilities.setDefinitionProvider(Boolean.TRUE);
		capabilities.setDocumentSymbolProvider(Boolean.TRUE);
		capabilities.setWorkspaceSymbolProvider(Boolean.TRUE);
		capabilities.setReferencesProvider(Boolean.TRUE);
		capabilities.setDocumentHighlightProvider(Boolean.TRUE);
		capabilities.setDocumentFormattingProvider(Boolean.TRUE);
		capabilities.setDocumentRangeFormattingProvider(Boolean.TRUE);
		capabilities.setCodeLensProvider(new CodeLensOptions(Boolean.TRUE));
		result.setCapabilities(capabilities);
		return result;
	}

	private void triggerInitialization(String root) {
		// Adjust any default preferences to server use
		preferenceManager.initialize();

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
