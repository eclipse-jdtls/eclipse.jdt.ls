/*******************************************************************************
 * Copyright (c) 2026 IBM Corporation and others.
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
package org.eclipse.jdt.ls.core.internal.mbt;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.jdt.core.IClasspathAttribute;
import org.eclipse.jdt.core.IClasspathEntry;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.ls.core.internal.JavaLanguageServerPlugin;
import org.eclipse.jdt.ls.core.internal.ServiceStatus;
import org.eclipse.jdt.ls.core.internal.managers.IBuildSupport;
import org.eclipse.jdt.ls.core.internal.managers.ProjectsManager.CHANGE_TYPE;

/**
 * Build support backed by {@code mbt.json}, a standard format originated by
 * Metals V2.
 *
 * <p>Reads a module's target from mbt.json and configures the JDT classpath
 * via {@code setRawClasspath()}. Source roots, library JARs (with optional
 * source attachments), project references, and JDK are all derived from
 * the mbt.json target data.</p>
 */
public class MbtBuildSupport implements IBuildSupport {

	private static final List<String> WATCH_FILE_PATTERNS = List.of("**/pom.xml", "**/build.gradle", "**/build.gradle.kts", "**/mbt.json");
	private static final long REGENERATION_DELAY_MS = 2000;

	private static volatile MbtInfo sharedMbtInfo;
	private static volatile Path sharedWorkspacePath;
	private static Job regenerationJob;

	private MbtInfo mbtInfo;
	private Path workspacePath;
	private Map<String, MbtDependencyModuleInfo> dependencyModuleIndex;
	private Map<String, String> projectNameToTargetId;

	@Override
	public boolean applies(IProject project) {
		if (!isMbtEnabled()) {
			return false;
		}
		ensureLoaded(project);
		return mbtInfo != null && findTargetId(project) != null;
	}

	private static boolean isMbtEnabled() {
		return JavaLanguageServerPlugin.getPreferencesManager() != null
				&& JavaLanguageServerPlugin.getPreferencesManager().getPreferences() != null
				&& JavaLanguageServerPlugin.getPreferencesManager().getPreferences().isImportMbtEnabled();
	}

	@Override
	public void update(IProject project, boolean force, IProgressMonitor monitor) throws CoreException {
		if (!applies(project)) {
			return;
		}

		String targetId = findTargetId(project);
		MbtTargetInfo target = mbtInfo.getNamespaces().get(targetId);
		if (target == null) {
			return;
		}

		IJavaProject javaProject = JavaCore.create(project);
		IClasspathEntry[] newEntries = buildClasspathEntries(project, targetId, target);

		if (!force && classpathEquals(javaProject.getRawClasspath(), newEntries)) {
			JavaLanguageServerPlugin.logInfo("MBT: classpath unchanged for " + project.getName() + ", skipping update");
			return;
		}

		JavaLanguageServerPlugin.logInfo("MBT: setting classpath for " + project.getName() + " (" + newEntries.length + " entries)");
		javaProject.setRawClasspath(newEntries, monitor);
	}

	@Override
	public boolean isBuildFile(IResource resource) {
		if (resource == null || resource.getProject() == null) {
			return false;
		}
		String name = resource.getName();
		return "mbt.json".equals(name) || "pom.xml".equals(name)
				|| "build.gradle".equals(name) || "build.gradle.kts".equals(name);
	}

	@Override
	public boolean isBuildLikeFileName(String fileName) {
		return "mbt.json".equals(fileName) || "pom.xml".equals(fileName)
				|| "build.gradle".equals(fileName) || "build.gradle.kts".equals(fileName);
	}

	@Override
	public boolean fileChanged(IResource resource, CHANGE_TYPE changeType, IProgressMonitor monitor) throws CoreException {
		if (resource == null || !applies(resource.getProject())) {
			return false;
		}
		refresh(resource, changeType, monitor);
		if (isBuildFile(resource)) {
			if ("mbt.json".equals(resource.getName())) {
				reloadMbtJson();
				return true;
			}
			scheduleRegeneration();
			return true;
		}
		return false;
	}

