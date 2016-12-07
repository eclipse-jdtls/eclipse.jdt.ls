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
package org.jboss.tools.vscode.java.internal;

import java.lang.reflect.Method;
import java.util.Map;

import org.eclipse.lsp4j.jsonrpc.json.JsonRpcMethod;
import org.eclipse.lsp4j.jsonrpc.json.MessageJsonHandler;
import org.eclipse.lsp4j.jsonrpc.messages.Message;
import org.eclipse.lsp4j.jsonrpc.services.ServiceEndpoints;
import org.jboss.tools.vscode.java.internal.handlers.JDTLanguageServer;

/**
 * @author Fred Bricon
 *
 */
public class JsonMessageHelper {

	private static MessageJsonHandler handler;
	static {
		Map<String, JsonRpcMethod> methods = ServiceEndpoints.getSupportedMethods(JDTLanguageServer.class);
		handler = new MessageJsonHandler(methods);
	}

	public JsonMessageHelper() {
	}

	/**
	 * Returns the deserialized params attribute of a JSON message payload
	 */
	@SuppressWarnings("unchecked")
	public static <T> T getParams(CharSequence jsonPayload) {
		Message message = handler.parseMessage(jsonPayload);
		Method getParam = null;
		try {
			getParam = message.getClass().getMethod("getParams");
			Object params = getParam.invoke(message);
			return (T)params;
		} catch (Exception e) {
			throw new UnsupportedOperationException("Can't deserialize into class");
		}
	}

}
