
package org.jboss.tools.langs.base;

import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;

public class ResponseError {
	
	public enum ReservedCode{
	
		PARSE_ERROR(-32700), 
		INVALID_REQUEST(-32600),
		METHOD_NOT_FOUND(-32601),
		INVALID_PARAMS(-32602),
		INTERNAL_ERROR(-32603),
		
		SERVER_ERROR_START(-32000),
		SERVER_ERROR_END(-32099);
		
		private final int code;
		
		private ReservedCode(int code) {
			this.code = code;
		}

		/**
		 * @return the code
		 */
		public int code() {
			return code;
		}
				
	}

    /**
     * A number indicating the error type that occurred.
     * 
     */
    @SerializedName("code")
    @Expose
    private Integer code;
    /**
     * A string providing a short description of the error.
     * 
     */
    @SerializedName("message")
    @Expose
    private String message;
    /**
     * A Primitive or Structured value that contains additional information about the error. Can be omitted.
     * 
     */
    @SerializedName("data")
    @Expose
    private Object data;

    /**
     * A number indicating the error type that occurred.
     * 
     * @return
     *     The code
     */
    public Integer getCode() {
        return code;
    }

    /**
     * A number indicating the error type that occurred.
     * 
     * @param code
     *     The code
     */
    public void setCode(Integer code) {
        this.code = code;
    }

    /**
     * A string providing a short description of the error.
     * 
     * @return
     *     The message
     */
    public String getMessage() {
        return message;
    }

    /**
     * A string providing a short description of the error.
     * 
     * @param message
     *     The message
     */
    public void setMessage(String message) {
        this.message = message;
    }

    /**
     * A Primitive or Structured value that contains additional information about the error. Can be omitted.
     * 
     * @return
     *     The data
     */
    public Object getData() {
        return data;
    }

    /**
     * A Primitive or Structured value that contains additional information about the error. Can be omitted.
     * 
     * @param data
     *     The data
     */
    public void setData(Object data) {
        this.data = data;
    }

}
