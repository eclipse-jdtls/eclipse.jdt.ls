
package org.jboss.tools.langs;

import javax.annotation.Generated;
import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;
import org.jboss.tools.langs.base.Params;


/**
 * Cancellation Support
 * 
 */
@Generated("org.jsonschema2pojo")
public class CancelParams
    extends Params
{

    /**
     * The request id to cancel
     * 
     */
    @SerializedName("id")
    @Expose
    private Integer id;

    /**
     * The request id to cancel
     * 
     * @return
     *     The id
     */
    public Integer getId() {
        return id;
    }

    /**
     * The request id to cancel
     * 
     * @param id
     *     The id
     */
    public void setId(Integer id) {
        this.id = id;
    }

}
