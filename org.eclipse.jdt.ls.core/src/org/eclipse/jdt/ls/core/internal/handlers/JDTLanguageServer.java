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

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import org.eclipse.jdt.core.WorkingCopyOwner;
import org.eclipse.jdt.ls.core.internal.JavaClientConnection;
import org.eclipse.jdt.ls.core.internal.JavaLanguageServerPlugin;
import org.eclipse.jdt.ls.core.internal.JavaProtocolExtensions;
import org.eclipse.jdt.ls.core.internal.LanguageServerWorkingCopyOwner;
import org.eclipse.jdt.ls.core.internal.ServiceStatus;
import org.eclipse.jdt.ls.core.internal.JavaClientConnection.JavaLanguageClient;
import org.eclipse.jdt.ls.core.internal.managers.ProjectsManager;
import org.eclipse.jdt.ls.core.internal.preferences.PreferenceManager;
import org.eclipse.jdt.ls.core.internal.preferences.Preferences;
import org.eclipse.lsp4j.CodeActionParams;
import org.eclipse.lsp4j.CodeLens;
import org.eclipse.lsp4j.CodeLensParams;
import org.eclipse.lsp4j.Command;
import org.eclipse.lsp4j.CompletionItem;
import org.eclipse.lsp4j.CompletionList;
import org.eclipse.lsp4j.DidChangeConfigurationParams;
import org.eclipse.lsp4j.DidChangeTextDocumentParams;
import org.eclipse.lsp4j.DidChangeWatchedFilesParams;
import org.eclipse.lsp4j.DidCloseTextDocumentParams;
import org.eclipse.lsp4j.DidOpenTextDocumentParams;
import org.eclipse.lsp4j.DidSaveTextDocumentParams;
import org.eclipse.lsp4j.DocumentFormattingParams;
import org.eclipse.lsp4j.DocumentHighlight;
import org.eclipse.lsp4j.DocumentOnTypeFormattingParams;
import org.eclipse.lsp4j.DocumentRangeFormattingParams;
import org.eclipse.lsp4j.DocumentSymbolParams;
import org.eclipse.lsp4j.Hover;
import org.eclipse.lsp4j.InitializeParams;
import org.eclipse.lsp4j.InitializeResult;
import org.eclipse.lsp4j.Location;
import org.eclipse.lsp4j.ReferenceParams;
import org.eclipse.lsp4j.RenameParams;
import org.eclipse.lsp4j.SignatureHelp;
import org.eclipse.lsp4j.SymbolInformation;
import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.eclipse.lsp4j.TextDocumentPositionParams;
import org.eclipse.lsp4j.TextEdit;
import org.eclipse.lsp4j.WorkspaceEdit;
import org.eclipse.lsp4j.WorkspaceSymbolParams;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.eclipse.lsp4j.jsonrpc.services.JsonDelegate;
import org.eclipse.lsp4j.services.LanguageServer;
import org.eclipse.lsp4j.services.TextDocumentService;
import org.eclipse.lsp4j.services.WorkspaceService;

/**
 * @author Gorkem Ercan
 *
 */
public class JDTLanguageServer implements LanguageServer, TextDocumentService, WorkspaceService, JavaProtocolExtensions {

	private JavaClientConnection client;
	private ProjectsManager pm;
	private LanguageServerWorkingCopyOwner workingCopyOwner;
	private PreferenceManager preferenceManager;

	public LanguageServerWorkingCopyOwner getWorkingCopyOwner() {
		return workingCopyOwner;
	}

	public JDTLanguageServer(ProjectsManager projects, PreferenceManager preferenceManager) {
		this.pm = projects;
		this.preferenceManager = preferenceManager;
	}

	public void connectClient(JavaLanguageClient client) {
		this.client = new JavaClientConnection(client);
		this.workingCopyOwner = new LanguageServerWorkingCopyOwner(this.client);
		pm.setConnection(client);
		WorkingCopyOwner.setPrimaryBufferProvider(this.workingCopyOwner);
	}

