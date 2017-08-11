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

import com.sun.jdi.AbsentInformationException;
import com.sun.jdi.ObjectReference;
import com.sun.jdi.StackFrame;
import com.sun.jdi.Type;
import com.sun.jdi.Value;

import java.util.List;
import java.util.Map;
import org.eclipse.jdt.ls.debug.adapter.formatter.ITypeFormatter;
import org.eclipse.jdt.ls.debug.adapter.formatter.IValueFormatter;
import org.eclipse.jdt.ls.debug.adapter.Variable;

/**
 * The interface for variable provider, implementations of this interface
 * will provide the ability to convert JDI value to display string.
 */
public interface IVariableProvider {
    /**
     * Test whether the value has children.
     *
     * @param value the value.
     * @return true if this value has children objects.
     */
    boolean hasChildren(Value value);

    /**
     * Get display text of the value.
     *
     * @param value   the value.
     * @param options additional information about expected format
     * @return the display text of the value
     */
    String valueToString(Value value, Map<String, Object> options);

    /**
     * Get display name of type.
     *
     * @param type    the JDI type
     * @param options additional information about expected format
     * @return display name of type of the value.
     */
    String typeToString(Type type, Map<String, Object> options);

    /**
     * Get the variables of the object.
     *
     * @param obj the object
     * @return the variable list
     * @throws AbsentInformationException when there is any error in retrieving information
     */
    List<Variable> listFieldVariables(ObjectReference obj) throws AbsentInformationException;

    /**
     * Get the variables of the object with pagination.
     *
     * @param obj   the object
     * @param start the start of the pagination
     * @param count the number of variables needed
     * @return the variable list
     * @throws AbsentInformationException when there is any error in retrieving information
     */
    List<Variable> listFieldVariables(ObjectReference obj, int start, int count)
            throws AbsentInformationException;

    /**
     * Get the local variables of an stack frame.
     *
     * @param stackFrame the stack frame
     * @return local variable list
     * @throws AbsentInformationException when there is any error in retrieving information
     */
    List<Variable> listLocalVariables(StackFrame stackFrame) throws AbsentInformationException;

    /**
     * Get the this variable of an stack frame.
     *
     * @param stackFrame the stack frame
     * @return this variable
     */
    Variable getThisVariable(StackFrame stackFrame);

    /**
     * Get the static variable of an stack frame.
     *
     * @param stackFrame the stack frame
     * @return the static variable of an stack frame.
     */
    List<Variable> listStaticVariables(StackFrame stackFrame);

    /**
     * Register a type formatter. Be careful about the priority of formatters, the formatter with the largest
     * priority which accepts the type will be used.
     *
     * @param typeFormatter the type formatter
     * @param priority      the priority for this formatter
     */
    void registerTypeFormatter(ITypeFormatter typeFormatter, int priority);

    /**
     * Register a value formatter. Be careful about the priority of formatters, the formatter with the largest
     * priority which accepts the type will be used.
     *
     * @param formatter the value formatter
     * @param priority  the priority for this formatter
     */
    void registerValueFormatter(IValueFormatter formatter, int priority);
}
