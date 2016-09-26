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
package org.jboss.tools.langs;

import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;


/**
 * Defines the capabilities provided by a language
 *
 * server.
 *
 */
public class ServerCapabilities {

	/**
	 * Defines how text documents are synced.
	 *
	 */
	@SerializedName("textDocumentSync")
	@Expose
	private Double textDocumentSync;
	/**
	 * The server provides hover support.
	 *
	 */
	@SerializedName("hoverProvider")
	@Expose
	private Boolean hoverProvider;
	/**
	 * Completion options.
	 *
	 */
	@SerializedName("completionProvider")
	@Expose
	private CompletionOptions completionProvider;
	/**
	 * Signature help options.
	 *
	 */
	@SerializedName("signatureHelpProvider")
	@Expose
	private SignatureHelpOptions signatureHelpProvider;
	/**
	 * The server provides goto definition support.
	 *
	 */
	@SerializedName("definitionProvider")
	@Expose
	private Boolean definitionProvider;
	/**
	 * The server provides find references support.
	 *
	 */
	@SerializedName("referencesProvider")
	@Expose
	private Boolean referencesProvider;
	/**
	 * The server provides document highlight support.
	 *
	 */
	@SerializedName("documentHighlightProvider")
	@Expose
	private Boolean documentHighlightProvider;
	/**
	 * The server provides document symbol support.
	 *
	 */
	@SerializedName("documentSymbolProvider")
	@Expose
	private Boolean documentSymbolProvider;
	/**
	 * The server provides workspace symbol support.
	 *
	 */
	@SerializedName("workspaceSymbolProvider")
	@Expose
	private Boolean workspaceSymbolProvider;
	/**
	 * The server provides code actions.
	 *
	 */
	@SerializedName("codeActionProvider")
	@Expose
	private Boolean codeActionProvider;
	/**
	 * Code Lens options.
	 *
	 */
	@SerializedName("codeLensProvider")
	@Expose
	private CodeLensOptions codeLensProvider;
	/**
	 * The server provides document formatting.
	 *
	 */
	@SerializedName("documentFormattingProvider")
	@Expose
	private Boolean documentFormattingProvider;
	/**
	 * The server provides document range formatting.
	 *
	 */
	@SerializedName("documentRangeFormattingProvider")
	@Expose
	private Boolean documentRangeFormattingProvider;
	/**
	 * Format document on type options
	 *
	 */
	@SerializedName("documentOnTypeFormattingProvider")
	@Expose
	private DocumentOnTypeFormattingOptions documentOnTypeFormattingProvider;
	/**
	 * The server provides rename support.
	 *
	 */
	@SerializedName("renameProvider")
	@Expose
	private Boolean renameProvider;
	/**
	 * Document link options
	 *
	 */
	@SerializedName("documentLinkProvider")
	@Expose
	private DocumentLinkOptions documentLinkProvider;

	/**
	 * Defines how text documents are synced.
	 *
	 * @return
	 *     The textDocumentSync
	 */
	public Double getTextDocumentSync() {
		return textDocumentSync;
	}

	/**
	 * Defines how text documents are synced.
	 *
	 * @param textDocumentSync
	 *     The textDocumentSync
	 */
	public void setTextDocumentSync(Double textDocumentSync) {
		this.textDocumentSync = textDocumentSync;
	}

	public ServerCapabilities withTextDocumentSync(Double textDocumentSync) {
		this.textDocumentSync = textDocumentSync;
		return this;
	}

	/**
	 * The server provides hover support.
	 *
	 * @return
	 *     The hoverProvider
	 */
	public Boolean getHoverProvider() {
		return hoverProvider;
	}

	/**
	 * The server provides hover support.
	 *
	 * @param hoverProvider
	 *     The hoverProvider
	 */
	public void setHoverProvider(Boolean hoverProvider) {
		this.hoverProvider = hoverProvider;
	}

	public ServerCapabilities withHoverProvider(Boolean hoverProvider) {
		this.hoverProvider = hoverProvider;
		return this;
	}

	/**
	 * Completion options.
	 *
	 * @return
	 *     The completionProvider
	 */
	public CompletionOptions getCompletionProvider() {
		return completionProvider;
	}

	/**
	 * Completion options.
	 *
	 * @param completionProvider
	 *     The completionProvider
	 */
	public void setCompletionProvider(CompletionOptions completionProvider) {
		this.completionProvider = completionProvider;
	}

