/*******************************************************************************
 * Copyright (c) 2017 Red Hat Inc. and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Red Hat Inc. - initial API and implementation
 *******************************************************************************/
package org.eclipse.jdt.ls.core.internal.corrections;

import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.ImportDeclaration;
import org.eclipse.jdt.core.dom.NodeFinder;
import org.eclipse.jdt.core.dom.rewrite.ASTRewrite;
import org.eclipse.jdt.internal.corext.dom.ASTNodes;
import org.eclipse.text.edits.ReplaceEdit;
import org.eclipse.text.edits.TextEdit;
import org.eclipse.text.edits.TextEditGroup;

/**
 * @author Gorkem Ercan
 *
 */
public class UnusedCodeCorrections {

	public static TextEdit createUnusedImportTextEdit(CompilationUnit cu, int problemStart, int problemLength){
		ImportDeclaration node = getImportDeclaration(cu, problemStart, problemLength);
		if(node == null ) {
			return null;
		}
		ASTRewrite rewrite = ASTRewrite.create(cu.getAST());
		rewrite.remove(node, new TextEditGroup("remove_import") );
		try {
			return rewrite.rewriteAST();
		} catch (JavaModelException | IllegalArgumentException e) {
			return null;
		}
	}

	public static TextEdit createSuperfluousSemicolonTextEdit(CompilationUnit cu, int problemStart, int problemLength){
		return new ReplaceEdit(problemStart, problemLength, "");
	}

	private static ImportDeclaration getImportDeclaration(CompilationUnit compilationUnit, int start, int length) {
		NodeFinder finder = new NodeFinder(compilationUnit, start, length);
		ASTNode selectedNode= finder.getCoveringNode();
		if (selectedNode != null) {
			ASTNode node= ASTNodes.getParent(selectedNode, ASTNode.IMPORT_DECLARATION);
			if (node instanceof ImportDeclaration) {
				return (ImportDeclaration)node;
			}
		}
		return null;
	}

}
