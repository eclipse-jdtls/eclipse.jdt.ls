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

import static org.eclipse.jdt.ls.core.internal.JavaLanguageServerPlugin.logInfo;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.eclipse.core.resources.IFolder;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.core.runtime.OperationCanceledException;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.jdt.core.IClasspathEntry;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.ls.core.internal.JDTUtils;
import org.eclipse.jdt.ls.core.internal.JavaLanguageServerPlugin;
import org.eclipse.jdt.ls.core.internal.JobHelpers;
import org.eclipse.jdt.ls.core.internal.ProjectUtils;
import org.eclipse.jdt.ls.core.internal.ResourceUtils;
import org.eclipse.jdt.ls.core.internal.managers.IBuildSupport;
import org.eclipse.jdt.ls.core.internal.managers.ProjectsManager;
import org.eclipse.jdt.ls.core.internal.preferences.PreferenceManager;
import org.eclipse.jdt.ls.core.internal.preferences.Preferences;
import org.eclipse.lsp4j.DidChangeWatchedFilesRegistrationOptions;
import org.eclipse.lsp4j.FileSystemWatcher;
import org.eclipse.lsp4j.jsonrpc.messages.Either;

public class SyntaxProjectsManager extends ProjectsManager {
	//@formatter:off
	private static final List<String> basicWatchers = Arrays.asList(
			"**/*.java"
			// "**/src/**"
	);
	//@formatter:on

	private final Set<String> watchers = new LinkedHashSet<>();

	private Job registerWatcherJob = new Job("Register Watchers") {

		@Override
		protected IStatus run(IProgressMonitor monitor) {

			try {
				JobHelpers.waitForWorkspaceJobsToComplete(new NullProgressMonitor());
				registerWatchers();
			} catch (InterruptedException e) {
				// do nothing
			}
			return Status.OK_STATUS;
		}
	};

	public SyntaxProjectsManager(PreferenceManager preferenceManager) {
		super(preferenceManager);
	}

	@Override
	public void cleanInvalidProjects(Collection<IPath> rootPaths, IProgressMonitor monitor) {
		List<String> syntaxProjects = rootPaths.stream().map((IPath rootPath) -> ProjectUtils.getWorkspaceInvisibleProjectName(rootPath)).collect(Collectors.toList());
		for (IProject project : ProjectUtils.getAllProjects()) {
			if (project.equals(getDefaultProject())) {
				continue;
			}
			if (project.exists() && syntaxProjects.contains(project.getName())) {
				try {
					project.getDescription();
				} catch (CoreException e) {
					try {
						project.delete(true, monitor);
					} catch (CoreException e1) {
						JavaLanguageServerPlugin.logException(e1.getMessage(), e1);
					}
				}
			} else {
				try {
					project.delete(false, true, monitor);
				} catch (CoreException e1) {
					JavaLanguageServerPlugin.logException(e1.getMessage(), e1);
				}
			}
		}
	}

	@Override
	protected void importProjects(Collection<IPath> rootPaths, IProgressMonitor monitor) throws CoreException, OperationCanceledException {
		// do nothing at syntax server mode.
	}

	@Override
	public void registerWatchers(boolean runInJob) {
		if (runInJob) {
			registerWatcherJob.schedule();
		} else {
			registerWatchers();
		}
	}

	@Override
	public List<FileSystemWatcher> registerWatchers() {
		logInfo(">> registerFeature 'workspace/didChangeWatchedFiles'");
		if (JavaLanguageServerPlugin.getPreferencesManager().getClientPreferences().isWorkspaceChangeWatchedFilesDynamicRegistered()) {
			IPath[] sources = new IPath[0];
			try {
				sources = listAllSourcePaths();
			} catch (JavaModelException e) {
				JavaLanguageServerPlugin.logException(e.getMessage(), e);
			}
			List<FileSystemWatcher> fileWatchers = new ArrayList<>();
			Set<String> patterns = new LinkedHashSet<>(basicWatchers);
			patterns.addAll(Stream.of(sources).map(ResourceUtils::toGlobPattern).collect(Collectors.toList()));

			for (String pattern : patterns) {
				FileSystemWatcher watcher = new FileSystemWatcher(Either.forLeft(pattern));
				fileWatchers.add(watcher);
			}
	
			if (!patterns.equals(watchers)) {
				JavaLanguageServerPlugin.logInfo(">> registerFeature 'workspace/didChangeWatchedFiles'");
				DidChangeWatchedFilesRegistrationOptions didChangeWatchedFilesRegistrationOptions = new DidChangeWatchedFilesRegistrationOptions(fileWatchers);
				JavaLanguageServerPlugin.getInstance().unregisterCapability(Preferences.WORKSPACE_WATCHED_FILES_ID, Preferences.WORKSPACE_WATCHED_FILES);
				JavaLanguageServerPlugin.getInstance().registerCapability(Preferences.WORKSPACE_WATCHED_FILES_ID, Preferences.WORKSPACE_WATCHED_FILES, didChangeWatchedFilesRegistrationOptions);
				watchers.clear();
				watchers.addAll(patterns);
			}

			return fileWatchers;
		}

		return Collections.emptyList();
	}


	private static IPath[] listAllSourcePaths() throws JavaModelException {
		Set<IPath> classpaths = new HashSet<>();
		IProject[] projects = ResourcesPlugin.getWorkspace().getRoot().getProjects();
		for (IProject project : projects) {
			if (ProjectsManager.DEFAULT_PROJECT_NAME.equals(project.getName())) {
				continue;
			}
			IJavaProject javaProject = JavaCore.create(project);
			if (javaProject != null && javaProject.exists()) {
				IClasspathEntry[] classpath = javaProject.getRawClasspath();
				for (IClasspathEntry entry : classpath) {
					if (entry.getEntryKind() == IClasspathEntry.CPE_SOURCE) {
						IPath path = entry.getPath();
						if (path == null) {
							continue;
						}

						IFolder folder = ResourcesPlugin.getWorkspace().getRoot().getFolder(path);
						if (folder.exists() && !folder.isDerived()) {
							IPath location = folder.getLocation();
							if (location != null && !ResourceUtils.isContainedIn(location, classpaths)) {
								classpaths.add(location);
							}
						}
					}
				}
			}
		}

		return classpaths.toArray(new IPath[classpaths.size()]);
	}

	@Override
	public void fileChanged(String uriString, CHANGE_TYPE changeType) {
		if (uriString == null) {
			return;
		}
		IResource resource = JDTUtils.getFileOrFolder(uriString);
		if (resource == null) {
			return;
		}

		try {
			Optional<IBuildSupport> bs = getBuildSupport(resource.getProject());
			if (bs.isPresent()) {
				IBuildSupport buildSupport = bs.get();
				buildSupport.fileChanged(resource, changeType, new NullProgressMonitor());
			}
		} catch (CoreException e) {
			JavaLanguageServerPlugin.logException("Problem refreshing workspace", e);
		}
	}
}
