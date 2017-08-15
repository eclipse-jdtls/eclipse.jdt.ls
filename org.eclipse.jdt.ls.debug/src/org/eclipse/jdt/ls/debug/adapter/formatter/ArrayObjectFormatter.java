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

import static org.eclipse.jdt.ls.debug.adapter.formatter.TypeIdentifiers.ARRAY;

import java.util.Map;
import java.util.function.BiFunction;

import com.sun.jdi.ArrayReference;
import com.sun.jdi.ObjectReference;
import com.sun.jdi.Type;
import com.sun.jdi.Value;

public class ArrayObjectFormatter extends ObjectFormatter {
    public ArrayObjectFormatter(BiFunction<Type, Map<String, Object>, String> typeStringFunction) {
        super(typeStringFunction);
    }

    @Override
    protected String getPrefix(ObjectReference value, Map<String, Object> options) {
        String arrayTypeWithLength = String.format("[%s]",
                NumericFormatter.formatNumber(arrayLength(value), options));
        return super.getPrefix(value, options).replaceFirst("\\[]", arrayTypeWithLength);
    }

    @Override
    public boolean acceptType(Type type, Map<String, Object> options) {
        return type != null && type.signature().charAt(0) == ARRAY;
    }

    private static int arrayLength(Value value) {
        return ((ArrayReference) value).length();
    }
}