	private void scheduleRegeneration() {
		if (workspacePath == null) {
			return;
		}
		Path ws = workspacePath;
		synchronized (MbtBuildSupport.class) {
			if (regenerationJob != null) {
				regenerationJob.cancel();
				JavaLanguageServerPlugin.logInfo("Cancelled previous mbt.json regeneration");
			}
			regenerationJob = new Job("Regenerating mbt.json") {
				@Override
				protected IStatus run(IProgressMonitor monitor) {
					try {
						JavaLanguageServerPlugin.logInfo("Regenerating mbt.json...");
						JavaLanguageServerPlugin.sendStatus(ServiceStatus.Message, "Regenerating mbt.json...");
						boolean persistToFile = JavaLanguageServerPlugin.getPreferencesManager() != null
								&& JavaLanguageServerPlugin.getPreferencesManager().getPreferences() != null
								&& JavaLanguageServerPlugin.getPreferencesManager().getPreferences().isMbtStorageFile();
						MbtInfo newInfo = MbtExtractor.generate(ws, persistToFile, monitor);
						if (monitor.isCanceled()) {
							return Status.CANCEL_STATUS;
						}
						if (newInfo != null) {
							sharedMbtInfo = newInfo;
							sharedWorkspacePath = ws;
							dependencyModuleIndex = null;
							projectNameToTargetId = null;
							mbtInfo = newInfo;
							JavaLanguageServerPlugin.logInfo("mbt.json regenerated successfully");
							JavaLanguageServerPlugin.sendStatus(ServiceStatus.Message, "mbt.json regenerated");
						}
						return Status.OK_STATUS;
					} catch (IOException e) {
						JavaLanguageServerPlugin.logException("Failed to regenerate mbt.json", e);
						return Status.error("Failed to regenerate mbt.json", e);
					} finally {
						synchronized (MbtBuildSupport.class) {
							if (regenerationJob == this) {
								regenerationJob = null;
							}
						}
					}
				}
			};
			regenerationJob.setPriority(Job.LONG);
			regenerationJob.schedule(REGENERATION_DELAY_MS);
		}
	}

	@Override
	public List<String> getWatchPatterns() {
		return WATCH_FILE_PATTERNS;
	}

	@Override
	public String buildToolName() {
		return "MBT";
	}

	@Override
	public String unsupportedOperationMessage() {
		return "Unsupported operation. Please regenerate mbt.json to update the project configuration.";
	}

	/**
	 * Builds the JDT classpath entries from the mbt.json target data.
	 */
	IClasspathEntry[] buildClasspathEntries(IProject project, String targetId, MbtTargetInfo target) {
		List<IClasspathEntry> entries = new ArrayList<>();

		// Detect if this project is a named JPMS module
		boolean isModular = hasModuleInfo(target);

		// Source folders (only if directory exists on disk)
		for (String source : target.getSources()) {
			Path sourcePath = workspacePath.resolve(source).normalize();
			if (!Files.isDirectory(sourcePath)) {
				continue;
			}
			Path projectLocation = project.getLocation().toFile().toPath().normalize();
			Path relativePath = projectLocation.relativize(sourcePath);
			org.eclipse.core.runtime.IPath srcPath = project.getFullPath().append(relativePath.toString().replace('\\', '/'));
			entries.add(JavaCore.newSourceEntry(srcPath));
		}

		// JRE container
		entries.add(JavaCore.newContainerEntry(
				new org.eclipse.core.runtime.Path("org.eclipse.jdt.launching.JRE_CONTAINER")));

		// Module path attribute for JPMS projects
		IClasspathAttribute[] moduleAttrs = isModular
				? new IClasspathAttribute[] { JavaCore.newClasspathAttribute(IClasspathAttribute.MODULE, "true") }
				: new IClasspathAttribute[0];

		// Project references (dependsOn → workspace projects or library fallback).
		// Collect ALL transitive dependsOn targets so that library-mode deps
		// (no IProject) still have their transitive classes visible.
		Set<String> allTransitiveDeps = new java.util.LinkedHashSet<>();
		collectAllDependsOnTargets(target.getDependsOn(), allTransitiveDeps, new HashSet<>());
		int projectRefCount = 0;
		int libraryRefCount = 0;
		for (String dep : allTransitiveDeps) {
			String depProjectName = MbtProjectImporter.toProjectName(dep);
			IProject depProject = ResourcesPlugin.getWorkspace().getRoot().getProject(depProjectName);
			if (depProject.exists() && depProject.isOpen()) {
				entries.add(JavaCore.newProjectEntry(depProject.getFullPath(),
						null, false, moduleAttrs, true));
				projectRefCount++;
			} else {
				MbtTargetInfo depTarget = mbtInfo.getNamespaces().get(dep);
				if (depTarget != null) {
					addTargetAsLibrary(entries, depTarget, moduleAttrs);
					libraryRefCount++;
				}
			}
		}
		if (libraryRefCount > 0) {
			JavaLanguageServerPlugin.logInfo("MBT: " + project.getName() + " classpath: "
					+ projectRefCount + " project ref(s), " + libraryRefCount + " library ref(s) from target/classes");
		}

		// Library JARs — own deps + inlined deps from dependsOn (isExported=false to prevent JPMS conflicts)
		// Deduplicate by groupId:artifactId, keeping the highest version to avoid
		// namespace conflicts (e.g., jakarta.ws.rs-api 2.1.6 uses javax.ws.rs,
		// while 3.1.0 uses jakarta.ws.rs — the lower version breaks imports).
		Map<String, String> bestVersionByGA = new java.util.LinkedHashMap<>();
		List<String> allDeps = collectAllDependencyModules(target);
		for (String depId : allDeps) {
			String[] depParts = depId.split(":");
			if (depParts.length < 3) {
				continue;
			}
			String gaKey = depParts[0] + ":" + depParts[1];
			String existing = bestVersionByGA.get(gaKey);
			if (existing == null || compareVersions(depParts[2], existing.split(":")[2]) > 0) {
				bestVersionByGA.put(gaKey, depId);
			}
		}
		for (String depId : bestVersionByGA.values()) {
			MbtDependencyModuleInfo depInfo = getDependencyModule(depId);
			if (depInfo == null || depInfo.getJar() == null) {
				continue;
			}
			Path jarPath = toPath(depInfo.getJar());
			if (jarPath == null || !Files.isRegularFile(jarPath)) {
				continue;
			}
			org.eclipse.core.runtime.IPath jar = org.eclipse.core.runtime.Path.fromOSString(jarPath.toString());
			org.eclipse.core.runtime.IPath sourceJar = null;
			if (depInfo.getSources() != null) {
				Path sourcesPath = toPath(depInfo.getSources());
				if (sourcesPath != null && Files.isRegularFile(sourcesPath)) {
					sourceJar = org.eclipse.core.runtime.Path.fromOSString(sourcesPath.toString());
				}
			}
			entries.add(JavaCore.newLibraryEntry(jar, sourceJar, null, null, moduleAttrs, false));
		}

		return entries.toArray(IClasspathEntry[]::new);
	}

