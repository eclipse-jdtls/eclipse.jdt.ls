/*******************************************************************************
 * Copyright (c) 2000, 2011 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Originally copied from org.eclipse.jdt.internal.corext.fix.SerialVersionDefaultOperation
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.jdt.ls.core.internal.corext.fix;

import org.eclipse.core.runtime.Assert;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.VariableDeclarationFragment;
import org.eclipse.jdt.core.dom.rewrite.ASTRewrite;
import org.eclipse.jdt.internal.corext.fix.LinkedProposalModelCore;
import org.eclipse.jdt.internal.corext.fix.LinkedProposalPositionGroupCore;


/**
 * Proposal for a default serial version id.
 *
 * @since 3.1
 */
public final class SerialVersionDefaultOperation extends AbstractSerialVersionOperation {

	/** The initializer linked position group id */
	private static final String GROUP_INITIALIZER= "initializer"; //$NON-NLS-1$

	/**
	 * Creates a new serial version default proposal.
	 *
	 * @param unit
	 *            the compilation unit
	 * @param nodes
	 *            the originally selected nodes
	 */
	public SerialVersionDefaultOperation(ICompilationUnit unit, ASTNode[] nodes) {
		super(unit, nodes);
	}


	@Override
	protected boolean addInitializer(final VariableDeclarationFragment fragment, final ASTNode declarationNode) {
		Assert.isNotNull(fragment);

		final Expression expression= fragment.getAST().newNumberLiteral(DEFAULT_EXPRESSION);
		if (expression != null) {
			fragment.setInitializer(expression);
		}
		return true;
	}

	@Override
	protected void addLinkedPositions(final ASTRewrite rewrite, final VariableDeclarationFragment fragment, final LinkedProposalModelCore positionGroups) {

		Assert.isNotNull(rewrite);
		Assert.isNotNull(fragment);

		final Expression initializer= fragment.getInitializer();
		if (initializer != null) {
			LinkedProposalPositionGroupCore group = new LinkedProposalPositionGroupCore(GROUP_INITIALIZER);
			group.addPosition(rewrite.track(initializer), true);
			positionGroups.addPositionGroup(group);
		}
	}

}
