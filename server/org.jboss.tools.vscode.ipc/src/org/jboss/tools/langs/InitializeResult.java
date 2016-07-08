
package org.jboss.tools.langs;

import javax.annotation.Generated;
import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;
import org.jboss.tools.langs.base.Params;


/**
 * The result returned from an initilize request.
 * 
 */
@Generated("org.jsonschema2pojo")
public class InitializeResult
    extends Params
{

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

}
