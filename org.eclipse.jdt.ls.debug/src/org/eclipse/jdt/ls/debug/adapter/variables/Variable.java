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

import com.sun.jdi.Field;
import com.sun.jdi.LocalVariable;
import com.sun.jdi.Type;
import com.sun.jdi.Value;
import org.apache.commons.lang3.StringUtils;

/**
 * This class represents a variable on a stopped stack frame, it contains more informations
 * about this variable:
 * <ul>
 * <li>
 * The field if this variable is a field value.
 * </li>
 * <li>
 * The local variable information if this variable is a local variable.
 * </li>
 * <li>
 * The argument index if this variable is an argument variable.
 * </li>
 * </ul>
 * The above informations are for further formatter to compose an more detailed
 * name for name conflict situation.
 */
public class Variable {
    /**
     * The JDI value.
     */
    public Value value;

    /**
     * The name of this variable.
     */
    public String name;

    /**
     * The field information if this variable is a field value.
     */
    public Field field;

    /**
     * The local variable information if this variable is a local variable.
     */
    public LocalVariable local;

    /**
     * The argument index if this variable is an argument variable.
     */
    public int argumentIndex;

    /**
     * The constructor of <code>JavaVariable</code>.
     * @param name the name of this variable.
     * @param value the JDI value
     */
    public Variable(String name, Value value) {
        if (StringUtils.isBlank(name)) {
            throw new IllegalArgumentException("Name is required for a java variable.");
        }
        this.name = name;
        this.value = value;
        this.argumentIndex = -1;
    }

    /**
     * Get the declaring type of this variable if it is a field declared by some class.
     *
     * @return the declaring type of this variable.
     */
    public Type getDeclaringType() {
        if (this.field != null) {
            return this.field.declaringType();
        }
        return null;
    }
}
