
package org.jboss.tools.langs;

import javax.annotation.Generated;

import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;

@Generated("org.jsonschema2pojo")
public class Hover {

    /**
     * The hover's content
     * 
     */
    @SerializedName("contents")
    @Expose
    private Object contents;
    @SerializedName("range")
    @Expose
    private Range range;

    /**
     * The hover's content
     * 
     * @return
     *     The contents
     */
    public Object getContents() {
        return contents;
    }

    /**
     * The hover's content
     * 
     * @param contents
     *     The contents
     */
    public void setContents(Object contents) {
        this.contents = contents;
    }

    public Hover withContents(Object contents) {
        this.contents = contents;
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

    public Hover withRange(Range range) {
        this.range = range;
        return this;
    }

}
