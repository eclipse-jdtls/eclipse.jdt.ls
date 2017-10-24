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
package org.eclipse.jdt.ls.core.internal;

import java.util.List;

import org.apache.commons.lang3.StringUtils;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.ls.core.internal.corext.codemanipulation.OrganizeImportsOperation;
import org.eclipse.jdt.ls.core.internal.corrections.InnovationContext;
import org.eclipse.jdt.ls.core.internal.corrections.proposals.CUCorrectionProposal;
import org.eclipse.jdt.ls.core.internal.corrections.proposals.IProposalRelevance;
import org.eclipse.jface.text.IDocument;
import org.eclipse.lsp4j.WorkspaceEdit;
import org.eclipse.ltk.core.refactoring.TextChange;
import org.eclipse.text.edits.TextEdit;

public class JDTDelegateCommandHandler implements IDelegateCommandHandler {

	/* (non-Javadoc)
	 * @see org.eclipse.jdt.ls.core.internal.IDelegateCommandHandler#executeCommand(java.lang.String, java.util.List, org.eclipse.core.runtime.IProgressMonitor)
	 */
	@Override
	public Object executeCommand(String commandId, List<Object> arguments, IProgressMonitor monitor) throws Exception {
		if (!StringUtils.isBlank(commandId)) {
			switch (commandId) {
				case "java.edit.organizeImports":
					return organizeImports(arguments);
				default:
					break;
			}
		}
		throw new UnsupportedOperationException(String.format("Java language server doesn't support the command '%s'.", commandId));
	}

	public Object organizeImports(List<Object> arguments) {
		String fileUri = (String) arguments.get(0);
		if (JDTUtils.toURI(fileUri) != null) {
			final ICompilationUnit unit = JDTUtils.resolveCompilationUnit(fileUri);
			return organizeImports(unit);
		}

		return new WorkspaceEdit();
	}

	public WorkspaceEdit organizeImports(ICompilationUnit unit) {
		try {
			if (unit == null) {
				return new WorkspaceEdit();
			}

			InnovationContext context = new InnovationContext(unit, 0, unit.getBuffer().getLength() - 1);
			CUCorrectionProposal proposal = new CUCorrectionProposal("OrganizeImports", unit, IProposalRelevance.ORGANIZE_IMPORTS) {
				@Override
				protected void addEdits(IDocument document, TextEdit editRoot) throws CoreException {
					CompilationUnit astRoot = context.getASTRoot();
					OrganizeImportsOperation op = new OrganizeImportsOperation(unit, astRoot, true, false, true, null);
					editRoot.addChild(op.createTextEdit(null));
				}
			};

			return getWorkspaceEdit(proposal, unit);
		} catch (CoreException e) {
			JavaLanguageServerPlugin.logException("Problem organize imports ", e);
		}
		return new WorkspaceEdit();
	}

	private WorkspaceEdit getWorkspaceEdit(CUCorrectionProposal proposal, ICompilationUnit cu) throws CoreException {
		TextChange textChange = proposal.getTextChange();
		TextEdit edit = textChange.getEdit();
		TextEditConverter converter = new ImportTextEditConverter(cu, edit);
		WorkspaceEdit $ = new WorkspaceEdit();
		$.getChanges().put(JDTUtils.getFileURI(cu), converter.convert());
		return $;
	}
}
