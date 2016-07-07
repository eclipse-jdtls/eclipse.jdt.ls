
package org.jboss.tools.langs;

import javax.annotation.Generated;
import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;

@Generated("org.jsonschema2pojo")
public class Change {

    @SerializedName("uri")
    @Expose
    private String uri;
    @SerializedName("edit")
    @Expose
    private Object edit;

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
     * 
     * @return
     *     The edit
     */
    public Object getEdit() {
        return edit;
    }

    /**
     * 
     * @param edit
     *     The edit
     */
    public void setEdit(Object edit) {
        this.edit = edit;
    }

}
