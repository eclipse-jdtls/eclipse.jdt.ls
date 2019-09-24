/*******************************************************************************
 * Copyright (c) 2016-2019 Red Hat Inc. and others.
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
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.net.MalformedURLException;
import java.net.URI;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

import org.apache.commons.io.FileUtils;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IMarker;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IWorkspaceRunnable;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IRegistryChangeEvent;
import org.eclipse.core.runtime.IRegistryChangeListener;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.core.runtime.Path;
import org.eclipse.core.runtime.Platform;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.jdt.core.IBuffer;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.WorkingCopyOwner;
import org.eclipse.jdt.ls.core.internal.DocumentAdapter;
import org.eclipse.jdt.ls.core.internal.JDTUtils;
import org.eclipse.jdt.ls.core.internal.JavaClientConnection.JavaLanguageClient;
import org.eclipse.jdt.ls.core.internal.JavaLanguageServerPlugin;
import org.eclipse.jdt.ls.core.internal.JobHelpers;
import org.eclipse.jdt.ls.core.internal.ProgressReport;
import org.eclipse.jdt.ls.core.internal.ProjectUtils;
import org.eclipse.jdt.ls.core.internal.ResourceUtils;
import org.eclipse.jdt.ls.core.internal.SimpleLogListener;
import org.eclipse.jdt.ls.core.internal.WorkspaceHelper;
import org.eclipse.jdt.ls.core.internal.handlers.BundleUtils;
import org.eclipse.jdt.ls.core.internal.handlers.ProgressReporterManager;
import org.eclipse.jdt.ls.core.internal.preferences.ClientPreferences;
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

	private static final java.lang.String REFERENCE_PREFIX = "reference:";

	protected IProgressMonitor monitor;
	protected ProjectsManager projectsManager;
	@Mock
	protected PreferenceManager preferenceManager;

	protected Preferences preferences;

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
	public void initProjectManager() throws Exception {
		clientRequests.clear();

		logListener = new SimpleLogListener();
		Platform.addLogListener(logListener);
		preferences = new Preferences();
		preferences.setRootPaths(Collections.singleton(new Path(getWorkingProjectDirectory().getAbsolutePath())));
		preferences.setCodeGenerationTemplateGenerateComments(true);
		if (preferenceManager == null) {
			preferenceManager = mock(PreferenceManager.class);
		}
		initPreferenceManager(true);

		JavaLanguageServerPlugin.setPreferencesManager(preferenceManager);
		projectsManager = new ProjectsManager(preferenceManager);
		ProgressReporterManager progressManager = new ProgressReporterManager(this.client, preferenceManager);
		progressManager.setReportThrottle(0);//disable throttling to ensure we capture all events
		Job.getJobManager().setProgressProvider(progressManager);
		monitor = progressManager.getDefaultMonitor();
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

	protected ClientPreferences initPreferenceManager(boolean supportClassFileContents) {
		PreferenceManager.initialize();
		when(preferenceManager.getPreferences()).thenReturn(preferences);
		when(preferenceManager.getPreferences(any())).thenReturn(preferences);
		when(preferenceManager.isClientSupportsClassFileContent()).thenReturn(supportClassFileContents);
		ClientPreferences clientPreferences = mock(ClientPreferences.class);
		when(clientPreferences.isProgressReportSupported()).thenReturn(true);
		when(clientPreferences.isSemanticHighlightingSupported()).thenReturn(true);
		when(preferenceManager.getClientPreferences()).thenReturn(clientPreferences);
		when(clientPreferences.isSupportedCodeActionKind(anyString())).thenReturn(true);
		when(clientPreferences.isOverrideMethodsPromptSupported()).thenReturn(true);
		when(clientPreferences.isHashCodeEqualsPromptSupported()).thenReturn(true);
		when(clientPreferences.isGenerateToStringPromptSupported()).thenReturn(true);
		when(clientPreferences.isAdvancedGenerateAccessorsSupported()).thenReturn(true);
		when(clientPreferences.isGenerateConstructorsPromptSupported()).thenReturn(true);
		when(clientPreferences.isGenerateDelegateMethodsPromptSupported()).thenReturn(true);
		return clientPreferences;
	}

	protected IJavaProject newEmptyProject() throws Exception {
		IProject testProject = ResourcesPlugin.getWorkspace().getRoot().getProject(TEST_PROJECT_NAME);
		assertEquals(false, testProject.exists());
		projectsManager.createJavaProject(testProject, new Path(getWorkingProjectDirectory().getAbsolutePath()).append(TEST_PROJECT_NAME), "src", "bin", new NullProgressMonitor());
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
		String fullpath = copyFiles(path, true).getAbsolutePath().replace('\\', '/');
		String fileName = fullpath.substring(fullpath.lastIndexOf("/") + 1);
		IPath filePath = new Path("src").append(fileName);
		final IFile file = testProject.getFile(filePath);
		URI uri = Paths.get(fullpath).toUri();
		JDTUtils.createFolders(file.getParent(), monitor);
		waitForBackgroundJobs();
		file.createLink(uri, IResource.REPLACE, monitor);
		waitForBackgroundJobs();
		return file;
	}

	protected List<IProject> importProjects(String path) throws Exception {
		return importProjects(Collections.singleton(path));
	}

	protected List<IProject> importExistingProjects(String path) throws Exception {
		return importProjects(Collections.singleton(path), false);
	}

	protected List<IProject> importProjects(Collection<String> paths) throws Exception {
		return importProjects(paths, true);
	}

	protected List<IProject> importProjects(Collection<String> paths, boolean deleteExistingFiles) throws Exception {
		final List<IPath> roots = new ArrayList<>();
		for (String path : paths) {
			File file = copyFiles(path, deleteExistingFiles);
			roots.add(Path.fromOSString(file.getAbsolutePath()));
		}
		IWorkspaceRunnable runnable = new IWorkspaceRunnable() {
			@Override
			public void run(IProgressMonitor monitor) throws CoreException {
				projectsManager.initializeProjects(roots, monitor);
			}
		};
		JavaCore.run(runnable, null, monitor);
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
		Job.getJobManager().setProgressProvider(null);
		JobHelpers.waitForJobsToComplete();
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

	protected void assertHasErrors(IProject project, String... expectedErrorsLike) {
		try {
			List<IMarker> markers = ResourceUtils.getErrorMarkers(project);
			String allErrors = ResourceUtils.toString(markers);
			for (String expectedError : expectedErrorsLike) {
				boolean hasError = markers.stream().map(ResourceUtils::getMessage).filter(Objects::nonNull).filter(m -> m.contains(expectedError)).findFirst().isPresent();
				assertTrue(expectedError + " was not found in: \n" + allErrors, hasError);
			}
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	protected void assertNoErrors(IResource resource) {
		try {
			List<IMarker> markers = ResourceUtils.getErrorMarkers(resource);
			assertEquals(resource.getName() + " has errors: \n" + ResourceUtils.toString(markers), 0, markers.size());
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	protected File copyFiles(String path, boolean reimportIfExists) throws IOException {
		File from = new File(getSourceProjectDirectory(), path);
		File to = new File(getWorkingProjectDirectory(), path);
		if (to.exists()) {
			if (!reimportIfExists) {
				return to;
			}
			FileUtils.forceDelete(to);
		}

		if (from.isDirectory()) {
			FileUtils.copyDirectory(from, to);
		} else {
			FileUtils.copyFile(from, to);
		}

		return to;
	}

	protected void assertTaskCompleted(String taskName) {
		List<Object> progressReports = clientRequests.get("sendProgressReport");
		assertNotNull("No progress report were sent to the client", progressReports);
		boolean completedTask = false;
		Set<String> tasks = new LinkedHashSet<>(progressReports.size());
		String taskId = null;
		for (Object o : progressReports) {
			ProgressReport report = (ProgressReport) o;
			assertNotNull(report.getId());
			tasks.add(report.getTask());
			if (taskName.equals(report.getTask())) {
				taskId = report.getId();
			}
			if (report.getId().equals(taskId) && report.isComplete()) {
				completedTask = true;
			}
		}
		assertNotNull("'" + taskName + "' was not found among " + tasks, taskId);
		assertTrue("'" + taskName + "' was not completed", completedTask);
	}

	protected void assertMatches(String pattern, String value) {
		assertTrue(value + " doesn't match pattern: " + pattern, Pattern.matches(pattern, value));
	}

	protected String getBundle(String folder, String bundleName) {
		return (new File(folder, bundleName)).getAbsolutePath();
	}

	protected String getBundleLocation(String location, boolean useReference) {
		File f = new File(location);
		String bundleLocation = null;
		try {
			bundleLocation = f.toURI().toURL().toString();
			if (useReference) {
				bundleLocation = REFERENCE_PREFIX + bundleLocation;
			}
		} catch (MalformedURLException e) {
			JavaLanguageServerPlugin.logException("Get bundle location failure ", e);
		}
		return bundleLocation;
	}

	protected void loadBundles(List<String> bundles) throws Exception {
		RegistryChangeListener listener = new RegistryChangeListener(false);
		try {
			Platform.getExtensionRegistry().addRegistryChangeListener(listener);
			BundleUtils.loadBundles(bundles);
			while (!listener.isChanged()) {
				Thread.sleep(100);
			}
		} finally {
			Platform.getExtensionRegistry().removeRegistryChangeListener(listener);
		}
	}

	private class RegistryChangeListener implements IRegistryChangeListener {
		private boolean changed;

		private RegistryChangeListener(boolean changed) {
			this.setChanged(changed);
		}

		/* (non-Javadoc)
		 * @see org.eclipse.core.runtime.IRegistryChangeListener#registryChanged(org.eclipse.core.runtime.IRegistryChangeEvent)
		 */
		@Override
		public void registryChanged(IRegistryChangeEvent event) {
			setChanged(true);
		}

		public boolean isChanged() {
			return changed;
		}

		public void setChanged(boolean changed) {
			this.changed = changed;
		}
	}
}
