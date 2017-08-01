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

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.eclipse.jdt.ls.debug.internal.Logger;

public class ProtocolServer {
    private static final int BUFFER_SIZE = 4096;
    private static final String TWO_CRLF = "\r\n\r\n";
    private static final Pattern CONTENT_LENGTH_MATCHER = Pattern.compile("Content-Length: (\\d+)");

    private Reader reader;
    private Writer writer;

    private CharBuffer rawData;
    private boolean terminateSession = false;
    private int bodyLength = -1;
    private int sequenceNumber = 1;

    private boolean isDispatchingData;
    private ConcurrentLinkedQueue<Messages.Event> eventQueue;

    private IDebugAdapter debugAdapter;

    /**
     * Constructs a protocol server instance based on the given reader and writer.
     * @param reader
     *              the input reader
     * @param writer
     *              the output writer
     */
    public ProtocolServer(Reader reader, Writer writer) {
        this.reader = reader;
        this.writer = writer;
        this.bodyLength = -1;
        this.sequenceNumber = 1;
        this.rawData = new CharBuffer();
        this.eventQueue = new ConcurrentLinkedQueue<>();
        this.debugAdapter = new DebugAdapter(debugEvent -> {
            this.sendEventLater(debugEvent.type, debugEvent);
        });
    }

    /**
     * A while-loop to parse input data and send output data constantly.
     */
    public void start() {
        char[] buffer = new char[BUFFER_SIZE];
        try {
            while (!this.terminateSession) {
                int read = this.reader.read(buffer, 0, BUFFER_SIZE);
                if (read == -1) {
                    break;
                }

                this.rawData.append(buffer, read);
                this.processData();
            }
        } catch (IOException e) {
            Logger.logException("Read data from io exception", e);
        }
    }

    /**
     * Sets terminateSession flag to true. And the dispatcher loop will be terminated after current dispatching operation finishes.
     */
    public void stop() {
        this.terminateSession = true;
    }

    /**
     * If the the dispatcher is idle, then send the event immediately.
     * Else add the new event to an eventQueue first and send them when dispatcher becomes idle again.
     * @param eventType
     *              event type
     * @param body 
     *              event content
     */
    private void sendEventLater(String eventType, Object body) {
        synchronized (this) {
            if (this.isDispatchingData) {
                this.eventQueue.offer(new Messages.Event(eventType, body));
            } else {
                sendMessage(new Messages.Event(eventType, body));
            }
        }
    }

    private void sendMessage(Messages.ProtocolMessage message) {
        message.seq = this.sequenceNumber++;

        String jsonMessage = JsonUtils.toJson(message);
        char[] jsonBytes = jsonMessage.toCharArray();

        String header = String.format("Content-Length: %d%s", jsonBytes.length, TWO_CRLF);
        char[] headerBytes = header.toCharArray();

        char[] data = new char[headerBytes.length + jsonBytes.length];
        System.arraycopy(headerBytes, 0, data, 0, headerBytes.length);
        System.arraycopy(jsonBytes, 0, data, headerBytes.length, jsonBytes.length);

        try {
            Logger.logInfo("\n[[RESPONSE]]\n" + new String(data));
            this.writer.write(data, 0, data.length);
            this.writer.flush();
        } catch (IOException e) {
            Logger.logException("Write data to io exception", e);
        }
    }

    private void processData() {
        while (true) {
            if (this.bodyLength >= 0) {
                if (this.rawData.length() >= this.bodyLength) {
                    char[] buf = this.rawData.removeFirst(this.bodyLength);
                    this.bodyLength = -1;
                    dispatchRequest(new String(buf));
                    continue;
                }
            } else {
                String body = this.rawData.getString();
                int idx = body.indexOf(TWO_CRLF);
                if (idx != -1) {
                    Matcher matcher = CONTENT_LENGTH_MATCHER.matcher(body);
                    if (matcher.find()) {
                        this.bodyLength = Integer.parseInt(matcher.group(1));
                        this.rawData.removeFirst(idx + TWO_CRLF.length());
                        continue;
                    }
                }
            }
            break;
        }
    }

    private void dispatchRequest(String request) {
        try {
            Logger.logInfo("\n[REQUEST]\n" + request);
            Messages.Request message = JsonUtils.fromJson(request, Messages.Request.class);
            if (message.type.equals("request")) {
                synchronized (this) {
                    this.isDispatchingData = true;
                }

                try {
                    Messages.Response response = this.debugAdapter.dispatchRequest(message);
                    if (message.command.equals("disconnect")) {
                        this.stop();
                        this.debugAdapter = null;
                    }
                    sendMessage(response);
                } catch (Exception e) {
                    Logger.logException("Dispatch debug protocol error", e);
                }
            }
        } finally {
            synchronized (this) {
                this.isDispatchingData = false;
            }

            while (this.eventQueue.peek() != null) {
                sendMessage(this.eventQueue.poll());
            }
        }
    }

    class CharBuffer {
        private char[] buffer;

        public CharBuffer() {
            this.buffer = new char[0];
        }

        public int length() {
            return this.buffer.length;
        }

        public String getString() {
            return new String(this.buffer);
        }

        public void append(char[] b, int length) {
            char[] newBuffer = new char[this.buffer.length + length];
            System.arraycopy(buffer, 0, newBuffer, 0, this.buffer.length);
            System.arraycopy(b, 0, newBuffer, this.buffer.length, length);
            this.buffer = newBuffer;
        }

        public char[] removeFirst(int n) {
            char[] b = new char[n];
            System.arraycopy(this.buffer, 0, b, 0, n);
            char[] newBuffer = new char[this.buffer.length - n];
            System.arraycopy(this.buffer, n, newBuffer, 0, this.buffer.length - n);
            this.buffer = newBuffer;
            return b;
        }
    }
}
