
package org.jboss.tools.langs;

import javax.annotation.Generated;
import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;

@Generated("org.jsonschema2pojo")
public class InitializeResult {

    /**
     * Defines the capabilities provided by a language
     * 
     * server.
     * 
     */
    @SerializedName("capabilities")
    @Expose
    private ServerCapabilities capabilities;

    /**
     * Defines the capabilities provided by a language
     * 
     * server.
     * 
     * @return
     *     The capabilities
     */
    public ServerCapabilities getCapabilities() {
        return capabilities;
    }

    /**
     * Defines the capabilities provided by a language
     * 
     * server.
     * 
     * @param capabilities
     *     The capabilities
     */
    public void setCapabilities(ServerCapabilities capabilities) {
        this.capabilities = capabilities;
    }

    public InitializeResult withCapabilities(ServerCapabilities capabilities) {
        this.capabilities = capabilities;
        return this;
    }

}
