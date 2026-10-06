/*******************************************************************************
 * Copyright (c) 2026 IBM Corporation and others.
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

/**
 * Module index for Maven projects.
 *
 * <p>Module discovery is fully lazy: no upfront scan, no XML parsing.
 * When a file is opened, the index walks up the directory tree to find
 * the nearest {@code pom.xml}. The actual project import is delegated
 * to M2E via {@link MavenProjectImporter#importModule}.</p>
 */
public class MavenModuleIndex extends AbstractModuleIndex {

	private static final String POM_XML = "pom.xml";

	/**
	 * Creates a new Maven module index.
	 *
	 * @param workspacePath the workspace root directory
	 */
	public MavenModuleIndex(Path workspacePath) {
		super(workspacePath);
	}

	@Override
	protected boolean hasBuildFile(Path dir) {
		return Files.isRegularFile(dir.resolve(POM_XML));
	}
}
