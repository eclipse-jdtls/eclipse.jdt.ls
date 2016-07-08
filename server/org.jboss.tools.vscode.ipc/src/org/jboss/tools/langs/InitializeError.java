
package org.jboss.tools.langs;

import javax.annotation.Generated;
import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;


/**
 * The error returned if the initilize request fails.
 * 
 */
@Generated("org.jsonschema2pojo")
public class InitializeError {

    /**
     * Indicates whether the client should retry to send the
     * 
     * initilize request after showing the message provided
     * 
     * in the {
     * 
     */
    @SerializedName("retry")
    @Expose
    private Boolean retry;

    /**
     * Indicates whether the client should retry to send the
     * 
     * initilize request after showing the message provided
     * 
     * in the {
     * 
     * @return
     *     The retry
     */
    public Boolean getRetry() {
        return retry;
    }

    /**
     * Indicates whether the client should retry to send the
     * 
     * initilize request after showing the message provided
     * 
     * in the {
     * 
     * @param retry
     *     The retry
     */
    public void setRetry(Boolean retry) {
        this.retry = retry;
    }

}
