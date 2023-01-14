/*******************************************************************************
 * Copyright (c) 2019-2020 Microsoft Corporation and others.
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

import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Stream;

import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.core.runtime.OperationCanceledException;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.ISourceRange;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.manipulation.CoreASTProvider;
import org.eclipse.jdt.core.manipulation.OrganizeImportsOperation;
import org.eclipse.jdt.core.search.TypeNameMatch;
import org.eclipse.jdt.ls.core.internal.JDTUtils;
import org.eclipse.jdt.ls.core.internal.JSONUtility;
import org.eclipse.jdt.ls.core.internal.JavaClientConnection;
import org.eclipse.jdt.ls.core.internal.JavaLanguageServerPlugin;
import org.eclipse.jdt.ls.core.internal.JobHelpers;
import org.eclipse.jdt.ls.core.internal.corrections.proposals.CUCorrectionProposal;
import org.eclipse.jdt.ls.core.internal.text.correction.SourceAssistProcessor;
import org.eclipse.jface.text.IDocument;
import org.eclipse.lsp4j.CodeActionParams;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.WorkspaceEdit;
import org.eclipse.text.edits.MultiTextEdit;
import org.eclipse.text.edits.TextEdit;

import com.google.gson.Gson;

public final class OrganizeImportsHandler {
	public static final String CLIENT_COMMAND_ID_CHOOSEIMPORTS = "java.action.organizeImports.chooseImports";

	// For test purpose
	public static TextEdit organizeImports(ICompilationUnit unit, Function<ImportSelection[], ImportCandidate[]> chooseImports) {
		return organizeImports(unit, chooseImports, false, new NullProgressMonitor());
	}

	public static TextEdit organizeImports(ICompilationUnit unit, Function<ImportSelection[], ImportCandidate[]> chooseImports, boolean restoreExistingImports, IProgressMonitor monitor) {
		if (unit == null) {
			return null;
		}

		CompilationUnit astRoot = CoreASTProvider.getInstance().getAST(unit, CoreASTProvider.WAIT_YES, monitor);
		if (astRoot == null) {
			return null;
		}

		OrganizeImportsOperation op = new OrganizeImportsOperation(unit, astRoot, true, false, true, chooseImports != null ? (TypeNameMatch[][] openChoices, ISourceRange[] ranges) -> {
			List<ImportSelection> selections = new ArrayList<>();
			for (int i = 0; i < openChoices.length; i++) {
				ImportCandidate[] candidates = Stream.of(openChoices[i]).map((choice) -> new ImportCandidate(choice)).toArray(ImportCandidate[]::new);
				Range range = null;
				try {
					range = JDTUtils.toRange(unit, ranges[i].getOffset(), ranges[i].getLength());
				} catch (JavaModelException e) {
					range = JDTUtils.newRange();
				}
				// TODO Sort the ambiguous candidates based on a relevance score.
				selections.add(new ImportSelection(candidates, range));
			}

			ImportCandidate[] chosens = chooseImports.apply(selections.toArray(new ImportSelection[0]));
			if (chosens == null) {
				return null;
			}

			Map<String, TypeNameMatch> typeMaps = new HashMap<>();
			Stream.of(openChoices).flatMap(x -> Arrays.stream(x)).forEach(x -> {
				typeMaps.put(x.getFullyQualifiedName() + "@" + x.hashCode(), x);
			});
			return Stream.of(chosens).filter(chosen -> chosen != null && typeMaps.containsKey(chosen.id)).map(chosen -> typeMaps.get(chosen.id)).toArray(TypeNameMatch[]::new);
		} : null, restoreExistingImports);
		try {
			JobHelpers.waitForJobs(DocumentLifeCycleHandler.DOCUMENT_LIFE_CYCLE_JOBS, monitor);
			TextEdit edit = op.createTextEdit(null);
			if (edit instanceof MultiTextEdit && edit.getOffset() == 0 && edit.getLength() == 0 && ((MultiTextEdit) edit).getChildrenSize() == 0) {
				return null;
			}
			return edit;
		} catch (OperationCanceledException | CoreException e) {
			JavaLanguageServerPlugin.logException("Failed to resolve organize imports source action", e);
		}
		return null;
	}

	public static WorkspaceEdit organizeImports(JavaClientConnection connection, CodeActionParams params, IProgressMonitor monitor) {
		String uri = params.getTextDocument().getUri();
		final ICompilationUnit unit = JDTUtils.resolveCompilationUnit(params.getTextDocument().getUri());
		if (unit == null) {
			return null;
		}

		TextEdit edit = organizeImports(unit, getChooseImportsFunction(uri, false), false, monitor);
		return SourceAssistProcessor.convertToWorkspaceEdit(unit, edit);
	}

	public static Function<ImportSelection[], ImportCandidate[]> getChooseImportsFunction(String documentUri, boolean restoreExistingImports) {
		return (selections) -> {
			Object commandResult = JavaLanguageServerPlugin.getInstance().getClientConnection().executeClientCommand(CLIENT_COMMAND_ID_CHOOSEIMPORTS, documentUri, selections, restoreExistingImports);
			String json = commandResult == null ? null : new Gson().toJson(commandResult);
			return JSONUtility.toModel(json, ImportCandidate[].class);
		};
	}

	public static CUCorrectionProposal getOrganizeImportsProposal(String label, String kind, ICompilationUnit cu, int relevance, CompilationUnit astRoot, boolean supportsChooseImports, boolean restoreExistingImports) {
		IResource resource = cu.getResource();
		if (resource == null) {
			return null;
		}
		URI uri = resource.getLocationURI();
		if (uri == null) {
			return null;
		}
		return new CUCorrectionProposal(label, kind, cu, null, relevance) {
			@Override
			protected void addEdits(IDocument document, TextEdit editRoot) throws CoreException {
				TextEdit edit = OrganizeImportsHandler.organizeImports(cu, supportsChooseImports ? OrganizeImportsHandler.getChooseImportsFunction(uri.toString(), restoreExistingImports) : null, restoreExistingImports, new NullProgressMonitor());
				if (edit != null) {
					editRoot.addChild(edit);
				}
			}
		};
	}

	public static class ImportCandidate {
		public String fullyQualifiedName;
		public String id;

		public ImportCandidate() {
		}

		public ImportCandidate(TypeNameMatch typeMatch) {
			fullyQualifiedName = typeMatch.getFullyQualifiedName();
			id = typeMatch.getFullyQualifiedName() + "@" + typeMatch.hashCode();
		}
	}

	public static class ImportSelection {
		public ImportCandidate[] candidates;
		public Range range;

		public ImportSelection(ImportCandidate[] candidates, Range range) {
			this.candidates = candidates;
			this.range = range;
		}
	}
}
