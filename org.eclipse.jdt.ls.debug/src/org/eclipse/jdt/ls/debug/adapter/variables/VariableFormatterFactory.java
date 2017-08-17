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

import org.eclipse.jdt.ls.debug.adapter.formatter.ArrayObjectFormatter;
import org.eclipse.jdt.ls.debug.adapter.formatter.BooleanFormatter;
import org.eclipse.jdt.ls.debug.adapter.formatter.CharacterFormatter;
import org.eclipse.jdt.ls.debug.adapter.formatter.ClassObjectFormatter;
import org.eclipse.jdt.ls.debug.adapter.formatter.NullObjectFormatter;
import org.eclipse.jdt.ls.debug.adapter.formatter.NumericFormatter;
import org.eclipse.jdt.ls.debug.adapter.formatter.ObjectFormatter;
import org.eclipse.jdt.ls.debug.adapter.formatter.SimpleTypeFormatter;
import org.eclipse.jdt.ls.debug.adapter.formatter.StringObjectFormatter;

public final class VariableFormatterFactory {
    /**
     * Private constructor to prevent instance of <code>VariableFormatterFactory</code>.
     */
    private VariableFormatterFactory() {
        
    }
    
    /**
     * Create an <code>IVariableFormatter</code> instance with proper value and type formatters.  
     * @return an <code>IVariableFormatter</code> instance
     */
    public static IVariableFormatter createVariableFormatter() {
        VariableFormatter formatter = new VariableFormatter();
        formatter.registerTypeFormatter(new SimpleTypeFormatter(), 1);
        formatter.registerValueFormatter(new BooleanFormatter(), 1);
        formatter.registerValueFormatter(new CharacterFormatter(), 1);
        formatter.registerValueFormatter(new NumericFormatter(), 1);
        formatter.registerValueFormatter(new ObjectFormatter(formatter::typeToString), 1);
        formatter.registerValueFormatter(new NullObjectFormatter(), 1);

        formatter.registerValueFormatter(new StringObjectFormatter(), 2);
        formatter.registerValueFormatter(new ArrayObjectFormatter(formatter::typeToString), 2);
        formatter.registerValueFormatter(new ClassObjectFormatter(formatter::typeToString), 2);
        return formatter;
    }
}
