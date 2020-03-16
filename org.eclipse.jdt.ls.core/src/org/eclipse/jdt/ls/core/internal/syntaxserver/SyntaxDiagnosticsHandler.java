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

package org.eclipse.jdt.ls.core.internal.syntaxserver;

import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.ls.core.internal.JavaClientConnection;
import org.eclipse.jdt.ls.core.internal.handlers.BaseDiagnosticsHandler;

public class SyntaxDiagnosticsHandler extends BaseDiagnosticsHandler {

	public SyntaxDiagnosticsHandler(JavaClientConnection conn, ICompilationUnit cu) {
		super(conn, cu);
	}

	@Override
	public boolean isSyntaxMode() {
		return true;
	}
}
