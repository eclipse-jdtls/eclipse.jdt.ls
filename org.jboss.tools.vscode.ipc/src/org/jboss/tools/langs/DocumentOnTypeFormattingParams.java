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

public class DocumentOnTypeFormattingParams {

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
	 * The character that has been typed.
	 * (Required)
	 *
	 */
	@SerializedName("ch")
	@Expose
	private String ch;
	/**
	 *
	 * (Required)
	 *
	 */
	@SerializedName("options")
	@Expose
	private FormattingOptions options;

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

	public DocumentOnTypeFormattingParams withTextDocument(TextDocumentIdentifier textDocument) {
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

	public DocumentOnTypeFormattingParams withPosition(Position position) {
		this.position = position;
		return this;
	}

	/**
	 * The character that has been typed.
	 * (Required)
	 *
	 * @return
	 *     The ch
	 */
	public String getCh() {
		return ch;
	}

	/**
	 * The character that has been typed.
	 * (Required)
	 *
	 * @param ch
	 *     The ch
	 */
	public void setCh(String ch) {
		this.ch = ch;
	}

	public DocumentOnTypeFormattingParams withCh(String ch) {
		this.ch = ch;
		return this;
	}

	/**
	 *
	 * (Required)
	 *
	 * @return
	 *     The options
	 */
	public FormattingOptions getOptions() {
		return options;
	}

	/**
	 *
	 * (Required)
	 *
	 * @param options
	 *     The options
	 */
	public void setOptions(FormattingOptions options) {
		this.options = options;
	}

	public DocumentOnTypeFormattingParams withOptions(FormattingOptions options) {
		this.options = options;
		return this;
	}

}
