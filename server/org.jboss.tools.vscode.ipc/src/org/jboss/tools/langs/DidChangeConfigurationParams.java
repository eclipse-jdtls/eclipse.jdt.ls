
package org.jboss.tools.langs;

import javax.annotation.Generated;
import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;

@Generated("org.jsonschema2pojo")
public class DidChangeConfigurationParams {

    /**
     * The actual changed settings
     * 
     */
    @SerializedName("settings")
    @Expose
    private Object settings;

    /**
     * The actual changed settings
     * 
     * @return
     *     The settings
     */
    public Object getSettings() {
        return settings;
    }

    /**
     * The actual changed settings
     * 
     * @param settings
     *     The settings
     */
    public void setSettings(Object settings) {
        this.settings = settings;
    }

    public DidChangeConfigurationParams withSettings(Object settings) {
        this.settings = settings;
        return this;
    }

}
