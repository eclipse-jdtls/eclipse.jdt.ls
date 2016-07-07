
package org.jboss.tools.langs;

import javax.annotation.Generated;
import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;


/**
 * A textual edit applicable to a text document.
 * 
 */
@Generated("org.jsonschema2pojo")
public class TextEdit {

    /**
     * The string to be inserted. For delete operations use an empty string.
     * 
     */
    @SerializedName("newText")
    @Expose
    private String newText;
    /**
     * A range in a text document expressed as (zero-based) start and end positions.
     * 
     */
    @SerializedName("range")
    @Expose
    private Range range;

    /**
     * The string to be inserted. For delete operations use an empty string.
     * 
     * @return
     *     The newText
     */
    public String getNewText() {
        return newText;
    }

    /**
     * The string to be inserted. For delete operations use an empty string.
     * 
     * @param newText
     *     The newText
     */
    public void setNewText(String newText) {
        this.newText = newText;
    }

    /**
     * A range in a text document expressed as (zero-based) start and end positions.
     * 
     * @return
     *     The range
     */
    public Range getRange() {
        return range;
    }

    /**
     * A range in a text document expressed as (zero-based) start and end positions.
     * 
     * @param range
     *     The range
     */
    public void setRange(Range range) {
        this.range = range;
    }

}
