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

import org.eclipse.core.runtime.IStatus;

/**
 * Filter checking whether an {@link IStatus} should be logged to the client.
 *
 * @author Fred Bricon
 *
 */
public interface ILogFilter {

	/**
	 * Checks whether an {@link IStatus} should be logged to the client.
	 *
	 * @param status
	 *            the status to check
	 * @return <code>true</code> if the status can be logged to the client.
	 */
	default boolean accepts(IStatus status) {
		return true;
	};

}
