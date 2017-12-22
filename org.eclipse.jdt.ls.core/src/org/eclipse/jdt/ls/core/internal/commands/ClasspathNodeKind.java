/*******************************************************************************
 * Copyright (c) 2017 Microsoft Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Microsoft Corporation - initial API and implementation
 *******************************************************************************/

package org.eclipse.jdt.ls.core.internal.commands;

public enum ClasspathNodeKind {

	PROJECT(1),

	CONTAINER(2),

	JAR(3),

	PACKAGE(4),

	CLASSFILE(5);

	private final int value;

	ClasspathNodeKind(int value) {
		this.value = value;
	}

	public int getValue() {
		return value;
	}

	public static ClasspathNodeKind forValue(int value) {
		ClasspathNodeKind[] allValues = ClasspathNodeKind.values();
		if (value < 1 || value > allValues.length) {
			throw new IllegalArgumentException("Illegal enum value: " + value);
		}
		return allValues[value - 1];
	}
}
