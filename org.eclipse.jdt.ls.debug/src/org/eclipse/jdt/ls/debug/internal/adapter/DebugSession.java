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

import java.io.IOException;
import java.net.URI;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import org.eclipse.jdt.ls.debug.internal.adapter.DispatcherProtocol.IResponder;
import org.eclipse.jdt.ls.debug.internal.adapter.Results.DebugResult;
import org.eclipse.jdt.ls.debug.internal.adapter.Results.SetBreakpointsResponseBody;
import org.eclipse.jdt.ls.debug.internal.adapter.Types.Capabilities;
import org.eclipse.jdt.ls.debug.internal.core.EventType;
import org.eclipse.jdt.ls.debug.internal.core.IBreakpoint;
import org.eclipse.jdt.ls.debug.internal.core.IBreakpointManager;
import org.eclipse.jdt.ls.debug.internal.core.IDebugContext;
import org.eclipse.jdt.ls.debug.internal.core.IDebugEvent;
import org.eclipse.jdt.ls.debug.internal.core.IDebugEventSetListener;
import org.eclipse.jdt.ls.debug.internal.core.IThread;
import org.eclipse.jdt.ls.debug.internal.core.IVMTarget;
import org.eclipse.jdt.ls.debug.internal.core.breakpoints.JavaLineBreakpoint;
import org.eclipse.jdt.ls.debug.internal.core.impl.DebugContext;
import org.eclipse.jdt.ls.debug.internal.core.impl.JDIThread;
import org.eclipse.jdt.ls.debug.internal.core.impl.JDIVMTarget;
import org.eclipse.jdt.ls.debug.internal.core.log.Logger;

import com.google.gson.JsonObject;
import com.sun.jdi.AbsentInformationException;
import com.sun.jdi.IncompatibleThreadStateException;
import com.sun.jdi.Location;
import com.sun.jdi.Method;
import com.sun.jdi.StackFrame;
import com.sun.jdi.ThreadReference;
import com.sun.jdi.VMDisconnectedException;
import com.sun.jdi.VirtualMachine;
import com.sun.jdi.connect.IllegalConnectorArgumentsException;
import com.sun.jdi.connect.VMStartException;
import com.sun.jdi.event.BreakpointEvent;
import com.sun.jdi.event.StepEvent;

public class DebugSession implements IDebugSession, IDebugEventSetListener {
    protected boolean debuggerLinesStartAt1;
    protected boolean debuggerPathsAreURI;
    protected boolean clientLinesStartAt1 = true;
    protected boolean clientPathsAreURI = true;

    private String cwd;
    private String[] sourcePath;
    private IResponder responder;
    private boolean shutdown = false;
    private IDebugContext debugContext;
    private IVMTarget vmTarget;
    private IdCollection<StackFrame> frameCollection = new IdCollection<>();

    /**
     * Constructs a DebugSession instance.
     */
    public DebugSession(boolean debuggerLinesStartAt1, boolean debuggerPathsAreURI, IResponder responder) {
        this.debuggerLinesStartAt1 = debuggerLinesStartAt1;
        this.debuggerPathsAreURI = debuggerPathsAreURI;
        this.responder = responder;
    }

