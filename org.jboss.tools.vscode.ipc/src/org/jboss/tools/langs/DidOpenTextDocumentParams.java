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

public class DidOpenTextDocumentParams {

	/**
	 *
	 * (Required)
	 *
	 */
	@SerializedName("textDocument")
	@Expose
	private TextDocumentItem textDocument;

	/**
	 *
	 * (Required)
	 *
	 * @return
	 *     The textDocument
	 */
	public TextDocumentItem getTextDocument() {
		return textDocument;
	}

	/**
	 *
	 * (Required)
	 *
	 * @param textDocument
	 *     The textDocument
	 */
	public void setTextDocument(TextDocumentItem textDocument) {
		this.textDocument = textDocument;
	}

	public DidOpenTextDocumentParams withTextDocument(TextDocumentItem textDocument) {
		this.textDocument = textDocument;
		return this;
	}

}
