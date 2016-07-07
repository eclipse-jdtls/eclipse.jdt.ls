
package org.jboss.tools.langs;

import javax.annotation.Generated;
import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;


/**
 * An identifier to denote a specific version of a text document.
 * 
 */
@Generated("org.jsonschema2pojo")
public class VersionedTextDocumentIdentifier
    extends TextDocumentIdentifier
{

    /**
     * The version number of this document.
     * 
     */
    @SerializedName("version")
    @Expose
    private Integer version;

    /**
     * The version number of this document.
     * 
     * @return
     *     The version
     */
    public Integer getVersion() {
        return version;
    }

    /**
     * The version number of this document.
     * 
     * @param version
     *     The version
     */
    public void setVersion(Integer version) {
        this.version = version;
    }

}
