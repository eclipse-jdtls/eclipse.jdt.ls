/*******************************************************************************
 * Copyright (c) 2018 Red Hat Inc. and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Red Hat Inc. - initial API and implementation
 *******************************************************************************/

package org.eclipse.jdt.ls.core.internal;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;

import org.eclipse.lsp4j.Position;
import org.junit.Test;

import com.google.gson.Gson;
import com.google.gson.JsonElement;

/**
 * JSONUtilityTest
 */
public class JSONUtilityTest {

	@Test
	public void testJsonElementToObject(){
		Gson gson = new Gson();
		Position position = new Position(5,5);
		JsonElement element = gson.toJsonTree(position);
		Position position2 = JSONUtility.toModel(element, Position.class);
		assertEquals(position,position2);
	}

	@Test
	public void testObjectToObject(){
		Position position = new Position(5,5);
		Position position2 = JSONUtility.toModel(position, Position.class);
		assertSame(position,position2);
	}

	@Test(expected=IllegalArgumentException.class)
	public void testNullClass(){
		JSONUtility.toModel(new Object(), null);
	}

	@Test
	public void testNullObject(){
		assertNull(JSONUtility.toModel(null, Object.class));
	}

}
