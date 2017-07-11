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

import static org.eclipse.jdt.ls.core.internal.JsonMessageHelper.getParams;
import static org.eclipse.jdt.ls.core.internal.Lsp4jAssertions.assertRange;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.net.URI;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.eclipse.core.resources.IProject;
import org.eclipse.jdt.ls.core.internal.ResourceUtils;
import org.eclipse.jdt.ls.core.internal.WorkspaceHelper;
import org.eclipse.jdt.ls.core.internal.managers.AbstractProjectsManagerBasedTest;
import org.eclipse.jdt.ls.core.internal.preferences.PreferenceManager;
import org.eclipse.jdt.ls.core.internal.preferences.Preferences;
import org.eclipse.lsp4j.CodeLens;
import org.eclipse.lsp4j.CodeLensParams;
import org.eclipse.lsp4j.Command;
import org.eclipse.lsp4j.Location;
import org.eclipse.lsp4j.Range;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

/**
 * @author Fred Bricon
 *
 */
public class CodeLensHandlerTest extends AbstractProjectsManagerBasedTest {

	private static final String CODELENS_PARAM_TEMPLATE =
			"{\n" +
					"    \"id\": \"1\",\n" +
					"    \"method\": \"textDocument/codeLens\",\n" +
					"    \"params\": {\n" +
					"        \"textDocument\": {\n" +
					"            \"uri\": \"${file}\"\n" +
					"        }\n" +
					"    },\n" +
					"    \"jsonrpc\": \"2.0\"\n" +
					"}";

	private static final String CODELENS_IMPLEMENTATIONS_TEMPLATE = "{\n" +
			"    \"id\": \"1\",\n" +
			"    \"method\": \"codeLens/resolve\",\n" +
			"    \"params\": {\n" +
			"		\"range\": {\n" +
			"	        \"start\": {\n" +
			"	            \"line\": ${line},\n" +
			"	            \"character\": ${start}\n" +
			"	        },\n" +
			"	        \"end\": {\n" +
			"	            \"line\": ${line},\n" +
			"	            \"character\": ${end}\n" +
			"	        }\n" +
			"	    },\n" +
			"	    \"data\": [\n" +
			"	        \"${file}\",\n" +
			"	        {\n" +
			"	            \"line\": ${line},\n" +
			"	            \"character\": ${start}\n" +
			"	        },\n" +
			"	        \"implementations\"\n" +
			"	    ]"+
			"    },\n" +
			"    \"jsonrpc\": \"2.0\"\n" +
			"}";

	private static final String CODELENS_REFERENCES_TEMPLATE = "{\n" +
			"    \"id\": \"1\",\n" +
			"    \"method\": \"codeLens/resolve\",\n" +
			"    \"params\": {\n" +
			"		\"range\": {\n" +
			"	        \"start\": {\n" +
			"	            \"line\": ${line},\n" +
			"	            \"character\": ${start}\n" +
			"	        },\n" +
			"	        \"end\": {\n" +
			"	            \"line\": ${line},\n" +
			"	            \"character\": ${end}\n" +
			"	        }\n" +
			"	    },\n" +
			"	    \"data\": [\n" +
			"	        \"${file}\",\n" +
			"	        {\n" +
			"	            \"line\": ${line},\n" +
			"	            \"character\": ${start}\n" +
			"	        },\n" +
			"	        \"references\"\n" +
			"	    ]"+
			"    },\n" +
			"    \"jsonrpc\": \"2.0\"\n" +
			"}";


	private CodeLensHandler handler;

	private IProject project;

	private PreferenceManager preferenceManager;

	@Before
	public void setup() throws Exception {
		importProjects("eclipse/hello");
		project = WorkspaceHelper.getProject("hello");
		preferenceManager = mock(PreferenceManager.class);
		when(preferenceManager.getPreferences()).thenReturn(new Preferences());
		handler = new CodeLensHandler(preferenceManager);
	}

	@Test
	public void testGetCodeLensSymbols() throws Exception {
		String payload = createCodeLensSymbolsRequest("src/java/Foo.java");
		CodeLensParams codeLensParams = getParams(payload);
		String uri = codeLensParams.getTextDocument().getUri();
		assertFalse(uri.isEmpty());
		//when
		List<CodeLens> result = handler.getCodeLensSymbols(uri, monitor);

		//then
		assertEquals("Found " + result, 3, result.size());

		CodeLens cl = result.get(0);
		Range r = cl.getRange();
		//CodeLens on main method
		assertRange(7, 20, 24, r);

		cl = result.get(1);
		r = cl.getRange();
		// CodeLens on foo method
		assertRange(14, 13, 16, r);

		cl = result.get(2);
		r = cl.getRange();
		//CodeLens on Foo type
		assertRange(5, 13, 16, r);
	}

