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
package org.eclipse.jdt.ls.core.internal.handlers;

import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.ListIterator;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.jdt.ls.core.internal.ClassFileUtil;
import org.eclipse.jdt.ls.core.internal.WorkspaceHelper;
import org.eclipse.jdt.ls.core.internal.managers.AbstractProjectsManagerBasedTest;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.SelectionRange;
import org.eclipse.lsp4j.SelectionRangeParams;
import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.junit.Before;
import org.junit.Test;

import com.google.common.collect.Lists;

public class SelectionRangeHandlerTest extends AbstractProjectsManagerBasedTest {

	private IProject project;
	private static Range TYPE_DECL_RANGE = new Range(new Position(2, 0), new Position(31, 1));
	private static Range COMP_UNIT_RAGE = new Range(new Position(0, 0), new Position(32, 0));

	@Before
	public void setup() throws Exception {
		importProjects(Arrays.asList("maven/salut"));
		project = WorkspaceHelper.getProject("salut");
	}

	@Test
	public void testJavadoc() throws CoreException {
		SelectionRange range = getSelectionRange("org.sample.Foo4", new Position(9, 31));
		assertTrue(validateSelectionRange(range, new Range(new Position(9, 4), new Position(9, 40)), // text element
				new Range(new Position(9, 4), new Position(9, 40)), // tag element
				new Range(new Position(8, 1), new Position(10, 4)), // javadoc
				new Range(new Position(8, 1), new Position(16, 2)), // method declaration
				TYPE_DECL_RANGE, COMP_UNIT_RAGE));
	}

	@Test
	public void testComments() throws CoreException {
		// line comment
		SelectionRange range = getSelectionRange("org.sample.Foo4", new Position(12, 57));
		assertTrue(validateSelectionRange(range, new Range(new Position(12, 43), new Position(12, 66)), // line comment
				new Range(new Position(11, 8), new Position(16, 2)), // block
				new Range(new Position(8, 1), new Position(16, 2)), // method declaration
				TYPE_DECL_RANGE, COMP_UNIT_RAGE));

		// block comment
		range = getSelectionRange("org.sample.Foo4", new Position(14, 17));
		assertTrue(validateSelectionRange(range, new Range(new Position(14, 2), new Position(14, 29)), // block comment
				new Range(new Position(11, 8), new Position(16, 2)), // block
				new Range(new Position(8, 1), new Position(16, 2)), // method declaration
				TYPE_DECL_RANGE, COMP_UNIT_RAGE));

		// block comment in param list
		range = getSelectionRange("org.sample.Foo4", new Position(18, 42));
		assertTrue(validateSelectionRange(range, new Range(new Position(18, 27), new Position(18, 68)), // block comment
				new Range(new Position(18, 1), new Position(30, 2)), // method declaration
				TYPE_DECL_RANGE, COMP_UNIT_RAGE));
	}

	@Test
	public void testStringLiteral() throws CoreException {
		SelectionRange range = getSelectionRange("org.sample.Foo4", new Position(12, 30));
		assertTrue(validateSelectionRange(range, new Range(new Position(12, 21), new Position(12, 40)), // string literal
				new Range(new Position(12, 2), new Position(12, 41)), // method invocation
				new Range(new Position(12, 2), new Position(12, 42)), // expression statement
				new Range(new Position(11, 8), new Position(16, 2)), // block
				new Range(new Position(8, 1), new Position(16, 2)), // method declaration
				TYPE_DECL_RANGE, COMP_UNIT_RAGE));
	}

	@Test
	public void testParamList() throws CoreException {
		SelectionRange range = getSelectionRange("org.sample.Foo4", new Position(18, 24));
		assertTrue(validateSelectionRange(range, new Range(new Position(18, 21), new Position(18, 27)), // simple name
				new Range(new Position(18, 17), new Position(18, 27)), // single variable declaration
				new Range(new Position(18, 1), new Position(30, 2)), // method declaration
				TYPE_DECL_RANGE, COMP_UNIT_RAGE));

	}

	@Test
	public void testSwitch() throws CoreException {
		SelectionRange range = getSelectionRange("org.sample.Foo4", new Position(22, 27));
		assertTrue(validateSelectionRange(range, new Range(new Position(22, 24), new Position(22, 30)), // simple name
				new Range(new Position(22, 5), new Position(22, 31)), // method invocation
				new Range(new Position(22, 5), new Position(22, 32)), // expression statement
				new Range(new Position(20, 3), new Position(26, 4)), // switch statement
				new Range(new Position(19, 6), new Position(27, 3)), // block
				new Range(new Position(19, 2), new Position(29, 3)), // try statement
				new Range(new Position(18, 85), new Position(30, 2)), // block
				new Range(new Position(18, 1), new Position(30, 2)), // method declaration
				TYPE_DECL_RANGE, COMP_UNIT_RAGE));
	}

	private SelectionRange getSelectionRange(String className, Position position) throws CoreException {
		SelectionRangeParams params = new SelectionRangeParams();
		params.setPositions(Lists.newArrayList(position));
		params.setTextDocument(new TextDocumentIdentifier(ClassFileUtil.getURI(project, className)));
		return new SelectionRangeHandler().selectionRange(params, monitor).get(0);
	}

	private boolean validateSelectionRange(SelectionRange range, Range... ranges) {
		ListIterator<Range> iterator = Arrays.asList(ranges).listIterator();
		while (range != null && iterator.hasNext()) {
			if (!range.getRange().equals(iterator.next())) {
				return false;
			}

			range = range.getParent();
		}

		if (range != null || iterator.hasNext()) {
			return false;
		}

		return true;
	}
}
