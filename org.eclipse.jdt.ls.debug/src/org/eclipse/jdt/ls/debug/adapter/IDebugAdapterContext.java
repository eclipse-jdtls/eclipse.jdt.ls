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

import org.eclipse.jdt.ls.debug.IDebugSession;

public interface IDebugAdapterContext {

    /**
     * Get the debug session.
     * 
     * @return the debug session.
     */
    IDebugSession getDebugSession();

    /**
     * Send debug event synchronously.
     * 
     * @param event
     *            the debug event
     */
    void sendEvent(Events.DebugEvent event);

    /**
     * Send debug event asynchronously.
     * 
     * @param event
     *            the debug event
     */
    void sendEventAsync(Events.DebugEvent event);

    <T extends IProvider> T getProvider(Class<T> clazz);

    boolean isDebuggerLinesStartAt1();

    void setDebuggerLinesStartAt1(boolean debuggerLinesStartAt1);

    boolean isDebuggerPathsAreUri();

    void setDebuggerPathsAreUri(boolean debuggerPathsAreUri);

    boolean isClientLinesStartAt1();

    void setClientLinesStartAt1(boolean clientLinesStartAt1);

    boolean isClientPathsAreUri();

    void setClientPathsAreUri(boolean clientPathsAreUri);
}
