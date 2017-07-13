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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.eclipse.jdt.ls.debug.internal.core.IBreakpoint;
import org.eclipse.jdt.ls.debug.internal.core.IBreakpointManager;
import org.eclipse.jdt.ls.debug.internal.core.IDebugContext;
import org.eclipse.jdt.ls.debug.internal.core.log.Logger;

public class BreakpointManager implements IBreakpointManager {
    /**
     * A collection of breakpoints registered with this manager.
     */
    private List<IBreakpoint> breakpoints;
    private IDebugContext debugContext;

    public BreakpointManager(IDebugContext debugContext) {
        this.breakpoints = Collections.synchronizedList(new ArrayList<>(5));
        this.debugContext = debugContext;
    }

    public void addBreakpoint(IBreakpoint breakpoint) {
        addBreakpoints(new IBreakpoint[] { breakpoint });
    }

    /**
     * Adds new breakpoints to breakpoint manager.
     */
    public void addBreakpoints(IBreakpoint[] breakpoints) {
        for (IBreakpoint breakpoint : breakpoints) {
            synchronized (this.breakpoints) {
                this.breakpoints.add(breakpoint);
            }
        }
        if (breakpoints != null && breakpoints.length > 0) {
            for (IBreakpoint breakpoint : breakpoints) {
                try {
                    breakpoint.addToVMTarget(this.debugContext.getVMTarget());
                } catch (Exception e) {
                    Logger.logException("Add breakpoint exception", e);
                }
            }

        }
    }

    public void removeBreakpoint(IBreakpoint breakpoint) {
        removeBreakpoints(new IBreakpoint[] { breakpoint });
    }

    /**
     * Removes the specified breakpoints from breakpoint manager.
     */
    public void removeBreakpoints(IBreakpoint[] breakpoints) {
        for (IBreakpoint breakpoint : breakpoints) {
            if (this.breakpoints.contains(breakpoint)) {
                try {
                    breakpoint.removeFromVMTarget(debugContext.getVMTarget());
                    ;
                    synchronized (breakpoints) {
                        this.breakpoints.remove(breakpoint);
                    }
                } catch (Exception e) {
                    Logger.logException("Remove breakpoint exception", e);
                }
            }
        }
    }

    public IBreakpoint[] getBreakpoints() {
        return this.breakpoints.toArray(new IBreakpoint[0]);
    }
}
