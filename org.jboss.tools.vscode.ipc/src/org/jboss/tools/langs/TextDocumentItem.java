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

public class TextDocumentItem {

	/**
	 * The text document's uri.
	 * (Required)
	 *
	 */
	@SerializedName("uri")
	@Expose
	private String uri;
	/**
	 * The text document's language identifier
	 * (Required)
	 *
	 */
	@SerializedName("languageId")
	@Expose
	private String languageId;
	/**
	 * The version number of this document (it will strictly increase after each
	 *
	 * change, including undo/redo).
	 * (Required)
	 *
	 */
	@SerializedName("version")
	@Expose
	private Double version;
	/**
	 * The content of the opened text document.
	 * (Required)
	 *
	 */
	@SerializedName("text")
	@Expose
	private String text;

	/**
	 * The text document's uri.
	 * (Required)
	 *
	 * @return
	 *     The uri
	 */
	public String getUri() {
		return uri;
	}

	/**
	 * The text document's uri.
	 * (Required)
	 *
	 * @param uri
	 *     The uri
	 */
	public void setUri(String uri) {
		this.uri = uri;
	}

	public TextDocumentItem withUri(String uri) {
		this.uri = uri;
		return this;
	}

	/**
	 * The text document's language identifier
	 * (Required)
	 *
	 * @return
	 *     The languageId
	 */
	public String getLanguageId() {
		return languageId;
	}

	/**
	 * The text document's language identifier
	 * (Required)
	 *
	 * @param languageId
	 *     The languageId
	 */
	public void setLanguageId(String languageId) {
		this.languageId = languageId;
	}

	public TextDocumentItem withLanguageId(String languageId) {
		this.languageId = languageId;
		return this;
	}

	/**
	 * The version number of this document (it will strictly increase after each
	 *
	 * change, including undo/redo).
	 * (Required)
	 *
	 * @return
	 *     The version
	 */
	public Double getVersion() {
		return version;
	}

	/**
	 * The version number of this document (it will strictly increase after each
	 *
	 * change, including undo/redo).
	 * (Required)
	 *
	 * @param version
	 *     The version
	 */
	public void setVersion(Double version) {
		this.version = version;
	}

	public TextDocumentItem withVersion(Double version) {
		this.version = version;
		return this;
	}

	/**
	 * The content of the opened text document.
	 * (Required)
	 *
	 * @return
	 *     The text
	 */
	public String getText() {
		return text;
	}

	/**
	 * The content of the opened text document.
	 * (Required)
	 *
	 * @param text
	 *     The text
	 */
	public void setText(String text) {
		this.text = text;
	}

	public TextDocumentItem withText(String text) {
		this.text = text;
		return this;
	}

}