    @Override
    public DebugResult dispatch(String command, JsonObject args) {
        if (this.shutdown) {
            return new DebugResult();
        }
        Logger.logInfo("Dispatch command:" + command);
        try {
            switch (command) {
            case "initialize":
                return initialize(JsonUtils.fromJson(args, Requests.InitializeArguments.class));

            case "launch":
                return launch(JsonUtils.fromJson(args, Requests.LaunchArguments.class));

            case "attach":
                return attach(JsonUtils.fromJson(args, Requests.AttachArguments.class));

            case "disconnect":
                return disconnect();

            case "configurationDone":
                return configurationDone();

            case "next":
                return next(JsonUtils.fromJson(args, Requests.NextArguments.class));

            case "continue":
                return resume(JsonUtils.fromJson(args, Requests.ContinueArguments.class));

            case "stepIn":
                return stepIn(JsonUtils.fromJson(args, Requests.StepInArguments.class));

            case "stepOut":
                return stepOut(JsonUtils.fromJson(args, Requests.StepOutArguments.class));

            case "pause":
                return pause(JsonUtils.fromJson(args, Requests.PauseArguments.class));

            case "stackTrace":
                return stackTrace(JsonUtils.fromJson(args, Requests.StackTraceArguments.class));

            case "scopes":
                return scopes(JsonUtils.fromJson(args, Requests.ScopesArguments.class));

            case "variables":
                Requests.VariablesArguments arguments = JsonUtils.fromJson(args, Requests.VariablesArguments.class);
                if (arguments.variablesReference == -1) {
                    return new DebugResult(1009, String.format("%s: property '%s' is missing, null, or empty",
                            "variables", "variablesReference"), null);
                }
                return variables(arguments);

            case "setVariable":
                Requests.SetVariableArguments setVarArguments = JsonUtils.fromJson(args,
                        Requests.SetVariableArguments.class);
                if (setVarArguments.value == null) {
                    // Just exit out of editing if we're given an empty
                    // expression.
                    return new DebugResult();
                }
                if (setVarArguments.variablesReference == -1) {
                    return new DebugResult(1106, String.format("%s: property '%s' is missing, null, or empty",
                            "setVariable", "variablesReference"), null);
                }
                if (setVarArguments.name == null) {
                    return new DebugResult(1106,
                            String.format("%s: property '%s' is missing, null, or empty", "setVariable", "name"), null);
                }
                return setVariable(setVarArguments);

            case "source":
                Requests.SourceArguments sourceArguments = JsonUtils.fromJson(args, Requests.SourceArguments.class);
                if (sourceArguments.sourceReference == -1) {
                    return new DebugResult(1010,
                            String.format("%s: property '%s' is missing, null, or empty", "source", "sourceReference"),
                            null);
                }
                return source(sourceArguments);

            case "threads":
                return threads();

            case "setBreakpoints":
                Requests.SetBreakpointArguments setBreakpointArguments = JsonUtils.fromJson(args,
                        Requests.SetBreakpointArguments.class);
                SetBreakpointsResponseBody body = setBreakpoints(setBreakpointArguments);
                return new DebugResult(body);

            case "setExceptionBreakpoints":
                return setExceptionBreakpoints(
                        JsonUtils.fromJson(args, Requests.SetExceptionBreakpointsArguments.class));

            case "setFunctionBreakpoints":
                Requests.SetFunctionBreakpointsArguments setFuncBreakpointArguments = JsonUtils.fromJson(args,
                        Requests.SetFunctionBreakpointsArguments.class);
                if (setFuncBreakpointArguments.breakpoints != null) {
                    // FunctionBreakpoint[] breakpoints =
                    // mapper.readValue(args.getString("breakpoints"),
                    // FunctionBreakpoint[].class);
                    // return SetFunctionBreakpoints(breakpoints);
                }
                return new DebugResult(1012, String.format("%s: property '%s' is missing, null, or empty",
                        "setFunctionBreakpoints", "breakpoints"), null);

            case "evaluate":
                Requests.EvaluateArguments evaluateArguments = JsonUtils.fromJson(args,
                        Requests.EvaluateArguments.class);
                if (evaluateArguments.expression == null) {
                    return new DebugResult(1013,
                            String.format("%s: property '%s' is missing, null, or empty", "evaluate", "expression"),
                            null);
                }
                return evaluate(evaluateArguments);

            default:
                return new DebugResult(1014, "unrecognized request: {_request}",
                        JsonUtils.fromJson("{ _request:" + command + "}", JsonObject.class));
            }
        } catch (Exception e) {
            Logger.logException("DebugSession dispatch exception", e);
        }
        return null;
    }

    @Override
    public DebugResult initialize(Requests.InitializeArguments arguments) {
        this.clientLinesStartAt1 = arguments.linesStartAt1;
        String pathFormat = arguments.pathFormat;
        if (pathFormat != null) {
            switch (pathFormat) {
                case "uri":
                    this.clientPathsAreURI = true;
                    break;
                default:
                    this.clientPathsAreURI = false;
                }
        }
        Capabilities caps = new Capabilities();
        caps.supportsConfigurationDoneRequest = true;
        // caps.supportsFunctionBreakpoints = true;
        // caps.supportsSetVariable = true;
        // caps.supportsConditionalBreakpoints = false;
        // caps.supportsEvaluateForHovers = true;
        caps.supportsDelayedStackTraceLoading = true;
        caps.exceptionBreakpointFilters = new ArrayList<>();
        DebugResult result = new DebugResult(new Results.InitializeResponseBody(caps));
        result.add(new Events.InitializedEvent());
        return result;
    }

