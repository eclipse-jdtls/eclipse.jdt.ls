
package org.jboss.tools.langs;

import javax.annotation.Generated;
import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;

@Generated("org.jsonschema2pojo")
public class ReferenceContext {

    /**
     * Include the declaration of the current symbol.
     * 
     */
    @SerializedName("includeDeclaration")
    @Expose
    private Boolean includeDeclaration;

    /**
     * Include the declaration of the current symbol.
     * 
     * @return
     *     The includeDeclaration
     */
    public Boolean getIncludeDeclaration() {
        return includeDeclaration;
    }

    /**
     * Include the declaration of the current symbol.
     * 
     * @param includeDeclaration
     *     The includeDeclaration
     */
    public void setIncludeDeclaration(Boolean includeDeclaration) {
        this.includeDeclaration = includeDeclaration;
    }

    public ReferenceContext withIncludeDeclaration(Boolean includeDeclaration) {
        this.includeDeclaration = includeDeclaration;
        return this;
    }

}
