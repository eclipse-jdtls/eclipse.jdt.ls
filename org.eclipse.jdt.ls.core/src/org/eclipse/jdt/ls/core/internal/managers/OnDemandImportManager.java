/*******************************************************************************
 * Copyright (c) 2026 Red Hat Inc. and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *     Angelo ZERR - initial API and implementation
 *******************************************************************************/
package org.eclipse.jdt.ls.core.internal.managers;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.jdt.core.IClassFile;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IPackageFragment;
import org.eclipse.jdt.core.IPackageFragmentRoot;
import org.eclipse.jdt.ls.core.internal.IProjectImporter;
import org.eclipse.jdt.ls.core.internal.JavaLanguageServerPlugin;
import org.eclipse.jdt.ls.core.internal.ServiceStatus;
import org.eclipse.jdt.ls.core.internal.mbt.MbtExtractor;
import org.eclipse.jdt.ls.core.internal.mbt.MbtInfo;
import org.eclipse.jdt.ls.core.internal.mbt.MbtJson;

/**
 * Centralized manager for on-demand project import.
 *
 * <p>When an importer activates on-demand mode, it registers itself here.
 * The manager delegates {@code didOpen} and references/rename triggers
 * to the active importer's {@code importOnDemand} and
 * {@code importReverseDependencies} methods.</p>
 *
 * <p>Also handles loading the dependency graph from {@code mbt.json}
 * in the background, populating the source folder index for
 * reverse-dependency resolution (references/rename).</p>
 */
public class OnDemandImportManager {

	private static volatile OnDemandImportManager instance;

	private volatile IProjectImporter activeImporter;
	private volatile AbstractSourceFolderIndex sourceFolderIndex;

	public static OnDemandImportManager getInstance() {
		if (instance == null) {
			synchronized (OnDemandImportManager.class) {
				if (instance == null) {
					instance = new OnDemandImportManager();
				}
			}
		}
		return instance;
	}

	/**
	 * Attempts to import the module containing the given class file on-demand.
	 * Used by {@code JDTUtils.toLocation} to redirect navigation from .class
	 * files to .java sources when the target belongs to a workspace module.
	 *
	 * <p>Uses the library entry's source attachment to locate the .java file,
	 * then imports the module and updates the referencing project's classpath
	 * so subsequent navigations go directly to the source.</p>
	 *
	 * @param classFile the class file being navigated to
	 * @param referencingProject the project whose classpath contains the library
	 * @param monitor progress monitor
	 * @return the source file URI, or {@code null} if import was not possible
	 */
	public String tryImportForClassFile(IClassFile classFile, IJavaProject referencingProject, IProgressMonitor monitor) {
		if (!isActive()) {
			return null;
		}
		try {
			IPackageFragmentRoot root = (IPackageFragmentRoot) classFile.getAncestor(IJavaElement.PACKAGE_FRAGMENT_ROOT);
			if (root == null) {
				return null;
			}
			org.eclipse.core.runtime.IPath sourceAttachment = root.getSourceAttachmentPath();
			if (sourceAttachment == null) {
				return null;
			}
			IPackageFragment pkg = (IPackageFragment) classFile.getParent();
			String pkgPath = pkg.getElementName().replace('.', '/');
			String cfName = classFile.getElementName();
			int dollar = cfName.indexOf('$');
			String sourceName = (dollar > 0 ? cfName.substring(0, dollar) : cfName.replace(".class", "")) + ".java";
			String relativePath = pkgPath.isEmpty() ? sourceName : pkgPath + "/" + sourceName;

			Path sourceDir = Path.of(sourceAttachment.toOSString());
			Path sourceFile = sourceDir.resolve(relativePath);
			if (!Files.isRegularFile(sourceFile)) {
				return null;
			}
			String sourceUri = sourceFile.toUri().toString();

			if (!tryOnDemandImport(sourceUri, monitor)) {
				return null;
			}
			JavaLanguageServerPlugin.logInfo("On-demand import triggered by navigation to " + cfName);

			if (referencingProject != null) {
				IProject project = referencingProject.getProject();
				Optional<IBuildSupport> bs = JavaLanguageServerPlugin.getProjectsManager().getBuildSupport(project);
				if (bs.isPresent()) {
					bs.get().update(project, true, monitor);
				}
			}
			return sourceUri;
		} catch (Exception e) {
			JavaLanguageServerPlugin.logException("Failed to import module for class file navigation", e);
			return null;
		}
	}

	/**
	 * Called early in the initialization job, before {@code initializeProjects()},
	 * to close stale projects left open by a crashed previous session.
	 * This prevents the Eclipse framework from iterating/indexing/building
	 * projects that will be re-imported on-demand.
	 */
	public void closeStaleProjects(java.util.Collection<org.eclipse.core.runtime.IPath> rootPaths, IProgressMonitor monitor) {
		for (org.eclipse.core.runtime.IPath root : rootPaths) {
			closePreviousSessionProjects(root.toFile().toPath().normalize(), monitor);
		}
	}

	public void activate(IProjectImporter importer, AbstractSourceFolderIndex index, Path workspacePath) {
		this.activeImporter = importer;
		this.sourceFolderIndex = index;
		startBackgroundDependencyLoading(workspacePath);
	}

	public void deactivate() {
		this.activeImporter = null;
		this.sourceFolderIndex = null;
	}

	public boolean isActive() {
		return activeImporter != null;
	}

