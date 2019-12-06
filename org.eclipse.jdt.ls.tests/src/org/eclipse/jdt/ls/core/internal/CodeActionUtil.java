/*******************************************************************************
 * Copyright (c) 2019 Microsoft Corporation and others.
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

import java.util.Collections;

import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.lsp4j.CodeActionContext;
import org.eclipse.lsp4j.CodeActionParams;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.TextDocumentIdentifier;

public class CodeActionUtil {

	public static Range getRange(ICompilationUnit unit, String search) throws JavaModelException {
		return getRange(unit, search, search.length());
	}

	public static Range getRange(ICompilationUnit unit, String search, int length) throws JavaModelException {
		String str = unit.getSource();
		int start = str.lastIndexOf(search);
		return JDTUtils.toRange(unit, start, length);
	}

	public static CodeActionParams constructCodeActionParams(ICompilationUnit unit, String search) throws JavaModelException {
		final Range range = getRange(unit, search);
		return constructCodeActionParams(unit, range);

	}

	public static CodeActionParams constructCodeActionParams(ICompilationUnit unit, Range range) {
		CodeActionParams params = new CodeActionParams();
		params.setTextDocument(new TextDocumentIdentifier(JDTUtils.toURI(unit)));
		params.setRange(range);
		params.setContext(new CodeActionContext(Collections.emptyList()));
		return params;
	}
}
