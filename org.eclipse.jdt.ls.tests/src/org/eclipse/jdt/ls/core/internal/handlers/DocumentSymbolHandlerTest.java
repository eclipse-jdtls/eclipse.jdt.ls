/*******************************************************************************
 * Copyright (c) 2016-2017 Red Hat Inc. and others.
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
package org.eclipse.jdt.ls.core.internal.handlers;

import static java.util.stream.Collectors.toList;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.when;

import java.io.UnsupportedEncodingException;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.ls.core.internal.ClassFileUtil;
import org.eclipse.jdt.ls.core.internal.WorkspaceHelper;
import org.eclipse.jdt.ls.core.internal.managers.AbstractProjectsManagerBasedTest;
import org.eclipse.lsp4j.DocumentSymbol;
import org.eclipse.lsp4j.DocumentSymbolParams;
import org.eclipse.lsp4j.Location;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.SymbolInformation;
import org.eclipse.lsp4j.SymbolKind;
import org.eclipse.lsp4j.SymbolTag;
import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.junit.Before;
import org.junit.Test;

import com.google.common.collect.TreeTraverser;

/**
 * @author snjeza
 */
public class DocumentSymbolHandlerTest extends AbstractProjectsManagerBasedTest {

	private IProject project;
	private IProject noSourceProject;

	@Before
	public void setup() throws Exception {
		importProjects(Arrays.asList("maven/salut", "eclipse/source-attachment"));
		project = WorkspaceHelper.getProject("salut");
		noSourceProject = WorkspaceHelper.getProject("source-attachment");
	}

	@Test
	public void testDocumentSymbolHandler() throws Exception {
		testClass("org.apache.commons.lang3.text.WordUtils", false);
	}

	@Test
	public void testDocumentSymbolHandler_hierarchical() throws Exception {
		testClass("org.apache.commons.lang3.text.WordUtils", true);
	}

	@Test
	public void testSyntheticMember() throws Exception {
		String className = "org.apache.commons.lang3.text.StrTokenizer";
		List<? extends SymbolInformation> symbols = getSymbols(className);
		boolean overloadedMethod1Found = false;
		boolean overloadedMethod2Found = false;
		String overloadedMethod1 = "getCSVInstance(String)";
		String overloadedMethod2 = "reset()";
		for (SymbolInformation symbol : symbols) {
			Location loc = symbol.getLocation();
			assertTrue("Class: " + className + ", Symbol:" + symbol.getName() + " - invalid location.",
					loc != null && isValid(loc.getRange()));
			assertFalse("Class: " + className + ", Symbol:" + symbol.getName() + " - invalid name",
					symbol.getName().startsWith("access$"));
			assertFalse("Class: " + className + ", Symbol:" + symbol.getName() + "- invalid name",
					symbol.getName().equals("<clinit>"));
			if (overloadedMethod1.equals(symbol.getName())) {
				overloadedMethod1Found = true;
			}
			if (overloadedMethod2.equals(symbol.getName())) {
				overloadedMethod2Found = true;
			}
		}
		assertTrue("The " + overloadedMethod1 + " method hasn't been found", overloadedMethod1Found);
		assertTrue("The " + overloadedMethod2 + " method hasn't been found", overloadedMethod2Found);
	}

	@Test
	public void testSyntheticMember_hierarchical() throws Exception {
		String className = "org.apache.commons.lang3.text.StrTokenizer";
		List<? extends DocumentSymbol> symbols = asStream(getHierarchicalSymbols(className)).collect(Collectors.toList());
		boolean overloadedMethod1Found = false;
		boolean overloadedMethod2Found = false;
		String overloadedMethod1 = "getCSVInstance(String) : StrTokenizer";
		String overloadedMethod2 = "reset() : StrTokenizer";
		for (DocumentSymbol symbol : symbols) {
			Range fullRange = symbol.getRange();
			Range selectionRange = symbol.getSelectionRange();
			assertTrue("Class: " + className + ", Symbol:" + symbol.getName() + " - invalid location.",
					fullRange != null && isValid(fullRange) && selectionRange != null && isValid(selectionRange));
			assertFalse("Class: " + className + ", Symbol:" + symbol.getName() + " - invalid name",
					symbol.getName().startsWith("access$"));
			assertFalse("Class: " + className + ", Symbol:" + symbol.getName() + "- invalid name",
					symbol.getName().equals("<clinit>"));
			if (overloadedMethod1.equals(symbol.getName() + symbol.getDetail())) {
				overloadedMethod1Found = true;
			}
			if (overloadedMethod2.equals(symbol.getName() + symbol.getDetail())) {
				overloadedMethod2Found = true;
			}
		}
		assertTrue("The " + overloadedMethod1 + " method hasn't been found", overloadedMethod1Found);
		assertTrue("The " + overloadedMethod2 + " method hasn't been found", overloadedMethod2Found);
	}

