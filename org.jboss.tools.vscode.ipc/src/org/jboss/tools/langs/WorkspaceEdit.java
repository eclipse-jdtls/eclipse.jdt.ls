
package org.jboss.tools.langs;

import javax.annotation.Generated;

import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;

@Generated("org.jsonschema2pojo")
public class WorkspaceEdit {

    /**
     * Holds changes to existing resources.
     * 
     */
    @SerializedName("changes")
    @Expose
    private Changes changes;

    /**
     * Holds changes to existing resources.
     * 
     * @return
     *     The changes
     */
    public Changes getChanges() {
        return changes;
    }

    /**
     * Holds changes to existing resources.
     * 
     * @param changes
     *     The changes
     */
    public void setChanges(Changes changes) {
        this.changes = changes;
    }

    public WorkspaceEdit withChanges(Changes changes) {
        this.changes = changes;
        return this;
    }

}
