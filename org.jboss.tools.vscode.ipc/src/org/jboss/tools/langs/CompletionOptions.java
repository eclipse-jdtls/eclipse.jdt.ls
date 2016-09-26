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

import java.util.ArrayList;
import java.util.List;

import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;


/**
 * Completion options.
 *
 */
public class CompletionOptions {

	/**
	 * The server provides support to resolve additional
	 *
	 * information for a completion item.
	 *
	 */
	@SerializedName("resolveProvider")
	@Expose
	private Boolean resolveProvider;
	/**
	 * The characters that trigger completion automatically.
	 *
	 */
	@SerializedName("triggerCharacters")
	@Expose
	private List<String> triggerCharacters = new ArrayList<>();

	/**
	 * The server provides support to resolve additional
	 *
	 * information for a completion item.
	 *
	 * @return
	 *     The resolveProvider
	 */
	public Boolean getResolveProvider() {
		return resolveProvider;
	}

	/**
	 * The server provides support to resolve additional
	 *
	 * information for a completion item.
	 *
	 * @param resolveProvider
	 *     The resolveProvider
	 */
	public void setResolveProvider(Boolean resolveProvider) {
		this.resolveProvider = resolveProvider;
	}

	public CompletionOptions withResolveProvider(Boolean resolveProvider) {
		this.resolveProvider = resolveProvider;
		return this;
	}

	/**
	 * The characters that trigger completion automatically.
	 *
	 * @return
	 *     The triggerCharacters
	 */
	public List<String> getTriggerCharacters() {
		return triggerCharacters;
	}

	/**
	 * The characters that trigger completion automatically.
	 *
	 * @param triggerCharacters
	 *     The triggerCharacters
	 */
	public void setTriggerCharacters(List<String> triggerCharacters) {
		this.triggerCharacters = triggerCharacters;
	}

	public CompletionOptions withTriggerCharacters(List<String> triggerCharacters) {
		this.triggerCharacters = triggerCharacters;
		return this;
	}

}
