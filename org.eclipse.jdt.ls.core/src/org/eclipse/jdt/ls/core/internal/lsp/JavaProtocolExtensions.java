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
package org.eclipse.jdt.ls.core.internal.lsp;

import java.util.List;
import java.util.concurrent.CompletableFuture;

import org.eclipse.jdt.ls.core.internal.BuildWorkspaceStatus;
import org.eclipse.jdt.ls.core.internal.codemanipulation.GenerateGetterSetterOperation.AccessorField;
import org.eclipse.jdt.ls.core.internal.handlers.ExtractInterfaceHandler.CheckExtractInterfaceResponse;
import org.eclipse.jdt.ls.core.internal.handlers.FindLinksHandler.FindLinksParams;
import org.eclipse.jdt.ls.core.internal.handlers.GenerateAccessorsHandler.GenerateAccessorsParams;
import org.eclipse.jdt.ls.core.internal.handlers.GenerateAccessorsHandler.AccessorCodeActionParams;
import org.eclipse.jdt.ls.core.internal.handlers.GenerateConstructorsHandler.CheckConstructorsResponse;
import org.eclipse.jdt.ls.core.internal.handlers.GenerateConstructorsHandler.GenerateConstructorsParams;
import org.eclipse.jdt.ls.core.internal.handlers.GenerateDelegateMethodsHandler.CheckDelegateMethodsResponse;
import org.eclipse.jdt.ls.core.internal.handlers.GenerateDelegateMethodsHandler.GenerateDelegateMethodsParams;
import org.eclipse.jdt.ls.core.internal.handlers.GenerateToStringHandler.CheckToStringResponse;
import org.eclipse.jdt.ls.core.internal.handlers.GenerateToStringHandler.GenerateToStringParams;
import org.eclipse.jdt.ls.core.internal.handlers.InferSelectionHandler.InferSelectionParams;
import org.eclipse.jdt.ls.core.internal.handlers.InferSelectionHandler.SelectionInfo;
import org.eclipse.jdt.ls.core.internal.handlers.GetRefactorEditHandler.GetRefactorEditParams;
import org.eclipse.jdt.ls.core.internal.handlers.GetRefactorEditHandler.RefactorWorkspaceEdit;
import org.eclipse.jdt.ls.core.internal.handlers.HashCodeEqualsHandler.CheckHashCodeEqualsResponse;
import org.eclipse.jdt.ls.core.internal.handlers.HashCodeEqualsHandler.GenerateHashCodeEqualsParams;
import org.eclipse.jdt.ls.core.internal.handlers.MoveHandler.MoveDestinationsResponse;
import org.eclipse.jdt.ls.core.internal.handlers.MoveHandler.MoveParams;
import org.eclipse.jdt.ls.core.internal.handlers.OverrideMethodsHandler.AddOverridableMethodParams;
import org.eclipse.jdt.ls.core.internal.handlers.OverrideMethodsHandler.OverridableMethodsResponse;
import org.eclipse.jdt.ls.core.internal.handlers.WorkspaceSymbolHandler.SearchSymbolParams;
import org.eclipse.lsp4j.CodeActionParams;
import org.eclipse.lsp4j.Location;
import org.eclipse.lsp4j.SymbolInformation;
import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.eclipse.lsp4j.WorkspaceEdit;
import org.eclipse.lsp4j.extended.ProjectConfigurationsUpdateParam;
import org.eclipse.lsp4j.extended.ProjectBuildParams;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.eclipse.lsp4j.jsonrpc.services.JsonNotification;
import org.eclipse.lsp4j.jsonrpc.services.JsonRequest;
import org.eclipse.lsp4j.jsonrpc.services.JsonSegment;

/**
 * Interface for protocol extensions for Java
 *
 * @author Gorkem Ercan
 *
 */
@JsonSegment("java")
public interface JavaProtocolExtensions {

	@JsonRequest
	CompletableFuture<String> classFileContents(TextDocumentIdentifier documentUri);

	/**
	 * Request a project configuration update
	 *
	 * @deprecated Please use {@link #projectConfigurationsUpdate(TextDocumentIdentifier)}.
	 * @param documentUri the document from which the project configuration will be updated
	 */
	@JsonNotification
	void projectConfigurationUpdate(TextDocumentIdentifier documentUri);

	/**
	 * Request multiple project configurations update
	 * @param documentUris the documents from which the project configuration will be updated
	 */
	@JsonNotification
	void projectConfigurationsUpdate(ProjectConfigurationsUpdateParam params);

	@JsonRequest
	CompletableFuture<BuildWorkspaceStatus> buildWorkspace(Either<Boolean, boolean[]> forceReBuild);

	@JsonRequest
	CompletableFuture<BuildWorkspaceStatus> buildProjects(ProjectBuildParams params);

	@JsonRequest
	CompletableFuture<OverridableMethodsResponse> listOverridableMethods(CodeActionParams params);

	@JsonRequest
	CompletableFuture<WorkspaceEdit> addOverridableMethods(AddOverridableMethodParams params);

	@JsonRequest
	CompletableFuture<CheckHashCodeEqualsResponse> checkHashCodeEqualsStatus(CodeActionParams params);

	@JsonRequest
	CompletableFuture<WorkspaceEdit> generateHashCodeEquals(GenerateHashCodeEqualsParams params);

	@JsonRequest
	CompletableFuture<WorkspaceEdit> organizeImports(CodeActionParams params);

	@JsonRequest
	CompletableFuture<CheckToStringResponse> checkToStringStatus(CodeActionParams params);

	@JsonRequest
	CompletableFuture<WorkspaceEdit> generateToString(GenerateToStringParams params);

	@JsonRequest
	CompletableFuture<AccessorField[]> resolveUnimplementedAccessors(AccessorCodeActionParams params);

	@JsonRequest
	CompletableFuture<WorkspaceEdit> generateAccessors(GenerateAccessorsParams params);

	@JsonRequest
	CompletableFuture<CheckConstructorsResponse> checkConstructorsStatus(CodeActionParams params);

	@JsonRequest
	CompletableFuture<WorkspaceEdit> generateConstructors(GenerateConstructorsParams params);

	@JsonRequest
	CompletableFuture<CheckDelegateMethodsResponse> checkDelegateMethodsStatus(CodeActionParams params);

	@JsonRequest
	CompletableFuture<WorkspaceEdit> generateDelegateMethods(GenerateDelegateMethodsParams params);

	@JsonRequest
	CompletableFuture<RefactorWorkspaceEdit> getRefactorEdit(GetRefactorEditParams params);

	@JsonRequest
	CompletableFuture<List<SelectionInfo>> inferSelection(InferSelectionParams params);

	@JsonRequest
	CompletableFuture<MoveDestinationsResponse> getMoveDestinations(MoveParams params);

	@JsonRequest
	CompletableFuture<RefactorWorkspaceEdit> move(MoveParams params);

	@JsonRequest
	CompletableFuture<List<SymbolInformation>> searchSymbols(SearchSymbolParams params);

	@JsonRequest
	CompletableFuture<List<? extends Location>> findLinks(FindLinksParams params);

	@JsonRequest
	CompletableFuture<CheckExtractInterfaceResponse> checkExtractInterfaceStatus(CodeActionParams params);

	@JsonNotification
	void validateDocument(ValidateDocumentParams params);
}
