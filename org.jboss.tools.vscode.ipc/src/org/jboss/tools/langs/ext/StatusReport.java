
package org.jboss.tools.langs.ext;

import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;

public class StatusReport {

    /**
     * The message type. See {
     * 
     */
    @SerializedName("type")
    @Expose
    private String type;
    /**
     * The actual message
     * 
     */
    @SerializedName("message")
    @Expose
    private String message;

    /**
     * The message type. See {
     * 
     * @return
     *     The type
     */
    public String getType() {
        return type;
    }

    /**
     * The message type. See {
     * 
     * @param type
     *     The type
     */
    public void setType(String type) {
        this.type = type;
    }

    public StatusReport withType(String type) {
        this.type = type;
        return this;
    }

    /**
     * The actual message
     * 
     * @return
     *     The message
     */
    public String getMessage() {
        return message;
    }

    /**
     * The actual message
     * 
     * @param message
     *     The message
     */
    public void setMessage(String message) {
        this.message = message;
    }

    public StatusReport withMessage(String message) {
        this.message = message;
        return this;
    }

}
