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

public class ReferenceContext {

	/**
	 * Include the declaration of the current symbol.
	 * (Required)
	 *
	 */
	@SerializedName("includeDeclaration")
	@Expose
	private Boolean includeDeclaration;

	/**
	 * Include the declaration of the current symbol.
	 * (Required)
	 *
	 * @return
	 *     The includeDeclaration
	 */
	public Boolean getIncludeDeclaration() {
		return includeDeclaration;
	}

	/**
	 * Include the declaration of the current symbol.
	 * (Required)
	 *
	 * @param includeDeclaration
	 *     The includeDeclaration
	 */
	public void setIncludeDeclaration(Boolean includeDeclaration) {
		this.includeDeclaration = includeDeclaration;
	}

	public ReferenceContext withIncludeDeclaration(Boolean includeDeclaration) {
		this.includeDeclaration = includeDeclaration;
		return this;
	}

}
