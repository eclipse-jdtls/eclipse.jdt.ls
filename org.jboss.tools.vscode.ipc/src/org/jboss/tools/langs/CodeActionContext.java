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
public class CodeActionContext {

    /**
     * An array of diagnostics.
     * 
     */
    @SerializedName("diagnostics")
    @Expose
    private List<Diagnostic> diagnostics = new ArrayList<Diagnostic>();

    /**
     * An array of diagnostics.
     * 
     * @return
     *     The diagnostics
     */
    public List<Diagnostic> getDiagnostics() {
        return diagnostics;
    }

    /**
     * An array of diagnostics.
     * 
     * @param diagnostics
     *     The diagnostics
     */
    public void setDiagnostics(List<Diagnostic> diagnostics) {
        this.diagnostics = diagnostics;
    }

    public CodeActionContext withDiagnostics(List<Diagnostic> diagnostics) {
        this.diagnostics = diagnostics;
        return this;
    }

}
