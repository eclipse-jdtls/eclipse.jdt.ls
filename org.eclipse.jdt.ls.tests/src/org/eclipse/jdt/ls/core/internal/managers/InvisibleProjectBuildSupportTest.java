/*******************************************************************************
 * Copyright (c) 2019 Red Hat Inc. and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Red Hat Inc. - initial API and implementation
 *******************************************************************************/
package org.eclipse.jdt.ls.core.internal.managers;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.eclipse.core.resources.IMarker;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.jobs.IJobChangeEvent;
import org.eclipse.core.runtime.jobs.IJobChangeListener;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.core.runtime.jobs.JobChangeAdapter;
import org.eclipse.jdt.core.IClasspathEntry;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.ls.core.internal.JavaProjectHelper;
import org.eclipse.jdt.ls.core.internal.ResourceUtils;
import org.eclipse.jdt.ls.core.internal.managers.ProjectsManager.CHANGE_TYPE;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.runners.MockitoJUnitRunner;

/**
 * @author Fred Bricon
 *
 */
@RunWith(MockitoJUnitRunner.class)
public class InvisibleProjectBuildSupportTest extends AbstractInvisibleProjectBasedTest {

	@Test
	public void testDynamicLibDetection() throws Exception {
		File projectFolder = createSourceFolderWithMissingLibs("dynamicLibDetection");
		IProject project = importRootFolder(projectFolder, "Test.java");
		List<IMarker> errors = ResourceUtils.getErrorMarkers(project);
		assertEquals("Unexpected errors " + ResourceUtils.toString(errors), 2, errors.size());

		//Add jars to fix compilation errors
		addLibs(projectFolder.toPath());
		Path libPath = projectFolder.toPath().resolve(InvisibleProjectBuildSupport.LIB_FOLDER);

		Path jar = libPath.resolve("foo.jar");
		projectsManager.fileChanged(jar.toUri().toString(), CHANGE_TYPE.CREATED);
		waitForBackgroundJobs();
		{
			IJavaProject javaProject = JavaCore.create(project);
			IClasspathEntry[] classpath = javaProject.getRawClasspath();
			assertEquals("Unexpected classpath:\n" + JavaProjectHelper.toString(classpath), 3, classpath.length);
			assertEquals("foo.jar", classpath[2].getPath().lastSegment());
			assertEquals("foo-sources.jar", classpath[2].getSourceAttachmentPath().lastSegment());
		}

		//remove sources
		Path sources = libPath.resolve("foo-sources.jar");
		Files.deleteIfExists(sources);
		projectsManager.fileChanged(sources.toUri().toString(), CHANGE_TYPE.DELETED);
		waitForBackgroundJobs();
		{
			IJavaProject javaProject = JavaCore.create(project);
			IClasspathEntry[] classpath = javaProject.getRawClasspath();
			assertEquals("Unexpected classpath:\n" + JavaProjectHelper.toString(classpath), 3, classpath.length);
			assertEquals("foo.jar", classpath[2].getPath().lastSegment());
			assertNull(classpath[2].getSourceAttachmentPath());
		}
		assertNoErrors(project);


		//remove lib folder
		Files.deleteIfExists(jar);//lib needs to be empty
		Files.deleteIfExists(libPath);
		projectsManager.fileChanged(libPath.toUri().toString(), CHANGE_TYPE.DELETED);
		waitForBackgroundJobs();
		{
			IJavaProject javaProject = JavaCore.create(project);
			IClasspathEntry[] classpath = javaProject.getRawClasspath();
			assertEquals("Unexpected classpath:\n" + JavaProjectHelper.toString(classpath), 2, classpath.length);
		}
		//back to square 1
		errors = ResourceUtils.getErrorMarkers(project);
		assertEquals("Unexpected errors " + ResourceUtils.toString(errors), 2, errors.size());

	}

	@Test
	public void testDebounceJarDetection() throws Exception {
		File projectFolder = createSourceFolderWithMissingLibs("dynamicLibDetection");
		IProject project = importRootFolder(projectFolder, "Test.java");
		List<IMarker> errors = ResourceUtils.getErrorMarkers(project);
		assertEquals("Unexpected errors " + ResourceUtils.toString(errors), 2, errors.size());

		//Add jars to fix compilation errors
		addLibs(projectFolder.toPath());

		Path libPath = projectFolder.toPath().resolve(InvisibleProjectBuildSupport.LIB_FOLDER);

		int[] jobInvocations = new int[1];
		IJobChangeListener listener = new JobChangeAdapter() {
			@Override
			public void scheduled(IJobChangeEvent event) {
				if (event.getJob() instanceof UpdateClasspathJob) {
					jobInvocations[0] = jobInvocations[0] + 1;
				}
			}
		};
		try {
			Job.getJobManager().addJobChangeListener(listener);
			//Spam the service
			for (int i = 0; i < 50; i++) {
				projectsManager.fileChanged(libPath.resolve("foo.jar").toUri().toString(), CHANGE_TYPE.CREATED);
				projectsManager.fileChanged(libPath.resolve("foo-sources.jar").toUri().toString(), CHANGE_TYPE.CREATED);
				Thread.sleep(5);
			}
			waitForBackgroundJobs();
			assertEquals("Update classpath job should have been invoked once", 1, jobInvocations[0]);
		} finally {
			Job.getJobManager().removeJobChangeListener(listener);
		}

		{
			IJavaProject javaProject = JavaCore.create(project);
			IClasspathEntry[] classpath = javaProject.getRawClasspath();
			assertEquals("Unexpected classpath:\n" + JavaProjectHelper.toString(classpath), 3, classpath.length);
			assertEquals("foo.jar", classpath[2].getPath().lastSegment());
			assertEquals("foo-sources.jar", classpath[2].getSourceAttachmentPath().lastSegment());
		}

	}

}
