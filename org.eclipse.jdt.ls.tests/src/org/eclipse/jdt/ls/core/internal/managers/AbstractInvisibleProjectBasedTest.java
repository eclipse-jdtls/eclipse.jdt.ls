/*******************************************************************************
 * Copyright (c) 2019 Red Hat Inc. and others.
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
package org.eclipse.jdt.ls.core.internal.managers;

import java.io.File;
import java.nio.file.Files;

import org.apache.commons.io.FileUtils;

/**
 * Base class for Invisible project tests.
 *
 * @author Fred Bricon
 *
 */
public abstract class AbstractInvisibleProjectBasedTest extends AbstractProjectsManagerBasedTest {

	/**
	 * Creates a temporary folder prefixed with <code>name</code> containing sources
	 * and jars under a lib directory.
	 */
	protected File createSourceFolderWithLibs(String name) throws Exception {
		return createSourceFolderWithLibs(name, true);
	}

	/**
	 * Creates a temporary folder prefixed with <code>name</code> containing sources
	 * but without its required jars.
	 */
	protected File createSourceFolderWithMissingLibs(String name) throws Exception {
		return createSourceFolderWithLibs(name, false);
	}

	protected File createSourceFolderWithLibs(String name, boolean addLibs) throws Exception {
		return createSourceFolderWithLibs(name, null, addLibs);
	}

	protected File createSourceFolderWithLibs(String name, String srcDir, boolean addLibs) throws Exception {
		java.nio.file.Path projectPath = Files.createTempDirectory(name);
		File projectFolder = projectPath.toFile();
		File sourceFolder;
		if (org.apache.commons.lang3.StringUtils.isBlank(srcDir)) {
			sourceFolder = projectFolder;
		} else {
			sourceFolder = new File(projectFolder, srcDir);
		}
		FileUtils.copyDirectory(new File(getSourceProjectDirectory(), "eclipse/source-attachment/src"), sourceFolder);
		if (addLibs) {
			addLibs(projectPath);
		}
		return projectFolder;
	}

	protected void addLibs(java.nio.file.Path projectPath) throws Exception {
		java.nio.file.Path libPath = Files.createDirectories(projectPath.resolve(InvisibleProjectBuildSupport.LIB_FOLDER));
		File libFile = libPath.toFile();
		FileUtils.copyFileToDirectory(new File(getSourceProjectDirectory(), "eclipse/source-attachment/foo.jar"), libFile);
		FileUtils.copyFileToDirectory(new File(getSourceProjectDirectory(), "eclipse/source-attachment/foo-sources.jar"), libFile);
	}


}
