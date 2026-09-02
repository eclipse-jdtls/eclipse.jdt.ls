/*******************************************************************************
 * Copyright (c) 2026 Jeongho Nam and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *     Jeongho Nam - initial API and implementation
 *******************************************************************************/
package org.eclipse.jdt.ls.core.internal.commands;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.ls.core.internal.WorkspaceHelper;
import org.eclipse.jdt.ls.core.internal.managers.AbstractProjectsManagerBasedTest;
import org.junit.jupiter.api.Test;

public class GraphSnapshotCommandTest extends AbstractProjectsManagerBasedTest {

	@Test
	public void exportsFrozenWorkspaceGenerationIncludingResidentChanges() throws Exception {
		importProjects("maven/salut2");
		Map<String, Object> first = GraphSnapshotCommand.execute(monitor);
		assertEquals(GraphSnapshotCommand.SCHEMA_VERSION, first.get("schemaVersion"));
		assertEquals(GraphSnapshotCommand.PROTOCOL_VERSION, first.get("protocolVersion"));
		assertFalse(rows(first, "sources").isEmpty());
		assertTrue(rows(first, "nodes").stream().anyMatch(node -> "class".equals(node.get("kind"))));
		assertTrue(rows(first, "edges").stream().allMatch(edge -> "contains".equals(edge.get("kind"))));

		Map<String, Object> unchanged = GraphSnapshotCommand.execute(monitor);
		assertEquals(first.get("generation"), unchanged.get("generation"));
		assertEquals("unchanged", unchanged.get("mode"));
		assertEquals(first.get("sequence"), unchanged.get("sequence"));

		IProject project = WorkspaceHelper.getProject("salut2");
		IFile file = project.getFile("src/main/java/foo/Bar.java");
		ICompilationUnit unit = JavaCore.createCompilationUnitFrom(file);
		unit.becomeWorkingCopy(monitor);
		try {
			unit.getBuffer().setContents(unit.getBuffer().getContents() + "\nclass ResidentOnly {}\n");
			unit.reconcile(ICompilationUnit.NO_AST, true, null, monitor);
			Map<String, Object> changed = GraphSnapshotCommand.execute(monitor);
			assertEquals("incremental", changed.get("mode"));
			assertNotEquals(first.get("generation"), changed.get("generation"));
			assertTrue(rows(changed, "nodes").stream().anyMatch(node -> "ResidentOnly".equals(node.get("name"))));
			Map<String, Object> source = rows(changed, "sources").stream()
					.filter(row -> String.valueOf(row.get("uri")).endsWith("/foo/Bar.java"))
					.findFirst().orElseThrow();
			assertNotEquals(source.get("checkerDigest"), source.get("diskDigest"));
		} finally {
			unit.discardWorkingCopy();
		}
	}

	@SuppressWarnings("unchecked")
	private static List<Map<String, Object>> rows(Map<String, Object> snapshot, String key) {
		return (List<Map<String, Object>>) snapshot.get(key);
	}
}