    @Override
    public DebugResult launch(Requests.LaunchArguments arguments) {
        this.cwd = arguments.cwd;
        String mainClass = arguments.startupClass;
        String[] classPathArray = arguments.classPath;
        String classpath = String.join(System.getProperty("path.separator"), classPathArray);
        classpath = classpath.replaceAll("\\\\", "/");
        if (arguments.sourcePath == null || arguments.sourcePath.length == 0) {
            this.sourcePath = new String[] { cwd };
        } else {
            this.sourcePath = new String[arguments.sourcePath.length];
            System.arraycopy(arguments.sourcePath, 0, this.sourcePath, 0, arguments.sourcePath.length);
        }
        
        if (mainClass.endsWith(".java")) {
            mainClass = mainClass.substring(0, mainClass.length() - 5);
        }
        Logger.logInfo("Launch JVM with main class \"" + mainClass + "\", -classpath \"" + classpath + "\"");
        try {
            Launcher launcher = new Launcher();
            VirtualMachine vm = launcher.launchJVM(mainClass, classpath);
            this.debugContext = new DebugContext();
            this.vmTarget = new JDIVMTarget(debugContext, vm, false);
        } catch (IOException | IllegalConnectorArgumentsException | VMStartException e) {
            Logger.logException("Launching debuggee vm exception", e);
            return new DebugResult(3001, "Cannot launch jvm.", null);
        }
        return new DebugResult();
    }

    @Override
    public DebugResult attach(Requests.AttachArguments arguments) {
        return null;
    }

    /**
     * VS Code terminates a debug session with the disconnect request.
     */
    @Override
    public DebugResult disconnect() {
        this.debugContext.getDebugEventHub().removeDebugEventSetListener(this);
        return new DebugResult(new Events.TerminatedEvent());
    }
    
    /**
     * VS Code sends a configurationDone request to indicate the end of configuration sequence.
     */
    public DebugResult configurationDone() {
        this.debugContext.getDebugEventHub().addDebugEventSetListener(this);
        // The configuration sequence has done, then resume VM.
        this.vmTarget.getVM().resume();
        return new DebugResult();
    }

    @Override
    public DebugResult setFunctionBreakpoints(Requests.SetFunctionBreakpointsArguments arguments) {
        return null;
    }

    @Override
    public SetBreakpointsResponseBody setBreakpoints(Requests.SetBreakpointArguments arguments) {
        IBreakpointManager bpManager = this.vmTarget.getDebugContext().getBreakpointManager();
        DebugUtils.addBreakpoint(arguments.source, arguments.breakpoints, bpManager);
        List<Types.Breakpoint> res = new ArrayList<>();
        int i = 1;
        for (IBreakpoint bp : bpManager.getBreakpoints()) {
            JavaLineBreakpoint lbp = (JavaLineBreakpoint) bp;
            res.add(new Types.Breakpoint(i, true, lbp.getLineNumber(), "Line breakpoint"));
            i++;
        }

        return new SetBreakpointsResponseBody(res);
    }

    @Override
    public DebugResult setExceptionBreakpoints(Requests.SetExceptionBreakpointsArguments arguments) {
        return null;
    }

    @Override
    public DebugResult resume(Requests.ContinueArguments arguments) {
        for (IThread ithread : this.vmTarget.getThreads()) {
            JDIThread jdiThread = (JDIThread) ithread;
            if (jdiThread.getUnderlyingThread().uniqueID() == arguments.threadId) {
                jdiThread.resume();
            }
        }

        return new DebugResult();
    }

    @Override
    public DebugResult next(Requests.NextArguments arguments) {
        try {
            for (IThread ithread : this.vmTarget.getThreads()) {
                JDIThread jdiThread = (JDIThread) ithread;
                if (jdiThread.getUnderlyingThread().uniqueID() == arguments.threadId) {
                    jdiThread.stepOver();
                }
            }
        } catch (Exception e) {
            Logger.logException("DebugSession#next exception", e);
        }
        return new DebugResult();
    }

    @Override
    public DebugResult stepIn(Requests.StepInArguments arguments) {
        try {
            for (IThread ithread : this.vmTarget.getThreads()) {
                JDIThread jdiThread = (JDIThread) ithread;
                if (jdiThread.getUnderlyingThread().uniqueID() == arguments.threadId) {
                    jdiThread.stepInto();
                }
            }
        } catch (Exception e) {
            Logger.logException("DebugSession#stepIn exception", e);
        }
        return new DebugResult();
    }

