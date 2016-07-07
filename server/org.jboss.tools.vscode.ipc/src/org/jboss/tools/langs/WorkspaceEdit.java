
package org.jboss.tools.langs;

import java.util.ArrayList;
import java.util.List;
import javax.annotation.Generated;
import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;


/**
 * A workspace edit represents changes to many resources managed in the workspace.
 * 
 */
@Generated("org.jsonschema2pojo")
public class WorkspaceEdit {

    /**
     * Holds changes to existing resources
     * 
     */
    @SerializedName("changes")
    @Expose
    private List<Change> changes = new ArrayList<Change>();

    /**
     * Holds changes to existing resources
     * 
     * @return
     *     The changes
     */
    public List<Change> getChanges() {
        return changes;
    }

    /**
     * Holds changes to existing resources
     * 
     * @param changes
     *     The changes
     */
    public void setChanges(List<Change> changes) {
        this.changes = changes;
    }

}
