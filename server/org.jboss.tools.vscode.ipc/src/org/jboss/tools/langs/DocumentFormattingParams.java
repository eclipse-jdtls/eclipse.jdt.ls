
package org.jboss.tools.langs;

import javax.annotation.Generated;
import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;

@Generated("org.jsonschema2pojo")
public class DocumentFormattingParams {

    @SerializedName("textDocument")
    @Expose
    private TextDocumentIdentifier textDocument;
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

    public DocumentFormattingParams withTextDocument(TextDocumentIdentifier textDocument) {
        this.textDocument = textDocument;
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

    public DocumentFormattingParams withOptions(FormattingOptions options) {
        this.options = options;
        return this;
    }

}
