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

import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;

public class FileEvent {

	/**
	 * The file's uri.
	 * (Required)
	 *
	 */
	@SerializedName("uri")
	@Expose
	private String uri;
	/**
	 * The change type.
	 * (Required)
	 *
	 */
	@SerializedName("type")
	@Expose
	private Double type;

	/**
	 * The file's uri.
	 * (Required)
	 *
	 * @return
	 *     The uri
	 */
	public String getUri() {
		return uri;
	}

	/**
	 * The file's uri.
	 * (Required)
	 *
	 * @param uri
	 *     The uri
	 */
	public void setUri(String uri) {
		this.uri = uri;
	}

	public FileEvent withUri(String uri) {
		this.uri = uri;
		return this;
	}

	/**
	 * The change type.
	 * (Required)
	 *
	 * @return
	 *     The type
	 */
	public Double getType() {
		return type;
	}

	/**
	 * The change type.
	 * (Required)
	 *
	 * @param type
	 *     The type
	 */
	public void setType(Double type) {
		this.type = type;
	}

	public FileEvent withType(Double type) {
		this.type = type;
		return this;
	}

}
