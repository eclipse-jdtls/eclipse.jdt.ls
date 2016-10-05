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

import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;

public class SymbolInformation {

	/**
	 * The name of this symbol.
	 * (Required)
	 *
	 */
	@SerializedName("name")
	@Expose
	private String name;
	/**
	 * The kind of this symbol.
	 * (Required)
	 *
	 */
	@SerializedName("kind")
	@Expose
	private Integer kind;
	/**
	 *
	 * (Required)
	 *
	 */
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
	 * (Required)
	 *
	 * @return
	 *     The name
	 */
	public String getName() {
		return name;
	}

	/**
	 * The name of this symbol.
	 * (Required)
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
	 * (Required)
	 *
	 * @return
	 *     The kind
	 */
	public Integer getKind() {
		return kind;
	}

	/**
	 * The kind of this symbol.
	 * (Required)
	 *
	 * @param kind
	 *     The kind
	 */
	public void setKind(Integer kind) {
		this.kind = kind;
	}

	public SymbolInformation withKind(Integer kind) {
		this.kind = kind;
		return this;
	}

	/**
	 *
	 * (Required)
	 *
	 * @return
	 *     The location
	 */
	public Location getLocation() {
		return location;
	}

	/**
	 *
	 * (Required)
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
