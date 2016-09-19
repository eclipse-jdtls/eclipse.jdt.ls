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


/**
 * Code Lens options.
 * 
 */
@Generated("org.jsonschema2pojo")
public class CodeLensOptions {

    /**
     * Code lens has a resolve provider as well.
     * 
     */
    @SerializedName("resolveProvider")
    @Expose
    private Boolean resolveProvider;

    /**
     * Code lens has a resolve provider as well.
     * 
     * @return
     *     The resolveProvider
     */
    public Boolean getResolveProvider() {
        return resolveProvider;
    }

    /**
     * Code lens has a resolve provider as well.
     * 
     * @param resolveProvider
     *     The resolveProvider
     */
    public void setResolveProvider(Boolean resolveProvider) {
        this.resolveProvider = resolveProvider;
    }

    public CodeLensOptions withResolveProvider(Boolean resolveProvider) {
        this.resolveProvider = resolveProvider;
        return this;
    }

}
