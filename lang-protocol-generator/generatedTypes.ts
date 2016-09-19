import {
		InitializeRequest, InitializeParams, InitializeResult, InitializeError, ClientCapabilities, ServerCapabilities, TextDocumentSyncKind,
		ShutdownRequest,
		ExitNotification,
		LogMessageNotification, LogMessageParams, MessageType,
		ShowMessageNotification, ShowMessageParams, ShowMessageRequest, ShowMessageRequestParams,
		TelemetryEventNotification,
		DidChangeConfigurationNotification, DidChangeConfigurationParams,
		TextDocumentPositionParams,
		DidOpenTextDocumentNotification, DidOpenTextDocumentParams, DidChangeTextDocumentNotification, DidChangeTextDocumentParams,
		DidCloseTextDocumentNotification, DidCloseTextDocumentParams, DidSaveTextDocumentNotification, DidSaveTextDocumentParams,
		DidChangeWatchedFilesNotification, DidChangeWatchedFilesParams, FileEvent, FileChangeType,
		PublishDiagnosticsNotification, PublishDiagnosticsParams,
		CompletionRequest, CompletionResolveRequest,
		HoverRequest,
		SignatureHelpRequest, DefinitionRequest, ReferencesRequest, DocumentHighlightRequest,
		DocumentSymbolRequest, WorkspaceSymbolRequest, WorkspaceSymbolParams,
		CodeActionRequest, CodeActionParams,
		CodeLensRequest, CodeLensResolveRequest,
		DocumentFormattingRequest, DocumentFormattingParams, DocumentRangeFormattingRequest, DocumentRangeFormattingParams,
		DocumentOnTypeFormattingRequest, DocumentOnTypeFormattingParams,
		RenameRequest, RenameParams,
		DocumentSymbolParams, ReferenceParams, CodeLensParams
} from './protocol';
import {
		Range, Position, Location, Diagnostic, DiagnosticSeverity, Command,
		TextEdit, WorkspaceEdit, WorkspaceChange, TextEditChange,
		TextDocumentIdentifier, CompletionItemKind, CompletionItem, CompletionList,
		Hover, MarkedString,
		SignatureHelp, SignatureInformation, ParameterInformation,
		Definition, CodeActionContext,
		DocumentHighlight, DocumentHighlightKind,
		SymbolInformation, SymbolKind,
		CodeLens,
		FormattingOptions
} from 'vscode-languageserver-types';

export interface AllTypes {
		range:Range;
		position: Position
		location: Location
		diagnostic : Diagnostic
		DiagnosticSeverity: DiagnosticSeverity
		command: Command
		textEdit: TextEdit
		workspaceEdit: WorkspaceEdit
		workspaceChange: WorkspaceChange
		textEditChange: TextEditChange
		textDocumentIdentifier: TextDocumentIdentifier
		completionItemKind: CompletionItemKind
		completionItem: CompletionItem
		completionList: CompletionList
		hover:Hover
		markedString: MarkedString
		signatureHelp : SignatureHelp
		signatureInformation: SignatureInformation
		parameterInformation: ParameterInformation
		definition: Definition
		codeActionContext: CodeActionContext
		documentHighlight : DocumentHighlight
		documentHighlightKind : DocumentHighlightKind
		symbolInformation : SymbolInformation
		symbolKind :SymbolKind
		codeLens : CodeLens,
		formattingOptions : FormattingOptions
}
export interface AllMessages{
		initializeParams: InitializeParams
		initializeResult : InitializeResult
		intializeError: InitializeError
		clientCapabilities: ClientCapabilities
		serverCapabilities: ServerCapabilities
		textDocumentSyncKind :TextDocumentSyncKind
		logMessageParams : LogMessageParams
		messageType : MessageType
		showMessageParams : ShowMessageParams
		showMessageRequestParams : ShowMessageRequestParams
		didChangeConfigurationParams:	DidChangeConfigurationParams
		textDocumentPositionParams: TextDocumentPositionParams 
		didOpenTextDocumentParams : DidOpenTextDocumentParams
		didChangeTextDocumentParams : DidChangeTextDocumentParams
		didCloseTextDocumentParams : DidCloseTextDocumentParams
		didSaveTextDocumentParams : DidSaveTextDocumentParams
		didChangeWatchedFilesParams : DidChangeWatchedFilesParams
		fileEvent : FileEvent
		fileChangeType : FileChangeType
		publishDiagnosticsParams : PublishDiagnosticsParams
		workspaceSymbolParams:WorkspaceSymbolParams
		codeActionParams:CodeActionParams
		documentFormattingParams: DocumentFormattingParams
		documentRangeFormattingParams:DocumentRangeFormattingParams,
		documentOnTypeFormattingParams:DocumentOnTypeFormattingParams,
		renameParams:RenameParams
		documentSymbolParams:DocumentSymbolParams
		referenceParams: ReferenceParams
		codeLensParams : CodeLensParams
}

export interface AllProtocol{
		range:Range;
		position: Position
		location: Location
		diagnostic : Diagnostic
		DiagnosticSeverity: DiagnosticSeverity
		command: Command
		textEdit: TextEdit
		workspaceEdit: WorkspaceEdit
		workspaceChange: WorkspaceChange
		textEditChange: TextEditChange
		textDocumentIdentifier: TextDocumentIdentifier
		completionItemKind: CompletionItemKind
		completionItem: CompletionItem
		completionList: CompletionList
		hover:Hover
		markedString: MarkedString
		signatureHelp : SignatureHelp
		signatureInformation: SignatureInformation
		parameterInformation: ParameterInformation
		definition: Definition
		codeActionContext: CodeActionContext
		documentHighlight : DocumentHighlight
		documentHighlightKind : DocumentHighlightKind
		symbolInformation : SymbolInformation
		symbolKind :SymbolKind
		codeLens : CodeLens,
		formattingOptions : FormattingOptions
		
		//Messages
		initializeParams: InitializeParams
		initializeResult : InitializeResult
		intializeError: InitializeError
		clientCapabilities: ClientCapabilities
		serverCapabilities: ServerCapabilities
		textDocumentSyncKind :TextDocumentSyncKind
		logMessageParams : LogMessageParams
		messageType : MessageType
		showMessageParams : ShowMessageParams
		showMessageRequestParams : ShowMessageRequestParams
		didChangeConfigurationParams:	DidChangeConfigurationParams
		textDocumentPositionParams: TextDocumentPositionParams 
		didOpenTextDocumentParams : DidOpenTextDocumentParams
		didChangeTextDocumentParams : DidChangeTextDocumentParams
		didCloseTextDocumentParams : DidCloseTextDocumentParams
		didSaveTextDocumentParams : DidSaveTextDocumentParams
		didChangeWatchedFilesParams : DidChangeWatchedFilesParams
		fileEvent : FileEvent
		fileChangeType : FileChangeType
		publishDiagnosticsParams : PublishDiagnosticsParams
		workspaceSymbolParams:WorkspaceSymbolParams
		codeActionParams:CodeActionParams
		documentFormattingParams: DocumentFormattingParams
		documentRangeFormattingParams:DocumentRangeFormattingParams,
		documentOnTypeFormattingParams:DocumentOnTypeFormattingParams,
		renameParams:RenameParams
		documentSymbolParams:DocumentSymbolParams
		referenceParams : ReferenceParams
		codeLensParams : CodeLensParams
}