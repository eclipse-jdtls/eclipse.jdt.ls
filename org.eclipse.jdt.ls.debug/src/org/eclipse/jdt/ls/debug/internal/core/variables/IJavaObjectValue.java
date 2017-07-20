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
 * Represents a common java object in JVM.
 */
public interface IJavaObjectValue extends IJavaValue {

    /**
     * The unique id in debug JVM. Use this id to get inner structure/value of this object.
     * @return the unique id.
     */
    public long getId();

    /**
     * Returns the generic signature as defined in the JVM specification for the
     * type of this value. Returns <code>null</code> if the value is
     * <code>null</code>, or if the type of this value is not a generic type.
     *
     * @return signature, or <code>null</code> if generic signature not
     *         available
     */
    public String getGenericSignature();
}
