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

public class CodeLens {

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
	 */
	@SerializedName("command")
	@Expose
	private Command command;
	/**
	 * An data entry field that is preserved on a code lens item between
	 *
	 * a [CodeLensRequest](#CodeLensRequest) and a [CodeLensResolveRequest]
	 *
	 * (#CodeLensResolveRequest)
	 *
	 */
	@SerializedName("data")
	@Expose
	private Object data;

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

	public CodeLens withRange(Range range) {
		this.range = range;
		return this;
	}

	/**
	 *
	 * @return
	 *     The command
	 */
	public Command getCommand() {
		return command;
	}

	/**
	 *
	 * @param command
	 *     The command
	 */
	public void setCommand(Command command) {
		this.command = command;
	}

	public CodeLens withCommand(Command command) {
		this.command = command;
		return this;
	}

	/**
	 * An data entry field that is preserved on a code lens item between
	 *
	 * a [CodeLensRequest](#CodeLensRequest) and a [CodeLensResolveRequest]
	 *
	 * (#CodeLensResolveRequest)
	 *
	 * @return
	 *     The data
	 */
	public Object getData() {
		return data;
	}

	/**
	 * An data entry field that is preserved on a code lens item between
	 *
	 * a [CodeLensRequest](#CodeLensRequest) and a [CodeLensResolveRequest]
	 *
	 * (#CodeLensResolveRequest)
	 *
	 * @param data
	 *     The data
	 */
	public void setData(Object data) {
		this.data = data;
	}

	public CodeLens withData(Object data) {
		this.data = data;
		return this;
	}

}
