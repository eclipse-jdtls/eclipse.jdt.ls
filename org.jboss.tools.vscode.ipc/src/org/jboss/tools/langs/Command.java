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

public class Command {

	/**
	 * Title of the command, like `save`.
	 * (Required)
	 *
	 */
	@SerializedName("title")
	@Expose
	private String title;
	/**
	 * The identifier of the actual command handler.
	 * (Required)
	 *
	 */
	@SerializedName("command")
	@Expose
	private String command;
	/**
	 * Arguments that the command handler should be
	 *
	 * invoked with.
	 *
	 */
	@SerializedName("arguments")
	@Expose
	private List<Object> arguments = new ArrayList<>();

	/**
	 * Title of the command, like `save`.
	 * (Required)
	 *
	 * @return
	 *     The title
	 */
	public String getTitle() {
		return title;
	}

	/**
	 * Title of the command, like `save`.
	 * (Required)
	 *
	 * @param title
	 *     The title
	 */
	public void setTitle(String title) {
		this.title = title;
	}

	public Command withTitle(String title) {
		this.title = title;
		return this;
	}

	/**
	 * The identifier of the actual command handler.
	 * (Required)
	 *
	 * @return
	 *     The command
	 */
	public String getCommand() {
		return command;
	}

	/**
	 * The identifier of the actual command handler.
	 * (Required)
	 *
	 * @param command
	 *     The command
	 */
	public void setCommand(String command) {
		this.command = command;
	}

	public Command withCommand(String command) {
		this.command = command;
		return this;
	}

	/**
	 * Arguments that the command handler should be
	 *
	 * invoked with.
	 *
	 * @return
	 *     The arguments
	 */
	public List<Object> getArguments() {
		return arguments;
	}

	/**
	 * Arguments that the command handler should be
	 *
	 * invoked with.
	 *
	 * @param arguments
	 *     The arguments
	 */
	public void setArguments(List<Object> arguments) {
		this.arguments = arguments;
	}

	public Command withArguments(List<Object> arguments) {
		this.arguments = arguments;
		return this;
	}

}
