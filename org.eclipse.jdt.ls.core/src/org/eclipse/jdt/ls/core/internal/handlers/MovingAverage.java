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

public class MovingAverage {
	public long n = 1;
	public long value;

	public MovingAverage() {
		this(400);
	}

	public MovingAverage(long value) {
		this.value = value;
	}

	public MovingAverage update(long value) {
		this.value = this.value + (value - this.value) / this.n;
		this.n += 1;
		return this;
	}
}
