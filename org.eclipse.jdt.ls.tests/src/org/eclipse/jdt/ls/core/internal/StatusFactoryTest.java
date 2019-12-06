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

import static org.junit.Assert.assertEquals;

import org.eclipse.core.runtime.IStatus;
import org.junit.Test;

public class StatusFactoryTest {

	@Test
	public void testNewErrorStatusString() throws Exception {
		IStatus error = StatusFactory.newErrorStatus("foo");
		assertEquals("foo", error.getMessage());
		assertEquals(IStatus.ERROR, error.getSeverity());
		assertEquals(IConstants.PLUGIN_ID, error.getPlugin());
	}

	@Test
	public void testNewErrorStatusStringThrowable() throws Exception {
		Exception e = new Exception();
		IStatus error = StatusFactory.newErrorStatus("foo", e);
		assertEquals("foo", error.getMessage());
		assertEquals(e, error.getException());
	}

}
