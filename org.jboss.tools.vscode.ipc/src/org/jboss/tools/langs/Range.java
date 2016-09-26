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

public class Range {

	/**
	 * Position in a text document expressed as zero-based line and character offset.
	 *
	 * The Position namespace provides helper functions to work with
	 *
	 * [Position](#Position) literals.
	 * (Required)
	 *
	 */
	@SerializedName("start")
	@Expose
	private Position start;
	/**
	 * Position in a text document expressed as zero-based line and character offset.
	 *
	 * The Position namespace provides helper functions to work with
	 *
	 * [Position](#Position) literals.
	 * (Required)
	 *
	 */
	@SerializedName("end")
	@Expose
	private Position end;

	/**
	 * Position in a text document expressed as zero-based line and character offset.
	 *
	 * The Position namespace provides helper functions to work with
	 *
	 * [Position](#Position) literals.
	 * (Required)
	 *
	 * @return
	 *     The start
	 */
	public Position getStart() {
		return start;
	}

	/**
	 * Position in a text document expressed as zero-based line and character offset.
	 *
	 * The Position namespace provides helper functions to work with
	 *
	 * [Position](#Position) literals.
	 * (Required)
	 *
	 * @param start
	 *     The start
	 */
	public void setStart(Position start) {
		this.start = start;
	}

	public Range withStart(Position start) {
		this.start = start;
		return this;
	}

	/**
	 * Position in a text document expressed as zero-based line and character offset.
	 *
	 * The Position namespace provides helper functions to work with
	 *
	 * [Position](#Position) literals.
	 * (Required)
	 *
	 * @return
	 *     The end
	 */
	public Position getEnd() {
		return end;
	}

	/**
	 * Position in a text document expressed as zero-based line and character offset.
	 *
	 * The Position namespace provides helper functions to work with
	 *
	 * [Position](#Position) literals.
	 * (Required)
	 *
	 * @param end
	 *     The end
	 */
	public void setEnd(Position end) {
		this.end = end;
	}

	public Range withEnd(Position end) {
		this.end = end;
		return this;
	}

}
