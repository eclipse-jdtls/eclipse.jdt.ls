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

/**
 * Strategy that searches the pre-scanned module list built by the background
 * {@link ModuleScanner}. Fast and reliable when the scan is complete.
 *
 * <p>Perf: 4/5 — N × {@code Files.isRegularFile()} (N = number of modules).<br>
 * Reliability: 5/5 — checks actual file existence.</p>
 */
public class ScannedModulesStrategy implements IClassFileSourceStrategy {

	@Override
	public Path findSource(IClassFile classFile, String relativePath, ModuleScanner scanner) {
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

	@Override
	public String getName() {
		return "ScannedModules";
	}
}
