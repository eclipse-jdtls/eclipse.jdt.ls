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
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.ISourceRange;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.Signature;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.IBinding;
import org.eclipse.jdt.core.dom.Name;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.core.dom.rewrite.ASTRewrite;
import org.eclipse.jdt.core.dom.rewrite.ImportRewrite;
import org.eclipse.jdt.core.manipulation.CodeStyleConfiguration;
import org.eclipse.jdt.core.manipulation.CoreASTProvider;
import org.eclipse.jdt.core.manipulation.ImportReferencesCollector;
import org.eclipse.jdt.core.manipulation.OrganizeImportsOperation;
import org.eclipse.jdt.core.search.TypeNameMatch;
import org.eclipse.jdt.internal.corext.codemanipulation.ContextSensitiveImportRewriteContext;
import org.eclipse.jdt.internal.corext.util.JavaModelUtil;
import org.eclipse.jdt.ls.core.internal.JDTUtils;
import org.eclipse.jdt.ls.core.internal.JSONUtility;
import org.eclipse.jdt.ls.core.internal.JavaClientConnection;
import org.eclipse.jdt.ls.core.internal.JavaLanguageServerPlugin;
import org.eclipse.jdt.ls.core.internal.JobHelpers;
import org.eclipse.jdt.ls.core.internal.corrections.SimilarElementsRequestor;
import org.eclipse.jdt.ls.core.internal.corrections.proposals.CUCorrectionProposal;
import org.eclipse.jdt.ls.core.internal.preferences.PreferenceManager;
import org.eclipse.jdt.ls.core.internal.text.correction.SourceAssistProcessor;
import org.eclipse.jface.text.IDocument;
import org.eclipse.lsp4j.CodeActionParams;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.WorkspaceEdit;
import org.eclipse.text.edits.DeleteEdit;
import org.eclipse.text.edits.InsertEdit;
import org.eclipse.text.edits.MalformedTreeException;
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
			// See https://bugs.eclipse.org/bugs/show_bug.cgi?id=283287
			// and https://github.com/redhat-developer/vscode-java/issues/1472
			TextEdit staticEdit = wrapStaticImports(edit, astRoot, unit);
			if (staticEdit.getChildrenSize() == 0) {
				return null;
			}
			return staticEdit;
		} catch (OperationCanceledException | CoreException e) {
			JavaLanguageServerPlugin.logException("Failed to resolve organize imports source action", e);
		}
		return null;
	}

	public static TextEdit wrapStaticImports(TextEdit edit, CompilationUnit root, ICompilationUnit unit) throws MalformedTreeException, CoreException {
		String[] favourites = PreferenceManager.getPrefs(unit.getResource()).getJavaCompletionFavoriteMembers();
		if (favourites.length == 0) {
			return edit;
		}
		IJavaProject project = unit.getJavaProject();
		if (JavaModelUtil.is50OrHigher(project)) {
			List<SimpleName> typeReferences = new ArrayList<>();
			List<SimpleName> staticReferences = new ArrayList<>();
			ImportReferencesCollector.collect(root, project, null, typeReferences, staticReferences);
			if (staticReferences.isEmpty()) {
				return edit;
			}
			ImportRewrite importRewrite = CodeStyleConfiguration.createImportRewrite(root, true);
			AST ast = root.getAST();
			ASTRewrite astRewrite = ASTRewrite.create(ast);
			for (SimpleName node : staticReferences) {
				addImports(root, unit, favourites, importRewrite, ast, astRewrite, node, true);
				addImports(root, unit, favourites, importRewrite, ast, astRewrite, node, false);
			}
			TextEdit staticEdit = importRewrite.rewriteImports(null);
			if (staticEdit != null && staticEdit.getChildrenSize() > 0) {
				TextEdit lastStatic = staticEdit.getChildren()[staticEdit.getChildrenSize() - 1];
				if (lastStatic instanceof DeleteEdit) {
					if (edit.getChildrenSize() > 0) {
						TextEdit last = edit.getChildren()[edit.getChildrenSize() - 1];
						if (last instanceof DeleteEdit && lastStatic.getOffset() == last.getOffset() && lastStatic.getLength() == last.getLength()) {
							edit.removeChild(last);
						}
					}
				}
				TextEdit firstStatic = staticEdit.getChildren()[0];
				if (firstStatic instanceof InsertEdit firstStaticInsert) {
					if (edit.getChildrenSize() > 0) {
						TextEdit firstEdit = edit.getChildren()[0];
						if (firstEdit instanceof InsertEdit firstEditInsert) {
							if (areEqual(firstEditInsert, firstStaticInsert)) {
								edit.removeChild(firstEdit);
							}
						}
					}
				}
				try {
					staticEdit.addChild(edit);
					return staticEdit;
				} catch (MalformedTreeException e) {
					JavaLanguageServerPlugin.logException("Failed to resolve static organize imports source action", e);
				}
			}
		}
		return edit;
	}

	private static boolean areEqual(InsertEdit edit1, InsertEdit edit2) {
		if (edit1 != null && edit2 != null) {
			return edit1.getOffset() == edit2.getOffset() && edit1.getLength() == edit2.getLength() && edit1.getText().equals(edit2.getText());
		}
		return false;
	}

	private static void addImports(CompilationUnit root, ICompilationUnit unit, String[] favourites, ImportRewrite importRewrite, AST ast, ASTRewrite astRewrite, SimpleName node, boolean isMethod) throws JavaModelException {
		IBinding binding = node.resolveBinding();
		if (binding != null) {
			importRewrite.addStaticImport(binding);
		} else {
			String name = node.getIdentifier();
			String[] imports = SimilarElementsRequestor.getStaticImportFavorites(unit, name, isMethod, favourites);
			if (imports.length > 1) {
				// See https://github.com/redhat-developer/vscode-java/issues/1472
				return;
			}
			for (int i = 0; i < imports.length; i++) {
				String curr = imports[i];
				String qualifiedTypeName = Signature.getQualifier(curr);
				String res = importRewrite.addStaticImport(qualifiedTypeName, name, isMethod, new ContextSensitiveImportRewriteContext(root, node.getStartPosition(), importRewrite));
				int dot = res.lastIndexOf('.');
				if (dot != -1) {
					String usedTypeName = importRewrite.addImport(qualifiedTypeName);
					Name newName = ast.newQualifiedName(ast.newName(usedTypeName), ast.newSimpleName(name));
					astRewrite.replace(node, newName, null);
				}
			}
		}
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
