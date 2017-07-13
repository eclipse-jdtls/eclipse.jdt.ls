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
import org.eclipse.jdt.ls.debug.internal.core.IVMTarget;
import org.eclipse.jdt.ls.debug.internal.core.IThread;

import com.sun.jdi.ThreadReference;

public class JDIThread extends DebugElement implements IThread {

    private ThreadReference thread;

    public JDIThread(IVMTarget target, ThreadReference thread) {
        super(target);
        this.thread = thread;
    }

    @Override
    public ThreadReference getUnderlyingThread() {
        return this.thread;
    }

    @Override
    public void fireCreationEvent() {
        fireEvent(new DebugEvent(this, EventType.THREADSTART_EVENT));
    }

    @Override
    public void fireTerminateEvent() {
        fireEvent(new DebugEvent(this, EventType.THREADDEATH_EVENT));
    }

    @Override
    public void stepInto() {
        // TODO Auto-generated method stub

    }

    @Override
    public void stepOver() {
        // TODO Auto-generated method stub

    }

    @Override
    public void resume() {
        // TODO Auto-generated method stub

    }

}
