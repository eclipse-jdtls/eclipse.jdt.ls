
package org.jboss.tools.langs;

import javax.annotation.Generated;
import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;


/**
 * Represents a diagnostic, such as a compiler error or warning. Diagnostic objects are only valid in the scope of a resource.
 * 
 */
@Generated("org.jsonschema2pojo")
public class Diagnostic {

    /**
     * A range in a text document expressed as (zero-based) start and end positions.
     * 
     */
    @SerializedName("range")
    @Expose
    private Range range;
    /**
     * The diagnostic's severity. Can be omitted. If omitted it is up to the client to interpret diagnostics as error, warning, info or hint.
     * 
     */
    @SerializedName("severity")
    @Expose
    private Integer severity;
    /**
     * The diagnostic's code. Can be omitted.
     * 
     */
    @SerializedName("code")
    @Expose
    private Integer code;
    /**
     * A human-readable string describing the source of this diagnostic, e.g. 'typescript' or 'super lint'..
     * 
     */
    @SerializedName("source")
    @Expose
    private String source;
    /**
     * The diagnostic's message.
     * 
     */
    @SerializedName("message")
    @Expose
    private String message;

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

    /**
     * The diagnostic's severity. Can be omitted. If omitted it is up to the client to interpret diagnostics as error, warning, info or hint.
     * 
     * @return
     *     The severity
     */
    public Integer getSeverity() {
        return severity;
    }

    /**
     * The diagnostic's severity. Can be omitted. If omitted it is up to the client to interpret diagnostics as error, warning, info or hint.
     * 
     * @param severity
     *     The severity
     */
    public void setSeverity(Integer severity) {
        this.severity = severity;
    }

    /**
     * The diagnostic's code. Can be omitted.
     * 
     * @return
     *     The code
     */
    public Integer getCode() {
        return code;
    }

    /**
     * The diagnostic's code. Can be omitted.
     * 
     * @param code
     *     The code
     */
    public void setCode(Integer code) {
        this.code = code;
    }

    /**
     * A human-readable string describing the source of this diagnostic, e.g. 'typescript' or 'super lint'..
     * 
     * @return
     *     The source
     */
    public String getSource() {
        return source;
    }

    /**
     * A human-readable string describing the source of this diagnostic, e.g. 'typescript' or 'super lint'..
     * 
     * @param source
     *     The source
     */
    public void setSource(String source) {
        this.source = source;
    }

    /**
     * The diagnostic's message.
     * 
     * @return
     *     The message
     */
    public String getMessage() {
        return message;
    }

    /**
     * The diagnostic's message.
     * 
     * @param message
     *     The message
     */
    public void setMessage(String message) {
        this.message = message;
    }

}
