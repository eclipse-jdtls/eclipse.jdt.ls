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
import org.eclipse.jdt.ls.core.internal.JavaClientConnection;
import org.eclipse.jdt.ls.core.internal.JavaLanguageServerPlugin;
import org.eclipse.jdt.ls.core.internal.text.correction.SourceAssistProcessor;
import org.eclipse.lsp4j.CodeActionParams;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.WorkspaceEdit;
import org.eclipse.text.edits.TextEdit;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;

public class OrganizeImportsHandler {

	public static TextEdit organizeImports(ICompilationUnit unit, Function<ImportSelection[], ImportChoice[]> chooseImports) {
		if (unit == null) {
			return null;
		}

		RefactoringASTParser astParser = new RefactoringASTParser(IASTSharedValues.SHARED_AST_LEVEL);
		CompilationUnit astRoot = astParser.parse(unit, true);
		OrganizeImportsOperation op = new OrganizeImportsOperation(unit, astRoot, true, false, true, (TypeNameMatch[][] openChoices, ISourceRange[] ranges) -> {
			List<ImportSelection> selections = new ArrayList<>();
			for (int i = 0; i < openChoices.length; i++) {
				ImportChoice[] choices = Stream.of(openChoices[i]).map((choice) -> new ImportChoice(choice)).toArray(ImportChoice[]::new);
				Range range = null;
				try {
					range = JDTUtils.toRange(unit, ranges[i].getOffset(), ranges[i].getLength());
				} catch (JavaModelException e) {
					range = JDTUtils.newRange();
				}
				// TODO Based on the context, recommend a default type to import for the code with multiple ambiguous imports.
				int defaultSelection = 0;
				selections.add(new ImportSelection(choices, range, defaultSelection));
			}

			ImportChoice[] chosens = chooseImports.apply(selections.toArray(new ImportSelection[0]));
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
			JavaLanguageServerPlugin.logException("Resolve organize imports source action", e);
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
			Object commandResult = connection.executeClientCommand("java.action.organizeImports.chooseImports", uri, selections);
			return toModel(commandResult, ImportChoice[].class);
		});
		return SourceAssistProcessor.convertToWorkspaceEdit(unit, edit);
	}

	private static <T> T toModel(Object obj, Class<T> clazz) {
		try {
			if (obj == null) {
				return null;
			}

			if (clazz.isInstance(obj)) {
				return clazz.cast(obj);
			}

			final Gson GSON = new Gson();
			String json = GSON.toJson(obj);
			return GSON.fromJson(json, clazz);
		} catch (JsonSyntaxException ex) {
			JavaLanguageServerPlugin.logException("Failed to cast the value to " + clazz, ex);
		}

		return null;
	}

	public static class ImportChoice {
		public String qualifiedName;
		public String id;

		public ImportChoice() {
		}

		public ImportChoice(TypeNameMatch typeMatch) {
			qualifiedName = typeMatch.getFullyQualifiedName();
			id = typeMatch.getFullyQualifiedName() + "@" + typeMatch.hashCode();
		}
	}

	public static class ImportSelection {
		public ImportChoice[] candidates;
		public Range range;
		public int defaultSelection = 0;

		public ImportSelection(ImportChoice[] candidates, Range range, int defaultSelection) {
			this.candidates = candidates;
			this.range = range;
			this.defaultSelection = defaultSelection;
		}

		public ImportSelection(ImportChoice[] candidates, Range range) {
			this(candidates, range, 0);
		}
	}
}
