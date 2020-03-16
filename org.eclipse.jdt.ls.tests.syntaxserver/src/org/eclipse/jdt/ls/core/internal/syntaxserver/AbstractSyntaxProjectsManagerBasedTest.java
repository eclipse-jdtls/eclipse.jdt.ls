/*******************************************************************************
* Copyright (c) 2020 Microsoft Corporation and others.
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

package org.eclipse.jdt.ls.core.internal.syntaxserver;

import static org.mockito.Matchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.io.FileUtils;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IWorkspaceRunnable;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.core.runtime.Path;
import org.eclipse.core.runtime.Platform;
import org.eclipse.jdt.core.IBuffer;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.WorkingCopyOwner;
import org.eclipse.jdt.ls.core.internal.DocumentAdapter;
import org.eclipse.jdt.ls.core.internal.JavaClientConnection.JavaLanguageClient;
import org.eclipse.jdt.ls.core.internal.JavaLanguageServerPlugin;
import org.eclipse.jdt.ls.core.internal.JobHelpers;
import org.eclipse.jdt.ls.core.internal.ProjectUtils;
import org.eclipse.jdt.ls.core.internal.SimpleLogListener;
import org.eclipse.jdt.ls.core.internal.preferences.ClientPreferences;
import org.eclipse.jdt.ls.core.internal.preferences.PreferenceManager;
import org.eclipse.jdt.ls.core.internal.preferences.Preferences;
import org.junit.After;
import org.junit.Before;
import org.mockito.Mock;

public abstract class AbstractSyntaxProjectsManagerBasedTest {
	protected IProgressMonitor monitor;
	protected SyntaxProjectsManager projectsManager;
	@Mock
	protected PreferenceManager preferenceManager;

	private PreferenceManager oldPreferenceManager;

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
		if (preferenceManager == null) {
			preferenceManager = mock(PreferenceManager.class);
		}
		initPreferenceManager(true);

		oldPreferenceManager = JavaLanguageServerPlugin.getPreferencesManager();
		JavaLanguageServerPlugin.setPreferencesManager(preferenceManager);
		projectsManager = new SyntaxProjectsManager(preferenceManager);
		monitor = new NullProgressMonitor();
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
		when(preferenceManager.getClientPreferences()).thenReturn(clientPreferences);
		return clientPreferences;
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
		JobHelpers.waitForWorkspaceJobsToComplete(monitor);
		return Arrays.asList(ProjectUtils.getAllProjects());
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
		JavaLanguageServerPlugin.setPreferencesManager(oldPreferenceManager);
		projectsManager = null;
		Platform.removeLogListener(logListener);
		logListener = null;
		Arrays.asList(ProjectUtils.getAllProjects()).forEach(project -> {
			try {
				project.delete(true, new NullProgressMonitor());
			} catch (CoreException e) {
				JavaLanguageServerPlugin.logException(e.getMessage(), e);
			}
		});
		FileUtils.forceDelete(getWorkingProjectDirectory());
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

	protected IPath getWorkingTestPath(String path) throws IOException {
		return Path.fromOSString(new File(getWorkingProjectDirectory(), path).getAbsolutePath());
	}
}
