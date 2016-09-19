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
public class Hover {

    /**
     * The hover's content
     * 
     */
    @SerializedName("contents")
    @Expose
    private Object contents;
    @SerializedName("range")
    @Expose
    private Range range;

    /**
     * The hover's content
     * 
     * @return
     *     The contents
     */
    public Object getContents() {
        return contents;
    }

    /**
     * The hover's content
     * 
     * @param contents
     *     The contents
     */
    public void setContents(Object contents) {
        this.contents = contents;
    }

    public Hover withContents(Object contents) {
        this.contents = contents;
        return this;
    }

    /**
     * 
     * @return
     *     The range
     */
    public Range getRange() {
        return range;
    }

    /**
     * 
     * @param range
     *     The range
     */
    public void setRange(Range range) {
        this.range = range;
    }

    public Hover withRange(Range range) {
        this.range = range;
        return this;
    }

}
