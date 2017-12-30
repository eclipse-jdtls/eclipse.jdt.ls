/*******************************************************************************
 * Copyright (c) 2016-2017 Red Hat Inc. and others.
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

import java.io.File;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.stream.Stream;

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
import org.eclipse.jdt.launching.IVMInstall;
import org.eclipse.jdt.launching.JavaRuntime;
import org.eclipse.jdt.launching.environments.IExecutionEnvironment;
import org.eclipse.jdt.launching.environments.IExecutionEnvironmentsManager;
import org.eclipse.jdt.ls.core.internal.ActionableNotification;
import org.eclipse.jdt.ls.core.internal.IProjectImporter;
import org.eclipse.jdt.ls.core.internal.JDTUtils;
import org.eclipse.jdt.ls.core.internal.JavaClientConnection.JavaLanguageClient;
import org.eclipse.jdt.ls.core.internal.JavaLanguageServerPlugin;
import org.eclipse.jdt.ls.core.internal.ProjectUtils;
import org.eclipse.jdt.ls.core.internal.ResourceUtils;
import org.eclipse.jdt.ls.core.internal.ServiceStatus;
import org.eclipse.jdt.ls.core.internal.StatusFactory;
import org.eclipse.jdt.ls.core.internal.preferences.PreferenceManager;
import org.eclipse.jdt.ls.core.internal.preferences.Preferences.FeatureStatus;
import org.eclipse.lsp4j.Command;
import org.eclipse.lsp4j.MessageType;
import org.eclipse.lsp4j.TextDocumentIdentifier;

public class ProjectsManager implements ISaveParticipant {

	public static final String DEFAULT_PROJECT_NAME = "jdt.ls-java-project";
	private PreferenceManager preferenceManager;
	private JavaLanguageClient client;

	public enum CHANGE_TYPE {
		CREATED, CHANGED, DELETED
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
		job.schedule();
		return job;
	}

	private void deleteInvalidProjects(Collection<IPath> rootPaths, IProgressMonitor monitor) {
		for (IProject project : getWorkspaceRoot().getProjects()) {
			if (project.exists() && (ResourceUtils.isContainedIn(project.getLocation(), rootPaths) || ProjectUtils.isGradleProject(project))) {
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
		IResource resource = JDTUtils.findFile(uriString);
		if (resource == null) {
			return;
		}
		try {
			if (changeType == CHANGE_TYPE.DELETED) {
				resource = resource.getParent();
			}
			if (resource != null) {
				resource.refreshLocal(IResource.DEPTH_INFINITE, new NullProgressMonitor());
			}
			if (isBuildFile(resource)) {
				FeatureStatus status = preferenceManager.getPreferences().getUpdateBuildConfigurationStatus();
				switch (status) {
					case automatic:
						updateProject(resource.getProject());
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
		} catch (CoreException e) {
			JavaLanguageServerPlugin.logException("Problem refreshing workspace", e);
		}
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
		IExtensionPoint extensionPoint = Platform.getExtensionRegistry().getExtensionPoint(JavaLanguageServerPlugin.PLUGIN_ID, "importers");
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
		if (project.exists()) {
			return project;
		}
		JavaLanguageServerPlugin.logInfo("Creating the default Java project");
		//Create project
		project.create(monitor);
		project.open(monitor);

		//Turn into Java project
		IProjectDescription description = project.getDescription();
		description.setNatureIds(new String[] { JavaCore.NATURE_ID });
		project.setDescription(description, monitor);
		IJavaProject javaProject = JavaCore.create(project);

		//Add build output folder
		IFolder output = project.getFolder("bin");
		if (!output.exists()) {
			output.create(true, true, monitor);
		}
		javaProject.setOutputLocation(output.getFullPath(), monitor);

		//Add source folder
		IFolder source = project.getFolder("src");
		if (!source.exists()) {
			source.create(true, true, monitor);
		}
		IPackageFragmentRoot root = javaProject.getPackageFragmentRoot(source);
		IClasspathEntry src = JavaCore.newSourceEntry(root.getPath());

		//Find default JVM
		IClasspathEntry jre = JavaRuntime.getDefaultJREContainerEntry();

		//Add JVM to project class path
		javaProject.setRawClasspath(new IClasspathEntry[] { jre, src }, monitor);

		JavaLanguageServerPlugin.logInfo("Finished creating the default Java project");
		return project;
	}

	public Job updateProject(IProject project) {
		if (project == null || (!ProjectUtils.isMavenProject(project) && !ProjectUtils.isGradleProject(project))) {
			return null;
		}
		JavaLanguageServerPlugin.sendStatus(ServiceStatus.Message, "Updating " + project.getName() + " configuration");
		WorkspaceJob job = new WorkspaceJob("Update project " + project.getName()) {
			@Override
			public IStatus runInWorkspace(IProgressMonitor monitor) {
				IStatus status = Status.OK_STATUS;
				String projectName = project.getName();
				try {
					long start = System.currentTimeMillis();
					Optional<IBuildSupport> buildSupport = getBuildSupport(project);
					if (buildSupport.isPresent()) {
						buildSupport.get().update(project, monitor);
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
		return Stream.of(new GradleBuildSupport(), new MavenBuildSupport());
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

}
