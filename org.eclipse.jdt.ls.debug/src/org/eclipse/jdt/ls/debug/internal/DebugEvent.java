package org.eclipse.jdt.ls.debug.internal;

import com.sun.jdi.event.Event;

public class DebugEvent {
    public Event event = null;
    public boolean shouldResume = true;
}
