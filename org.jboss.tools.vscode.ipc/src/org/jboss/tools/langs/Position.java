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


/**
 * Position in a text document expressed as zero-based line and character offset.
 *
 * The Position namespace provides helper functions to work with
 *
 * [Position](#Position) literals.
 *
 */
public class Position {

	/**
	 * Line position in a document (zero-based).
	 * (Required)
	 *
	 */
	@SerializedName("line")
	@Expose
	private Integer line;
	/**
	 * Character offset on a line in a document (zero-based).
	 * (Required)
	 *
	 */
	@SerializedName("character")
	@Expose
	private Integer character;

	/**
	 * Line position in a document (zero-based).
	 * (Required)
	 *
	 * @return
	 *     The line
	 */
	public Integer getLine() {
		return line;
	}

	/**
	 * Line position in a document (zero-based).
	 * (Required)
	 *
	 * @param line
	 *     The line
	 */
	public void setLine(Integer line) {
		this.line = line;
	}

	public Position withLine(Integer line) {
		this.line = line;
		return this;
	}

	/**
	 * Character offset on a line in a document (zero-based).
	 * (Required)
	 *
	 * @return
	 *     The character
	 */
	public Integer getCharacter() {
		return character;
	}

	/**
	 * Character offset on a line in a document (zero-based).
	 * (Required)
	 *
	 * @param character
	 *     The character
	 */
	public void setCharacter(Integer character) {
		this.character = character;
	}

	public Position withCharacter(Integer character) {
		this.character = character;
		return this;
	}

}
