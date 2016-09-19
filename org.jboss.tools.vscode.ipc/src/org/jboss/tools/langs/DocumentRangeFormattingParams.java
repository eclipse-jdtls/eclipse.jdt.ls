
package org.jboss.tools.langs;

import javax.annotation.Generated;

import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;

@Generated("org.jsonschema2pojo")
public class DocumentRangeFormattingParams {

    @SerializedName("textDocument")
    @Expose
    private TextDocumentIdentifier textDocument;
    @SerializedName("range")
    @Expose
    private Range range;
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

    public DocumentRangeFormattingParams withTextDocument(TextDocumentIdentifier textDocument) {
        this.textDocument = textDocument;
        return this;
    }

    /**
     * 
     * @return
     *     The range
     */
    public Range getRange() {
        return range;
    }

    /**
     * 
     * @param range
     *     The range
     */
    public void setRange(Range range) {
        this.range = range;
    }

    public DocumentRangeFormattingParams withRange(Range range) {
        this.range = range;
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

    public DocumentRangeFormattingParams withOptions(FormattingOptions options) {
        this.options = options;
        return this;
    }

}
