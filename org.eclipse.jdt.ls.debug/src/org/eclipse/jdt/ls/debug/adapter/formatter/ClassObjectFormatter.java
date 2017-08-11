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

import static org.eclipse.jdt.ls.debug.adapter.formatter.TypeIdentifiers.CLASS_OBJECT;
import static org.eclipse.jdt.ls.debug.adapter.formatter.TypeIdentifiers.CLASS_SIGNATURE;

import java.util.Map;
import java.util.function.BiFunction;

import com.sun.jdi.ClassObjectReference;
import com.sun.jdi.ObjectReference;
import com.sun.jdi.Type;

public class ClassObjectFormatter extends ObjectFormatter {
    public ClassObjectFormatter(BiFunction<Type, Map<String, Object>, String> typeStringFunction) {
        super(typeStringFunction);
    }

    @Override
    protected String getPrefix(ObjectReference value, Map<String, Object> options) {
        Type classType = ((ClassObjectReference) value).reflectedType();
        return String.format("%s (%s)", super.getPrefix(value, options),
                typeToStringFunction.apply(classType, options));
    }

    @Override
    public boolean acceptType(Type type, Map<String, Object> options) {
        return super.acceptType(type, options) && (type.signature().charAt(0) == CLASS_OBJECT
                || type.signature().equals(CLASS_SIGNATURE));
    }
}