	public ServerCapabilities withCompletionProvider(CompletionOptions completionProvider) {
		this.completionProvider = completionProvider;
		return this;
	}

	/**
	 * Signature help options.
	 *
	 * @return
	 *     The signatureHelpProvider
	 */
	public SignatureHelpOptions getSignatureHelpProvider() {
		return signatureHelpProvider;
	}

	/**
	 * Signature help options.
	 *
	 * @param signatureHelpProvider
	 *     The signatureHelpProvider
	 */
	public void setSignatureHelpProvider(SignatureHelpOptions signatureHelpProvider) {
		this.signatureHelpProvider = signatureHelpProvider;
	}

	public ServerCapabilities withSignatureHelpProvider(SignatureHelpOptions signatureHelpProvider) {
		this.signatureHelpProvider = signatureHelpProvider;
		return this;
	}

	/**
	 * The server provides goto definition support.
	 *
	 * @return
	 *     The definitionProvider
	 */
	public Boolean getDefinitionProvider() {
		return definitionProvider;
	}

	/**
	 * The server provides goto definition support.
	 *
	 * @param definitionProvider
	 *     The definitionProvider
	 */
	public void setDefinitionProvider(Boolean definitionProvider) {
		this.definitionProvider = definitionProvider;
	}

	public ServerCapabilities withDefinitionProvider(Boolean definitionProvider) {
		this.definitionProvider = definitionProvider;
		return this;
	}

	/**
	 * The server provides find references support.
	 *
	 * @return
	 *     The referencesProvider
	 */
	public Boolean getReferencesProvider() {
		return referencesProvider;
	}

	/**
	 * The server provides find references support.
	 *
	 * @param referencesProvider
	 *     The referencesProvider
	 */
	public void setReferencesProvider(Boolean referencesProvider) {
		this.referencesProvider = referencesProvider;
	}

	public ServerCapabilities withReferencesProvider(Boolean referencesProvider) {
		this.referencesProvider = referencesProvider;
		return this;
	}

	/**
	 * The server provides document highlight support.
	 *
	 * @return
	 *     The documentHighlightProvider
	 */
	public Boolean getDocumentHighlightProvider() {
		return documentHighlightProvider;
	}

	/**
	 * The server provides document highlight support.
	 *
	 * @param documentHighlightProvider
	 *     The documentHighlightProvider
	 */
	public void setDocumentHighlightProvider(Boolean documentHighlightProvider) {
		this.documentHighlightProvider = documentHighlightProvider;
	}

	public ServerCapabilities withDocumentHighlightProvider(Boolean documentHighlightProvider) {
		this.documentHighlightProvider = documentHighlightProvider;
		return this;
	}

	/**
	 * The server provides document symbol support.
	 *
	 * @return
	 *     The documentSymbolProvider
	 */
	public Boolean getDocumentSymbolProvider() {
		return documentSymbolProvider;
	}

	/**
	 * The server provides document symbol support.
	 *
	 * @param documentSymbolProvider
	 *     The documentSymbolProvider
	 */
	public void setDocumentSymbolProvider(Boolean documentSymbolProvider) {
		this.documentSymbolProvider = documentSymbolProvider;
	}

	public ServerCapabilities withDocumentSymbolProvider(Boolean documentSymbolProvider) {
		this.documentSymbolProvider = documentSymbolProvider;
		return this;
	}

	/**
	 * The server provides workspace symbol support.
	 *
	 * @return
	 *     The workspaceSymbolProvider
	 */
	public Boolean getWorkspaceSymbolProvider() {
		return workspaceSymbolProvider;
	}

	/**
	 * The server provides workspace symbol support.
	 *
	 * @param workspaceSymbolProvider
	 *     The workspaceSymbolProvider
	 */
	public void setWorkspaceSymbolProvider(Boolean workspaceSymbolProvider) {
		this.workspaceSymbolProvider = workspaceSymbolProvider;
	}

	public ServerCapabilities withWorkspaceSymbolProvider(Boolean workspaceSymbolProvider) {
		this.workspaceSymbolProvider = workspaceSymbolProvider;
		return this;
	}

	/**
	 * The server provides code actions.
	 *
	 * @return
	 *     The codeActionProvider
	 */
	public Boolean getCodeActionProvider() {
		return codeActionProvider;
	}

	/**
	 * The server provides code actions.
	 *
	 * @param codeActionProvider
	 *     The codeActionProvider
	 */
	public void setCodeActionProvider(Boolean codeActionProvider) {
		this.codeActionProvider = codeActionProvider;
	}

