/*******************************************************************************
 * Copyright (c) 2019 Microsoft Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Microsoft Corporation - initial API and implementation
 *******************************************************************************/

package org.eclipse.jdt.ls.core.internal.corext.template.java;

import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jface.text.templates.Template;

public enum CodeSnippetTemplates {

	SYSTRACE(TemplatePreferences.SYSTRACE_ID, JavaContextType.ID_STATEMENTS, TemplatePreferences.SYSTRACE_CONTENT, TemplatePreferences.SYSTRACE_DESCRIPTION),
	FOREACH(TemplatePreferences.FOREACH_ID, JavaContextType.ID_STATEMENTS, TemplatePreferences.FOREACH_CONTENT, TemplatePreferences.FOREACH_DESCRIPTION),
	FORI(TemplatePreferences.FORI_ID, JavaContextType.ID_STATEMENTS, TemplatePreferences.FORI_CONTENT, TemplatePreferences.FORI_DESCRIPTION);

	private final String templateId;
	private final String contextType;
	private final String defaultContent;
	private final String description;

	private CodeSnippetTemplates(String templatesId, String contextType, String defaultContent, String description) {
		this.templateId = templatesId;
		this.contextType = contextType;
		this.defaultContent = defaultContent;
		this.description = description;
	}

	public Template createTemplate() {
		return new Template(this.name().toLowerCase(), this.description, this.contextType, this.defaultContent, false);
	}

	public String getId() {
		return this.templateId;
	}
}

class TemplatePreferences {
	// IDs
	public static final String SYSTRACE_ID = "org.eclipse.jdt.ui.templates.systrace";
	public static final String FOREACH_ID = "org.eclipse.jdt.ui.templates.for_array";
	public static final String FORI_ID = "org.eclipse.jdt.ui.templates.for_iterable";

	// DefaultContents
	public static final String SYSTRACE_CONTENT = "System.out.println(\"${enclosing_type}.${enclosing_method}()\");";
	public static final String FOREACH_CONTENT = "for (${iterable_type} ${iterable_element} : ${iterable}) {\n" + "\t$$0\n" + "}";
	public static final String FORI_CONTENT = "for (int ${index} = 0; ${index} < ${array}.length; ${index}++) {\n" + "\t$$0\n" + "}";

	// Descriptions
	public static final String SYSTRACE_DESCRIPTION = "Print current method to standard out";
	public static final String FOREACH_DESCRIPTION = "Iterate over an array or Iterable";
	public static final String FORI_DESCRIPTION = "Iterate over array";
}
