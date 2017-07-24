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

import com.sun.jdi.VirtualMachine;

import io.reactivex.Observable;

public interface IEventHub extends AutoCloseable {
    void start(VirtualMachine vm);

    Observable<DebugEvent> events();

    Observable<DebugEvent> breakpointEvents();

    Observable<DebugEvent> threadEvents();

    Observable<DebugEvent> exceptionEvents();

    Observable<DebugEvent> stepEvents();

    Observable<DebugEvent> vmEvents();
}
