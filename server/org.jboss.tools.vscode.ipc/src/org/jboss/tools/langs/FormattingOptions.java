
package org.jboss.tools.langs;

import javax.annotation.Generated;
import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;

@Generated("org.jsonschema2pojo")
public class FormattingOptions {

    /**
     * Size of a tab in spaces.
     * 
     */
    @SerializedName("tabSize")
    @Expose
    private Double tabSize;
    /**
     * Prefer spaces over tabs.
     * 
     */
    @SerializedName("insertSpaces")
    @Expose
    private Boolean insertSpaces;

    /**
     * Size of a tab in spaces.
     * 
     * @return
     *     The tabSize
     */
    public Double getTabSize() {
        return tabSize;
    }

    /**
     * Size of a tab in spaces.
     * 
     * @param tabSize
     *     The tabSize
     */
    public void setTabSize(Double tabSize) {
        this.tabSize = tabSize;
    }

    public FormattingOptions withTabSize(Double tabSize) {
        this.tabSize = tabSize;
        return this;
    }

    /**
     * Prefer spaces over tabs.
     * 
     * @return
     *     The insertSpaces
     */
    public Boolean getInsertSpaces() {
        return insertSpaces;
    }

    /**
     * Prefer spaces over tabs.
     * 
     * @param insertSpaces
     *     The insertSpaces
     */
    public void setInsertSpaces(Boolean insertSpaces) {
        this.insertSpaces = insertSpaces;
    }

    public FormattingOptions withInsertSpaces(Boolean insertSpaces) {
        this.insertSpaces = insertSpaces;
        return this;
    }

}
