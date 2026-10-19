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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IMarker;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.ls.core.internal.JDTUtils;
import org.eclipse.jdt.ls.core.internal.ResourceUtils;
import org.eclipse.jdt.ls.core.internal.TextEditUtil;
import org.eclipse.jdt.ls.core.internal.WorkspaceHelper;
import org.eclipse.jdt.ls.core.internal.handlers.RenameHandler;
import org.eclipse.jdt.ls.core.internal.managers.AbstractProjectsManagerBasedTest;
import org.eclipse.jdt.ls.core.internal.preferences.ClientPreferences;
import org.eclipse.jdt.ls.core.internal.preferences.Preferences;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.RenameParams;
import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.eclipse.lsp4j.WorkspaceEdit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
public class LombokRenameHandlerTest extends AbstractProjectsManagerBasedTest {

	private RenameHandler handler;

	private ClientPreferences clientPreferences;

	@BeforeEach
	public void setup() throws Exception {
		clientPreferences = preferenceManager.getClientPreferences();
		when(clientPreferences.isResourceOperationSupported()).thenReturn(false);
		Preferences p = mock(Preferences.class);
		when(p.getProjectConfigurations()).thenReturn(null);
		when(preferenceManager.getPreferences()).thenReturn(p);
		when(p.isRenameEnabled()).thenReturn(true);
		when(p.getMavenDefaultMojoExecutionAction()).thenReturn("ignore");
		handler = new RenameHandler(preferenceManager);
	}

	// https://github.com/redhat-developer/vscode-java/issues/2805
	@Test
	public void testRenameMethodLombok() throws Exception {
		when(preferenceManager.getPreferences().isImportMavenEnabled()).thenReturn(true);
		importProjects("maven/mavenlombok");
		IProject project = WorkspaceHelper.getProject("mavenlombok");
		List<IMarker> markers = ResourceUtils.getErrorMarkers(project);
		if (!markers.isEmpty()) {
			// there isn't the lombok agent
			return;
		}
		IFile main = project.getFile("src/main/java/org/sample/Main.java");
		assertTrue(main.exists());
		ICompilationUnit mainCu = JavaCore.createCompilationUnitFrom(main);
		String mainSource = mainCu.getSource();
		String mainExpected = mainSource.replace("getName", "getName1");
		IFile file = project.getFile("src/main/java/org/sample/Test.java");
		assertTrue(file.exists());
		ICompilationUnit cu = JavaCore.createCompilationUnitFrom(file);
		Position pos = new Position(6, 23);
		String source = cu.getSource();
		String expected = source.replace("name", "name1");
		WorkspaceEdit edit = getRenameEdit(cu, pos, "name1");
		assertNotNull(edit);
		assertEquals(2, edit.getChanges().size());
		assertEquals(expected, TextEditUtil.apply(source, edit.getChanges().get(JDTUtils.toURI(cu))));
		assertEquals(mainExpected, TextEditUtil.apply(mainSource, edit.getChanges().get(JDTUtils.toURI(mainCu))));
	}

	// https://github.com/eclipse/eclipse.jdt.ls/issues/1775
	@Test
	public void testRenameTypeLombok() throws Exception {
		when(preferenceManager.getPreferences().isImportMavenEnabled()).thenReturn(true);
		importProjects("maven/mavenlombok");
		IProject project = ResourcesPlugin.getWorkspace().getRoot().getProject("mavenlombok");
		IFile file = project.getFile("src/main/java/org/sample/Test.java");
		assertTrue(file.exists());
		ICompilationUnit cu = JavaCore.createCompilationUnitFrom(file);
		Position pos = new Position(5, 15);
		String source = cu.getSource();
		String expected = source.replace("Test", "Test1");
		WorkspaceEdit edit = getRenameEdit(cu, pos, "Test1");
		assertNotNull(edit);
		assertEquals(2, edit.getChanges().size());
		assertEquals(expected, TextEditUtil.apply(source, edit.getChanges().get(JDTUtils.toURI(cu))));
	}

	// https://github.com/redhat-developer/vscode-java/issues/3203
	@Test
	public void testLombokSingular() throws Exception {
		when(preferenceManager.getPreferences().isImportMavenEnabled()).thenReturn(true);
		importProjects("maven/mavenlombok");
		IProject project = ResourcesPlugin.getWorkspace().getRoot().getProject("mavenlombok");
		IFile file = project.getFile("src/main/java/org/sample/Test2.java");
		assertTrue(file.exists());
		ICompilationUnit cu = JavaCore.createCompilationUnitFrom(file);
		Position pos = new Position(9, 18);
		String source = cu.getSource();
		String expected = source.replace("singulars", "singulars2");
		WorkspaceEdit edit = getRenameEdit(cu, pos, "singulars2");
		assertNotNull(edit);
		assertEquals(1, edit.getChanges().size());
		assertEquals(expected, TextEditUtil.apply(source, edit.getChanges().get(JDTUtils.toURI(cu))));
	}

	private WorkspaceEdit getRenameEdit(ICompilationUnit cu, Position pos, String newName) {
		TextDocumentIdentifier identifier = new TextDocumentIdentifier(JDTUtils.toURI(cu));

		RenameParams params = new RenameParams(identifier, pos, newName);
		return handler.rename(params, monitor);
	}
}
