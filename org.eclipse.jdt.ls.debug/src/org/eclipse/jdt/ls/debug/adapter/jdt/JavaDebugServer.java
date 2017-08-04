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

package org.eclipse.jdt.ls.debug.adapter.jdt;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;

import org.eclipse.jdt.ls.core.debug.IDebugServer;
import org.eclipse.jdt.ls.debug.adapter.ProtocolServer;
import org.eclipse.jdt.ls.debug.internal.Logger;

public class JavaDebugServer implements IDebugServer {
    private ServerSocket serverSocket = null;
    private Socket connection = null;
    private ProtocolServer protocolServer = null;

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

                        protocolServer = new ProtocolServer(in, out, new JdtProviderContext());
                        // protocol server will dispatch request and send response in a while-loop.
                        protocolServer.start();
                    } catch (IOException e1) {
                        Logger.logException("Setup socket connection exception", e1);
                    } finally {
                        closeServerSocket();
                        closeConnection();
                        Logger.logInfo("Close debugserver socket port " + serverPort);
                    }
                }

            }, "Debug Protocol Server").start();
        }
    }

    @Override
    public void stop() {
        if (protocolServer != null) {
            protocolServer.stop();
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

    private void closeConnection() {
        if (connection != null) {
            try {
                connection.close();
            } catch (IOException e) {
                Logger.logException("Close client socket exception", e);
            }
        }
        connection = null;
    }
}
