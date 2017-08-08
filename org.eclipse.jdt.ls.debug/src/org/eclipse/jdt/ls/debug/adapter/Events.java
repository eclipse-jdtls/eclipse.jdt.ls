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

package org.eclipse.jdt.ls.debug.adapter;

import java.util.HashMap;

/**
 * The event types defined by VSCode Debug Protocol.
 */
public class Events {
    public static class DebugEvent {
        public String type;

        public DebugEvent(String type) {
            this.type = type;
        }
    }

    public static class InitializedEvent extends DebugEvent {
        public InitializedEvent() {
            super("initialized");
        }
    }

    public static class StoppedEvent extends DebugEvent {
        public long threadId;
        public String reason;
        public String description;
        public String text;
        public boolean allThreadsStopped;

        /**
         * Constructor.
         */
        public StoppedEvent(String reason, long threadId) {
            super("stopped");
            this.reason = reason;
            this.threadId = threadId;
            this.allThreadsStopped = false;
        }

        /**
         * Constructor.
         */
        public StoppedEvent(String reason, long threadId, boolean allThreadsStopped) {
            this(reason, threadId);
            this.allThreadsStopped = allThreadsStopped;
        }

        /**
         * Constructor.
         */
        public StoppedEvent(String reason, long threadId, boolean allThreadsStopped, String description, String text) {
            this(reason, threadId, allThreadsStopped);
            this.description = description;
            this.text = text;
        }
    }

    public static class ContinuedEvent extends DebugEvent {
        public long threadId;
        public boolean allThreadsContinued;

        /**
         * Constructor.
         */
        public ContinuedEvent(long threadId) {
            super("continued");
            this.threadId = threadId;
        }

        /**
         * Constructor.
         */
        public ContinuedEvent(long threadId, boolean allThreadsContinued) {
            this(threadId);
            this.allThreadsContinued = allThreadsContinued;
        }

        /**
         * Constructor.
         */
        public ContinuedEvent(boolean allThreadsContinued) {
            super("continued");
            this.allThreadsContinued = allThreadsContinued;
        }
    }

    public static class ExitedEvent extends DebugEvent {
        public int exitCode;

        public ExitedEvent(int code) {
            super("exited");
            this.exitCode = code;
        }
    }

    public static class TerminatedEvent extends DebugEvent {
        public boolean restart;

        public TerminatedEvent() {
            super("terminated");
        }

        public TerminatedEvent(boolean restart) {
            this();
            this.restart = restart;
        }
    }

    public static class ThreadEvent extends DebugEvent {
        public String reason;
        public long threadId;

        /**
         * Constructor.
         */
        public ThreadEvent(String reason, long threadId) {
            super("thread");
            this.reason = reason;
            this.threadId = threadId;
        }
    }

    public static class OutputEvent extends DebugEvent {
        public enum Category {
            console, stdout, stderr, telemetry
        }

        public Category category;
        public String output;
        public HashMap<String, Object> data;

        /**
         * Constructor.
         */
        public OutputEvent(Category category, String output, HashMap<String, Object> data) {
            super("output");
            this.category = category;
            this.output = output;
            this.data = data;
        }
    }

    public static class BreakpointEvent extends DebugEvent {
        public String reason;
        public Types.Breakpoint breakpoint;

        /**
         * Constructor.
         */
        public BreakpointEvent(String reason, Types.Breakpoint breakpoint) {
            super("breakpoint");
            this.reason = reason;
            this.breakpoint = breakpoint;
        }
    }
}
