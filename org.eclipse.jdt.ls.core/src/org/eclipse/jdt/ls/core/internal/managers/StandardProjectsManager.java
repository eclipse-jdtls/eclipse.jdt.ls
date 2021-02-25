/*******************************************************************************
 * Copyright (c) 2016-2019 Red Hat Inc. and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *     Red Hat Inc. - initial API and implementation
 *     Microsoft Corporation
 *******************************************************************************/
package org.eclipse.jdt.ls.core.internal.managers;

import static java.util.Arrays.asList;
import static org.eclipse.jdt.ls.core.internal.JavaLanguageServerPlugin.logInfo;
import static org.eclipse.jdt.ls.core.internal.ResourceUtils.isContainedIn;

import java.io.File;
import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.eclipse.buildship.core.internal.CorePlugin;
import org.eclipse.buildship.core.internal.preferences.PersistentModel;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IFolder;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.ISaveContext;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IConfigurationElement;
import org.eclipse.core.runtime.IExtensionPoint;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.core.runtime.Path;
import org.eclipse.core.runtime.Platform;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.SubMonitor;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.jdt.core.IClasspathEntry;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.ls.core.internal.ActionableNotification;
import org.eclipse.jdt.ls.core.internal.IConstants;
import org.eclipse.jdt.ls.core.internal.JDTUtils;
import org.eclipse.jdt.ls.core.internal.JavaLanguageServerPlugin;
import org.eclipse.jdt.ls.core.internal.JobHelpers;
import org.eclipse.jdt.ls.core.internal.ProjectUtils;
import org.eclipse.jdt.ls.core.internal.ResourceUtils;
import org.eclipse.jdt.ls.core.internal.preferences.IPreferencesChangeListener;
import org.eclipse.jdt.ls.core.internal.preferences.PreferenceManager;
import org.eclipse.jdt.ls.core.internal.preferences.Preferences;
import org.eclipse.jdt.ls.core.internal.preferences.Preferences.FeatureStatus;
import org.eclipse.lsp4j.Command;
import org.eclipse.lsp4j.DidChangeWatchedFilesRegistrationOptions;
import org.eclipse.lsp4j.FileSystemWatcher;
import org.eclipse.lsp4j.MessageType;
import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.eclipse.lsp4j.WatchKind;

public class StandardProjectsManager extends ProjectsManager {
	protected static final String BUILD_SUPPORT_EXTENSION_POINT_ID = "buildSupport";
	private static final Set<String> watchers = new LinkedHashSet<>();
	private PreferenceManager preferenceManager;
	//@formatter:off
	private static final List<String> basicWatchers = Arrays.asList(
			"**/*.java",
			"**/.project",
			"**/.classpath",
			"**/.settings/*.prefs",
			"**/src/**"
	);
	//@formatter:on

	private Job registerWatcherJob = new Job("Register Watchers") {

		@Override
		protected IStatus run(IProgressMonitor monitor) {
			JobHelpers.waitForJobsToComplete();
			registerWatchers();
			return Status.OK_STATUS;
		}

	};

	private IPreferencesChangeListener preferenceChangeListener = null;

	public StandardProjectsManager(PreferenceManager preferenceManager) {
		super(preferenceManager);
		this.preferenceManager = preferenceManager;
	}

	@Override
	public void cleanInvalidProjects(final Collection<IPath> rootPaths, IProgressMonitor monitor) {
		SubMonitor subMonitor = SubMonitor.convert(monitor, 20);
		deleteInvalidProjects(rootPaths, subMonitor.split(10));
		GradleBuildSupport.cleanGradleModels(subMonitor.split(10));
	}

