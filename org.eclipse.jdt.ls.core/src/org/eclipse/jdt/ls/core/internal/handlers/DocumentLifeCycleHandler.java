/*******************************************************************************
 * Copyright (c) 2016-2017 Red Hat Inc. and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *     Red Hat Inc. - initial API and implementation
 *******************************************************************************/
package org.eclipse.jdt.ls.core.internal.handlers;

import static com.google.common.collect.Lists.newArrayList;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import com.google.common.collect.Iterables;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.runtime.IPath;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.manipulation.CoreASTProvider;
import org.eclipse.jdt.internal.ui.javaeditor.HighlightedPositionCore;
import org.eclipse.jdt.ls.core.internal.JDTUtils;
import org.eclipse.jdt.ls.core.internal.JavaClientConnection;
import org.eclipse.jdt.ls.core.internal.JavaLanguageServerPlugin;
import org.eclipse.jdt.ls.core.internal.ResourceUtils;
import org.eclipse.jdt.ls.core.internal.highlighting.SemanticHighlightingService;
import org.eclipse.jdt.ls.core.internal.highlighting.SemanticHighlightingService.HighlightedPositionDiffContext;
import org.eclipse.jdt.ls.core.internal.managers.InvisibleProjectImporter;
import org.eclipse.jdt.ls.core.internal.managers.ProjectsManager;
import org.eclipse.jdt.ls.core.internal.preferences.PreferenceManager;
import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.BadPositionCategoryException;
import org.eclipse.jface.text.Document;
import org.eclipse.jface.text.DocumentEvent;
import org.eclipse.jface.text.IDocument;
import org.eclipse.lsp4j.DidChangeTextDocumentParams;
import org.eclipse.lsp4j.DidCloseTextDocumentParams;
import org.eclipse.lsp4j.DidOpenTextDocumentParams;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.TextDocumentContentChangeEvent;
import org.eclipse.lsp4j.VersionedTextDocumentIdentifier;
import org.eclipse.text.edits.DeleteEdit;
import org.eclipse.text.edits.InsertEdit;
import org.eclipse.text.edits.MalformedTreeException;
import org.eclipse.text.edits.ReplaceEdit;
import org.eclipse.text.edits.TextEdit;

public class DocumentLifeCycleHandler extends BaseDocumentLifeCycleHandler {

	private JavaClientConnection connection;
	private PreferenceManager preferenceManager;

	private CoreASTProvider sharedASTProvider;
	private SemanticHighlightingService semanticHighlightingService;

	public DocumentLifeCycleHandler(JavaClientConnection connection, PreferenceManager preferenceManager, ProjectsManager projectsManager, boolean delayValidation) {
		super(delayValidation);
		this.connection = connection;
		this.preferenceManager = preferenceManager;
		this.sharedASTProvider = CoreASTProvider.getInstance();
		this.semanticHighlightingService = new SemanticHighlightingService(this.connection, this.sharedASTProvider, this.preferenceManager);
	}

	@Override
	public BaseDiagnosticsHandler createDiagnosticsHandler(ICompilationUnit unit) {
		return new DiagnosticsHandler(connection, unit);
	}

	@Override
	public boolean isSyntaxMode(ICompilationUnit unit) {
		return JDTUtils.isDefaultProject(unit) || !JDTUtils.isOnClassPath(unit);
	}

	@Override
	public ICompilationUnit resolveCompilationUnit(String uri) {
		ICompilationUnit unit = null;
		IFile resource = JDTUtils.findFile(uri);
		if (resource != null) { // Open the files already managed by the jdt workspace.
			unit = JDTUtils.resolveCompilationUnit(resource);
		} else { // Open the standalone files.
			IPath filePath = ResourceUtils.canonicalFilePathFromURI(uri);
			Collection<IPath> rootPaths = preferenceManager.getPreferences().getRootPaths();
			Optional<IPath> belongedRootPath = rootPaths.stream().filter(rootPath -> rootPath.isPrefixOf(filePath)).findFirst();
			boolean invisibleProjectEnabled = false;
			if (belongedRootPath.isPresent()) {
				IPath rootPath = belongedRootPath.get();
				invisibleProjectEnabled = InvisibleProjectImporter.loadInvisibleProject(filePath, rootPath, false);
				if (invisibleProjectEnabled) {
					unit = JDTUtils.resolveCompilationUnit(uri);
				}
			}

			if (!invisibleProjectEnabled) {
				unit = JDTUtils.getFakeCompilationUnit(uri);
			}
		}

		return unit;
	}

