
package org.jboss.tools.langs;

import javax.annotation.Generated;
import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;


/**
 * Defines the capabilities provided by a language
 * 
 * server.
 * 
 */
@Generated("org.jsonschema2pojo")
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
    private CompletionProvider completionProvider;
    /**
     * Signature help options.
     * 
     */
    @SerializedName("signatureHelpProvider")
    @Expose
    private SignatureHelpProvider signatureHelpProvider;
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
    private CodeLensProvider codeLensProvider;
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
    private DocumentOnTypeFormattingProvider documentOnTypeFormattingProvider;
    /**
     * The server provides rename support.
     * 
     */
    @SerializedName("renameProvider")
    @Expose
    private Boolean renameProvider;

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

    /**
     * Completion options.
     * 
     * @return
     *     The completionProvider
     */
    public CompletionProvider getCompletionProvider() {
        return completionProvider;
    }

    /**
     * Completion options.
     * 
     * @param completionProvider
     *     The completionProvider
     */
    public void setCompletionProvider(CompletionProvider completionProvider) {
        this.completionProvider = completionProvider;
    }

    /**
     * Signature help options.
     * 
     * @return
     *     The signatureHelpProvider
     */
    public SignatureHelpProvider getSignatureHelpProvider() {
        return signatureHelpProvider;
    }

    /**
     * Signature help options.
     * 
     * @param signatureHelpProvider
     *     The signatureHelpProvider
     */
    public void setSignatureHelpProvider(SignatureHelpProvider signatureHelpProvider) {
        this.signatureHelpProvider = signatureHelpProvider;
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

    /**
     * Code Lens options.
     * 
     * @return
     *     The codeLensProvider
     */
    public CodeLensProvider getCodeLensProvider() {
        return codeLensProvider;
    }

    /**
     * Code Lens options.
     * 
     * @param codeLensProvider
     *     The codeLensProvider
     */
    public void setCodeLensProvider(CodeLensProvider codeLensProvider) {
        this.codeLensProvider = codeLensProvider;
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

    /**
     * Format document on type options
     * 
     * @return
     *     The documentOnTypeFormattingProvider
     */
    public DocumentOnTypeFormattingProvider getDocumentOnTypeFormattingProvider() {
        return documentOnTypeFormattingProvider;
    }

    /**
     * Format document on type options
     * 
     * @param documentOnTypeFormattingProvider
     *     The documentOnTypeFormattingProvider
     */
    public void setDocumentOnTypeFormattingProvider(DocumentOnTypeFormattingProvider documentOnTypeFormattingProvider) {
        this.documentOnTypeFormattingProvider = documentOnTypeFormattingProvider;
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

}
