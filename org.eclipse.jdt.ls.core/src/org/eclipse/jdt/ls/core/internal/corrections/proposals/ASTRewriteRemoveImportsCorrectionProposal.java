/*******************************************************************************
 * Copyright (c) 2022 Red Hat Inc. and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *     Red Hat Inc. - initial API and implementation
 *******************************************************************************/
package org.eclipse.jdt.ls.core.internal.corrections.proposals;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.dom.rewrite.ASTRewrite;
import org.eclipse.jdt.core.dom.rewrite.ImportRewrite;
import org.eclipse.jdt.internal.corext.refactoring.structure.ImportRemover;

public class ASTRewriteRemoveImportsCorrectionProposal extends ASTRewriteCorrectionProposal {

	private ImportRemover fImportRemover;

	public ASTRewriteRemoveImportsCorrectionProposal(String name, String kind, ICompilationUnit cu, ASTRewrite rewrite, int relevance) {
		super(name, kind, cu, rewrite, relevance);
	}

	public void setImportRemover(ImportRemover remover) {
		fImportRemover = remover;
	}

	@Override
	protected ASTRewrite getRewrite() throws CoreException {
		ASTRewrite rewrite = super.getRewrite();
		ImportRewrite importRewrite = getImportRewrite();
		if (fImportRemover != null && importRewrite != null) {
			fImportRemover.applyRemoves(importRewrite);
		}
		return rewrite;
	}

}
