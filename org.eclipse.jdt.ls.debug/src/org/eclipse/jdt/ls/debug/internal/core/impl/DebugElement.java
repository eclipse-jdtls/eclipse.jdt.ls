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

import org.eclipse.jdt.ls.debug.internal.core.IDebugElement;
import org.eclipse.jdt.ls.debug.internal.core.IDebugEvent;
import org.eclipse.jdt.ls.debug.internal.core.IVMTarget;
import org.eclipse.jdt.ls.debug.internal.core.IJDIEventHub;
import org.eclipse.jdt.ls.debug.internal.core.IJDIEventListener;

import com.sun.jdi.VirtualMachine;
import com.sun.jdi.request.EventRequest;
import com.sun.jdi.request.EventRequestManager;

public abstract class DebugElement implements IDebugElement {

    private IVMTarget target;

    public DebugElement(IVMTarget target) {
        this.target = target;
    }

    @Override
    public IVMTarget getVMTarget() {
        return this.target;
    }

    @Override
    public VirtualMachine getVM() {
        return ((JDIVMTarget) getVMTarget()).getVM();
    }

    /**
     * Gets the EventRequestManager of the debuggee VM.
     */
    public EventRequestManager getEventRequestManager() {
        VirtualMachine vm = getVM();
        if (vm == null) {
            return null;
        }
        return vm.eventRequestManager();
    }

    /**
     * Registers an JDIEventListener to the event hub of debuggee VM.
     * @param request
     *              event request
     * @param listener
     *              event listener
     */
    public void addEventListener(EventRequest request, IJDIEventListener listener) {
        IJDIEventHub eventHub = ((JDIVMTarget) getVMTarget()).getEventHub();
        if (eventHub != null) {
            eventHub.addJDIEventListener(request, listener);
        }
    }

    /**
     * Removes the listener of the specified request from the event hub.
     * @param request
     *              event request
     */
    public void removeEventListener(EventRequest request) {
        IJDIEventHub eventHub = ((JDIVMTarget) getVMTarget()).getEventHub();
        if (eventHub != null) {
            eventHub.removeJDIEventListener(request);
        }
    }

    public void fireEvent(IDebugEvent event) {
        DebugEventHub eventHub = (DebugEventHub) getVMTarget().getDebugContext().getDebugEventHub();
        eventHub.fireDebugEventSet(new IDebugEvent[] { event });
    }
}
