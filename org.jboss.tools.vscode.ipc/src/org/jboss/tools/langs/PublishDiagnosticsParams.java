/*******************************************************************************
 * Copyright (c) 2016 Red Hat Inc. and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Red Hat Inc. - initial API and implementation
 *******************************************************************************/
package org.jboss.tools.langs;

import java.util.ArrayList;
import java.util.List;

import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;

public class PublishDiagnosticsParams {

	/**
	 * The URI for which diagnostic information is reported.
	 * (Required)
	 *
	 */
	@SerializedName("uri")
	@Expose
	private String uri;
	/**
	 * An array of diagnostic information items.
	 * (Required)
	 *
	 */
	@SerializedName("diagnostics")
	@Expose
	private List<Diagnostic> diagnostics = new ArrayList<>();

	/**
	 * The URI for which diagnostic information is reported.
	 * (Required)
	 *
	 * @return
	 *     The uri
	 */
	public String getUri() {
		return uri;
	}

	/**
	 * The URI for which diagnostic information is reported.
	 * (Required)
	 *
	 * @param uri
	 *     The uri
	 */
	public void setUri(String uri) {
		this.uri = uri;
	}

	public PublishDiagnosticsParams withUri(String uri) {
		this.uri = uri;
		return this;
	}

	/**
	 * An array of diagnostic information items.
	 * (Required)
	 *
	 * @return
	 *     The diagnostics
	 */
	public List<Diagnostic> getDiagnostics() {
		return diagnostics;
	}

	/**
	 * An array of diagnostic information items.
	 * (Required)
	 *
	 * @param diagnostics
	 *     The diagnostics
	 */
	public void setDiagnostics(List<Diagnostic> diagnostics) {
		this.diagnostics = diagnostics;
	}

	public PublishDiagnosticsParams withDiagnostics(List<Diagnostic> diagnostics) {
		this.diagnostics = diagnostics;
		return this;
	}

}
