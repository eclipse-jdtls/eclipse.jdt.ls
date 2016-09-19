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

import javax.annotation.Generated;

import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;

@Generated("org.jsonschema2pojo")
public class SignatureHelp {

    /**
     * One or more signatures.
     * 
     */
    @SerializedName("signatures")
    @Expose
    private List<SignatureInformation> signatures = new ArrayList<SignatureInformation>();
    /**
     * The active signature.
     * 
     */
    @SerializedName("activeSignature")
    @Expose
    private Double activeSignature;
    /**
     * The active parameter of the active signature.
     * 
     */
    @SerializedName("activeParameter")
    @Expose
    private Double activeParameter;

    /**
     * One or more signatures.
     * 
     * @return
     *     The signatures
     */
    public List<SignatureInformation> getSignatures() {
        return signatures;
    }

    /**
     * One or more signatures.
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
    public Double getActiveSignature() {
        return activeSignature;
    }

    /**
     * The active signature.
     * 
     * @param activeSignature
     *     The activeSignature
     */
    public void setActiveSignature(Double activeSignature) {
        this.activeSignature = activeSignature;
    }

    public SignatureHelp withActiveSignature(Double activeSignature) {
        this.activeSignature = activeSignature;
        return this;
    }

    /**
     * The active parameter of the active signature.
     * 
     * @return
     *     The activeParameter
     */
    public Double getActiveParameter() {
        return activeParameter;
    }

    /**
     * The active parameter of the active signature.
     * 
     * @param activeParameter
     *     The activeParameter
     */
    public void setActiveParameter(Double activeParameter) {
        this.activeParameter = activeParameter;
    }

    public SignatureHelp withActiveParameter(Double activeParameter) {
        this.activeParameter = activeParameter;
        return this;
    }

}
