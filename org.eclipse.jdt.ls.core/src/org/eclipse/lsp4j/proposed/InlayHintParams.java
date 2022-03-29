/*******************************************************************************
 * Copyright (c) 2022 Red Hat Inc. and others.
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

package org.eclipse.lsp4j.proposed;

import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.eclipse.lsp4j.WorkDoneProgressParams;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.eclipse.lsp4j.jsonrpc.validation.NonNull;
import org.eclipse.lsp4j.util.Preconditions;
import org.eclipse.xtext.xbase.lib.Pure;

public class InlayHintParams implements WorkDoneProgressParams {

	/**
	 * An optional token that a server can use to report work done progress.
	 */
	private Either<String, Integer> workDoneToken;

	/**
	 * The document to format.
	 */
	@NonNull
	private TextDocumentIdentifier textDocument;

	/**
	 * The visible document range for which inlay hints should be computed.
	 */
	@NonNull
	private Range range;

	/**
	 * An optional token that a server can use to report work done progress.
	 */
	@Pure
	@Override
	public Either<String, Integer> getWorkDoneToken() {
		return this.workDoneToken;
	}

	/**
	 * An optional token that a server can use to report work done progress.
	 */
	public void setWorkDoneToken(final Either<String, Integer> workDoneToken) {
		this.workDoneToken = workDoneToken;
	}

	public void setWorkDoneToken(final String workDoneToken) {
		if (workDoneToken == null) {
			this.workDoneToken = null;
			return;
		}
		this.workDoneToken = Either.forLeft(workDoneToken);
	}

	/**
	 * The document to format.
	 */
	@Pure
	@NonNull
	public TextDocumentIdentifier getTextDocument() {
		return this.textDocument;
	}

	/**
	 * The document to format.
	 */
	public void setTextDocument(@NonNull final TextDocumentIdentifier textDocument) {
		this.textDocument = Preconditions.checkNotNull(textDocument, "textDocument");
	}

	public void setWorkDoneToken(final Integer workDoneToken) {
		if (workDoneToken == null) {
			this.workDoneToken = null;
			return;
		}
		this.workDoneToken = Either.forRight(workDoneToken);
	}

	/**
	 * Returns the visible document range for which inlay hints should be computed.
	 * 
	 * @return the visible document range for which inlay hints should be computed.
	 */
	@Pure
	@NonNull
	public Range getRange() {
		return range;
	}

	/**
	 * Set the visible document range for which inlay hints should be computed.
	 * 
	 * @param range
	 */
	public void setRange(@NonNull Range range) {
		this.range = Preconditions.checkNotNull(range, "range");
	}
}
