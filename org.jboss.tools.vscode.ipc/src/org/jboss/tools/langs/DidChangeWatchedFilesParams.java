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

import javax.annotation.Generated;

import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;

@Generated("org.jsonschema2pojo")
public class DidChangeWatchedFilesParams {

    /**
     * The actual file events.
     * 
     */
    @SerializedName("changes")
    @Expose
    private List<FileEvent> changes = new ArrayList<FileEvent>();

    /**
     * The actual file events.
     * 
     * @return
     *     The changes
     */
    public List<FileEvent> getChanges() {
        return changes;
    }

    /**
     * The actual file events.
     * 
     * @param changes
     *     The changes
     */
    public void setChanges(List<FileEvent> changes) {
        this.changes = changes;
    }

    public DidChangeWatchedFilesParams withChanges(List<FileEvent> changes) {
        this.changes = changes;
        return this;
    }

}
