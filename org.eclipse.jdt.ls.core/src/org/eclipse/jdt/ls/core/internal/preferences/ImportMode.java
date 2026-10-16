/*******************************************************************************
 * Copyright (c) 2026 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *     Angelo ZERR - initial API and implementation
 *******************************************************************************/
package org.eclipse.jdt.ls.core.internal.preferences;

/**
 * Controls how projects are imported into the workspace.
 *
 * <ul>
 *   <li>{@link #FULL} — imports all projects eagerly at startup (default).</li>
 *   <li>{@link #ON_DEMAND} — imports projects lazily when a file is opened.</li>
 * </ul>
 */
public enum ImportMode {

	/**
	 * Traditional mode: all projects are discovered and imported at startup.
	 * Safe, predictable, and well-tested — but slow for large workspaces
	 * (e.g. Quarkus with 1300+ modules).
	 */
	FULL("full"),

	/**
	 * On-demand mode: projects are imported lazily when the user first opens
	 * a file belonging to that module. This drastically reduces startup time
	 * and memory usage for large multi-module workspaces.
	 */
	ON_DEMAND("ondemand");

	private final String value;

	ImportMode(String value) {
		this.value = value;
	}

	/**
	 * Returns the string value used in settings (e.g. {@code "full"}, {@code "ondemand"}).
	 */
	public String getValue() {
		return value;
	}

	/**
	 * Parses a string setting into an {@link ImportMode}.
	 * Defaults to {@link #FULL} if the value is {@code null} or unrecognized.
	 *
	 * @param value the setting value (e.g. {@code "ondemand"})
	 * @return the corresponding {@link ImportMode}
	 */
	public static ImportMode fromString(String value) {
		return fromString(value, FULL);
	}

	/**
	 * Parses a string setting into an {@link ImportMode}.
	 * Returns {@code defaultMode} if the value is {@code null} or unrecognized.
	 *
	 * @param value the setting value (e.g. {@code "ondemand"})
	 * @param defaultMode the fallback when the value is not recognized
	 * @return the corresponding {@link ImportMode}
	 */
	public static ImportMode fromString(String value, ImportMode defaultMode) {
		if (value != null) {
			for (ImportMode mode : values()) {
				if (mode.value.equalsIgnoreCase(value)) {
					return mode;
				}
			}
		}
		return defaultMode;
	}
}
