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

public class ShowMessageParams {

	/**
	 * The message type. See {
	 * (Required)
	 *
	 */
	@SerializedName("type")
	@Expose
	private Integer type;
	/**
	 * The actual message
	 * (Required)
	 *
	 */
	@SerializedName("message")
	@Expose
	private String message;

	/**
	 * The message type. See {
	 * (Required)
	 *
	 * @return
	 *     The type
	 */
	public Integer getType() {
		return type;
	}

	/**
	 * The message type. See {
	 * (Required)
	 *
	 * @param type
	 *     The type
	 */
	public void setType(Integer type) {
		this.type = type;
	}

	public ShowMessageParams withType(Integer type) {
		this.type = type;
		return this;
	}

	/**
	 * The actual message
	 * (Required)
	 *
	 * @return
	 *     The message
	 */
	public String getMessage() {
		return message;
	}

	/**
	 * The actual message
	 * (Required)
	 *
	 * @param message
	 *     The message
	 */
	public void setMessage(String message) {
		this.message = message;
	}

	public ShowMessageParams withMessage(String message) {
		this.message = message;
		return this;
	}

}
