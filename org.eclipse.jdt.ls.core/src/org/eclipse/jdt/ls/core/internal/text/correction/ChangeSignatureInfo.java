/*******************************************************************************
 * Copyright (c) 2024 Red Hat Inc. and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *     Red Hat Inc. - initial API and implementation
 *******************************************************************************/
package org.eclipse.jdt.ls.core.internal.text.correction;

import org.eclipse.jdt.ls.core.internal.handlers.ChangeSignatureHandler.MethodException;
import org.eclipse.jdt.ls.core.internal.handlers.ChangeSignatureHandler.MethodParameter;

public class ChangeSignatureInfo {

	public String methodIdentifier;
	public String modifier;
	public String returnType;
	public String methodName;
	public MethodParameter[] parameters;
	public MethodException[] exceptions;
	public String errorMessage;

	public ChangeSignatureInfo(String methodIdentifier, String modifier, String returnType, String methodName, MethodParameter[] parameters, MethodException[] exceptions) {
		this.methodIdentifier = methodIdentifier;
		this.modifier = modifier;
		this.returnType = returnType;
		this.methodName = methodName;
		this.parameters = parameters;
		this.exceptions = exceptions;
	}

	public ChangeSignatureInfo(String errorMessage) {
		this.errorMessage = errorMessage;
	}
}