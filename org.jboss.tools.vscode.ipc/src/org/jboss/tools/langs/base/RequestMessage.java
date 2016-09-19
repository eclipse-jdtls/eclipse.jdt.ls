
package org.jboss.tools.langs.base;

import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;


/**
 * A request message to describe a request between the client and the server. Every processed request must send a response back to the sender of the request.
 * 
 */
public class RequestMessage <R>
    extends Message
{

    /**
     * The request id.
     * 
     */
    @SerializedName("id")
    @Expose
    private Integer id;
    /**
     * The method to be invoked.
     * 
     */
    @SerializedName("method")
    @Expose
    private String method;
    /**
     * The method's params.
     * 
     */
    @SerializedName("params")
    @Expose
    private R params;

    /**
     * The request id.
     * 
     * @return
     *     The id
     */
    public Integer getId() {
        return id;
    }

    /**
     * The request id.
     * 
     * @param id
     *     The id
     */
    public void setId(Integer id) {
        this.id = id;
    }

    /**
     * The method to be invoked.
     * 
     * @return
     *     The method
     */
    public String getMethod() {
        return method;
    }

    /**
     * The method to be invoked.
     * 
     * @param method
     *     The method
     */
    public void setMethod(String method) {
        this.method = method;
    }

    /**
     * The method's params.
     * 
     * @return
     *     The params
     */
    public R getParams() {
        return params;
    }

    /**
     * The method's params.
     * 
     * @param params
     *     The params
     */
    public void setParams(R params) {
        this.params = params;
    }
    
    /**
     * Builds a {@link ResponseMessage} with the passed result.
     * @param p
     * @return result
     */
    public <P> ResponseMessage<P> responseWith(P p){
    	ResponseMessage<P> response = new ResponseMessage<P>();
    	response.setId(this.getId());
    	response.setMethod(this.getMethod());
    	if( p != null ){
    		response.setResult(p);
    	}
    	return response;
    }
    
    public ResponseMessage<?> respondWithError(int code, String message, Object data){
    	ResponseMessage<?> $ = new ResponseMessage();
    	$.setId(this.getId());
    	$.setMethod(this.getMethod());
    	ResponseError error = new ResponseError();
    	error.setCode(Integer.valueOf(code));
    	error.setMessage(message);
    	error.setData(data);
    	$.setError(error);
    	return $;
    }

}
