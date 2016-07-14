
package org.jboss.tools.langs;

import javax.annotation.Generated;
import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;

@Generated("org.jsonschema2pojo")
public class WorkspaceSymbolParams {

    /**
     * A non-empty query string
     * 
     */
    @SerializedName("query")
    @Expose
    private String query;

    /**
     * A non-empty query string
     * 
     * @return
     *     The query
     */
    public String getQuery() {
        return query;
    }

    /**
     * A non-empty query string
     * 
     * @param query
     *     The query
     */
    public void setQuery(String query) {
        this.query = query;
    }

    public WorkspaceSymbolParams withQuery(String query) {
        this.query = query;
        return this;
    }

}
