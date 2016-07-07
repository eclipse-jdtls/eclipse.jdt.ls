
package org.jboss.tools.langs;

import javax.annotation.Generated;
import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;


/**
 * A parameter literal used in requests to pass a text document and a position inside that document.
 * 
 */
@Generated("org.jsonschema2pojo")
public class TextDocumentPositionParams {

    /**
     * The position inside the text document.
     * 
     */
    @SerializedName("position")
    @Expose
    private Object position;
    /**
     * Text documents are identified using an URI. On the protocol level URI's are passed as strings
     * 
     */
    @SerializedName("textDocument")
    @Expose
    private TextDocumentIdentifier textDocument;

    /**
     * The position inside the text document.
     * 
     * @return
     *     The position
     */
    public Object getPosition() {
        return position;
    }

    /**
     * The position inside the text document.
     * 
     * @param position
     *     The position
     */
    public void setPosition(Object position) {
        this.position = position;
    }

    /**
     * Text documents are identified using an URI. On the protocol level URI's are passed as strings
     * 
     * @return
     *     The textDocument
     */
    public TextDocumentIdentifier getTextDocument() {
        return textDocument;
    }

    /**
     * Text documents are identified using an URI. On the protocol level URI's are passed as strings
     * 
     * @param textDocument
     *     The textDocument
     */
    public void setTextDocument(TextDocumentIdentifier textDocument) {
        this.textDocument = textDocument;
    }

}
