/*******************************************************************************
 * Copyright (c) 2023 Red Hat Inc. and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *     Red Hat Inc. - initial API and implementation
 *******************************************************************************/
package org.eclipse.jdt.ls.core.internal.managers;

import org.eclipse.jdt.ls.core.internal.LogReader.LogEntry;

import com.google.gson.JsonObject;

public class TelemetryEvent {

	public static final String JAVA_PROJECT_BUILD = "java.workspace.initialized";
	public static final String IMPORT_PROJECT = "java.workspace.importProject";

	private String name;
	private Object properties;

	public TelemetryEvent(String name, Object properties) {
		this.name = name;
		this.properties = properties;
	}

	public String getName() {
		return name;
	}

	public Object getProperties() {
		return properties;
	}

	public static void addLogEntryProperties(JsonObject properties, LogEntry entry) {
		properties.addProperty("message", redact(entry.getMessage()));
		if (entry.getStack() != null) {
			properties.addProperty("exception", entry.getMessage() + '\n' + entry.getStack());
		}
	}

	private static String redact(String message) {
		// not sure why we're doing this, introduced in https://github.com/eclipse-jdtls/eclipse.jdt.ls/pull/2715
		if (message == null) {
			return null;
		}

		if (message.startsWith("Error occured while building workspace.")) {
			return "Error occured while building workspace.";
		}

		return message;
	}
}