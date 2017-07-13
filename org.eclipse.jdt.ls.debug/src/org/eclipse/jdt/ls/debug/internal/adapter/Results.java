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

package org.eclipse.jdt.ls.debug.internal.adapter;

import java.util.ArrayList;
import java.util.List;

import com.google.gson.JsonObject;

/**
 * The response content types defined by VSCode Debug Protocol.
 */
public class Results {
    /**
     * This monomorphic class is used to return results from a debugger request
     * or to return errors. In addition events can be attached that are fired
     * after the request results have been returned to the caller.
     */
    public static class DebugResult {
        public boolean success; // boolean indicating success
        public ResponseBody body; // depending on value of success either the
        public List<Events.DebugEvent> events; // send this event after

        /**
         * A success result without additional data.
         */
        public DebugResult() {
            this.success = true;
        }

        /**
         * A success result with as additional event.
         */
        public DebugResult(Events.DebugEvent ev) {
            this.success = true;
            add(ev);
        }

        /**
         * A result with a response body. If body is a ErrorResponseBody then
         * Success will be set to false.
         */
        public DebugResult(ResponseBody body) {
            this.success = true;
            this.body = body;
            if (body instanceof ErrorResponseBody) {
                this.success = false;
            }
        }

        /**
         * A failure result with a full error message.
         */
        public DebugResult(int id, String format, JsonObject arguments) {
            this.success = false;
            this.body = new ErrorResponseBody(new Types.Message(id, format, arguments));
        }

        /**
         * Add a DebugEvent to this request result. Events are fired after the
         * result is returned to the caller of the request.
         */
        public void add(Events.DebugEvent ev) {
            if (ev != null) {
                if (this.events == null) {
                    this.events = new ArrayList<>();
                }
                this.events.add(ev);
            }
        }
    }

    /**
     * subclasses of ResponseBody are serialized as the response body. Don't
     * change their instance variables since that will break the OpenDebug
     * protocol.
     */
    public static class ResponseBody {
        // empty
    }

    public static class InitializeResponseBody extends ResponseBody {
        public Types.Capabilities body;

        public InitializeResponseBody(Types.Capabilities capabilities) {
            body = capabilities;
        }
    }

    public static class ErrorResponseBody extends ResponseBody {
        public Types.Message error;

        public ErrorResponseBody(Types.Message m) {
            error = m;
        }
    }

    public static class StackTraceResponseBody extends ResponseBody {
        public Types.StackFrame[] stackFrames;

        public int totalFrames;

        /**
         * Constructs an StackTraceResponseBody with the given stack frame list.
         * @param frames
         *              a {@link Types.StackFrame} list
         * @param total
         *              the total frame number
         */
        public StackTraceResponseBody(List<Types.StackFrame> frames, int total) {
            if (frames == null) {
                stackFrames = new Types.StackFrame[0];
            } else {
                stackFrames = frames.toArray(new Types.StackFrame[0]);
            }

            totalFrames = total;
        }
    }

    public static class ScopesResponseBody extends ResponseBody {
        public Types.Scope[] scopes;

        /**
         * Constructs a ScopesResponseBody with the Scope list.
         * @param scps
         *              a {@link Types.Scope} list
         */
        public ScopesResponseBody(List<Types.Scope> scps) {
            if (scps == null) {
                scopes = new Types.Scope[0];
            } else {
                scopes = scps.toArray(new Types.Scope[0]);
            }
        }
    }

    public static class VariablesResponseBody extends ResponseBody {
        public Types.Variable[] variables;

        /**
         * Constructs a VariablesResponseBody with the given variable list.
         * @param vars
         *              a {@link Types.Variable} list
         */
        public VariablesResponseBody(List<Types.Variable> vars) {
            if (vars == null) {
                variables = new Types.Variable[0];
            } else {
                variables = vars.toArray(new Types.Variable[0]);
            }
        }
    }

    public static class SetVariablesResponseBody extends ResponseBody {
        public String value;

        public SetVariablesResponseBody(String val) {
            value = val;
        }
    }

    public static class SourceResponseBody extends ResponseBody {
        public String content;

        public SourceResponseBody(String cont) {
            content = cont;
        }
    }

    public static class ThreadsResponseBody extends ResponseBody {
        public Types.Thread[] threads;

        /**
         * Constructs a ThreadsResponseBody with the given thread list.
         * @param vars
         *            a {@link Types.Thread} list
         */
        public ThreadsResponseBody(List<Types.Thread> vars) {
            if (vars == null) {
                threads = new Types.Thread[0];
            } else {
                threads = vars.toArray(new Types.Thread[0]);
            }
        }
    }

    public static class EvaluateResponseBody extends ResponseBody {
        public String result;
        public int variablesReference;
        public String type;

        /**
         * Constructor.
         */
        public EvaluateResponseBody(String value, int reff, String type) {
            this.result = value;
            this.variablesReference = reff;
            this.type = type;
        }
    }

    public static class SetBreakpointsResponseBody extends ResponseBody {
        public Types.Breakpoint[] breakpoints;

        /**
         * Constructs a SetBreakpointsResponssseBody with the given breakpoint list.
         * @param bpts
         *            a {@link Types.Breakpoint} list
         */
        public SetBreakpointsResponseBody(List<Types.Breakpoint> bpts) {
            if (bpts == null) {
                breakpoints = new Types.Breakpoint[0];
            } else {
                breakpoints = bpts.toArray(new Types.Breakpoint[0]);
            }
        }
    }
}
