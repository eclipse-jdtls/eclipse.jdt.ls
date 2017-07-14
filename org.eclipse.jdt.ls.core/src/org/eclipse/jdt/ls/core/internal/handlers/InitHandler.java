/*******************************************************************************
 * Copyright (c) 2016-2017 Red Hat Inc. and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Red Hat Inc. - initial API and implementation
 *     IBM Corporation (Markus Keller)
 *     Microsoft Corporation
 *******************************************************************************/
package org.eclipse.jdt.ls.core.internal.handlers;

import static org.eclipse.jdt.ls.core.internal.JavaLanguageServerPlugin.logInfo;

import java.net.URI;
import java.nio.file.Paths;
import java.util.Arrays;

import org.eclipse.core.resources.IResourceChangeEvent;
import org.eclipse.core.resources.IncrementalProjectBuilder;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.resources.WorkspaceJob;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.core.runtime.OperationCanceledException;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.SubMonitor;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.jdt.ls.core.internal.JavaClientConnection;
import org.eclipse.jdt.ls.core.internal.JavaLanguageServerPlugin;
import org.eclipse.jdt.ls.core.internal.ServiceStatus;
import org.eclipse.jdt.ls.core.internal.managers.ProjectsManager;
import org.eclipse.jdt.ls.core.internal.preferences.PreferenceManager;
import org.eclipse.lsp4j.ClientCapabilities;
import org.eclipse.lsp4j.CodeLensOptions;
import org.eclipse.lsp4j.CompletionOptions;
import org.eclipse.lsp4j.InitializeParams;
import org.eclipse.lsp4j.InitializeResult;
import org.eclipse.lsp4j.ServerCapabilities;
import org.eclipse.lsp4j.TextDocumentSyncKind;

/**
 * Handler for the VS Code extension initialization
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
		if(param.getCapabilities()==null){
			preferenceManager.updateClientPrefences(new ClientCapabilities());
		}else{
			preferenceManager.updateClientPrefences(param.getCapabilities());
		}
		String rootPath = param.getRootUri();
		if (rootPath != null) {
			URI uri = URI.create(rootPath);
			if ("file".equals(uri.getScheme())){
				rootPath = Paths.get(uri).toString();
			}
		}
		if (rootPath == null) {
			rootPath = ResourcesPlugin.getWorkspace().getRoot().getLocation().toOSString();
			logInfo("No root uri was defined. Falling back on "+rootPath);
		}
		triggerInitialization(rootPath);
		ResourcesPlugin.getWorkspace().addResourceChangeListener(new WorkspaceDiagnosticsHandler(connection, projectsManager), IResourceChangeEvent.POST_BUILD | IResourceChangeEvent.POST_CHANGE);
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
		if (!preferenceManager.getClientPreferences().isFormattingDynamicRegistrationSupported()) {
			capabilities.setDocumentFormattingProvider(Boolean.TRUE);
		}
		if (!preferenceManager.getClientPreferences().isRangeFormattingDynamicRegistrationSupported()) {
			capabilities.setDocumentRangeFormattingProvider(Boolean.TRUE);
		}
		if (!preferenceManager.getClientPreferences().isCodeLensDynamicRegistrationSupported()) {
			capabilities.setCodeLensProvider(new CodeLensOptions(true));
		}
		if (!preferenceManager.getClientPreferences().isSignatureHelpDynamicRegistrationSupported()) {
			capabilities.setSignatureHelpProvider(SignatureHelpHandler.createOptions());
		}
		capabilities.setCodeActionProvider(Boolean.TRUE);
		result.setCapabilities(capabilities);
		return result;
	}

	private void triggerInitialization(String root) {

		Job job = new WorkspaceJob("Initialize Workspace") {
			@Override
			public IStatus runInWorkspace(IProgressMonitor monitor) {
				connection.sendStatus(ServiceStatus.Starting, "Init...");
				SubMonitor subMonitor = SubMonitor.convert(new ServerStatusMonitor(), 100);
				try {
					projectsManager.initializeProjects(root, subMonitor.split(60));
					ResourcesPlugin.getWorkspace().build(IncrementalProjectBuilder.FULL_BUILD, subMonitor.split(40));
					connection.sendStatus(ServiceStatus.Started, "Ready");
				} catch (OperationCanceledException e) {
					connection.sendStatus(ServiceStatus.Error, "Initialization has been cancelled.");
				} catch (CoreException e) {
					JavaLanguageServerPlugin.logException("Initialization failed", e);
					connection.sendStatus(ServiceStatus.Error, getMessage(e.getStatus()));
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
		private final static long DELAY = 300;

		private double totalWork;
		private String subtask;
		private double progress;
		private long lastReport = 0;

		@Override
		public void beginTask(String task, int totalWork) {
			this.totalWork = totalWork;
			sendProgress();
		}

		@Override
		public void subTask(String name) {
			this.subtask = name;
			sendProgress();
		}

		@Override
		public void worked(int work) {
			progress += work;
			sendProgress();
		}

		private void sendProgress() {
			// throttle the sending of progress
			long currentTime = System.currentTimeMillis();
			if (currentTime - lastReport > DELAY) {
				lastReport = currentTime;
				String message = this.subtask == null || this.subtask.length() == 0 ? "" : (" - " + this.subtask);
				connection.sendStatus(ServiceStatus.Starting, String.format("%.0f%%  Starting Java Language Server %s", progress / totalWork * 100, message));
			}
		}

	}
}
