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

public enum InlayHintsParameterMode {
	NONE,     /* do not show inlay hints */
	LITERALS, /* only show inlay hints for literal arguments */
	ALL;      /* show inlay hints for both literal and non-literal arguments */

	public static InlayHintsParameterMode fromString(String value, InlayHintsParameterMode defaultMode) {
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
