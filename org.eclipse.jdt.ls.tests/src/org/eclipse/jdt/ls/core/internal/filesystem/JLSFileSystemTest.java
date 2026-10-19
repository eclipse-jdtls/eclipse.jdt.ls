/*******************************************************************************
 * Copyright (c) 2026 Microsoft Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/

package org.eclipse.jdt.ls.core.internal.filesystem;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.OutputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Stream;

import org.apache.commons.io.FileUtils;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IProjectDescription;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.core.runtime.preferences.IEclipsePreferences;
import org.eclipse.core.runtime.preferences.InstanceScope;
import org.eclipse.jdt.ls.core.internal.IConstants;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class JLSFileSystemTest {

	private static final String PRESERVE_PROJECT_METADATA = "java.import.preserveProjectMetadata";

	private Path tempDirectory;
	private IProject workspaceProject;
	private final Set<Path> shadowRoots = new HashSet<>();
	private String previousPreservationSetting;
	private String previousRootMetadataMode;

	@BeforeEach
	void setUp() throws Exception {
		tempDirectory = Files.createTempDirectory("jls-metadata-preservation-");
		IEclipsePreferences preferences = InstanceScope.INSTANCE.getNode(IConstants.PLUGIN_ID);
		previousPreservationSetting = preferences.get(PRESERVE_PROJECT_METADATA, null);
		preferences.remove(PRESERVE_PROJECT_METADATA);
		previousRootMetadataMode = System.getProperty(JLSFsUtils.GENERATES_METADATA_FILES_AT_PROJECT_ROOT);
	}

	@Test
	void preservesTheCompleteMetadataSnapshotBeforeTheFirstWrite() throws Exception {
		Path projectRoot = tempDirectory.resolve("project");
		Path projectFile = projectRoot.resolve(".project");
		Path classpathFile = projectRoot.resolve(".classpath");
		Path settingsFile = projectRoot.resolve(".settings/nested/custom.options");
		byte[] projectBytes = "user project bytes\n".getBytes(StandardCharsets.UTF_8);
		byte[] classpathBytes = "user classpath bytes\n".getBytes(StandardCharsets.UTF_8);
		byte[] settingsBytes = "user settings bytes\n".getBytes(StandardCharsets.UTF_8);
		Files.createDirectories(settingsFile.getParent());
		Files.write(projectFile, projectBytes);
		Files.write(classpathFile, classpathBytes);
		Files.write(settingsFile, settingsBytes);
		enablePreservation();

		Object projectStore = getStore(projectFile);
		Files.writeString(classpathFile, "changed after the snapshot\n");
		Files.writeString(settingsFile, "changed after the snapshot\n");

		assertNotEquals(projectFile, localPath(projectStore));
		assertArrayEquals(projectBytes, Files.readAllBytes(localPath(projectStore)));
		assertArrayEquals(classpathBytes, Files.readAllBytes(localPath(getStore(classpathFile))));
		assertArrayEquals(settingsBytes, Files.readAllBytes(localPath(getStore(settingsFile))));
	}

	@Test
	void routesMissingMetadataToTheShadowWithoutCreatingItAtTheProjectRoot() throws Exception {
		Path projectRoot = tempDirectory.resolve("project");
		Path projectFile = projectRoot.resolve(".project");
		Path classpathFile = projectRoot.resolve(".classpath");
		Files.createDirectories(projectRoot);
		Files.writeString(projectFile, "project bytes\n");
		enablePreservation();

		Object classpathStore = getStore(classpathFile);
		byte[] generatedBytes = "generated classpath\n".getBytes(StandardCharsets.UTF_8);
		try (OutputStream output = (OutputStream) invoke(classpathStore, "openOutputStream",
				new Class<?>[] { int.class, org.eclipse.core.runtime.IProgressMonitor.class }, 0, null)) {
			output.write(generatedBytes);
		}

		assertFalse(Files.exists(classpathFile));
		assertArrayEquals(generatedBytes, Files.readAllBytes(localPath(getStore(classpathFile))));
	}

	@Test
	void protectsEverySettingsDescendant() throws Exception {
		Path projectRoot = tempDirectory.resolve("project");
		Path settingsDirectory = projectRoot.resolve(".settings");
		Path nestedFile = settingsDirectory.resolve("nested/.settings/custom.options");
		Path siblingFile = settingsDirectory.resolve("org.eclipse.jdt.core.prefs");
		byte[] nestedBytes = "nested=true\n".getBytes(StandardCharsets.UTF_8);
		byte[] siblingBytes = "sibling=true\n".getBytes(StandardCharsets.UTF_8);
		Files.createDirectories(nestedFile.getParent());
		Files.writeString(projectRoot.resolve(".project"), "project bytes\n");
		Files.write(nestedFile, nestedBytes);
		Files.write(siblingFile, siblingBytes);
		enablePreservation();

		Object nestedStore = getStore(nestedFile);
		byte[] updatedBytes = "nested=false\n".getBytes(StandardCharsets.UTF_8);
		try (OutputStream output = (OutputStream) invoke(nestedStore, "openOutputStream",
				new Class<?>[] { int.class, org.eclipse.core.runtime.IProgressMonitor.class }, 0, null)) {
			output.write(updatedBytes);
		}

		assertArrayEquals(nestedBytes, Files.readAllBytes(nestedFile));
		assertArrayEquals(updatedBytes, Files.readAllBytes(localPath(nestedStore)));
		Path shadowSettings = localPath(getStore(settingsDirectory));
		assertArrayEquals(siblingBytes, Files.readAllBytes(shadowSettings.resolve(siblingFile.getFileName())));
	}

	@Test
	void routesEveryFileStoreEntryPointToTheSameShadow() throws Exception {
		Path projectRoot = tempDirectory.resolve("project");
		Path projectFile = projectRoot.resolve(".project");
		Path classpathFile = projectRoot.resolve(".classpath");
		Files.createDirectories(projectRoot);
		Files.writeString(projectFile, "project bytes\n");
		Files.writeString(classpathFile, "classpath bytes\n");
		enablePreservation();

		Path expected = localPath(getStore(classpathFile));
		Object projectStore = newFileStore(projectRoot.toFile());

		assertNotEquals(classpathFile, expected);
		assertEquals(expected, localPath(invoke(projectStore, "getChild", new Class<?>[] { String.class }, ".classpath")));
		assertEquals(expected, localPath(invoke(projectStore, "getFileStore",
				new Class<?>[] { org.eclipse.core.runtime.IPath.class }, new org.eclipse.core.runtime.Path(".classpath"))));
	}

	@Test
	void routesTheFirstProjectDescriptionForARegisteredWorkspaceProject() throws Exception {
		Path projectRoot = tempDirectory.resolve("project");
		Files.createDirectories(projectRoot);
		projectRoot = projectRoot.toRealPath();
		Path projectFile = projectRoot.resolve(".project");
		enablePreservation();

		String projectName = "preserved-project-" + Long.toUnsignedString(System.nanoTime());
		IProjectDescription description = ResourcesPlugin.getWorkspace().newProjectDescription(projectName);
		description.setLocation(org.eclipse.core.runtime.Path.fromOSString(projectRoot.toString()));
		workspaceProject = ResourcesPlugin.getWorkspace().getRoot().getProject(projectName);
		workspaceProject.create(description, new NullProgressMonitor());
		workspaceProject.open(new NullProgressMonitor());

		Path shadowProject = localPath(getStore(projectFile));
		assertFalse(Files.exists(projectFile));
		assertNotEquals(projectFile, shadowProject);
		assertTrue(Files.readString(shadowProject).contains("<name>" + projectName + "</name>"));
	}

	@Test
	void hidesAProtectedRootEntryAfterItIsDeletedFromTheShadow() throws Exception {
		Path projectRoot = tempDirectory.resolve("project");
		Path projectFile = projectRoot.resolve(".project");
		Path classpathFile = projectRoot.resolve(".classpath");
		Files.createDirectories(projectRoot);
		Files.writeString(projectFile, "project bytes\n");
		Files.writeString(classpathFile, "classpath bytes\n");
		enablePreservation();

		Files.delete(localPath(getStore(classpathFile)));
		Object projectStore = newFileStore(projectRoot.toFile());

		assertTrue(Files.exists(classpathFile));
		String[] childNames = (String[]) invoke(projectStore, "childNames",
				new Class<?>[] { int.class, org.eclipse.core.runtime.IProgressMonitor.class }, 0, null);
		Object[] childInfos = (Object[]) invoke(projectStore, "childInfos",
				new Class<?>[] { int.class, org.eclipse.core.runtime.IProgressMonitor.class }, 0, null);
		assertFalse(Stream.of(childNames).anyMatch(".classpath"::equals));
		assertFalse(hasChildInfo(childInfos, ".classpath"));
	}

	@Test
	void isolatesSameNamedProjectsByTheirRootLocation() throws Exception {
		Path firstProject = tempDirectory.resolve("first/project");
		Path secondProject = tempDirectory.resolve("second/project");
		Path firstProjectFile = firstProject.resolve(".project");
		Path secondProjectFile = secondProject.resolve(".project");
		Files.createDirectories(firstProject);
		Files.createDirectories(secondProject);
		Files.writeString(firstProjectFile, "first project\n");
		Files.writeString(secondProjectFile, "second project\n");
		enablePreservation();

		Path firstShadow = localPath(getStore(firstProjectFile));
		Path secondShadow = localPath(getStore(secondProjectFile));

		assertNotEquals(firstProjectFile, firstShadow);
		assertNotEquals(secondProjectFile, secondShadow);
		assertNotEquals(firstShadow, secondShadow);
		assertArrayEquals("first project\n".getBytes(StandardCharsets.UTF_8), Files.readAllBytes(firstShadow));
		assertArrayEquals("second project\n".getBytes(StandardCharsets.UTF_8), Files.readAllBytes(secondShadow));
	}

	private static void enablePreservation() {
		System.setProperty(JLSFsUtils.GENERATES_METADATA_FILES_AT_PROJECT_ROOT, "true");
		InstanceScope.INSTANCE.getNode(IConstants.PLUGIN_ID).putBoolean(PRESERVE_PROJECT_METADATA, true);
	}

	private Object getStore(Path path) throws Exception {
		// Avoid compile-time references to the Java 25 filesystem bundle resolved by the target platform;
		// CodeQL compiles this test on Java 21.
		Object fileSystem = Class.forName("org.eclipse.jdt.ls.core.internal.filesystem.JLSFileSystem").getConstructor().newInstance();
		Object store = invoke(fileSystem, "getStore", new Class<?>[] { org.eclipse.core.runtime.IPath.class },
				org.eclipse.core.runtime.Path.fromOSString(path.toString()));
		Path redirectedPath = localPath(store);
		if (!path.equals(redirectedPath)) {
			Path shadowRoot = redirectedPath;
			while (shadowRoot != null && !Files.isRegularFile(shadowRoot.resolve(".complete"))) {
				shadowRoot = shadowRoot.getParent();
			}
			if (shadowRoot != null) {
				shadowRoots.add(shadowRoot);
			}
		}
		return store;
	}

	private static Path localPath(Object store) throws Exception {
		File file = (File) invoke(store, "toLocalFile",
				new Class<?>[] { int.class, org.eclipse.core.runtime.IProgressMonitor.class }, 0, null);
		assertNotNull(file);
		return file.toPath();
	}

	private static Object newFileStore(File file) throws Exception {
		return Class.forName("org.eclipse.jdt.ls.core.internal.filesystem.JLSFile")
				.getConstructor(File.class).newInstance(file);
}

	private static Object invoke(Object receiver, String methodName, Class<?>[] parameterTypes, Object... arguments)
			throws Exception {
		Method method = receiver.getClass().getMethod(methodName, parameterTypes);
		try {
			return method.invoke(receiver, arguments);
		} catch (InvocationTargetException e) {
			Throwable cause = e.getCause();
			if (cause instanceof Exception exception) {
				throw exception;
			}
			if (cause instanceof Error error) {
				throw error;
			}
			throw new RuntimeException(cause);
		}
	}

	private static boolean hasChildInfo(Object[] childInfos, String name) throws Exception {
		for (Object childInfo : childInfos) {
			if (name.equals(invoke(childInfo, "getName", new Class<?>[0]))) {
				return true;
			}
		}
		return false;
	}

	@AfterEach
	void cleanUp() throws Exception {
		if (workspaceProject != null && workspaceProject.exists()) {
			workspaceProject.delete(false, true, new NullProgressMonitor());
		}
		for (Path shadowRoot : shadowRoots) {
			FileUtils.deleteDirectory(shadowRoot.toFile());
		}
		if (previousRootMetadataMode == null) {
			System.clearProperty(JLSFsUtils.GENERATES_METADATA_FILES_AT_PROJECT_ROOT);
		} else {
			System.setProperty(JLSFsUtils.GENERATES_METADATA_FILES_AT_PROJECT_ROOT, previousRootMetadataMode);
		}
		IEclipsePreferences preferences = InstanceScope.INSTANCE.getNode(IConstants.PLUGIN_ID);
		if (previousPreservationSetting == null) {
			preferences.remove(PRESERVE_PROJECT_METADATA);
		} else {
			preferences.put(PRESERVE_PROJECT_METADATA, previousPreservationSetting);
		}
		FileUtils.deleteDirectory(tempDirectory.toFile());
	}
}
