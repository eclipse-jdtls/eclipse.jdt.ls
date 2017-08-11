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

package org.eclipse.jdt.ls.debug.adapter;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.eclipse.jdt.ls.debug.adapter.formatter.ArrayObjectFormatter;
import org.eclipse.jdt.ls.debug.adapter.formatter.BooleanFormatter;
import org.eclipse.jdt.ls.debug.adapter.formatter.CharacterFormatter;
import org.eclipse.jdt.ls.debug.adapter.formatter.ClassObjectFormatter;
import org.eclipse.jdt.ls.debug.adapter.formatter.IFormatter;
import org.eclipse.jdt.ls.debug.adapter.formatter.ITypeFormatter;
import org.eclipse.jdt.ls.debug.adapter.formatter.IValueFormatter;
import org.eclipse.jdt.ls.debug.adapter.formatter.NullObjectFormatter;
import org.eclipse.jdt.ls.debug.adapter.formatter.NumericFormatter;
import org.eclipse.jdt.ls.debug.adapter.formatter.ObjectFormatter;
import org.eclipse.jdt.ls.debug.adapter.formatter.SimpleTypeFormatter;
import org.eclipse.jdt.ls.debug.adapter.formatter.StringObjectFormatter;
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

/**
 * The default provider for retrieving display name of the type.
 */
public final class DefaultVariableProvider implements IVariableProvider {
    private boolean includeStatic;
    private Map<IValueFormatter, Integer> valueFormatterMap;
    private Map<ITypeFormatter, Integer> typeFormatterMap;

    /**
     * Creates a default variable provider.
     *
     * @param includeStatic whether to show static variables
     */
    public DefaultVariableProvider(boolean includeStatic) {
        this.includeStatic = includeStatic;
        valueFormatterMap = new HashMap<>();
        typeFormatterMap = new HashMap<>();
        initialize();
    }

    /**
     * Initialize this provider.
     */
    private void initialize() {
        registerTypeFormatter(new SimpleTypeFormatter(), 1);
        registerValueFormatter(new BooleanFormatter(), 1);
        registerValueFormatter(new CharacterFormatter(), 1);
        registerValueFormatter(new NumericFormatter(), 1);
        registerValueFormatter(new ObjectFormatter(this::typeToString), 1);
        registerValueFormatter(new NullObjectFormatter(), 1);

        registerValueFormatter(new StringObjectFormatter(), 2);
        registerValueFormatter(new ArrayObjectFormatter(this::typeToString), 2);
        registerValueFormatter(new ClassObjectFormatter(this::typeToString), 2);
    }

    private static IFormatter getFormatter(Map<? extends IFormatter, Integer> formatterMap, Type type,
                                           Map<String, Object> options) {
        List<? extends IFormatter> formatterList =
                formatterMap.keySet().stream().filter(t -> t.acceptType(type, options))
                        .sorted((a, b) ->
                                -Integer.compare(formatterMap.get(a), formatterMap.get(b))).collect(Collectors.toList());
        if (formatterList.isEmpty()) {
            throw new UnsupportedOperationException(String.format("There is no related formatter for type %s.",
                    type == null ? "null" : type.name()));
        }
        return formatterList.get(0);
    }

    @Override
    public String typeToString(Type type, Map<String, Object> options) {
        IFormatter formatter = getFormatter(this.typeFormatterMap, type, options);
        return formatter.toString(type, options);
    }

    @Override
    public String valueToString(Value value, Map<String, Object> options) {
        Type type = value == null ? null : value.type();
        IFormatter formatter = getFormatter(this.valueFormatterMap, type, options);
        return formatter.toString(value, options);
    }

    @Override
    public void registerValueFormatter(IValueFormatter formatter, int priority) {
        valueFormatterMap.put(formatter, priority);
    }

    @Override
    public void registerTypeFormatter(ITypeFormatter typeFormatter, int priority) {
        typeFormatterMap.put(typeFormatter, priority);
    }

    /**
     * Test whether the value has referenced objects.
     *
     * @param value the value.
     * @return true if this value is reference objects.
     */
    @Override
    public boolean hasChildren(Value value) {
        if (value == null) {
            return false;
        }
        Type type = value.type();
        if (type instanceof ArrayType) {
            return ((ArrayReference) value).length() > 0;
        }
        return value.type() instanceof ReferenceType
                && ((ReferenceType) type).allFields().stream()
                        .filter(t -> includeStatic || !t.isStatic()).toArray().length > 0;
    }

    /**
     * Get the variables of the object.
     *
     * @param obj the object
     * @return the variable list
     * @throws AbsentInformationException when there is any error in retrieving information
     */
    @Override
    public List<Variable> listFieldVariables(ObjectReference obj)
            throws AbsentInformationException {
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
        List<Field> fields = obj.referenceType().allFields().stream()
                .filter(t -> includeStatic || !t.isStatic()).sorted((a, b) -> {
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
     * @param obj   the object
     * @param start the start of the pagination
     * @param count the number of variables needed
     * @return the variable list
     * @throws AbsentInformationException when there is any error in retrieving information
     */
    @Override
    public List<Variable> listFieldVariables(ObjectReference obj, int start, int count)
            throws AbsentInformationException {
        List<Variable> res = new ArrayList<>();
        Type type = obj.type();
        if (type instanceof ArrayType) {
            int arrayIndex = start;
            for (Value elementValue : ((ArrayReference) obj).getValues(start, count)) {
                Variable ele = new Variable(String.valueOf(arrayIndex++), elementValue);
                arrayIndex++;
                res.add(ele);
            }
            return res;
        }
        throw new UnsupportedOperationException("Only Array type is supported.");
    }

    /**
     * Get the local variables of an stack frame.
     *
     * @param stackFrame the stack frame
     * @return local variable list
     * @throws AbsentInformationException when there is any error in retrieving information
     */
    @Override
    public List<Variable> listLocalVariables(StackFrame stackFrame)
            throws AbsentInformationException {
        List<Variable> res = new ArrayList<>();
        try {
            for (LocalVariable localVariable : stackFrame.visibleVariables()) {
                Variable var = new Variable(localVariable.name(),
                        stackFrame.getValue(localVariable));
                var.local = localVariable;
                res.add(var);
            }
        } catch (AbsentInformationException ex) {
            int argId = 0;
            for (Value argValue : stackFrame.getArgumentValues()) {
                Variable var = new Variable("arg" + argId, argValue);
                var.argumentIndex = argId++;
                res.add(var);
            }
        }
        return res;
    }

    /**
     * Get the this variable of an stack frame.
     *
     * @param stackFrame the stack frame
     * @return this variable
     */
    @Override
    public Variable getThisVariable(StackFrame stackFrame) {
        ObjectReference thisObject = stackFrame.thisObject();
        if (thisObject == null) {
            return null;
        }
        return new Variable("this", thisObject);
    }

    /**
     * Get the static variable of an stack frame.
     *
     * @param stackFrame the stack frame
     * @return the static variable of an stack frame.
     */
    @Override
    public List<Variable> listStaticVariables(StackFrame stackFrame) {
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
