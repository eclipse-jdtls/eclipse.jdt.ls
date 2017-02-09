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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import java.util.List;

import org.eclipse.jdt.ls.core.internal.handlers.NavigateToDefinitionHandler;
import org.eclipse.lsp4j.Location;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.eclipse.lsp4j.TextDocumentPositionParams;
import org.junit.Before;
import org.junit.Test;

/**
 * @author Fred Bricon
 *
 */
public class NavigateToDefinitionHandlerTest {

	private NavigateToDefinitionHandler handler;

	@Before
	public void setUp() {
		handler = new NavigateToDefinitionHandler();
	}

	@Test
	public void testGetEmptyDefinition() throws Exception {
		List<? extends Location> definitions = handler.getDefinition(new TextDocumentPositionParams(new TextDocumentIdentifier("/foo/bar"), "/foo/bar", new Position(1, 1)));
		assertNotNull(definitions);
		assertEquals(1, definitions.size());
		assertNotNull("Location has no Range", definitions.get(0).getRange());
	}

}
