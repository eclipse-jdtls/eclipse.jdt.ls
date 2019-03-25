/*******************************************************************************
 * Copyright (c) 2019 Microsoft Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Microsoft Corporation - initial API and implementation
 *******************************************************************************/

package org.eclipse.jdt.ls.core.internal.handlers;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Stream;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.OperationCanceledException;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.ISourceRange;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.manipulation.OrganizeImportsOperation;
import org.eclipse.jdt.core.search.TypeNameMatch;
import org.eclipse.jdt.internal.corext.dom.IASTSharedValues;
import org.eclipse.jdt.internal.corext.refactoring.util.RefactoringASTParser;
import org.eclipse.jdt.ls.core.internal.JDTUtils;
import org.eclipse.jdt.ls.core.internal.JSONUtility;
import org.eclipse.jdt.ls.core.internal.JavaClientConnection;
import org.eclipse.jdt.ls.core.internal.JavaLanguageServerPlugin;
import org.eclipse.jdt.ls.core.internal.text.correction.SourceAssistProcessor;
import org.eclipse.lsp4j.CodeActionParams;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.WorkspaceEdit;
import org.eclipse.text.edits.TextEdit;

import com.google.gson.Gson;

public final class OrganizeImportsHandler {
	public static final String CLIENT_COMMAND_ID_CHOOSEIMPORTS = "java.action.organizeImports.chooseImports";

	public static TextEdit organizeImports(ICompilationUnit unit, Function<ImportSelection[], ImportCandidate[]> chooseImports) {
		if (unit == null) {
			return null;
		}

		RefactoringASTParser astParser = new RefactoringASTParser(IASTSharedValues.SHARED_AST_LEVEL);
		CompilationUnit astRoot = astParser.parse(unit, true);
		OrganizeImportsOperation op = new OrganizeImportsOperation(unit, astRoot, true, false, true, (TypeNameMatch[][] openChoices, ISourceRange[] ranges) -> {
			List<ImportSelection> selections = new ArrayList<>();
			for (int i = 0; i < openChoices.length; i++) {
				ImportCandidate[] candidates = Stream.of(openChoices[i]).map((choice) -> new ImportCandidate(choice)).toArray(ImportCandidate[]::new);
				Range range = null;
				try {
					range = JDTUtils.toRange(unit, ranges[i].getOffset(), ranges[i].getLength());
				} catch (JavaModelException e) {
					range = JDTUtils.newRange();
				}
				// TODO Based on the context, recommend a default type to import for the code with multiple ambiguous imports.
				int defaultSelection = 0;
				selections.add(new ImportSelection(candidates, range, defaultSelection));
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
		});

		try {
			return op.createTextEdit(null);
		} catch (OperationCanceledException | CoreException e) {
			JavaLanguageServerPlugin.logException("Failed to resolve organize imports source action", e);
		}

		return null;
	}

	public static WorkspaceEdit organizeImports(JavaClientConnection connection, CodeActionParams params) {
		String uri = params.getTextDocument().getUri();
		final ICompilationUnit unit = JDTUtils.resolveCompilationUnit(params.getTextDocument().getUri());
		if (unit == null) {
			return null;
		}

		TextEdit edit = organizeImports(unit, (selections) -> {
			Object commandResult = connection.executeClientCommand(CLIENT_COMMAND_ID_CHOOSEIMPORTS, uri, selections);
			String json = commandResult == null ? null : new Gson().toJson(commandResult);
			return JSONUtility.toModel(json, ImportCandidate[].class);
		});
		return SourceAssistProcessor.convertToWorkspaceEdit(unit, edit);
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
		public int defaultSelection = 0;

		public ImportSelection(ImportCandidate[] candidates, Range range, int defaultSelection) {
			this.candidates = candidates;
			this.range = range;
			this.defaultSelection = defaultSelection;
		}

		public ImportSelection(ImportCandidate[] candidates, Range range) {
			this(candidates, range, 0);
		}
	}
}
