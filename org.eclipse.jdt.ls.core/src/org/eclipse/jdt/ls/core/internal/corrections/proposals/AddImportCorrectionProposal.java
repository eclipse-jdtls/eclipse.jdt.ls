/*******************************************************************************
 * Copyright (c) 2000, 2012 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.jdt.ls.core.internal.corrections.proposals;


import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.core.dom.rewrite.ASTRewrite;
import org.eclipse.jdt.ui.text.java.correction.ASTRewriteCorrectionProposalCore;


public class AddImportCorrectionProposal extends ASTRewriteCorrectionProposalCore {

	private final String fTypeName;
	private final String fQualifierName;

	public AddImportCorrectionProposal(String name, ICompilationUnit cu, int relevance, String qualifierName,
			String typeName, SimpleName node) {
		super(name, cu, ASTRewrite.create(node.getAST()), relevance);
		fTypeName= typeName;
		fQualifierName= qualifierName;
	}

	public String getQualifiedTypeName() {
		return fQualifierName + '.' + fTypeName;
	}

}
