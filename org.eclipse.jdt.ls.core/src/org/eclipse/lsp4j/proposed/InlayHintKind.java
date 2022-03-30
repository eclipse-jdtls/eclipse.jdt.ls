/*******************************************************************************
 * Copyright (c) 2022 Red Hat Inc. and others.
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

package org.eclipse.lsp4j.proposed;

public enum InlayHintKind {

	/**
	 * An inlay hint that for a type annotation.
	 */
	Type(1),

	/**
	 * An inlay hint that is for a parameter.
	 */
	Parameter(2);

	private final int value;

	InlayHintKind(int value) {
		this.value = value;
	}

	public int getValue() {
		return value;
	}

	public static InlayHintKind forValue(int value) {
		InlayHintKind[] allValues = InlayHintKind.values();
		if (value < 1 || value > allValues.length)
			throw new IllegalArgumentException("Illegal enum value: " + value);
		return allValues[value - 1];
	}
}
