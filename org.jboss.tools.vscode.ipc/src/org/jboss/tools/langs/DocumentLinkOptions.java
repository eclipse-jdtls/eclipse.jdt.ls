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


/**
 * Document link options
 *
 */
public class DocumentLinkOptions {

	/**
	 * Document links have a resolve provider as well.
	 *
	 */
	@SerializedName("resolveProvider")
	@Expose
	private Boolean resolveProvider;

	/**
	 * Document links have a resolve provider as well.
	 *
	 * @return
	 *     The resolveProvider
	 */
	public Boolean getResolveProvider() {
		return resolveProvider;
	}

	/**
	 * Document links have a resolve provider as well.
	 *
	 * @param resolveProvider
	 *     The resolveProvider
	 */
	public void setResolveProvider(Boolean resolveProvider) {
		this.resolveProvider = resolveProvider;
	}

	public DocumentLinkOptions withResolveProvider(Boolean resolveProvider) {
		this.resolveProvider = resolveProvider;
		return this;
	}

}
