/*******************************************************************************
 * Copyright (c) 2016-2022 Red Hat Inc. and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *     Red Hat Inc. - initial API and implementation
 *******************************************************************************/
package org.eclipse.jdt.ls.core.internal.handlers;

import static org.eclipse.jdt.ls.core.internal.JavaLanguageServerPlugin.debugTrace;
import static org.eclipse.jdt.ls.core.internal.JavaLanguageServerPlugin.logException;
import static org.eclipse.jdt.ls.core.internal.JavaLanguageServerPlugin.logInfo;

import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.core.runtime.OperationCanceledException;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.WorkingCopyOwner;
import org.eclipse.jdt.launching.JavaRuntime;
import org.eclipse.jdt.ls.core.internal.BaseJDTLanguageServer;
import org.eclipse.jdt.ls.core.internal.BuildWorkspaceStatus;
import org.eclipse.jdt.ls.core.internal.IDelegateCommandHandler;
import org.eclipse.jdt.ls.core.internal.JDTUtils;
import org.eclipse.jdt.ls.core.internal.JSONUtility;
import org.eclipse.jdt.ls.core.internal.JVMConfigurator;
import org.eclipse.jdt.ls.core.internal.JavaClientConnection;
import org.eclipse.jdt.ls.core.internal.JavaClientConnection.JavaLanguageClient;
import org.eclipse.jdt.ls.core.internal.JavaLanguageServerPlugin;
import org.eclipse.jdt.ls.core.internal.JobHelpers;
import org.eclipse.jdt.ls.core.internal.LanguageServerApplication;
import org.eclipse.jdt.ls.core.internal.LanguageServerWorkingCopyOwner;
import org.eclipse.jdt.ls.core.internal.ServiceStatus;
import org.eclipse.jdt.ls.core.internal.codemanipulation.GenerateGetterSetterOperation.AccessorField;
import org.eclipse.jdt.ls.core.internal.handlers.CodeActionHandler.CodeActionData;
import org.eclipse.jdt.ls.core.internal.handlers.ExtractInterfaceHandler.CheckExtractInterfaceResponse;
import org.eclipse.jdt.ls.core.internal.handlers.FindLinksHandler.FindLinksParams;
import org.eclipse.jdt.ls.core.internal.handlers.GenerateAccessorsHandler.AccessorCodeActionParams;
import org.eclipse.jdt.ls.core.internal.handlers.GenerateAccessorsHandler.GenerateAccessorsParams;
import org.eclipse.jdt.ls.core.internal.handlers.GenerateConstructorsHandler.CheckConstructorsResponse;
import org.eclipse.jdt.ls.core.internal.handlers.GenerateConstructorsHandler.GenerateConstructorsParams;
import org.eclipse.jdt.ls.core.internal.handlers.GenerateDelegateMethodsHandler.CheckDelegateMethodsResponse;
import org.eclipse.jdt.ls.core.internal.handlers.GenerateDelegateMethodsHandler.GenerateDelegateMethodsParams;
import org.eclipse.jdt.ls.core.internal.handlers.GenerateToStringHandler.CheckToStringResponse;
import org.eclipse.jdt.ls.core.internal.handlers.GenerateToStringHandler.GenerateToStringParams;
import org.eclipse.jdt.ls.core.internal.handlers.GetRefactorEditHandler.GetRefactorEditParams;
import org.eclipse.jdt.ls.core.internal.handlers.GetRefactorEditHandler.RefactorWorkspaceEdit;
import org.eclipse.jdt.ls.core.internal.handlers.HashCodeEqualsHandler.CheckHashCodeEqualsResponse;
import org.eclipse.jdt.ls.core.internal.handlers.HashCodeEqualsHandler.GenerateHashCodeEqualsParams;
import org.eclipse.jdt.ls.core.internal.handlers.InferSelectionHandler.InferSelectionParams;
import org.eclipse.jdt.ls.core.internal.handlers.InferSelectionHandler.SelectionInfo;
import org.eclipse.jdt.ls.core.internal.handlers.MoveHandler.MoveDestinationsResponse;
import org.eclipse.jdt.ls.core.internal.handlers.MoveHandler.MoveParams;
import org.eclipse.jdt.ls.core.internal.handlers.OverrideMethodsHandler.AddOverridableMethodParams;
import org.eclipse.jdt.ls.core.internal.handlers.OverrideMethodsHandler.OverridableMethodsResponse;
import org.eclipse.jdt.ls.core.internal.handlers.WorkspaceSymbolHandler.SearchSymbolParams;
import org.eclipse.jdt.ls.core.internal.lsp.JavaProtocolExtensions;
import org.eclipse.jdt.ls.core.internal.lsp.ValidateDocumentParams;
import org.eclipse.jdt.ls.core.internal.managers.ContentProviderManager;
import org.eclipse.jdt.ls.core.internal.managers.ProjectsManager;
import org.eclipse.jdt.ls.core.internal.managers.StandardProjectsManager;
import org.eclipse.jdt.ls.core.internal.managers.TelemetryManager;
import org.eclipse.jdt.ls.core.internal.preferences.PreferenceManager;
import org.eclipse.jdt.ls.core.internal.preferences.Preferences;
import org.eclipse.jdt.ls.core.internal.text.correction.ChangeSignatureInfo;
import org.eclipse.lsp4j.CallHierarchyIncomingCall;
import org.eclipse.lsp4j.CallHierarchyIncomingCallsParams;
import org.eclipse.lsp4j.CallHierarchyItem;
import org.eclipse.lsp4j.CallHierarchyOutgoingCall;
import org.eclipse.lsp4j.CallHierarchyOutgoingCallsParams;
import org.eclipse.lsp4j.CallHierarchyPrepareParams;
import org.eclipse.lsp4j.CodeAction;
import org.eclipse.lsp4j.CodeActionParams;
import org.eclipse.lsp4j.CodeLens;
import org.eclipse.lsp4j.CodeLensOptions;
import org.eclipse.lsp4j.CodeLensParams;
import org.eclipse.lsp4j.Command;
import org.eclipse.lsp4j.CompletionItem;
import org.eclipse.lsp4j.CompletionList;
import org.eclipse.lsp4j.CompletionParams;
import org.eclipse.lsp4j.DeclarationParams;
import org.eclipse.lsp4j.DefinitionParams;
import org.eclipse.lsp4j.DidChangeConfigurationParams;
import org.eclipse.lsp4j.DidChangeTextDocumentParams;
import org.eclipse.lsp4j.DidChangeWatchedFilesParams;
import org.eclipse.lsp4j.DidChangeWorkspaceFoldersParams;
import org.eclipse.lsp4j.DidCloseTextDocumentParams;
import org.eclipse.lsp4j.DidOpenTextDocumentParams;
import org.eclipse.lsp4j.DidSaveTextDocumentParams;
import org.eclipse.lsp4j.DocumentFormattingParams;
import org.eclipse.lsp4j.DocumentHighlight;
import org.eclipse.lsp4j.DocumentHighlightParams;
import org.eclipse.lsp4j.DocumentOnTypeFormattingOptions;
import org.eclipse.lsp4j.DocumentOnTypeFormattingParams;
import org.eclipse.lsp4j.DocumentRangeFormattingParams;
import org.eclipse.lsp4j.DocumentSymbol;
import org.eclipse.lsp4j.DocumentSymbolParams;
import org.eclipse.lsp4j.ExecuteCommandOptions;
import org.eclipse.lsp4j.ExecuteCommandParams;
import org.eclipse.lsp4j.FoldingRange;
import org.eclipse.lsp4j.FoldingRangeRequestParams;
import org.eclipse.lsp4j.Hover;
import org.eclipse.lsp4j.HoverParams;
import org.eclipse.lsp4j.ImplementationParams;
import org.eclipse.lsp4j.InitializeParams;
import org.eclipse.lsp4j.InitializeResult;
import org.eclipse.lsp4j.InitializedParams;
import org.eclipse.lsp4j.InlayHint;
import org.eclipse.lsp4j.InlayHintParams;
import org.eclipse.lsp4j.Location;
import org.eclipse.lsp4j.LocationLink;
import org.eclipse.lsp4j.PrepareRenameDefaultBehavior;
import org.eclipse.lsp4j.PrepareRenameParams;
import org.eclipse.lsp4j.PrepareRenameResult;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.ReferenceParams;
import org.eclipse.lsp4j.RenameFilesParams;
import org.eclipse.lsp4j.RenameParams;
import org.eclipse.lsp4j.SelectionRange;
import org.eclipse.lsp4j.SelectionRangeParams;
import org.eclipse.lsp4j.SemanticTokens;
import org.eclipse.lsp4j.SemanticTokensParams;
import org.eclipse.lsp4j.SetTraceParams;
import org.eclipse.lsp4j.SignatureHelp;
import org.eclipse.lsp4j.SignatureHelpParams;
import org.eclipse.lsp4j.SymbolInformation;
import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.eclipse.lsp4j.TextEdit;
import org.eclipse.lsp4j.TypeDefinitionParams;
import org.eclipse.lsp4j.TypeHierarchyItem;
import org.eclipse.lsp4j.TypeHierarchyPrepareParams;
import org.eclipse.lsp4j.TypeHierarchySubtypesParams;
import org.eclipse.lsp4j.TypeHierarchySupertypesParams;
import org.eclipse.lsp4j.WillSaveTextDocumentParams;
import org.eclipse.lsp4j.WorkspaceEdit;
import org.eclipse.lsp4j.WorkspaceSymbol;
import org.eclipse.lsp4j.WorkspaceSymbolParams;
import org.eclipse.lsp4j.extended.ProjectBuildParams;
import org.eclipse.lsp4j.extended.ProjectConfigurationsUpdateParam;
import org.eclipse.lsp4j.jsonrpc.CompletableFutures;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.eclipse.lsp4j.jsonrpc.messages.Either3;
import org.eclipse.lsp4j.jsonrpc.services.JsonDelegate;
import org.eclipse.lsp4j.services.LanguageServer;
import org.eclipse.lsp4j.services.NotebookDocumentService;
import org.eclipse.lsp4j.services.TextDocumentService;
import org.eclipse.lsp4j.services.WorkspaceService;

