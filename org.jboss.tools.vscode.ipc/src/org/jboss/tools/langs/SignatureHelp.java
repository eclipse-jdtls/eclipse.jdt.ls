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

public class SignatureHelp {

	/**
	 * One or more signatures.
	 * (Required)
	 *
	 */
	@SerializedName("signatures")
	@Expose
	private List<SignatureInformation> signatures = new ArrayList<>();
	/**
	 * The active signature.
	 *
	 */
	@SerializedName("activeSignature")
	@Expose
	private Integer activeSignature;
	/**
	 * The active parameter of the active signature.
	 *
	 */
	@SerializedName("activeParameter")
	@Expose
	private Integer activeParameter;

	/**
	 * One or more signatures.
	 * (Required)
	 *
	 * @return
	 *     The signatures
	 */
	public List<SignatureInformation> getSignatures() {
		return signatures;
	}

	/**
	 * One or more signatures.
	 * (Required)
	 *
	 * @param signatures
	 *     The signatures
	 */
	public void setSignatures(List<SignatureInformation> signatures) {
		this.signatures = signatures;
	}

	public SignatureHelp withSignatures(List<SignatureInformation> signatures) {
		this.signatures = signatures;
		return this;
	}

	/**
	 * The active signature.
	 *
	 * @return
	 *     The activeSignature
	 */
	public Integer getActiveSignature() {
		return activeSignature;
	}

	/**
	 * The active signature.
	 *
	 * @param activeSignature
	 *     The activeSignature
	 */
	public void setActiveSignature(Integer activeSignature) {
		this.activeSignature = activeSignature;
	}

	public SignatureHelp withActiveSignature(Integer activeSignature) {
		this.activeSignature = activeSignature;
		return this;
	}

	/**
	 * The active parameter of the active signature.
	 *
	 * @return
	 *     The activeParameter
	 */
	public Integer getActiveParameter() {
		return activeParameter;
	}

	/**
	 * The active parameter of the active signature.
	 *
	 * @param activeParameter
	 *     The activeParameter
	 */
	public void setActiveParameter(Integer activeParameter) {
		this.activeParameter = activeParameter;
	}

	public SignatureHelp withActiveParameter(Integer activeParameter) {
		this.activeParameter = activeParameter;
		return this;
	}

}
