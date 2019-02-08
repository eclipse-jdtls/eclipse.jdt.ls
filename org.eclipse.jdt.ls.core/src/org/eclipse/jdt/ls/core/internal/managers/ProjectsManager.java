/*******************************************************************************
 * Copyright (c) 2016-2019 Red Hat Inc. and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Red Hat Inc. - initial API and implementation
 *     Microsoft Corporation
 *******************************************************************************/
package org.eclipse.jdt.ls.core.internal.managers;

import static java.util.Arrays.asList;
import static org.eclipse.jdt.ls.core.internal.JavaLanguageServerPlugin.logInfo;

import java.io.File;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.codehaus.plexus.util.StringUtils;
import org.eclipse.core.resources.IFolder;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IProjectDescription;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.ISaveContext;
import org.eclipse.core.resources.ISaveParticipant;
import org.eclipse.core.resources.IWorkspace;
import org.eclipse.core.resources.IWorkspaceDescription;
import org.eclipse.core.resources.IWorkspaceRoot;
import org.eclipse.core.resources.IWorkspaceRunnable;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.resources.WorkspaceJob;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IConfigurationElement;
import org.eclipse.core.runtime.IExtensionPoint;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.core.runtime.OperationCanceledException;
import org.eclipse.core.runtime.Platform;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.SubMonitor;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.jdt.core.IClasspathEntry;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IPackageFragmentRoot;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.launching.IVMInstall;
import org.eclipse.jdt.launching.JavaRuntime;
import org.eclipse.jdt.launching.environments.IExecutionEnvironment;
import org.eclipse.jdt.launching.environments.IExecutionEnvironmentsManager;
import org.eclipse.jdt.ls.core.internal.ActionableNotification;
import org.eclipse.jdt.ls.core.internal.IConstants;
import org.eclipse.jdt.ls.core.internal.IProjectImporter;
import org.eclipse.jdt.ls.core.internal.JDTUtils;
import org.eclipse.jdt.ls.core.internal.JavaClientConnection.JavaLanguageClient;
import org.eclipse.jdt.ls.core.internal.JavaLanguageServerPlugin;
import org.eclipse.jdt.ls.core.internal.JobHelpers;
import org.eclipse.jdt.ls.core.internal.ProjectUtils;
import org.eclipse.jdt.ls.core.internal.ResourceUtils;
import org.eclipse.jdt.ls.core.internal.ServiceStatus;
import org.eclipse.jdt.ls.core.internal.StatusFactory;
import org.eclipse.jdt.ls.core.internal.preferences.PreferenceManager;
import org.eclipse.jdt.ls.core.internal.preferences.Preferences;
import org.eclipse.jdt.ls.core.internal.preferences.Preferences.FeatureStatus;
import org.eclipse.lsp4j.Command;
import org.eclipse.lsp4j.DidChangeWatchedFilesRegistrationOptions;
import org.eclipse.lsp4j.FileSystemWatcher;
import org.eclipse.lsp4j.MessageType;
import org.eclipse.lsp4j.TextDocumentIdentifier;

public class ProjectsManager implements ISaveParticipant {

	public static final String DEFAULT_PROJECT_NAME = "jdt.ls-java-project";
	private static final Set<String> watchers = new HashSet<>();
	private PreferenceManager preferenceManager;
	private JavaLanguageClient client;

	public enum CHANGE_TYPE {
		CREATED, CHANGED, DELETED
	};

	private Job registerWatcherJob = new Job("Register Watchers") {

		@Override
		protected IStatus run(IProgressMonitor monitor) {
			JobHelpers.waitForJobsToComplete();
			registerWatchers();
			return Status.OK_STATUS;
		}

	};

	public ProjectsManager(PreferenceManager preferenceManager) {
		this.preferenceManager = preferenceManager;
	}

	public void initializeProjects(final Collection<IPath> rootPaths, IProgressMonitor monitor) throws CoreException, OperationCanceledException {
		// Run as a Java runnable to trigger any build while importing
		JavaCore.run(new IWorkspaceRunnable() {
			@Override
			public void run(IProgressMonitor monitor) throws CoreException {
				SubMonitor subMonitor = SubMonitor.convert(monitor, 100);
				deleteInvalidProjects(rootPaths, subMonitor.split(10));
				GradleBuildSupport.cleanGradleModels(subMonitor.split(10));
				createJavaProject(getDefaultProject(), subMonitor.split(10));
				cleanupResources(getDefaultProject());
				importProjects(rootPaths, subMonitor.split(70));
				subMonitor.done();
			}
		}, monitor);
	}

