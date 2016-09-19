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

import java.util.ArrayList;
import java.util.List;

import javax.annotation.Generated;

import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;

@Generated("org.jsonschema2pojo")
public class DidChangeTextDocumentParams {

    @SerializedName("textDocument")
    @Expose
    private VersionedTextDocumentIdentifier textDocument;
    /**
     * The actual content changes.
     * 
     */
    @SerializedName("contentChanges")
    @Expose
    private List<TextDocumentContentChangeEvent> contentChanges = new ArrayList<TextDocumentContentChangeEvent>();

    /**
     * 
     * @return
     *     The textDocument
     */
    public VersionedTextDocumentIdentifier getTextDocument() {
        return textDocument;
    }

    /**
     * 
     * @param textDocument
     *     The textDocument
     */
    public void setTextDocument(VersionedTextDocumentIdentifier textDocument) {
        this.textDocument = textDocument;
    }

    public DidChangeTextDocumentParams withTextDocument(VersionedTextDocumentIdentifier textDocument) {
        this.textDocument = textDocument;
        return this;
    }

    /**
     * The actual content changes.
     * 
     * @return
     *     The contentChanges
     */
    public List<TextDocumentContentChangeEvent> getContentChanges() {
        return contentChanges;
    }

    /**
     * The actual content changes.
     * 
     * @param contentChanges
     *     The contentChanges
     */
    public void setContentChanges(List<TextDocumentContentChangeEvent> contentChanges) {
        this.contentChanges = contentChanges;
    }

    public DidChangeTextDocumentParams withContentChanges(List<TextDocumentContentChangeEvent> contentChanges) {
        this.contentChanges = contentChanges;
        return this;
    }

}
