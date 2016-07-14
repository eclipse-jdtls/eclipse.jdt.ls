
package org.jboss.tools.langs;

import javax.annotation.Generated;
import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;

@Generated("org.jsonschema2pojo")
public class TextDocumentPositionParams {

    @SerializedName("textDocument")
    @Expose
    private TextDocumentIdentifier textDocument;
    /**
     * Position in a text document expressed as zero-based line and character offset.
     * 
     * The Position namespace provides helper functions to work with
     * 
     * [Position](#Position) literals.
     * 
     */
    @SerializedName("position")
    @Expose
    private Position position;

    /**
     * 
     * @return
     *     The textDocument
     */
    public TextDocumentIdentifier getTextDocument() {
        return textDocument;
    }

    /**
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
