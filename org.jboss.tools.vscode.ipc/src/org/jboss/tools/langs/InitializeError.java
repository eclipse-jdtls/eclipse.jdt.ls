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

public class InitializeError {

	/**
	 * Indicates whether the client should retry to send the
	 *
	 * initilize request after showing the message provided
	 *
	 * in the {
	 * (Required)
	 *
	 */
	@SerializedName("retry")
	@Expose
	private Boolean retry;

	/**
	 * Indicates whether the client should retry to send the
	 *
	 * initilize request after showing the message provided
	 *
	 * in the {
	 * (Required)
	 *
	 * @return
	 *     The retry
	 */
	public Boolean getRetry() {
		return retry;
	}

	/**
	 * Indicates whether the client should retry to send the
	 *
	 * initilize request after showing the message provided
	 *
	 * in the {
	 * (Required)
	 *
	 * @param retry
	 *     The retry
	 */
	public void setRetry(Boolean retry) {
		this.retry = retry;
	}

	public InitializeError withRetry(Boolean retry) {
		this.retry = retry;
		return this;
	}

}
