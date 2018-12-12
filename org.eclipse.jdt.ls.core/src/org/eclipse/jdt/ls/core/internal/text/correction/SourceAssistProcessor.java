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

package org.eclipse.jdt.ls.core.internal.text.correction;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.AnonymousClassDeclaration;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.TypeDeclaration;
import org.eclipse.jdt.core.manipulation.OrganizeImportsOperation;
import org.eclipse.jdt.internal.ui.text.correction.IProblemLocationCore;
import org.eclipse.jdt.ls.core.internal.codemanipulation.GenerateGetterSetterOperation;
import org.eclipse.jdt.ls.core.internal.corrections.CorrectionMessages;
import org.eclipse.jdt.ls.core.internal.corrections.IInvocationContext;
import org.eclipse.jdt.ls.core.internal.corrections.proposals.CUCorrectionProposal;
import org.eclipse.jdt.ls.core.internal.corrections.proposals.IProposalRelevance;
import org.eclipse.jface.text.IDocument;
import org.eclipse.lsp4j.CodeActionKind;
import org.eclipse.text.edits.TextEdit;

public class SourceAssistProcessor {
	public static final String SOURCE_ACTION_GENERATE_KIND = CodeActionKind.Source + ".generate";
	public static final String SOURCE_ACTION_GENERATE_ACCESSORS_KIND = SOURCE_ACTION_GENERATE_KIND + ".accessors";

	public List<CUCorrectionProposal> getAssists(IInvocationContext context, IProblemLocationCore[] locations) {
		ArrayList<CUCorrectionProposal> resultingCollections = new ArrayList<>();

		getOrganizeImportsProposal(context, resultingCollections);
		getGetterSetterProposal(context, resultingCollections);

		return resultingCollections;
	}

	private static void getOrganizeImportsProposal(IInvocationContext context, ArrayList<CUCorrectionProposal> resultingCollections) {
		ICompilationUnit unit = context.getCompilationUnit();

		CUCorrectionProposal proposal = new CUCorrectionProposal(CorrectionMessages.ReorgCorrectionsSubProcessor_organizeimports_description,
				CodeActionKind.SourceOrganizeImports,
				unit,
				null,
				IProposalRelevance.ORGANIZE_IMPORTS) {
			@Override
			protected void addEdits(IDocument document, TextEdit editRoot) throws CoreException {
				CompilationUnit astRoot = context.getASTRoot();
				OrganizeImportsOperation op = new OrganizeImportsOperation(unit, astRoot, true, false, true, null);
				editRoot.addChild(op.createTextEdit(null));
			}
		};

		resultingCollections.add(proposal);
	}

	private static void getGetterSetterProposal(IInvocationContext context, List<CUCorrectionProposal> resultingCollections) {
		final IType type = getSelectionType(context);
		try {
			if (!GenerateGetterSetterOperation.supportsGetterSetter(type)) {
				return;
			}
		} catch (JavaModelException e) {
			return;
		}

		ICompilationUnit unit = context.getCompilationUnit();
		CUCorrectionProposal proposal = new CUCorrectionProposal(ActionMessages.GenerateGetterSetterAction_label, SOURCE_ACTION_GENERATE_ACCESSORS_KIND, unit, null, IProposalRelevance.GENERATE_GETTER_AND_SETTER) {

			@Override
			protected void addEdits(IDocument document, TextEdit editRoot) throws CoreException {
				CompilationUnit astRoot = context.getASTRoot();
				GenerateGetterSetterOperation operation = new GenerateGetterSetterOperation(type, astRoot);
				TextEdit textEdit = operation.createTextEdit(null);
				if (textEdit != null) {
					editRoot.addChild(textEdit);
				}
			}
		};

		resultingCollections.add(proposal);
	}

	private static IType getSelectionType(IInvocationContext context) {
		ICompilationUnit unit = context.getCompilationUnit();
		ASTNode node = context.getCoveredNode();
		if (node == null) {
			node = context.getCoveringNode();
		}

		ITypeBinding typeBinding = null;
		while (node != null && !(node instanceof CompilationUnit)) {
			if (node instanceof TypeDeclaration) {
				typeBinding = ((TypeDeclaration) node).resolveBinding();
				break;
			} else if (node instanceof AnonymousClassDeclaration) { // Anonymous
				typeBinding = ((AnonymousClassDeclaration) node).resolveBinding();
				break;
			}

			node = node.getParent();
		}

		if (typeBinding != null && typeBinding.getJavaElement() instanceof IType) {
			return (IType) typeBinding.getJavaElement();
		}

		return unit.findPrimaryType();
	}
}
