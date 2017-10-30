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

public class DidChangeWorkspaceFoldersParams {
	/**
	 * The actual workspace folder change event.
	 */
	@NonNull
	private WorkspaceFoldersChangeEvent event;

	public DidChangeWorkspaceFoldersParams() {
		// TODO Auto-generated constructor stub
	}

	/**
	 * @param event
	 */
	public DidChangeWorkspaceFoldersParams(@NonNull WorkspaceFoldersChangeEvent event) {
		this.event = event;
	}

	/**
	 * @return the event
	 */
	@NonNull
	public WorkspaceFoldersChangeEvent getEvent() {
		return event;
	}

	/**
	 * @param event
	 *            the event to set
	 */
	public void setEvent(@NonNull WorkspaceFoldersChangeEvent event) {
		this.event = event;
	}

	/* (non-Javadoc)
	 * @see java.lang.Object#toString()
	 */
	@Override
	public String toString() {
		return "DidChangeWorkspaceFoldersParams [event=" + event + "]";
	}

	/* (non-Javadoc)
	 * @see java.lang.Object#hashCode()
	 */
	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((event == null) ? 0 : event.hashCode());
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
		DidChangeWorkspaceFoldersParams other = (DidChangeWorkspaceFoldersParams) obj;
		if (event == null) {
			if (other.event != null) {
				return false;
			}
		} else if (!event.equals(other.event)) {
			return false;
		}
		return true;
	}

}