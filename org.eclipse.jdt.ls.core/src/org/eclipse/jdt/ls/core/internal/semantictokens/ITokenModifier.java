/*******************************************************************************
 * Copyright (c) 2020 Microsoft Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *     Microsoft Corporation - initial API and implementation
 *******************************************************************************/

package org.eclipse.jdt.ls.core.internal.semantictokens;

import org.eclipse.jdt.core.dom.IBinding;

public interface ITokenModifier {
    /**
     * Determine whether this modifier applies to a named entity.
     * @param binding corresponding binding of the named entity.
     * @return <code>true</code> if this modifier applies to the binding and
	 *    <code>false</code> otherwise
     */
    public boolean applies(IBinding binding);

    /**
     * identifier of the modifier
     */
    public String toString();
}