	public ServerCapabilities withCodeActionProvider(Boolean codeActionProvider) {
		this.codeActionProvider = codeActionProvider;
		return this;
	}

	/**
	 * Code Lens options.
	 *
	 * @return
	 *     The codeLensProvider
	 */
	public CodeLensOptions getCodeLensProvider() {
		return codeLensProvider;
	}

	/**
	 * Code Lens options.
	 *
	 * @param codeLensProvider
	 *     The codeLensProvider
	 */
	public void setCodeLensProvider(CodeLensOptions codeLensProvider) {
		this.codeLensProvider = codeLensProvider;
	}

	public ServerCapabilities withCodeLensProvider(CodeLensOptions codeLensProvider) {
		this.codeLensProvider = codeLensProvider;
		return this;
	}

	/**
	 * The server provides document formatting.
	 *
	 * @return
	 *     The documentFormattingProvider
	 */
	public Boolean getDocumentFormattingProvider() {
		return documentFormattingProvider;
	}

	/**
	 * The server provides document formatting.
	 *
	 * @param documentFormattingProvider
	 *     The documentFormattingProvider
	 */
	public void setDocumentFormattingProvider(Boolean documentFormattingProvider) {
		this.documentFormattingProvider = documentFormattingProvider;
	}

	public ServerCapabilities withDocumentFormattingProvider(Boolean documentFormattingProvider) {
		this.documentFormattingProvider = documentFormattingProvider;
		return this;
	}

	/**
	 * The server provides document range formatting.
	 *
	 * @return
	 *     The documentRangeFormattingProvider
	 */
	public Boolean getDocumentRangeFormattingProvider() {
		return documentRangeFormattingProvider;
	}

	/**
	 * The server provides document range formatting.
	 *
	 * @param documentRangeFormattingProvider
	 *     The documentRangeFormattingProvider
	 */
	public void setDocumentRangeFormattingProvider(Boolean documentRangeFormattingProvider) {
		this.documentRangeFormattingProvider = documentRangeFormattingProvider;
	}

	public ServerCapabilities withDocumentRangeFormattingProvider(Boolean documentRangeFormattingProvider) {
		this.documentRangeFormattingProvider = documentRangeFormattingProvider;
		return this;
	}

	/**
	 * Format document on type options
	 *
	 * @return
	 *     The documentOnTypeFormattingProvider
	 */
	public DocumentOnTypeFormattingOptions getDocumentOnTypeFormattingProvider() {
		return documentOnTypeFormattingProvider;
	}

	/**
	 * Format document on type options
	 *
	 * @param documentOnTypeFormattingProvider
	 *     The documentOnTypeFormattingProvider
	 */
	public void setDocumentOnTypeFormattingProvider(DocumentOnTypeFormattingOptions documentOnTypeFormattingProvider) {
		this.documentOnTypeFormattingProvider = documentOnTypeFormattingProvider;
	}

	public ServerCapabilities withDocumentOnTypeFormattingProvider(DocumentOnTypeFormattingOptions documentOnTypeFormattingProvider) {
		this.documentOnTypeFormattingProvider = documentOnTypeFormattingProvider;
		return this;
	}

	/**
	 * The server provides rename support.
	 *
	 * @return
	 *     The renameProvider
	 */
	public Boolean getRenameProvider() {
		return renameProvider;
	}

	/**
	 * The server provides rename support.
	 *
	 * @param renameProvider
	 *     The renameProvider
	 */
	public void setRenameProvider(Boolean renameProvider) {
		this.renameProvider = renameProvider;
	}

	public ServerCapabilities withRenameProvider(Boolean renameProvider) {
		this.renameProvider = renameProvider;
		return this;
	}

	/**
	 * Document link options
	 *
	 * @return
	 *     The documentLinkProvider
	 */
	public DocumentLinkOptions getDocumentLinkProvider() {
		return documentLinkProvider;
	}

	/**
	 * Document link options
	 *
	 * @param documentLinkProvider
	 *     The documentLinkProvider
	 */
	public void setDocumentLinkProvider(DocumentLinkOptions documentLinkProvider) {
		this.documentLinkProvider = documentLinkProvider;
	}

	public ServerCapabilities withDocumentLinkProvider(DocumentLinkOptions documentLinkProvider) {
		this.documentLinkProvider = documentLinkProvider;
		return this;
	}

}
