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
import java.util.List;
import java.util.Map;

import org.eclipse.jdi.Bootstrap;

import com.sun.jdi.VirtualMachine;
import com.sun.jdi.connect.Connector;
import com.sun.jdi.connect.IllegalConnectorArgumentsException;
import com.sun.jdi.connect.LaunchingConnector;
import com.sun.jdi.connect.VMStartException;

public class Launcher {
    public Launcher() {
    }

    /**
     * Launches and returns a new java virtual machine with the given mainclass and classpath options.
     */
    public VirtualMachine launchJVM(String mainClass, String classpath)
            throws IOException, IllegalConnectorArgumentsException, VMStartException {
        List<LaunchingConnector> list = Bootstrap.virtualMachineManager().launchingConnectors();
        LaunchingConnector launchingConnector = list.get(0);
        Map<String, Connector.Argument> subargs = launchingConnector.defaultArguments();
        subargs.get("options").setValue("-cp " + classpath);
        subargs.get("suspend").setValue("true");
        subargs.get("main").setValue(mainClass); // class
        return launchingConnector.launch(subargs);
    }
}
