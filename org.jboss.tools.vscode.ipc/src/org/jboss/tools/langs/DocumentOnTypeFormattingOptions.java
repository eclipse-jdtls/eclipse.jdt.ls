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
 * Format document on type options
 *
 */
public class DocumentOnTypeFormattingOptions {

	/**
	 * A character on which formatting should be triggered, like `}`.
	 * (Required)
	 *
	 */
	@SerializedName("firstTriggerCharacter")
	@Expose
	private String firstTriggerCharacter;
	/**
	 * More trigger characters.
	 *
	 */
	@SerializedName("moreTriggerCharacter")
	@Expose
	private List<String> moreTriggerCharacter = new ArrayList<>();

	/**
	 * A character on which formatting should be triggered, like `}`.
	 * (Required)
	 *
	 * @return
	 *     The firstTriggerCharacter
	 */
	public String getFirstTriggerCharacter() {
		return firstTriggerCharacter;
	}

	/**
	 * A character on which formatting should be triggered, like `}`.
	 * (Required)
	 *
	 * @param firstTriggerCharacter
	 *     The firstTriggerCharacter
	 */
	public void setFirstTriggerCharacter(String firstTriggerCharacter) {
		this.firstTriggerCharacter = firstTriggerCharacter;
	}

	public DocumentOnTypeFormattingOptions withFirstTriggerCharacter(String firstTriggerCharacter) {
		this.firstTriggerCharacter = firstTriggerCharacter;
		return this;
	}

	/**
	 * More trigger characters.
	 *
	 * @return
	 *     The moreTriggerCharacter
	 */
	public List<String> getMoreTriggerCharacter() {
		return moreTriggerCharacter;
	}

	/**
	 * More trigger characters.
	 *
	 * @param moreTriggerCharacter
	 *     The moreTriggerCharacter
	 */
	public void setMoreTriggerCharacter(List<String> moreTriggerCharacter) {
		this.moreTriggerCharacter = moreTriggerCharacter;
	}

	public DocumentOnTypeFormattingOptions withMoreTriggerCharacter(List<String> moreTriggerCharacter) {
		this.moreTriggerCharacter = moreTriggerCharacter;
		return this;
	}

}
