
package org.jboss.tools.langs;

import javax.annotation.Generated;

import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;

@Generated("org.jsonschema2pojo")
public class DidOpenTextDocumentParams {

    @SerializedName("textDocument")
    @Expose
    private TextDocumentItem textDocument;

    /**
     * 
     * @return
     *     The textDocument
     */
    public TextDocumentItem getTextDocument() {
        return textDocument;
    }

    /**
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
