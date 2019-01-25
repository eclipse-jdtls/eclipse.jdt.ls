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
import static java.util.stream.Collectors.toList;
import static org.eclipse.lsp4j.CallHierarchyDirection.Incoming;
import static org.eclipse.lsp4j.CallHierarchyDirection.Outgoing;
import static org.eclipse.lsp4j.SymbolKind.Class;
import static org.eclipse.lsp4j.SymbolKind.Constructor;
import static org.eclipse.lsp4j.SymbolKind.Field;
import static org.eclipse.lsp4j.SymbolKind.Method;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.List;

import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.ls.core.internal.ClassFileUtil;
import org.eclipse.jdt.ls.core.internal.WorkspaceHelper;
import org.eclipse.jdt.ls.core.internal.hover.JavaElementLabels;
import org.eclipse.jdt.ls.core.internal.managers.AbstractProjectsManagerBasedTest;
import org.eclipse.lsp4j.CallHierarchyDirection;
import org.eclipse.lsp4j.CallHierarchyItem;
import org.eclipse.lsp4j.CallHierarchyParams;
import org.eclipse.lsp4j.Location;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.SymbolKind;
import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.junit.Before;
import org.junit.Test;

/**
 * @see CallHierarchyHandler
 */
public class CallHierarchyHandlerTest extends AbstractProjectsManagerBasedTest {

	@Before
	public void setup() throws Exception {
		importProjects(Arrays.asList("eclipse/hello", "maven/salut"));
	}

	@Test
	public void incoming_src_missing() throws Exception {
		// Line 16 from `CallHierarchy`
		//<|>/*nothing*/
		String uri = getUriFromSrcProject("org.sample.CallHierarchy");
		assertNull(getIncomings(uri, 13, 0, 1));
	}

	@Test
	public void incoming_src_resolve_0() throws Exception {
		// Line 15 from `CallHierarchy`
		//    protected int <|>protectedField = 200;
		String uri = getUriFromSrcProject("org.sample.CallHierarchy");
		CallHierarchyItem item = getIncomings(uri, 14, 18, 0);
		assertItem(item, "protectedField", Field, "", null, false, null);
	}

	@Test
	public void incoming_src_resolve_1() throws Exception {
		// Line 26 from `CallHierarchy`
		//    public void <|>bar() {
		String uri = getUriFromSrcProject("org.sample.CallHierarchy");
		CallHierarchyItem item = getIncomings(uri, 25, 16, 1);
		assertItem(item, "bar()", Method, "void", 3, false, null);

		List<CallHierarchyItem> calls = item.getCalls();
		assertItem(calls.get(0), "Child()", Constructor, "", null, false, asList(45));
		assertItem(calls.get(1), "main(String[])", Method, "void", null, true, asList(7));
		assertItem(calls.get(2), "method_1()", Method, "void", null, false, asList(35));
	}

	@Test
	public void outgoing_src_resolve_0_enclosing() throws Exception {
		// Line 20 from `CallHierarchy`
		///*should resolve to enclosing "method/constructor/initializer"*/
		String uri = getUriFromSrcProject("org.sample.CallHierarchy");
		CallHierarchyItem item = getIncomings(uri, 19, 0, 0);
		assertItem(item, "Base()", Constructor, "", null, false, null);
	}

	@Test
	public void outgoing_src_resolve_2() throws Exception {
		// Line 34 from `CallHierarchy`
		//    protected void <|>method_1() {
		String uri = getUriFromSrcProject("org.sample.CallHierarchy");
		CallHierarchyItem item = getOutgoings(uri, 33, 19, 2);
		assertItem(item, "method_1()", Method, "void", 2, false, null);

		List<CallHierarchyItem> calls = item.getCalls();
		assertItem(calls.get(0), "foo()", Method, "void", 0, false, asList(34));
		assertItem(calls.get(1), "bar()", Method, "void", 2, false, asList(35));

		List<CallHierarchyItem> call1Calls = calls.get(1).getCalls();
		assertItem(call1Calls.get(0), "Child()", Constructor, "", null, false, asList(27, 28));
		assertItem(call1Calls.get(1), "currentThread()", Method, "Thread", null, false, asList(29, 30));
	}

