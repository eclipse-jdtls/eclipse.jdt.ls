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
