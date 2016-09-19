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

@Generated("org.jsonschema2pojo")
public class VersionedTextDocumentIdentifier {

    /**
     * The version number of this document.
     * 
     */
    @SerializedName("version")
    @Expose
    private Double version;
    /**
     * The text document's uri.
     * 
     */
    @SerializedName("uri")
    @Expose
    private String uri;

    /**
     * The version number of this document.
     * 
     * @return
     *     The version
     */
    public Double getVersion() {
        return version;
    }

    /**
     * The version number of this document.
     * 
     * @param version
     *     The version
     */
    public void setVersion(Double version) {
        this.version = version;
    }

    public VersionedTextDocumentIdentifier withVersion(Double version) {
        this.version = version;
        return this;
    }

    /**
     * The text document's uri.
     * 
     * @return
     *     The uri
     */
    public String getUri() {
        return uri;
    }

    /**
     * The text document's uri.
     * 
     * @param uri
     *     The uri
     */
    public void setUri(String uri) {
        this.uri = uri;
    }

    public VersionedTextDocumentIdentifier withUri(String uri) {
        this.uri = uri;
        return this;
    }

}
