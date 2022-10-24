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

import org.eclipse.core.runtime.preferences.IEclipsePreferences;
import org.eclipse.jface.text.templates.Template;
import org.eclipse.text.templates.ContextTypeRegistry;
import org.eclipse.text.templates.TemplatePersistenceData;
import org.eclipse.text.templates.TemplateStoreCore;

/**
 * LSTemplateStore
 */
public class JavaLanguageServerTemplateStore extends TemplateStoreCore {

	public JavaLanguageServerTemplateStore(ContextTypeRegistry registry, IEclipsePreferences store, String key) {
		super(registry, store, key);
	}

	@Override
	protected void loadContributedTemplates() {
		for (CodeSnippetTemplate snippet : CodeSnippetTemplate.values()) {
			Template template = snippet.createTemplate();
			add(new TemplatePersistenceData(template, true, snippet.getId()));
		}
		for (PostfixTemplate snippet : PostfixTemplate.values()) {
			Template template = snippet.createTemplate();
			add(new TemplatePersistenceData(template, true, snippet.getId()));
		}
	}
}
