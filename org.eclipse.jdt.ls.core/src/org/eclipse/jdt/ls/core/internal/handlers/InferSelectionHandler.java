/*******************************************************************************
 * Copyright (c) 2020 Microsoft Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *     Microsoft Corporation - initial API and implementation
 *******************************************************************************/

package org.eclipse.jdt.ls.core.internal.handlers;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.ParenthesizedExpression;
import org.eclipse.jdt.ls.core.internal.JDTUtils;
import org.eclipse.jdt.ls.core.internal.corext.refactoring.code.ExtractFieldRefactoring;
import org.eclipse.jdt.ls.core.internal.corext.refactoring.code.ExtractConstantRefactoring;
import org.eclipse.jdt.ls.core.internal.corext.refactoring.code.ExtractMethodRefactoring;
import org.eclipse.jdt.ls.core.internal.corext.refactoring.code.ExtractTempRefactoring;
import org.eclipse.jdt.ls.core.internal.corrections.DiagnosticsHelper;
import org.eclipse.jdt.ls.core.internal.corrections.InnovationContext;
import org.eclipse.jdt.ls.core.internal.text.correction.RefactorProposalUtility;
import org.eclipse.lsp4j.CodeActionParams;

public class InferSelectionHandler {

	public static List<SelectionInfo> inferSelectionsForRefactor(InferSelectionParams params) {
		final ICompilationUnit unit = JDTUtils.resolveCompilationUnit(params.context.getTextDocument().getUri());
		if (unit == null) {
			return null;
		}
		int start = DiagnosticsHelper.getStartOffset(unit, params.context.getRange());
		int end = DiagnosticsHelper.getEndOffset(unit, params.context.getRange());
		InnovationContext context = new InnovationContext(unit, start, end - start);
		List<SelectionInfo> selectionCandidates = new ArrayList<SelectionInfo>();
		ASTNode parent = context.getCoveringNode();
		try {
			if (RefactorProposalUtility.EXTRACT_METHOD_COMMAND.equals(params.command)) {
				while (parent != null && parent instanceof Expression) {
					if (parent instanceof ParenthesizedExpression) {
						parent = parent.getParent();
						continue;
					}
					ExtractMethodRefactoring refactoring = new ExtractMethodRefactoring(context.getASTRoot(), parent.getStartPosition(), parent.getLength());
					if (refactoring.checkInitialConditions(new NullProgressMonitor()).isOK()) {
						selectionCandidates.add(new SelectionInfo(parent.toString(), parent.getStartPosition(), parent.getLength()));
					}
					parent = parent.getParent();
				}
			} else if (RefactorProposalUtility.EXTRACT_VARIABLE_ALL_OCCURRENCE_COMMAND.equals(params.command) || RefactorProposalUtility.EXTRACT_VARIABLE_COMMAND.equals(params.command)) {
				while (parent != null && parent instanceof Expression) {
					if (parent instanceof ParenthesizedExpression) {
						parent = parent.getParent();
						continue;
					}
					ExtractTempRefactoring refactoring = new ExtractTempRefactoring(context.getASTRoot(), parent.getStartPosition(), parent.getLength());
					if (refactoring.checkInitialConditions(new NullProgressMonitor()).isOK()) {
						selectionCandidates.add(new SelectionInfo(parent.toString(), parent.getStartPosition(), parent.getLength()));
					}
					parent = parent.getParent();
				}
			} else if (RefactorProposalUtility.EXTRACT_CONSTANT_COMMAND.equals(params.command)) {
				while (parent != null && parent instanceof Expression) {
					if (parent instanceof ParenthesizedExpression) {
						parent = parent.getParent();
						continue;
					}
					ExtractConstantRefactoring refactoring = new ExtractConstantRefactoring(context.getASTRoot(), parent.getStartPosition(), parent.getLength());
					if (refactoring.checkInitialConditions(new NullProgressMonitor()).isOK()) {
						selectionCandidates.add(new SelectionInfo(parent.toString(), parent.getStartPosition(), parent.getLength()));
					}
					parent = parent.getParent();
				}
			} else if (RefactorProposalUtility.EXTRACT_FIELD_COMMAND.equals(params.command)) {
				while (parent != null && parent instanceof Expression) {
					if (parent instanceof ParenthesizedExpression) {
						parent = parent.getParent();
						continue;
					}
					ExtractFieldRefactoring refactoring = new ExtractFieldRefactoring(context.getASTRoot(), parent.getStartPosition(), parent.getLength());
					if (refactoring.checkInitialConditions(new NullProgressMonitor()).isOK()) {
						List<String> scopes = RefactorProposalUtility.getInitializeScopes(refactoring);
						if (!scopes.isEmpty()) {
							selectionCandidates.add(new SelectionInfo(parent.toString(), parent.getStartPosition(), parent.getLength(), scopes));
						}
					}
					parent = parent.getParent();
				}
			}

		} catch (CoreException e) {
			// do nothing.
		}

		if (selectionCandidates.size() == 0) {
			return null;
		}
		return selectionCandidates;
	}

	public static class SelectionInfo {
		public String name;
		public int offset;
		public int length;
		public List<String> params;

		public SelectionInfo(String name, int offset, int length) {
			this(name, offset, length, null);
		}

		public SelectionInfo(String name, int offset, int length, List<String> params) {
			this.name = name;
			this.offset = offset;
			this.length = length;
			this.params = params;
		}
	}

	public static class InferSelectionParams {
		public String command;
		public CodeActionParams context;

		public InferSelectionParams(String command, CodeActionParams context) {
			this.command = command;
			this.context = context;
		}
	}

}
