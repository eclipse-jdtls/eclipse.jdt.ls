package org.jboss.tools.langs.base;
/**
 * Exceptions class for errors that should be reported back to client.
 * 
 * @author Gorkem Ercan
 *
 */
public class LSPException extends Exception {

	private static final long serialVersionUID = 1L;
	private final int code;
	private final Object data;
	
	
	public LSPException(int code, String message, Object data, Throwable cause) {
		super(message, cause);
		this.code = code;
		this.data = data;
	}


	/**
	 * @return the code
	 */
	public int getCode() {
		return code;
	}


	/**
	 * @return the data
	 */
	public Object getData() {
		return data;
	}
	
	
	
	
	

	
}
