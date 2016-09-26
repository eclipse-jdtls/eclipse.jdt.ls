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

public class Location {

	/**
	 *
	 * (Required)
	 *
	 */
	@SerializedName("uri")
	@Expose
	private String uri;
	/**
	 *
	 * (Required)
	 *
	 */
	@SerializedName("range")
	@Expose
	private Range range;

	/**
	 *
	 * (Required)
	 *
	 * @return
	 *     The uri
	 */
	public String getUri() {
		return uri;
	}

	/**
	 *
	 * (Required)
	 *
	 * @param uri
	 *     The uri
	 */
	public void setUri(String uri) {
		this.uri = uri;
	}

	public Location withUri(String uri) {
		this.uri = uri;
		return this;
	}

	/**
	 *
	 * (Required)
	 *
	 * @return
	 *     The range
	 */
	public Range getRange() {
		return range;
	}

	/**
	 *
	 * (Required)
	 *
	 * @param range
	 *     The range
	 */
	public void setRange(Range range) {
		this.range = range;
	}

	public Location withRange(Range range) {
		this.range = range;
		return this;
	}

}
