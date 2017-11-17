/*******************************************************************************
 * Copyright (c) 2017 Microsoft Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Microsoft Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.jdt.ls.core.internal.codelens;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.jdt.core.ITypeRoot;
import org.eclipse.lsp4j.CodeLens;

/**
 * Context of CodeLens.
 *
 */
public class CodeLensContext {

	private final List<CodeLens> codeLenses;
	private final ITypeRoot root;

	public CodeLensContext(ITypeRoot root) {
		codeLenses = new ArrayList<>();
		this.root = root;
	}

	public void addCodeLens(CodeLens lens) {
		codeLenses.add(lens);
	}

	public List<CodeLens> getCodeLenses() {
		return codeLenses;
	}

	public void clearCodeLenses() {
		codeLenses.clear();
	}

	public ITypeRoot getRoot() {
		return this.root;
	}
}
