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

import com.google.common.base.CaseFormat;

/**
 * How the arguments fill during completion.
 */
public enum CompletionGuessMethodArgumentsMode {
	/**
	 * Do not insert argument names during completion.
	 */
	OFF,
	/**
	 * The parameter names will be inserted during completion.
	 */
	INSERT_PARAMETER_NAMES,
	/**
	 * The best guessed arguments will be inserted during completion according to the code context.
	 */
	INSERT_BEST_GUESSED_ARGUMENTS;

	public static CompletionGuessMethodArgumentsMode fromString(String value, CompletionGuessMethodArgumentsMode defaultMode) {
		if (value != null) {
			String val = CaseFormat.UPPER_CAMEL.to(CaseFormat.UPPER_UNDERSCORE, value);
			try {
				return valueOf(val);
			} catch(Exception e) {
				// fall back to default severity
			}
		}
		return defaultMode;
	}
}
