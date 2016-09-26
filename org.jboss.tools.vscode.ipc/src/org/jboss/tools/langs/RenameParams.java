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

public class RenameParams {

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
	 * The new name of the symbol. If the given name is not valid the
	 *
	 * request must return a [ResponseError](#ResponseError) with an
	 *
	 * appropriate message set.
	 * (Required)
	 *
	 */
	@SerializedName("newName")
	@Expose
	private String newName;

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

	public RenameParams withTextDocument(TextDocumentIdentifier textDocument) {
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

	public RenameParams withPosition(Position position) {
		this.position = position;
		return this;
	}

	/**
	 * The new name of the symbol. If the given name is not valid the
	 *
	 * request must return a [ResponseError](#ResponseError) with an
	 *
	 * appropriate message set.
	 * (Required)
	 *
	 * @return
	 *     The newName
	 */
	public String getNewName() {
		return newName;
	}

	/**
	 * The new name of the symbol. If the given name is not valid the
	 *
	 * request must return a [ResponseError](#ResponseError) with an
	 *
	 * appropriate message set.
	 * (Required)
	 *
	 * @param newName
	 *     The newName
	 */
	public void setNewName(String newName) {
		this.newName = newName;
	}

	public RenameParams withNewName(String newName) {
		this.newName = newName;
		return this;
	}

}
