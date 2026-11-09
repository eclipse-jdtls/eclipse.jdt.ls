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
import org.eclipse.jdt.ls.core.internal.jsh.JshSnippetWrapper.JshWrapResult;
import org.eclipse.jdt.ls.core.internal.managers.ProjectsManager;

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

	public void didChange(String uri, String content) {
		JshDocument document = documents.get(uri);
		if (document == null) {
			// Shouldn't normally happen (didOpen should always precede didChange), but don't crash
			// on an out-of-order/duplicate notification - just establish the document now.
			didOpen(uri, content);
			return;
		}
		document.setContent(content);
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
		private volatile JshWrapResult wrapResult;

		private JshDocument(ICompilationUnit workingCopy) {
			this.workingCopy = workingCopy;
		}

		void setContent(String content) {
			JshWrapResult result = JshSnippetWrapper.wrap(content);
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
