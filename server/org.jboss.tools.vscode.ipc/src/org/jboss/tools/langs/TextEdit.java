
package org.jboss.tools.langs;

import javax.annotation.Generated;
import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;

@Generated("org.jsonschema2pojo")
public class TextEdit {

    @SerializedName("range")
    @Expose
    private Range range;
    /**
     * The string to be inserted. For delete operations use an
     * 
     * empty string.
     * 
     */
    @SerializedName("newText")
    @Expose
    private String newText;

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

    public TextEdit withRange(Range range) {
        this.range = range;
        return this;
    }

    /**
     * The string to be inserted. For delete operations use an
     * 
     * empty string.
     * 
     * @return
     *     The newText
     */
    public String getNewText() {
        return newText;
    }

    /**
     * The string to be inserted. For delete operations use an
     * 
     * empty string.
     * 
     * @param newText
     *     The newText
     */
    public void setNewText(String newText) {
        this.newText = newText;
    }

    public TextEdit withNewText(String newText) {
        this.newText = newText;
        return this;
    }

}
