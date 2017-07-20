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

import java.util.HashMap;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import org.eclipse.jdt.ls.debug.internal.core.IJDIEventHub;
import org.eclipse.jdt.ls.debug.internal.core.IJDIEventListener;
import org.eclipse.jdt.ls.debug.internal.core.log.Logger;

import com.sun.jdi.VMDisconnectedException;
import com.sun.jdi.VirtualMachine;
import com.sun.jdi.event.Event;
import com.sun.jdi.event.EventIterator;
import com.sun.jdi.event.EventQueue;
import com.sun.jdi.event.EventSet;
import com.sun.jdi.event.VMDeathEvent;
import com.sun.jdi.event.VMDisconnectEvent;
import com.sun.jdi.event.VMStartEvent;
import com.sun.jdi.request.EventRequest;

public class JDIEventHub implements IJDIEventHub {
    private JDIVMTarget target;
    private HashMap<EventRequest, IJDIEventListener> eventHandlers;
    private boolean shutdown = false;
    private ThreadPoolExecutor executor = null;

    public JDIEventHub(JDIVMTarget target) {
        this.target = target;
        this.eventHandlers = new HashMap<>(10);
    }

    @Override
    public void addJDIEventListener(EventRequest request, IJDIEventListener listener) {
        this.eventHandlers.put(request, listener);
    }

    @Override
    public void removeJDIEventListener(EventRequest request) {
        this.eventHandlers.remove(request);
    }

    public boolean isShutdown() {
        return this.shutdown;
    }

    /**
     * Shutdown the JDI Event Hub.
     */
    public void shutdown() {
        this.shutdown = true;
        if (this.executor != null) {
            this.executor.shutdown();
        }
    }

    @Override
    public void run() {
        VirtualMachine jvm = this.target.getVM();
        if (jvm != null) {
            EventQueue eventQueue = jvm.eventQueue();
            EventSet eventSet = null;
            executor = new ThreadPoolExecutor(0, 5, 30L, TimeUnit.SECONDS, new SynchronousQueue<Runnable>());
            while (!isShutdown()) {
                try {
                    eventSet = eventQueue.remove(1000);
                } catch (InterruptedException | VMDisconnectedException e) {
                    break;
                }

                if (!isShutdown() && eventSet != null) {
                    final EventSet currentEventSet = eventSet;
                    executor.submit(new Runnable() {

                        @Override
                        public void run() {
                            dispatch(currentEventSet);
                        }

                    });
                }
            }
        }
    }

    private void dispatch(EventSet eventSet) {
        EventIterator eventIter = eventSet.eventIterator();
        // print JDI Events
        StringBuffer buf = new StringBuffer("JDI Event Set: {\n"); //$NON-NLS-1$
        while (eventIter.hasNext()) {
            buf.append(eventIter.next());
            if (eventIter.hasNext()) {
                buf.append(", ");
            }
        }
        buf.append("}\n");
        Logger.logInfo(buf.toString());

        eventIter = eventSet.eventIterator();
        boolean vote = false;
        boolean resume = true;
        while (eventIter.hasNext()) {
            if (isShutdown()) {
                return;
            }
            Event event = eventIter.nextEvent();
            IJDIEventListener listener = this.eventHandlers.get(event.request());
            if (listener != null) {
                vote = true;
                resume = listener.handleEvent(event, this.target, !resume, eventSet) && resume;
                continue;
            }

            // Dispatch VM start/end events
            if (event instanceof VMDeathEvent) {
                this.target.handleVMDeath((VMDeathEvent) event);
                shutdown(); // stop listening for events
            } else if (event instanceof VMDisconnectEvent) {
                this.target.handleVMDisconnect((VMDisconnectEvent) event);
                shutdown(); // stop listening for events
            } else if (event instanceof VMStartEvent) {
                target.handleVMStart((VMStartEvent) event);
            } else {
                // not handled
            }
        }

        if (vote && resume) {
            try {
                eventSet.resume();
            } catch (VMDisconnectedException e) {
                // empty
            } catch (RuntimeException e) {
                // empty
            }
        }
    }

}
