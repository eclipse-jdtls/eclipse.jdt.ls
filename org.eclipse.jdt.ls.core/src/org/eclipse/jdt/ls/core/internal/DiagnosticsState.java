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

import java.util.HashMap;
import java.util.Map;

public class DiagnosticsState {

	private ErrorLevel globalErrorLevel = ErrorLevel.SYNTAX_ERROR;

	private Map<String, ErrorLevel> customizedErrorLevels = new HashMap<>();

	public boolean isOnlySyntaxReported(String uri) {
		return customizedErrorLevels.getOrDefault(uri, globalErrorLevel) == ErrorLevel.SYNTAX_ERROR;
	}

	public void setErrorLevel(String uri, boolean syntaxOnly) {
		customizedErrorLevels.put(uri, syntaxOnly ? ErrorLevel.SYNTAX_ERROR : ErrorLevel.COMPILATION_ERROR);
	}

	public ErrorLevel getGlobalErrorLevel() {
		return globalErrorLevel;
	}

	public void setGlobalErrorLevel(boolean syntaxOnly) {
		setGlobalErrorLevel(syntaxOnly ? ErrorLevel.SYNTAX_ERROR : ErrorLevel.COMPILATION_ERROR);
	}

	public void setGlobalErrorLevel(ErrorLevel level) {
		globalErrorLevel = level == null ? ErrorLevel.SYNTAX_ERROR : level;
		customizedErrorLevels.clear();
	}

	public enum ErrorLevel {
		SYNTAX_ERROR,
		COMPILATION_ERROR
	}
}
