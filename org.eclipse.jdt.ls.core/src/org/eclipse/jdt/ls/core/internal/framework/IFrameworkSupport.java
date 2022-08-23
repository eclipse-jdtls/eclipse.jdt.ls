/*******************************************************************************
 * Copyright (c) 2022 Microsoft Corporation and others.
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

package org.eclipse.jdt.ls.core.internal.framework;

import org.eclipse.core.runtime.IProgressMonitor;

/**
 * Interface class for framework support.
 */
public interface IFrameworkSupport {
    /**
	 * Tasks can be executed here after projects import finished.
	 * @param monitor progress monitor
	 */
    void onDidProjectsImported(IProgressMonitor monitor);
}
