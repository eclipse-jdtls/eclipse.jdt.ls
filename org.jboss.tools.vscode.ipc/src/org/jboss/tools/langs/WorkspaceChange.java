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

import javax.annotation.Generated;

import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;

@Generated("org.jsonschema2pojo")
public class WorkspaceChange {

    @SerializedName("workspaceEdit")
    @Expose
    private Object workspaceEdit;
    @SerializedName("textEditChanges")
    @Expose
    private Object textEditChanges;
    @SerializedName("edit")
    @Expose
    private WorkspaceEdit edit;
    /**
     * Returns the [TextEditChange](#TextEditChange) to manage text edits
     * 
     * for resources.
     * 
     */
    @SerializedName("getTextEditChange")
    @Expose
    private Object getTextEditChange;

    /**
     * 
     * @return
     *     The workspaceEdit
     */
    public Object getWorkspaceEdit() {
        return workspaceEdit;
    }

    /**
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
     * @return
     *     The textEditChanges
     */
    public Object getTextEditChanges() {
        return textEditChanges;
    }

    /**
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
     * @return
     *     The edit
     */
    public WorkspaceEdit getEdit() {
        return edit;
    }

    /**
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