/**
 * @author Gorkem Ercan
 *
 */
public class JDTLanguageServer extends BaseJDTLanguageServer implements LanguageServer, TextDocumentService, WorkspaceService,
		JavaProtocolExtensions {

	public static final String JAVA_LSP_JOIN_ON_COMPLETION = "java.lsp.joinOnCompletion";
	public static final String JAVA_LSP_INITIALIZE_WORKSPACE = "java.lsp.initializeWorkspace";
	private ProjectsManager pm;
	private LanguageServerWorkingCopyOwner workingCopyOwner;
	private PreferenceManager preferenceManager;
	private DocumentLifeCycleHandler documentLifeCycleHandler;
	private WorkspaceEventsHandler workspaceEventHandler;
	private WorkspaceDiagnosticsHandler workspaceDiagnosticsHandler;
	private ClasspathUpdateHandler classpathUpdateHandler;
	private JVMConfigurator jvmConfigurator;
	private WorkspaceExecuteCommandHandler commandHandler;
	private TypeHierarchyHandler typeHierarchyHandler = new TypeHierarchyHandler();

	private ProgressReporterManager progressReporterManager;
	/**
	 * The status of the language service
	 */
	private ServiceStatus status;
	private TelemetryManager telemetryManager;
	private Object codeActionLock = new Object();

	private Job shutdownJob = new Job("Shutdown...") {

		@Override
		protected IStatus run(IProgressMonitor monitor) {
			try {
				JavaRuntime.removeVMInstallChangedListener(jvmConfigurator);
				if (workspaceDiagnosticsHandler != null) {
					workspaceDiagnosticsHandler.removeResourceChangeListener();
					workspaceDiagnosticsHandler = null;
				}
				if (classpathUpdateHandler != null) {
					classpathUpdateHandler.removeElementChangeListener();
					classpathUpdateHandler = null;
				}
				ResourcesPlugin.getWorkspace().save(true, monitor);
			} catch (CoreException e) {
				logException(e.getMessage(), e);
			}
			return Status.OK_STATUS;
		}

	};

	@Override
	public LanguageServerWorkingCopyOwner getWorkingCopyOwner() {
		return workingCopyOwner;
	}

	public JDTLanguageServer(ProjectsManager projects, PreferenceManager preferenceManager) {
		this(projects, preferenceManager, WorkspaceExecuteCommandHandler.getInstance());
	}

	public JDTLanguageServer(ProjectsManager projects, PreferenceManager preferenceManager, TelemetryManager telemetryManager) {
		this(projects, preferenceManager, WorkspaceExecuteCommandHandler.getInstance(), telemetryManager);
	}

	public JDTLanguageServer(ProjectsManager projects, PreferenceManager preferenceManager, WorkspaceExecuteCommandHandler commandHandler) {
		this(projects, preferenceManager, commandHandler, new TelemetryManager());
	}

	public JDTLanguageServer(ProjectsManager projects, PreferenceManager preferenceManager, WorkspaceExecuteCommandHandler commandHandler, TelemetryManager telemetryManager) {
		this.pm = projects;
		this.preferenceManager = preferenceManager;
		this.jvmConfigurator = new JVMConfigurator();
		JavaRuntime.addVMInstallChangedListener(jvmConfigurator);
		this.commandHandler = commandHandler;
		this.telemetryManager = telemetryManager;
	}

	@Override
	public void connectClient(JavaLanguageClient client) {
		super.connectClient(client);
		progressReporterManager = new ProgressReporterManager(client, preferenceManager);
		this.workingCopyOwner = new LanguageServerWorkingCopyOwner(this.client);
		pm.setConnection(client);
		WorkingCopyOwner.setPrimaryBufferProvider(this.workingCopyOwner);
		this.documentLifeCycleHandler = new DocumentLifeCycleHandler(this.client, preferenceManager, pm, true);
		this.workspaceEventHandler = new WorkspaceEventsHandler(pm, this.client, this.documentLifeCycleHandler);
		this.telemetryManager.setLanguageClient(client);
		this.telemetryManager.setPreferenceManager(preferenceManager);
		this.telemetryManager.setProjectseManager(pm);
	}

	// For testing purpose
	public void setClientConnection(JavaClientConnection client) {
		this.client = client;
	}

	//For testing purposes
	public void disconnectClient() {
		Job.getJobManager().setProgressProvider(null);
	}

	public ProgressReporterManager getProgressReporterManager() {
		return this.progressReporterManager;
	}

	/* (non-Javadoc)
	 * @see org.eclipse.lsp4j.services.LanguageServer#initialize(org.eclipse.lsp4j.InitializeParams)
	 */
	@Override
	public CompletableFuture<InitializeResult> initialize(InitializeParams params) {
		logInfo(">> initialize");
		status = ServiceStatus.Starting;
		InitHandler handler = new InitHandler(pm, preferenceManager, client, commandHandler, telemetryManager);
		return CompletableFuture.completedFuture(handler.initialize(params));
	}

	/*
	 * (non-Javadoc)
	 * @see org.eclipse.lsp4j.services.LanguageServer#initialized(org.eclipse.lsp4j.InitializedParams)
	 */
	@Override
	public void initialized(InitializedParams params) {
		logInfo(">> initialized");
		try {
			JobHelpers.waitForInitializeJobs(60 * 60 * 1000); // 1 hour
		} catch (OperationCanceledException e) {
			logException(e.getMessage(), e);
		}
		logInfo(">> initialization job finished");

		Job initializeWorkspace = new Job("Initialize workspace") {

			@Override
			public IStatus run(IProgressMonitor monitor) {
				try {
					StandardProjectsManager.cleanInvalidJavaProjects(monitor);
					workspaceDiagnosticsHandler = new WorkspaceDiagnosticsHandler(JDTLanguageServer.this.client, pm, preferenceManager.getClientPreferences(), documentLifeCycleHandler);
					workspaceDiagnosticsHandler.addResourceChangeListener();
					classpathUpdateHandler = new ClasspathUpdateHandler(JDTLanguageServer.this.client, documentLifeCycleHandler);
					classpathUpdateHandler.addElementChangeListener();
					SourceAttachUpdateHandler attachListener = new SourceAttachUpdateHandler(client);
					attachListener.addElementChangeListener();

					registerCapabilities();
					// we do not have the user setting initialized yet at this point but we should
					// still call to enable defaults in case client does not support configuration changes
					syncCapabilitiesToSettings();

					// before send the service ready notification, make sure all bundles are synchronized
					synchronizeBundles();

					client.sendStatus(ServiceStatus.ServiceReady, "ServiceReady");
					status = ServiceStatus.ServiceReady;
					telemetryManager.onServiceReady(System.currentTimeMillis());
					pm.projectsImported(monitor);

					IndexUtils.copyIndexesToSharedLocation();
					JobHelpers.waitForBuildJobs(60 * 60 * 1000); // 1 hour
					logInfo(">> build jobs finished");
					// https://github.com/redhat-developer/vscode-java/issues/3637 - delay registerWatchers
					pm.registerWatchers();
					debugTrace(">> watchers registered");
					// https://github.com/redhat-developer/vscode-java/issues/3797
					pm.checkIndexes();
					debugTrace(">> indexes checked");
					pm.projectsBuildFinished(monitor);
					telemetryManager.onBuildFinished(System.currentTimeMillis());
					workspaceDiagnosticsHandler.publishDiagnostics(monitor);
				} catch (OperationCanceledException | CoreException e) {
					logException(e.getMessage(), e);
					pm.projectsBuildFinished(monitor);
					return Status.CANCEL_STATUS;
				}
				return Status.OK_STATUS;
			}

			@Override
			public boolean belongsTo(Object family) {
				return JAVA_LSP_INITIALIZE_WORKSPACE.equals(family);
			}

		};
		initializeWorkspace.setPriority(Job.BUILD);
		initializeWorkspace.setSystem(true);
		initializeWorkspace.schedule();
	}

	/**
	 * Register capabilities to client
	 */
	private void registerCapabilities() {
		if (preferenceManager.getClientPreferences().isWorkspaceSymbolDynamicRegistered()) {
			registerCapability(Preferences.WORKSPACE_SYMBOL_ID, Preferences.WORKSPACE_SYMBOL);
		}
		if (!preferenceManager.getClientPreferences().isClientDocumentSymbolProviderRegistered() && preferenceManager.getClientPreferences().isDocumentSymbolDynamicRegistered()) {
			registerCapability(Preferences.DOCUMENT_SYMBOL_ID, Preferences.DOCUMENT_SYMBOL);
		}
		if (preferenceManager.getClientPreferences().isDefinitionDynamicRegistered()) {
			registerCapability(Preferences.DEFINITION_ID, Preferences.DEFINITION);
		}
		if (preferenceManager.getClientPreferences().isDeclarationDynamicRegistered()) {
			registerCapability(Preferences.DECLARATION_ID, Preferences.DECLARATION);
		}
		if (preferenceManager.getClientPreferences().isTypeDefinitionDynamicRegistered()) {
			registerCapability(Preferences.TYPEDEFINITION_ID, Preferences.TYPEDEFINITION);
		}
		if (!preferenceManager.getClientPreferences().isClientHoverProviderRegistered() && preferenceManager.getClientPreferences().isHoverDynamicRegistered()) {
			registerCapability(Preferences.HOVER_ID, Preferences.HOVER);
		}
		if (preferenceManager.getClientPreferences().isReferencesDynamicRegistered()) {
			registerCapability(Preferences.REFERENCES_ID, Preferences.REFERENCES);
		}
		if (preferenceManager.getClientPreferences().isDocumentHighlightDynamicRegistered()) {
			registerCapability(Preferences.DOCUMENT_HIGHLIGHT_ID, Preferences.DOCUMENT_HIGHLIGHT);
		}
		if (preferenceManager.getClientPreferences().isWorkspaceFoldersSupported()) {
			registerCapability(Preferences.WORKSPACE_CHANGE_FOLDERS_ID, Preferences.WORKSPACE_CHANGE_FOLDERS);
		}
		if (preferenceManager.getClientPreferences().isImplementationDynamicRegistered()) {
			registerCapability(Preferences.IMPLEMENTATION_ID, Preferences.IMPLEMENTATION);
		}
		if (preferenceManager.getClientPreferences().isInlayHintDynamicRegistered()) {
			registerCapability(Preferences.INLAY_HINT_ID, Preferences.INLAY_HINT);
		}
	}

	/**
	 * Toggles the server capabilities according to user preferences.
	 */
	private void syncCapabilitiesToSettings() {
		if (preferenceManager.getClientPreferences().isCompletionDynamicRegistered()) {
			toggleCapability(preferenceManager.getPreferences().isCompletionEnabled(), Preferences.COMPLETION_ID, Preferences.COMPLETION, CompletionHandler.getDefaultCompletionOptions(preferenceManager));
		}
		if (preferenceManager.getClientPreferences().isFormattingDynamicRegistrationSupported()) {
			toggleCapability(preferenceManager.getPreferences().isJavaFormatEnabled(), Preferences.FORMATTING_ID, Preferences.TEXT_DOCUMENT_FORMATTING, null);
		}
		if (preferenceManager.getClientPreferences().isRangeFormattingDynamicRegistrationSupported()) {
			toggleCapability(preferenceManager.getPreferences().isJavaFormatEnabled(), Preferences.FORMATTING_RANGE_ID, Preferences.TEXT_DOCUMENT_RANGE_FORMATTING, null);
		}
		if (preferenceManager.getClientPreferences().isOnTypeFormattingDynamicRegistrationSupported()) {
			toggleCapability(preferenceManager.getPreferences().isJavaFormatOnTypeEnabled(), Preferences.FORMATTING_ON_TYPE_ID, Preferences.TEXT_DOCUMENT_ON_TYPE_FORMATTING,
					new DocumentOnTypeFormattingOptions(";", Arrays.asList("\n", "}")));
		}
		if (preferenceManager.getClientPreferences().isCodeLensDynamicRegistrationSupported()) {
			toggleCapability(preferenceManager.getPreferences().isCodeLensEnabled(), Preferences.CODE_LENS_ID, Preferences.TEXT_DOCUMENT_CODE_LENS, new CodeLensOptions(true));
		}
		if (preferenceManager.getClientPreferences().isSignatureHelpDynamicRegistrationSupported()) {
			toggleCapability(preferenceManager.getPreferences().isSignatureHelpEnabled(), Preferences.SIGNATURE_HELP_ID, Preferences.TEXT_DOCUMENT_SIGNATURE_HELP, SignatureHelpHandler.createOptions());
		}
		if (preferenceManager.getClientPreferences().isRenameDynamicRegistrationSupported()) {
			toggleCapability(preferenceManager.getPreferences().isRenameEnabled(), Preferences.RENAME_ID, Preferences.TEXT_DOCUMENT_RENAME, RenameHandler.createOptions());
		}
		if (preferenceManager.getClientPreferences().isExecuteCommandDynamicRegistrationSupported()) {
			toggleCapability(preferenceManager.getPreferences().isExecuteCommandEnabled(), Preferences.EXECUTE_COMMAND_ID, Preferences.WORKSPACE_EXECUTE_COMMAND,
					new ExecuteCommandOptions(new ArrayList<>(commandHandler.getNonStaticCommands())));
		}
		if (preferenceManager.getClientPreferences().isCodeActionDynamicRegistered()) {
			toggleCapability(preferenceManager.getClientPreferences().isCodeActionDynamicRegistered(), Preferences.CODE_ACTION_ID, Preferences.CODE_ACTION, CodeActionHandler.createOptions(preferenceManager));
		}
		if (preferenceManager.getClientPreferences().isFoldgingRangeDynamicRegistered()) {
			toggleCapability(preferenceManager.getPreferences().isFoldingRangeEnabled(), Preferences.FOLDINGRANGE_ID, Preferences.FOLDINGRANGE, null);
		}
		if (preferenceManager.getClientPreferences().isSelectionRangeDynamicRegistered()) {
			toggleCapability(preferenceManager.getPreferences().isSelectionRangeEnabled(), Preferences.SELECTION_RANGE_ID, Preferences.SELECTION_RANGE, null);
		}
	}

	/**
	 * Ask client to check if any bundles need to be synchronized.
	 */
	private void synchronizeBundles() {
		if (preferenceManager.getClientPreferences().isExecuteClientCommandSupport()) {
			try {
				Object commandResult = JavaLanguageServerPlugin.getInstance()
					.getClientConnection().executeClientCommand("_java.reloadBundles.command");
				if (commandResult instanceof List<?> list) {
					List<String> bundlesToRefresh = (List<String>) commandResult;
					if (bundlesToRefresh.size() > 0) {
						BundleUtils.loadBundles(bundlesToRefresh);
					}
				} else if (commandResult instanceof Map<?, ?> m) {
					String message = (String) m.get("message");
					JavaLanguageServerPlugin.logError(message);
				} else {
					JavaLanguageServerPlugin.logError(
						"Unexpected result from executeClientCommand: " + commandResult);
				}
			} catch (Exception e) {
				JavaLanguageServerPlugin.logException(e);
			}
		}
	}

	/* (non-Javadoc)
	 * @see org.eclipse.lsp4j.services.LanguageServer#shutdown()
	 */
	@Override
	public CompletableFuture<Object> shutdown() {
		logInfo(">> shutdown");
		return computeAsync((monitor) -> {
			shutdownJob.setSystem(true);
			shutdownJob.schedule();
			shutdownReceived = true;
			if (preferenceManager.getClientPreferences().shouldLanguageServerExitOnShutdown()) {
				Executors.newSingleThreadScheduledExecutor().schedule(() -> exit(), 1, TimeUnit.SECONDS);
			}
			return new Object();
		});
	}

	/* (non-Javadoc)
	 * @see org.eclipse.lsp4j.services.LanguageServer#exit()
	 */
	@Override
	public void exit() {
		logInfo(">> exit");
		LanguageServerApplication application = JavaLanguageServerPlugin.getLanguageServer();
		if (application != null && application.getParentProcessId() != ProcessHandle.current().pid()) {
			Executors.newSingleThreadScheduledExecutor().schedule(() -> {
				logInfo("Forcing exit after 1 min.");
				System.exit(FORCED_EXIT_CODE);
			}, 1, TimeUnit.MINUTES);
		}
		if (!shutdownReceived) {
			shutdownJob.setSystem(true);
			shutdownJob.schedule();
		}
		try {
			shutdownJob.join();
		} catch (InterruptedException e) {
			JavaLanguageServerPlugin.logException(e.getMessage(), e);
		}
		JavaLanguageServerPlugin.getLanguageServer().exit();
	}

	/* (non-Javadoc)
	 * @see org.eclipse.lsp4j.services.LanguageServer#getTextDocumentService()
	 */
	@Override
	public TextDocumentService getTextDocumentService() {
		return this;
	}

	/* (non-Javadoc)
	 * @see org.eclipse.lsp4j.services.LanguageServer#getWorkspaceService()
	 */
	@Override
	public WorkspaceService getWorkspaceService() {
		return this;
	}

	@JsonDelegate
	public JavaProtocolExtensions getJavaExtensions() {
		return this;
	}

	/* (non-Javadoc)
	 * @see org.eclipse.lsp4j.services.WorkspaceService#symbol(org.eclipse.lsp4j.WorkspaceSymbolParams)
	 */
	@Override
	public CompletableFuture<Either<List<? extends SymbolInformation>, List<? extends WorkspaceSymbol>>> symbol(WorkspaceSymbolParams params) {
		debugTrace(">> workspace/symbol");
		return computeAsync((monitor) -> {
			return Either.forLeft(WorkspaceSymbolHandler.search(params.getQuery(), monitor));
		});
	}

	/* (non-Javadoc)
	 * @see org.eclipse.lsp4j.services.WorkspaceService#didChangeConfiguration(org.eclipse.lsp4j.DidChangeConfigurationParams)
	 */
	@Override
	public void didChangeConfiguration(DidChangeConfigurationParams params) {
		debugTrace(">> workspace/didChangeConfiguration");
		Object settings = JSONUtility.toModel(params.getSettings(), Map.class);
		boolean nullAnalysisOptionsUpdated = false;
		if (settings instanceof Map) {
			Collection<IPath> rootPaths = preferenceManager.getPreferences().getRootPaths();
			@SuppressWarnings("unchecked")
			Preferences prefs = Preferences.createFrom((Map<String, Object>) settings);
			prefs.setRootPaths(rootPaths);
			boolean nullAnalysisConfigurationsChanged =!prefs.getNullableTypes().equals(preferenceManager.getPreferences().getNullableTypes())
				|| !prefs.getNonnullTypes().equals(preferenceManager.getPreferences().getNonnullTypes())
				|| !prefs.getNullAnalysisMode().equals(preferenceManager.getPreferences().getNullAnalysisMode());
			preferenceManager.update(prefs);
			if (nullAnalysisConfigurationsChanged) {
				// trigger rebuild all the projects when the null analysis configuration changed **and** the compiler options updated
				nullAnalysisOptionsUpdated = this.preferenceManager.getPreferences().updateAnnotationNullAnalysisOptions();
			}
		}
		if (status == ServiceStatus.ServiceReady) {
			// If we toggle on the capabilities too early before the tasks in initialized handler finished,
			// client will start to send request to server, but the server won't be able to handle them.
			syncCapabilitiesToSettings();
		}
		boolean jvmChanged = false;
		try {
			jvmChanged = JVMConfigurator.configureJVMs(preferenceManager.getPreferences(), this.client);
		} catch (Exception e) {
			JavaLanguageServerPlugin.logException(e.getMessage(), e);
		}
		try {
			boolean isAutobuildEnabled = preferenceManager.getPreferences().isAutobuildEnabled();
			boolean autoBuildChanged = ProjectsManager.setAutoBuilding(isAutobuildEnabled);
			if ((jvmChanged || nullAnalysisOptionsUpdated) && isAutobuildEnabled) {
				buildWorkspace(Either.forLeft(true));
			} else if (autoBuildChanged && isAutobuildEnabled) {
				buildWorkspace(Either.forLeft(false));
			} else if (nullAnalysisOptionsUpdated && !isAutobuildEnabled) {
				documentLifeCycleHandler.publishDiagnostics(new NullProgressMonitor());
			}
		} catch (CoreException e) {
			JavaLanguageServerPlugin.logException(e.getMessage(), e);
		}
		debugTrace(">> New configuration: " + settings);
	}


	/* (non-Javadoc)
	 * @see org.eclipse.lsp4j.services.WorkspaceService#didChangeWatchedFiles(org.eclipse.lsp4j.DidChangeWatchedFilesParams)
	 */
	@Override
	public void didChangeWatchedFiles(DidChangeWatchedFilesParams params) {
		debugTrace(">> workspace/didChangeWatchedFiles ");
		this.workspaceEventHandler.didChangeWatchedFiles(params);
	}

	/* (non-Javadoc)
	 * @see org.eclipse.lsp4j.services.WorkspaceService#executeCommand(org.eclipse.lsp4j.ExecuteCommandParams)
	 */
	@Override
	public CompletableFuture<Object> executeCommand(ExecuteCommandParams params) {
		debugTrace(">> workspace/executeCommand " + (params == null ? null : params.getCommand()));
		// see https://github.com/eclipse-jdtls/eclipse.jdt.ls/issues/2799
		// Handle the paste event synchronously in the main thread
		if (IDelegateCommandHandler.JAVA_EDIT_HANDLE_PASTE_EVENT.equals(params.getCommand()) || IDelegateCommandHandler.JAVA_EDIT_SMART_SEMICOLON_DETECTION.equals(params.getCommand())) {
			Object result = commandHandler.executeCommand(params, new NullProgressMonitor());
			return CompletableFuture.completedFuture(result);
		} else {
			return computeAsyncWithClientProgress((monitor) -> {
				return commandHandler.executeCommand(params, monitor);
			});
		}
	}

	/* (non-Javadoc)
	 * @see org.eclipse.lsp4j.services.TextDocumentService#completion(org.eclipse.lsp4j.CompletionParams)
	 */
	@Override
	public CompletableFuture<Either<List<CompletionItem>, CompletionList>> completion(CompletionParams position) {
		debugTrace(">> document/completion");
		try {
			CompletionHandler handler = new CompletionHandler(preferenceManager);
			IProgressMonitor monitor = new NullProgressMonitor();
			if (Boolean.getBoolean(JAVA_LSP_JOIN_ON_COMPLETION)) {
				waitForLifecycleJobs(monitor);
			}
			Either<List<CompletionItem>, CompletionList> result = handler.completion(position, monitor);
			return CompletableFuture.completedFuture(result);
		} catch (Exception ex) {
			return CompletableFuture.failedFuture(ex);
		}
	}

	/* (non-Javadoc)
	 * @see org.eclipse.lsp4j.services.TextDocumentService#resolveCompletionItem(org.eclipse.lsp4j.CompletionItem)
	 */
	@Override
	public CompletableFuture<CompletionItem> resolveCompletionItem(CompletionItem unresolved) {
		debugTrace(">> document/resolveCompletionItem");
		try {
			CompletionResolveHandler handler = new CompletionResolveHandler(preferenceManager);
			IProgressMonitor monitor = new NullProgressMonitor();
			if ((Boolean.getBoolean(JAVA_LSP_JOIN_ON_COMPLETION))) {
				waitForLifecycleJobs(monitor);
			}
			CompletionItem result = handler.resolve(unresolved, monitor);
			return CompletableFuture.completedFuture(result);
		} catch (Exception ex) {
			return CompletableFuture.failedFuture(ex);
		}
	}

	/* (non-Javadoc)
	 * @see org.eclipse.lsp4j.services.TextDocumentService#hover(org.eclipse.lsp4j.HoverParams)
	 */
	@Override
	public CompletableFuture<Hover> hover(HoverParams position) {
		debugTrace(">> document/hover");
		HoverHandler handler = new HoverHandler(this.preferenceManager);
		return computeAsync((monitor) -> handler.hover(position, monitor));
	}

	/* (non-Javadoc)
	 * @see org.eclipse.lsp4j.services.TextDocumentService#signatureHelp(org.eclipse.lsp4j.SignatureHelpParams)
	 */
	@Override
	public CompletableFuture<SignatureHelp> signatureHelp(SignatureHelpParams position) {
		debugTrace(">> document/signatureHelp");
		SignatureHelpHandler handler = new SignatureHelpHandler(preferenceManager);
		return computeAsync((monitor) -> handler.signatureHelp(position, monitor));
	}

	/* (non-Javadoc)
	 * @see org.eclipse.lsp4j.services.TextDocumentService#definition(org.eclipse.lsp4j.DefinitionParams)
	 */
	@Override
	public CompletableFuture<Either<List<? extends Location>, List<? extends LocationLink>>> definition(DefinitionParams position) {
		debugTrace(">> document/definition");
		NavigateToDefinitionHandler handler = new NavigateToDefinitionHandler(this.preferenceManager);
		return computeAsync((monitor) -> {
			waitForLifecycleJobs(monitor);
			return Either.forLeft(handler.definition(position, monitor));
		});
	}

	@Override
	public CompletableFuture<Either<List<? extends Location>, List<? extends LocationLink>>> declaration(DeclarationParams position) {
		debugTrace(">> document/declaration");
		NavigateToDeclarationHandler handler = new NavigateToDeclarationHandler(this.preferenceManager);
		return computeAsync((monitor) -> {
			waitForLifecycleJobs(monitor);
			return Either.forLeft(handler.declaration(position, monitor));
		});
	}

	/* (non-Javadoc)
	 * @see org.eclipse.lsp4j.services.TextDocumentService#typeDefinition(org.eclipse.lsp4j.TypeDefinitionParams)
	 */
	@Override
	public CompletableFuture<Either<List<? extends Location>, List<? extends LocationLink>>> typeDefinition(TypeDefinitionParams position) {
		debugTrace(">> document/typeDefinition");
		NavigateToTypeDefinitionHandler handler = new NavigateToTypeDefinitionHandler();
		return computeAsync((monitor) -> {
			waitForLifecycleJobs(monitor);
			return Either.forLeft((handler.typeDefinition(position, monitor)));
		});
	}

	/* (non-Javadoc)
	 * @see org.eclipse.lsp4j.services.TextDocumentService#references(org.eclipse.lsp4j.ReferenceParams)
	 */
	@Override
	public CompletableFuture<List<? extends Location>> references(ReferenceParams params) {
		debugTrace(">> document/references");
		ReferencesHandler handler = new ReferencesHandler(this.preferenceManager);
		return computeAsync((monitor) -> handler.findReferences(params, monitor));
	}

	@Override
	public CompletableFuture<List<? extends Location>> findLinks(FindLinksParams params) {
		debugTrace(">> java/findLinks");
		return computeAsync((monitor) -> FindLinksHandler.findLinks(params.type, params.position, monitor));
	}

	/* (non-Javadoc)
	 * @see org.eclipse.lsp4j.services.TextDocumentService#documentHighlight(org.eclipse.lsp4j.DocumentHighlightParams)
	 */
	@Override
	public CompletableFuture<List<? extends DocumentHighlight>> documentHighlight(DocumentHighlightParams position) {
		debugTrace(">> document/documentHighlight");
		return computeAsync((monitor) -> DocumentHighlightHandler.documentHighlight(position, monitor));
	}

	/* (non-Javadoc)
	 * @see org.eclipse.lsp4j.services.TextDocumentService#documentSymbol(org.eclipse.lsp4j.DocumentSymbolParams)
	 */
	@Override
	public CompletableFuture<List<Either<SymbolInformation, DocumentSymbol>>> documentSymbol(DocumentSymbolParams params) {
		debugTrace(">> document/documentSymbol");
		DocumentSymbolHandler handler = new DocumentSymbolHandler(preferenceManager);
		return computeAsync((monitor) -> {
			waitForLifecycleJobs(monitor);
			return handler.documentSymbol(params, monitor);
		});
	}

	/* (non-Javadoc)
	 * @see org.eclipse.lsp4j.services.TextDocumentService#codeAction(org.eclipse.lsp4j.CodeActionParams)
	 */
	@Override
	public CompletableFuture<List<Either<Command, CodeAction>>> codeAction(CodeActionParams params) {
		debugTrace(">> document/codeAction");
		CodeActionHandler handler = new CodeActionHandler(this.preferenceManager);
		return computeAsync((monitor) -> {
			waitForLifecycleJobs(monitor);
			// see https://github.com/eclipse-jdtls/eclipse.jdt.ls/issues/2799
			// Optimize the performance of the code actions
			synchronized (codeActionLock) {
				return handler.getCodeActionCommands(params, monitor);
			}
		});
	}

	/* (non-Javadoc)
	 * @see org.eclipse.lsp4j.services.TextDocumentService#resolveCodeAction(org.eclipse.lsp4j.CodeAction)
	 */
	@Override
	public CompletableFuture<CodeAction> resolveCodeAction(CodeAction params) {
		debugTrace(">> codeAction/resolve");
		Object data = params.getData();
		// if no data property is specified, no further resolution the server can provide, so return the original result back.
		if (data == null) {
			return CompletableFuture.completedFuture(params);
		}
		if (data instanceof CodeActionData codeActionData) {
			// if the data is CodeActionData and no proposal in the data, return the original result back.
			if (codeActionData.getProposal() == null) {
				return CompletableFuture.completedFuture(params);
			}
		}
		if (CodeActionHandler.codeActionStore.isEmpty()) {
			return CompletableFuture.completedFuture(params);
		}
		CodeActionResolveHandler handler = new CodeActionResolveHandler();
		return computeAsync((monitor) -> {
			waitForLifecycleJobs(monitor);
			return handler.resolve(params, monitor);
		});
	}

	/* (non-Javadoc)
	 * @see org.eclipse.lsp4j.services.TextDocumentService#codeLens(org.eclipse.lsp4j.CodeLensParams)
	 */
	@Override
	public CompletableFuture<List<? extends CodeLens>> codeLens(CodeLensParams params) {
		debugTrace(">> document/codeLens");
		CodeLensHandler handler = new CodeLensHandler(preferenceManager);
		return computeAsync((monitor) -> {
			waitForLifecycleJobs(monitor);
			return handler.getCodeLensSymbols(params.getTextDocument().getUri(), monitor);
		});
	}

	/* (non-Javadoc)
	 * @see org.eclipse.lsp4j.services.TextDocumentService#resolveCodeLens(org.eclipse.lsp4j.CodeLens)
	 */
	@Override
	public CompletableFuture<CodeLens> resolveCodeLens(CodeLens unresolved) {
		debugTrace(">> codeLens/resolve");
		CodeLensHandler handler = new CodeLensHandler(preferenceManager);
		return computeAsync((monitor) -> {
			waitForLifecycleJobs(monitor);
			return handler.resolve(unresolved, monitor);
		});
	}

	/* (non-Javadoc)
	 * @see org.eclipse.lsp4j.services.TextDocumentService#formatting(org.eclipse.lsp4j.DocumentFormattingParams)
	 */
	@Override
	public CompletableFuture<List<? extends TextEdit>> formatting(DocumentFormattingParams params) {
		debugTrace(">> document/formatting");
		FormatterHandler handler = new FormatterHandler(preferenceManager);
		return computeAsync((monitor) -> handler.formatting(params, monitor));
	}

	/* (non-Javadoc)
	 * @see org.eclipse.lsp4j.services.TextDocumentService#rangeFormatting(org.eclipse.lsp4j.DocumentRangeFormattingParams)
	 */
	@Override
	public CompletableFuture<List<? extends TextEdit>> rangeFormatting(DocumentRangeFormattingParams params) {
		debugTrace(">> document/rangeFormatting");
		FormatterHandler handler = new FormatterHandler(preferenceManager);
		return computeAsync((monitor) -> handler.rangeFormatting(params, monitor));
	}

	/* (non-Javadoc)
	 * @see org.eclipse.lsp4j.services.TextDocumentService#onTypeFormatting(org.eclipse.lsp4j.DocumentOnTypeFormattingParams)
	 */
	@Override
	public CompletableFuture<List<? extends TextEdit>> onTypeFormatting(DocumentOnTypeFormattingParams params) {
		debugTrace(">> document/onTypeFormatting");
		FormatterHandler handler = new FormatterHandler(preferenceManager);
		return computeAsync((monitor) -> handler.onTypeFormatting(params, monitor));
	}

	/* (non-Javadoc)
	 * @see org.eclipse.lsp4j.services.TextDocumentService#prepareRename(org.eclipse.lsp4j.PrepareRenameParams)
	 */
	@Override
	public CompletableFuture<Either3<Range, PrepareRenameResult, PrepareRenameDefaultBehavior>> prepareRename(PrepareRenameParams params) {
		debugTrace(">> document/prepareRename");
		PrepareRenameHandler handler = new PrepareRenameHandler(preferenceManager);
		return computeAsync((monitor) -> {
			waitForLifecycleJobs(monitor);
			return Either3.forLeft3(handler.prepareRename(params, monitor));
		});
	}

	/* (non-Javadoc)
	 * @see org.eclipse.lsp4j.services.TextDocumentService#rename(org.eclipse.lsp4j.RenameParams)
	 */
	@Override
	public CompletableFuture<WorkspaceEdit> rename(RenameParams params) {
		debugTrace(">> document/rename");
		RenameHandler handler = new RenameHandler(preferenceManager);
		return computeAsync((monitor) -> {
			waitForLifecycleJobs(monitor);
			return handler.rename(params, monitor);
		});
	}

	/* (non-Javadoc)
	 * @see org.eclipse.lsp4j.services.TextDocumentService#didOpen(org.eclipse.lsp4j.DidOpenTextDocumentParams)
	 */
	@Override
	public void didOpen(DidOpenTextDocumentParams params) {
		debugTrace(">> document/didOpen");
		documentLifeCycleHandler.didOpen(params);
	}

	/* (non-Javadoc)
	 * @see org.eclipse.lsp4j.services.TextDocumentService#didChange(org.eclipse.lsp4j.DidChangeTextDocumentParams)
	 */
	@Override
	public void didChange(DidChangeTextDocumentParams params) {
		debugTrace(">> document/didChange");
		documentLifeCycleHandler.didChange(params);
	}

	/* (non-Javadoc)
	 * @see org.eclipse.lsp4j.services.TextDocumentService#didClose(org.eclipse.lsp4j.DidCloseTextDocumentParams)
	 */
	@Override
	public void didClose(DidCloseTextDocumentParams params) {
		debugTrace(">> document/didClose");
		documentLifeCycleHandler.didClose(params);
	}

	@Override
	public void validateDocument(ValidateDocumentParams params) {
		logInfo(">> java/validateDocument");
		computeAsync((monitor) -> {
			try {
				documentLifeCycleHandler.validateDocument(params.getTextDocument().getUri(), true, monitor);
			} catch (JavaModelException e) {
				// ignore
			}
			return null;
		});
	}

	/* (non-Javadoc)
	 * @see org.eclipse.lsp4j.services.TextDocumentService#willSaveWaitUntil(org.eclipse.lsp4j.WillSaveTextDocumentParams)
	 */
	@Override
	public CompletableFuture<List<TextEdit>> willSaveWaitUntil(WillSaveTextDocumentParams params) {
		debugTrace(">> document/willSaveWaitUntil");
		SaveActionHandler handler = new SaveActionHandler(preferenceManager);
		return computeAsync((monitor) -> handler.willSaveWaitUntil(params, monitor));
	}

	/* (non-Javadoc)
	 * @see org.eclipse.lsp4j.services.TextDocumentService#didSave(org.eclipse.lsp4j.DidSaveTextDocumentParams)
	 */
	@Override
	public void didSave(DidSaveTextDocumentParams params) {
		debugTrace(">> document/didSave");
		documentLifeCycleHandler.didSave(params);
		String documentUri = params.getTextDocument().getUri();
		Preferences preferences = preferenceManager.getPreferences();
		List<String> lspCleanups = Collections.emptyList();
		if (preferences.getCleanUpActionsOnSaveEnabled()) {
			lspCleanups = preferences.getCleanUpActions();
		}

		if (lspCleanups.contains(BaseDocumentLifeCycleHandler.RENAME_FILE_TO_TYPE)) {
			computeAsync((monitor) -> {
				DocumentLifeCycleHandler.handleFileRenameForTypeDeclaration(documentUri);
				return null;
			});
		}
	}

	@Override
	public CompletableFuture<WorkspaceEdit> willRenameFiles(RenameFilesParams params) {
		debugTrace(">> workspace/willRenameFiles");
		return computeAsyncWithClientProgress((monitor) -> {
			waitForLifecycleJobs(monitor);
			return FileEventHandler.handleWillRenameFiles(params, monitor);
		});
	}

	/* (non-Javadoc)
	 * @see org.eclipse.jdt.ls.core.internal.JavaProtocolExtensions#ClassFileContents(org.eclipse.lsp4j.TextDocumentIdentifier)
	 */
	@Override
	public CompletableFuture<String> classFileContents(TextDocumentIdentifier param) {
		debugTrace(">> java/classFileContents");
		ContentProviderManager handler = JavaLanguageServerPlugin.getContentProviderManager();
		URI uri = JDTUtils.toURI(param.getUri());
		return computeAsync((monitor) -> handler.getContent(uri, monitor));
	}

	/* (non-Javadoc)
	 * @see org.eclipse.jdt.ls.core.internal.JavaProtocolExtensions#projectConfigurationUpdate(org.eclipse.lsp4j.TextDocumentIdentifier)
	 */
	@Override
	public void projectConfigurationUpdate(TextDocumentIdentifier param) {
		debugTrace(">> java/projectConfigurationUpdate");
		ProjectConfigurationUpdateHandler handler = new ProjectConfigurationUpdateHandler(pm);
		handler.updateConfiguration(param);
	}

	/* (non-Javadoc)
	 * @see org.eclipse.jdt.ls.core.internal.JavaProtocolExtensions#projectConfigurationsUpdate(org.eclipse.lsp4j.extended.ProjectConfigurationsUpdateParam)
	 */
	@Override
	public void projectConfigurationsUpdate(ProjectConfigurationsUpdateParam param) {
		debugTrace(">> java/projectConfigurationsUpdate");
		ProjectConfigurationUpdateHandler handler = new ProjectConfigurationUpdateHandler(pm);
		handler.updateConfigurations(param.getIdentifiers());
	}

	/* (non-Javadoc)
	 * @see org.eclipse.jdt.ls.core.internal.JavaProtocolExtensions#buildWorkspace(boolean)
	 */
	@Override
	public CompletableFuture<BuildWorkspaceStatus> buildWorkspace(Either<Boolean, boolean[]> forceRebuild) {
		// See https://github.com/redhat-developer/vscode-java/issues/1929,
		// some language client will convert the parameter to an array.
		boolean rebuild = forceRebuild.isLeft() ? forceRebuild.getLeft() : forceRebuild.getRight()[0];
		debugTrace(">> java/buildWorkspace (" + (rebuild ? "full)" : "incremental)"));
		BuildWorkspaceHandler handler = new BuildWorkspaceHandler(pm);
		return computeAsyncWithClientProgress((monitor) -> handler.buildWorkspace(rebuild, monitor));
	}

	@Override
	public CompletableFuture<BuildWorkspaceStatus> buildProjects(ProjectBuildParams params) {
		debugTrace(">> java/buildProjects");
		BuildWorkspaceHandler handler = new BuildWorkspaceHandler(pm);
		return computeAsyncWithClientProgress((monitor) -> handler.buildProjects(params, monitor));
	}

	/* (non-Javadoc)
	 * @see org.eclipse.lsp4j.services.WorkspaceService#didChangeWorkspaceFolders(DidChangeWorkspaceFoldersParams)
	 */
	@Override
	public void didChangeWorkspaceFolders(DidChangeWorkspaceFoldersParams params) {
		debugTrace(">> java/didChangeWorkspaceFolders");
		if (!preferenceManager.getClientPreferences().skipProjectConfiguration()) {
			WorkspaceFolderChangeHandler handler = new WorkspaceFolderChangeHandler(pm, preferenceManager);
			handler.update(params);
		}
	}

	@Override
	public CompletableFuture<Either<List<? extends Location>, List<? extends LocationLink>>> implementation(ImplementationParams position) {
		debugTrace(">> document/implementation");
		return computeAsyncWithClientProgress((monitor) -> {
			ImplementationsHandler handler = new ImplementationsHandler(preferenceManager);
			return Either.forLeft(handler.findImplementations(position, monitor));
		});
	}

	/* (non-Javadoc)
	 * @see org.eclipse.lsp4j.services.TextDocumentService#foldingRange(org.eclipse.lsp4j.FoldingRangeRequestParams)
	 */
	@Override
	public CompletableFuture<List<FoldingRange>> foldingRange(FoldingRangeRequestParams params) {
		debugTrace(">> document/foldingRange");
		return computeAsyncWithClientProgress((monitor) -> {
			waitForLifecycleJobs(monitor);
			return new FoldingRangeHandler().foldingRange(params, monitor);
		});
	}

	@Override
	public CompletableFuture<List<SelectionRange>> selectionRange(SelectionRangeParams params) {
		debugTrace(">> document/selectionRange");
		return computeAsyncWithClientProgress((monitor) -> {
			waitForLifecycleJobs(monitor);
			return new SelectionRangeHandler().selectionRange(params, monitor);
		});
	}

	@Override
	public CompletableFuture<OverridableMethodsResponse> listOverridableMethods(CodeActionParams params) {
		debugTrace(">> java/listOverridableMethods");
		return computeAsync((monitor) -> OverrideMethodsHandler.listOverridableMethods(params, monitor));
	}

	@Override
	public CompletableFuture<WorkspaceEdit> addOverridableMethods(AddOverridableMethodParams params) {
		debugTrace(">> java/addOverridableMethods");
		return computeAsync((monitor) -> OverrideMethodsHandler.addOverridableMethods(params, monitor));
	}

	@Override
	public CompletableFuture<CheckHashCodeEqualsResponse> checkHashCodeEqualsStatus(CodeActionParams params) {
		debugTrace(">> java/checkHashCodeEqualsStatus");
		return computeAsync((monitor) -> HashCodeEqualsHandler.checkHashCodeEqualsStatus(params, monitor));
	}

	@Override
	public CompletableFuture<WorkspaceEdit> generateHashCodeEquals(GenerateHashCodeEqualsParams params) {
		debugTrace(">> java/generateHashCodeEquals");
		return computeAsync((monitor) -> HashCodeEqualsHandler.generateHashCodeEquals(params, monitor));
	}

	@Override
	public CompletableFuture<CheckToStringResponse> checkToStringStatus(CodeActionParams params) {
		debugTrace(">> java/checkToStringStatus");
		return computeAsync((monitor) -> GenerateToStringHandler.checkToStringStatus(params, monitor));
	}

	@Override
	public CompletableFuture<WorkspaceEdit> generateToString(GenerateToStringParams params) {
		debugTrace(">> java/generateToString");
		return computeAsync((monitor) -> GenerateToStringHandler.generateToString(params, monitor));
	}

	@Override
	public CompletableFuture<WorkspaceEdit> organizeImports(CodeActionParams params) {
		debugTrace(">> java/organizeImports");
		return computeAsync((monitor) -> OrganizeImportsHandler.organizeImports(client, params, monitor));
	}

	@Override
	public CompletableFuture<WorkspaceEdit> cleanup(TextDocumentIdentifier doc) {
		debugTrace(">> java/cleanup");
		SaveActionHandler handler = new SaveActionHandler(preferenceManager);
		return computeAsync((monitor) -> handler.performManualCleanupActions(doc, monitor));
	}

	@Override
	public CompletableFuture<AccessorField[]> resolveUnimplementedAccessors(AccessorCodeActionParams params) {
		debugTrace(">> java/resolveUnimplementedAccessors");
		return computeAsync((monitor) -> GenerateAccessorsHandler.getUnimplementedAccessors(params));
	}

	@Override
	public CompletableFuture<WorkspaceEdit> generateAccessors(GenerateAccessorsParams params) {
		debugTrace(">> java/generateAccessors");
		return computeAsync((monitor) -> GenerateAccessorsHandler.generateAccessors(params, monitor));
	}

	@Override
	public CompletableFuture<CheckConstructorsResponse> checkConstructorsStatus(CodeActionParams params) {
		debugTrace(">> java/checkConstructorsStatus");
		return computeAsync((monitor) -> GenerateConstructorsHandler.checkConstructorsStatus(params, monitor));
	}

	@Override
	public CompletableFuture<WorkspaceEdit> generateConstructors(GenerateConstructorsParams params) {
		debugTrace(">> java/generateConstructors");
		return computeAsync((monitor) -> GenerateConstructorsHandler.generateConstructors(params, monitor));
	}

	@Override
	public CompletableFuture<CheckDelegateMethodsResponse> checkDelegateMethodsStatus(CodeActionParams params) {
		debugTrace(">> java/checkDelegateMethodsStatus");
		return computeAsync((monitor) -> GenerateDelegateMethodsHandler.checkDelegateMethodsStatus(params, monitor));
	}

	@Override
	public CompletableFuture<WorkspaceEdit> generateDelegateMethods(GenerateDelegateMethodsParams params) {
		debugTrace(">> java/generateDelegateMethods");
		return computeAsync((monitor) -> GenerateDelegateMethodsHandler.generateDelegateMethods(params, monitor));
	}

	@Override
	public CompletableFuture<RefactorWorkspaceEdit> getRefactorEdit(GetRefactorEditParams params) {
		debugTrace(">> java/getRefactorEdit");
		return computeAsync((monitor) -> GetRefactorEditHandler.getEditsForRefactor(params));
	}

	@Override
	public CompletableFuture<ChangeSignatureInfo> getChangeSignatureInfo(CodeActionParams params) {
		debugTrace(">> java/getChangeSignatureInfo");
		return computeAsync((monitor) -> ChangeSignatureInfoHandler.getChangeSignatureInfo(params, monitor));
	}

	@Override
	public CompletableFuture<List<SelectionInfo>> inferSelection(InferSelectionParams params) {
		debugTrace(">> java/inferSelection");
		return computeAsync((monitor) -> InferSelectionHandler.inferSelectionsForRefactor(params));
	}

	@Override
	public CompletableFuture<MoveDestinationsResponse> getMoveDestinations(MoveParams params) {
		debugTrace(">> java/getMoveDestinations");
		return computeAsync((monitor) -> MoveHandler.getMoveDestinations(params));
	}

	@Override
	public CompletableFuture<RefactorWorkspaceEdit> move(MoveParams params) {
		debugTrace(">> java/move");
		return computeAsyncWithClientProgress((monitor) -> MoveHandler.move(params, monitor));
	}

	@Override
	public CompletableFuture<List<SymbolInformation>> searchSymbols(SearchSymbolParams params) {
		debugTrace(">> java/searchSymbols");
		return computeAsyncWithClientProgress((monitor) -> WorkspaceSymbolHandler.search(params.getQuery(), params.maxResults, params.projectName, params.sourceOnly, monitor));
	}

	@Override
	public CompletableFuture<List<CallHierarchyItem>> prepareCallHierarchy(CallHierarchyPrepareParams params) {
		debugTrace(">> textDocument/prepareCallHierarchy");
		return computeAsyncWithClientProgress((monitor) -> new CallHierarchyHandler().prepareCallHierarchy(params, monitor));
	}

	@Override
	public CompletableFuture<List<CallHierarchyIncomingCall>> callHierarchyIncomingCalls(CallHierarchyIncomingCallsParams params) {
		debugTrace(">> callHierarchy/incomingCalls");
		return computeAsyncWithClientProgress((monitor) -> new CallHierarchyHandler().callHierarchyIncomingCalls(params, monitor));
	}

	@Override
	public CompletableFuture<List<CallHierarchyOutgoingCall>> callHierarchyOutgoingCalls(CallHierarchyOutgoingCallsParams params) {
		debugTrace(">> callHierarchy/outgoingCalls");
		return computeAsyncWithClientProgress((monitor) -> new CallHierarchyHandler().callHierarchyOutgoingCalls(params, monitor));
	}

	@Override
	public CompletableFuture<SemanticTokens> semanticTokensFull(SemanticTokensParams params) {
		debugTrace(">> textDocument/semanticTokens/full");
		return computeAsync(monitor -> SemanticTokensHandler.full(monitor, params,
			documentLifeCycleHandler.new DocumentMonitor(params.getTextDocument().getUri())));
	}

	@Override
	public CompletableFuture<List<InlayHint>> inlayHint(InlayHintParams params) {
		debugTrace(">> textDocument/inlayHint");
		return computeAsync(monitor -> new InlayHintsHandler(preferenceManager).inlayHint(params, monitor));
	}

	@Override
	public CompletableFuture<CheckExtractInterfaceResponse> checkExtractInterfaceStatus(CodeActionParams params) {
		debugTrace(">> java/checkExtractInterfaceStatus");
		return computeAsync((monitor) -> ExtractInterfaceHandler.checkExtractInterfaceStatus(params));
	}

	@Override
	public CompletableFuture<List<TypeHierarchyItem>> prepareTypeHierarchy(TypeHierarchyPrepareParams params) {
		logInfo(">> textDocument/prepareTypeHierarchy");
		return computeAsync(monitor -> typeHierarchyHandler.prepareTypeHierarchy(params, monitor));
	}

	@Override
	public CompletableFuture<List<TypeHierarchyItem>> typeHierarchySupertypes(TypeHierarchySupertypesParams params) {
		logInfo(">> typeHierarchy/supertypes");
		return computeAsync(monitor -> typeHierarchyHandler.getSupertypeItems(params, monitor));
	}

	@Override
	public CompletableFuture<List<TypeHierarchyItem>> typeHierarchySubtypes(TypeHierarchySubtypesParams params) {
		logInfo(">> typeHierarchy/subtypes");
		return computeAsync(monitor -> typeHierarchyHandler.getSubtypeItems(params, monitor));
	}

	@Override
	public NotebookDocumentService getNotebookDocumentService() {
		return null;
	}

	@Override
	public void setTrace(SetTraceParams params) {
		// https://github.com/eclipse-jdtls/eclipse.jdt.ls/issues/2891
		// FIXME: implement the behavior of this method.
	}
	private <R> CompletableFuture<R> computeAsyncWithClientProgress(Function<IProgressMonitor, R> code) {
		return CompletableFutures.computeAsync((cc) -> {
			IProgressMonitor monitor = progressReporterManager.getProgressReporter(cc);
			return code.apply(monitor);
		});
	}

	private void waitForLifecycleJobs(IProgressMonitor monitor) {
		JobHelpers.waitForJobs(DocumentLifeCycleHandler.DOCUMENT_LIFE_CYCLE_JOBS, monitor);
	}

	// for test only
	public boolean isEventHandlerEmpty() {
		return this.workspaceEventHandler.isEmpty();
	}

}
