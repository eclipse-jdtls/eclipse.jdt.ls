/*******************************************************************************
 * Copyright (c) 2017 David Gileadi and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *     David Gileadi - initial API and implementation
 *******************************************************************************/
package org.eclipse.jdt.ls.core.internal;

import java.net.URI;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.jdt.core.IClassFile;
import org.eclipse.jdt.ls.core.internal.preferences.Preferences;

public class FakeContentProvider implements IDecompiler {

	public static Preferences preferences;
	public static Object returnValue;

	@Override
	public void setPreferences(Preferences preferences) {
		FakeContentProvider.preferences = preferences;
	}

	@Override
	public String getContent(URI uri, IProgressMonitor monitor) throws CoreException {
		if (returnValue instanceof Throwable) {
			throw new CoreException(new Status(IStatus.ERROR, "test.plugin", "FakeContentProvider error", (Throwable) returnValue));
		} else if (returnValue instanceof IProgressMonitor) {
			monitor.setCanceled(true);
			return "Canceled";
		}
		return (String) returnValue;
	}

	@Override
	public String getSource(IClassFile classFile, IProgressMonitor monitor) throws CoreException {
		return getContent(null, monitor);
	}
}
