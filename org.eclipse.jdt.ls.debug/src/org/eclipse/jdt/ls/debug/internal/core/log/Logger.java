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

package org.eclipse.jdt.ls.debug.internal.core.log;

import org.eclipse.jdt.ls.debug.internal.JavaDebuggerServerPlugin;

public class Logger {
    public static void logInfo(String message) {
        JavaDebuggerServerPlugin.logInfo(message);
    }

    public static void logException(String message, Exception e) {
        JavaDebuggerServerPlugin.logException(message, e);
    }

    public static void logError(String error) {
        JavaDebuggerServerPlugin.logError(error);
    }
    
}
