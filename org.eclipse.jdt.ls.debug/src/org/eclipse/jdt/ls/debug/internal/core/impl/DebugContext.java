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

package org.eclipse.jdt.ls.debug.internal.core.impl;

import org.eclipse.jdt.ls.debug.internal.core.IBreakpointManager;
import org.eclipse.jdt.ls.debug.internal.core.IDebugContext;
import org.eclipse.jdt.ls.debug.internal.core.IDebugEventHub;
import org.eclipse.jdt.ls.debug.internal.core.IVMTarget;
import org.eclipse.jdt.ls.debug.internal.core.breakpoints.BreakpointManager;

public class DebugContext implements IDebugContext {
    private BreakpointManager breakpointManager = null;
    private DebugEventHub debugEventHub = null;
    private IVMTarget vmTarget;

    public DebugContext() {
        this.breakpointManager = new BreakpointManager(this);
        this.debugEventHub = new DebugEventHub();
    }

    @Override
    public IBreakpointManager getBreakpointManager() {
        return this.breakpointManager;
    }

    @Override
    public IDebugEventHub getDebugEventHub() {
        return this.debugEventHub;
    }

    public void setVMTarget(IVMTarget vmTarget) {
        this.vmTarget = vmTarget;
    }

    public IVMTarget getVMTarget() {
        return vmTarget;
    }
}
