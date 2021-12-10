/*******************************************************************************
 * Copyright (c) 2021 Microsoft Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *     Microsoft Corporation - initial API and implementation
 *******************************************************************************/

package org.eclipse.jdt.ls.core.internal.handlers;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class MovingAverageTest {

	@Test
	public void testUpdate() {
		MovingAverage average = new MovingAverage();

		// initialize to 400 at first
		assertEquals(400, average.value);

		average.update(200);
		// the first input value takes over the initial value
		assertEquals(200, average.value);

		average.update(100);
		// (200 + 100) / 2
		assertEquals(150, average.value);
	}
}
