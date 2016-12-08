/*******************************************************************************
 * Copyright (c) 2016 Red Hat Inc. and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Red Hat Inc. - initial API and implementation
 *******************************************************************************/
package org.jboss.tools.vscode.java.internal.handlers;

import static org.jboss.tools.vscode.java.internal.JsonMessageHelper.getParams;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.net.URI;
import java.nio.file.Paths;

import org.eclipse.core.resources.IProject;
import org.eclipse.lsp4j.Hover;
import org.eclipse.lsp4j.TextDocumentPositionParams;
import org.jboss.tools.vscode.java.internal.ResourceUtils;
import org.jboss.tools.vscode.java.internal.WorkspaceHelper;
import org.jboss.tools.vscode.java.internal.managers.AbstractProjectsManagerBasedTest;
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
		String payload = createHoverRequest("src/java/Foo.java", 5, 10);
		TextDocumentPositionParams position = getParams(payload);

		//when
		Hover hover = handler.hover(position).get();

		//then
		assertNotNull(hover);
		assertEquals("Couldn't find hover for "+payload, 1, hover.getContents().size());
		assertTrue("Unexpected hover "+hover.getContents().get(0), hover.getContents().get(0).startsWith("The \"standard\" output stream"));
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
		assertEquals("Couldn't find hover for "+payload, 1, hover.getContents().size());
		assertEquals("Unexpected hover "+hover.getContents().get(0), "This is foo", hover.getContents().get(0));
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
}
