/*******************************************************************************
 * Copyright (c) 2017 Red Hat Inc. and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Red Hat Inc. - initial API and implementation
 *******************************************************************************/
package org.eclipse.jdt.ls.core.internal;

import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;

public class StatusUtils {

	public static IStatus createError(int code, Throwable throwable) {
		String message = throwable.getMessage();
		if (message == null) {
			message = throwable.getClass().getName();
		}
		return new Status(IStatus.ERROR, JavaLanguageServerPlugin.PLUGIN_ID, code, message, throwable);
	}

	public static IStatus createError(int code, String message, Throwable throwable) {
		return new Status(IStatus.ERROR, JavaLanguageServerPlugin.PLUGIN_ID, code, message, throwable);
	}

	public static IStatus createWarning(int code, String message, Throwable throwable) {
		return new Status(IStatus.WARNING, JavaLanguageServerPlugin.PLUGIN_ID, code, message, throwable);
	}

	public static IStatus createInfo(int code, String message, Throwable throwable) {
		return new Status(IStatus.INFO, JavaLanguageServerPlugin.PLUGIN_ID, code, message, throwable);
	}
}
