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
package org.eclipse.jdt.ls.core.internal.managers;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * Source folder index for Maven projects with lazy module discovery.
 *
 * <p>No upfront scan, no XML parsing: modules are discovered lazily
 * when a file is opened by walking up the directory tree to find
 * the nearest {@code pom.xml}. The actual import is delegated to M2E.</p>
 *
 * <p>Module ID: the absolute normalized directory path.</p>
 */
public class MavenSourceFolderIndex extends AbstractSourceFolderIndex {

	public MavenSourceFolderIndex(Path workspacePath) {
		super(workspacePath);
	}

	@Override
	public List<String> collectTargetsToImport(String targetId) {
		if (targetId == null || importedModules.contains(targetId) || !moduleToDir.containsKey(targetId)) {
			return Collections.emptyList();
		}
		List<String> result = new ArrayList<>();
		result.add(targetId);
		// Include direct workspace dependencies (from mbt.json dependency graph)
		// so that M2E's project references resolve and compilation succeeds.
		// Without this, M2E's MavenRuntimeClasspathProvider creates CPE_PROJECT
		// entries for reactor modules that don't exist as Eclipse projects.
		Set<String> deps = dependsOn.get(targetId);
		if (deps != null) {
			for (String dep : deps) {
				if (!importedModules.contains(dep) && moduleToDir.containsKey(dep)) {
					result.add(dep);
				}
			}
		}
		return result;
	}

	@Override
	protected boolean hasBuildFile(Path dir) {
		return Files.isRegularFile(dir.resolve("pom.xml"));
	}

	@Override
	protected String discoverModule(Path dir) {
		if (!hasBuildFile(dir)) {
			return null;
		}
		String moduleId = dir.toAbsolutePath().normalize().toString();
		registerModule(moduleId, dir);
		return moduleId;
	}
}
