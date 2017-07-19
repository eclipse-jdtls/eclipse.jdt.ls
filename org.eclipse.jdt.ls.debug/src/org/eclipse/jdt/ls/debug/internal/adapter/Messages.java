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

import com.google.gson.JsonObject;

/**
 * The response types defined by VSCode Debug Protocol.
 */
public class Messages {

    public static class DispatcherMessage {
        public int seq;
        public String type;

        public DispatcherMessage(String type) {
            this.type = type;
        }
    }

    public static class DispatcherRequest extends DispatcherMessage {
        public String command;
        public JsonObject arguments;

        /**
         * Constructor.
         */
        public DispatcherRequest(int id, String cmd, JsonObject arg) {
            super("request");
            this.seq = id;
            this.command = cmd;
            this.arguments = arg;
        }
    }

    public static class DispatcherResponse extends DispatcherMessage {
        public boolean success;
        public String message;
        public int request_seq;
        public String command;
        public Object body;

        public DispatcherResponse() {
            super("response");
        }

        /**
         * Constructor.
         */
        public DispatcherResponse(String msg) {
            super("response");
            this.success = false;
            this.message = msg;
        }

        /**
         * Constructor.
         */
        public DispatcherResponse(boolean succ, String message) {
            super("response");
            this.success = succ;
            this.message = message;
        }

        /**
         * Constructor.
         */
        public DispatcherResponse(DispatcherResponse m) {
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
        public DispatcherResponse(int rseq, String cmd) {
            super("response");
            this.request_seq = rseq;
            this.command = cmd;
        }
    }

    public static class DispatcherEvent extends DispatcherMessage {
        public String event;
        public Object body;

        public DispatcherEvent() {
            super("event");
        }

        /**
         * Constructor.
         */
        public DispatcherEvent(DispatcherEvent m) {
            super("event");
            this.seq = m.seq;
            this.event = m.event;
            this.body = m.body;
        }

        /**
         * Constructor.
         */
        public DispatcherEvent(String type, Object body) {
            super("event");
            this.event = type;
            this.body = body;
        }
    }
}
