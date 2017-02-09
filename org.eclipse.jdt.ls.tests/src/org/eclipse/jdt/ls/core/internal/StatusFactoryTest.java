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

import static org.junit.Assert.assertEquals;

import org.eclipse.core.runtime.IStatus;
import org.eclipse.jdt.ls.core.internal.JavaLanguageServerPlugin;
import org.eclipse.jdt.ls.core.internal.StatusFactory;
import org.junit.Test;

public class StatusFactoryTest {

	@Test
	public void testNewErrorStatusString() throws Exception {
		IStatus error = StatusFactory.newErrorStatus("foo");
		assertEquals("foo", error.getMessage());
		assertEquals(IStatus.ERROR, error.getSeverity());
		assertEquals(JavaLanguageServerPlugin.PLUGIN_ID, error.getPlugin());
	}

	@Test
	public void testNewErrorStatusStringThrowable() throws Exception {
		Exception e = new Exception();
		IStatus error = StatusFactory.newErrorStatus("foo", e);
		assertEquals("foo", error.getMessage());
		assertEquals(e, error.getException());
	}

}
