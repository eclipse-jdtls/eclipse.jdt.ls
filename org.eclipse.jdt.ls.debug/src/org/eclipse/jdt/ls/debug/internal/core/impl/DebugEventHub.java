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
import java.util.List;

import org.eclipse.jdt.ls.debug.internal.core.IDebugEvent;
import org.eclipse.jdt.ls.debug.internal.core.IDebugEventHub;
import org.eclipse.jdt.ls.debug.internal.core.IDebugEventSetListener;

public class DebugEventHub implements IDebugEventHub {
    private List<IDebugEventSetListener> eventListeners;

    public DebugEventHub() {
        this.eventListeners = Collections.synchronizedList(new ArrayList<>(5));
    }

    /**
     * Notifies a DebugEvent array to listeners.
     */
    public void fireDebugEventSet(IDebugEvent[] events) {
        if (events == null || this.eventListeners.isEmpty()) {
            return;
        }

        for (IDebugEventSetListener listener : this.eventListeners) {
            listener.handleDebugEvents(events);
        }
    }

    /**
     * Adds a new DebugEvent Listener to DebugEvent Hub.
     */
    public void addDebugEventSetListener(IDebugEventSetListener listener) {
        synchronized (this.eventListeners) {
            this.eventListeners.add(listener);
        }
    }

    /**
     * Removes the listener from the DebugEvent Hub.
     */
    public void removeDebugEventSetListener(IDebugEventSetListener listener) {
        synchronized (this.eventListeners) {
            this.eventListeners.remove(listener);
        }
    }
}
