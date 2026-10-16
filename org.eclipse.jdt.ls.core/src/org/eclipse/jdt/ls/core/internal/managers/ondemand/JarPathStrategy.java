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
 * Strategy that extracts the artifact name from the JAR filename and matches
 * it against scanned module artifactIds. Fast but fragile — the JAR name
 * may not match the workspace module's artifactId.
 *
 * <p>Perf: 4/5 — string matching + one {@code Files.isRegularFile()} per match.<br>
 * Reliability: 2/5 — depends on naming conventions.</p>
 */
public class JarPathStrategy implements IClassFileSourceStrategy {

	@Override
	public Path findSource(IClassFile classFile, String relativePath, ModuleScanner scanner) {
		try {
			IPackageFragmentRoot root = (IPackageFragmentRoot) classFile.getAncestor(IJavaElement.PACKAGE_FRAGMENT_ROOT);
			if (root == null) {
				return null;
			}
			org.eclipse.core.runtime.IPath jarPath = root.getPath();
			if (jarPath == null) {
				return null;
			}
			String jarName = jarPath.lastSegment();
			if (jarName == null || !jarName.endsWith(".jar")) {
				return null;
			}
			String artifactId = extractArtifactId(jarName);
			if (artifactId == null) {
				return null;
			}
			for (ModuleInfo module : scanner.getModules()) {
				if (artifactId.equals(module.artifactId())) {
					for (Path srcFolder : module.sourceFolders()) {
						Path candidate = srcFolder.resolve(relativePath);
						if (Files.isRegularFile(candidate)) {
							return candidate;
						}
					}
				}
			}
		} catch (Exception e) {
			// Fall through to next strategy
		}
		return null;
	}

	/**
	 * Extracts the artifact ID from a JAR filename by stripping the version suffix.
	 * Example: "quarkus-core-3.0.0.Final.jar" → "quarkus-core"
	 */
	static String extractArtifactId(String jarName) {
		String name = jarName.substring(0, jarName.length() - 4); // strip ".jar"
		// Find the last segment that starts with a digit preceded by a dash
		int i = name.length() - 1;
		while (i > 0) {
			if (name.charAt(i) == '-' && i + 1 < name.length() && Character.isDigit(name.charAt(i + 1))) {
				return name.substring(0, i);
			}
			i--;
		}
		return name;
	}

	@Override
	public String getName() {
		return "JarPath";
	}
}
