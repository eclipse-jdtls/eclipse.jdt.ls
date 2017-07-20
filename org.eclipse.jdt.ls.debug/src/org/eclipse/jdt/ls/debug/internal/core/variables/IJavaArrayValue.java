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
 * Represents an java array object in JVM.
 */
public interface IJavaArrayValue extends IJavaObjectValue {
    /**
     * Returns the length of this array.
     *
     * @return the length of this array
     */
    public int getArrayLength();

    /**
     * Returns the dimension this array.
     *
     * @return the dimension of this array
     */
    public int getArrayDimension();

    /**
     * Return the signature of closest non-array type signature, for example:
     * String[][][] will return String type.
     * Java doesn't support array of generic type, see:
     * http://www.angelikalanger.com/GenericsFAQ/FAQSections/ParameterizedTypes.html#FAQ104
     *
     * @return the closest non-array type signature of the array element.
     */
    public String getComponentTypeSignature();

}
