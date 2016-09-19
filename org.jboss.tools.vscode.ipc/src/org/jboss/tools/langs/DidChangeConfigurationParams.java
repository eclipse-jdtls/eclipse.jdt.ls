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
public class DidChangeConfigurationParams {

    /**
     * The actual changed settings
     * 
     */
    @SerializedName("settings")
    @Expose
    private Object settings;

    /**
     * The actual changed settings
     * 
     * @return
     *     The settings
     */
    public Object getSettings() {
        return settings;
    }

    /**
     * The actual changed settings
     * 
     * @param settings
     *     The settings
     */
    public void setSettings(Object settings) {
        this.settings = settings;
    }

    public DidChangeConfigurationParams withSettings(Object settings) {
        this.settings = settings;
        return this;
    }

}
