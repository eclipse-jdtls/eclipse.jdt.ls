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

package org.eclipse.jdt.ls.debug.internal;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;

import org.eclipse.jdt.ls.core.debug.IDebugServer;
import org.eclipse.jdt.ls.debug.internal.adapter.DebugSession;
import org.eclipse.jdt.ls.debug.internal.adapter.DispatcherProtocol;
import org.eclipse.jdt.ls.debug.internal.adapter.DispatcherProtocol.IResponder;
import org.eclipse.jdt.ls.debug.internal.adapter.Events.DebugEvent;
import org.eclipse.jdt.ls.debug.internal.adapter.JsonUtils;
import org.eclipse.jdt.ls.debug.internal.adapter.Results.DebugResult;
import org.eclipse.jdt.ls.debug.internal.adapter.Results.ErrorResponseBody;
import org.eclipse.jdt.ls.debug.internal.adapter.Types.Message;
import org.eclipse.jdt.ls.debug.internal.core.log.Logger;

import com.google.gson.JsonObject;

public class JavaDebugServer implements IDebugServer {
    private ServerSocket serverSocket = null;
    private Socket connection = null;
    private DispatcherProtocol dispatcher = null;

    /**
     * Constructs a JavaDebugServer instance which will launch a ServerSocket to 
     * listen for incoming socket connection.
     */
    public JavaDebugServer() {
        try {
            this.serverSocket = new ServerSocket(0, 1);
        } catch (IOException e) {
            Logger.logException("Create ServerSocket exception", e);
        }
    }

    @Override
    public int getPort() {
        if (this.serverSocket != null) {
            return this.serverSocket.getLocalPort();
        }
        return -1;
    }

    @Override
    public void start() {
        if (this.serverSocket != null) {
            // Execute eventLoop in a new thread.
            new Thread(new Runnable() {

                @Override
                public void run() {
                    int serverPort = -1;
                    try {
                        // It's blocking here to waiting for incoming socket connection.
                        connection = serverSocket.accept();
                        serverPort = serverSocket.getLocalPort();
                        closeServerSocket(); // Stop listening for further connections.
                        Logger.logInfo("Start debugserver on socket port " + serverPort);
                        BufferedReader in = new BufferedReader(new InputStreamReader(connection.getInputStream()));
                        PrintWriter out = new PrintWriter(connection.getOutputStream(), true);

                        dispatcher = new DispatcherProtocol(in, out);
                        // Hanging here because this eventLoop is a while-loop.
                        dispatcher.eventLoop(new ProtocolHandler(dispatcher));
                    } catch (IOException e1) {
                        Logger.logException("Setup socket connection exception", e1);
                    } finally {
                        closeServerSocket();
                        closeClientSocket();
                        Logger.logInfo("Close debugserver socket port " + serverPort);
                    }
                }

            }).start();
        }
    }

    @Override
    public void stop() {
        if (dispatcher != null) {
            // Exit event dispatcher loop and the DebugServer thread will complete automatically.
            dispatcher.stop();
        }
    }

    private void closeServerSocket() {
        if (serverSocket != null) {
            try {
                serverSocket.close();
            } catch (IOException e) {
                Logger.logException("Close ServerSocket exception", e);
            }
        }
        serverSocket = null;
    }

    private void closeClientSocket() {
        if (connection != null) {
            try {
                connection.close();
            } catch (IOException e) {
                Logger.logException("Close client socket exception", e);
            }
        }
        connection = null;
    }

    class ProtocolHandler implements DispatcherProtocol.IHandler {
        private DispatcherProtocol dispatcher = null;
        private DebugSession debugSession = null;
        
        public ProtocolHandler(DispatcherProtocol dispatcher) {
            this.dispatcher = dispatcher;
        }
        
        @Override
        public void run(String command, JsonObject arguments, IResponder responder) {
            if (arguments == null) {
                arguments = new JsonObject();
            }

            try {
                if (command.equals("initialize")) {
                    String adapterID = JsonUtils.getString(arguments, "adapterID", "");
                    debugSession = new DebugSession(true, false, responder);
                    if (debugSession == null) {
                        responder.setBody(new ErrorResponseBody(new Message(1103,
                                "initialize: can't create debug session for adapter '{_id}'",
                                JsonUtils.fromJson("{ _id: " + adapterID + "}",
                                        JsonObject.class))));
                    }
                }

                if (debugSession != null) {
                    DebugResult dr = debugSession.dispatch(command, arguments);
                    if (dr != null) {
                        responder.setBody(dr.body);

                        if (dr.events != null) {
                            for (DebugEvent e : dr.events) {
                                responder.addEvent(e.type, e);
                            }
                        }
                    }
                }

                if (command.equals("disconnect")) {
                    dispatcher.stop();
                    debugSession = null;
                }
            } catch (Exception e) {
                Logger.logException("Dispatch debug protocol error", e);
            }
        }
    }

}
