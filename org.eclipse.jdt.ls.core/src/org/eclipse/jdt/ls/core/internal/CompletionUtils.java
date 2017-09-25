/*******************************************************************************
 * Copyright (c) 2017 Red Hat Inc. and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Red Hat Inc. - initial API and implementation
 *******************************************************************************/
package org.eclipse.jdt.ls.core.internal;

/**
 * @author snjeza
 *
 */
public final class CompletionUtils {

	private static final String ESCAPE_DOLLAR = "\\\\\\$";
	private static final String DOLLAR = "\\$";

	private CompletionUtils() {
		// No instanciation
	}

	public static String sanitizeCompletion(String replace) {
		return replace == null ? null : replace.replaceAll(DOLLAR, ESCAPE_DOLLAR);
	}

}
