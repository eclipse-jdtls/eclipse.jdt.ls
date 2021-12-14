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

package org.eclipse.jdt.ls.core.internal;

/**
 * A class used to calculate the cumulative moving average.
 * @see <a href="https://en.wikipedia.org/wiki/Moving_average#Cumulative_moving_average">Definition from Wikipedia</a>.
 */
public class MovingAverage {
	/**
	 * The average value
	 */
	public long value;

	private long n = 1;

	public MovingAverage() {
		this(0);
	}

	/**
	 * Initialize the moving average with a initial value
	 * @param initValue The initial value of the moving average
	 */
	public MovingAverage(long initValue) {
		this.value = initValue;
	}

	/**
	 * Update the moving average value
	 * @param value A new value used to update the moving average
	 * @return The <code>MovingAverage</code> instance
	 */
	public MovingAverage update(long value) {
		this.value = this.value + (value - this.value) / this.n;
		this.n += 1;
		return this;
	}
}
