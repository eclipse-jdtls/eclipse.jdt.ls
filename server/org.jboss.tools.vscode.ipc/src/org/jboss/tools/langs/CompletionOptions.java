
package org.jboss.tools.langs;

import java.util.ArrayList;
import java.util.List;
import javax.annotation.Generated;
import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;


/**
 * Completion options.
 * 
 */
@Generated("org.jsonschema2pojo")
public class CompletionOptions {

    /**
     * The server provides support to resolve additional
     * 
     * information for a completion item.
     * 
     */
    @SerializedName("resolveProvider")
    @Expose
    private Boolean resolveProvider;
    /**
     * The characters that trigger completion automatically.
     * 
     */
    @SerializedName("triggerCharacters")
    @Expose
    private List<String> triggerCharacters = new ArrayList<String>();

    /**
     * The server provides support to resolve additional
     * 
     * information for a completion item.
     * 
     * @return
     *     The resolveProvider
     */
    public Boolean getResolveProvider() {
        return resolveProvider;
    }

    /**
     * The server provides support to resolve additional
     * 
     * information for a completion item.
     * 
     * @param resolveProvider
     *     The resolveProvider
     */
    public void setResolveProvider(Boolean resolveProvider) {
        this.resolveProvider = resolveProvider;
    }

    public CompletionOptions withResolveProvider(Boolean resolveProvider) {
        this.resolveProvider = resolveProvider;
        return this;
    }

    /**
     * The characters that trigger completion automatically.
     * 
     * @return
     *     The triggerCharacters
     */
    public List<String> getTriggerCharacters() {
        return triggerCharacters;
    }

    /**
     * The characters that trigger completion automatically.
     * 
     * @param triggerCharacters
     *     The triggerCharacters
     */
    public void setTriggerCharacters(List<String> triggerCharacters) {
        this.triggerCharacters = triggerCharacters;
    }

    public CompletionOptions withTriggerCharacters(List<String> triggerCharacters) {
        this.triggerCharacters = triggerCharacters;
        return this;
    }

}
