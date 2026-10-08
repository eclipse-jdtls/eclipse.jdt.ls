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
 *     Angelo ZERR - initial API and implementation
 *******************************************************************************/
package org.eclipse.jdt.ls.core.internal.managers.ondemand;

import java.nio.file.Files;
import java.nio.file.Path;

import org.eclipse.jdt.core.IClassFile;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IPackageFragmentRoot;

/**
 * Strategy that uses the JAR's source attachment path to locate the source file.
 * Instant when available, but source attachment is often {@code null} or points
 * to a {@code -sources.jar} rather than a workspace directory.
 *
 * <p>Perf: 5/5 — single path resolution.<br>
 * Reliability: 2/5 — source attachment rarely points to workspace source.</p>
 */
public class SourceAttachmentStrategy implements IClassFileSourceStrategy {

	@Override
	public Path findSource(IClassFile classFile, String relativePath, ModuleScanner scanner) {
		try {
			IPackageFragmentRoot root = (IPackageFragmentRoot) classFile.getAncestor(IJavaElement.PACKAGE_FRAGMENT_ROOT);
			if (root == null) {
				return null;
			}
			org.eclipse.core.runtime.IPath sourceAttachment = root.getSourceAttachmentPath();
			if (sourceAttachment == null) {
				return null;
			}
			Path sourceDir = Path.of(sourceAttachment.toOSString());
			if (!Files.isDirectory(sourceDir)) {
				return null;
			}
			Path candidate = sourceDir.resolve(relativePath);
			if (Files.isRegularFile(candidate)) {
				return candidate;
			}
		} catch (Exception e) {
			// Fall through to next strategy
		}
		return null;
	}

	@Override
	public String getName() {
		return "SourceAttachment";
	}
}
