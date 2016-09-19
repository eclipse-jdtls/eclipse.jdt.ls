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
public class SymbolInformation {

    /**
     * The name of this symbol.
     * 
     */
    @SerializedName("name")
    @Expose
    private String name;
    /**
     * The kind of this symbol.
     * 
     */
    @SerializedName("kind")
    @Expose
    private Double kind;
    @SerializedName("location")
    @Expose
    private Location location;
    /**
     * The name of the symbol containing this symbol.
     * 
     */
    @SerializedName("containerName")
    @Expose
    private String containerName;

    /**
     * The name of this symbol.
     * 
     * @return
     *     The name
     */
    public String getName() {
        return name;
    }

    /**
     * The name of this symbol.
     * 
     * @param name
     *     The name
     */
    public void setName(String name) {
        this.name = name;
    }

    public SymbolInformation withName(String name) {
        this.name = name;
        return this;
    }

    /**
     * The kind of this symbol.
     * 
     * @return
     *     The kind
     */
    public Double getKind() {
        return kind;
    }

    /**
     * The kind of this symbol.
     * 
     * @param kind
     *     The kind
     */
    public void setKind(Double kind) {
        this.kind = kind;
    }

    public SymbolInformation withKind(Double kind) {
        this.kind = kind;
        return this;
    }

    /**
     * 
     * @return
     *     The location
     */
    public Location getLocation() {
        return location;
    }

    /**
     * 
     * @param location
     *     The location
     */
    public void setLocation(Location location) {
        this.location = location;
    }

    public SymbolInformation withLocation(Location location) {
        this.location = location;
        return this;
    }

    /**
     * The name of the symbol containing this symbol.
     * 
     * @return
     *     The containerName
     */
    public String getContainerName() {
        return containerName;
    }

    /**
     * The name of the symbol containing this symbol.
     * 
     * @param containerName
     *     The containerName
     */
    public void setContainerName(String containerName) {
        this.containerName = containerName;
    }

    public SymbolInformation withContainerName(String containerName) {
        this.containerName = containerName;
        return this;
    }

}
