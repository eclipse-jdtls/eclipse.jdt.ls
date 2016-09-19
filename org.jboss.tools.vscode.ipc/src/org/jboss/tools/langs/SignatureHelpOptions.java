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


/**
 * Signature help options.
 * 
 */
@Generated("org.jsonschema2pojo")
public class SignatureHelpOptions {

    /**
     * The characters that trigger signature help
     * 
     * automatically.
     * 
     */
    @SerializedName("triggerCharacters")
    @Expose
    private List<String> triggerCharacters = new ArrayList<String>();

    /**
     * The characters that trigger signature help
     * 
     * automatically.
     * 
     * @return
     *     The triggerCharacters
     */
    public List<String> getTriggerCharacters() {
        return triggerCharacters;
    }

    /**
     * The characters that trigger signature help
     * 
     * automatically.
     * 
     * @param triggerCharacters
     *     The triggerCharacters
     */
    public void setTriggerCharacters(List<String> triggerCharacters) {
        this.triggerCharacters = triggerCharacters;
    }

    public SignatureHelpOptions withTriggerCharacters(List<String> triggerCharacters) {
        this.triggerCharacters = triggerCharacters;
        return this;
    }

}