	private void importProjects(Collection<IPath> rootPaths, IProgressMonitor monitor) throws CoreException {
		SubMonitor subMonitor = SubMonitor.convert(monitor, rootPaths.size() * 100);
		for (IPath rootPath : rootPaths) {
			File rootFolder = rootPath.toFile();
			IProjectImporter importer = getImporter(rootFolder, subMonitor.split(30));
			if (importer != null) {
				importer.importToWorkspace(subMonitor.split(70));
			}
		}
	}

	public Job updateWorkspaceFolders(Collection<IPath> addedRootPaths, Collection<IPath> removedRootPaths) {
		JavaLanguageServerPlugin.sendStatus(ServiceStatus.Message, "Updating workspace folders: Adding " + addedRootPaths.size() + " folder(s), removing " + removedRootPaths.size() + " folders.");
		WorkspaceJob job = new WorkspaceJob("Updating workspace folders") {

			@Override
			public boolean belongsTo(Object family) {
				return IConstants.UPDATE_WORKSPACE_FOLDERS_FAMILY.equals(family) || IConstants.JOBS_FAMILY.equals(family);
			}

			@Override
			public IStatus runInWorkspace(IProgressMonitor monitor) {
				IStatus status = Status.OK_STATUS;
				SubMonitor subMonitor = SubMonitor.convert(monitor, addedRootPaths.size() + removedRootPaths.size());
				try {
					long start = System.currentTimeMillis();
					IProject[] projects = getWorkspaceRoot().getProjects();
					for (IProject project : projects) {
						if (ResourceUtils.isContainedIn(project.getLocation(), removedRootPaths)) {
							try {
								project.delete(false, true, subMonitor.split(1));
							} catch (CoreException e) {
								JavaLanguageServerPlugin.logException("Problems removing '" + project.getName() + "' from workspace.", e);
							}
						}
					}
					importProjects(addedRootPaths, subMonitor.split(addedRootPaths.size()));
					registerWatcherJob.schedule();
					long elapsed = System.currentTimeMillis() - start;

					JavaLanguageServerPlugin.logInfo("Updated workspace folders in " + elapsed + " ms: Added " + addedRootPaths.size() + " folder(s), removed" + removedRootPaths.size() + " folders.");
					JavaLanguageServerPlugin.logInfo(getWorkspaceInfo());
					return Status.OK_STATUS;
				} catch (CoreException e) {
					String msg = "Error updating workspace folders";
					JavaLanguageServerPlugin.logError(msg);
					status = StatusFactory.newErrorStatus(msg, e);
				}
				GradleBuildSupport.cleanGradleModels(monitor);
				return status;
			}
		};
		job.setRule(getWorkspaceRoot());
		job.schedule();
		return job;
	}

	public void cleanupResources(IProject project) throws CoreException {
		IJavaProject javaProj = JavaCore.create(project);
		if (javaProj == null) {
			return;
		}

		Arrays.stream(javaProj.getPackageFragments()).filter(packageFragment -> {
			try {
				return packageFragment.containsJavaResources() && packageFragment.getKind() == IPackageFragmentRoot.K_SOURCE;
			} catch (CoreException e) {
				JavaLanguageServerPlugin.logException("Unable to collect " + project.getName() + "' package fragements", e);
			}
			return false;
		}).flatMap(packageFragment -> {
			try {
				return Arrays.stream(packageFragment.getCompilationUnits());
			} catch (CoreException e) {
				JavaLanguageServerPlugin.logException("Unable to collect " + project.getName() + "'s compilation units", e);
			}
			return null;
		}).forEach((cu) -> {
			try {
				IResource resource = cu.getResource();
				if (resource.isLinked()) {
					File f = new File(cu.getUnderlyingResource().getLocationURI());
					if (!f.exists()) {
						cu.delete(true, null);
					}
				}
			} catch (CoreException e) {
				JavaLanguageServerPlugin.logException("Unable to delete missing compilation unit (" + cu.getElementName() + ") from " + project.getName(), e);
			}
		});
	}

