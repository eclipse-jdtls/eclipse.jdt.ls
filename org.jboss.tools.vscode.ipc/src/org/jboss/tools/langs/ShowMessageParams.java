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
public class ShowMessageParams {

    /**
     * The message type. See {
     * 
     */
    @SerializedName("type")
    @Expose
    private Double type;
    /**
     * The actual message
     * 
     */
    @SerializedName("message")
    @Expose
    private String message;

    /**
     * The message type. See {
     * 
     * @return
     *     The type
     */
    public Double getType() {
        return type;
    }

    /**
     * The message type. See {
     * 
     * @param type
     *     The type
     */
    public void setType(Double type) {
        this.type = type;
    }

    public ShowMessageParams withType(Double type) {
        this.type = type;
        return this;
    }

    /**
     * The actual message
     * 
     * @return
     *     The message
     */
    public String getMessage() {
        return message;
    }

    /**
     * The actual message
     * 
     * @param message
     *     The message
     */
    public void setMessage(String message) {
        this.message = message;
    }

    public ShowMessageParams withMessage(String message) {
        this.message = message;
        return this;
    }

}
