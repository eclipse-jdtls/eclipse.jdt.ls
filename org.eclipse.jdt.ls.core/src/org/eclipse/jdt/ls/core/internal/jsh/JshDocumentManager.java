/*******************************************************************************
 * Copyright (c) 2026 Red Hat Inc. and others.
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
package org.eclipse.jdt.ls.core.internal.jsh;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IPackageFragment;
import org.eclipse.jdt.core.IPackageFragmentRoot;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.WorkingCopyOwner;
import org.eclipse.jdt.ls.core.internal.JavaLanguageServerPlugin;
import org.eclipse.jdt.ls.core.internal.ProjectUtils;
import org.eclipse.jdt.ls.core.internal.ResourceUtils;
import org.eclipse.jdt.ls.core.internal.handlers.JsonRpcHelpers;
import org.eclipse.jdt.ls.core.internal.jsh.JshSnippetWrapper.JshWrapResult;
import org.eclipse.jdt.ls.core.internal.managers.ProjectsManager;
import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.Document;
import org.eclipse.jface.text.IDocument;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.TextDocumentContentChangeEvent;
import org.eclipse.text.edits.DeleteEdit;
import org.eclipse.text.edits.InsertEdit;
import org.eclipse.text.edits.MalformedTreeException;
import org.eclipse.text.edits.ReplaceEdit;
import org.eclipse.text.edits.TextEdit;

/**
 * Owns the lifecycle of the synthetic working copies backing open .jsh files: one per URI,
 * created on open, re-wrapped and reconciled on change, discarded on close.
 * <p>
 * This only maintains the synthetic working copy and its offset mapping; it does not yet wire
 * into completion, diagnostics, or hover (see the later phases tracked on
 * https://github.com/eclipse-jdtls/eclipse.jdt.ls/issues/3973).
 */
public class JshDocumentManager {

	private static final WorkingCopyOwner OWNER = new WorkingCopyOwner() {
	};

	/**
	 * Every open .jsh file gets its own synthetic compilation unit name so concurrently open .jsh
	 * files never collide in the same package.
	 */
	private static final AtomicLong COUNTER = new AtomicLong();

	private final ConcurrentHashMap<String, JshDocument> documents = new ConcurrentHashMap<>();

	/**
	 * Opens (or re-opens) the .jsh document at the given URI, creating its backing synthetic
	 * working copy against the nearest enclosing real project's classpath (falling back to the
	 * shared default project if the file isn't under any real project).
	 */
	public void didOpen(String uri, String content) {
		try {
			IJavaProject project = resolveProject(uri);
			IPackageFragment pkg = findScratchpadPackage(project);
			if (pkg == null) {
				JavaLanguageServerPlugin.logError("Could not find a source package fragment to host .jsh scratchpad for " + uri);
				return;
			}
			String name = JshSnippetWrapper.SCRATCHPAD_CLASS_NAME + "_" + COUNTER.incrementAndGet() + ".java";
			ICompilationUnit cu = pkg.getCompilationUnit(name).getWorkingCopy(OWNER, new NullProgressMonitor());
			JshDocument document = new JshDocument(cu);
			document.setContent(content);
			documents.put(uri, document);
		} catch (JavaModelException e) {
			JavaLanguageServerPlugin.logException("Failed to open .jsh document " + uri, e);
		}
	}

	/**
	 * Applies a batch of (possibly incremental, range-based) content changes to the .jsh document
	 * at the given URI, the same way changes to a normal open Java file are applied - necessary
	 * since the LSP client negotiates one text-document sync mode for the whole server, so .jsh
	 * files get the same incremental change events real .java files do, not whole-file replacements.
	 */
	public void didChange(String uri, List<TextDocumentContentChangeEvent> changes) {
		JshDocument document = documents.get(uri);
		if (document == null) {
			// Shouldn't normally happen (didOpen should always precede didChange), but don't crash
			// on an out-of-order/duplicate notification - start from nothing and apply the changes.
			didOpen(uri, "");
			document = documents.get(uri);
			if (document == null) {
				return;
			}
		}
		document.applyChanges(changes);
	}

	public void didClose(String uri) {
		JshDocument document = documents.remove(uri);
		if (document != null) {
			document.dispose();
		}
	}

