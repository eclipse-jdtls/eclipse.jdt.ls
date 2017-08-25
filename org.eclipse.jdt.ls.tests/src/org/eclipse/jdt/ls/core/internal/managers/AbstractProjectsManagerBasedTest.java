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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.when;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.net.URI;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.io.FileUtils;
import org.eclipse.core.resources.IContainer;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IFolder;
import org.eclipse.core.resources.IMarker;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.core.runtime.Path;
import org.eclipse.core.runtime.Platform;
import org.eclipse.jdt.core.IBuffer;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.WorkingCopyOwner;
import org.eclipse.jdt.ls.core.internal.DocumentAdapter;
import org.eclipse.jdt.ls.core.internal.JavaClientConnection.JavaLanguageClient;
import org.eclipse.jdt.ls.core.internal.JobHelpers;
import org.eclipse.jdt.ls.core.internal.ProjectUtils;
import org.eclipse.jdt.ls.core.internal.ResourceUtils;
import org.eclipse.jdt.ls.core.internal.SimpleLogListener;
import org.eclipse.jdt.ls.core.internal.TestVMType;
import org.eclipse.jdt.ls.core.internal.WorkspaceHelper;
import org.eclipse.jdt.ls.core.internal.preferences.PreferenceManager;
import org.eclipse.jdt.ls.core.internal.preferences.Preferences;
import org.junit.After;
import org.junit.Before;
import org.mockito.Mock;

/**
 * @author Fred Bricon
 *
 */
public abstract class AbstractProjectsManagerBasedTest {

	public static final String TEST_PROJECT_NAME = "TestProject";

	protected IProgressMonitor monitor = new NullProgressMonitor();
	protected ProjectsManager projectsManager;
	@Mock
	protected PreferenceManager preferenceManager;
	protected Preferences preferences = new Preferences();

	protected SimpleLogListener logListener;

	protected Map<String, List<Object>> clientRequests = new HashMap<>();
	protected JavaLanguageClient client = (JavaLanguageClient) Proxy.newProxyInstance(getClass().getClassLoader(), new Class[] { JavaLanguageClient.class }, new InvocationHandler() {

		@Override
		public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
			if (args.length == 1) {
				String name = method.getName();
				List<Object> params = clientRequests.get(name);
				if (params == null) {
					params = new ArrayList<>();
					clientRequests.put(name, params);
				}
				params.add(args[0]);
			}
			return null;
		}
	});

	@Before
	public void initProjectManager() throws CoreException {
		TestVMType.setTestJREAsDefault();
		clientRequests.clear();

		logListener = new SimpleLogListener();
		Platform.addLogListener(logListener);
		if (preferenceManager != null) {
			when(preferenceManager.getPreferences()).thenReturn(preferences);
		}
		projectsManager = new ProjectsManager(preferenceManager);

		WorkingCopyOwner.setPrimaryBufferProvider(new WorkingCopyOwner() {
			@Override
			public IBuffer createBuffer(ICompilationUnit workingCopy) {
				ICompilationUnit original= workingCopy.getPrimary();
				IResource resource= original.getResource();
				if (resource instanceof IFile) {
					return new DocumentAdapter(workingCopy, (IFile)resource);
				}
				return DocumentAdapter.Null;
			}
		});
	}

	protected IJavaProject newEmptyProject() throws Exception {
		IProject testProject = ResourcesPlugin.getWorkspace().getRoot().getProject(TEST_PROJECT_NAME);
		assertEquals(false, testProject.exists());
		projectsManager.createJavaProject(testProject, new NullProgressMonitor());
		waitForBackgroundJobs();
		return JavaCore.create(testProject);
	}

	protected IJavaProject newDefaultProject() throws Exception {
		IProject testProject = projectsManager.getDefaultProject();
		projectsManager.createJavaProject(testProject, new NullProgressMonitor());
		waitForBackgroundJobs();
		return JavaCore.create(testProject);
	}

	protected IFile linkFilesToDefaultProject(String path) throws Exception {
		IProject testProject = projectsManager.getDefaultProject();
		String fullpath = copyFiles(path).getAbsolutePath();
		String fileName = fullpath.substring(fullpath.lastIndexOf("\\") + 1);
		IPath filePath = new Path("src").append(fileName);
		final IFile file = testProject.getFile(filePath);
		URI uri = Paths.get(fullpath).toUri();
		createFolders(file.getParent(), monitor);
		waitForBackgroundJobs();
		file.createLink(uri, IResource.REPLACE, monitor);
		waitForBackgroundJobs();
		return file;
	}

	protected List<IProject> importProjects(String path) throws Exception {
		File to = copyFiles(path);
		projectsManager.initializeProjects(to.getAbsolutePath(), monitor);
		waitForBackgroundJobs();
		return WorkspaceHelper.getAllProjects();
	}

	protected void waitForBackgroundJobs() throws Exception {
		JobHelpers.waitForJobsToComplete(monitor);
	}

	protected File getSourceProjectDirectory() {
		return new File("projects");
	}

	protected File getWorkingProjectDirectory() throws IOException {
		File dir = new File("target", "workingProjects");
		FileUtils.forceMkdir(dir);
		return dir;
	}

	@After
	public void cleanUp() throws Exception {
		projectsManager = null;
		Platform.removeLogListener(logListener);
		logListener = null;
		WorkspaceHelper.deleteAllProjects();
		FileUtils.forceDelete(getWorkingProjectDirectory());
	}

	protected void assertIsJavaProject(IProject project) {
		assertNotNull(project);
		assertTrue(project.getName() +" is missing the Java nature", ProjectUtils.isJavaProject(project));
	}

	protected void assertHasErrors(IProject project) {
		try {
			assertTrue(project.getName() + " has no errors", ResourceUtils.getErrorMarkers(project).size() > 0);
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	protected void assertNoErrors(IProject project) {
		try {
			List<IMarker> markers = ResourceUtils.getErrorMarkers(project);
			assertEquals(project.getName() + " has errors: \n"+ResourceUtils.toString(markers), 0, markers.size());
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	private File copyFiles(String path) throws IOException {
		File from = new File(getSourceProjectDirectory(), path);
		File to = new File(getWorkingProjectDirectory(), path);
		if (to.exists()) {
			FileUtils.forceDelete(to);
		}

		if (from.isDirectory()) {
			FileUtils.copyDirectory(from, to);
		} else {
			FileUtils.copyFile(from, to);
		}

		return to;
	}

	private static void createFolders(IContainer folder, IProgressMonitor monitor) throws CoreException {
		if (!folder.exists() && folder instanceof IFolder) {
			IContainer parent = folder.getParent();
			createFolders(parent, monitor);
			folder.refreshLocal(IResource.DEPTH_ZERO, monitor);
			if (!folder.exists()) {
				((IFolder) folder).create(true, true, monitor);
			}
		}
	}

}
