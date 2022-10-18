/*******************************************************************************
 * Copyright (c) 2019-2022 Microsoft Corporation and others.
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

package org.eclipse.jdt.ls.core.internal.corext.template.java;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import org.eclipse.jdt.ls.core.internal.JavaLanguageServerPlugin;
import org.eclipse.jface.text.templates.Template;
import org.eclipse.text.templates.TemplatePersistenceData;
import org.junit.Test;

public class JavaLanguageServerTemplateStoreTest {

	@Test
	public void testTemplateStoreContent() {
		JavaLanguageServerTemplateStore store = JavaLanguageServerPlugin.getInstance().getTemplateStore();
		Template[] templates = store.getTemplates();
		CodeSnippetTemplate[] snippets = CodeSnippetTemplate.values();
		PostfixTemplate[] postfixes = PostfixTemplate.values();

		assertEquals(templates.length, snippets.length + postfixes.length);

		for (CodeSnippetTemplate snippet : snippets) {
			TemplatePersistenceData templateData = store.getTemplateData(snippet.getId());
			assertNotNull(templateData);
			assertEquals(templateData.getTemplate().getName(), snippet.name().toLowerCase());
		}
		
		for (PostfixTemplate postfix : postfixes) {
			TemplatePersistenceData templateData = store.getTemplateData(postfix.getId());
			assertNotNull(templateData);
			assertEquals(templateData.getTemplate().getName(), postfix.name().toLowerCase());
		}
	}
}
