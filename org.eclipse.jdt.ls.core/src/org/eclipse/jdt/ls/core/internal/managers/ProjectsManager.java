/*******************************************************************************
 * Copyright (c) 2016-2022 Red Hat Inc. and others.
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

import static org.eclipse.jdt.ls.core.internal.JVMConfigurator.configureJVMSettings;

import java.io.File;
import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.commons.lang3.StringUtils;
import org.eclipse.core.internal.resources.CharsetManager;
import org.eclipse.core.internal.resources.Workspace;
import org.eclipse.core.resources.FileInfoMatcherDescription;
import org.eclipse.core.resources.IContainer;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IFolder;
import org.eclipse.core.resources.IMarker;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IProjectDescription;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IResourceFilterDescription;
import org.eclipse.core.resources.ISaveContext;
import org.eclipse.core.resources.ISaveParticipant;
import org.eclipse.core.resources.IWorkspace;
import org.eclipse.core.resources.IWorkspaceDescription;
import org.eclipse.core.resources.IWorkspaceRoot;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.resources.WorkspaceJob;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IConfigurationElement;
import org.eclipse.core.runtime.IExtensionPoint;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.MultiStatus;
import org.eclipse.core.runtime.OperationCanceledException;
import org.eclipse.core.runtime.Platform;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.SubMonitor;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.jdt.core.IClasspathEntry;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IPackageFragmentRoot;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.launching.IVMInstall;
import org.eclipse.jdt.launching.JavaRuntime;
import org.eclipse.jdt.launching.environments.IExecutionEnvironment;
import org.eclipse.jdt.launching.environments.IExecutionEnvironmentsManager;
import org.eclipse.jdt.ls.core.internal.EventNotification;
import org.eclipse.jdt.ls.core.internal.EventType;
import org.eclipse.jdt.ls.core.internal.IConstants;
import org.eclipse.jdt.ls.core.internal.IProjectImporter;
import org.eclipse.jdt.ls.core.internal.JDTEnvironmentUtils;
import org.eclipse.jdt.ls.core.internal.JDTUtils;
import org.eclipse.jdt.ls.core.internal.JavaClientConnection.JavaLanguageClient;
import org.eclipse.jdt.ls.core.internal.LogReader.LogEntry;
import org.eclipse.jdt.ls.core.internal.JavaLanguageServerPlugin;
import org.eclipse.jdt.ls.core.internal.JobHelpers;
import org.eclipse.jdt.ls.core.internal.ProjectUtils;
import org.eclipse.jdt.ls.core.internal.ResourceUtils;
import org.eclipse.jdt.ls.core.internal.ServiceStatus;
import org.eclipse.jdt.ls.core.internal.StatusFactory;
import org.eclipse.jdt.ls.core.internal.handlers.BaseInitHandler;
import org.eclipse.jdt.ls.core.internal.handlers.ProjectEncodingMode;
import org.eclipse.jdt.ls.core.internal.preferences.PreferenceManager;
import org.eclipse.lsp4j.PublishDiagnosticsParams;

import com.google.gson.JsonObject;

public abstract class ProjectsManager implements ISaveParticipant, IProjectsManager {

	public static final String DEFAULT_PROJECT_NAME = "jdt.ls-java-project";
	public static final String PROJECTS_IMPORTED = "__PROJECTS_IMPORTED__";
	private static final String CORE_RESOURCES_MATCHER_ID = "org.eclipse.core.resources.regexFilterMatcher";
	public static final String CREATED_BY_JAVA_LANGUAGE_SERVER = "__CREATED_BY_JAVA_LANGUAGE_SERVER__";
	public static final String BUILD_FILE_MARKER_TYPE = "org.eclipse.jdt.ls.buildFileMarker";
	private static final int JDTLS_FILTER_TYPE = IResourceFilterDescription.EXCLUDE_ALL | IResourceFilterDescription.INHERITABLE | IResourceFilterDescription.FILES | IResourceFilterDescription.FOLDERS;

	protected final PreferenceManager preferenceManager;
	protected final TelemetryManager telemetryManager;

	protected JavaLanguageClient client;

	public enum CHANGE_TYPE {
		CREATED, CHANGED, DELETED
	};

	public ProjectsManager(PreferenceManager preferenceManager, TelemetryManager telemetryManager) {
		this.preferenceManager = preferenceManager;
		this.telemetryManager = telemetryManager;
	}

	@Override
	public void initializeProjects(final Collection<IPath> rootPaths, IProgressMonitor monitor) throws CoreException, OperationCanceledException {
		if (!preferenceManager.getClientPreferences().skipProjectConfiguration()) {
			SubMonitor subMonitor = SubMonitor.convert(monitor, 100);
			cleanInvalidProjects(rootPaths, subMonitor.split(20));
			if (rootPaths.isEmpty() && !ProjectsManager.getDefaultProject().exists()) {
				ProjectsManager.createJavaProject(ProjectsManager.getDefaultProject(), subMonitor.split(10));
				ProjectsManager.cleanupResources(ProjectsManager.getDefaultProject());
			}
			Collection<IPath> projectConfigurations = preferenceManager.getPreferences().getProjectConfigurations();
			if (projectConfigurations == null) {
				// old way to import project
				importProjects(rootPaths, subMonitor.split(70));
			} else {
				importProjectsFromConfigurationFiles(rootPaths, projectConfigurations, monitor);
			}
			updateEncoding(monitor);
			reportProjectsStatus();
			subMonitor.done();
		}
	}

	private void updateEncoding(IProgressMonitor monitor) throws CoreException {
		if (preferenceManager != null && ProjectEncodingMode.SETDEFAULT.equals(preferenceManager.getPreferences().getProjectEncoding())) {
			IWorkspace workspace = ResourcesPlugin.getWorkspace();
			if (workspace instanceof Workspace ws) {
				CharsetManager charsetManager = ws.getCharsetManager();
				String encoding = ResourcesPlugin.getEncoding();
				for (IProject project : ProjectUtils.getAllProjects()) {
					if (!Objects.equals(encoding, project.getDefaultCharset(false))) {
						try {
							charsetManager.setCharsetFor(project.getFullPath(), encoding);
						} catch (CoreException e) {
							JavaLanguageServerPlugin.logException(e.getMessage(), e);
						}
					}
				}
			}
		}
	}

	protected void importProjects(Collection<IPath> rootPaths, IProgressMonitor monitor) throws CoreException, OperationCanceledException {
		SubMonitor subMonitor = SubMonitor.convert(monitor, rootPaths.size() * 100);
		MultiStatus importStatusCollection = new MultiStatus(IConstants.PLUGIN_ID, -1, "Failed to import projects", null);
		for (IPath rootPath : rootPaths) {
			File rootFolder = rootPath.toFile();
			try {
				for (IProjectImporter importer : importers()) {
					JsonObject properties = new JsonObject();
					properties.addProperty("importer", importer.getClass().getName());
					try {
						importer.initialize(rootFolder);
						if (importer.applies(subMonitor.split(1))) {
							importer.importToWorkspace(subMonitor.split(70));
							boolean resolved = importer.isResolved(rootFolder);
							properties.addProperty("resolved", resolved);
							this.telemetryManager.sendEvent(new TelemetryEvent(TelemetryEvent.IMPORT_PROJECT, properties));
							if (resolved) {
								break;
							}
						}
					} catch (CoreException e) {
						TelemetryEvent.addLogEntryProperties(properties, LogEntry.from(e.getStatus()));
						this.telemetryManager.sendEvent(new TelemetryEvent(TelemetryEvent.IMPORT_PROJECT, properties));
						throw e;
					}
				}
			} catch (CoreException e) {
				// if a rootPath import failed, keep importing the next rootPath
				importStatusCollection.add(e.getStatus());
				JavaLanguageServerPlugin.logException("Failed to import projects", e);
			}
		}
		if (!importStatusCollection.isOK()) {
			throw new CoreException(importStatusCollection);
		}
	}

	protected void importProjectsFromConfigurationFiles(Collection<IPath> rootPaths, Collection<IPath> projectConfigurations, IProgressMonitor monitor) throws OperationCanceledException, CoreException {
		SubMonitor subMonitor = SubMonitor.convert(monitor, rootPaths.size() * 100);
		MultiStatus importStatusCollection = new MultiStatus(IConstants.PLUGIN_ID, -1, "Failed to import projects", null);
		for (IPath rootPath : rootPaths) {
			File rootFolder = rootPath.toFile();
			Set<IPath> buildFiles = projectConfigurations.stream()
					.filter(rootPath::isPrefixOf).collect(Collectors.toSet());
			try {
				for (IProjectImporter importer : importers()) {
					JsonObject properties = new JsonObject();
					properties.addProperty("importer", importer.getClass().getName());
					try {
						importer.initialize(rootFolder);
						if (importer.applies(buildFiles, subMonitor.split(1))) {
							importer.importToWorkspace(subMonitor.split(70));
							buildFiles = removeImportedConfigurations(buildFiles, importer);
							this.telemetryManager.sendEvent(new TelemetryEvent(TelemetryEvent.IMPORT_PROJECT, properties));
						}
					} catch (CoreException e) {
						TelemetryEvent.addLogEntryProperties(properties, LogEntry.from(e.getStatus()));
						this.telemetryManager.sendEvent(new TelemetryEvent(TelemetryEvent.IMPORT_PROJECT, properties));
						throw e;
					}
				}
			} catch (CoreException e) {
				// if a rootPath import failed, keep importing the next rootPath
				importStatusCollection.add(e.getStatus());
				JavaLanguageServerPlugin.logException("Failed to import projects", e);
			}
		}
		if (!importStatusCollection.isOK()) {
			throw new CoreException(importStatusCollection);
		}
	}

	private Set<IPath> removeImportedConfigurations(Set<IPath> configurations, IProjectImporter importer) {
		return configurations.stream()
			.filter(config -> {
			try {
				return !importer.isResolved(config.toFile());
			} catch (OperationCanceledException | CoreException e) {
				return true;
			}
			})
			.collect(Collectors.toSet());
	}

	public void importProjects(IProgressMonitor monitor) {
		WorkspaceJob job = new WorkspaceJob("Importing projects in workspace...") {

			@Override
			public IStatus runInWorkspace(IProgressMonitor monitor) throws CoreException {
				try {
					importProjects(preferenceManager.getPreferences().getRootPaths(), monitor);
				} catch (OperationCanceledException e) {
					return Status.CANCEL_STATUS;
				} catch (CoreException e) {
					return new Status(Status.ERROR, IConstants.PLUGIN_ID, "Importing projects failed.", e);
				}
				List<URI> projectUris = Arrays.stream(getWorkspaceRoot().getProjects())
					.map(project -> ProjectUtils.getProjectRealFolder(project).toFile().toURI())
					.collect(Collectors.toList());

				EventNotification notification = new EventNotification().withType(EventType.ProjectsImported).withData(projectUris);
				client.sendEventNotification(notification);
				reportProjectsStatus();
				return Status.OK_STATUS;
			}
		};
		job.setRule(getWorkspaceRoot());
		job.schedule();
	}

	public void changeImportedProjects(Collection<String> toImport, Collection<String> toUpdate,
			Collection<String> toDelete, IProgressMonitor monitor) {
		Set<IPath> filesToImport = new HashSet<>();
		for (String uri : toImport) {
			filesToImport.add(ResourceUtils.canonicalFilePathFromURI(uri));
		}

		Set<IProject> projectsToUpdate = new HashSet<>();
		for (String uri : toUpdate) {
			IContainer folder = JDTUtils.findFolder(uri);
			if (folder == null) {
				continue;
			}
			IProject project = folder.getProject();
			if (project != null) {
				projectsToUpdate.add(project);
			}
		}

		Set<IProject> projectsToDelete = new HashSet<>();
		for (String uri : toDelete) {
			IContainer folder = JDTUtils.findFolder(uri);
			if (folder == null) {
				continue;
			}
			IProject project = folder.getProject();
			if (project != null) {
				projectsToDelete.add(project);
			}
		}

		WorkspaceJob job = new ImportProjectsFromSelectionJob(filesToImport, projectsToUpdate, projectsToDelete);
		job.setRule(getWorkspaceRoot());
		job.schedule();
	}

	@Override
	public Job updateWorkspaceFolders(Collection<IPath> addedRootPaths, Collection<IPath> removedRootPaths) {
		JavaLanguageServerPlugin.sendStatus(ServiceStatus.Message, "Updating workspace folders: Adding " + addedRootPaths.size() + " folder(s), removing " + removedRootPaths.size() + " folders.");
		Job[] removedJobs = Job.getJobManager().find(removedRootPaths);
		for (Job removedJob : removedJobs) {
			if (removedJob.belongsTo(IConstants.UPDATE_WORKSPACE_FOLDERS_FAMILY) || removedJob.belongsTo(BaseInitHandler.JAVA_LS_INITIALIZATION_JOBS)) {
				removedJob.cancel();
			}
		}
		WorkspaceJob job = new WorkspaceJob("Updating workspace folders") {

			@SuppressWarnings("unchecked")
			@Override
			public boolean belongsTo(Object family) {
				Collection<IPath> addedRootPathsSet = addedRootPaths.stream().collect(Collectors.toSet());
				boolean equalToRootPaths = false;
				if (family instanceof Collection<?>) {
					equalToRootPaths = addedRootPathsSet.equals(((Collection<IPath>) family).stream().collect(Collectors.toSet()));
				}
				return IConstants.UPDATE_WORKSPACE_FOLDERS_FAMILY.equals(family) || IConstants.JOBS_FAMILY.equals(family) || equalToRootPaths;
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
					registerWatchers(true);
					long elapsed = System.currentTimeMillis() - start;

					JavaLanguageServerPlugin.logInfo("Updated workspace folders in " + elapsed + " ms: Added " + addedRootPaths.size() + " folder(s), removed" + removedRootPaths.size() + " folders.");
					JavaLanguageServerPlugin.logInfo(getWorkspaceInfo());
					return Status.OK_STATUS;
				} catch (CoreException e) {
					String msg = "Error updating workspace folders";
					JavaLanguageServerPlugin.logError(msg);
					status = StatusFactory.newErrorStatus(msg, e);
				}

				cleanInvalidProjects(preferenceManager.getPreferences().getRootPaths(), monitor);
				return status;
			}
		};
		job.setRule(getWorkspaceRoot());
		job.schedule();
		return job;
	}

	public static void cleanupResources(IProject project) throws CoreException {
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

	private static IWorkspaceRoot getWorkspaceRoot() {
		return ResourcesPlugin.getWorkspace().getRoot();
	}

	@Override
	public boolean isBuildFile(IResource resource) {
		return buildSupports().filter(bs -> bs.isBuildFile(resource)).findAny().isPresent();
	}

	@Override
	public boolean isBuildLikeFileName(String fileName) {
		return buildSupports().filter(bs -> bs.isBuildLikeFileName(fileName)).findAny().isPresent();
	}

	public static IProject getDefaultProject() {
		return getWorkspaceRoot().getProject(DEFAULT_PROJECT_NAME);
	}

	public static Collection<IProjectImporter> importers() {
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

	public static IProject createJavaProject(IProject project, IProgressMonitor monitor) throws CoreException, OperationCanceledException {
		return createJavaProject(project, null, "src", "bin", monitor);
	}

	public static IProject createJavaProject(IProject project, IPath projectLocation, String src, String bin, IProgressMonitor monitor) throws CoreException, OperationCanceledException {
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
		configureJVMSettings(javaProject);
		if (!JDTEnvironmentUtils.isSyntaxServer()) {
			JobHelpers.waitForAutoBuildJobs(JobHelpers.MAX_TIME_MILLIS);
		}
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

	@Override
	public Job updateProject(IProject project, boolean force) {
		if (project == null || ProjectUtils.isInternalBuildSupport(BuildSupportManager.find(project).orElse(null))) {
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
						registerWatchers(true);
					}
					updateEncoding(monitor);
					project.deleteMarkers(BUILD_FILE_MARKER_TYPE, false, IResource.DEPTH_ONE);
					long elapsed = System.currentTimeMillis() - start;
					JavaLanguageServerPlugin.logInfo("Updated " + projectName + " in " + elapsed + " ms");
				} catch (Exception e) {
					String msg = "Error updating " + projectName;
					JavaLanguageServerPlugin.logError(msg);
					status = StatusFactory.newErrorStatus(msg, e);
				}
				reportProjectsStatus();
				return status;
			}
		};
		waitForUpdateJobs();
		job.schedule();
		return job;
	}

	private void waitForUpdateJobs() {
		int maxThread = preferenceManager.getPreferences().getMaxConcurrentBuilds();
		Job[] jobs = Job.getJobManager().find(IConstants.UPDATE_PROJECT_FAMILY);
		if (jobs != null && jobs.length > maxThread) {
			JobHelpers.waitForJobs(IConstants.UPDATE_PROJECT_FAMILY, null);
		}
	}

	@Override
	public Job updateProjects(Collection<IProject> projects, boolean force) {
		JavaLanguageServerPlugin.sendStatus(ServiceStatus.Message, "Updating project configurations...");
		UpdateProjectsWorkspaceJob job = new UpdateProjectsWorkspaceJob(projects, force);
		waitForUpdateJobs();
		job.schedule();
		return job;
	}

	@Override
	public Optional<IBuildSupport> getBuildSupport(IProject project) {
		return buildSupports().filter(bs -> bs.applies(project)).findFirst();
	}

	/**
	 * Check if the build support for the specified project depends on the default VM.
	 */
	public boolean useDefaultVM(IProject project, IVMInstall defaultVM) {
		if (project == null) {
			return false;
		}

		IBuildSupport buildSupport = getBuildSupport(project).orElse(null);
		if (buildSupport != null) {
			return buildSupport.useDefaultVM(project, defaultVM);
		}

		return false;
	}

	protected Stream<IBuildSupport> buildSupports() {
		return Stream.of(new EclipseBuildSupport());
	}

	public void setConnection(JavaLanguageClient client) {
		this.client = client;
	}

	public JavaLanguageClient getConnection() {
		return this.client;
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

	public static boolean setAutoBuilding(boolean enable) throws CoreException {
		IWorkspace workspace = ResourcesPlugin.getWorkspace();
		IWorkspaceDescription description = workspace.getDescription();
		boolean changed = description.isAutoBuilding() != enable;
		if (changed) {
			description.setAutoBuilding(enable);
			workspace.setDescription(description);
		}
		return changed;
	}

	public static Runnable interruptAutoBuild() throws CoreException {
		IWorkspace workspace = ResourcesPlugin.getWorkspace();
		if (workspace instanceof Workspace ws) {
			ws.getBuildManager().interrupt();
			return () -> {
			};
		} else {
			boolean changed = setAutoBuilding(false);
			if (changed) {
				return () -> {
					try {
						setAutoBuilding(true);
					} catch (CoreException e) {
						// ignore
					}
				};
			} else {
				return () -> {
				};
			}
		}
	}

	public void configureFilters(IProgressMonitor monitor) throws CoreException {
		List<String> resourceFilters = preferenceManager.getPreferences().getResourceFilters();
		if (resourceFilters != null && !resourceFilters.isEmpty()) {
			resourceFilters = new ArrayList<>(resourceFilters);
			resourceFilters.add(CREATED_BY_JAVA_LANGUAGE_SERVER);
		}
		String resourceFilter = resourceFilters == null ? null : String.join("|", resourceFilters);
		for (IProject project : ProjectUtils.getAllProjects()) {
			if (project.equals(getDefaultProject())) {
				continue;
			}
			List<IResourceFilterDescription> filters = Stream.of(project.getFilters())
					.filter(f -> {
				FileInfoMatcherDescription matcher = f.getFileInfoMatcherDescription();
				return CORE_RESOURCES_MATCHER_ID.equals(matcher.getId()) && matcher.getArguments() instanceof String args && args.contains(CREATED_BY_JAVA_LANGUAGE_SERVER);
					})
					.collect(Collectors.toList());
			boolean filterExists = false;
			for (IResourceFilterDescription filter : filters) {
				if (resourceFilter == null || resourceFilter.isEmpty()) {
					filter.delete(IResource.BACKGROUND_REFRESH, monitor);
				} else if (!Objects.equals(resourceFilter, filter.getFileInfoMatcherDescription().getArguments())) {
					filter.delete(IResource.BACKGROUND_REFRESH, monitor);
				} else {
					filterExists = true;
					break;
				}
			}
			if (!filterExists && resourceFilter != null && !resourceFilter.isEmpty()) {
				project.createFilter(JDTLS_FILTER_TYPE, new FileInfoMatcherDescription(CORE_RESOURCES_MATCHER_ID, resourceFilter), IResource.BACKGROUND_REFRESH, monitor);
			}
		}
	}

	public void reportProjectsStatus() {
		int maxProjectProblemSeverity = ProjectUtils.getMaxProjectProblemSeverity();
		if (maxProjectProblemSeverity == IMarker.SEVERITY_ERROR) {
			JavaLanguageServerPlugin.sendStatus(ServiceStatus.ProjectStatus, "WARNING");
		} else {
			JavaLanguageServerPlugin.sendStatus(ServiceStatus.ProjectStatus, "OK");
		}
	}

	class UpdateProjectsWorkspaceJob extends WorkspaceJob {

		private final Collection<IProject> projects;
		private final boolean force;

		public UpdateProjectsWorkspaceJob(Collection<IProject> projects, boolean force) {
			super("Updating project configurations");
			this.projects = projects;
			this.force = force;
		}

		@Override
		public boolean belongsTo(Object family) {
			return IConstants.UPDATE_PROJECT_FAMILY.equals(family) || IConstants.JOBS_FAMILY.equals(family);
		}

		@Override
		public IStatus runInWorkspace(IProgressMonitor monitor) {
			long start = System.currentTimeMillis();
			MultiStatus status = new MultiStatus(IConstants.PLUGIN_ID, 0, "Update project configurations");
			for (Entry<IBuildSupport, List<IProject>> entry : groupByBuildSupport(projects).entrySet()) {
				IStatus onWillUpdateStatus = onWillConfigurationUpdate(entry.getKey(),
						entry.getValue(), monitor);

				// if onWillUpdate() failed, skip updating the projects.
				if (!onWillUpdateStatus.isOK()) {
					status.add(onWillUpdateStatus);
					continue;
				}

				for (IProject project : entry.getValue()) {
					if (monitor.isCanceled()) {
						return Status.CANCEL_STATUS;
					}
					updateProject(entry.getKey(), project, force, status, monitor);
				}
			}

			onDidConfigurationUpdated(status, monitor);
			long elapsed = System.currentTimeMillis() - start;
			JavaLanguageServerPlugin.logInfo("Projects updated in " + elapsed + " ms");
			return status;
		}

		private Map<IBuildSupport, List<IProject>> groupByBuildSupport(Collection<IProject> projects) {
			Map<IBuildSupport, List<IProject>> groupByBuildSupport = new HashMap<>();
			List<IBuildSupport> buildSupports = buildSupports().toList();
			for (IProject project : projects) {
				Optional<IBuildSupport> buildSupport = buildSupports.stream()
						.filter(bs -> bs.applies(project)).findFirst();
				if (buildSupport.isPresent()) {
					IBuildSupport bs = buildSupport.get();
					groupByBuildSupport.computeIfAbsent(bs, k -> new ArrayList<>()).add(project);
				}
			}
			return groupByBuildSupport;
		}

		private IStatus onWillConfigurationUpdate(IBuildSupport buildSupport, Collection<IProject> projects,
				IProgressMonitor monitor) {
			try {
				buildSupport.onWillUpdate(projects, monitor);
				return Status.OK_STATUS;
			} catch (CoreException e) {
				JavaLanguageServerPlugin.log(e);
				return StatusFactory.newErrorStatus("Failed to prepare update from " + buildSupport.buildToolName(), e);
			}
		}

		private void updateProject(IBuildSupport buildSupport, IProject project, boolean force,
				MultiStatus status, IProgressMonitor monitor) {
			try {
				project.refreshLocal(IResource.DEPTH_INFINITE, monitor);
				buildSupport.update(project, force, monitor);
				project.deleteMarkers(BUILD_FILE_MARKER_TYPE, false, IResource.DEPTH_ONE);
			} catch (CoreException e) {
				JavaLanguageServerPlugin.log(e);
				status.add(StatusFactory.newErrorStatus("Error updating " + project.getName(), e));
			}
		}

		private void onDidConfigurationUpdated(MultiStatus status, IProgressMonitor monitor) {
			try {
				registerWatchers(true);
				updateEncoding(monitor);
				reportProjectsStatus();
			} catch (CoreException e) {
				JavaLanguageServerPlugin.log(e);
				status.add(StatusFactory.newErrorStatus("Error updating encoding", e));
			}
		}
	}

	private final class ImportProjectsFromSelectionJob extends WorkspaceJob {
		private final Set<IPath> filesToImport;
		private final Set<IProject> projectsToUpdate;
		private final Set<IProject> projectsToDelete;

		private ImportProjectsFromSelectionJob(Set<IPath> filesToImport, Set<IProject> projectsToUpdate, Set<IProject> projectsToDelete) {
			super("Applying the selected build files...");
			this.filesToImport = filesToImport;
			this.projectsToUpdate = projectsToUpdate;
			this.projectsToDelete = projectsToDelete;
		}

		@Override
		public IStatus runInWorkspace(IProgressMonitor monitor) throws CoreException {
			try {
				SubMonitor subMonitor = SubMonitor.convert(monitor, 100);
				deleteProjects(subMonitor);
				importProjects(subMonitor);
				updateProjects(projectsToUpdate, true);
				return Status.OK_STATUS;
			} catch (OperationCanceledException e) {
				return Status.CANCEL_STATUS;
			} catch (CoreException e) {
				return new Status(IStatus.ERROR, IConstants.PLUGIN_ID, "Applying the selected build files failed.", e);
			}
		}

		private void importProjects(SubMonitor subMonitor) throws CoreException {
			importProjectsFromConfigurationFiles(
					preferenceManager.getPreferences().getRootPaths(),
					filesToImport,
					subMonitor.split(70)
			);
			List<URI> projectUris = filesToImport.stream()
					.map(path -> {
				IFile file = JDTUtils.findFile(path.toFile().toURI().toString());
				return file == null ? null : file.getProject();
					})
					.filter(Objects::nonNull)
					.map(project -> ProjectUtils.getProjectRealFolder(project).toFile().toURI())
					.collect(Collectors.toList());
			if (!projectUris.isEmpty()) {
				EventNotification notification = new EventNotification().withType(EventType.ProjectsImported).withData(projectUris);
				client.sendEventNotification(notification);
			}
		}

		private void deleteProjects(SubMonitor subMonitor) throws CoreException {
			List<URI> projectUris = new LinkedList<>();
			for (IProject project : projectsToDelete) {
				clearDiagnostics(project);
				// once the project is deleted, the project.getLocationURI() will return null, so we
				// store the uri before deleting happens.
				projectUris.add(ProjectUtils.getProjectRealFolder(project).toFile().toURI());
				project.delete(false /*deleteContent*/, false /*force*/, subMonitor.split(1));
			}
			if (!projectUris.isEmpty()) {
				EventNotification notification = new EventNotification().withType(EventType.ProjectsDeleted).withData(projectUris);
				client.sendEventNotification(notification);
			}
		}

		private void clearDiagnostics(IProject project) throws CoreException {
			IMarker[] markers = project.findMarkers(null, true, IResource.DEPTH_INFINITE);
			Set<String> uris = new HashSet<>();
			for (IMarker marker : markers) {
				URI locationURI = marker.getResource().getLocationURI();
				if (locationURI != null) {
					String uriString = locationURI.toString();
					if (new File(locationURI).isDirectory() && Platform.OS_WIN32.equals(Platform.getOS()) &&
							!uriString.endsWith("/")) {
						uriString += "/";
					}
					uris.add(uriString);
				}
			}
			for (String uri : uris) {
				PublishDiagnosticsParams diagnostics = new PublishDiagnosticsParams(ResourceUtils.toClientUri(uri), Collections.emptyList());
				client.publishDiagnostics(diagnostics);
			}
		}
	}

	public void checkIndexes() {
	}

}
