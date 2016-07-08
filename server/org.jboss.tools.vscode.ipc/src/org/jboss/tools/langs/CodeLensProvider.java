
package org.jboss.tools.langs;

import javax.annotation.Generated;
import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;


/**
 * Code Lens options.
 * 
 */
@Generated("org.jsonschema2pojo")
public class CodeLensProvider {

    /**
     * Code lens has a resolve provider as well.
     * 
     */
    @SerializedName("resolveProvider")
    @Expose
    private Boolean resolveProvider;

    /**
     * Code lens has a resolve provider as well.
     * 
     * @return
     *     The resolveProvider
     */
    public Boolean getResolveProvider() {
        return resolveProvider;
    }

    /**
     * Code lens has a resolve provider as well.
     * 
     * @param resolveProvider
     *     The resolveProvider
     */
    public void setResolveProvider(Boolean resolveProvider) {
        this.resolveProvider = resolveProvider;
    }

}
