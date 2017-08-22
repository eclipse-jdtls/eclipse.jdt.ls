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

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import com.sun.jdi.InternalException;
import org.eclipse.jdt.ls.debug.internal.Logger;

import com.sun.jdi.AbsentInformationException;
import com.sun.jdi.ArrayReference;
import com.sun.jdi.ArrayType;
import com.sun.jdi.Field;
import com.sun.jdi.LocalVariable;
import com.sun.jdi.ObjectReference;
import com.sun.jdi.ReferenceType;
import com.sun.jdi.StackFrame;
import com.sun.jdi.Type;
import com.sun.jdi.TypeComponent;
import com.sun.jdi.Value;

public abstract class VariableUtils {
    /**
     * Test whether the value has referenced objects.
     *
     * @param value
     *            the value.
     * @param includeStatic
     *            whether or not the static fields are visible.
     * @return true if this value is reference objects.
     */
    public static boolean hasChildren(Value value, boolean includeStatic) {
        if (value == null) {
            return false;
        }
        Type type = value.type();
        if (type instanceof ArrayType) {
            return ((ArrayReference) value).length() > 0;
        }
        return value.type() instanceof ReferenceType && ((ReferenceType) type).allFields().stream()
                .filter(t -> includeStatic || !t.isStatic()).toArray().length > 0;
    }
    
    /**
     * Get the variables of the object.
     *
     * @param obj
     *            the object
     * @return the variable list
     * @throws AbsentInformationException
     *             when there is any error in retrieving information
     */
    public static List<Variable> listFieldVariables(ObjectReference obj, boolean includeStatic) throws AbsentInformationException {
        List<Variable> res = new ArrayList<>();
        Type type = obj.type();
        if (type instanceof ArrayType) {
            int arrayIndex = 0;
            for (Value elementValue : ((ArrayReference) obj).getValues()) {
                Variable ele = new Variable(String.valueOf(arrayIndex++), elementValue);
                res.add(ele);
            }
            return res;
        }
        List<Field> fields = obj.referenceType().allFields().stream().filter(t -> includeStatic || !t.isStatic())
                .sorted((a, b) -> {
                    try {
                        boolean v1isStatic = a.isStatic();
                        boolean v2isStatic = b.isStatic();
                        if (v1isStatic && !v2isStatic) {
                            return -1;
                        }
                        if (!v1isStatic && v2isStatic) {
                            return 1;
                        }
                        return a.name().compareToIgnoreCase(b.name());
                    } catch (Exception e) {
                        Logger.logException("Cannot sort fields", e);
                        return -1;
                    }
                }).collect(Collectors.toList());
        fields.forEach(f -> {
            Variable var = new Variable(f.name(), obj.getValue(f));
            var.field = f;
            res.add(var);
        });
        return res;
    }

    /**
     * Get the variables of the object with pagination.
     *
     * @param obj
     *            the object
     * @param start
     *            the start of the pagination
     * @param count
     *            the number of variables needed
     * @return the variable list
     * @throws AbsentInformationException
     *             when there is any error in retrieving information
     */
    public static List<Variable> listFieldVariables(ObjectReference obj, int start, int count)
            throws AbsentInformationException {
        List<Variable> res = new ArrayList<>();
        Type type = obj.type();
        if (type instanceof ArrayType) {
            int arrayIndex = start;
            for (Value elementValue : ((ArrayReference) obj).getValues(start, count)) {
                res.add(new Variable(String.valueOf(arrayIndex++), elementValue));
            }
            return res;
        }
        throw new UnsupportedOperationException("Only Array type is supported.");
    }

    /**
     * Get the local variables of an stack frame.
     *
     * @param stackFrame
     *            the stack frame
     * @return local variable list
     * @throws AbsentInformationException
     *             when there is any error in retrieving information
     */
    public static List<Variable> listLocalVariables(StackFrame stackFrame) throws AbsentInformationException {
        List<Variable> res = new ArrayList<>();
        try {
            for (LocalVariable localVariable : stackFrame.visibleVariables()) {
                Variable var = new Variable(localVariable.name(), stackFrame.getValue(localVariable));
                var.local = localVariable;
                res.add(var);
            }
        } catch (AbsentInformationException ex) {
            int argId = 0;
            try {
                for (Value argValue : stackFrame.getArgumentValues()) {
                    Variable var = new Variable("arg" + argId, argValue);
                    var.argumentIndex = argId++;
                    res.add(var);
                }
            } catch (InternalException ex2) {
                // From Oracle's forums:
                // This could be a JPDA bug. Unexpected JDWP Error: 32 means that an 'opaque' frame was
                // detected at the lower JPDA levels,
                // typically a native frame.
                if (ex2.errorCode() != 32) {
                    throw ex;
                }
            }
        }
        return res;
    }

    /**
     * Get the this variable of an stack frame.
     *
     * @param stackFrame
     *            the stack frame
     * @return this variable
     */
    public static Variable getThisVariable(StackFrame stackFrame) {
        ObjectReference thisObject = stackFrame.thisObject();
        if (thisObject == null) {
            return null;
        }
        return new Variable("this", thisObject);
    }

    /**
     * Get the static variable of an stack frame.
     *
     * @param stackFrame
     *            the stack frame
     * @return the static variable of an stack frame.
     */
    public static List<Variable> listStaticVariables(StackFrame stackFrame) {
        List<Variable> res = new ArrayList<>();
        ReferenceType type = stackFrame.location().declaringType();
        type.allFields().stream().filter(TypeComponent::isStatic).forEach(field -> {
            Variable staticVar = new Variable(field.name(), type.getValue(field));
            staticVar.field = field;
            res.add(staticVar);
        });
        return res;
    }

}
