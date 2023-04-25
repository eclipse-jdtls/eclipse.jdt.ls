/*******************************************************************************
 * Copyright (c) 2023 Microsoft Corporation and others.
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

import org.eclipse.jdt.core.CompletionProposal;
import org.eclipse.jface.text.templates.Template;

public class PostfixCompletionProposal extends CompletionProposal {
	private Template template;
	private JavaPostfixContext context;

	public PostfixCompletionProposal(Template template, JavaPostfixContext context) {
		this.template = template;
		this.context = context;
	}

	public Template getTemplate() {
		return this.template;
	}

	public JavaPostfixContext getContext() {
		return this.context;
	}
}
