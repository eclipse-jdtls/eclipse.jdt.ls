/*******************************************************************************
 * Copyright (c) 2019-2021 Red Hat Inc. and others.
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

import static org.eclipse.jdt.ls.core.internal.JsonMessageHelper.getParams;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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
import org.eclipse.jdt.ls.core.internal.ClassFileUtil;
import org.eclipse.jdt.ls.core.internal.DependencyUtil;
import org.eclipse.jdt.ls.core.internal.JavaProjectHelper;
import org.eclipse.jdt.ls.core.internal.JobHelpers;
import org.eclipse.jdt.ls.core.internal.ProjectUtils;
import org.eclipse.jdt.ls.core.internal.ResourceUtils;
import org.eclipse.jdt.ls.core.internal.handlers.HoverHandler;
import org.eclipse.jdt.ls.core.internal.handlers.HoverHandlerTest;
import org.eclipse.jdt.ls.core.internal.handlers.NavigateToDefinitionHandler;
import org.eclipse.jdt.ls.core.internal.managers.ProjectsManager.CHANGE_TYPE;
import org.eclipse.jdt.ls.core.internal.preferences.Preferences;
import org.eclipse.jdt.ls.core.internal.preferences.Preferences.ReferencedLibraries;
import org.eclipse.lsp4j.Hover;
import org.eclipse.lsp4j.Location;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.eclipse.lsp4j.TextDocumentPositionParams;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;

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
	public void testManuallyReferenceLibraries() throws Exception {
		File projectFolder = createSourceFolderWithMissingLibs("dynamicLibDetection");
		IProject project = importRootFolder(projectFolder, "Test.java");
		List<IMarker> errors = ResourceUtils.getErrorMarkers(project);
		assertEquals("Unexpected errors " + ResourceUtils.toString(errors), 2, errors.size());

		File originBinary = new File(getSourceProjectDirectory(), "eclipse/source-attachment/foo.jar");
		File originSource = new File(getSourceProjectDirectory(), "eclipse/source-attachment/foo-sources.jar");

		Set<String> include = new HashSet<>();
		Set<String> exclude = new HashSet<>();
		Map<String, String> sources = new HashMap<>();

		// Include following jars (by lib/** detection)
		// - /lib/foo.jar
		// - /lib/foo-sources.jar
		File libFolder = Files.createDirectories(projectFolder.toPath().resolve(InvisibleProjectBuildSupport.LIB_FOLDER)).toFile();
		File fooBinary = new File(libFolder, "foo.jar");
		File fooSource = new File(libFolder, "foo-sources.jar");
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
		sources.put(barBinary.toString(), barSource.toString());

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
			projectsManager.fileChanged(fooBinary.toURI().toString(), CHANGE_TYPE.CREATED); // Request sent by jdt.ls's lib detection
			UpdateClasspathJob.getInstance().updateClasspath(javaProject, include, exclude, sources); // Request sent by third-party client
			waitForBackgroundJobs();
			assertEquals("Update classpath job should have been invoked once", 1, jobInvocations[0]);
		} finally {
			Job.getJobManager().removeJobChangeListener(listener);
		}

		{
			// The requests sent by `fileChanged` and `updateClasspath` is merged in queue,
			// So latter's `exclude: lib/foo.jar` comes into effect to block former's `include: lib/foo.jar`
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

	@Test
	public void testVariableReferenceLibraries() throws Exception {
		ReferencedLibraries libraries = new ReferencedLibraries();
		libraries.getInclude().add("~/lib/foo.jar");
		libraries.getExclude().add("~/lib/bar.jar");
		libraries.getSources().put("~/library/bar.jar", "~/library/sources/bar-src.jar");
		assertTrue(libraries.getInclude().iterator().next().startsWith(System.getProperty("user.home")));
		assertTrue(libraries.getExclude().iterator().next().startsWith(System.getProperty("user.home")));
		libraries.getSources().forEach((k, v) -> {
			assertTrue(k.startsWith(System.getProperty("user.home")));
			assertTrue(v.startsWith(System.getProperty("user.home")));
		});
		libraries = new ReferencedLibraries();
		libraries.getInclude().add("${java.home}/lib/foo.jar");
		libraries.getExclude().add("${java.home}/lib/bar.jar");
		libraries.getSources().put("${java.home}/library/bar.jar", "${java.home}/library/sources/bar-src.jar");
		assertTrue(libraries.getInclude().iterator().next().startsWith(System.getProperty("java.home")));
		assertTrue(libraries.getExclude().iterator().next().startsWith(System.getProperty("java.home")));
		libraries.getSources().forEach((k, v) -> {
			assertTrue(k.startsWith(System.getProperty("java.home")));
			assertTrue(v.startsWith(System.getProperty("java.home")));
		});
		libraries = new ReferencedLibraries();
		libraries.getInclude().add("${foo}");
		assertTrue(libraries.getInclude().iterator().next().equals("${foo}"));
	}

	@Test
	public void testDynamicReferenceLibraries() throws Exception {
		File projectFolder = createSourceFolderWithMissingLibs("dynamicLibDetection");
		IProject project = importRootFolder(projectFolder, "Test.java");
		List<IMarker> errors = ResourceUtils.getErrorMarkers(project);
		assertEquals("Unexpected errors " + ResourceUtils.toString(errors), 2, errors.size());

		ReferencedLibraries libraries = new ReferencedLibraries();
		libraries.getInclude().add("lib/foo.jar");
		libraries.getInclude().add("library/**/*.jar");
		libraries.getExclude().add("library/sources/**");
		libraries.getSources().put("library/bar.jar", "library/sources/bar-src.jar");
		preferenceManager.getPreferences().setReferencedLibraries(libraries);

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

		File originBinary = new File(getSourceProjectDirectory(), "eclipse/source-attachment/foo.jar");
		File originSource = new File(getSourceProjectDirectory(), "eclipse/source-attachment/foo-sources.jar");
		File libFolder = Files.createDirectories(projectFolder.toPath().resolve("lib")).toFile();
		File libraryFolder = Files.createDirectories(projectFolder.toPath().resolve("library")).toFile();
		File sourcesFolder = Files.createDirectories(libraryFolder.toPath().resolve("sources")).toFile();

		try {
			Job.getJobManager().addJobChangeListener(listener);

			{
				// Include following jars (with detected source jar)
				// - /lib/foo.jar
				// - /lib/foo-sources.jar
				File fooBinary = new File(libFolder, "foo.jar");
				File fooSource = new File(libFolder, "foo-sources.jar");
				FileUtils.copyFile(originBinary, fooBinary);
				FileUtils.copyFile(originSource, fooSource);
				projectsManager.fileChanged(fooBinary.toURI().toString(), CHANGE_TYPE.CREATED);
				waitForBackgroundJobs();
				IClasspathEntry[] classpath = javaProject.getRawClasspath();
				Optional<IClasspathEntry> fooEntry = Arrays.stream(classpath).filter(c -> c.getPath().lastSegment().equals("foo.jar")).findFirst();
				assertTrue("Cannot find foo binary", fooEntry.isPresent());
				assertEquals("Update classpath job should have been invoked 1 times", 1, jobInvocations[0]);
				assertEquals("Unexpected classpath:\n" + JavaProjectHelper.toString(classpath), 3, classpath.length);
				assertEquals("foo.jar", fooEntry.get().getPath().lastSegment());
				assertEquals("foo-sources.jar", fooEntry.get().getSourceAttachmentPath().lastSegment());
			}

			{
				// Include following jars (with manually addding source map):
				// - /library/bar.jar
				// - /library/sources/bar-src.jar
				File barBinary = new File(libraryFolder, "bar.jar");
				File barSource = new File(sourcesFolder, "bar-src.jar");
				FileUtils.copyFile(originBinary, barBinary);
				FileUtils.copyFile(originSource, barSource);
				projectsManager.fileChanged(barBinary.toURI().toString(), CHANGE_TYPE.CREATED);
				waitForBackgroundJobs();
				IClasspathEntry[] classpath = javaProject.getRawClasspath();
				Optional<IClasspathEntry> barEntry = Arrays.stream(classpath).filter(c -> c.getPath().lastSegment().equals("bar.jar")).findFirst();
				assertTrue("Cannot find bar binary", barEntry.isPresent());
				assertEquals("Update classpath job should have been invoked 2 times", 2, jobInvocations[0]);
				assertEquals("Unexpected classpath:\n" + JavaProjectHelper.toString(classpath), 4, classpath.length);
				assertEquals("bar.jar", barEntry.get().getPath().lastSegment());
				assertEquals("bar-src.jar", barEntry.get().getSourceAttachmentPath().lastSegment());
			}

			{
				// Include following jars (will be excluded):
				// - /library/sources/exclude.jar
				// - /library/sources/exclude-sources.jar
				File excludeBinary = new File(sourcesFolder, "exclude.jar");
				File excludeSource = new File(sourcesFolder, "exclude-sources.jar");
				FileUtils.copyFile(originBinary, excludeBinary);
				FileUtils.copyFile(originSource, excludeSource);
				projectsManager.fileChanged(excludeBinary.toURI().toString(), CHANGE_TYPE.CREATED); // won't send update request
				waitForBackgroundJobs();
				assertEquals("Update classpath job should still have been invoked 2 times", 2, jobInvocations[0]);
				assertEquals("Classpath length should still be 4", 4, javaProject.getRawClasspath().length);
			}
		} finally {
			Job.getJobManager().removeJobChangeListener(listener);
		}
	}

	@Test
	@SuppressWarnings("serial")
	public void testImportReferencedLibrariesConfiguration() throws Exception {
		{ // Test import of configuration without specifying referenced libraries
			Map<String, Object> configuration = new HashMap<>();
			Preferences preferences = Preferences.createFrom(configuration);
			ReferencedLibraries libraries = Preferences.JAVA_PROJECT_REFERENCED_LIBRARIES_DEFAULT;
			assertEquals("Configuration with no corresponding field", libraries, preferences.getReferencedLibraries());
		}

		{ // Test import of referenced libraries with a shortcut list
			List<String> include = Arrays.asList("libraries/**/*.jar");
			Map<String, Object> configuration = new HashMap<>();
			configuration.put(Preferences.JAVA_PROJECT_REFERENCED_LIBRARIES_KEY, include);
			Preferences preferences = Preferences.createFrom(configuration);
			ReferencedLibraries libraries = new ReferencedLibraries(new HashSet<>(include));
			assertEquals("Configuration with shortcut include array", libraries, preferences.getReferencedLibraries());
		}

		{ // Test import of referenced libraries object with include
			List<String> include = Arrays.asList("libraries/**/*.jar");
			Map<String, Object> configuration = new HashMap<>();
			configuration.put(Preferences.JAVA_PROJECT_REFERENCED_LIBRARIES_KEY, new HashMap<String, Object>() {{
				put("include", include);
			}});
			Preferences preferences = Preferences.createFrom(configuration);
			ReferencedLibraries libraries = new ReferencedLibraries(new HashSet<>(include), new HashSet<>(), new HashMap<>());
			assertEquals("Configuration with include", libraries, preferences.getReferencedLibraries());
		}

		{ // Test import of referenced libraries object with include and exclude
			List<String> include = Arrays.asList("libraries/**/*.jar");
			List<String> exclude = Arrays.asList("libraries/sources/**");
			Map<String, Object> configuration = new HashMap<>();
			configuration.put(Preferences.JAVA_PROJECT_REFERENCED_LIBRARIES_KEY, new HashMap<String, Object>() {{
				put("include", include);
				put("exclude", exclude);
			}});
			Preferences preferences = Preferences.createFrom(configuration);
			ReferencedLibraries libraries = new ReferencedLibraries(new HashSet<>(include), new HashSet<>(exclude), new HashMap<>());
			assertEquals("Configuration with include and exclude", libraries, preferences.getReferencedLibraries());
		}

		{ // Test import of referenced libraries object with include and sources
			List<String> include = Arrays.asList("libraries/**/*.jar");
			Map<String, String> sources = new HashMap<>() {{
				put("libraries/foo.jar", "libraries/foo-src.jar");
			}};
			Map<String, Object> configuration = new HashMap<>();
			configuration.put(Preferences.JAVA_PROJECT_REFERENCED_LIBRARIES_KEY, new HashMap<String, Object>() {{
				put("include", include);
				put("sources", sources);
			}});
			Preferences preferences = Preferences.createFrom(configuration);
			ReferencedLibraries libraries = new ReferencedLibraries(new HashSet<>(include), new HashSet<>(), sources);
			assertEquals("Configuration with include and sources", libraries, preferences.getReferencedLibraries());
		}

		{ // Test import of referenced libraries object with include, exclude and sources
			List<String> include = Arrays.asList("libraries/**/*.jar");
			List<String> exclude = Arrays.asList("libraries/sources/**");
			Map<String, String> sources = new HashMap<>() {{
				put("libraries/foo.jar", "libraries/foo-src.jar");
			}};
			Map<String, Object> configuration = new HashMap<>();
			configuration.put(Preferences.JAVA_PROJECT_REFERENCED_LIBRARIES_KEY, new HashMap<String, Object>() {{
				put("include", include);
				put("exclude", exclude);
				put("sources", sources);
			}});
			Preferences preferences = Preferences.createFrom(configuration);
			ReferencedLibraries libraries = new ReferencedLibraries(new HashSet<>(include), new HashSet<>(exclude), sources);
			assertEquals("Configuration with include, exclude and sources", libraries, preferences.getReferencedLibraries());
		}

		{ // Test import of referenced libraries with exclude and sources
			List<String> exclude = Arrays.asList("libraries/sources/**");
			Map<String, String> sources = new HashMap<>() {{
				put("libraries/foo.jar", "libraries/foo-src.jar");
			}};
			Map<String, Object> configuration = new HashMap<>();
			configuration.put(Preferences.JAVA_PROJECT_REFERENCED_LIBRARIES_KEY, new HashMap<String, Object>() {{
				put("exclude", exclude);
				put("sources", sources);
			}});
			Preferences preferences = Preferences.createFrom(configuration);
			ReferencedLibraries libraries = new ReferencedLibraries(new HashSet<>(), new HashSet<>(exclude), sources);
			assertEquals("Configuration with exclude and sources", libraries, preferences.getReferencedLibraries());
		}

		{ // Test import of referenced libraries with only exclude
			List<String> exclude = Arrays.asList("libraries/sources/**");
			Map<String, Object> configuration = new HashMap<>();
			configuration.put(Preferences.JAVA_PROJECT_REFERENCED_LIBRARIES_KEY, new HashMap<String, Object>() {{
				put("exclude", exclude);
			}});
			Preferences preferences = Preferences.createFrom(configuration);
			ReferencedLibraries libraries = new ReferencedLibraries(new HashSet<>(), new HashSet<>(exclude), new HashMap<>());
			assertEquals("Configuration with exclude", libraries, preferences.getReferencedLibraries());
		}

		{ // Test import of referenced libraries with only sources
			Map<String, String> sources = new HashMap<>() {{
				put("libraries/foo.jar", "libraries/foo-src.jar");
			}};
			Map<String, Object> configuration = new HashMap<>();
			configuration.put(Preferences.JAVA_PROJECT_REFERENCED_LIBRARIES_KEY, new HashMap<String, Object>() {{
				put("sources", sources);
			}});
			Preferences preferences = Preferences.createFrom(configuration);
			ReferencedLibraries libraries = new ReferencedLibraries(new HashSet<>(), new HashSet<>(), sources);
			assertEquals("Configuration with sources", libraries, preferences.getReferencedLibraries());
		}
	}

	@Test
	public void testUpdateReferencedLibraries() throws Exception {
		IProject project = copyAndImportFolder("singlefile/simple", "src/App.java");
		NavigateToDefinitionHandler handler = new NavigateToDefinitionHandler(preferenceManager);
		String uri = ClassFileUtil.getURI(project, "App");
		TextDocumentIdentifier identifier = new TextDocumentIdentifier(uri);
		List<? extends Location> definitions = handler.definition(new TextDocumentPositionParams(identifier, new Position(0, 13)), monitor);

		// The original mylib.jar is an empty jar, so the GTD is not available
		assertEquals(0, definitions.size());

		// replace it which contains the class 'mylib.A'
		IPath projectRealPath = ProjectUtils.getProjectRealFolder(project);
		IPath newLibPath = projectRealPath.append("mylib.jar");
		IPath referencedLibraryPath = projectRealPath.append("lib/mylib.jar");
		FileUtils.copyFile(newLibPath.toFile(), referencedLibraryPath.toFile());

		List<String> include = Arrays.asList("lib/**/*.jar");
		ReferencedLibraries libraries = new ReferencedLibraries(new HashSet<>(include));
		UpdateClasspathJob.getInstance().updateClasspath(JavaCore.create(project), libraries);
		waitForBackgroundJobs();

		definitions = handler.definition(new TextDocumentPositionParams(identifier, new Position(0, 13)), monitor);
		assertEquals(1, definitions.size());
	}

	@Test
	public void testDynamicSourceLookups() throws Exception {
		IProject project = copyAndImportFolder("singlefile/downloadSources", "UsingRemark.java");

		// place remark artifact in lib folder
		File remarkFile = DependencyUtil.getArtifact("com.kotcrab.remark", "remark", "1.2.0", null);
		IPath projectRealPath = ProjectUtils.getProjectRealFolder(project);
		IPath remarkCopy = projectRealPath.append("lib/remark.jar");
		FileUtils.copyFile(remarkFile, remarkCopy.toFile());

		// update classpath
		List<String> include = Arrays.asList("lib/**/*.jar");
		ReferencedLibraries libraries = new ReferencedLibraries(new HashSet<>(include));
		UpdateClasspathJob.getInstance().updateClasspath(JavaCore.create(project), libraries);
		waitForBackgroundJobs();

		List<IMarker> errors = ResourceUtils.getErrorMarkers(project);
		assertEquals("Unexpected errors " + ResourceUtils.toString(errors), 0, errors.size());

		IJavaProject javaProject = JavaCore.create(project);
		IClasspathEntry remark = JavaProjectHelper.findJarEntry(javaProject, "remark.jar");
		assertNotNull(remark);

		// construct hover request
		URI standalone = new File(projectRealPath.toFile(), "UsingRemark.java").toURI();
		String payload = HoverHandlerTest.createHoverRequest(standalone, 2, 3);
		TextDocumentPositionParams position = getParams(payload);

		// perform hover
		int retries = 0;
		HoverHandler handler = new HoverHandler(preferenceManager);
		Hover hover = null;
		while (remark.getSourceAttachmentPath() == null && retries++ < 3) {
			File lastUpdated = new File(remarkFile.getParentFile(), "m2e-lastUpdated.properties");
			if (lastUpdated.exists()) {
				FileUtils.forceDelete(lastUpdated);
			}
			String timeoutStr = System.getProperty("java.lsp.mavensearch.timeout", "10");
			long timeout;
			try {
				timeout = Long.parseLong(timeoutStr);
			} catch (Exception e) {
				timeout = 10;
			}
			timeout = timeout * retries;
			System.setProperty("java.lsp.mavensearch.timeout", String.valueOf(timeout));
			hover = handler.hover(position, monitor);
			if (hover.getContents().getLeft().size() < 2) {
				JobHelpers.waitForDownloadSourcesJobs(JobHelpers.MAX_TIME_MILLIS);
				waitForBackgroundJobs();
				hover = handler.hover(position, monitor);
			}
			remark = JavaProjectHelper.findJarEntry(javaProject, "remark.jar");
		}
		// verify library has source attachment
		assertNotNull(remark.getSourceAttachmentPath());
		assertNotNull(hover);
		String javadoc = hover.getContents().getLeft().get(1).getLeft();
		assertTrue("Unexpected Javadoc:" + javadoc, javadoc.contains("The class that manages converting HTML to Markdown"));

		// add another artifact to lib folder
		File jsoupFile = DependencyUtil.getArtifact("org.jsoup", "jsoup", "1.9.2", null);
		IPath jsoupCopy = projectRealPath.append("lib/jsoup.jar");
		FileUtils.copyFile(jsoupFile, jsoupCopy.toFile());

		// update classpath
		UpdateClasspathJob.getInstance().updateClasspath(JavaCore.create(project), libraries);
		waitForBackgroundJobs();

		// perform hover
		hover = handler.hover(position, monitor);
		if (hover.getContents().getLeft().size() < 2) {
			JobHelpers.waitForDownloadSourcesJobs(JobHelpers.MAX_TIME_MILLIS);
			waitForBackgroundJobs();
			hover = handler.hover(position, monitor);
		}

		// verify original library source attachment persists
		assertNotNull(remark.getSourceAttachmentPath());
		assertNotNull(hover);
		javadoc = hover.getContents().getLeft().get(1).getLeft();
		assertTrue("Unexpected Javadoc:" + javadoc, javadoc.contains("The class that manages converting HTML to Markdown"));

	}
}
