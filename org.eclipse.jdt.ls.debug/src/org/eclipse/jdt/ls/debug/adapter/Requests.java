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

/**
 * The request arguments types defined by VSCode Debug Protocol.
 */
public class Requests {

    public static class Arguments {

    }

    public static class InitializeArguments extends Arguments {
        public String clientID;
        public String adapterID;
        public String pathFormat;
        public boolean linesStartAt1;
        public boolean columnsStartAt1;
        public boolean supportsVariableType;
        public boolean supportsVariablePaging;
        public boolean supportsRunInTerminalRequest;
    }

    public static class LaunchArguments extends Arguments {
        public String type;
        public String name;
        public String request;
        public String cwd;
        public String startupClass;
        public String projectName;
        public String classpath;
        public String[] sourcePath = new String[0];
        public boolean stopOnEntry = false;
        public String[] options = new String[0];
    }

    public static class AttachArguments extends Arguments {

    }

    public static class RestartArguments extends Arguments {

    }

    public static class DisconnectArguments extends Arguments {
        // If client doesn't set terminateDebuggee attribute at the DisconnectRequest,
        // the debugger would choose to terminate debuggee by default.
        public boolean terminateDebuggee = true;
        public boolean restart;
    }

    public static class SetBreakpointArguments extends Arguments {
        public Types.Source source;
        public int[] lines = new int[0];
        public Types.SourceBreakpoint[] breakpoints = new Types.SourceBreakpoint[0];
        public boolean sourceModified = false;
    }

    public static class StackTraceArguments extends Arguments {
        public int threadId;
        public int startFrame;
        public int levels;
    }

    public static class SetFunctionBreakpointsArguments extends Arguments {
        public Types.FunctionBreakpoint[] breakpoints;
    }

    public static class SetExceptionBreakpointsArguments extends Arguments {
        public String[] filters = new String[0];
    }

    public static class ContinueArguments extends Arguments {
        public int threadId;
    }

    public static class NextArguments extends Arguments {
        public int threadId;
    }

    public static class StepInArguments extends Arguments {
        public int threadId;
        public int targetId;
    }

    public static class StepOutArguments extends Arguments {
        public int threadId;
    }

    public static class PauseArguments extends Arguments {
        public int threadId;
    }

    public static class ScopesArguments extends Arguments {
        public int frameId;
    }

    public static class VariablesArguments extends Arguments {
        public int variablesReference = -1;
        public String filter;
        public int start;
        public int count;
    }

    public static class SetVariableArguments extends Arguments {
        public int variablesReference;
        public String name;
        public String value;
    }

    public static class SourceArguments extends Arguments {
        public int sourceReference;
    }

    public static class EvaluateArguments extends Arguments {
        public String expression;
        public int frameId;
        public String context;
    }
}
