/*******************************************************************************
 * Copyright (c) 2024 Red Hat Inc. and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *     Red Hat Inc. - initial API and implementation
 *******************************************************************************/

package org.eclipse.lsp4j.extended;

import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.TextEdit;

public class SnippetTextEdit extends TextEdit {
	StringValue snippet;

	public SnippetTextEdit(Range range, String snippet) {
		setRange(range);
		this.snippet = new StringValue(snippet);
	}

	public StringValue getSnippet() {
		return snippet;
	}

	public static final class StringValue {
		public static final String kind = "snippet";
		String value;

		StringValue(String value) {
			this.value = value;
		}

		public String getValue() {
			return value;
		}
	}
}