
package org.jboss.tools.langs;

import java.util.ArrayList;
import java.util.List;
import javax.annotation.Generated;
import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;

@Generated("org.jsonschema2pojo")
public class PublishDiagnosticsParams {

    /**
     * The URI for which diagnostic information is reported.
     * 
     */
    @SerializedName("uri")
    @Expose
    private String uri;
    /**
     * An array of diagnostic information items.
     * 
     */
    @SerializedName("diagnostics")
    @Expose
    private List<Diagnostic> diagnostics = new ArrayList<Diagnostic>();

    /**
     * The URI for which diagnostic information is reported.
     * 
     * @return
     *     The uri
     */
    public String getUri() {
        return uri;
    }

    /**
     * The URI for which diagnostic information is reported.
     * 
     * @param uri
     *     The uri
     */
    public void setUri(String uri) {
        this.uri = uri;
    }

    public PublishDiagnosticsParams withUri(String uri) {
        this.uri = uri;
        return this;
    }

    /**
     * An array of diagnostic information items.
     * 
     * @return
     *     The diagnostics
     */
    public List<Diagnostic> getDiagnostics() {
        return diagnostics;
    }

    /**
     * An array of diagnostic information items.
     * 
     * @param diagnostics
     *     The diagnostics
     */
    public void setDiagnostics(List<Diagnostic> diagnostics) {
        this.diagnostics = diagnostics;
    }

    public PublishDiagnosticsParams withDiagnostics(List<Diagnostic> diagnostics) {
        this.diagnostics = diagnostics;
        return this;
    }

}
