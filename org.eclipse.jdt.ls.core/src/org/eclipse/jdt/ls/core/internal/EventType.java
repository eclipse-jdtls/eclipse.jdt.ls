/*******************************************************************************
 * Copyright (c) 2020 Microsoft Corporation and others.
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

public enum EventType {
    /**
	 * classpath updated event.
	 */
	ClasspathUpdated(100),
	
	/**
	 * projects imported event.
	 */
	ProjectsImported(200);
	
	private final int value;
	
	EventType(int value) {
		this.value = value;
	}
	
	public int getValue() {
		return value;
	}
}
