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

import static org.eclipse.jdt.ls.debug.adapter.formatter.TypeIdentifiers.CHAR;

import java.util.Map;

import com.sun.jdi.Type;
import com.sun.jdi.Value;
import com.sun.jdi.VirtualMachine;

public class CharacterFormatter implements IValueFormatter {

    @Override
    public String toString(Object value, Map<String, Object> options) {
        return value == null ? NullObjectFormatter.NULL_STRING : value.toString();
    }

    @Override
    public boolean acceptType(Type type, Map<String, Object> options) {
        if (type == null) {
            return false;
        }
        char signature0 = type.signature().charAt(0);
        return signature0 == CHAR;
    }

    @Override
    public Value valueOf(String value, Type type, Map<String, Object> options) {
        VirtualMachine vm = type.virtualMachine();
        if (value == null) {
            return null;
        }
        if (value.length() == 3
                && value.startsWith("'")
                && value.endsWith("'")) {
            return type.virtualMachine().mirrorOf(value.charAt(1));
        }
        return vm.mirrorOf(value.charAt(0));
    }
}
