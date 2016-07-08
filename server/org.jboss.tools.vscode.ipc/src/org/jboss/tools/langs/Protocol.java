
package org.jboss.tools.langs;

import javax.annotation.Generated;
import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;

@Generated("org.jsonschema2pojo")
public class Protocol {

    /**
     * Position in a text document expressed as zero-based line and character offset.
     * 
     */
    @SerializedName("position")
    @Expose
    private Position position;
    /**
     * A range in a text document expressed as (zero-based) start and end positions.
     * 
     */
    @SerializedName("range")
    @Expose
    private Range range;
    /**
     * Represents a location inside a resource, such as a line inside a text file.
     * 
     */
    @SerializedName("location")
    @Expose
    private Location location;
    /**
     * Represents a diagnostic, such as a compiler error or warning. Diagnostic objects are only valid in the scope of a resource.
     * 
     */
    @SerializedName("diagnostic")
    @Expose
    private Diagnostic diagnostic;
    /**
     * Represents a reference to a command. Provides a title which will be used to represent a command in the UI and, optionally, an array of arguments which will be passed to the command handler function when invoked.
     * 
     */
    @SerializedName("command")
    @Expose
    private Command command;
    /**
     * A textual edit applicable to a text document.
     * 
     */
    @SerializedName("textEdit")
    @Expose
    private TextEdit textEdit;
    /**
     * A workspace edit represents changes to many resources managed in the workspace.
     * 
     */
    @SerializedName("workspaceEdit")
    @Expose
    private WorkspaceEdit workspaceEdit;
    /**
     * Text documents are identified using an URI. On the protocol level URI's are passed as strings
     * 
     */
    @SerializedName("textDocumentIdentifier")
    @Expose
    private TextDocumentIdentifier textDocumentIdentifier;
    /**
     * An item to transfer a text document from the client to the server.
     * 
     */
    @SerializedName("textDocumentItem")
    @Expose
    private TextDocumentItem textDocumentItem;
    /**
     * An identifier to denote a specific version of a text document.
     * 
     */
    @SerializedName("versionedTextDocumentIdentifier")
    @Expose
    private VersionedTextDocumentIdentifier versionedTextDocumentIdentifier;
    /**
     * A parameter literal used in requests to pass a text document and a position inside that document.
     * 
     */
    @SerializedName("textDocumentPositionParams")
    @Expose
    private TextDocumentPositionParams textDocumentPositionParams;
    /**
     * Cancellation Support
     * 
     */
    @SerializedName("cancelParams")
    @Expose
    private CancelParams cancelParams;
    /**
     * Defines the capabilities provided by the client.
     * 
     */
    @SerializedName("clientCapabilities")
    @Expose
    private ClientCapabilities clientCapabilities;
    @SerializedName("initializeParams")
    @Expose
    private InitializeParams initializeParams;
    /**
     * The error returned if the initilize request fails.
     * 
     */
    @SerializedName("initializeError")
    @Expose
    private InitializeError initializeError;
    /**
     * Defines the capabilities provided by a language
     * 
     * server.
     * 
     */
    @SerializedName("serverCapabilities")
    @Expose
    private ServerCapabilities serverCapabilities;
    /**
     * The result returned from an initilize request.
     * 
     */
    @SerializedName("initializeResult")
    @Expose
    private InitializeResult initializeResult;

    /**
     * Position in a text document expressed as zero-based line and character offset.
     * 
     * @return
     *     The position
     */
    public Position getPosition() {
        return position;
    }

    /**
     * Position in a text document expressed as zero-based line and character offset.
     * 
     * @param position
     *     The position
     */
    public void setPosition(Position position) {
        this.position = position;
    }

    /**
     * A range in a text document expressed as (zero-based) start and end positions.
     * 
     * @return
     *     The range
     */
    public Range getRange() {
        return range;
    }

    /**
     * A range in a text document expressed as (zero-based) start and end positions.
     * 
     * @param range
     *     The range
     */
    public void setRange(Range range) {
        this.range = range;
    }

    /**
     * Represents a location inside a resource, such as a line inside a text file.
     * 
     * @return
     *     The location
     */
    public Location getLocation() {
        return location;
    }

    /**
     * Represents a location inside a resource, such as a line inside a text file.
     * 
     * @param location
     *     The location
     */
    public void setLocation(Location location) {
        this.location = location;
    }

    /**
     * Represents a diagnostic, such as a compiler error or warning. Diagnostic objects are only valid in the scope of a resource.
     * 
     * @return
     *     The diagnostic
     */
    public Diagnostic getDiagnostic() {
        return diagnostic;
    }

    /**
     * Represents a diagnostic, such as a compiler error or warning. Diagnostic objects are only valid in the scope of a resource.
     * 
     * @param diagnostic
     *     The diagnostic
     */
    public void setDiagnostic(Diagnostic diagnostic) {
        this.diagnostic = diagnostic;
    }

    /**
     * Represents a reference to a command. Provides a title which will be used to represent a command in the UI and, optionally, an array of arguments which will be passed to the command handler function when invoked.
     * 
     * @return
     *     The command
     */
    public Command getCommand() {
        return command;
    }

    /**
     * Represents a reference to a command. Provides a title which will be used to represent a command in the UI and, optionally, an array of arguments which will be passed to the command handler function when invoked.
     * 
     * @param command
     *     The command
     */
    public void setCommand(Command command) {
        this.command = command;
    }

    /**
     * A textual edit applicable to a text document.
     * 
     * @return
     *     The textEdit
     */
    public TextEdit getTextEdit() {
        return textEdit;
    }

