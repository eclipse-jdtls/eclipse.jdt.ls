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
 * Strategy that searches the pre-scanned module list built by the background
 * {@link ModuleScanner}. Fast and reliable when the scan is complete.
 */
public class ScannedModulesStrategy implements IClassFileSourceStrategy {

	@Override
	public Path findSource(IClassFile classFile, String relativePath, ModuleScanner scanner) {
		// Prefer the module whose artifactId matches the JAR name
		String jarArtifactId = getJarArtifactId(classFile);
		if (jarArtifactId != null) {
			for (ModuleInfo module : scanner.getModules()) {
				if (jarArtifactId.equals(module.artifactId())) {
					for (Path srcFolder : module.sourceFolders()) {
						Path candidate = srcFolder.resolve(relativePath);
						if (Files.isRegularFile(candidate)) {
							return candidate;
						}
					}
				}
			}
		}
		// Fallback: any module containing the source file
		for (ModuleInfo module : scanner.getModules()) {
			for (Path srcFolder : module.sourceFolders()) {
				Path candidate = srcFolder.resolve(relativePath);
				if (Files.isRegularFile(candidate)) {
					return candidate;
				}
			}
		}
		return null;
	}

	private static String getJarArtifactId(IClassFile classFile) {
		try {
			IPackageFragmentRoot root = (IPackageFragmentRoot) classFile.getAncestor(
					IJavaElement.PACKAGE_FRAGMENT_ROOT);
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
			return JarPathStrategy.extractArtifactId(jarName);
		} catch (Exception e) {
			return null;
		}
	}

	@Override
	public String getName() {
		return "ScannedModules";
	}
}
