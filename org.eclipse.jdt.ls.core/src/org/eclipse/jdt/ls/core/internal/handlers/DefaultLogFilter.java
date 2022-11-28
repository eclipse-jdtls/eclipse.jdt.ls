/*******************************************************************************
 * Copyright (c) 2017 Red Hat Inc. and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *     Red Hat Inc. - initial API and implementation
 *******************************************************************************/
package org.eclipse.jdt.ls.core.internal.handlers;

import java.util.function.Predicate;

import org.eclipse.core.runtime.IStatus;

/**
 * Default Log filter. Excludes the following messages from being logged to the
 * client:
 * <ul>
 * <li>"Missing resource filter type"</li>
 * </ul>
 *
 * @author Fred Bricon
 *
 */
public class DefaultLogFilter implements Predicate<IStatus> {

	private static final String MISSING_RESOURCE_FILTER_TYPE = "Missing resource filter type";

	@Override
	public boolean test(IStatus status) {
		return accepts(getMessage(status));
	}

	private boolean accepts(String message) {
		// Checking for status messages is a bit weak, since it could still change in theory (although highly unlikely)
		// and might fail in case of I18n'ed messages
		if (message == null || message.startsWith(MISSING_RESOURCE_FILTER_TYPE)) {
			return false;
		}
		return true;
	}

	private String getMessage(IStatus status) {
		return status == null ? null : status.getMessage();
	}


}
