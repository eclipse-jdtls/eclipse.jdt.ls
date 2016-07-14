
package org.jboss.tools.langs;

import javax.annotation.Generated;
import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;

@Generated("org.jsonschema2pojo")
public class RenameParams {

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
     * The new name of the symbol. If the given name is not valid the
     * 
     * request must return a [ResponseError](#ResponseError) with an
     * 
     * appropriate message set.
     * 
     */
    @SerializedName("newName")
    @Expose
    private String newName;

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
