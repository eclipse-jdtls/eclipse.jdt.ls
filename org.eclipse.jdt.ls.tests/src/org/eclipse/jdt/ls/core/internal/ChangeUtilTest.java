/*******************************************************************************
 * Copyright (c) 2019 Microsoft Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *     Microsoft Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.jdt.ls.core.internal;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.when;

import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IPackageFragment;
import org.eclipse.jdt.core.IPackageFragmentRoot;
import org.eclipse.jdt.core.refactoring.CompilationUnitChange;
import org.eclipse.jdt.ls.core.internal.corext.refactoring.changes.RenameCompilationUnitChange;
import org.eclipse.jdt.ls.core.internal.managers.AbstractProjectsManagerBasedTest;
import org.eclipse.jdt.ls.core.internal.preferences.ClientPreferences;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.RenameFile;
import org.eclipse.lsp4j.ResourceOperation;
import org.eclipse.lsp4j.TextDocumentEdit;
import org.eclipse.lsp4j.TextEdit;
import org.eclipse.lsp4j.VersionedTextDocumentIdentifier;
import org.eclipse.lsp4j.WorkspaceEdit;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.eclipse.ltk.core.refactoring.CompositeChange;
import org.eclipse.text.edits.InsertEdit;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.runners.MockitoJUnitRunner;

/**
 * @author Yan Zhang
 *
 */

@RunWith(MockitoJUnitRunner.class)
public class ChangeUtilTest extends AbstractProjectsManagerBasedTest {

	private ClientPreferences clientPreferences;

	private IPackageFragmentRoot sourceFolder;

	@Before
	public void setup() throws Exception {
		IJavaProject javaProject = newEmptyProject();
		sourceFolder = javaProject.getPackageFragmentRoot(javaProject.getProject().getFolder("src"));
		clientPreferences = preferenceManager.getClientPreferences();
		when(clientPreferences.isResourceOperationSupported()).thenReturn(true);
	}

	// Text Changes
	@Test
	public void testConvertCompilationUnitChange() throws CoreException {
		IPackageFragment pack1 = sourceFolder.createPackageFragment("test1", false, null);
		ICompilationUnit cu = pack1.createCompilationUnit("E.java", "", false, null);
		CompilationUnitChange change = new CompilationUnitChange("insertText", cu);
		String newText = "// some content";
		change.setEdit(new InsertEdit(0, newText));

		WorkspaceEdit edit = ChangeUtil.convertToWorkspaceEdit(change);

		assertEquals(edit.getDocumentChanges().size(), 1);

		TextDocumentEdit textDocumentEdit = edit.getDocumentChanges().get(0).getLeft();
		assertNotNull(textDocumentEdit);
		assertEquals(textDocumentEdit.getEdits().size(), 1);

		TextEdit textEdit = textDocumentEdit.getEdits().get(0);
		assertEquals(textEdit.getNewText(), newText);
	}

	// Resource Changes
	@Test
	public void testConvertRenameCompilationUnitChange() throws CoreException {
		IPackageFragment pack1 = sourceFolder.createPackageFragment("test1", false, null);
		ICompilationUnit cu = pack1.createCompilationUnit("E.java", "", false, null);
		String newName = "ENew.java";
		RenameCompilationUnitChange change = new RenameCompilationUnitChange(cu, newName);
		String oldUri = JDTUtils.toURI(cu);
		String newUri = ResourceUtils.fixURI(URI.create(oldUri).resolve(newName));

		WorkspaceEdit edit = ChangeUtil.convertToWorkspaceEdit(change);

		assertEquals(edit.getDocumentChanges().size(), 1);

		ResourceOperation resourceOperation = edit.getDocumentChanges().get(0).getRight();
		assertTrue(resourceOperation instanceof RenameFile);

		assertEquals(((RenameFile) resourceOperation).getOldUri(), oldUri);
		assertEquals(((RenameFile) resourceOperation).getNewUri(), newUri);
	}

	//Composite Changes
	@Test
	public void testConvertSimpleCompositeChange() throws CoreException {
		IPackageFragment pack1 = sourceFolder.createPackageFragment("test1", false, null);
		ICompilationUnit cu = pack1.createCompilationUnit("E.java", "", false, null);
		CompositeChange change = new CompositeChange("simple composite change");

		RenameCompilationUnitChange resourceChange = new RenameCompilationUnitChange(cu, "ENew.java");
		change.add(resourceChange);
		CompilationUnitChange textChange = new CompilationUnitChange("insertText", cu);
		textChange.setEdit(new InsertEdit(0, "// some content"));
		change.add(textChange);

		WorkspaceEdit edit = ChangeUtil.convertToWorkspaceEdit(change);
		assertEquals(edit.getDocumentChanges().size(), 2);
		assertTrue(edit.getDocumentChanges().get(0).getRight() instanceof RenameFile);
		assertTrue(edit.getDocumentChanges().get(1).getLeft() instanceof TextDocumentEdit);
	}

	@Test
	public void testMergeChanges() {
		WorkspaceEdit editA = new WorkspaceEdit();
		String uriA = "uriA";
		TextEdit textEdit = new TextEdit(
			new Range(new Position(0, 0), new Position(0, 0)),
			"package test;");
		editA.getChanges().put(uriA, Arrays.asList(textEdit));

		WorkspaceEdit root = ChangeUtil.mergeChanges(null, editA);
		assertNotNull(root);
		assertNotNull(root.getChanges());
		assertNotNull(root.getChanges().get(uriA));
		assertEquals(1, root.getChanges().size());
		assertEquals(1, root.getChanges().get(uriA).size());

		WorkspaceEdit editB = new WorkspaceEdit();
		editB.getChanges().put(uriA, Arrays.asList(textEdit));
		List<Either<TextDocumentEdit, ResourceOperation>> documentChanges = new ArrayList<>();
		TextDocumentEdit textDocumentEdit = new TextDocumentEdit(
			new VersionedTextDocumentIdentifier(uriA, 1), Arrays.asList(textEdit));
		documentChanges.add(Either.forLeft(textDocumentEdit));
		ResourceOperation resourceOperation = new RenameFile("uriA", "uriB");
		documentChanges.add(Either.forRight(resourceOperation));
		editB.setDocumentChanges(documentChanges);

		root = ChangeUtil.mergeChanges(editA, editB);
		assertNotNull(root);
		assertNotNull(root.getChanges());
		assertNotNull(root.getChanges().get(uriA));
		assertEquals(1, root.getChanges().size());
		assertEquals(2, root.getChanges().get(uriA).size());
		assertNotNull(root.getDocumentChanges());
		assertEquals(2, root.getDocumentChanges().size());
		assertTrue(root.getDocumentChanges().get(0).isLeft());
		assertEquals(textDocumentEdit, root.getDocumentChanges().get(0).getLeft());
		assertTrue(root.getDocumentChanges().get(1).isRight());
		assertEquals(resourceOperation, root.getDocumentChanges().get(1).getRight());

		root = ChangeUtil.mergeChanges(editA, editB, true);
		assertNotNull(root);
		assertNotNull(root.getChanges());
		assertNotNull(root.getChanges().get(uriA));
		assertEquals(1, root.getChanges().size());
		assertEquals(2, root.getChanges().get(uriA).size());
		assertNotNull(root.getDocumentChanges());
		assertEquals(1, root.getDocumentChanges().size());
		assertTrue(root.getDocumentChanges().get(0).isLeft());
		assertEquals(textDocumentEdit, root.getDocumentChanges().get(0).getLeft());
	}
}
