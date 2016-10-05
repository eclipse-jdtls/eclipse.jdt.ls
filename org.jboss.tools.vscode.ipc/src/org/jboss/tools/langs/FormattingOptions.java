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

public class FormattingOptions {

	/**
	 * Size of a tab in spaces.
	 * (Required)
	 *
	 */
	@SerializedName("tabSize")
	@Expose
	private Integer tabSize;
	/**
	 * Prefer spaces over tabs.
	 * (Required)
	 *
	 */
	@SerializedName("insertSpaces")
	@Expose
	private Boolean insertSpaces;

	/**
	 * Size of a tab in spaces.
	 * (Required)
	 *
	 * @return
	 *     The tabSize
	 */
	public Integer getTabSize() {
		return tabSize;
	}

	/**
	 * Size of a tab in spaces.
	 * (Required)
	 *
	 * @param tabSize
	 *     The tabSize
	 */
	public void setTabSize(Integer tabSize) {
		this.tabSize = tabSize;
	}

	public FormattingOptions withTabSize(Integer tabSize) {
		this.tabSize = tabSize;
		return this;
	}

	/**
	 * Prefer spaces over tabs.
	 * (Required)
	 *
	 * @return
	 *     The insertSpaces
	 */
	public Boolean getInsertSpaces() {
		return insertSpaces;
	}

	/**
	 * Prefer spaces over tabs.
	 * (Required)
	 *
	 * @param insertSpaces
	 *     The insertSpaces
	 */
	public void setInsertSpaces(Boolean insertSpaces) {
		this.insertSpaces = insertSpaces;
	}

	public FormattingOptions withInsertSpaces(Boolean insertSpaces) {
		this.insertSpaces = insertSpaces;
		return this;
	}

}
