/*******************************************************************************
 * Copyright (c) 2026 IBM Corp. and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *     IBM Corp. - initial API and implementation
 *******************************************************************************/
package org.eclipse.jdt.ls.core.internal.lombok;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.eclipse.core.resources.IMarker;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.jdt.ls.core.internal.ClassFileUtil;
import org.eclipse.jdt.ls.core.internal.ResourceUtils;
import org.eclipse.jdt.ls.core.internal.WorkspaceHelper;
import org.eclipse.jdt.ls.core.internal.handlers.NavigateToDefinitionHandler;
import org.eclipse.jdt.ls.core.internal.managers.AbstractProjectsManagerBasedTest;
import org.eclipse.lsp4j.Location;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.eclipse.lsp4j.TextDocumentPositionParams;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class LombokNavigateToDefinitionHandlerTest extends AbstractProjectsManagerBasedTest {

	private NavigateToDefinitionHandler handler;
	private IProject project;

	@BeforeEach
	public void setUp() throws Exception {
		handler = new NavigateToDefinitionHandler(preferenceManager);
		importProjects("maven/mavenlombok");
		project = WorkspaceHelper.getProject("mavenlombok");
		Job.getJobManager().join(ResourcesPlugin.FAMILY_AUTO_BUILD, monitor);
		Job.getJobManager().join(ResourcesPlugin.FAMILY_MANUAL_BUILD, monitor);
		Job.getJobManager().join(ResourcesPlugin.FAMILY_AUTO_REFRESH, monitor);
	}

	// https://github.com/redhat-developer/vscode-java/issues/2805
	@Test
	public void testLombok() throws Exception {
		List<IMarker> markers = ResourceUtils.getErrorMarkers(project);
		if (!markers.isEmpty()) {
			// there isn't the lombok agent
			return;
		}
		String uri = ClassFileUtil.getURI(project, "org.sample.Main");
		TextDocumentIdentifier identifier = new TextDocumentIdentifier(uri);
		List<? extends Location> locations = handler.definition(new TextDocumentPositionParams(identifier, new Position(5, 20)), monitor);
		assertNotNull(locations);
		assertEquals(1, locations.size());
		assertEquals(6, locations.get(0).getRange().getStart().getLine());
		assertEquals(6, locations.get(0).getRange().getEnd().getLine());
		assertEquals(19, locations.get(0).getRange().getStart().getCharacter());
		assertEquals(23, locations.get(0).getRange().getEnd().getCharacter());
		assertNotNull(locations.get(0).getUri());
		assertTrue(locations.get(0).getUri().endsWith("org/sample/Test.java"));
	}

}