	@Test
	public void testTypes() throws Exception {
		String className = "org.sample.Bar";
		List<? extends SymbolInformation> symbols = getSymbols(className);
		assertHasSymbol("Bar", "Bar.java", SymbolKind.Class, symbols);
		assertHasSymbol("main(String[])", "Bar", SymbolKind.Method, symbols);
		assertHasSymbol("MyInterface", "Bar", SymbolKind.Interface, symbols);
		assertHasSymbol("foo()", "MyInterface", SymbolKind.Method, symbols);
		assertHasSymbol("MyClass", "Bar", SymbolKind.Class, symbols);
		assertHasSymbol("bar()", "MyClass", SymbolKind.Method, symbols);
		assertHasSymbol("Foo", "Bar", SymbolKind.Enum, symbols);
		assertHasSymbol("Bar", "Foo", SymbolKind.EnumMember, symbols);
		assertHasSymbol("Zoo", "Foo", SymbolKind.EnumMember, symbols);
		assertHasSymbol("EMPTY", "Bar", SymbolKind.Constant, symbols);

	}

	@Test
	public void testTypes_hierarchical() throws Exception {
		String className = "org.sample.Bar";
		List<? extends DocumentSymbol> symbols = getHierarchicalSymbols(className);
		assertHasHierarchicalSymbol("main(String[]) : void", "Bar", SymbolKind.Method, symbols);
		assertHasHierarchicalSymbol("MyInterface", "Bar", SymbolKind.Interface, symbols);
		assertHasHierarchicalSymbol("foo() : void", "MyInterface", SymbolKind.Method, symbols);
		assertHasHierarchicalSymbol("MyClass", "Bar", SymbolKind.Class, symbols);
		assertHasHierarchicalSymbol("bar() : void", "MyClass", SymbolKind.Method, symbols);
	}

	@Test
	public void testSyntheticMember_hierarchical_noSourceAttached() throws Exception {
		String className = "foo.bar";
		List<? extends DocumentSymbol> symbols = asStream(internalGetHierarchicalSymbols(noSourceProject, monitor, className)).collect(Collectors.toList());
		assertHasHierarchicalSymbol("bar()", "bar", SymbolKind.Constructor, symbols);
		assertHasHierarchicalSymbol("add(int...) : int", "bar", SymbolKind.Method, symbols);
	}

	@Test
	public void testDeprecated() throws Exception {
		when(preferenceManager.getClientPreferences().isSymbolTagSupported()).thenReturn(true);

		String className = "org.sample.Bar";
		List<? extends SymbolInformation> symbols = getSymbols(className);

		SymbolInformation deprecated = symbols.stream()
			.filter(symbol -> symbol.getName().equals("MyInterface"))
			.findFirst().orElse(null);

		assertNotNull(deprecated);
		assertEquals(SymbolKind.Interface, deprecated.getKind());
		assertNotNull(deprecated.getTags());
		assertTrue("Should have deprecated tag", deprecated.getTags().contains(SymbolTag.Deprecated));

		SymbolInformation notDeprecated = symbols.stream()
			.filter(symbol -> symbol.getName().equals("MyClass"))
			.findFirst().orElse(null);

		assertNotNull(notDeprecated);
		assertEquals(SymbolKind.Class, notDeprecated.getKind());
		if (notDeprecated.getTags() != null) {
			assertFalse("Should not have deprecated tag", deprecated.getTags().contains(SymbolTag.Deprecated));
		}
	}

	@Test
	public void testDeprecated_property() throws Exception {
		when(preferenceManager.getClientPreferences().isSymbolTagSupported()).thenReturn(false);

		String className = "org.sample.Bar";
		List<? extends SymbolInformation> symbols = getSymbols(className);

		SymbolInformation deprecated = symbols.stream()
			.filter(symbol -> symbol.getName().equals("MyInterface"))
			.findFirst().orElse(null);

		assertNotNull(deprecated);
		assertEquals(SymbolKind.Interface, deprecated.getKind());
		assertNotNull(deprecated.getDeprecated());
		assertTrue("Should be deprecated", deprecated.getDeprecated());
	}

	@Test
	public void testLombok() throws Exception {
		boolean lombokDisabled = "true".equals(System.getProperty("jdt.ls.lombok.disabled"));
		if (lombokDisabled) {
			return;
		}
		importProjects("maven/mavenlombok");
		project = ResourcesPlugin.getWorkspace().getRoot().getProject("mavenlombok");
		String className = "org.sample.Test";
		List<? extends SymbolInformation> symbols = getSymbols(className);
		//@formatter:on
		assertFalse("No symbols found for " + className, symbols.isEmpty());
		assertHasSymbol("Test", "Test.java", SymbolKind.Class, symbols);
		Optional<? extends SymbolInformation> method = symbols.stream().filter(s -> (s.getKind() == SymbolKind.Method)).findAny();
		assertFalse(method.isPresent());
	}

