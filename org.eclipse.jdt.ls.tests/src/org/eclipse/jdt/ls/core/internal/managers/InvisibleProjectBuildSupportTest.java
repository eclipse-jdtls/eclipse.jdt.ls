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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.io.FileUtils;
import org.eclipse.core.resources.IMarker;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.IPath;
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

	@Test
	public void testReferencedBinariesUpdate() throws Exception {
		File projectFolder = createSourceFolderWithMissingLibs("dynamicLibDetection");
		IProject project = importRootFolder(projectFolder, "Test.java");
		List<IMarker> errors = ResourceUtils.getErrorMarkers(project);
		assertEquals("Unexpected errors " + ResourceUtils.toString(errors), 2, errors.size());

		File originBinary = new File(getSourceProjectDirectory(), "eclipse/source-attachment/foo.jar");
		File originSource = new File(getSourceProjectDirectory(), "eclipse/source-attachment/foo-sources.jar");

		Set<String> include = new HashSet<>();
		Set<String> exclude = new HashSet<>();
		Map<String, IPath> sources = new HashMap<>();

		// Include following jars (by lib/** detection)
		// - /lib/foo.jar
		// - /lib/foo-sources.jar
		File libFolder = Files.createDirectories(projectFolder.toPath().resolve(InvisibleProjectBuildSupport.LIB_FOLDER)).toFile();
		File fooBinary = new File(libFolder, "foo.jar");
		File fooSource = new File(libFolder, "foo-source.jar");
		FileUtils.copyFile(originBinary, fooBinary);
		FileUtils.copyFile(originSource, fooSource);

		// Include following jars (by manually add include)
		// - /bar.jar
		// - /library/bar-src.jar
		File libraryFolder = Files.createDirectories(projectFolder.toPath().resolve("library")).toFile();
		File barBinary = new File(projectFolder, "bar.jar");
		File barSource = new File(libraryFolder, "bar-src.jar");
		FileUtils.copyFile(originBinary, barBinary);
		FileUtils.copyFile(originSource, barSource);
		include.add(barBinary.toString());
		sources.put(barBinary.toString(), new org.eclipse.core.runtime.Path(barSource.toString()));

		// Exclude following jars (by manually add exclude)
		// - /lib/foo.jar
		exclude.add(fooBinary.toString());

		// Before sending requests
		IJavaProject javaProject = JavaCore.create(project);
		int[] jobInvocations = new int[1];
		IJobChangeListener listener = new JobChangeAdapter() {
			@Override
			public void scheduled(IJobChangeEvent event) {
				if (event.getJob() instanceof UpdateClasspathJob) {
					jobInvocations[0] = jobInvocations[0] + 1;
				}
			}
		};

		try { // Send two update request concurrently
			Job.getJobManager().addJobChangeListener(listener);
			projectsManager.fileChanged(fooBinary.toString(), CHANGE_TYPE.CREATED); // Request sent by jdt.ls's lib detection
			UpdateClasspathJob.getInstance().updateClasspath(javaProject, include, exclude, sources); // Request sent by third-party client
			waitForBackgroundJobs();
			assertEquals("Update classpath job should have been invoked once", 1, jobInvocations[0]);
		} finally {
			Job.getJobManager().removeJobChangeListener(listener);
		}

		{
			// The requests sent by `jdt'ls lib detection` and `third-party client` is merged in queue,
			// So client's `exclude: lib/foo.jar` comes into effect to block jdt.ls's `include: lib/foo.jar`
			// This is the way client uses to neutralize the effect of jdt.ls's lib detection
			IClasspathEntry[] classpath = javaProject.getRawClasspath();
			// Check only one jar file is added to classpath (foo.jar is excluded)
			assertEquals("Unexpected classpath:\n" + JavaProjectHelper.toString(classpath), 3, classpath.length);
			// Check the only added jar is bar.jar
			assertEquals("bar.jar", classpath[2].getPath().lastSegment());
			assertEquals("bar-src.jar", classpath[2].getSourceAttachmentPath().lastSegment());
			// Check the source of bar.jar is in /library folder
			assertEquals("library", classpath[2].getSourceAttachmentPath().removeLastSegments(1).lastSegment());
		}
	}
}
