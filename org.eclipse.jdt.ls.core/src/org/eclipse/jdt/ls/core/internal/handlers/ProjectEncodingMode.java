/*******************************************************************************
 * Copyright (c) 2022 Red Hat Inc. and others.
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
package org.eclipse.jdt.ls.core.internal.handlers;

/**
 *
 * @author snjeza
 *
 */
public enum ProjectEncodingMode {
	IGNORE, /* Ignore project encoding settings */
	WARNING, /* Show warning if a project has no explicit encoding set */
	SETDEFAULT; /* Set the default workspace encoding settings */

	public static ProjectEncodingMode fromString(String value, ProjectEncodingMode defaultMode) {
		if (value != null) {
			String val = value.toUpperCase();
			try {
				return valueOf(val);
			} catch (Exception e) {
				// fall back to default mode
			}
		}
		return defaultMode;
	}
}