	private List<? extends DocumentSymbol> internalGetHierarchicalSymbols(IProject project, IProgressMonitor monitor, String className)
			throws JavaModelException, UnsupportedEncodingException, InterruptedException, ExecutionException {
		String uri = ClassFileUtil.getURI(project, className);
		TextDocumentIdentifier identifier = new TextDocumentIdentifier(uri);
		DocumentSymbolParams params = new DocumentSymbolParams();
		params.setTextDocument(identifier);
		when(preferenceManager.getClientPreferences().isHierarchicalDocumentSymbolSupported()).thenReturn(true);
		//@formatter:off
		List<DocumentSymbol> symbols = new DocumentSymbolHandler(preferenceManager)
				.documentSymbol(params, monitor).stream()
				.map(Either::getRight).collect(toList());
		//@formatter:on
		assertTrue(symbols.size() > 0);
		return symbols;
	}

	private void assertHasSymbol(String expectedType, String expectedParent, SymbolKind expectedKind, Collection<? extends SymbolInformation> symbols) {
		Optional<? extends SymbolInformation> symbol = symbols.stream()
															.filter(s -> expectedType.equals(s.getName()) && expectedParent.equals(s.getContainerName()))
															.findFirst();
		assertTrue(expectedType + " (" + expectedParent + ")" + " is missing from " + symbols, symbol.isPresent());
		assertKind(expectedKind, symbol.get());
	}

	private void assertHasHierarchicalSymbol(String expectedType, String expectedParent, SymbolKind expectedKind, Collection<? extends DocumentSymbol> symbols) {
		Optional<? extends DocumentSymbol> parent = asStream(symbols).filter(s -> expectedParent.equals(s.getName() + s.getDetail())).findFirst();
		assertTrue("Cannot find parent with name: " + expectedParent, parent.isPresent());
		Optional<? extends DocumentSymbol> symbol = asStream(symbols)
															.filter(s -> expectedType.equals(s.getName() + s.getDetail()) && parent.get().getChildren().contains(s))
															.findFirst();
		assertTrue(expectedType + " (" + expectedParent + ")" + " is missing from " + symbols, symbol.isPresent());
		assertKind(expectedKind, symbol.get());
	}

	private void assertKind(SymbolKind expectedKind, SymbolInformation symbol) {
		assertSame("Unexpected SymbolKind in " + symbol.getName(), expectedKind, symbol.getKind());
	}

	private void assertKind(SymbolKind expectedKind, DocumentSymbol symbol) {
		assertSame("Unexpected SymbolKind in " + symbol.getName(), expectedKind, symbol.getKind());
	}

	private Stream<DocumentSymbol> asStream(Collection<? extends DocumentSymbol> symbols) {
		//@formatter:off
		return symbols.stream()
				.map(s -> TreeTraverser.<DocumentSymbol>using(ds -> ds.getChildren() == null
					? Collections.<DocumentSymbol>emptyList()
					: ds.getChildren()).breadthFirstTraversal(s).toList())
				.flatMap(List::stream);
		//@formatter:on
	}

	private void testClass(String className, boolean hierarchical)
			throws JavaModelException, UnsupportedEncodingException, InterruptedException, ExecutionException {

		if (!hierarchical) {
			List<? extends SymbolInformation> symbols = getSymbols(className);
			for (SymbolInformation symbol : symbols) {
				Location loc = symbol.getLocation();
				assertTrue("Class: " + className + ", Symbol:" + symbol.getName() + " - invalid location.",
						loc != null && isValid(loc.getRange()));
			}
		} else {
			List<? extends DocumentSymbol> hierarchicalSymbols = getHierarchicalSymbols(className);
			for (DocumentSymbol symbol : hierarchicalSymbols) {
				Range fullRange = symbol.getRange();
				Range selectionRange = symbol.getSelectionRange();
				assertTrue("Class: " + className + ", Symbol:" + symbol.getName() + " - invalid location.",
						fullRange != null && isValid(fullRange) && selectionRange != null && isValid(selectionRange));
			}
		}
	}

	private List<? extends SymbolInformation> getSymbols(String className)
			throws JavaModelException, UnsupportedEncodingException, InterruptedException, ExecutionException {
		String uri = ClassFileUtil.getURI(project, className);
		TextDocumentIdentifier identifier = new TextDocumentIdentifier(uri);
		DocumentSymbolParams params = new DocumentSymbolParams();
		params.setTextDocument(identifier);
		when(preferenceManager.getClientPreferences().isHierarchicalDocumentSymbolSupported()).thenReturn(false);
		//@formatter:off
		List<SymbolInformation> symbols = new DocumentSymbolHandler(preferenceManager)
				.documentSymbol(params, monitor).stream()
				.map(Either::getLeft).collect(toList());
		//@formatter:on
		assertFalse("No symbols found for " + className, symbols.isEmpty());
		return symbols;
	}

	private List<? extends DocumentSymbol> getHierarchicalSymbols(String className) throws JavaModelException, UnsupportedEncodingException, InterruptedException, ExecutionException {
		return internalGetHierarchicalSymbols(project, monitor, className);
	}


	private boolean isValid(Range range) {
		return range != null && isValid(range.getStart()) && isValid(range.getEnd());
	}

	private boolean isValid(Position position) {
		return position != null && position.getLine() >= 0 && position.getCharacter() >= 0;
	}


}
