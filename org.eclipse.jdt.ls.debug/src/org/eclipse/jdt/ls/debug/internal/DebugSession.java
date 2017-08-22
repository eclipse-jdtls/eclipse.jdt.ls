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

package org.eclipse.jdt.ls.debug.internal;

import java.util.List;

import org.eclipse.jdt.ls.debug.IBreakpoint;
import org.eclipse.jdt.ls.debug.IDebugSession;
import org.eclipse.jdt.ls.debug.IEventHub;

import com.sun.jdi.ThreadReference;
import com.sun.jdi.VirtualMachine;
import com.sun.jdi.request.EventRequest;

public class DebugSession implements IDebugSession {
    private VirtualMachine vm;
    private EventHub eventHub = new EventHub();

    public DebugSession(VirtualMachine virtualMachine) {
        vm = virtualMachine;
    }

    @Override
    public void start() {
        // request thread events by default
        EventRequest threadStartRequest = vm.eventRequestManager().createThreadStartRequest();
        threadStartRequest.setSuspendPolicy(EventRequest.SUSPEND_NONE);
        threadStartRequest.enable();

        EventRequest threadDeathRequest = vm.eventRequestManager().createThreadDeathRequest();
        threadDeathRequest.setSuspendPolicy(EventRequest.SUSPEND_NONE);
        threadDeathRequest.enable();

        eventHub.start(vm);
    }

    @Override
    public void suspend() {
        vm.suspend();
    }

    @Override
    public void resume() {
        vm.resume();
    }

    @Override
    public void detach() {
        vm.dispose();
    }

    @Override
    public void terminate() {
        if (vm.process().isAlive()) {
            vm.exit(0);
        }
    }

    @Override
    public IBreakpoint createBreakpoint(String className, int lineNumber) {
        return new Breakpoint(this.vm, this.eventHub(), className, lineNumber);
    }

    @Override
    public IBreakpoint createBreakpoint(String className, int lineNumber, int hitCount) {
        return new Breakpoint(this.vm, this.eventHub(), className, lineNumber, hitCount);
    }

    @Override
    public Process process() {
        return vm.process();
    }

    @Override
    public List<ThreadReference> allThreads() {
        return vm.allThreads();
    }

    @Override
    public IEventHub eventHub() {
        return eventHub;
    }

}
