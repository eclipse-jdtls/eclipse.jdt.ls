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
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import java.net.URI;
import java.nio.file.Paths;

import org.eclipse.core.resources.IProject;
import org.eclipse.jdt.ls.core.internal.ResourceUtils;
import org.eclipse.jdt.ls.core.internal.WorkspaceHelper;
import org.eclipse.jdt.ls.core.internal.handlers.HoverHandler;
import org.eclipse.jdt.ls.core.internal.managers.AbstractProjectsManagerBasedTest;
import org.eclipse.lsp4j.Hover;
import org.eclipse.lsp4j.TextDocumentPositionParams;
import org.junit.Before;
import org.junit.Test;

/**
 * @author Fred Bricon
 */
public class HoverHandlerTest extends AbstractProjectsManagerBasedTest {

	private static String HOVER_TEMPLATE =
			"{\n" +
					"    \"id\": \"1\",\n" +
					"    \"method\": \"textDocument/hover\",\n" +
					"    \"params\": {\n" +
					"        \"textDocument\": {\n" +
					"            \"uri\": \"${file}\"\n" +
					"        },\n" +
					"        \"position\": {\n" +
					"            \"line\": ${line},\n" +
					"            \"character\": ${char}\n" +
					"        }\n" +
					"    },\n" +
					"    \"jsonrpc\": \"2.0\"\n" +
					"}";

	private HoverHandler handler;

	private IProject project;

	@Before
	public void setup() throws Exception {
		importProjects("eclipse/hello");
		project = WorkspaceHelper.getProject("hello");
		handler = new HoverHandler();
	}

	@Test
	public void testHover() throws Exception {
		//given
		//Hovers on the System.out
		String payload = createHoverRequest("src/java/Foo.java", 5, 15);
		TextDocumentPositionParams position = getParams(payload);

		//when
		Hover hover = handler.hover(position).get();

		//then
		assertNotNull(hover);
		String result = hover.getContents().get(0).getLeft();//wow this is so elegant!
		assertEquals("Unexpected hover "+result, "This is foo", result);
	}

	@Test
	public void testHoverStandalone() throws Exception {
		//given
		//Hovers on the System.out
		URI standalone = Paths.get("projects","maven","salut","src","main","java","java","Foo.java").toUri();
		String payload = createHoverRequest(standalone, 10, 70);
		TextDocumentPositionParams position = getParams(payload);

		//when
		Hover hover = handler.hover(position).get();

		//then
		assertNotNull(hover);
		String result = hover.getContents().get(0).getLeft();//wow this is so elegant!
		assertEquals("Unexpected hover "+result, "This is foo", result);
	}

	@Test
	public void testEmptyHover() throws Exception {
		//given
		//Hovers on the System.out
		URI standalone = Paths.get("projects","maven","salut","src","main","java","java","Foo.java").toUri();
		String payload = createHoverRequest(standalone, 1, 2);
		TextDocumentPositionParams position = getParams(payload);

		//when
		Hover hover = handler.hover(position).get();

		//then
		assertNotNull(hover);
		assertNull("Shouldn't find hover for "+payload, hover.getContents());
	}

	String createHoverRequest(String file, int line, int kar) {
		URI uri = project.getFile(file).getRawLocationURI();
		return createHoverRequest(uri, line, kar);
	}

	String createHoverRequest(URI file, int line, int kar) {
		String fileURI = ResourceUtils.fixURI(file);
		return HOVER_TEMPLATE.replace("${file}", fileURI)
				.replace("${line}", String.valueOf(line))
				.replace("${char}", String.valueOf(kar));
	}

	@Test
	public void testHoverVariable() throws Exception {
		//given
		//Hover on args parameter
		String argParam = createHoverRequest("src/java/Foo.java", 7, 37);
		TextDocumentPositionParams position = getParams(argParam);

		//when
		Hover hover = handler.hover(position).get();

		//then
		assertNotNull(hover);
		String result = hover.getContents().get(0).getLeft();//wow this is so elegant!
		assertEquals("Unexpected hover "+result, "String[] args - java.Foo.main(String[])", result);
	}
}
