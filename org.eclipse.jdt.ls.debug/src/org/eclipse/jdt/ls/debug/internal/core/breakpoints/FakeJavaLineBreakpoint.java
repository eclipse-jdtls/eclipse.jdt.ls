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

package org.eclipse.jdt.ls.debug.internal.core.breakpoints;

import org.eclipse.jdt.ls.debug.internal.core.IVMTarget;

/**
 * This is just a faked breakpoint and being used for invalid line breakpoints (such as empty line breakpoint).
 * It doesn't create any JDWP communication with debugee VM. 
 */
public class FakeJavaLineBreakpoint extends JavaLineBreakpoint {
    public FakeJavaLineBreakpoint(final String fullQualifiedName, final int lineNumber, final int hitCount) {
        super(fullQualifiedName, lineNumber, hitCount);
    }

    @Override
    public void addToVMTarget(IVMTarget target) {
        // do nothing here.
    }

    @Override
    public void removeFromVMTarget(IVMTarget target) {
        // do nothing here.
    }

}