	/**
	 * Adds the given target's output classes directory as a library entry
	 * with source attachment. Used for dependsOn targets that don't have
	 * an open IProject — avoids creating a project and triggering a JDT build.
	 */
	private void addTargetAsLibrary(List<IClasspathEntry> entries, MbtTargetInfo depTarget,
			IClasspathAttribute[] moduleAttrs) {
		for (String classesDir : depTarget.getClasses()) {
			Path classesPath = workspacePath.resolve(classesDir).normalize();
			if (!Files.isDirectory(classesPath)) {
				continue;
			}
			org.eclipse.core.runtime.IPath classesIPath =
					org.eclipse.core.runtime.Path.fromOSString(classesPath.toString());

			org.eclipse.core.runtime.IPath sourceAttachment = null;
			for (String source : depTarget.getSources()) {
				Path sourcePath = workspacePath.resolve(source).normalize();
				if (Files.isDirectory(sourcePath)) {
					sourceAttachment = org.eclipse.core.runtime.Path.fromOSString(sourcePath.toString());
					break;
				}
			}

			entries.add(JavaCore.newLibraryEntry(classesIPath, sourceAttachment, null,
					null, moduleAttrs, false));
		}
	}

	/**
	 * Recursively collects all transitive dependsOn target IDs.
	 */
	private void collectAllDependsOnTargets(List<String> dependsOn, Set<String> result,
			Set<String> visited) {
		for (String dep : dependsOn) {
			if (!visited.add(dep)) {
				continue;
			}
			result.add(dep);
			MbtTargetInfo depTarget = mbtInfo.getNamespaces().get(dep);
			if (depTarget != null) {
				collectAllDependsOnTargets(depTarget.getDependsOn(), result, visited);
			}
		}
	}

	/**
	 * Collects all dependency module IDs for the given target: its own
	 * {@code dependencyModules} plus those from all {@code dependsOn} targets
	 * (recursively). This makes each project self-contained at classpath time
	 * without bloating the mbt.json file.
	 */
	private List<String> collectAllDependencyModules(MbtTargetInfo target) {
		Set<String> result = new java.util.LinkedHashSet<>(target.getDependencyModules());
		Set<String> visitedTargets = new HashSet<>();
		collectDepsFromDependsOn(target.getDependsOn(), result, visitedTargets);
		return new ArrayList<>(result);
	}

	private void collectDepsFromDependsOn(List<String> dependsOn, Set<String> result, Set<String> visitedTargets) {
		for (String depTargetId : dependsOn) {
			if (!visitedTargets.add(depTargetId)) {
				continue;
			}
			MbtTargetInfo depTarget = mbtInfo.getNamespaces().get(depTargetId);
			if (depTarget == null) {
				continue;
			}
			result.addAll(depTarget.getDependencyModules());
			collectDepsFromDependsOn(depTarget.getDependsOn(), result, visitedTargets);
		}
	}

