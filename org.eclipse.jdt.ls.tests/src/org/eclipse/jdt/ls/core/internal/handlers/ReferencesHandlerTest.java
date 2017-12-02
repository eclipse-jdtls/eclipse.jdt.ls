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
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.net.URI;
import java.util.List;

import org.eclipse.core.resources.IProject;
import org.eclipse.jdt.ls.core.internal.ResourceUtils;
import org.eclipse.jdt.ls.core.internal.WorkspaceHelper;
import org.eclipse.jdt.ls.core.internal.managers.AbstractProjectsManagerBasedTest;
import org.eclipse.jdt.ls.core.internal.preferences.PreferenceManager;
import org.eclipse.jdt.ls.core.internal.preferences.Preferences;
import org.eclipse.lsp4j.Location;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.ReferenceContext;
import org.eclipse.lsp4j.ReferenceParams;
import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.junit.Before;
import org.junit.Test;

/**
 * ReferencesHandlerTest
 */
public class ReferencesHandlerTest extends AbstractProjectsManagerBasedTest{

	private ReferencesHandler handler;
	private IProject project;


	@Before
	public void setup() throws Exception{
		importProjects("eclipse/hello");
		project = WorkspaceHelper.getProject("hello");
		preferenceManager = mock(PreferenceManager.class);
		when(preferenceManager.getPreferences()).thenReturn(new Preferences());
		handler = new ReferencesHandler(preferenceManager);
	}

	@Test
	public void testEmpty(){
		ReferenceParams param = new ReferenceParams();
		param.setPosition(new Position(1, 1));
		param.setContext(new ReferenceContext(false));
		param.setTextDocument( new TextDocumentIdentifier("/foo/bar"));
		List<Location> references =  handler.findReferences(param, monitor);
		assertNotNull(references);
		assertTrue("references are not empty", references.isEmpty());
	}

	@Test
	public void testReference(){
		URI uri = project.getFile("src/java/Foo2.java").getRawLocationURI();
		String fileURI = ResourceUtils.fixURI(uri);

		ReferenceParams param = new ReferenceParams();
		param.setPosition(new Position(5,16));
		param.setContext(new ReferenceContext(true));
		param.setTextDocument( new TextDocumentIdentifier(fileURI));
		List<Location> references =  handler.findReferences(param, monitor);
		assertNotNull("findReferences should not return null",references);
		assertEquals(1, references.size());
		Location l = references.get(0);
		String refereeUri = ResourceUtils.fixURI(project.getFile("src/java/Foo3.java").getRawLocationURI());
		assertEquals(refereeUri, l.getUri());
	}

}