	@Test
	public void testGetCodeLenseBoundaries() {
		List<CodeLens> result = handler.getCodeLensSymbols(null, monitor);
		assertNotNull(result);
		assertEquals(0, result.size());

		String payload = createCodeLensSymbolsRequest("src/java/Missing.java");
		CodeLensParams codeLensParams = getParams(payload);
		String uri = codeLensParams.getTextDocument().getUri();
		result = handler.getCodeLensSymbols(uri, monitor);
		assertEquals(0, result.size());
	}

	@Test
	public void testDisableCodeLensSymbols() throws Exception {
		Preferences noCodeLenses = Preferences.createFrom(Collections.singletonMap(Preferences.REFERENCES_CODE_LENS_ENABLED_KEY, "false"));
		Mockito.reset(preferenceManager);
		when(preferenceManager.getPreferences()).thenReturn(noCodeLenses);
		handler = new CodeLensHandler(preferenceManager);

		String payload = createCodeLensSymbolsRequest("src/java/IFoo.java");
		CodeLensParams codeLensParams = getParams(payload);
		String uri = codeLensParams.getTextDocument().getUri();
		assertFalse(uri.isEmpty());

		//when
		List<CodeLens> result = handler.getCodeLensSymbols(uri, monitor);

		//then
		assertEquals(0, result.size());
	}

	@Test
	public void testEnableImplementationsCodeLensSymbols() throws Exception {
		Preferences implementationsCodeLenses = Preferences.createFrom(Collections.singletonMap(Preferences.IMPLEMENTATIONS_CODE_LENS_ENABLED_KEY, "true"));
		Mockito.reset(preferenceManager);
		when(preferenceManager.getPreferences()).thenReturn(implementationsCodeLenses);
		handler = new CodeLensHandler(preferenceManager);

		String payload = createCodeLensSymbolsRequest("src/java/IFoo.java");
		CodeLensParams codeLensParams = getParams(payload);
		String uri = codeLensParams.getTextDocument().getUri();
		assertFalse(uri.isEmpty());

		//when
		List<CodeLens> result = handler.getCodeLensSymbols(uri, monitor);

		//then
		assertEquals(2, result.size());
		CodeLens lens = result.get(1);
		@SuppressWarnings("unchecked")
		List<Object> data = (List<Object>) lens.getData();
		String type = (String) data.get(2);
		assertEquals(type, "implementations");
	}

	@Test
	public void testDisableImplementationsCodeLensSymbols() throws Exception {
		Preferences noImplementationsCodeLenses = Preferences.createFrom(Collections.singletonMap(Preferences.IMPLEMENTATIONS_CODE_LENS_ENABLED_KEY, "false"));
		Mockito.reset(preferenceManager);
		when(preferenceManager.getPreferences()).thenReturn(noImplementationsCodeLenses);
		Preferences noReferencesCodeLenses = Preferences.createFrom(Collections.singletonMap(Preferences.REFERENCES_CODE_LENS_ENABLED_KEY, "false"));
		Mockito.reset(preferenceManager);
		when(preferenceManager.getPreferences()).thenReturn(noReferencesCodeLenses);
		handler = new CodeLensHandler(preferenceManager);

		String payload = createCodeLensSymbolsRequest("src/java/IFoo.java");
		CodeLensParams codeLensParams = getParams(payload);
		String uri = codeLensParams.getTextDocument().getUri();
		assertFalse(uri.isEmpty());

		//when
		List<CodeLens> result = handler.getCodeLensSymbols(uri, monitor);

		//then
		assertEquals(0, result.size());
	}

	@SuppressWarnings("unchecked")
	@Test
	public void testResolveImplementationsCodeLense() {
		String source = "src/java/IFoo.java";
		String payload = createCodeLensImplementationsRequest(source, 5, 17, 21);

		CodeLens lens = getParams(payload);
		Range range = lens.getRange();
		assertRange(5, 17, 21, range);

		CodeLens result = handler.resolve(lens, monitor);
		assertNotNull(result);

		//Check if command found
		Command command = result.getCommand();
		assertNotNull(command);
		assertEquals("2 implementations", command.getTitle());
		assertEquals("java.show.implementations", command.getCommand());

		//Check codelens args
		List<Object> args = command.getArguments();
		assertEquals(3, args.size());

		//Check we point to the Bar class
		String sourceUri = args.get(0).toString();
		assertTrue(sourceUri.endsWith("IFoo.java"));

		//CodeLens position
		Map<String, Object> map = (Map<String, Object>) args.get(1);
		assertEquals(5.0, map.get("line"));
		assertEquals(17.0, map.get("character"));

		//Reference location
		List<Location> locations = (List<Location>) args.get(2);
		assertEquals(2, locations.size());
		Location loc = locations.get(0);
		assertTrue(loc.getUri().endsWith("src/java/Foo2.java"));
		assertRange(5, 13, 17, loc.getRange());
	}

