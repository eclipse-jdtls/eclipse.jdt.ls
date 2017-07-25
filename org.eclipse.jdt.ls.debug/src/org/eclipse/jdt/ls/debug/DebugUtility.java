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

import org.eclipse.jdt.ls.debug.internal.DebugSession;

import com.sun.jdi.ThreadReference;
import com.sun.jdi.VirtualMachineManager;
import com.sun.jdi.connect.Connector.Argument;
import com.sun.jdi.connect.IllegalConnectorArgumentsException;
import com.sun.jdi.connect.LaunchingConnector;
import com.sun.jdi.connect.VMStartException;

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

    public static IDebugSession attach(/* TODO: arguments? */) {
        throw new UnsupportedOperationException();
    }

    public static void stepOver(ThreadReference thread) {
        throw new UnsupportedOperationException();
    }

    public static void stepInto(ThreadReference thread) {
        throw new UnsupportedOperationException();
    }

    public static void stepOut(ThreadReference thread) {
        throw new UnsupportedOperationException();
    }
}