    /**
     * A textual edit applicable to a text document.
     * 
     * @param textEdit
     *     The textEdit
     */
    public void setTextEdit(TextEdit textEdit) {
        this.textEdit = textEdit;
    }

    /**
     * A workspace edit represents changes to many resources managed in the workspace.
     * 
     * @return
     *     The workspaceEdit
     */
    public WorkspaceEdit getWorkspaceEdit() {
        return workspaceEdit;
    }

    /**
     * A workspace edit represents changes to many resources managed in the workspace.
     * 
     * @param workspaceEdit
     *     The workspaceEdit
     */
    public void setWorkspaceEdit(WorkspaceEdit workspaceEdit) {
        this.workspaceEdit = workspaceEdit;
    }

    /**
     * Text documents are identified using an URI. On the protocol level URI's are passed as strings
     * 
     * @return
     *     The textDocumentIdentifier
     */
    public TextDocumentIdentifier getTextDocumentIdentifier() {
        return textDocumentIdentifier;
    }

    /**
     * Text documents are identified using an URI. On the protocol level URI's are passed as strings
     * 
     * @param textDocumentIdentifier
     *     The textDocumentIdentifier
     */
    public void setTextDocumentIdentifier(TextDocumentIdentifier textDocumentIdentifier) {
        this.textDocumentIdentifier = textDocumentIdentifier;
    }

    /**
     * An item to transfer a text document from the client to the server.
     * 
     * @return
     *     The textDocumentItem
     */
    public TextDocumentItem getTextDocumentItem() {
        return textDocumentItem;
    }

    /**
     * An item to transfer a text document from the client to the server.
     * 
     * @param textDocumentItem
     *     The textDocumentItem
     */
    public void setTextDocumentItem(TextDocumentItem textDocumentItem) {
        this.textDocumentItem = textDocumentItem;
    }

    /**
     * An identifier to denote a specific version of a text document.
     * 
     * @return
     *     The versionedTextDocumentIdentifier
     */
    public VersionedTextDocumentIdentifier getVersionedTextDocumentIdentifier() {
        return versionedTextDocumentIdentifier;
    }

    /**
     * An identifier to denote a specific version of a text document.
     * 
     * @param versionedTextDocumentIdentifier
     *     The versionedTextDocumentIdentifier
     */
    public void setVersionedTextDocumentIdentifier(VersionedTextDocumentIdentifier versionedTextDocumentIdentifier) {
        this.versionedTextDocumentIdentifier = versionedTextDocumentIdentifier;
    }

    /**
     * A parameter literal used in requests to pass a text document and a position inside that document.
     * 
     * @return
     *     The textDocumentPositionParams
     */
    public TextDocumentPositionParams getTextDocumentPositionParams() {
        return textDocumentPositionParams;
    }

    /**
     * A parameter literal used in requests to pass a text document and a position inside that document.
     * 
     * @param textDocumentPositionParams
     *     The textDocumentPositionParams
     */
    public void setTextDocumentPositionParams(TextDocumentPositionParams textDocumentPositionParams) {
        this.textDocumentPositionParams = textDocumentPositionParams;
    }

    /**
     * Cancellation Support
     * 
     * @return
     *     The cancelParams
     */
    public CancelParams getCancelParams() {
        return cancelParams;
    }

    /**
     * Cancellation Support
     * 
     * @param cancelParams
     *     The cancelParams
     */
    public void setCancelParams(CancelParams cancelParams) {
        this.cancelParams = cancelParams;
    }

    /**
     * Defines the capabilities provided by the client.
     * 
     * @return
     *     The clientCapabilities
     */
    public ClientCapabilities getClientCapabilities() {
        return clientCapabilities;
    }

    /**
     * Defines the capabilities provided by the client.
     * 
     * @param clientCapabilities
     *     The clientCapabilities
     */
    public void setClientCapabilities(ClientCapabilities clientCapabilities) {
        this.clientCapabilities = clientCapabilities;
    }

    /**
     * 
     * @return
     *     The initializeParams
     */
    public InitializeParams getInitializeParams() {
        return initializeParams;
    }

    /**
     * 
     * @param initializeParams
     *     The initializeParams
     */
    public void setInitializeParams(InitializeParams initializeParams) {
        this.initializeParams = initializeParams;
    }

    /**
     * The error returned if the initilize request fails.
     * 
     * @return
     *     The initializeError
     */
    public InitializeError getInitializeError() {
        return initializeError;
    }

    /**
     * The error returned if the initilize request fails.
     * 
     * @param initializeError
     *     The initializeError
     */
    public void setInitializeError(InitializeError initializeError) {
        this.initializeError = initializeError;
    }

    /**
     * Defines the capabilities provided by a language
     * 
     * server.
     * 
     * @return
     *     The serverCapabilities
     */
    public ServerCapabilities getServerCapabilities() {
        return serverCapabilities;
    }

    /**
     * Defines the capabilities provided by a language
     * 
     * server.
     * 
     * @param serverCapabilities
     *     The serverCapabilities
     */
    public void setServerCapabilities(ServerCapabilities serverCapabilities) {
        this.serverCapabilities = serverCapabilities;
    }

    /**
     * The result returned from an initilize request.
     * 
     * @return
     *     The initializeResult
     */
    public InitializeResult getInitializeResult() {
        return initializeResult;
    }

    /**
     * The result returned from an initilize request.
     * 
     * @param initializeResult
     *     The initializeResult
     */
    public void setInitializeResult(InitializeResult initializeResult) {
        this.initializeResult = initializeResult;
    }

}
