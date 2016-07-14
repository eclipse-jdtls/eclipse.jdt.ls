package org.jboss.tools.langs.base;

import java.lang.reflect.Type;

import org.jboss.tools.langs.InitializeParams;
import org.jboss.tools.langs.InitializeResult;

public enum LSPMethods {

	INITIALIZE("initialize",InitializeParams.class,InitializeResult.class);
	
	private final String method;
	private final Type requestType;
	private final Type resultType;
	
	LSPMethods(String method, Type request, Type result ) {
		this.method = method;
		this.requestType = request;
		this.resultType = result;
	}

	/**
	 * @return the resultType
	 */
	public Type getResultType() {
		return resultType;
	}

	/**
	 * @return the requestType
	 */
	public Type getRequestType() {
		return requestType;
	}

	/**
	 * @return the method
	 */
	public String getMethod() {
		return method;
	}
	
	public static LSPMethods fromMethod(String method){
		LSPMethods[] values = LSPMethods.values();
		for (LSPMethods lspmethod : values) {
			if(lspmethod.getMethod().equals(method))
			return lspmethod;
		}
		return null;
	}
	
}
