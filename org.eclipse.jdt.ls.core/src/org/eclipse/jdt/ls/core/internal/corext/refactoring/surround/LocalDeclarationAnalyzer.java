/*******************************************************************************
 * Copyright (c) 2000, 2015 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Originally copied from org.eclipse.jdt.internal.corext.refactoring.surround.LocalDeclarationAnalyzer
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.jdt.ls.core.internal.corext.refactoring.surround;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.IVariableBinding;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.core.dom.VariableDeclaration;
import org.eclipse.jdt.internal.corext.dom.ASTNodes;
import org.eclipse.jdt.ls.core.internal.corext.dom.Selection;

public class LocalDeclarationAnalyzer extends ASTVisitor {

	private Selection fSelection;
	private List<VariableDeclaration> fAffectedLocals;

	public static VariableDeclaration[] perform(ASTNode parent, Selection selection) {
		LocalDeclarationAnalyzer analyzer = new LocalDeclarationAnalyzer(selection);
		parent.accept(analyzer);
		return analyzer.fAffectedLocals.toArray(new VariableDeclaration[analyzer.fAffectedLocals.size()]);
	}

	private LocalDeclarationAnalyzer(Selection selection) {
		fSelection = selection;
		fAffectedLocals = new ArrayList<>(1);
	}

	@Override
	public boolean visit(SimpleName node) {
		IVariableBinding binding = null;
		if (node.isDeclaration() || !considerNode(node) || (binding = ASTNodes.getLocalVariableBinding(node)) == null) {
			return false;
		}
		handleReferenceToLocal(node, binding);
		return true;
	}

	private boolean considerNode(ASTNode node) {
		return fSelection.getVisitSelectionMode(node) == Selection.AFTER;
	}

	private void handleReferenceToLocal(SimpleName node, IVariableBinding binding) {
		VariableDeclaration declaration = ASTNodes.findVariableDeclaration(binding, node);
		if (declaration != null && fSelection.covers(declaration)) {
			addLocalDeclaration(declaration);
		}
	}

	private void addLocalDeclaration(VariableDeclaration declaration) {
		if (!fAffectedLocals.contains(declaration)) {
			fAffectedLocals.add(declaration);
		}
	}
}
