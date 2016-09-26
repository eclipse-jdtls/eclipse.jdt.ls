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

public class ShowMessageRequestParams {

	/**
	 * The message type. See {
	 * (Required)
	 *
	 */
	@SerializedName("type")
	@Expose
	private Double type;
	/**
	 * The actual message
	 * (Required)
	 *
	 */
	@SerializedName("message")
	@Expose
	private String message;
	/**
	 * The message action items to present.
	 *
	 */
	@SerializedName("actions")
	@Expose
	private List<MessageActionItem> actions = new ArrayList<>();

	/**
	 * The message type. See {
	 * (Required)
	 *
	 * @return
	 *     The type
	 */
	public Double getType() {
		return type;
	}

	/**
	 * The message type. See {
	 * (Required)
	 *
	 * @param type
	 *     The type
	 */
	public void setType(Double type) {
		this.type = type;
	}

	public ShowMessageRequestParams withType(Double type) {
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

	public ShowMessageRequestParams withMessage(String message) {
		this.message = message;
		return this;
	}

	/**
	 * The message action items to present.
	 *
	 * @return
	 *     The actions
	 */
	public List<MessageActionItem> getActions() {
		return actions;
	}

	/**
	 * The message action items to present.
	 *
	 * @param actions
	 *     The actions
	 */
	public void setActions(List<MessageActionItem> actions) {
		this.actions = actions;
	}

	public ShowMessageRequestParams withActions(List<MessageActionItem> actions) {
		this.actions = actions;
		return this;
	}

}
