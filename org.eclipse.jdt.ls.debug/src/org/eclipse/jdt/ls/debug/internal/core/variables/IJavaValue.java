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

package org.eclipse.jdt.ls.debug.internal.core.variables;

/**
 * An object, primitive data type, or array, on a java custom object.
 */
public interface IJavaValue {
    /**
     * Returns the JNI-style signature for the type of this value, or
     * <code>null</code> if the value is <code>null</code>.
     */
    public String getTypeSigature();


    /**
     * Returns the real value of this value for primitive value, or the id of an
     * non-primitive value, or <code>null</code> if this value represents the
     * <code>null</code> value.
     *
     * @return the type of this value, or <code>null</code> if this value
     *         represents the <code>null</code> value
     */
    public Object getValue();

    /**
     * Returns whether this value currently contains any inner structure.
     * @return whether this value currently contains any inner structure.
     */
    public boolean isExpandable();
}
