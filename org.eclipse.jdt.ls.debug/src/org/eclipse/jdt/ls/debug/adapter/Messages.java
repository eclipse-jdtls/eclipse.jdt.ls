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

import com.google.gson.JsonObject;

/**
 * The response types defined by VSCode Debug Protocol.
 */
public class Messages {

    public static class ProtocolMessage {
        public int seq;
        public String type;

        public ProtocolMessage(String type) {
            this.type = type;
        }
    }

    public static class Request extends ProtocolMessage {
        public String command;
        public JsonObject arguments;

        /**
         * Constructor.
         */
        public Request(int id, String cmd, JsonObject arg) {
            super("request");
            this.seq = id;
            this.command = cmd;
            this.arguments = arg;
        }
    }

    public static class Response extends ProtocolMessage {
        public boolean success;
        public String message;
        public int request_seq;
        public String command;
        public Object body;

        public Response() {
            super("response");
        }

        /**
         * Constructor.
         */
        public Response(String msg) {
            super("response");
            this.success = false;
            this.message = msg;
        }

        /**
         * Constructor.
         */
        public Response(boolean succ, String message) {
            super("response");
            this.success = succ;
            this.message = message;
        }

        /**
         * Constructor.
         */
        public Response(Response m) {
            super("response");
            this.seq = m.seq;
            this.success = m.success;
            this.message = m.message;
            this.request_seq = m.request_seq;
            this.command = m.command;
            this.body = m.body;
        }

        /**
         * Constructor.
         */
        public Response(int rseq, String cmd) {
            super("response");
            this.request_seq = rseq;
            this.command = cmd;
        }

        public Response(int rseq, String cmd, boolean succ) {
            this(rseq, cmd);
            this.success = succ;
        }
    }

    public static class Event extends ProtocolMessage {
        public String event;
        public Object body;

        public Event() {
            super("event");
        }

        /**
         * Constructor.
         */
        public Event(Event m) {
            super("event");
            this.seq = m.seq;
            this.event = m.event;
            this.body = m.body;
        }

        /**
         * Constructor.
         */
        public Event(String type, Object body) {
            super("event");
            this.event = type;
            this.body = body;
        }
    }
}
