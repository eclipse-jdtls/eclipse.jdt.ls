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

import org.eclipse.jdt.ls.debug.internal.core.EventType;
import org.eclipse.jdt.ls.debug.internal.core.IDebugContext;
import org.eclipse.jdt.ls.debug.internal.core.IJDIEventHub;
import org.eclipse.jdt.ls.debug.internal.core.IThread;
import org.eclipse.jdt.ls.debug.internal.core.IThreadManager;
import org.eclipse.jdt.ls.debug.internal.core.IVMTarget;

import com.sun.jdi.VirtualMachine;
import com.sun.jdi.event.VMDeathEvent;
import com.sun.jdi.event.VMDisconnectEvent;
import com.sun.jdi.event.VMStartEvent;

public class JDIVMTarget extends DebugElement implements IVMTarget {

    private VirtualMachine jvm;
    private boolean resumeOnStartup;
    private IDebugContext debugContext;
    private JDIEventHub vmEventHub;
    private JDIThreadManager vmThreadManager;

    /**
     * Constructor.
     * @param context
     *              the debug session context
     * @param jvm
     *              the debuggee VM
     * @param resumeOnStartup
     *              whether the VM should be resumed on startup
     */
    public JDIVMTarget(IDebugContext context, VirtualMachine jvm, boolean resumeOnStartup) {
        super(null);
        this.jvm = jvm;
        this.resumeOnStartup = resumeOnStartup;
        this.debugContext = context;
        this.debugContext.setVMTarget(this);
        initialize();
    }

    protected void initialize() {
        this.vmEventHub = new JDIEventHub(this);
        this.vmThreadManager = new JDIThreadManager(this);

        Thread t = new Thread(vmEventHub, "VirtualMachineEventHub");
        t.setDaemon(true);
        t.start();
    }

    @Override
    public IVMTarget getVMTarget() {
        return this;
    }

    @Override
    public VirtualMachine getVM() {
        return this.jvm;
    }

    @Override
    public IJDIEventHub getEventHub() {
        return this.vmEventHub;
    }

    public IThreadManager getThreadManager() {
        return this.vmThreadManager;
    }

    @Override
    public IThread[] getThreads() {
        return this.vmThreadManager.getThreads();
    }

    public IDebugContext getDebugContext() {
        return this.debugContext;
    }

    @Override
    public void fireCreationEvent() {
        fireEvent(new DebugEvent(this, EventType.VMSTART_EVENT));
    }

    @Override
    public void fireTerminateEvent() {
        fireEvent(new DebugEvent(this, EventType.VMDEATH_EVENT));
    }

    public void handleVMDeath(VMDeathEvent event) {
        fireTerminateEvent();
    }

    public void handleVMDisconnect(VMDisconnectEvent event) {
        fireTerminateEvent();
    }

    public void handleVMStart(VMStartEvent event) {
        fireCreationEvent();
    }

    /**
     * Resume the debuggee VM.
     */
    public void resume() {
        this.resumeOnStartup = true;
        VirtualMachine vm = getVM();
        if (vm != null) {
            vm.resume();
        }
    }
}
