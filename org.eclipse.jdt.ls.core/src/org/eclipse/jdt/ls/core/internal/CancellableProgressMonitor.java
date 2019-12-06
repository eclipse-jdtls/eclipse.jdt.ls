/*******************************************************************************
 * Copyright (c) 2016-2017 Red Hat Inc. and others.
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
package org.eclipse.jdt.ls.core.internal;

import java.util.concurrent.CancellationException;

import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.lsp4j.jsonrpc.CancelChecker;

/**
 * Progress monitor wrapping a {@link CancelChecker}. Cancelling the
 * CancelChecker will also cancel this monitor.
 *
 * @author Gorkem Ercan
 */
public class CancellableProgressMonitor extends NullProgressMonitor {

	private final CancelChecker cancelChecker;

	private boolean done;

	public CancellableProgressMonitor(CancelChecker checker) {
		this.cancelChecker = checker;
	}

	@Override
	public boolean isCanceled() {
		if (super.isCanceled()) {
			return true;
		}
		if(cancelChecker != null ){
			try {
				cancelChecker.checkCanceled();
			} catch (CancellationException ce) {
				return true;
			}
		}
		return false;
	}

	@Override
	public void done() {
		super.done();
		done = true;
	}

	public boolean isDone() {
		return done;
	}
}
