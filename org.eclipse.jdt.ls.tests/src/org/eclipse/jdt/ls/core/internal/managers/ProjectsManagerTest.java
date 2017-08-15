/*******************************************************************************
 * Copyright (c) 2016-2017 Red Hat Inc. and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Red Hat Inc. - initial API and implementation
 *******************************************************************************/
package org.eclipse.jdt.ls.core.internal.managers;

import static org.eclipse.jdt.ls.core.internal.JobHelpers.waitForJobsToComplete;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.net.URI;
import java.nio.file.FileVisitOption;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;
import java.util.List;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.jdt.ls.core.internal.JDTUtils;
import org.eclipse.jdt.ls.core.internal.TestVMType;
import org.eclipse.jdt.ls.core.internal.WorkspaceHelper;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

/**
 * @author Fred Bricon
 *
 */
public class ProjectsManagerTest extends AbstractProjectsManagerBasedTest {

	@BeforeClass
	public static void init() throws CoreException {
		TestVMType.setTestJREAsDefault();
	}

	@Before
	public void setUp() throws CoreException {
		projectsManager.initializeProjects(null, monitor);
		waitForJobsToComplete();
	}

	@Test
	public void testCreateDefaultProject() throws Exception {
		List<IProject> projects = WorkspaceHelper.getAllProjects();
		assertEquals(1, projects.size());
		IProject result = projects.get(0);
		assertNotNull(result);
		assertEquals(projectsManager.getDefaultProject(), result);
		assertTrue("the default project doesn't exist", result.exists());
	}

	@Test
	public void testFakeCompilationUnit() throws Exception {
		String tempDir = System.getProperty("java.io.tmpdir");
		File dir = new File(tempDir, "/test/src/org/eclipse");
		dir.mkdirs();
		File file = new File(dir, "Test.java");
		file.createNewFile();
		URI uri = file.toURI();
		JDTUtils.resolveCompilationUnit(uri);
		IProject project = projectsManager.getDefaultProject();
		IFile iFile = project.getFile("/src/org/eclipse/Test.java");
		assertTrue(iFile.getFullPath().toString() + " doesn't exist.", iFile.exists());
		Path path = Paths.get(tempDir + "/test");
		Files.walk(path, FileVisitOption.FOLLOW_LINKS).sorted(Comparator.reverseOrder()).map(Path::toFile).forEach(File::delete);
	}

}
