
package org.jboss.tools.langs;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.Generated;

import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;


/**
 * Signature help options.
 * 
 */
@Generated("org.jsonschema2pojo")
public class SignatureHelpOptions {

    /**
     * The characters that trigger signature help
     * 
     * automatically.
     * 
     */
    @SerializedName("triggerCharacters")
    @Expose
    private List<String> triggerCharacters = new ArrayList<String>();

    /**
     * The characters that trigger signature help
     * 
     * automatically.
     * 
     * @return
     *     The triggerCharacters
     */
    public List<String> getTriggerCharacters() {
        return triggerCharacters;
    }

    /**
     * The characters that trigger signature help
     * 
     * automatically.
     * 
     * @param triggerCharacters
     *     The triggerCharacters
     */
    public void setTriggerCharacters(List<String> triggerCharacters) {
        this.triggerCharacters = triggerCharacters;
    }

    public SignatureHelpOptions withTriggerCharacters(List<String> triggerCharacters) {
        this.triggerCharacters = triggerCharacters;
        return this;
    }

}