	@SuppressWarnings("unchecked")
	@Test
	public void testResolveCodeLense() {
		String source = "src/java/Foo.java";
		String payload = createCodeLensRequest(source, 5, 13, 16);

		CodeLens lens = getParams(payload);
		Range range = lens.getRange();
		assertRange(5, 13, 16, range);

		CodeLens result = handler.resolve(lens, monitor);
		assertNotNull(result);

		//Check if command found
		Command command = result.getCommand();
		assertNotNull(command);
		assertEquals("1 reference", command.getTitle());
		assertEquals("java.show.references",command.getCommand());

		//Check codelens args
		List<Object> args = command.getArguments();
		assertEquals(3,args.size());

		//Check we point to the Bar class
		String sourceUri = args.get(0).toString();
		assertTrue(sourceUri.endsWith(source));

		//CodeLens position
		Map<String, Object> map = (Map<String, Object>)args.get(1);
		assertEquals(5.0, map.get("line"));
		assertEquals(13.0, map.get("character"));

		//Reference location
		List<Location> locations = (List<Location>)args.get(2);
		assertEquals(1, locations.size());
		Location loc = locations.get(0);
		assertTrue(loc.getUri().endsWith("src/java/Bar.java"));
		assertRange(5, 25, 28, loc.getRange());
	}

	@Test
	public void testResolveCodeLenseBoundaries() {
		CodeLens result = handler.resolve(null, monitor);
		assertNull(result);

		String payload = createCodeLensRequest("src/java/Missing.java", 5, 13, 16);
		CodeLens lens = getParams(payload);
		result = handler.resolve(lens, monitor);
		assertSame(lens, result);
		assertNull(result.getCommand());
	}

	@Test
	public void testIgnoreLombokCodeLensSymbols() throws Exception {
		String payload = createCodeLensSymbolsRequest("src/java/Bar.java");
		CodeLensParams codeLensParams = getParams(payload);
		String uri = codeLensParams.getTextDocument().getUri();
		assertFalse(uri.isEmpty());
		//when
		List<CodeLens> result = handler.getCodeLensSymbols(uri, monitor);

		//then
		assertEquals("Found " + result, 4, result.size());

		//CodeLens on constructor
		CodeLens cl = result.get(0);
		assertRange(7, 11, 14, cl.getRange());

		//CodeLens on somethingFromJPAModelGen
		cl = result.get(1);
		assertRange(16, 16, 40, cl.getRange());

		// CodeLens on foo
		cl = result.get(2);
		assertRange(22, 16, 19, cl.getRange());

		//CodeLens on Bar type
		cl = result.get(3);
		assertRange(5, 13, 16, cl.getRange());
	}

	String createCodeLensSymbolsRequest(String file) {
		URI uri = project.getFile(file).getRawLocationURI();
		return createCodeLensSymbolRequest(uri);
	}

	String createCodeLensSymbolRequest(URI file) {
		String fileURI = ResourceUtils.fixURI(file);
		return CODELENS_PARAM_TEMPLATE.replace("${file}", fileURI);
	}

	String createCodeLensRequest(String file, int line, int start, int end) {
		URI uri = project.getFile(file).getRawLocationURI();
		return createCodeLensRequest(uri, line, start, end);
	}

	String createCodeLensImplementationsRequest(String file, int line, int start, int end) {
		URI uri = project.getFile(file).getRawLocationURI();
		return createCodeLensImplementationsRequest(uri, line, start, end);
	}

	String createCodeLensRequest(URI file, int line, int start, int end) {
		String fileURI = ResourceUtils.fixURI(file);
		return CODELENS_REFERENCES_TEMPLATE.replace("${file}", fileURI)
				.replace("${line}", String.valueOf(line))
				.replace("${start}", String.valueOf(start))
				.replace("${end}", String.valueOf(end));
	}

	String createCodeLensImplementationsRequest(URI file, int line, int start, int end) {
		String fileURI = ResourceUtils.fixURI(file);
		return CODELENS_IMPLEMENTATIONS_TEMPLATE.replace("${file}", fileURI)
				.replace("${line}", String.valueOf(line))
				.replace("${start}", String.valueOf(start))
				.replace("${end}", String.valueOf(end));
	}

}
