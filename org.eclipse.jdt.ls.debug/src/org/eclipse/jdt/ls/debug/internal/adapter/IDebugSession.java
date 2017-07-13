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

import org.eclipse.jdt.ls.debug.internal.adapter.Results.DebugResult;
import org.eclipse.jdt.ls.debug.internal.adapter.Results.SetBreakpointsResponseBody;

import com.google.gson.JsonObject;

public interface IDebugSession {
    DebugResult dispatch(String command, JsonObject args);

    DebugResult initialize(Requests.InitializeArguments arguments);

    DebugResult launch(Requests.LaunchArguments arguments);

    DebugResult attach(Requests.AttachArguments arguments);

    DebugResult disconnect();

    DebugResult setFunctionBreakpoints(Requests.SetFunctionBreakpointsArguments arguments);

    // NOTE: This method should never return a failure result, as this causes
    // the launch to be aborted half-way
    // through. Instead, failures should be returned as unverified breakpoints.
    SetBreakpointsResponseBody setBreakpoints(Requests.SetBreakpointArguments arguments);

    DebugResult setExceptionBreakpoints(Requests.SetExceptionBreakpointsArguments arguments);

    DebugResult resume(Requests.ContinueArguments arguments);

    DebugResult next(Requests.NextArguments arguments);

    DebugResult stepIn(Requests.StepInArguments arguments);

    DebugResult stepOut(Requests.StepOutArguments arguments);

    DebugResult pause(Requests.PauseArguments arguments);

    DebugResult threads();

    DebugResult stackTrace(Requests.StackTraceArguments arguments);

    DebugResult scopes(Requests.ScopesArguments arguments);

    DebugResult variables(Requests.VariablesArguments arguments);

    DebugResult setVariable(Requests.SetVariableArguments arguments);

    DebugResult source(Requests.SourceArguments arguments);

    DebugResult evaluate(Requests.EvaluateArguments arguments);
}
