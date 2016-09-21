/*******************************************************************************
 * Copyright (c) 2016 Red Hat Inc. and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Red Hat Inc. - initial API and implementation
 *******************************************************************************/
package org.jboss.tools.vscode.internal.ipc;

/**
 * An interface passed to {@link RequestHandler}
 * to notify cancellation.
 *
 * @author Gorkem Ercan
 *
 */
public interface CancelMonitor {

	/**
	 * Returns the state of this monitor.
	 *
	 * @return
	 */
	boolean cancelled();
	/**
	 * Called when a cancel notification is receieved.
	 */
	void onCancel();

}