	private void deleteInvalidProjects(Collection<IPath> rootPaths, IProgressMonitor monitor) {
		List<String> workspaceProjects = rootPaths.stream().map((IPath rootPath) -> ProjectUtils.getWorkspaceInvisibleProjectName(rootPath)).collect(Collectors.toList());
		List<IProject> validGradleProjects = new ArrayList<>();
		List<IProject> suspiciousGradleProjects = new ArrayList<>();
		for (IProject project : ProjectUtils.getAllProjects()) {
			if (project.equals(getDefaultProject())) {
				continue;
			}
			if (project.exists() && (ResourceUtils.isContainedIn(project.getLocation(), rootPaths) || ProjectUtils.isGradleProject(project) || workspaceProjects.contains(project.getName()))) {
				try {
					project.getDescription();
					if (ProjectUtils.isGradleProject(project)) {
						if (ResourceUtils.isContainedIn(project.getLocation(), rootPaths)) {
							validGradleProjects.add(project);
						} else {
							suspiciousGradleProjects.add(project);
						}
					}
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

		List<IProject> unrelatedProjects = findUnrelatedGradleProjects(suspiciousGradleProjects, validGradleProjects);
		unrelatedProjects.forEach((project) -> {
			try {
				project.delete(false, true, monitor);
			} catch (CoreException e1) {
				JavaLanguageServerPlugin.logException(e1.getMessage(), e1);
			}
		});
	}

	/**
	 * Find those gradle projects not referenced by any gradle project in the current workspace.
	 */
	private List<IProject> findUnrelatedGradleProjects(List<IProject> suspiciousProjects, List<IProject> validProjects) {
		suspiciousProjects.sort((IProject p1, IProject p2) -> p1.getLocation().toOSString().length() - p2.getLocation().toOSString().length());

		List<IProject> unrelatedCandidates = new ArrayList<>();
		Collection<IPath> validSubPaths = new ArrayList<>();
		for (IProject suspiciousProject : suspiciousProjects) {
			if (validSubPaths.contains(suspiciousProject.getFullPath().makeRelative())) {
				continue;
			}

			// Check whether the suspicious gradle project is the parent project of the opening project.
			boolean isParentProject = false;
			Collection<IPath> subpaths = null;
			PersistentModel model = CorePlugin.modelPersistence().loadModel(suspiciousProject);
			if (model.isPresent()) {
				subpaths = model.getSubprojectPaths();
				if (!subpaths.isEmpty()) {
					for (IProject validProject : validProjects) {
						if (subpaths.contains(validProject.getFullPath().makeRelative())) {
							isParentProject = true;
							break;
						}
					}
				}
			}

			if (isParentProject) {
				validSubPaths.addAll(subpaths);
			} else {
				unrelatedCandidates.add(suspiciousProject);
			}
		}

		List<IProject> result = new ArrayList<>();
		// Exclude those projects which are the subprojects of the verified parent project.
		for (IProject candidate : unrelatedCandidates) {
			if (!validSubPaths.contains(candidate.getFullPath().makeRelative())) {
				result.add(candidate);
			}
		}

		return result;
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
		URI formatterUri = preferenceManager.getPreferences().getFormatterAsURI();
		if (formatterUri != null) {
			URI uri = JDTUtils.toURI(uriString);
			if (uri != null && uri.equals(formatterUri) && JavaLanguageServerPlugin.getInstance().getProtocol() != null) {
				if (changeType == CHANGE_TYPE.DELETED || changeType == CHANGE_TYPE.CREATED) {
					registerWatchers();
				}
				FormatterManager.configureFormatter(preferenceManager.getPreferences());
			}
		}

		try {
			Optional<IBuildSupport> bs = getBuildSupport(resource.getProject());
			if (bs.isPresent()) {
				IBuildSupport buildSupport = bs.get();
				boolean requireConfigurationUpdate = buildSupport.fileChanged(resource, changeType, new NullProgressMonitor());
				if (requireConfigurationUpdate) {
					FeatureStatus status = preferenceManager.getPreferences().getUpdateBuildConfigurationStatus();
					switch (status) {
						case automatic:
							// do not force the build, because it's not started by user and should be done only if build file has changed
							updateProject(resource.getProject(), false);
							break;
						case disabled:
							break;
						default:
							if (client != null) {
								String cmd = "java.projectConfiguration.status";
								TextDocumentIdentifier uri = new TextDocumentIdentifier(uriString);
								ActionableNotification updateProjectConfigurationNotification = new ActionableNotification().withSeverity(MessageType.Info)
										.withMessage("A build file was modified. Do you want to synchronize the Java classpath/configuration?").withCommands(asList(new Command("Never", cmd, asList(uri, FeatureStatus.disabled)),
												new Command("Now", cmd, asList(uri, FeatureStatus.interactive)), new Command("Always", cmd, asList(uri, FeatureStatus.automatic))));
								client.sendActionableNotification(updateProjectConfigurationNotification);
							}
					}
				}
			}
		} catch (CoreException e) {
			JavaLanguageServerPlugin.logException("Problem refreshing workspace", e);
		}
	}

	@Override
	public boolean isBuildFile(IResource resource) {
		return buildSupports().filter(bs -> bs.isBuildFile(resource)).findAny().isPresent();
	}

	@Override
	public Optional<IBuildSupport> getBuildSupport(IProject project) {
		return buildSupports().filter(bs -> bs.applies(project)).findFirst();
	}

	protected Stream<IBuildSupport> buildSupports() {
		Map<Integer, IBuildSupport> supporters = new TreeMap<>();
		IExtensionPoint extensionPoint = Platform.getExtensionRegistry().getExtensionPoint(IConstants.PLUGIN_ID, BUILD_SUPPORT_EXTENSION_POINT_ID);
		IConfigurationElement[] configs = extensionPoint.getConfigurationElements();
		for (IConfigurationElement config : configs) {
			try {
				Integer order = Integer.valueOf(config.getAttribute("order"));
				supporters.put(order, (IBuildSupport) config.createExecutableExtension("class")); //$NON-NLS-1$
			} catch (CoreException e) {
				JavaLanguageServerPlugin.logError(config.getAttribute("class") + " implementation was skipped \n" + e.getStatus());
			}
		}
		return supporters.values().stream();
	}

	@Override
	public boolean isBuildLikeFileName(String fileName) {
		return buildSupports().filter(bs -> bs.isBuildLikeFileName(fileName)).findAny().isPresent();
	}

	@Override
	public void prepareToSave(ISaveContext context) throws CoreException {
		if (context.getKind() == ISaveContext.FULL_SAVE) {
			GradleBuildSupport.saveModels();
		}
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
		logInfo(">> registerWatchers'");
		if (preferenceManager.getClientPreferences().isWorkspaceChangeWatchedFilesDynamicRegistered()) {
			Set<String> patterns = new LinkedHashSet<>(basicWatchers);
			buildSupports().forEach(e -> e.getWatchPatterns().forEach(patterns::add));
			Set<IPath> sources = new HashSet<>();
			IProject[] projects = ResourcesPlugin.getWorkspace().getRoot().getProjects();
			try {
				for (IProject project : projects) {
					if (DEFAULT_PROJECT_NAME.equals(project.getName())) {
						continue;
					}
					IJavaProject javaProject = JavaCore.create(project);
					if (javaProject != null && javaProject.exists()) {
						IClasspathEntry[] classpath = javaProject.getRawClasspath();
						for (IClasspathEntry entry : classpath) {
							if (entry.getEntryKind() == IClasspathEntry.CPE_SOURCE) {
								IPath path = entry.getPath();
								if (path != null && !path.toString().contains("/src/") && !path.toString().endsWith("/src")) {
									IFolder folder = ResourcesPlugin.getWorkspace().getRoot().getFolder(path);
									if (folder.exists() && !folder.isDerived()) {
										IPath location = folder.getLocation();
										if (location != null && !isContainedIn(location, sources)) {
											sources.add(location);
										}
									}

								}
							}
							if (entry.getEntryKind() == IClasspathEntry.CPE_LIBRARY) {
								IPath path = entry.getPath();
								IFile resource = ResourcesPlugin.getWorkspace().getRoot().getFile(path);
								if (resource != null && !resource.isDerived()) {
									IPath location = resource.getLocation();
									if (location != null && !isContainedIn(location, sources)) {
										sources.add(location);
									}
								}
							}
						}
						if (!ProjectUtils.isVisibleProject(project)) { // Invisible project will watch referenced libraries' include patterns
							IPath projectFolder = ProjectUtils.getProjectRealFolder(project);
							Set<String> libraries = preferenceManager.getPreferences().getReferencedLibraries().getInclude();
							for (String pattern: libraries) {
								patterns.add(ProjectUtils.resolveGlobPath(projectFolder, pattern).toPortableString());
							}
						}
					}
				}
			} catch (JavaModelException e) {
				JavaLanguageServerPlugin.logException(e.getMessage(), e);
			}
			List<FileSystemWatcher> fileWatchers = new ArrayList<>();
			URI formatter = preferenceManager.getPreferences().getFormatterAsURI();
			if (formatter != null && "file".equals(formatter.getScheme())) {
				File file = new File(formatter);
				if (file != null && file.isFile()) {
					IPath formatterPath = new Path(file.getAbsolutePath());
					if (!isContainedIn(formatterPath, sources)) {
						sources.add(formatterPath);
					}
				}
			}
			patterns.addAll(sources.stream().map(ResourceUtils::toGlobPattern).collect(Collectors.toList()));

			for (String pattern : patterns) {
				FileSystemWatcher watcher = new FileSystemWatcher(pattern);
				fileWatchers.add(watcher);
			}

			// Watch on project root folders.
			for (IProject project : projects) {
				if (ProjectUtils.isVisibleProject(project) && project.exists()) {
					FileSystemWatcher watcher = new FileSystemWatcher(
						ResourceUtils.toGlobPattern(project.getLocation(), false), WatchKind.Delete);
					fileWatchers.add(watcher);
				}
			}

			if (!patterns.equals(watchers)) {
				logInfo(">> registerFeature 'workspace/didChangeWatchedFiles'");
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

	@Override
	public void registerListeners() {
		if (this.preferenceChangeListener == null) {
			this.preferenceChangeListener = new IPreferencesChangeListener() {
				@Override
				public void preferencesChange(Preferences oldPreferences, Preferences newPreferences) {
					if (!oldPreferences.getReferencedLibraries().equals(newPreferences.getReferencedLibraries())) {
						registerWatcherJob.schedule(1000L);
						UpdateClasspathJob.getInstance().updateClasspath();
					}
					if (!Objects.equals(oldPreferences.getResourceFilters(), newPreferences.getResourceFilters())) {
						try {
							configureFilters(new NullProgressMonitor());
						} catch (CoreException e) {
							JavaLanguageServerPlugin.logException(e.getMessage(), e);
						}
					}
				}
			};
			this.preferenceManager.addPreferencesChangeListener(this.preferenceChangeListener);
		}
		buildSupports().forEach(p -> {
			try {
				p.registerPreferencesChangeListener(this.preferenceManager);
			} catch (CoreException e) {
				JavaLanguageServerPlugin.logException(e.getMessage(), e);
			}
		});
	}
}