	private boolean hasModuleInfo(MbtTargetInfo target) {
		for (String source : target.getSources()) {
			Path moduleInfo = workspacePath.resolve(source).resolve("module-info.java");
			if (Files.isRegularFile(moduleInfo)) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Sets the mbt.json data for this build support instance and shares it
	 * with the registry instance.
	 */
	void setMbtInfo(MbtInfo mbtInfo) {
		this.mbtInfo = mbtInfo;
		this.dependencyModuleIndex = null;
		this.projectNameToTargetId = null;
		sharedMbtInfo = mbtInfo;
	}

	/**
	 * Sets the workspace root path and shares it with the registry instance.
	 */
	void setWorkspacePath(Path workspacePath) {
		this.workspacePath = workspacePath;
		sharedWorkspacePath = workspacePath;
	}

	private void ensureLoaded(IProject project) {
		if (mbtInfo != null) {
			return;
		}
		if (sharedMbtInfo != null) {
			mbtInfo = sharedMbtInfo;
			workspacePath = sharedWorkspacePath;
			return;
		}
		if (project == null || project.getLocation() == null) {
			return;
		}
		Path projectPath = project.getLocation().toFile().toPath().toAbsolutePath().normalize();
		Path candidate = projectPath;
		while (candidate != null) {
			Path mbtJsonPath = candidate.resolve("mbt.json");
			if (Files.isRegularFile(mbtJsonPath)) {
				try {
					mbtInfo = MbtJson.read(mbtJsonPath);
					workspacePath = candidate;
					dependencyModuleIndex = null;
					sharedMbtInfo = mbtInfo;
					sharedWorkspacePath = workspacePath;
				} catch (Exception e) {
					JavaLanguageServerPlugin.logException("Failed to load mbt.json", e);
				}
				return;
			}
			candidate = candidate.getParent();
		}
	}

	private String findTargetId(IProject project) {
		if (mbtInfo == null) {
			return null;
		}
		if (projectNameToTargetId == null) {
			Map<String, String> map = new HashMap<>();
			for (String targetId : mbtInfo.getNamespaces().keySet()) {
				map.put(MbtProjectImporter.toProjectName(targetId), targetId);
			}
			projectNameToTargetId = map;
		}
		return projectNameToTargetId.get(project.getName());
	}

	private MbtDependencyModuleInfo getDependencyModule(String id) {
		if (dependencyModuleIndex == null) {
			dependencyModuleIndex = new HashMap<>();
			for (MbtDependencyModuleInfo dep : mbtInfo.getDependencyModules()) {
				dependencyModuleIndex.put(dep.getId(), dep);
			}
		}
		return dependencyModuleIndex.get(id);
	}

	private void reloadMbtJson() {
		if (workspacePath == null) {
			return;
		}
		try {
			Path mbtJsonPath = MbtJson.getPath(workspacePath);
			mbtInfo = MbtJson.read(mbtJsonPath);
			dependencyModuleIndex = null;
			projectNameToTargetId = null;
			JavaLanguageServerPlugin.logInfo("Reloaded mbt.json");
		} catch (Exception e) {
			JavaLanguageServerPlugin.logException("Failed to reload mbt.json", e);
		}
	}

	private static int compareVersions(String v1, String v2) {
		int i = 0, j = 0;
		while (i < v1.length() || j < v2.length()) {
			int n1 = 0, n2 = 0;
			while (i < v1.length() && Character.isDigit(v1.charAt(i))) {
				n1 = n1 * 10 + (v1.charAt(i++) - '0');
			}
			while (j < v2.length() && Character.isDigit(v2.charAt(j))) {
				n2 = n2 * 10 + (v2.charAt(j++) - '0');
			}
			if (n1 != n2) {
				return Integer.compare(n1, n2);
			}
			while (i < v1.length() && !Character.isDigit(v1.charAt(i))) {
				i++;
			}
			while (j < v2.length() && !Character.isDigit(v2.charAt(j))) {
				j++;
			}
		}
		return 0;
	}

	private static boolean classpathEquals(IClasspathEntry[] current, IClasspathEntry[] newEntries) {
		if (current == null || newEntries == null) {
			return false;
		}
		return Arrays.equals(current, newEntries);
	}

	/**
	 * Converts a string that may be a {@code file://} URI or a plain filesystem
	 * path to a {@link Path}.
	 */
	private static Path toPath(String value) {
		if (value == null || value.isBlank()) {
			return null;
		}
		if (value.startsWith("file:")) {
			try {
				return Paths.get(new URI(value));
			} catch (URISyntaxException e) {
				return null;
			}
		}
		return Path.of(value);
	}
}
