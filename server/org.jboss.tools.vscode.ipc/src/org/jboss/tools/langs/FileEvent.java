
package org.jboss.tools.langs;

import javax.annotation.Generated;
import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;

@Generated("org.jsonschema2pojo")
public class FileEvent {

    /**
     * The file's uri.
     * 
     */
    @SerializedName("uri")
    @Expose
    private String uri;
    /**
     * The change type.
     * 
     */
    @SerializedName("type")
    @Expose
    private Double type;

    /**
     * The file's uri.
     * 
     * @return
     *     The uri
     */
    public String getUri() {
        return uri;
    }

    /**
     * The file's uri.
     * 
     * @param uri
     *     The uri
     */
    public void setUri(String uri) {
        this.uri = uri;
    }

    public FileEvent withUri(String uri) {
        this.uri = uri;
        return this;
    }

    /**
     * The change type.
     * 
     * @return
     *     The type
     */
    public Double getType() {
        return type;
    }

    /**
     * The change type.
     * 
     * @param type
     *     The type
     */
    public void setType(Double type) {
        this.type = type;
    }

    public FileEvent withType(Double type) {
        this.type = type;
        return this;
    }

}
