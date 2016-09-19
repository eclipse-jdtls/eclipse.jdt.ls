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
public class WorkspaceSymbolParams {

    /**
     * A non-empty query string
     * 
     */
    @SerializedName("query")
    @Expose
    private String query;

    /**
     * A non-empty query string
     * 
     * @return
     *     The query
     */
    public String getQuery() {
        return query;
    }

    /**
     * A non-empty query string
     * 
     * @param query
     *     The query
     */
    public void setQuery(String query) {
        this.query = query;
    }

    public WorkspaceSymbolParams withQuery(String query) {
        this.query = query;
        return this;
    }

}
