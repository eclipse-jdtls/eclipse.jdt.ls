/*******************************************************************************
 * Copyright (c) 2016 Red Hat Inc. and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Red Hat Inc. - initial API and implementation
 *******************************************************************************/
package org.jboss.tools.vscode.ipc;

public enum MessageType {
	/**
	 * An error message
	 */
	Error (1),
	/**
	 * A warning message
	 */
	Warning (2),
	/**
	 * An info message
	 */
	Info (3),
	/**
	 * A basic logging message
	 */
	Log (4);
	
	int type;
	private MessageType(int type) {
		this.type = type;
	}
	
	public int getType() {
		return type;
	}
	
}
