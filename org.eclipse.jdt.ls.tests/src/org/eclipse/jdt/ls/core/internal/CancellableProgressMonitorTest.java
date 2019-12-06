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
package org.eclipse.jdt.ls.core.internal;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.doThrow;

import java.util.concurrent.CancellationException;

import org.eclipse.lsp4j.jsonrpc.CancelChecker;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

/**
 * @author Fred Bricon
 */
@RunWith(MockitoJUnitRunner.class)
public class CancellableProgressMonitorTest {

	@Mock
	private CancelChecker checker;

	@Test
	public void testCancelled() {
		doThrow(CancellationException.class).when(checker).checkCanceled();
		assertTrue(new CancellableProgressMonitor(checker).isCanceled());
	}

	@Test
	public void testNotCancelled() {
		assertFalse(new CancellableProgressMonitor(null).isCanceled());
		assertFalse(new CancellableProgressMonitor(checker).isCanceled());
	}
}
