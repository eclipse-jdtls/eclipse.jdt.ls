/*******************************************************************************
 * Copyright (c) 2016 Red Hat Inc. and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Red Hat Inc. - initial API and implementation
 *******************************************************************************/
package org.jboss.tools.langs.base;
/**
 * Exceptions class for errors that should be reported back to client.
 * 
 * @author Gorkem Ercan
 *
 */
public class LSPException extends RuntimeException {

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
