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

package org.eclipse.jdt.ls.debug.adapter.formatter;

import java.util.HashMap;
import java.util.Map;

import com.sun.jdi.Type;

public interface IFormatter {

    /**
     * Get the string representations for an object.
     *
     * @param value the value
     * @param options additional information about expected format.
     * @return the string representations.
     */
    String toString(Object value, Map<String, Object> options);

    /**
     * The conditional function for this formatter.
     *
     * @param type the JDI type
     * @param options additional information about expected format
     * @return whether or not this formatter is expected to work on this type.
     */
    boolean acceptType(Type type, Map<String, Object> options);

    /**
     * Get the default options for this formatter.
     * @return the default options
     */
    default Map<String, Object> getDefaultOptions() {
        return new HashMap<>();
    }
}
