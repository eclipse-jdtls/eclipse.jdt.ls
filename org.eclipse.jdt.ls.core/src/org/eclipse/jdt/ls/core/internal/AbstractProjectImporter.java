/*******************************************************************************
 * Copyright (c) 2016-2017 Red Hat Inc. and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Red Hat Inc. - initial API and implementation
 *******************************************************************************/
package org.eclipse.jdt.ls.core.internal;

import java.io.File;
import java.util.Objects;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.OperationCanceledException;

public abstract class AbstractProjectImporter implements IProjectImporter {

	protected File rootFolder;

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
	public abstract void importToWorkspace(IProgressMonitor monitor) throws OperationCanceledException, CoreException;

	@Override
	public abstract void reset();

}
