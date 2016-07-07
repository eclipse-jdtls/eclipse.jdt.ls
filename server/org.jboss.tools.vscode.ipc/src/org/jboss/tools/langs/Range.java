
package org.jboss.tools.langs;

import javax.annotation.Generated;
import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;


/**
 * A range in a text document expressed as (zero-based) start and end positions.
 * 
 */
@Generated("org.jsonschema2pojo")
public class Range {

    /**
     * Position in a text document expressed as zero-based line and character offset.
     * 
     */
    @SerializedName("start")
    @Expose
    private Position start;
    /**
     * Position in a text document expressed as zero-based line and character offset.
     * 
     */
    @SerializedName("end")
    @Expose
    private Position end;

    /**
     * Position in a text document expressed as zero-based line and character offset.
     * 
     * @return
     *     The start
     */
    public Position getStart() {
        return start;
    }

    /**
     * Position in a text document expressed as zero-based line and character offset.
     * 
     * @param start
     *     The start
     */
    public void setStart(Position start) {
        this.start = start;
    }

    /**
     * Position in a text document expressed as zero-based line and character offset.
     * 
     * @return
     *     The end
     */
    public Position getEnd() {
        return end;
    }

    /**
     * Position in a text document expressed as zero-based line and character offset.
     * 
     * @param end
     *     The end
     */
    public void setEnd(Position end) {
        this.end = end;
    }

}
