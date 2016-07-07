
package org.jboss.tools.langs;

import javax.annotation.Generated;
import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;


/**
 * Text documents are identified using an URI. On the protocol level URI's are passed as strings
 * 
 */
@Generated("org.jsonschema2pojo")
public class TextDocumentIdentifier {

    /**
     * The text document's uri.
     * 
     */
    @SerializedName("uri")
    @Expose
    private String uri;

    /**
     * The text document's uri.
     * 
     * @return
     *     The uri
     */
    public String getUri() {
        return uri;
    }

    /**
     * The text document's uri.
     * 
     * @param uri
     *     The uri
     */
    public void setUri(String uri) {
        this.uri = uri;
    }

}
