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

package org.eclipse.jdt.ls.debug;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import org.eclipse.jdt.ls.debug.internal.DebugSession;

import com.sun.jdi.Location;
import com.sun.jdi.ThreadReference;
import com.sun.jdi.VirtualMachineManager;
import com.sun.jdi.connect.AttachingConnector;
import com.sun.jdi.connect.Connector.Argument;
import com.sun.jdi.connect.IllegalConnectorArgumentsException;
import com.sun.jdi.connect.LaunchingConnector;
import com.sun.jdi.connect.VMStartException;
import com.sun.jdi.event.StepEvent;
import com.sun.jdi.request.EventRequest;
import com.sun.jdi.request.StepRequest;

public class DebugUtility {
    public static IDebugSession launch(VirtualMachineManager vmManager, String mainClass, List<String> classPaths)
            throws IOException, IllegalConnectorArgumentsException, VMStartException {
        return DebugUtility.launch(vmManager, mainClass, String.join(File.pathSeparator, classPaths));
    }

    /**
     * Launches a debuggee in suspend mode.
     *
     * @param vmManager
     *            the virtual machine manager.
     * @param mainClass
     *            the main class.
     * @param classPaths
     *            the class paths.
     * @return an instance of IDebugSession.
     * @throws IOException
     *             when unable to launch.
     * @throws IllegalConnectorArgumentsException
     *             when one of the arguments is invalid.
     * @throws VMStartException
     *             when the debuggee was successfully launched, but terminated
     *             with an error before a connection could be established.
     */
    public static IDebugSession launch(VirtualMachineManager vmManager, String mainClass, String classPaths)
            throws IOException, IllegalConnectorArgumentsException, VMStartException {
        List<LaunchingConnector> connectors = vmManager.launchingConnectors();
        LaunchingConnector connector = connectors.get(0);

        Map<String, Argument> arguments = connector.defaultArguments();
        arguments.get("options").setValue("-cp " + classPaths);
        arguments.get("suspend").setValue("true");
        arguments.get("main").setValue(mainClass);

        return new DebugSession(connector.launch(arguments));
    }

    /**
     * Attach to an existing debuggee VM.
     * @param vmManager
     *               the virtual machine manager
     * @param hostName
     *               the machine where the debuggee VM is launched on
     * @param port
     *               the debug port that the debuggee VM exposed
     * @param attachTimeout
     *               the timeout when attaching to the debuggee VM
     * @return an instance of IDebugSession
     * @throws IOException
     *               when unable to attach.
     * @throws IllegalConnectorArgumentsException
     *               when one of the connector arguments is invalid.
     */
    public static IDebugSession attach(VirtualMachineManager vmManager, String hostName, int port, int attachTimeout)
            throws IOException, IllegalConnectorArgumentsException {
        List<AttachingConnector> connectors = vmManager.attachingConnectors();
        AttachingConnector connector = connectors.get(0);
        Map<String, Argument> arguments = connector.defaultArguments();
        arguments.get("hostname").setValue(hostName);
        arguments.get("port").setValue(String.valueOf(port));
        arguments.get("timeout").setValue(String.valueOf(attachTimeout));
        return new DebugSession(connector.attach(arguments));
    }

    /**
     * Steps over newly pushed frames.
     *
     * @param thread
     *            the target thread.
     * @param eventHub
     *            the {@link IEventHub} instance.
     * @return the new {@link Location} of the execution flow of the specified
     *         thread.
     */
    public static CompletableFuture<Location> stepOver(ThreadReference thread, IEventHub eventHub) {
        return DebugUtility.step(thread, eventHub, StepRequest.STEP_LINE, StepRequest.STEP_OVER);
    }

    /**
     * Steps into newly pushed frames.
     *
     * @param thread
     *            the target thread.
     * @param eventHub
     *            the {@link IEventHub} instance.
     * @return the new {@link Location} of the execution flow of the specified
     *         thread.
     */
    public static CompletableFuture<Location> stepInto(ThreadReference thread, IEventHub eventHub) {
        return DebugUtility.step(thread, eventHub, StepRequest.STEP_LINE, StepRequest.STEP_INTO);
    }

    /**
     * Steps out of the current frame.
     *
     * @param thread
     *            the target thread.
     * @param eventHub
     *            the {@link IEventHub} instance.
     * @return the new {@link Location} of the execution flow of the specified
     *         thread.
     */
    public static CompletableFuture<Location> stepOut(ThreadReference thread, IEventHub eventHub) {
        return DebugUtility.step(thread, eventHub, StepRequest.STEP_LINE, StepRequest.STEP_OUT);
    }

    private static CompletableFuture<Location> step(ThreadReference thread, IEventHub eventHub, int stepSize,
            int stepDepth) {
        CompletableFuture<Location> future = new CompletableFuture<Location>();

        StepRequest request = thread.virtualMachine().eventRequestManager().createStepRequest(thread, stepSize,
                stepDepth);

        eventHub.stepEvents().filter(debugEvent -> request.equals(debugEvent.event.request())).take(1)
                .subscribe(debugEvent -> {
                    StepEvent event = (StepEvent) debugEvent.event;
                    future.complete(event.location());
                    thread.virtualMachine().eventRequestManager().deleteEventRequest(request);
                });
        request.setSuspendPolicy(EventRequest.SUSPEND_EVENT_THREAD);
        request.addCountFilter(1);
        request.enable();

        thread.resume();

        return future;
    }
}
