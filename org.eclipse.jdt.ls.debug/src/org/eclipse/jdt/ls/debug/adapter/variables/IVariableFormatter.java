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

import com.sun.jdi.Type;
import com.sun.jdi.Value;
import org.eclipse.jdt.ls.debug.adapter.formatter.ITypeFormatter;
import org.eclipse.jdt.ls.debug.adapter.formatter.IValueFormatter;

import java.util.Map;

public interface IVariableFormatter {
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

    /**
     * Get the default options for all formatters registered.
     * @return The default options.
     */
    Map<String, Object> getDefaultOptions();

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
}
