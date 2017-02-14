/*******************************************************************************
 * Copyright (c) 2017 Red Hat Inc. and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Red Hat Inc. - initial API and implementation
 *******************************************************************************/
package org.eclipse.jdt.ls.core.internal.handlers;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import java.util.HashMap;
import java.util.Map;

import org.junit.Test;

/**
 * @author Fred Bricon
 *
 */
public class MapFlattenerTest {

	@Test
	public void testFlatten() throws Exception {
		assertNull(MapFlattener.flatten(null));

		Map<String, Object> top = new HashMap<>();
		top.put("foo", "bar");

		Map<String, Object> middle = new HashMap<>();
		top.put("java", middle);
		middle.put("thing", "value");

		Map<String, Object> bottom = new HashMap<>();
		middle.put("bottom", bottom);
		bottom.put("another", "thing");

		Map<String, Object> flattened = MapFlattener.flatten(top);
		assertEquals("bar", flattened.get("foo"));
		assertEquals("value", flattened.get("java.thing"));
		assertEquals("thing", flattened.get("java.bottom.another"));
	}

}