    @Override
    public DebugResult stepOut(Requests.StepOutArguments arguments) {
        try {
            for (IThread ithread : this.vmTarget.getThreads()) {
                JDIThread jdiThread = (JDIThread) ithread;
                if (jdiThread.getUnderlyingThread().uniqueID() == arguments.threadId) {
                    jdiThread.stepOver();
                }
            }
        } catch (Exception e) {
            Logger.logException("DebugSession#stepOut exception", e);
        }
        return new DebugResult();
    }

    @Override
    public DebugResult pause(Requests.PauseArguments arguments) {
        return null;
    }

    @Override
    public DebugResult threads() {
        if (this.shutdown) {
            return new DebugResult(new Results.ThreadsResponseBody(new ArrayList<Types.Thread>()));
        }
        ArrayList<Types.Thread> threads = new ArrayList<>();
        try {
            for (IThread thread : this.vmTarget.getThreads()) {
                JDIThread jdiThread = (JDIThread) thread;
                ThreadReference tr = jdiThread.getUnderlyingThread();
                threads.add(new Types.Thread(tr.uniqueID(), tr.name()));
            }
            return new DebugResult(new Results.ThreadsResponseBody(threads));
        } catch (VMDisconnectedException ex) {
            return new DebugResult(new Results.ThreadsResponseBody(new ArrayList<Types.Thread>()));
        }
    }

    @Override
    public DebugResult stackTrace(Requests.StackTraceArguments arguments) {
        List<Types.StackFrame> result = new ArrayList<>();
        if (arguments.startFrame < 0 || arguments.levels < 0) {
            return new DebugResult(new Results.StackTraceResponseBody(result, 0));
        }
        try {
            for (IThread ithread : this.vmTarget.getThreads()) {
                JDIThread jdiThread = (JDIThread) ithread;
                if (jdiThread.getUnderlyingThread().uniqueID() == arguments.threadId) {
                    List<StackFrame> stackFrames = jdiThread.getUnderlyingThread().frames();
                    if (arguments.startFrame >= stackFrames.size()) {
                        return new DebugResult(new Results.StackTraceResponseBody(result, 0));
                    }
                    if (arguments.levels == 0) {
                        arguments.levels = stackFrames.size() - arguments.startFrame;
                    } else {
                        arguments.levels = Math.min(stackFrames.size() - arguments.startFrame, arguments.levels);
                    }

                    for (int i = 0; i < arguments.levels; i++) {
                        StackFrame stackFrame = stackFrames.get(arguments.startFrame + i);
                        int frameId = this.frameCollection.create(stackFrame);
                        Location location = stackFrame.location();
                        Method method = location.method();
                        // TODO Will use LS to get real source path of the
                        // class.
                        String originalSourcePath = location.sourcePath();
                        String fullpath = DebugUtils.sourceLookup(this.sourcePath, originalSourcePath);
                        if (fullpath == null) {
                            fullpath = Paths.get(this.cwd, originalSourcePath).toString();
                        }
                        Types.StackFrame newFrame = new Types.StackFrame(frameId, method.name(),
                                new Types.Source(fullpath, 0), location.lineNumber(), 0);
                        result.add(newFrame);
                    }
                }
            }
        } catch (IncompatibleThreadStateException | AbsentInformationException e) {
            Logger.logException("DebugSession#stackTrace exception", e);
        }
        return new DebugResult(new Results.StackTraceResponseBody(result, result.size()));
    }

    @Override
    public DebugResult scopes(Requests.ScopesArguments arguments) {
        List<Types.Scope> scps = new ArrayList<>();
        scps.add(new Types.Scope("Local", 1000000 + arguments.frameId, false));
        return new DebugResult(new Results.ScopesResponseBody(scps));
    }

    @Override
    public DebugResult variables(Requests.VariablesArguments arguments) {
        List<Types.Variable> list = new ArrayList<>();
        return new DebugResult(new Results.VariablesResponseBody(list));
    }

    @Override
    public DebugResult setVariable(Requests.SetVariableArguments arguments) {
        return null;
    }

    @Override
    public DebugResult source(Requests.SourceArguments arguments) {
        return null;
    }

    @Override
    public DebugResult evaluate(Requests.EvaluateArguments arguments) {
        return null;
    }

