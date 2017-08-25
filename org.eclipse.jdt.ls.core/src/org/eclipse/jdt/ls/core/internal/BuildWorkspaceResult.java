/*******************************************************************************
 * Copyright (c) 2017 Microsoft Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Microsoft Corporation. - initial API and implementation
 *******************************************************************************/
package org.eclipse.jdt.ls.core.internal;

import org.eclipse.lsp4j.jsonrpc.validation.NonNull;
import org.eclipse.xtext.xbase.lib.Pure;

/**
 * Representation of a response that server sends to client about the workspace
 * build result
 *
 */
public class BuildWorkspaceResult {

	@NonNull
	private BuildWorkspaceStatus status;

	private String details;

	public BuildWorkspaceResult(BuildWorkspaceStatus status, String details) {
		this.status = status;
		this.details = details;
	}

	public BuildWorkspaceResult(BuildWorkspaceStatus status) {
		this.status = status;
	}

	/**
	 * @return the details
	 */
	public String getDetails() {
		return details;
	}

	/**
	 * @param details
	 *            the details to set
	 */
	public void setDetails(String details) {
		this.details = details;
	}

	/**
	 * @return the status
	 */
	@NonNull
	@Pure
	public BuildWorkspaceStatus getStatus() {
		return status;
	}

	/**
	 * @param status
	 *            the status to set
	 */
	public void setStatus(BuildWorkspaceStatus status) {
		this.status = status;
	}

}
