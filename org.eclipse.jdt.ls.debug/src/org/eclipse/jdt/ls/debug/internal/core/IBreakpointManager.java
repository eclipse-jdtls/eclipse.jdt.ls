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

package org.eclipse.jdt.ls.debug.internal.core;

public interface IBreakpointManager {
    public void addBreakpoint(String source, IBreakpoint breakpoint);

    public void addBreakpoint(String source, IBreakpoint breakpoint, boolean sourceModified);

    public void addBreakpoints(String source, IBreakpoint[] breakpoints);

    public void addBreakpoints(String source, IBreakpoint[] breakpoints, boolean sourceModified);

    public void removeBreakpoint(String source, IBreakpoint breakpoint);

    public void removeBreakpoints(String source, IBreakpoint[] breakpoints);

    public IBreakpoint[] getBreakpoints();

    public IBreakpoint[] getBreakpoints(String source);
}
