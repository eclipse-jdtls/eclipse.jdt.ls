/*******************************************************************************
 * Copyright (c) 2017 Microsoft Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Microsoft Corporation - initial API and implementation
 *******************************************************************************/

package org.eclipse.jdt.ls.debug.adapter.variables;

import java.util.Objects;

public class JdiObjectProxy<T> {
    private final T object;
    private final int hashCode;


    /**
     * Create a jdi object proxy.
     *
     * @param object the underling jdi object
     */
    public JdiObjectProxy(T object) {
        if (object == null) {
            throw new IllegalArgumentException("Null object is illegal for JdiObjectProxy.");
        }
        this.object = object;
        this.hashCode = object.hashCode();

    }

    @Override
    public String toString() {
        return String.valueOf(getProxiedObject());
    }

    @Override
    public int hashCode() {
        return hashCode;
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof JdiObjectProxy)) {
            return false;
        }
        final JdiObjectProxy other = (JdiObjectProxy) o;
        return Objects.equals(this.getProxiedObject(), other.getProxiedObject());
    }

    public T getProxiedObject() {
        return object;
    }

}
