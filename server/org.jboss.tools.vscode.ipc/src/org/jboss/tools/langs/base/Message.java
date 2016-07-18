
package org.jboss.tools.langs.base;

import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;


/**
 * A general message as defined by JSON-RPC. The language server protocol always uses 2.0 as the jsonrpc version.
 * 
 */
public abstract class Message
 {

    @SerializedName("jsonrpc")
    @Expose
    private String jsonrpc ="2.0";

    /**
     * 
     * @return
     *     The jsonrpc
     */
    public String getJsonrpc() {
        return jsonrpc;
    }

    /**
     * 
     * @param jsonrpc
     *     The jsonrpc
     */
    public void setJsonrpc(String jsonrpc) {
        this.jsonrpc = jsonrpc;
    }
    
}
