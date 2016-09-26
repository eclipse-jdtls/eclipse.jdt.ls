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

public class TextDocumentPositionParams {

	/**
	 *
	 * (Required)
	 *
	 */
	@SerializedName("textDocument")
	@Expose
	private TextDocumentIdentifier textDocument;
	/**
	 * Position in a text document expressed as zero-based line and character offset.
	 *
	 * The Position namespace provides helper functions to work with
	 *
	 * [Position](#Position) literals.
	 * (Required)
	 *
	 */
	@SerializedName("position")
	@Expose
	private Position position;

	/**
	 *
	 * (Required)
	 *
	 * @return
	 *     The textDocument
	 */
	public TextDocumentIdentifier getTextDocument() {
		return textDocument;
	}

	/**
	 *
	 * (Required)
	 *
	 * @param textDocument
	 *     The textDocument
	 */
	public void setTextDocument(TextDocumentIdentifier textDocument) {
		this.textDocument = textDocument;
	}

	public TextDocumentPositionParams withTextDocument(TextDocumentIdentifier textDocument) {
		this.textDocument = textDocument;
		return this;
	}

	/**
	 * Position in a text document expressed as zero-based line and character offset.
	 *
	 * The Position namespace provides helper functions to work with
	 *
	 * [Position](#Position) literals.
	 * (Required)
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
	 * The Position namespace provides helper functions to work with
	 *
	 * [Position](#Position) literals.
	 * (Required)
	 *
	 * @param position
	 *     The position
	 */
	public void setPosition(Position position) {
		this.position = position;
	}

	public TextDocumentPositionParams withPosition(Position position) {
		this.position = position;
		return this;
	}

}
