/*******************************************************************************
 * Copyright (c) 2000, 2012 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.jdt.ls.core.internal.corrections.proposals;


import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.core.dom.rewrite.ASTRewrite;
import org.eclipse.lsp4j.CodeActionKind;


public class AddImportCorrectionProposal extends ASTRewriteCorrectionProposal {

	private final String fTypeName;
	private final String fQualifierName;

	public AddImportCorrectionProposal(String name, ICompilationUnit cu, int relevance, String qualifierName,
			String typeName, SimpleName node) {
		super(name, CodeActionKind.QuickFix, cu, ASTRewrite.create(node.getAST()), relevance);
		fTypeName= typeName;
		fQualifierName= qualifierName;
	}

	public String getQualifiedTypeName() {
		return fQualifierName + '.' + fTypeName;
	}

}
