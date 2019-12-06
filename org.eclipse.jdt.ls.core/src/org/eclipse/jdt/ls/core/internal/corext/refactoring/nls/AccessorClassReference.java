/*******************************************************************************
 * Copyright (c) 2000, 2011 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Originally copied from org.eclipse.jdt.internal.corext.refactoring.nls.AccessorClassReference
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.jdt.ls.core.internal.corext.refactoring.nls;

import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jface.text.Region;


public class AccessorClassReference {

    private ITypeBinding fBinding;
    private Region fRegion;
    private String fResourceBundleName;

    public AccessorClassReference(ITypeBinding typeBinding, String resourceBundleName, Region accessorRegion) {
        super();
        fBinding= typeBinding;
        fRegion= accessorRegion;
        fResourceBundleName= resourceBundleName;
    }

	public ITypeBinding getBinding() {
		return fBinding;
	}

	public String getName() {
		return fBinding.getName();
	}

	public Region getRegion() {
		return fRegion;
	}

	public String getResourceBundleName() {
		return fResourceBundleName;
	}

    @Override
	public boolean equals(Object obj) {
        if (obj instanceof AccessorClassReference) {
            AccessorClassReference cmp = (AccessorClassReference) obj;
            return fBinding == cmp.fBinding;
        }
        return false;
    }

    @Override
	public int hashCode() {
        return fBinding.hashCode();
    }
}
