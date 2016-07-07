
package org.jboss.tools.langs;

import javax.annotation.Generated;
import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;


/**
 * Represents a location inside a resource, such as a line inside a text file.
 * 
 */
@Generated("org.jsonschema2pojo")
public class Location {

    @SerializedName("uri")
    @Expose
    private String uri;
    /**
     * A range in a text document expressed as (zero-based) start and end positions.
     * 
     */
    @SerializedName("range")
    @Expose
    private Range range;

    /**
     * 
     * @return
     *     The uri
     */
    public String getUri() {
        return uri;
    }

    /**
     * 
     * @param uri
     *     The uri
     */
    public void setUri(String uri) {
        this.uri = uri;
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
