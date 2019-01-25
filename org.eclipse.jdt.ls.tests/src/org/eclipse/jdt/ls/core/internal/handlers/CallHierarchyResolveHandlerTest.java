/*******************************************************************************
 * Copyright (c) 2019 TypeFox and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     TypeFox - initial API and implementation
 *******************************************************************************/
package org.eclipse.jdt.ls.core.internal.handlers;

import static java.util.Arrays.asList;
import static org.eclipse.jdt.ls.core.internal.handlers.CallHierarchyHandlerTest.assertItem;
import static org.eclipse.jdt.ls.core.internal.handlers.CallHierarchyHandlerTest.getIncomings;
import static org.eclipse.jdt.ls.core.internal.handlers.CallHierarchyHandlerTest.getUriFromSrcProject;
import static org.eclipse.lsp4j.SymbolKind.Constructor;
import static org.eclipse.lsp4j.SymbolKind.Method;
import static org.junit.Assert.assertNull;

import java.util.Arrays;
import java.util.List;

import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.jdt.ls.core.internal.managers.AbstractProjectsManagerBasedTest;
import org.eclipse.lsp4j.CallHierarchyDirection;
import org.eclipse.lsp4j.CallHierarchyItem;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.ResolveCallHierarchyItemParams;
import org.junit.Before;
import org.junit.Test;

/**
 * @see CallHierarchyResolveHandler
 */
public class CallHierarchyResolveHandlerTest extends AbstractProjectsManagerBasedTest {

	@Before
	public void setup() throws Exception {
		importProjects(Arrays.asList("eclipse/hello", "maven/salut"));
	}

	@Test
	public void cannot_resolve() {
		CallHierarchyItem invalid = new CallHierarchyItem();
		invalid.setUri("file:///missing/Resource.java");
		invalid.setRange(new Range(new Position(0, 0), new Position(0, 0)));
		CallHierarchyItem item = resolveIncoming(invalid, 1);
		assertNull(item);
	}

	@Test
	public void resolve_incoming() throws Exception {
		// Line 26 from `CallHierarchy`
		//    public void <|>bar() {
		String uri = getUriFromSrcProject("org.sample.CallHierarchy");
		CallHierarchyItem item = getIncomings(uri, 25, 16, 0);
		assertItem(item, "bar()", Method, "void", null, false, null);

		item = resolveIncoming(item, 1);
		assertItem(item, "bar()", Method, "void", 3, false, null);

		List<CallHierarchyItem> calls = item.getCalls();
		assertItem(calls.get(0), "Child()", Constructor, "", null, false, asList(45));
		assertItem(calls.get(1), "main(String[])", Method, "void", null, true, asList(7));
		assertItem(calls.get(2), "method_1()", Method, "void", null, false, asList(35));
	}

	private static CallHierarchyItem resolveIncoming(CallHierarchyItem toResolve, int resolve) {
		ResolveCallHierarchyItemParams params = new ResolveCallHierarchyItemParams();
		params.setDirection(CallHierarchyDirection.Incoming);
		params.setItem(toResolve);
		params.setResolve(resolve);
		return new CallHierarchyResolveHandler().resolve(params, new NullProgressMonitor());
	}

}
