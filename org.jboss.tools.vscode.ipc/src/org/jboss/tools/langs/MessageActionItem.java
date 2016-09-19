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
public class MessageActionItem {

    /**
     * A short title like 'Retry', 'Open Log' etc.
     * 
     */
    @SerializedName("title")
    @Expose
    private String title;

    /**
     * A short title like 'Retry', 'Open Log' etc.
     * 
     * @return
     *     The title
     */
    public String getTitle() {
        return title;
    }

    /**
     * A short title like 'Retry', 'Open Log' etc.
     * 
     * @param title
     *     The title
     */
    public void setTitle(String title) {
        this.title = title;
    }

    public MessageActionItem withTitle(String title) {
        this.title = title;
        return this;
    }

}
