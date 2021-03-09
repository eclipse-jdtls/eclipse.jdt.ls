/*******************************************************************************
 * Copyright (c) 2016-2021 Red Hat Inc. and others.
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

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.manipulation.CoreASTProvider;
import org.eclipse.jdt.ls.core.internal.JDTUtils;
import org.eclipse.jdt.ls.core.internal.JavaClientConnection;
import org.eclipse.jdt.ls.core.internal.JavaLanguageServerPlugin;
import org.eclipse.jdt.ls.core.internal.ResourceUtils;
import org.eclipse.jdt.ls.core.internal.managers.InvisibleProjectImporter;
import org.eclipse.jdt.ls.core.internal.managers.ProjectsManager;
import org.eclipse.jdt.ls.core.internal.preferences.PreferenceManager;
import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.IDocument;
import org.eclipse.lsp4j.DidChangeTextDocumentParams;
import org.eclipse.lsp4j.DidCloseTextDocumentParams;
import org.eclipse.lsp4j.DidOpenTextDocumentParams;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.TextDocumentContentChangeEvent;
import org.eclipse.text.edits.DeleteEdit;
import org.eclipse.text.edits.InsertEdit;
import org.eclipse.text.edits.MalformedTreeException;
import org.eclipse.text.edits.ReplaceEdit;
import org.eclipse.text.edits.TextEdit;

public class DocumentLifeCycleHandler extends BaseDocumentLifeCycleHandler {

	private JavaClientConnection connection;
	private PreferenceManager preferenceManager;

	private CoreASTProvider sharedASTProvider;

	public DocumentLifeCycleHandler(JavaClientConnection connection, PreferenceManager preferenceManager, ProjectsManager projectsManager, boolean delayValidation) {
		super(delayValidation);
		this.connection = connection;
		this.preferenceManager = preferenceManager;
		this.sharedASTProvider = CoreASTProvider.getInstance();
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
			if (filePath != null) {
				Collection<IPath> rootPaths = preferenceManager.getPreferences().getRootPaths();
				Optional<IPath> belongedRootPath = rootPaths.stream().filter(rootPath -> rootPath.isPrefixOf(filePath)).findFirst();
				boolean invisibleProjectEnabled = false;
				if (belongedRootPath.isPresent()) {
					IPath rootPath = belongedRootPath.get();
					try {
						invisibleProjectEnabled = InvisibleProjectImporter.loadInvisibleProject(filePath, rootPath, false, new NullProgressMonitor());
					} catch (CoreException e) {
						JavaLanguageServerPlugin.logException("Failed to load invisible project", e);
					}
					if (invisibleProjectEnabled) {
						unit = JDTUtils.resolveCompilationUnit(uri);
					}
				}
				if (!invisibleProjectEnabled) {
					unit = JDTUtils.getFakeCompilationUnit(uri);
				}
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

				IDocument document = JsonRpcHelpers.toDocument(unit.getBuffer());
				edit.apply(document, TextEdit.NONE);

			}
			triggerValidation(unit);
		} catch (JavaModelException | MalformedTreeException | BadLocationException e) {
			JavaLanguageServerPlugin.logException("Error while handling document change. URI: " + uri, e);
		}

		return unit;
	}

	@Override
	public ICompilationUnit handleClosed(DidCloseTextDocumentParams params) {
		ICompilationUnit unit = super.handleClosed(params);
		return unit;
	}

}
