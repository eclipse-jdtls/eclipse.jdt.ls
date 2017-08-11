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

import com.sun.jdi.Type;
import com.sun.jdi.Value;

import java.util.Map;

public class NullObjectFormatter implements IValueFormatter {
    public static final String NULL_STRING = "null";

    @Override
    public String toString(Object value, Map<String, Object> options) {
        return NULL_STRING;
    }

    @Override
    public boolean acceptType(Type type, Map<String, Object> options) {
        return type == null;
    }

    @Override
    public Value valueOf(String value, Type type, Map<String, Object> options) {
        if (value == null || NULL_STRING.equals(value)) {
            return null;
        }
        throw new UnsupportedOperationException("Set value is not supported by NullObjectFormatter.");
    }

}
