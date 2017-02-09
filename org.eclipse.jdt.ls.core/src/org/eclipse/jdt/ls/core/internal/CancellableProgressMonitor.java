/*******************************************************************************
 * Copyright (c) 2016-2017 Red Hat Inc. and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Red Hat Inc. - initial API and implementation
 *******************************************************************************/
package org.eclipse.jdt.ls.core.internal;

import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.lsp4j.jsonrpc.CancelChecker;

/**
 *
 * @author Gorkem Ercan
 */
public class CancellableProgressMonitor extends NullProgressMonitor {

	private final CancelChecker cancelChecker;
	/**
	 *
	 */
	public CancellableProgressMonitor(CancelChecker checker) {
		this.cancelChecker = checker;
	}

	/* (non-Javadoc)
	 * @see org.eclipse.core.runtime.NullProgressMonitor#isCanceled()
	 */
	@Override
	public boolean isCanceled() {
		if(cancelChecker != null ){
			cancelChecker.checkCanceled();
		}
		return false;
	}
}
