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
package org.jboss.tools.vscode.internal.ipc;

import org.jboss.tools.langs.base.LSPException;

/** 
 * Interface used for registering JSON RPC method handlers 
 * to the framework.
 * 
 * @author Gorkem Ercan
 *
 * @param <R> param object
 * @param <S> result object
 */
public interface RequestHandler<R,S> {
	
	
	/**
	 * Returns true if this handler can 
	 * handle this request. 
	 * 
	 * @param request - JSON RPC method value
	 * @return
	 */
	public boolean canHandle(String request);
	
	/**
	 * Invoked by the framework if canHandle 
	 * returns true. Params are converted to their 
	 * corresponding Java models and passed in. 
	 * Return value is used as response result.
	 * <p>
	 * {@link LSPException}s thrown will be converted to 
	 * errors and delivered to client if this is 
	 * a request/response 
	 * </p>
	 * 
	 * @param param
	 * @return result
	 * @throws LSPException
	 */
	public S handle(R param) ;
	
}
