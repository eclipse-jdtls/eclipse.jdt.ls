/*******************************************************************************
 * Copyright (c) 2018 Red Hat Inc. and others.
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

package org.eclipse.jdt.ls.core.internal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.eclipse.lsp4j.Position;
import org.junit.jupiter.api.Test;

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

	@Test
	void testNullClass(){
		assertThrows(IllegalArgumentException.class, () -> {
			JSONUtility.toModel(new Object(), null);
		});
	}

	@Test
	public void testNullObject(){
		assertNull(JSONUtility.toModel(null, Object.class));
	}

}
