
package org.jboss.tools.langs;

import javax.annotation.Generated;
import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;


/**
 * Position in a text document expressed as zero-based line and character offset.
 * 
 */
@Generated("org.jsonschema2pojo")
public class Position {

    /**
     * Line position in a document (zero-based).
     * 
     */
    @SerializedName("line")
    @Expose
    private Integer line;
    /**
     * Character offset on a line in a document (zero-based).
     * 
     */
    @SerializedName("character")
    @Expose
    private Integer character;

    /**
     * Line position in a document (zero-based).
     * 
     * @return
     *     The line
     */
    public Integer getLine() {
        return line;
    }

    /**
     * Line position in a document (zero-based).
     * 
     * @param line
     *     The line
     */
    public void setLine(Integer line) {
        this.line = line;
    }

    /**
     * Character offset on a line in a document (zero-based).
     * 
     * @return
     *     The character
     */
    public Integer getCharacter() {
        return character;
    }

    /**
     * Character offset on a line in a document (zero-based).
     * 
     * @param character
     *     The character
     */
    public void setCharacter(Integer character) {
        this.character = character;
    }

}
