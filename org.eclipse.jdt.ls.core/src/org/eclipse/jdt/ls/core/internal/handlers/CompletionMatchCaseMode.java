/*******************************************************************************
 * Copyright (c) 2022 Microsoft Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *     Microsoft Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.jdt.ls.core.internal.handlers;

/**
 * The mode for case matching when doing completion.
 */
public enum CompletionMatchCaseMode {
	/**
	 * Do not match case.
	 */
	OFF,
	/**
	 * Match case for the first letter.
	 */
	FIRSTLETTER;

	public static CompletionMatchCaseMode fromString(String value, CompletionMatchCaseMode defaultMode) {
		if (value != null) {
			String val = value.toUpperCase();
			try {
				return valueOf(val);
			} catch(Exception e) {
				// fall back to default severity
			}
		}
		return defaultMode;
	}
}
