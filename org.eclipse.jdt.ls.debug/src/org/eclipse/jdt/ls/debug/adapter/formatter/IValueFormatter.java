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

public interface IValueFormatter extends IFormatter {
    /**
     * Create the value from string, this method is used in setValue feature
     * where converts user-input string to JDI value.
     *
     * @param value the string text.
     * @param type the expected value type.
     * @param options additional information about expected format
     * @return the JDI value.
     */
    Value valueOf(String value, Type type, Map<String, Object> options);
}
