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
public class WorkspaceEdit {

    /**
     * Holds changes to existing resources.
     * 
     */
    @SerializedName("changes")
    @Expose
    private Changes changes;

    /**
     * Holds changes to existing resources.
     * 
     * @return
     *     The changes
     */
    public Changes getChanges() {
        return changes;
    }

    /**
     * Holds changes to existing resources.
     * 
     * @param changes
     *     The changes
     */
    public void setChanges(Changes changes) {
        this.changes = changes;
    }

    public WorkspaceEdit withChanges(Changes changes) {
        this.changes = changes;
        return this;
    }

}
