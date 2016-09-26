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

public class WorkspaceChange {

	/**
	 *
	 * (Required)
	 *
	 */
	@SerializedName("workspaceEdit")
	@Expose
	private Object workspaceEdit;
	/**
	 *
	 * (Required)
	 *
	 */
	@SerializedName("textEditChanges")
	@Expose
	private Object textEditChanges;
	/**
	 *
	 * (Required)
	 *
	 */
	@SerializedName("edit")
	@Expose
	private WorkspaceEdit edit;
	/**
	 * Returns the [TextEditChange](#TextEditChange) to manage text edits
	 *
	 * for resources.
	 * (Required)
	 *
	 */
	@SerializedName("getTextEditChange")
	@Expose
	private Object getTextEditChange;

	/**
	 *
	 * (Required)
	 *
	 * @return
	 *     The workspaceEdit
	 */
	public Object getWorkspaceEdit() {
		return workspaceEdit;
	}

	/**
	 *
	 * (Required)
	 *
	 * @param workspaceEdit
	 *     The workspaceEdit
	 */
	public void setWorkspaceEdit(Object workspaceEdit) {
		this.workspaceEdit = workspaceEdit;
	}

	public WorkspaceChange withWorkspaceEdit(Object workspaceEdit) {
		this.workspaceEdit = workspaceEdit;
		return this;
	}

	/**
	 *
	 * (Required)
	 *
	 * @return
	 *     The textEditChanges
	 */
	public Object getTextEditChanges() {
		return textEditChanges;
	}

	/**
	 *
	 * (Required)
	 *
	 * @param textEditChanges
	 *     The textEditChanges
	 */
	public void setTextEditChanges(Object textEditChanges) {
		this.textEditChanges = textEditChanges;
	}

	public WorkspaceChange withTextEditChanges(Object textEditChanges) {
		this.textEditChanges = textEditChanges;
		return this;
	}

	/**
	 *
	 * (Required)
	 *
	 * @return
	 *     The edit
	 */
	public WorkspaceEdit getEdit() {
		return edit;
	}

	/**
	 *
	 * (Required)
	 *
	 * @param edit
	 *     The edit
	 */
	public void setEdit(WorkspaceEdit edit) {
		this.edit = edit;
	}

	public WorkspaceChange withEdit(WorkspaceEdit edit) {
		this.edit = edit;
		return this;
	}

	/**
	 * Returns the [TextEditChange](#TextEditChange) to manage text edits
	 *
	 * for resources.
	 * (Required)
	 *
	 * @return
	 *     The getTextEditChange
	 */
	public Object getGetTextEditChange() {
		return getTextEditChange;
	}

	/**
	 * Returns the [TextEditChange](#TextEditChange) to manage text edits
	 *
	 * for resources.
	 * (Required)
	 *
	 * @param getTextEditChange
	 *     The getTextEditChange
	 */
	public void setGetTextEditChange(Object getTextEditChange) {
		this.getTextEditChange = getTextEditChange;
	}

	public WorkspaceChange withGetTextEditChange(Object getTextEditChange) {
		this.getTextEditChange = getTextEditChange;
		return this;
	}

}
