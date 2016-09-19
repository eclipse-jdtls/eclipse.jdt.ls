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
public class TextEditChange {

    /**
     * Gets all text edits for this change.
     * 
     */
    @SerializedName("all")
    @Expose
    private Object all;
    /**
     * Clears the edits for this change.
     * 
     */
    @SerializedName("clear")
    @Expose
    private Object clear;
    /**
     * Insert the given text at the given position.
     * 
     */
    @SerializedName("insert")
    @Expose
    private Object insert;
    /**
     * Replace the given range with given text for the given resource.
     * 
     */
    @SerializedName("replace")
    @Expose
    private Object replace;
    /**
     * Delete the text at the given range.
     * 
     */
    @SerializedName("delete")
    @Expose
    private Object delete;

    /**
     * Gets all text edits for this change.
     * 
     * @return
     *     The all
     */
    public Object getAll() {
        return all;
    }

    /**
     * Gets all text edits for this change.
     * 
     * @param all
     *     The all
     */
    public void setAll(Object all) {
        this.all = all;
    }

    public TextEditChange withAll(Object all) {
        this.all = all;
        return this;
    }

    /**
     * Clears the edits for this change.
     * 
     * @return
     *     The clear
     */
    public Object getClear() {
        return clear;
    }

    /**
     * Clears the edits for this change.
     * 
     * @param clear
     *     The clear
     */
    public void setClear(Object clear) {
        this.clear = clear;
    }

    public TextEditChange withClear(Object clear) {
        this.clear = clear;
        return this;
    }

    /**
     * Insert the given text at the given position.
     * 
     * @return
     *     The insert
     */
    public Object getInsert() {
        return insert;
    }

    /**
     * Insert the given text at the given position.
     * 
     * @param insert
     *     The insert
     */
    public void setInsert(Object insert) {
        this.insert = insert;
    }

    public TextEditChange withInsert(Object insert) {
        this.insert = insert;
        return this;
    }

    /**
     * Replace the given range with given text for the given resource.
     * 
     * @return
     *     The replace
     */
    public Object getReplace() {
        return replace;
    }

    /**
     * Replace the given range with given text for the given resource.
     * 
     * @param replace
     *     The replace
     */
    public void setReplace(Object replace) {
        this.replace = replace;
    }

    public TextEditChange withReplace(Object replace) {
        this.replace = replace;
        return this;
    }

    /**
     * Delete the text at the given range.
     * 
     * @return
     *     The delete
     */
    public Object getDelete() {
        return delete;
    }

    /**
     * Delete the text at the given range.
     * 
     * @param delete
     *     The delete
     */
    public void setDelete(Object delete) {
        this.delete = delete;
    }

    public TextEditChange withDelete(Object delete) {
        this.delete = delete;
        return this;
    }

}
