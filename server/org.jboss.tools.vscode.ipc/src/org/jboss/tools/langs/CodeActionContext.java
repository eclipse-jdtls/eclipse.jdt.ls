
package org.jboss.tools.langs;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.Generated;

import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;

@Generated("org.jsonschema2pojo")
public class CodeActionContext {

    /**
     * An array of diagnostics.
     * 
     */
    @SerializedName("diagnostics")
    @Expose
    private List<Diagnostic> diagnostics = new ArrayList<Diagnostic>();

    /**
     * An array of diagnostics.
     * 
     * @return
     *     The diagnostics
     */
    public List<Diagnostic> getDiagnostics() {
        return diagnostics;
    }

    /**
     * An array of diagnostics.
     * 
     * @param diagnostics
     *     The diagnostics
     */
    public void setDiagnostics(List<Diagnostic> diagnostics) {
        this.diagnostics = diagnostics;
    }

    public CodeActionContext withDiagnostics(List<Diagnostic> diagnostics) {
        this.diagnostics = diagnostics;
        return this;
    }

}