	@Override
	public ICompilationUnit handleOpen(DidOpenTextDocumentParams params) {
		ICompilationUnit unit = super.handleOpen(params);

		if (unit == null || unit.getResource() == null || unit.getResource().isDerived()) {
			return unit;
		}

		try {
			installSemanticHighlightings(unit);
		} catch (JavaModelException | BadPositionCategoryException e) {
			JavaLanguageServerPlugin.logException("Error while opening document. URI: " + params.getTextDocument().getUri(), e);
		}

		return unit;
	}

	@Override
	public ICompilationUnit handleChanged(DidChangeTextDocumentParams params) {
		String uri = params.getTextDocument().getUri();
		ICompilationUnit unit = JDTUtils.resolveCompilationUnit(uri);

		if (unit == null || !unit.isWorkingCopy() || params.getContentChanges().isEmpty() || unit.getResource().isDerived()) {
			return unit;
		}

		try {
			if (unit.equals(sharedASTProvider.getActiveJavaElement())) {
				sharedASTProvider.disposeAST();
			}
			List<TextDocumentContentChangeEvent> contentChanges = params.getContentChanges();
			List<HighlightedPositionDiffContext> diffContexts = newArrayList();
			for (TextDocumentContentChangeEvent changeEvent : contentChanges) {

				Range range = changeEvent.getRange();
				int length;

				if (range != null) {
					length = changeEvent.getRangeLength().intValue();
				} else {
					// range is optional and if not given, the whole file content is replaced
					length = unit.getSource().length();
					range = JDTUtils.toRange(unit, 0, length);
				}

				int startOffset = JsonRpcHelpers.toOffset(unit.getBuffer(), range.getStart().getLine(), range.getStart().getCharacter());

				TextEdit edit = null;
				String text = changeEvent.getText();
				if (length == 0) {
					edit = new InsertEdit(startOffset, text);
				} else if (text.isEmpty()) {
					edit = new DeleteEdit(startOffset, length);
				} else {
					edit = new ReplaceEdit(startOffset, length, text);
				}

				// Avoid any computation if the `SemanticHighlightingService#isEnabled` is `false`.
				if (semanticHighlightingService.isEnabled()) {
					IDocument oldState = new Document(unit.getBuffer().getContents());
					IDocument newState = JsonRpcHelpers.toDocument(unit.getBuffer());
					//@formatter:off
					List<HighlightedPositionCore> oldPositions = diffContexts.isEmpty()
						? semanticHighlightingService.getHighlightedPositions(uri)
						: Iterables.getLast(diffContexts).newPositions;
					//@formatter:on
					edit.apply(newState, TextEdit.NONE);
					// This is a must. Make the document immutable.
					// Otherwise, any consecutive `newStates` get out-of-sync due to the shared buffer from the compilation unit.
					newState = new Document(newState.get());
					List<HighlightedPositionCore> newPositions = semanticHighlightingService.calculateHighlightedPositions(unit, true);
					DocumentEvent event = new DocumentEvent(newState, startOffset, length, text);
					diffContexts.add(new HighlightedPositionDiffContext(oldState, event, oldPositions, newPositions));
				} else {
					IDocument document = JsonRpcHelpers.toDocument(unit.getBuffer());
					edit.apply(document, TextEdit.NONE);
				}

			}
			triggerValidation(unit);
			updateSemanticHighlightings(params.getTextDocument(), diffContexts);
		} catch (JavaModelException | MalformedTreeException | BadLocationException | BadPositionCategoryException e) {
			JavaLanguageServerPlugin.logException("Error while handling document change. URI: " + uri, e);
		}

		return unit;
	}

	public ICompilationUnit handleClosed(DidCloseTextDocumentParams params) {
		ICompilationUnit unit = super.handleClosed(params);
		uninstallSemanticHighlightings(params.getTextDocument().getUri());
		return unit;
	}

	protected void installSemanticHighlightings(ICompilationUnit unit) throws JavaModelException, BadPositionCategoryException {
		this.semanticHighlightingService.install(unit);
	}

	protected void uninstallSemanticHighlightings(String uri) {
		this.semanticHighlightingService.uninstall(uri);
	}

	protected void updateSemanticHighlightings(VersionedTextDocumentIdentifier textDocument, List<HighlightedPositionDiffContext> diffContexts) throws BadLocationException, BadPositionCategoryException, JavaModelException {
		this.semanticHighlightingService.update(textDocument, diffContexts);
	}

}
