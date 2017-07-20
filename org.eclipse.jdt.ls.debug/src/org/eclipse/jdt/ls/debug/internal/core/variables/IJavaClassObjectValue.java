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
 * Represents an java Class object in JVM.
 */
public interface IJavaClassObjectValue extends IJavaObjectValue {
    /**
     * Returns the type signature associated with instances of this class.
     *
     * @return the type signature associated with instances of this class
     */
    public String getInstanceTypeSignature();
}
