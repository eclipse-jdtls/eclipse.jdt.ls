
package org.jboss.tools.langs;

import javax.annotation.Generated;
import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;

@Generated("org.jsonschema2pojo")
public class MessageActionItem {

    /**
     * A short title like 'Retry', 'Open Log' etc.
     * 
     */
    @SerializedName("title")
    @Expose
    private String title;

    /**
     * A short title like 'Retry', 'Open Log' etc.
     * 
     * @return
     *     The title
     */
    public String getTitle() {
        return title;
    }

    /**
     * A short title like 'Retry', 'Open Log' etc.
     * 
     * @param title
     *     The title
     */
    public void setTitle(String title) {
        this.title = title;
    }

    public MessageActionItem withTitle(String title) {
        this.title = title;
        return this;
    }

}