	private void deleteInvalidProjects(Collection<IPath> rootPaths, IProgressMonitor monitor) {
		List<String> workspaceProjects = rootPaths.stream().map((IPath rootPath) -> ProjectUtils.getWorkspaceInvisibleProjectName(rootPath)).collect(Collectors.toList());
		for (IProject project : getWorkspaceRoot().getProjects()) {
			if (project.exists() && (ResourceUtils.isContainedIn(project.getLocation(), rootPaths) || ProjectUtils.isGradleProject(project)) || workspaceProjects.contains(project.getName())) {
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

	private static IWorkspaceRoot getWorkspaceRoot() {
		return ResourcesPlugin.getWorkspace().getRoot();
	}

	public void fileChanged(String uriString, CHANGE_TYPE changeType) {
		if (uriString == null) {
			return;
		}
		IResource resource = JDTUtils.isFolder(uriString) ? JDTUtils.findFolder(uriString) : JDTUtils.findFile(uriString);
		if (resource == null) {
			return;
		}
		String formatterUrl = preferenceManager.getPreferences().getFormatterUrl();
		if (formatterUrl != null) {
			try {
				URL url = getUrl(formatterUrl);
				URI formatterUri = url.toURI();
				URI uri = JDTUtils.toURI(uriString);
				if (uri != null && uri.equals(formatterUri) && JavaLanguageServerPlugin.getInstance().getProtocol() != null) {
					if (changeType == CHANGE_TYPE.DELETED || changeType == CHANGE_TYPE.CREATED) {
						registerWatchers();
					}
					FormatterManager.configureFormatter(preferenceManager, this);
				}
			} catch (URISyntaxException e) {
				// ignore
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

	public URL getUrl(String formatterUrl) {
		URL url = null;
		try {
			url = new URL(ResourceUtils.toClientUri(formatterUrl));
		} catch (MalformedURLException e1) {
			File file = findFile(formatterUrl);
			if (file != null && file.isFile()) {
				try {
					url = file.toURI().toURL();
				} catch (MalformedURLException e) {
					JavaLanguageServerPlugin.logInfo("Invalid formatter:" + formatterUrl);
				}
			}
		}
		return url;
	}

	public boolean isBuildFile(IResource resource) {
		return buildSupports().filter(bs -> bs.isBuildFile(resource)).findAny().isPresent();
	}

	private IProjectImporter getImporter(File rootFolder, IProgressMonitor monitor) throws OperationCanceledException, CoreException {
		Collection<IProjectImporter> importers = importers();
		SubMonitor subMonitor = SubMonitor.convert(monitor, importers.size());
		for (IProjectImporter importer : importers) {
			importer.initialize(rootFolder);
			if (importer.applies(subMonitor.split(1))) {
				return importer;
			}
		}
		return null;
	}

	public IProject getDefaultProject() {
		return getWorkspaceRoot().getProject(DEFAULT_PROJECT_NAME);
	}

	private Collection<IProjectImporter> importers() {
		Map<Integer, IProjectImporter> importers = new TreeMap<>();
		IExtensionPoint extensionPoint = Platform.getExtensionRegistry().getExtensionPoint(IConstants.PLUGIN_ID, "importers");
		IConfigurationElement[] configs = extensionPoint.getConfigurationElements();
		for (int i = 0; i < configs.length; i++) {
			try {
				Integer order = Integer.valueOf(configs[i].getAttribute("order"));
				importers.put(order, (IProjectImporter) configs[i].createExecutableExtension("class")); //$NON-NLS-1$
			} catch (CoreException e) {
				JavaLanguageServerPlugin.log(e.getStatus());
			}
		}
		return importers.values();
	}

	public IProject createJavaProject(IProject project, IProgressMonitor monitor) throws CoreException, OperationCanceledException {
		return createJavaProject(project, null, "src", "bin", monitor);
	}

	public IProject createJavaProject(IProject project, IPath projectLocation, String src, String bin, IProgressMonitor monitor) throws CoreException, OperationCanceledException {
		if (project.exists()) {
			return project;
		}
		JavaLanguageServerPlugin.logInfo("Creating the Java project " + project.getName());
		//Create project
		IProjectDescription description = ResourcesPlugin.getWorkspace().newProjectDescription(project.getName());
		if (projectLocation != null) {
			description.setLocation(projectLocation);
		}
		project.create(description, monitor);
		project.open(monitor);

		//Turn into Java project
		description = project.getDescription();
		description.setNatureIds(new String[] { JavaCore.NATURE_ID });
		project.setDescription(description, monitor);
		IJavaProject javaProject = JavaCore.create(project);

		//Add build output folder
		if (StringUtils.isNotBlank(bin)) {
			IFolder output = project.getFolder(bin);
			if (!output.exists()) {
				output.create(true, true, monitor);
			}
			javaProject.setOutputLocation(output.getFullPath(), monitor);
		}

		List<IClasspathEntry> classpaths = new ArrayList<>();
		//Add source folder
		if (StringUtils.isNotBlank(src)) {
			IFolder source = project.getFolder(src);
			if (!source.exists()) {
				source.create(true, true, monitor);
			}
			IPackageFragmentRoot root = javaProject.getPackageFragmentRoot(source);
			IClasspathEntry srcClasspath = JavaCore.newSourceEntry(root.getPath());
			classpaths.add(srcClasspath);
		}

		//Find default JVM
		IClasspathEntry jre = JavaRuntime.getDefaultJREContainerEntry();
		classpaths.add(jre);

		//Add JVM to project class path
		javaProject.setRawClasspath(classpaths.toArray(new IClasspathEntry[0]), monitor);

		JavaLanguageServerPlugin.logInfo("Finished creating the Java project " + project.getName());
		return project;
	}

	public Job updateProject(IProject project, boolean force) {
		if (project == null || (!ProjectUtils.isMavenProject(project) && !ProjectUtils.isGradleProject(project))) {
			return null;
		}
		JavaLanguageServerPlugin.sendStatus(ServiceStatus.Message, "Updating " + project.getName() + " configuration");
		WorkspaceJob job = new WorkspaceJob("Update project " + project.getName()) {

			@Override
			public boolean belongsTo(Object family) {
				return IConstants.UPDATE_PROJECT_FAMILY.equals(family) || (IConstants.JOBS_FAMILY + "." + project.getName()).equals(family) || IConstants.JOBS_FAMILY.equals(family);
			}

			@Override
			public IStatus runInWorkspace(IProgressMonitor monitor) {
				IStatus status = Status.OK_STATUS;
				String projectName = project.getName();
				SubMonitor progress = SubMonitor.convert(monitor, 100).checkCanceled();
				try {
					long start = System.currentTimeMillis();
					project.refreshLocal(IResource.DEPTH_INFINITE, progress.split(5));
					Optional<IBuildSupport> buildSupport = getBuildSupport(project);
					if (buildSupport.isPresent()) {
						buildSupport.get().update(project, force, progress.split(95));
						registerWatcherJob.schedule();
					}
					long elapsed = System.currentTimeMillis() - start;
					JavaLanguageServerPlugin.logInfo("Updated " + projectName + " in " + elapsed + " ms");
				} catch (CoreException e) {
					String msg = "Error updating " + projectName;
					JavaLanguageServerPlugin.logError(msg);
					status = StatusFactory.newErrorStatus(msg, e);
				}
				return status;
			}
		};
		job.schedule();
		return job;
	}

	private Optional<IBuildSupport> getBuildSupport(IProject project) {
		return buildSupports().filter(bs -> bs.applies(project)).findFirst();
	}

	private Stream<IBuildSupport> buildSupports() {
		return Stream.of(new GradleBuildSupport(), new MavenBuildSupport(), new InvisibleProjectBuildSupport(), new DefaultProjectBuildSupport(this), new EclipseBuildSupport());
	}

	public void setConnection(JavaLanguageClient client) {
		this.client = client;
	}

	private String getWorkspaceInfo() {
		StringBuilder b = new StringBuilder();
		b.append("Projects:\n");
		for (IProject project : getWorkspaceRoot().getProjects()) {
			b.append(project.getName()).append(": ").append(project.getLocation().toOSString()).append('\n');
			if (ProjectUtils.isJavaProject(project)) {
				IJavaProject javaProject = JavaCore.create(project);
				try {
					b.append("  resolved classpath:\n");
					IClasspathEntry[] cpEntries = javaProject.getRawClasspath();
					for (IClasspathEntry cpe : cpEntries) {
						b.append("  ").append(cpe.getPath().toString()).append('\n');
						if (cpe.getEntryKind() == IClasspathEntry.CPE_CONTAINER) {
							IPackageFragmentRoot[] roots = javaProject.findPackageFragmentRoots(cpe);
							for (IPackageFragmentRoot root : roots) {
								b.append("    ").append(root.getPath().toString()).append('\n');
							}
						}
					}
				} catch (CoreException e) {
					// ignore
				}
			} else {
				b.append("  non-Java project\n");
			}
		}
		b.append("Java Runtimes:\n");
		IVMInstall defaultVMInstall = JavaRuntime.getDefaultVMInstall();
		b.append("  default: ");
		if (defaultVMInstall != null) {
			b.append(defaultVMInstall.getInstallLocation().toString());
		} else {
			b.append("-");
		}
		IExecutionEnvironmentsManager eem = JavaRuntime.getExecutionEnvironmentsManager();
		for (IExecutionEnvironment ee : eem.getExecutionEnvironments()) {
			IVMInstall[] vms = ee.getCompatibleVMs();
			b.append("  ").append(ee.getDescription()).append(": ");
			if (vms.length > 0) {
				b.append(vms[0].getInstallLocation().toString());
			} else {
				b.append("-");
			}
			b.append("\n");
		}
		return b.toString();
	}

	/* (non-Javadoc)
	 * @see org.eclipse.core.resources.ISaveParticipant#doneSaving(org.eclipse.core.resources.ISaveContext)
	 */
	@Override
	public void doneSaving(ISaveContext context) {
	}

	/* (non-Javadoc)
	 * @see org.eclipse.core.resources.ISaveParticipant#prepareToSave(org.eclipse.core.resources.ISaveContext)
	 */
	@Override
	public void prepareToSave(ISaveContext context) throws CoreException {
		if (context.getKind() == ISaveContext.FULL_SAVE) {
			GradleBuildSupport.saveModels();
		}
	}

	/* (non-Javadoc)
	 * @see org.eclipse.core.resources.ISaveParticipant#rollback(org.eclipse.core.resources.ISaveContext)
	 */
	@Override
	public void rollback(ISaveContext context) {
	}

	/* (non-Javadoc)
	 * @see org.eclipse.core.resources.ISaveParticipant#saving(org.eclipse.core.resources.ISaveContext)
	 */
	@Override
	public void saving(ISaveContext context) throws CoreException {
	}

	public boolean setAutoBuilding(boolean enable) throws CoreException {
		IWorkspace workspace = ResourcesPlugin.getWorkspace();
		IWorkspaceDescription description = workspace.getDescription();
		boolean changed = description.isAutoBuilding() != enable;
		if (changed) {
			description.setAutoBuilding(enable);
			workspace.setDescription(description);
		}
		return changed;
	}

	public List<FileSystemWatcher> registerWatchers() {
		logInfo(">> registerFeature 'workspace/didChangeWatchedFiles'");
		if (preferenceManager.getClientPreferences().isWorkspaceChangeWatchedFilesDynamicRegistered()) {
			Set<String> sources = new HashSet<>();
			try {
				IProject[] projects = ResourcesPlugin.getWorkspace().getRoot().getProjects();
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
								if (path != null) {
									IFolder folder = ResourcesPlugin.getWorkspace().getRoot().getFolder(path);
									if (folder.exists() && !folder.isDerived()) {
										IPath location = folder.getLocation();
										if (location != null) {
											sources.add(location.toString() + "/**");
										}
									}

								}
							}
						}
						if (!ProjectUtils.isVisibleProject(project)) {
							//watch lib folder for invisible projects
							IPath realFolderPath = project.getFolder(ProjectUtils.WORKSPACE_LINK).getLocation();
							if (realFolderPath != null) {
								IPath libFolderPath = realFolderPath.append(InvisibleProjectBuildSupport.LIB_FOLDER);
								sources.add(libFolderPath.toString() + "/**");
							}
						}
					}
				}
			} catch (JavaModelException e) {
				JavaLanguageServerPlugin.logException(e.getMessage(), e);
			}
			List<FileSystemWatcher> fileWatchers = new ArrayList<>();
			String formatterUrl = preferenceManager.getPreferences().getFormatterUrl();
			if (formatterUrl != null) {
				File file = new File(formatterUrl);
				if (!file.isFile()) {
					file = findFile(formatterUrl);
				}
				if (file != null && file.isFile()) {
					sources.add(file.getAbsolutePath());
				}
			}
			for (String pattern : sources) {
				FileSystemWatcher watcher = new FileSystemWatcher(pattern);
				fileWatchers.add(watcher);
			}
			if (!sources.equals(watchers)) {
				logInfo(">> registerFeature 'workspace/didChangeWatchedFiles'");
				DidChangeWatchedFilesRegistrationOptions didChangeWatchedFilesRegistrationOptions = new DidChangeWatchedFilesRegistrationOptions(fileWatchers);
				JavaLanguageServerPlugin.getInstance().unregisterCapability(Preferences.WORKSPACE_WATCHED_FILES_ID, Preferences.WORKSPACE_WATCHED_FILES);
				JavaLanguageServerPlugin.getInstance().registerCapability(Preferences.WORKSPACE_WATCHED_FILES_ID, Preferences.WORKSPACE_WATCHED_FILES, didChangeWatchedFilesRegistrationOptions);
				watchers.clear();
				watchers.addAll(sources);
			}
			return fileWatchers;
		}
		return Collections.emptyList();
	}

	public File findFile(String formatterUrl) {
		File file = new File(formatterUrl);
		if (file.exists()) {
			return file;
		}
		Collection<IPath> rootPaths = preferenceManager.getPreferences().getRootPaths();
		if (rootPaths != null) {
			for (IPath rootPath : rootPaths) {
				File f = new File(rootPath.toOSString(), formatterUrl);
				if (f.isFile()) {
					return f;
				}
			}
		}
		return null;
	}
}