	@Test
	public void incoming_jar_resolve_2() throws Exception {
		// Line 12 from `CallHierarchyOther`
		//  @Deprecated public static class <|>X {
		String uri = getUriFromJarProject("org.sample.CallHierarchyOther");
		CallHierarchyItem item = getIncomings(uri, 11, 34, 2);
		assertItem(item, "X", Class, "", 1, true, null);

		List<CallHierarchyItem> calls = item.getCalls();
		assertItem(calls.get(0), "FooBuilder()", Constructor, "", 1, false, asList(10));

		List<CallHierarchyItem> call0Calls = calls.get(0).getCalls();
		assertItem(call0Calls.get(0), "{...}", Constructor, "", null, false, asList(5, 6, 7));
	}

	@Test
	public void outgoing_jar_resolve_2() throws Exception {
		// Line 15 from `CallHierarchy`
		//    public Object <|>build() {
		String uri = getUriFromJarProject("org.sample.CallHierarchy");
		CallHierarchyItem item = getOutgoings(uri, 14, 18, 2);
		assertItem(item, "build()", Method, "Object", 1, false, null);

		List<CallHierarchyItem> calls = item.getCalls();
		assertItem(calls.get(0), "capitalize(String)", Method, "String", 1, false, asList(15, 16));

		List<CallHierarchyItem> call0Calls = calls.get(0).getCalls();
		assertItem(call0Calls.get(0), "capitalize(String, char...)", Method, "String", null, false, asList(369));

		String jarUri = call0Calls.get(0).getUri();
		assertTrue(jarUri.startsWith("jdt://"));
		assertTrue(jarUri.contains("org.apache.commons.lang3.text"));
		assertTrue(jarUri.contains("WordUtils.class"));
	}

	/**
	 * @param item
	 *            to assert
	 * @param name
	 *            the expected name
	 * @param kind
	 *            the expected kind
	 * @param detail
	 *            the expected detail <b>without</b> the ` : ` (declaration) suffix.
	 * @param calls
	 *            the number of calls or {@code null} if expected non-defined calls.
	 * @param deprecated
	 *            expected deprecated state
	 * @param callLocationStartLines
	 *            {@code null}, if there are no call locations. Otherwise a list of
	 *            expected start lines for the call locations
	 * @return the {@code item}
	 */
	static CallHierarchyItem assertItem(CallHierarchyItem item, String name, SymbolKind kind, String detail, Integer calls, boolean deprecated, List<Integer> callLocationStartLines) {
		assertNotNull(item);
		assertEquals(name, item.getName());
		assertEquals(kind, item.getKind());
		if (detail.isEmpty()) {
			assertEquals(detail, item.getDetail());
		} else {
			assertEquals(JavaElementLabels.DECL_STRING + detail, item.getDetail());
		}
		if (calls == null) {
			assertNull(item.getCalls());
		} else {
			assertEquals(calls.intValue(), item.getCalls().size());
		}
		assertEquals(deprecated, item.getDeprecated());
		if (callLocationStartLines == null) {
			assertNull(item.getCallLocations());
		} else {
			List<Location> callLocations = item.getCallLocations();
			assertEquals(callLocationStartLines.size(), callLocations.size());
			List<Integer> actualStartLines = callLocations.stream().map(loc -> loc.getRange().getStart().getLine()).collect(toList());
			assertEquals(callLocationStartLines, actualStartLines);
		}
		return item;
	}

	static CallHierarchyItem getIncomings(String uri, int line, int character, int resolve) {
		CallHierarchyParams params = createParams(uri, line, character, Incoming, resolve);
		return new CallHierarchyHandler().callHierarchy(params, new NullProgressMonitor());
	}

	static CallHierarchyItem getOutgoings(String uri, int line, int character, int resolve) {
		CallHierarchyParams params = createParams(uri, line, character, Outgoing, resolve);
		return new CallHierarchyHandler().callHierarchy(params, new NullProgressMonitor());
	}

	static CallHierarchyParams createParams(String uri, int line, int character, CallHierarchyDirection direction, int resolve) {
		CallHierarchyParams params = new CallHierarchyParams();
		params.setTextDocument(new TextDocumentIdentifier(uri));
		params.setPosition(new Position(line, character));
		params.setDirection(direction);
		params.setResolve(resolve);
		return params;
	}

	static String getUriFromJarProject(String className) throws JavaModelException {
		return ClassFileUtil.getURI(WorkspaceHelper.getProject("salut"), className);
	}

	static String getUriFromSrcProject(String className) throws JavaModelException {
		return ClassFileUtil.getURI(WorkspaceHelper.getProject("hello"), className);
	}

}
