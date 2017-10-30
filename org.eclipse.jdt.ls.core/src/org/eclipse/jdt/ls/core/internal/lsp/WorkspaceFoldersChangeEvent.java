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

import java.util.Arrays;

import org.eclipse.lsp4j.jsonrpc.validation.NonNull;

/**
 * The workspace folder change event.
 */
public class WorkspaceFoldersChangeEvent {
	/**
	 * The array of added workspace folders
	 */
	@NonNull
	private WorkspaceFolder[] added;

	/**
	 * The array of the removed workspace folders
	 */
	@NonNull
	private WorkspaceFolder[] removed;

	public WorkspaceFoldersChangeEvent() {
	}

	public WorkspaceFoldersChangeEvent(@NonNull final WorkspaceFolder[] added, @NonNull final WorkspaceFolder[] removed) {
		this.added = added;
		this.removed = removed;
	}

	/**
	 * @return the added
	 */
	@NonNull
	public WorkspaceFolder[] getAdded() {
		return added;
	}

	/**
	 * @param added
	 *            the added to set
	 */
	public void setAdded(@NonNull WorkspaceFolder[] added) {
		this.added = added;
	}

	/**
	 * @return the removed
	 */
	@NonNull
	public WorkspaceFolder[] getRemoved() {
		return removed;
	}

	/**
	 * @param removed
	 *            the removed to set
	 */
	public void setRemoved(@NonNull WorkspaceFolder[] removed) {
		this.removed = removed;
	}

	/* (non-Javadoc)
	 * @see java.lang.Object#hashCode()
	 */
	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + Arrays.hashCode(added);
		result = prime * result + Arrays.hashCode(removed);
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
		WorkspaceFoldersChangeEvent other = (WorkspaceFoldersChangeEvent) obj;
		if (!Arrays.equals(added, other.added)) {
			return false;
		}
		if (!Arrays.equals(removed, other.removed)) {
			return false;
		}
		return true;
	}

	/* (non-Javadoc)
	 * @see java.lang.Object#toString()
	 */
	@Override
	public String toString() {
		return "WorkspaceFoldersChangeEvent [added=" + Arrays.toString(added) + ", removed=" + Arrays.toString(removed) + "]";
	}

}