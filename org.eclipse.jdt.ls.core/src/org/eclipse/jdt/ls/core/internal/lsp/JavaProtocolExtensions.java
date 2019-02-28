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
package org.eclipse.jdt.ls.core.internal.lsp;

import java.util.concurrent.CompletableFuture;

import org.eclipse.jdt.ls.core.internal.BuildWorkspaceStatus;
import org.eclipse.jdt.ls.core.internal.handlers.HashCodeEqualsHandler.CheckHashCodeEqualsResponse;
import org.eclipse.jdt.ls.core.internal.handlers.HashCodeEqualsHandler.GenerateHashCodeEqualsParams;
import org.eclipse.jdt.ls.core.internal.handlers.OverrideMethodsHandler.AddOverridableMethodParams;
import org.eclipse.jdt.ls.core.internal.handlers.OverrideMethodsHandler.OverridableMethodsResponse;
import org.eclipse.lsp4j.CodeActionParams;
import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.eclipse.lsp4j.WorkspaceEdit;
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
	 * @param documentUri the document from which the project configuration will be updated
	 */
	@JsonNotification
	void projectConfigurationUpdate(TextDocumentIdentifier documentUri);

	@JsonRequest
	CompletableFuture<BuildWorkspaceStatus> buildWorkspace(boolean forceReBuild);

	@JsonRequest
	CompletableFuture<OverridableMethodsResponse> listOverridableMethods(CodeActionParams params);

	@JsonRequest
	CompletableFuture<WorkspaceEdit> addOverridableMethods(AddOverridableMethodParams params);

	@JsonRequest
	CompletableFuture<CheckHashCodeEqualsResponse> checkHashCodeEqualsStatus(CodeActionParams params);

	@JsonRequest
	CompletableFuture<WorkspaceEdit> generateHashCodeEquals(GenerateHashCodeEqualsParams params);
}
