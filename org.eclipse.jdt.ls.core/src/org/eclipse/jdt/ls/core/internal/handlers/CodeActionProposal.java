/*******************************************************************************
* Copyright (c) 2021 Microsoft Corporation and others.
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

package org.eclipse.jdt.ls.core.internal.handlers;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.OperationCanceledException;
import org.eclipse.lsp4j.WorkspaceEdit;

public interface CodeActionProposal {
	public WorkspaceEdit resolveEdit(IProgressMonitor monitor) throws OperationCanceledException, CoreException;
}
