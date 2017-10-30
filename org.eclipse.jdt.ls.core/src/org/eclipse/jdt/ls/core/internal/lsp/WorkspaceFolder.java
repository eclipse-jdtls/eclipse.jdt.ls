/*******************************************************************************
 * Copyright (c) 2017 Microsoft Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Microsoft Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.jdt.ls.core.internal.lsp;

import org.eclipse.lsp4j.jsonrpc.validation.NonNull;

public class WorkspaceFolder {
	/**
	 * The associated URI for this workspace folder.
	 */
	@NonNull
	private String uri;

	/**
	 * The name of the workspace folder. Defaults to the uri's basename.
	 */
	@NonNull
	private String name;

	public WorkspaceFolder() {
	}

	public WorkspaceFolder(@NonNull String uri, @NonNull String name) {
		this.uri = uri;
		this.name = name;
	}

	/**
	 * Gets the associated URI for this workspace folder.
	 *
	 * @return the uri
	 */
	@NonNull
	public String getUri() {
		return uri;
	}

	/**
	 * Sets the associated URI for this workspace folder.
	 *
	 * @param uri
	 *            the uri to set
	 */
	public void setUri(@NonNull String uri) {
		this.uri = uri;
	}

	/**
	 * The name of the workspace folder. Defaults to the uri's basename.
	 *
	 * @return the name
	 */
	@NonNull
	public String getName() {
		return name;
	}

	/**
	 * The name of the workspace folder. Defaults to the uri's basename.
	 *
	 * @param name
	 *            the name to set
	 */
	public void setName(@NonNull String name) {
		this.name = name;
	}

	/* (non-Javadoc)
	 * @see java.lang.Object#toString()
	 */
	@Override
	public String toString() {
		return "WorkspaceFolder [uri=" + uri + ", name=" + name + "]";
	}

	/* (non-Javadoc)
	 * @see java.lang.Object#hashCode()
	 */
	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((name == null) ? 0 : name.hashCode());
		result = prime * result + ((uri == null) ? 0 : uri.hashCode());
		return result;
	}

	/* (non-Javadoc)
	 * @see java.lang.Object#equals(java.lang.Object)
	 */
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
		WorkspaceFolder other = (WorkspaceFolder) obj;
		if (name == null) {
			if (other.name != null) {
				return false;
			}
		} else if (!name.equals(other.name)) {
			return false;
		}
		if (uri == null) {
			if (other.uri != null) {
				return false;
			}
		} else if (!uri.equals(other.uri)) {
			return false;
		}
		return true;
	}

}