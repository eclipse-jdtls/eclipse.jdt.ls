/*******************************************************************************
 * Copyright (c) 2019 Microsoft Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
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
		String str = unit.getSource();
		int start = str.lastIndexOf(search);
		return JDTUtils.toRange(unit, start, search.length());
	}

	public static CodeActionParams constructCodeActionParams(ICompilationUnit unit, String search) throws JavaModelException {
		CodeActionParams params = new CodeActionParams();
		params.setTextDocument(new TextDocumentIdentifier(JDTUtils.toURI(unit)));
		final Range range = getRange(unit, search);
		params.setRange(range);
		params.setContext(new CodeActionContext(Collections.emptyList()));
		return params;
	}
}