	/**
	 * Tries to import the project containing the given URI on-demand.
	 * Called from {@code DocumentLifeCycleHandler.resolveCompilationUnit()}.
	 */
	public boolean tryOnDemandImport(String uri, IProgressMonitor monitor) {
		IProjectImporter importer = activeImporter;
		if (importer == null) {
			return false;
		}
		try {
			return !importer.importOnDemand(uri, monitor).isEmpty();
		} catch (CoreException e) {
			JavaLanguageServerPlugin.logException("On-demand import failed for " + uri, e);
			return false;
		}
	}

	/**
	 * Tries to import reverse dependencies of the project containing the
	 * given URI. Called from {@code ReferencesHandler} and {@code RenameHandler}.
	 *
	 * @param forModification {@code true} for Rename (needs IProject to modify source),
	 *        {@code false} for Find References (CPE_LIBRARY is sufficient)
	 */
	public boolean tryImportReverseDependencies(String uri, boolean forModification, IProgressMonitor monitor) {
		IProjectImporter importer = activeImporter;
		if (importer == null) {
			return false;
		}
		try {
			return importer.importReverseDependencies(uri, forModification, monitor);
		} catch (CoreException e) {
			JavaLanguageServerPlugin.logException("Reverse dependency import failed for " + uri, e);
			return false;
		}
	}

	private void startBackgroundDependencyLoading(Path workspacePath) {
		if (workspacePath == null) {
			return;
		}
		Job job = new Job("Loading dependency graph") {
			@Override
			protected IStatus run(IProgressMonitor monitor) {
				try {
					MbtInfo mbtInfo = null;
					Path mbtJsonPath = MbtJson.getPath(workspacePath);
					if (!Files.isRegularFile(mbtJsonPath)) {
						boolean persistToFile = JavaLanguageServerPlugin.getPreferencesManager()
								.getPreferences().isMbtStorageFile();
						JavaLanguageServerPlugin.logInfo("Background MBT generation started for workspace: " + workspacePath);
						JavaLanguageServerPlugin.sendStatus(ServiceStatus.Message, "Generating mbt.json in background...");
						long start = System.currentTimeMillis();
						mbtInfo = MbtExtractor.generate(workspacePath, persistToFile, monitor);
						long elapsed = System.currentTimeMillis() - start;
						if (mbtInfo != null && !mbtInfo.getNamespaces().isEmpty()) {
							JavaLanguageServerPlugin.logInfo("Background MBT generation complete: "
									+ mbtInfo.getNamespaces().size() + " targets (" + elapsed + "ms)");
							IProjectImporter importer = activeImporter;
							if (importer != null) {
								importer.onMbtJsonReady(mbtInfo);
							}
						} else {
							JavaLanguageServerPlugin.logInfo("Background MBT generation produced no targets (" + elapsed + "ms)");
						}
					}
					if (mbtInfo == null && Files.isRegularFile(mbtJsonPath)) {
						mbtInfo = MbtJson.read(mbtJsonPath);
					}
					if (mbtInfo != null) {
						AbstractSourceFolderIndex index = sourceFolderIndex;
						if (index != null) {
							JavaLanguageServerPlugin.logInfo("Loading dependency graph");
							JavaLanguageServerPlugin.sendStatus(ServiceStatus.Message, "Loading dependency graph...");
							index.populateDependencies(mbtInfo);
							String msg = "Dependency graph ready (" + index.getModuleCount() + " modules)";
							JavaLanguageServerPlugin.logInfo(msg);
							JavaLanguageServerPlugin.sendStatus(ServiceStatus.Message, msg);
						}
					}
					return Status.OK_STATUS;
				} catch (IOException e) {
					JavaLanguageServerPlugin.logException("Failed to load dependency graph", e);
					return Status.error("Failed to load dependency graph", e);
				}
			}
		};
		job.setPriority(Job.LONG);
		job.schedule();
	}

	/**
	 * Closes all projects under the given workspace root. Called at
	 * on-demand activation to clean up projects from a previous session
	 * (any build system) so that on-demand import starts fresh.
	 */
	public void closePreviousSessionProjects(java.nio.file.Path workspacePath, IProgressMonitor monitor) {
		closeProjects(workspacePath, monitor, "On-demand mode");
	}

	/**
	 * Closes all on-demand imported projects before workspace save on shutdown.
	 * This prevents the next session from loading stale projects at startup.
	 */
	public void closeProjectsOnShutdown(IProgressMonitor monitor) {
		if (!isActive()) {
			return;
		}
		closeProjects(null, monitor, "On-demand shutdown");
	}

	private void closeProjects(java.nio.file.Path workspacePath, IProgressMonitor monitor, String context) {
		IProject[] allProjects = ResourcesPlugin.getWorkspace().getRoot().getProjects();
		int closed = 0;
		for (IProject project : allProjects) {
			if (project.isOpen()) {
				if (workspacePath != null && project.getLocation() != null) {
					java.nio.file.Path projectPath = project.getLocation().toFile().toPath().normalize();
					if (!projectPath.startsWith(workspacePath)) {
						continue;
					}
				}
				try {
					project.close(monitor);
					closed++;
				} catch (CoreException e) {
					JavaLanguageServerPlugin.logException("Failed to close project " + project.getName(), e);
				}
			}
		}
		if (closed > 0) {
			JavaLanguageServerPlugin.logInfo(context + ": closed " + closed + " project(s)");
		}
	}
}
