/*******************************************************************************
 * Copyright (c) 2021 Microsoft Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *     Microsoft Corporation - initial API and implementation
 *******************************************************************************/

package org.eclipse.jdt.ls.core.internal;

import static org.junit.Assert.assertTrue;

import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

import org.eclipse.core.internal.utils.FileUtil;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.OperationCanceledException;
import org.eclipse.jdt.ls.core.internal.managers.GradleProjectImporter;
import org.eclipse.jdt.ls.core.internal.managers.MavenProjectImporter;
import org.junit.Test;

public class AbstractProjectImporterTest {
	@Test
	public void testExcludeNestedConfigurations() {
		ProjectImporter importer = new ProjectImporter();
		Collection<IPath> configurationPaths = new ArrayList<>();
		configurationPaths.add(FileUtil.toPath(new File("projects", "gradle").toPath().resolve("subprojects/project1/build.gradle").toUri()));
		configurationPaths.add(FileUtil.toPath(new File("projects", "gradle").toPath().resolve("subprojects/project2/build.gradle").toUri()));
		configurationPaths.add(FileUtil.toPath(new File("projects", "gradle").toPath().resolve("subprojects/build.gradle").toUri()));
		configurationPaths.add(FileUtil.toPath(new File("projects", "gradle").toPath().resolve("subprojects/settings.gradle").toUri()));
		
		Collection<Path> paths = importer.findProjectPath(configurationPaths, Arrays.asList(
			GradleProjectImporter.BUILD_GRADLE_DESCRIPTOR,
			GradleProjectImporter.SETTINGS_GRADLE_DESCRIPTOR
		), false);

		assertTrue(paths.size() == 1);
		for (Path path : paths) {
			assertTrue(path.endsWith("subprojects"));
		}
	}

	@Test
	public void testIncludeNestedConfigurations() {
		ProjectImporter importer = new ProjectImporter();
		Collection<IPath> configurationPaths = new ArrayList<>();
		configurationPaths.add(FileUtil.toPath(new File("projects", "maven").toPath().resolve("multimodule3/pom.xml").toUri()));
		configurationPaths.add(FileUtil.toPath(new File("projects", "maven").toPath().resolve("multimodule3/module1/pom.xml").toUri()));
		configurationPaths.add(FileUtil.toPath(new File("projects", "maven").toPath().resolve("multimodule3/module2/pom.xml").toUri()));
		Collection<Path> paths = importer.findProjectPath(configurationPaths, Arrays.asList(
			MavenProjectImporter.POM_FILE
		), true);

		assertTrue(paths.size() == 3);
	}
}

class ProjectImporter extends AbstractProjectImporter {

	@Override
	public boolean applies(IProgressMonitor monitor) throws OperationCanceledException, CoreException {
		return false;
	}

	@Override
	public void importToWorkspace(IProgressMonitor monitor) throws OperationCanceledException, CoreException {
	}

	@Override
	public void reset() {
	}

	// make public for test purpose
	public Collection<Path> findProjectPath(Collection<IPath> projectConfigurations, List<String> names, boolean includeNested) {
		return findProjectPathByConfigurationName(projectConfigurations, names, includeNested);
	}
}
