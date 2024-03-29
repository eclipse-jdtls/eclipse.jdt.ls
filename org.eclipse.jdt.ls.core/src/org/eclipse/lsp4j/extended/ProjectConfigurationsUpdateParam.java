/*******************************************************************************
 * Copyright (c) 2022 Microsoft Corporation and others.
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

package org.eclipse.lsp4j.extended;

import java.util.List;

import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.eclipse.lsp4j.jsonrpc.util.Preconditions;
import org.eclipse.lsp4j.jsonrpc.validation.NonNull;
import org.eclipse.xtext.xbase.lib.Pure;
import org.eclipse.xtext.xbase.lib.util.ToStringBuilder;

@SuppressWarnings("all")
public class ProjectConfigurationsUpdateParam {
	/**
	 * The text document's identifiers.
	 */
	@NonNull
	private List<TextDocumentIdentifier> identifiers;

	public ProjectConfigurationsUpdateParam() {
	}

	public ProjectConfigurationsUpdateParam(@NonNull final List<TextDocumentIdentifier> identifiers) {
		this.identifiers = Preconditions.<List<TextDocumentIdentifier>>checkNotNull(identifiers, "identifiers");
	}

	@Pure
	@NonNull
	public List<TextDocumentIdentifier> getIdentifiers() {
		return identifiers;
	}

	public void setIdentifiers(@NonNull final List<TextDocumentIdentifier> identifiers) {
		this.identifiers = Preconditions.<List<TextDocumentIdentifier>>checkNotNull(identifiers, "identifiers");
	}

	@Override
	@Pure
	public String toString() {
	  ToStringBuilder b = new ToStringBuilder(this);
	  b.add("identifiers", this.identifiers);
	  return b.toString();
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((identifiers == null) ? 0 : identifiers.hashCode());
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj) {
			return true;
		}
		if (obj == null) {
			return false;
		}
		if (getClass() != obj.getClass()) {
			return false;
		}
		ProjectConfigurationsUpdateParam other = (ProjectConfigurationsUpdateParam) obj;
		if (identifiers == null) {
			if (other.identifiers != null) {
				return false;
			}
		} else if (!identifiers.equals(other.identifiers)) {
			return false;
		}
		return true;
	}
}
