/*******************************************************************************
 * Copyright (c) 2017 Red Hat Inc. and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *     Red Hat Inc. - initial API and implementation
 *******************************************************************************/
package org.eclipse.jdt.ls.core.internal.handlers;

import static org.eclipse.jdt.ls.core.internal.handlers.MapFlattener.getBoolean;
import static org.eclipse.jdt.ls.core.internal.handlers.MapFlattener.getInt;
import static org.eclipse.jdt.ls.core.internal.handlers.MapFlattener.getList;
import static org.eclipse.jdt.ls.core.internal.handlers.MapFlattener.getString;
import static org.eclipse.jdt.ls.core.internal.handlers.MapFlattener.getValue;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.Test;

/**
 * @author Fred Bricon
 *
 */
public class MapFlattenerTest {

	@Test
	public void testGetString() throws Exception {
		Map<String, Object> config = new HashMap<>();
		config.put("foo", "bar");

		Map<String, Object> middle = new HashMap<>();
		config.put("java", middle);
		middle.put("thing", "value");

		Map<String, Object> bottom = new HashMap<>();
		middle.put("bottom", bottom);
		bottom.put("another", "thing");

		assertNull("default", getString(config, "missing"));
		assertEquals("default", getString(config, "missing", "default"));
		assertEquals("bar", getString(config, "foo"));
		assertEquals("value", getString(config, "java.thing"));
		assertEquals("thing", getString(config, "java.bottom.another"));
	}

	@Test
	public void testGetInt() throws Exception {
		Map<String, Object> config = new HashMap<>();
		config.put("foo", 1);

		Map<String, Object> middle = new HashMap<>();
		config.put("java", middle);
		middle.put("nope", "not an int");

		Map<String, Object> bottom = new HashMap<>();
		middle.put("bottom", bottom);
		bottom.put("another", "3");

		assertEquals(0, getInt(config, "missing"));
		assertEquals(1, getInt(config, "foo"));
		assertEquals(2, getInt(config, "java.nope", 2));
		assertEquals(3, getInt(config, "java.bottom.another"));
	}

	@Test
	public void testGetBoolean() throws Exception {
		Map<String, Object> config = new HashMap<>();
		config.put("foo", true);

		Map<String, Object> middle = new HashMap<>();
		config.put("java", middle);
		middle.put("thing", "TRUE");

		Map<String, Object> bottom = new HashMap<>();
		middle.put("bottom", bottom);
		bottom.put("another", true);

		assertTrue(getBoolean(config, "foo"));
		assertTrue(getBoolean(config, "java.thing"));
		assertTrue(getBoolean(config, "java.default", true));
		assertFalse(getBoolean(config, "java.missing"));
		assertTrue(getBoolean(config, "java.bottom.another"));
	}

	@Test
	public void testGetList() throws Exception {
		Map<String, Object> config = new HashMap<>();
		config.put("foo", "['a', 'b']");

		Map<String, Object> middle = new HashMap<>();
		config.put("java", middle);

		Map<String, Object> bottom = new HashMap<>();
		middle.put("bottom", bottom);
		bottom.put("another", "c, d");

		List<String> foo = getList(config, "foo");
		assertNotNull(foo);
		assertEquals("a", foo.get(0));
		assertEquals("b", foo.get(1));

		List<String> nope = getList(config, "java");
		assertNull(nope);

		List<String> def = new ArrayList<>();
		assertSame(def, getList(config, "java", def));

		List<String> bar = getList(config, "java.bottom.another");
		assertNotNull(bar);
		assertEquals("c", bar.get(0));
		assertEquals("d", bar.get(1));

		config.put("args", "a  b");
		List<String> args = getList(config, "args");
		assertNotNull(args);
		assertEquals(2, args.size());
		assertEquals("a", args.get(0));
		assertEquals("b", args.get(1));
		config.put("args", "a  b");

		config.put("args2", "a");
		List<String> args2 = getList(config, "args2");
		assertNotNull(args2);
		assertEquals(1, args2.size());
		assertEquals("a", args2.get(0));
	}

	@Test
	public void testGetValue() throws Exception {
		Map<String, Object> config = new HashMap<>();
		config.put("foo", 1);

		Map<String, Object> middle = new HashMap<>();
		config.put("java", middle);

		Map<String, Object> bottom = new HashMap<>();
		middle.put("bottom", bottom);
		bottom.put("another", 2);

		assertSame(bottom, getValue(config, "java.bottom"));
	}

}