    protected int convertDebuggerLineToClient(int line) {
        if (this.debuggerLinesStartAt1) {
            return this.clientLinesStartAt1 ? line : line - 1;
        } else {
            return this.clientLinesStartAt1 ? line + 1 : line;
        }
    }

    protected int convertClientLineToDebugger(int line) {
        if (this.debuggerLinesStartAt1) {
            return this.clientLinesStartAt1 ? line : line + 1;
        } else {
            return this.clientLinesStartAt1 ? line - 1 : line;
        }
    }

    protected int convertDebuggerColumnToClient(int column) {
        return column;
    }

    protected String convertDebuggerPathToClient(String path) {
        if (this.debuggerPathsAreURI) {
            if (this.clientPathsAreURI) {
                return path;
            } else {
                URI uri = Paths.get(path).toUri();
                return uri.getPath();
            }
        } else {
            if (this.clientPathsAreURI) {
                return Paths.get(path).toUri().getPath();

            } else {
                return path;
            }
        }
    }

    protected String convertClientPathToDebugger(String clientPath) {
        if (clientPath == null) {
            return null;
        }

        if (this.debuggerPathsAreURI) {
            if (this.clientPathsAreURI) {
                return clientPath;
            } else {
                return Paths.get(clientPath).toUri().getPath();
            }
        } else {
            if (this.clientPathsAreURI) {
                return Paths.get(clientPath).toUri().getPath();
            } else {
                return clientPath;
            }
        }
    }

    @Override
    public void handleDebugEvents(IDebugEvent[] events) {
        for (IDebugEvent event : events) {
            EventType type = event.getKind();
            Object source = event.getSource();
            switch (type) {
            case VMSTART_EVENT:
                break;
            case VMDEATH_EVENT:
                if (!this.shutdown) {
                    Events.ExitedEvent exitedEvent = new Events.ExitedEvent(0);
                    Events.TerminatedEvent terminatedEvent = new Events.TerminatedEvent();
                    this.responder.addEvent(exitedEvent.type, exitedEvent);
                    this.responder.addEvent(terminatedEvent.type, terminatedEvent);
                }
                this.shutdown = true;
                break;
            case THREADSTART_EVENT:
                if (source instanceof IThread) {
                    IThread jdiThread = (IThread) source;
                    ThreadReference startThread = jdiThread.getUnderlyingThread();
                    Events.ThreadEvent threadEvent = new Events.ThreadEvent("started", startThread.uniqueID());
                    this.responder.addEvent(threadEvent.type, threadEvent);
                }
                break;
            case THREADDEATH_EVENT:
                if (source instanceof IThread) {
                    IThread jdiThread = (IThread) source;
                    ThreadReference deathThread = jdiThread.getUnderlyingThread();
                    Events.ThreadEvent threadDeathEvent = new Events.ThreadEvent("exited", deathThread.uniqueID());
                    this.responder.addEvent(threadDeathEvent.type, threadDeathEvent);
                }
                break;
            case BREAKPOINT_EVENT:
                BreakpointEvent bpEvent = (BreakpointEvent) source;
                ThreadReference bpThread = bpEvent.thread();
                Location bpLocation = bpEvent.location();
                try {
                    // TODO Need use Language server to get absolute source
                    // path.
                    Events.StoppedEvent stopevent = new Events.StoppedEvent("breakpoint",
                            new Types.Source(cwd + "/" + bpLocation.sourcePath(), 0), bpLocation.lineNumber(), 0,
                            "", bpThread.uniqueID());
                    this.responder.addEvent(stopevent.type, stopevent);
                } catch (AbsentInformationException e) {
                    Logger.logException("Get breakpoint info exception", e);
                }
                break;
            case STEP_EVENT:
                StepEvent stepEvent = (StepEvent) source;
                ThreadReference stepThread = stepEvent.thread();
                Location stepLocation = stepEvent.location();
                Events.StoppedEvent stopevent;
                try {
                    stopevent = new Events.StoppedEvent("step",
                            new Types.Source(cwd + "/" + stepLocation.sourcePath(), 0), stepLocation.lineNumber(),
                            0, "", stepThread.uniqueID());
                    this.responder.addEvent(stopevent.type, stopevent);
                } catch (AbsentInformationException e) {
                    Logger.logException("Get step info exception", e);
                }
                break;
            default:
                // nothing
            }
        }
    }

}
