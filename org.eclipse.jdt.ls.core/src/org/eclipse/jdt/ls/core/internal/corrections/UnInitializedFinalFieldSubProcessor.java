/*******************************************************************************
 * Copyright (c) 2024 Red Hat Inc. and others.
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
package org.eclipse.jdt.ls.core.internal.corrections;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.dom.IVariableBinding;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.ui.text.java.IProblemLocation;
import org.eclipse.jdt.internal.ui.text.correction.IProposalRelevance;
import org.eclipse.jdt.internal.ui.text.correction.UnInitializedFinalFieldBaseSubProcessor;
import org.eclipse.jdt.internal.ui.text.correction.proposals.InitializeFinalFieldProposalCore;
import org.eclipse.jdt.ls.core.internal.handlers.CodeActionHandler;
import org.eclipse.lsp4j.CodeActionKind;

/**
 * @author snjeza
 *
 */
public class UnInitializedFinalFieldSubProcessor extends UnInitializedFinalFieldBaseSubProcessor<ProposalKindWrapper> {

	@Override
	protected ProposalKindWrapper createInitializeFinalFieldProposal(IProblemLocation problem, ICompilationUnit targetCU, SimpleName node, IVariableBinding targetBinding, int createConstructor) {
		InitializeFinalFieldProposalCore proposal = new InitializeFinalFieldProposalCore(problem, targetCU, node, targetBinding, IProposalRelevance.CREATE_CONSTRUCTOR);
		return CodeActionHandler.wrap(proposal, CodeActionKind.QuickFix);
	}

	@Override
	protected ProposalKindWrapper createInitializeFinalFieldProposal(IProblemLocation problem, ICompilationUnit targetCU, MethodDeclaration node, int createConstructor, int updateAtConstructor) {
		InitializeFinalFieldProposalCore proposal = new InitializeFinalFieldProposalCore(problem, targetCU, node, IProposalRelevance.CREATE_CONSTRUCTOR, InitializeFinalFieldProposalCore.UPDATE_AT_CONSTRUCTOR);
		return CodeActionHandler.wrap(proposal, CodeActionKind.QuickFix);
	}

	@Override
	protected ProposalKindWrapper conditionallyCreateInitializeFinalFieldProposal(IProblemLocation problem, ICompilationUnit targetCU, MethodDeclaration node, int createConstructor, int updateAtConstructor) {
		InitializeFinalFieldProposalCore initializeFinalFieldProposal = new InitializeFinalFieldProposalCore(problem, targetCU, node, IProposalRelevance.CREATE_CONSTRUCTOR, InitializeFinalFieldProposalCore.UPDATE_CONSTRUCTOR_NEW_PARAMETER);
		try {
			if (initializeFinalFieldProposal.hasProposal()) {
				return CodeActionHandler.wrap(initializeFinalFieldProposal, CodeActionKind.QuickFix);
			}
		} catch (CoreException ce) {
		}
		return null;
	}
}
