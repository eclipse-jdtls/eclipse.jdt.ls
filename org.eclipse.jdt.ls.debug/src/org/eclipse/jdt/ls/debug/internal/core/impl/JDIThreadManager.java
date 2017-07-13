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

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

import org.eclipse.jdt.ls.debug.internal.core.IVMTarget;
import org.eclipse.jdt.ls.debug.internal.core.IJDIEventListener;
import org.eclipse.jdt.ls.debug.internal.core.IThread;
import org.eclipse.jdt.ls.debug.internal.core.IThreadManager;
import org.eclipse.jdt.ls.debug.internal.core.log.Logger;

import com.sun.jdi.ObjectCollectedException;
import com.sun.jdi.ThreadReference;
import com.sun.jdi.VMDisconnectedException;
import com.sun.jdi.VirtualMachine;
import com.sun.jdi.event.Event;
import com.sun.jdi.event.EventSet;
import com.sun.jdi.event.ThreadDeathEvent;
import com.sun.jdi.event.ThreadStartEvent;
import com.sun.jdi.request.EventRequest;
import com.sun.jdi.request.EventRequestManager;

public class JDIThreadManager implements IThreadManager {
    private List<IThread> threads;
    private JDIVMTarget target;

    /**
     * Constructor.
     */
    public JDIThreadManager(JDIVMTarget target) {
        this.target = target;
        this.threads = Collections.synchronizedList(new ArrayList<IThread>(5));
        initialize();
    }

    protected void initialize() {
        // register event handlers for thread creation, thread termination.
        new ThreadStartHandler();
        new ThreadDeathHandler();

        // Adds all of pre-existings threads to this debug target.
        List<ThreadReference> threads = null;
        VirtualMachine vm = this.target.getVM();
        if (vm != null) {
            try {
                threads = vm.allThreads();
            } catch (RuntimeException e) {
                Logger.logException("Get allThreads exception", e);
            }
            if (threads != null) {
                Iterator<ThreadReference> initialThreads = threads.iterator();
                while (initialThreads.hasNext()) {
                    createThread(initialThreads.next());
                }
            }
        }
    }

    /**
     * Gets all threads belonging to the debuggee VM.
     */
    public IThread[] getThreads() {
        synchronized (this.threads) {
            return this.threads.toArray(new IThread[0]);
        }
    }

    /**
     * Finds the corresponding JDIThread instance with the underlying thread.
     */
    public IThread findThread(ThreadReference threadReference) {
        for (IThread thread : this.threads) {
            if (thread.getUnderlyingThread().equals(threadReference)) {
                return thread;
            }
        }
        return null;
    }

    /**
     * Creates an JDIThread for the underlying thread.
     */
    public IThread createThread(ThreadReference threadReference) {
        IThread jdiThread = new JDIThread(this.target, threadReference);
        synchronized (this.threads) {
            this.threads.add(jdiThread);
        }
        jdiThread.fireCreationEvent();
        return jdiThread;
    }

    class ThreadStartHandler implements IJDIEventListener {

        protected EventRequest request;

        protected ThreadStartHandler() {
            EventRequestManager manager = target.getEventRequestManager();
            if (manager != null) {
                try {
                    EventRequest req = manager.createThreadStartRequest();
                    req.setSuspendPolicy(EventRequest.SUSPEND_NONE);
                    req.enable();
                    target.addEventListener(req, this);
                    this.request = req;
                } catch (RuntimeException e) {
                    Logger.logException("Create ThreadStartRequest exception", e);
                }
            }
        }

        @Override
        public boolean handleEvent(Event event, IVMTarget target, boolean suspendVote, EventSet eventSet) {
            ThreadReference thread = ((ThreadStartEvent) event).thread();
            try {
                // https://bugs.eclipse.org/bugs/show_bug.cgi?id=443727
                // the backing ThreadReference could be read in as null
                if (thread == null || thread.isCollected()) {
                    return false;
                }
            } catch (VMDisconnectedException exception) {
                return false;
            } catch (ObjectCollectedException e) {
                return false;
            }
            IThread jdiThread = findThread(thread);
            if (jdiThread == null) {
                jdiThread = createThread(thread);
            } else {
                // TODO
            }
            return true;
        }

        protected void deleteRequest() {
            if (this.request != null) {
                target.removeEventListener(this.request);
                this.request = null;
            }
        }
    }

    class ThreadDeathHandler implements IJDIEventListener {

        protected ThreadDeathHandler() {
            EventRequestManager manager = target.getEventRequestManager();
            if (manager != null) {
                try {
                    EventRequest req = manager.createThreadDeathRequest();
                    req.setSuspendPolicy(EventRequest.SUSPEND_NONE);
                    req.enable();
                    target.addEventListener(req, this);
                } catch (RuntimeException e) {
                    Logger.logException("Create ThreadDeathRequest exception", e);
                }
            }
        }

        @Override
        public boolean handleEvent(Event event, IVMTarget target, boolean suspendVote, EventSet eventSet) {
            ThreadReference ref = ((ThreadDeathEvent) event).thread();
            IThread thread = findThread(ref);
            if (thread != null) {
                synchronized (threads) {
                    threads.remove(thread);
                }
                thread.fireTerminateEvent();
            }
            return true;
        }

    }
}