	/* (non-Javadoc)
	 * @see org.eclipse.lsp4j.services.LanguageServer#initialize(org.eclipse.lsp4j.InitializeParams)
	 */
	@Override
	public CompletableFuture<InitializeResult> initialize(InitializeParams params) {
		logInfo(">> initialize");
		InitHandler handler= new InitHandler(pm, preferenceManager, client);
		return CompletableFuture.completedFuture(handler.initialize(params));
	}

	/* (non-Javadoc)
	 * @see org.eclipse.lsp4j.services.LanguageServer#shutdown()
	 */
	@Override
	public CompletableFuture<Object> shutdown() {
		logInfo(">> shutdown");
		JavaLanguageServerPlugin.getLanguageServer().shutdown();
		return CompletableFuture.completedFuture(new Object());
	}

	/* (non-Javadoc)
	 * @see org.eclipse.lsp4j.services.LanguageServer#exit()
	 */
	@Override
	public void exit() {
		logInfo(">> exit");
		System.exit(0);
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
	public JavaProtocolExtensions getJavaExtensions(){
		return this;
	}

	/* (non-Javadoc)
	 * @see org.eclipse.lsp4j.services.WorkspaceService#symbol(org.eclipse.lsp4j.WorkspaceSymbolParams)
	 */
	@Override
	public CompletableFuture<List<? extends SymbolInformation>> symbol(WorkspaceSymbolParams params) {
		logInfo(">> workspace/symbol");
		WorkspaceSymbolHandler handler = new WorkspaceSymbolHandler();
		return CompletableFuture.supplyAsync(()->{return handler.search(params.getQuery());});
	}

	/* (non-Javadoc)
	 * @see org.eclipse.lsp4j.services.WorkspaceService#didChangeConfiguration(org.eclipse.lsp4j.DidChangeConfigurationParams)
	 */
	@Override
	public void didChangeConfiguration(DidChangeConfigurationParams params) {
		logInfo(">> workspace/didChangeConfiguration");
		Object settings = params.getSettings();
		if (settings instanceof Map) {
			@SuppressWarnings("unchecked")
			Map<String, Object> javaConfig = MapFlattener.flatten((Map<String, Object>)settings);
			Preferences prefs = Preferences.createFrom(javaConfig);
			preferenceManager.update(prefs);
		}
		logInfo(">>New configuration: "+settings);
	}

	/* (non-Javadoc)
	 * @see org.eclipse.lsp4j.services.WorkspaceService#didChangeWatchedFiles(org.eclipse.lsp4j.DidChangeWatchedFilesParams)
	 */
	@Override
	public void didChangeWatchedFiles(DidChangeWatchedFilesParams params) {
		logInfo(">> workspace/didChangeWatchedFiles");
		WorkspaceEventsHandler handler= new WorkspaceEventsHandler(pm, client);
		handler.didChangeWatchedFiles(params);
	}

	/* (non-Javadoc)
	 * @see org.eclipse.lsp4j.services.TextDocumentService#completion(org.eclipse.lsp4j.TextDocumentPositionParams)
	 */
	@Override
	public CompletableFuture<Either<List<CompletionItem>, CompletionList>> completion(TextDocumentPositionParams position) {
		logInfo(">> document/completion");
		CompletionHandler handler = new CompletionHandler();
		return handler.completion(position);
	}

	/* (non-Javadoc)
	 * @see org.eclipse.lsp4j.services.TextDocumentService#resolveCompletionItem(org.eclipse.lsp4j.CompletionItem)
	 */
	@Override
	public CompletableFuture<CompletionItem> resolveCompletionItem(CompletionItem unresolved) {
		logInfo(">> document/resolveCompletionItem");
		CompletionResolveHandler handler = new CompletionResolveHandler();
		return CompletableFuture.supplyAsync(()->handler.resolve(unresolved));
	}

	/* (non-Javadoc)
	 * @see org.eclipse.lsp4j.services.TextDocumentService#hover(org.eclipse.lsp4j.TextDocumentPositionParams)
	 */
	@Override
	public CompletableFuture<Hover> hover(TextDocumentPositionParams position) {
		logInfo(">> document/hover");
		HoverHandler handler = new HoverHandler();
		return handler.hover(position);
	}

	/* (non-Javadoc)
	 * @see org.eclipse.lsp4j.services.TextDocumentService#signatureHelp(org.eclipse.lsp4j.TextDocumentPositionParams)
	 */
	@Override
	public CompletableFuture<SignatureHelp> signatureHelp(TextDocumentPositionParams position) {
		logInfo(">> document/signatureHelp");
		// Not yet implemented
		return null;
	}

	/* (non-Javadoc)
	 * @see org.eclipse.lsp4j.services.TextDocumentService#definition(org.eclipse.lsp4j.TextDocumentPositionParams)
	 */
	@Override
	public CompletableFuture<List<? extends Location>> definition(TextDocumentPositionParams position) {
		logInfo(">> document/definition");
		NavigateToDefinitionHandler handler = new NavigateToDefinitionHandler();
		return handler.definition(position);
	}

	/* (non-Javadoc)
	 * @see org.eclipse.lsp4j.services.TextDocumentService#references(org.eclipse.lsp4j.ReferenceParams)
	 */
	@Override
	public CompletableFuture<List<? extends Location>> references(ReferenceParams params) {
		logInfo(">> document/references");
		ReferencesHandler handler = new ReferencesHandler();
		return CompletableFuture.supplyAsync(()->handler.findReferences(params));
	}

	/* (non-Javadoc)
	 * @see org.eclipse.lsp4j.services.TextDocumentService#documentHighlight(org.eclipse.lsp4j.TextDocumentPositionParams)
	 */
	@Override
	public CompletableFuture<List<? extends DocumentHighlight>> documentHighlight(TextDocumentPositionParams position) {
		logInfo(">> document/documentHighlight");
		DocumentHighlightHandler handler = new DocumentHighlightHandler();
		return handler.documentHighlight(position);
	}

	/* (non-Javadoc)
	 * @see org.eclipse.lsp4j.services.TextDocumentService#documentSymbol(org.eclipse.lsp4j.DocumentSymbolParams)
	 */
	@Override
	public CompletableFuture<List<? extends SymbolInformation>> documentSymbol(DocumentSymbolParams params) {
		logInfo(">> document/documentSymbol");
		DocumentSymbolHandler handler = new DocumentSymbolHandler();
		return handler.documentSymbol(params);
	}

	/* (non-Javadoc)
	 * @see org.eclipse.lsp4j.services.TextDocumentService#codeAction(org.eclipse.lsp4j.CodeActionParams)
	 */
	@Override
	public CompletableFuture<List<? extends Command>> codeAction(CodeActionParams params) {
		logInfo(">> document/codeAction");
		// Not yet implemented
		return null;
	}

	/* (non-Javadoc)
	 * @see org.eclipse.lsp4j.services.TextDocumentService#codeLens(org.eclipse.lsp4j.CodeLensParams)
	 */
	@Override
	public CompletableFuture<List<? extends CodeLens>> codeLens(CodeLensParams params) {
		logInfo(">> document/codeLens");
		CodeLensHandler handler = new CodeLensHandler(preferenceManager);
		return CompletableFuture.supplyAsync(()->handler.getCodeLensSymbols(params.getTextDocument().getUri()));
	}

	/* (non-Javadoc)
	 * @see org.eclipse.lsp4j.services.TextDocumentService#resolveCodeLens(org.eclipse.lsp4j.CodeLens)
	 */
	@Override
	public CompletableFuture<CodeLens> resolveCodeLens(CodeLens unresolved) {
		logInfo(">> codeLens/resolve");
		CodeLensHandler handler = new CodeLensHandler(preferenceManager);
		return CompletableFuture.supplyAsync(()->handler.resolve(unresolved));
	}

	/* (non-Javadoc)
	 * @see org.eclipse.lsp4j.services.TextDocumentService#formatting(org.eclipse.lsp4j.DocumentFormattingParams)
	 */
	@Override
	public CompletableFuture<List<? extends TextEdit>> formatting(DocumentFormattingParams params) {
		logInfo(">> document/formatting");
		FormatterHandler handler = new FormatterHandler();
		return handler.formatting(params);
	}

	/* (non-Javadoc)
	 * @see org.eclipse.lsp4j.services.TextDocumentService#rangeFormatting(org.eclipse.lsp4j.DocumentRangeFormattingParams)
	 */
	@Override
	public CompletableFuture<List<? extends TextEdit>> rangeFormatting(DocumentRangeFormattingParams params) {
		logInfo(">> document/rangeFormatting");
		FormatterHandler handler = new FormatterHandler();
		return handler.rangeFormatting(params);
	}

	/* (non-Javadoc)
	 * @see org.eclipse.lsp4j.services.TextDocumentService#onTypeFormatting(org.eclipse.lsp4j.DocumentOnTypeFormattingParams)
	 */
	@Override
	public CompletableFuture<List<? extends TextEdit>> onTypeFormatting(DocumentOnTypeFormattingParams params) {
		logInfo(">> document/onTypeFormatting");
		// Not yet implemented
		return null;
	}

	/* (non-Javadoc)
	 * @see org.eclipse.lsp4j.services.TextDocumentService#rename(org.eclipse.lsp4j.RenameParams)
	 */
	@Override
	public CompletableFuture<WorkspaceEdit> rename(RenameParams params) {
		logInfo(">> document/rename");
		// Not yet implemented
		return null;
	}

	/* (non-Javadoc)
	 * @see org.eclipse.lsp4j.services.TextDocumentService#didOpen(org.eclipse.lsp4j.DidOpenTextDocumentParams)
	 */
	@Override
	public void didOpen(DidOpenTextDocumentParams params) {
		logInfo(">> document/didOpen");
		DocumentLifeCycleHandler handler = new DocumentLifeCycleHandler(client, preferenceManager);
		handler.didOpen(params);
	}

	/* (non-Javadoc)
	 * @see org.eclipse.lsp4j.services.TextDocumentService#didChange(org.eclipse.lsp4j.DidChangeTextDocumentParams)
	 */
	@Override
	public void didChange(DidChangeTextDocumentParams params) {
		logInfo(">> document/didChange");
		DocumentLifeCycleHandler handler = new DocumentLifeCycleHandler(client, preferenceManager);
		handler.didChange(params);
	}

	/* (non-Javadoc)
	 * @see org.eclipse.lsp4j.services.TextDocumentService#didClose(org.eclipse.lsp4j.DidCloseTextDocumentParams)
	 */
	@Override
	public void didClose(DidCloseTextDocumentParams params) {
		logInfo(">> document/didClose");
		DocumentLifeCycleHandler handler = new DocumentLifeCycleHandler(client, preferenceManager);
		handler.didClose(params);
	}

	/* (non-Javadoc)
	 * @see org.eclipse.lsp4j.services.TextDocumentService#didSave(org.eclipse.lsp4j.DidSaveTextDocumentParams)
	 */
	@Override
	public void didSave(DidSaveTextDocumentParams params) {
		logInfo(">> document/didSave");
		DocumentLifeCycleHandler handler = new DocumentLifeCycleHandler(client, preferenceManager);
		handler.didSave(params);
	}

	/* (non-Javadoc)
	 * @see org.eclipse.jdt.ls.core.internal.JavaProtocolExtensions#ClassFileContents(org.eclipse.lsp4j.TextDocumentIdentifier)
	 */
	@Override
	public CompletableFuture<String> classFileContents(TextDocumentIdentifier param) {
		logInfo(">> java/classFileContents");
		ClassfileContentHandler handler = new ClassfileContentHandler();
		return  handler.contents(param);
	}

	/* (non-Javadoc)
	 * @see org.eclipse.jdt.ls.core.internal.JavaProtocolExtensions#projectConfigurationUpdate(org.eclipse.lsp4j.TextDocumentIdentifier)
	 */
	@Override
	public void projectConfigurationUpdate(TextDocumentIdentifier param) {
		logInfo(">> java/projectConfigurationUpdate");
		ProjectConfigurationUpdateHandler handler = new ProjectConfigurationUpdateHandler(pm);
		handler.updateConfiguration(param);
	}

	public void sendStatus(ServiceStatus serverStatus, String status) {
		if (client != null) {
			client.sendStatus(serverStatus, status);
		}
	}

}
