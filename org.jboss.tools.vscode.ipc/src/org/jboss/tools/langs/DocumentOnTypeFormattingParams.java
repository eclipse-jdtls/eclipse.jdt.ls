
package org.jboss.tools.langs;

import javax.annotation.Generated;

import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;

@Generated("org.jsonschema2pojo")
public class DocumentOnTypeFormattingParams {

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
     * The character that has been typed.
     * 
     */
    @SerializedName("ch")
    @Expose
    private String ch;
    @SerializedName("options")
    @Expose
    private FormattingOptions options;

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

    public DocumentOnTypeFormattingParams withPosition(Position position) {
        this.position = position;
        return this;
    }

    /**
     * The character that has been typed.
     * 
     * @return
     *     The ch
     */
    public String getCh() {
        return ch;
    }

    /**
     * The character that has been typed.
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
     * @return
     *     The options
     */
    public FormattingOptions getOptions() {
        return options;
    }

    /**
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