	/**
	 * @return the current synthetic document for the given URI, or {@code null} if it isn't open
	 *         (or isn't a .jsh document at all)
	 */
	public JshDocument get(String uri) {
		return documents.get(uri);
	}

	private static IJavaProject resolveProject(String uri) {
		IPath filePath = ResourceUtils.canonicalFilePathFromURI(uri);
		IProject bestMatch = null;
		if (filePath != null) {
			for (IProject project : ResourcesPlugin.getWorkspace().getRoot().getProjects()) {
				if (Objects.equals(project, ProjectsManager.getDefaultProject()) || !ProjectUtils.isJavaProject(project)) {
					continue;
				}
				IPath projectLocation = project.getLocation();
				if (projectLocation != null && projectLocation.isPrefixOf(filePath)
						&& (bestMatch == null || projectLocation.segmentCount() > bestMatch.getLocation().segmentCount())) {
					bestMatch = project;
				}
			}
		}
		return JavaCore.create(bestMatch != null ? bestMatch : ProjectsManager.getDefaultProject());
	}

	private static IPackageFragment findScratchpadPackage(IJavaProject project) throws JavaModelException {
		for (IPackageFragmentRoot root : project.getPackageFragmentRoots()) {
			if (root.getKind() == IPackageFragmentRoot.K_SOURCE) {
				return root.getPackageFragment("");
			}
		}
		return null;
	}

	/**
	 * A single open .jsh file's synthetic working copy plus the offset mapping for its current
	 * content.
	 */
	public static final class JshDocument {

		private final ICompilationUnit workingCopy;
		private final IDocument rawDocument = new Document();
		private volatile JshWrapResult wrapResult;

		private JshDocument(ICompilationUnit workingCopy) {
			this.workingCopy = workingCopy;
		}

		void setContent(String content) {
			rawDocument.set(content == null ? "" : content);
			pushToSyntheticBuffer();
		}

		/**
		 * Applies each change event to {@link #rawDocument} in order - mirroring
		 * {@code BaseDocumentLifeCycleHandler.handleChanged}'s handling for real Java files - then
		 * pushes the resulting raw content, re-wrapped, into the synthetic working copy's buffer.
		 */
		void applyChanges(List<TextDocumentContentChangeEvent> changes) {
			try {
				for (TextDocumentContentChangeEvent changeEvent : changes) {
					Range range = changeEvent.getRange();
					int startOffset;
					int length;
					if (range != null) {
						Position start = range.getStart();
						Position end = range.getEnd();
						startOffset = JsonRpcHelpers.toOffset(rawDocument, start.getLine(), start.getCharacter());
						int endOffset = JsonRpcHelpers.toOffset(rawDocument, end.getLine(), end.getCharacter());
						length = endOffset - startOffset;
					} else {
						// No range means the whole document was replaced.
						startOffset = 0;
						length = rawDocument.getLength();
					}

					String text = changeEvent.getText();
					TextEdit edit;
					if (length == 0) {
						edit = new InsertEdit(startOffset, text);
					} else if (text.isEmpty()) {
						edit = new DeleteEdit(startOffset, length);
					} else {
						edit = new ReplaceEdit(startOffset, length, text);
					}
					edit.apply(rawDocument, TextEdit.NONE);
				}
				pushToSyntheticBuffer();
			} catch (BadLocationException | MalformedTreeException e) {
				JavaLanguageServerPlugin.logException("Failed to apply .jsh document changes", e);
			}
		}

		private void pushToSyntheticBuffer() {
			JshWrapResult result = JshSnippetWrapper.wrap(rawDocument.get());
			try {
				workingCopy.getBuffer().setContents(result.getWrappedSource());
				workingCopy.makeConsistent(new NullProgressMonitor());
				this.wrapResult = result;
			} catch (JavaModelException e) {
				JavaLanguageServerPlugin.logException("Failed to update .jsh synthetic working copy", e);
			}
		}

		void dispose() {
			try {
				workingCopy.discardWorkingCopy();
			} catch (JavaModelException e) {
				JavaLanguageServerPlugin.logException("Failed to discard .jsh synthetic working copy", e);
			}
		}

		public ICompilationUnit getWorkingCopy() {
			return workingCopy;
		}

		public JshWrapResult getWrapResult() {
			return wrapResult;
		}

	}

}
