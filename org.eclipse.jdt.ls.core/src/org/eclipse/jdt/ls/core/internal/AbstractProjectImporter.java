/*******************************************************************************
 * Copyright (c) 2016-2017 Red Hat Inc. and others.
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
package org.eclipse.jdt.ls.core.internal;

import java.io.File;
import java.util.Collection;
import java.util.Objects;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.OperationCanceledException;
import org.eclipse.jdt.ls.core.internal.preferences.Preferences;

public abstract class AbstractProjectImporter implements IProjectImporter {

	protected File rootFolder;
	protected Collection<java.nio.file.Path> directories;

	@Override
	public void initialize(File rootFolder) {
		if (!Objects.equals(this.rootFolder, rootFolder)) {
			reset();
		}
		this.rootFolder = rootFolder;
	}

	@Override
	public abstract boolean applies(IProgressMonitor monitor) throws OperationCanceledException, CoreException;

	@Override
	public boolean isResolved(File folder) throws OperationCanceledException, CoreException {
		return directories != null && directories.contains(folder.toPath());
	};

	@Override
	public abstract void importToWorkspace(IProgressMonitor monitor) throws OperationCanceledException, CoreException;

	@Override
	public abstract void reset();

	protected static Preferences getPreferences() {
		return (JavaLanguageServerPlugin.getPreferencesManager() == null || JavaLanguageServerPlugin.getPreferencesManager().getPreferences() == null) ? new Preferences() : JavaLanguageServerPlugin.getPreferencesManager().getPreferences();
	}

}
