
package org.jboss.tools.langs;

import javax.annotation.Generated;
import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;

@Generated("org.jsonschema2pojo")
public class ParameterInformation {

    /**
     * The label of this signature. Will be shown in
     * 
     * the UI.
     * 
     */
    @SerializedName("label")
    @Expose
    private String label;
    /**
     * The human-readable doc-comment of this signature. Will be shown
     * 
     * in the UI but can be omitted.
     * 
     */
    @SerializedName("documentation")
    @Expose
    private String documentation;

    /**
     * The label of this signature. Will be shown in
     * 
     * the UI.
     * 
     * @return
     *     The label
     */
    public String getLabel() {
        return label;
    }

    /**
     * The label of this signature. Will be shown in
     * 
     * the UI.
     * 
     * @param label
     *     The label
     */
    public void setLabel(String label) {
        this.label = label;
    }

    public ParameterInformation withLabel(String label) {
        this.label = label;
        return this;
    }

    /**
     * The human-readable doc-comment of this signature. Will be shown
     * 
     * in the UI but can be omitted.
     * 
     * @return
     *     The documentation
     */
    public String getDocumentation() {
        return documentation;
    }

    /**
     * The human-readable doc-comment of this signature. Will be shown
     * 
     * in the UI but can be omitted.
     * 
     * @param documentation
     *     The documentation
     */
    public void setDocumentation(String documentation) {
        this.documentation = documentation;
    }

    public ParameterInformation withDocumentation(String documentation) {
        this.documentation = documentation;
        return this;
    }

}
