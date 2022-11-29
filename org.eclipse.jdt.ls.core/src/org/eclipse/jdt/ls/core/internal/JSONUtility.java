/*******************************************************************************
 * Copyright (c) 2018-2019 Red Hat Inc. and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *     Red Hat Inc. - initial API and implementation
 *
 *******************************************************************************/
package org.eclipse.jdt.ls.core.internal;

import java.util.HashMap;

import org.eclipse.lsp4j.jsonrpc.json.MessageJsonHandler;

import com.google.gson.Gson;
import com.google.gson.JsonElement;

/**
 * JSONUtility
 */
public class JSONUtility {

	/**
	 * Converts given JSON objects to given Model objects.
	 *
	 * @throws IllegalArgumentException if clazz is null
	 */
	public static <T> T toModel(Object object, Class<T> clazz){
		return toModel(new Gson(), object, clazz);
	}

	/**
	 * Converts given JSON objects to given Model objects with the customized
	 * TypeAdapters from lsp4j.
	 *
	 * @throws IllegalArgumentException
	 *             if clazz is null
	 */
	public static <T> T toLsp4jModel(Object object, Class<T> clazz) {
		return toModel(new MessageJsonHandler(new HashMap<>()).getGson(), object, clazz);
	}

	private static <T> T toModel(Gson gson, Object object, Class<T> clazz) {
		if(object == null){
			return null;
		}
		if(clazz == null ){
			throw new IllegalArgumentException("Class can not be null");
		}
		if (object instanceof JsonElement json) {
			return gson.fromJson(json, clazz);
		}
		if (clazz.isInstance(object)) {
			return clazz.cast(object);
		}
		if (object instanceof String json) {
			return gson.fromJson(json, clazz);
		}
		return null;
	}
}
