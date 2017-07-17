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

import java.nio.file.Paths;
import java.util.List;

import com.google.gson.JsonObject;

/**
 * The data types defined by VSCode Debug Protocol.
 */
public class Types {
    public static class Message {
        public int id;
        public String format;
        public JsonObject variables;

        /**
         * Constructs a message with the given information.
         * @param id
         *          message id
         * @param format
         *          a format string
         * @param variables
         *          arguments referenced by the format specifiers in the format string.
         */
        public Message(int id, String format, JsonObject variables) {
            this.id = id;
            this.format = format;
            this.variables = variables;
        }
    }

    public static class StackFrame {
        public int id;
        public Source source;
        public int line;
        public int column;
        public String name;

        /**
         * Constructs a StackFrame with the given information.
         * @param id
         *          the stack frame id
         * @param name
         *          the stack frame name
         * @param src
         *          source info of the stack frame
         * @param ln
         *          line number of the stack frame
         * @param col
         *          column number of the stack frame
         */
        public StackFrame(int id, String name, Source src, int ln, int col) {
            this.id = id;
            this.name = name;
            this.source = src;
            this.line = ln;
            this.column = col;
        }
    }

    public static class Scope {
        public String name;
        public int variablesReference;
        public boolean expensive;

        /**
         * Constructor.
         */
        public Scope(String name, int rf, boolean exp) {
            this.name = name;
            this.variablesReference = rf;
            this.expensive = exp;
        }
    }

    public static class Variable {
        public String name;
        public String value;
        public String type;
        public int variablesReference;
        public int indexedVariables;
        public String evaluateName;

        /**
         * Constructor.
         */
        public Variable(String name, String val, String type, int rf, String evaluateName) {
            this.name = name;
            this.value = val;
            this.type = type;
            this.variablesReference = rf;
            this.evaluateName = evaluateName;
        }
    }

    public static class Thread {
        public long id;
        public String name;

        /**
         * Constructor.
         */
        public Thread(long l, String name) {
            this.id = l;
            if (name == null || name.length() == 0) {
                this.name = String.format("Thread #%d", l);
            } else {
                this.name = name;
            }
        }
    }

    public static class Source {
        public String name;
        public String path;
        public int sourceReference;

        public Source() {
        }

        /**
         * Constructor.
         */
        public Source(String name, String path, int rf) {
            this.name = name;
            this.path = path;
            this.sourceReference = rf;
        }

        /**
         * Constructor.
         */
        public Source(String path, int rf) {
            this.name = Paths.get(path).getFileName().toString();
            this.path = path;
            this.sourceReference = rf;
        }
    }

    public static class Breakpoint {
        public int id;
        public boolean verified;
        public int line;
        public String message;

        /**
         * Constructor.
         */
        public Breakpoint(int id, boolean verified, int line, String message) {
            this.id = id;
            this.verified = verified;
            this.line = line;
            this.message = message;
        }
    }

    public static class SourceBreakpoint {
        public int line;
        public int hitCondition = -1;
        public String condition;

        public SourceBreakpoint() {
        }

        /**
         * Constructor.
         */
        public SourceBreakpoint(int line, String condition, int hitCondition) {
            this.line = line;
            this.condition = condition;
            this.hitCondition = hitCondition;
        }
    }

    public static class FunctionBreakpoint {
        public String name;
        public String condition;
        public String hitCondition;

        public FunctionBreakpoint() {
        }

        public FunctionBreakpoint(String name) {
            this.name = name;
        }
    }

    public static class ExceptionBreakpointFilter {
        public static final String Filter_All = "all";
        public static final String Filter_UserUnhandled = "user-unhandled";

        public String label;
        public String filter;

        /**
         * Constructor.
         */
        public void setFilter(String value) {
            if (!Filter_All.equals(value) && !Filter_UserUnhandled.equals(value)) {
                return;
            }
            this.filter = value;
        }
    }

    public static class Capabilities {
        public boolean supportsConfigurationDoneRequest;
        public boolean supportsFunctionBreakpoints;
        public boolean supportsConditionalBreakpoints;
        public boolean supportsHitConditionalBreakpoints;
        public boolean supportsEvaluateForHovers;
        public boolean supportsStepBack;
        public boolean supportsSetVariable;
        public boolean supportsDelayedStackTraceLoading;
        public List<ExceptionBreakpointFilter> exceptionBreakpointFilters;
    }
}
