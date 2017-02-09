/*******************************************************************************
 * Copyright (c) 2016-2017 Red Hat Inc. and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Red Hat Inc. - initial API and implementation
 *******************************************************************************/
package org.eclipse.jdt.ls.core.internal.handlers;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Flattens nested Maps so that a map like
 * <pre>
 * [foo:[[ bar: value1], [baz: value2]]]
 * </pre>
 * is transformed into:
 * <pre>
 * [[foo.bar: value1],[foo.baz: value2]]
 * </pre>
 *
 * @author Fred Bricon
 *
 */
public class MapFlattener {

	private MapFlattener() {
		//No need for public instanciation
	}

	public static Map<String, Object> flatten(Map<String, Object> map) {
		if (map == null) {
			return null;
		}
		Map<String, Object> resultMap = new LinkedHashMap<>();
		visit(null, resultMap, map);
		return resultMap;
	}

	private static void visit(String prefix, Map<String, Object> resultMap, Map<String, Object> map) {
		for (Map.Entry<String, Object> entry : map.entrySet()) {
			String k = entry.getKey();
			String newKey = prefix == null?k:prefix+"."+k;
			Object v = entry.getValue();
			if (v instanceof Map) {
				visit(newKey, resultMap, (Map<String, Object>)v);
			} else {
				resultMap.put(newKey, v);
			}
		}
	}
}
