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

package org.eclipse.jdt.ls.core.internal.lsp;

import java.util.Objects;

import org.eclipse.lsp4j.TextDocumentIdentifier;

public class ValidateDocumentParams {
	TextDocumentIdentifier textDocument;

	public ValidateDocumentParams() {
	}

	public ValidateDocumentParams(TextDocumentIdentifier textDocument) {
		this.textDocument = textDocument;
	}

	public TextDocumentIdentifier getTextDocument() {
		return textDocument;
	}

	public void setTextDocument(TextDocumentIdentifier textDocument) {
		this.textDocument = textDocument;
	}

	@Override
	public int hashCode() {
		return Objects.hash(textDocument);
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		ValidateDocumentParams other = (ValidateDocumentParams) obj;
		return Objects.equals(textDocument, other.textDocument);
	}
}
