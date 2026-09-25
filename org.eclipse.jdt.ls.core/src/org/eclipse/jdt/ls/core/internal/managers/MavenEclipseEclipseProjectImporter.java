/*******************************************************************************
 * Copyright (c) 2026 Red Hat Inc. and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *     Red Hat Inc. - initial API and implementation
 *******************************************************************************/
package org.eclipse.jdt.ls.core.internal.managers;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.maven.Maven;
import org.apache.maven.execution.AbstractExecutionListener;
import org.apache.maven.execution.DefaultMavenExecutionRequest;
import org.apache.maven.execution.ExecutionEvent;
import org.apache.maven.execution.MavenExecutionRequestPopulator;
import org.apache.maven.execution.MavenExecutionResult;
import org.apache.maven.project.MavenProject;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.OperationCanceledException;
import org.eclipse.jdt.ls.core.internal.JavaLanguageServerPlugin;
import org.eclipse.jdt.ls.core.internal.ProjectUtils;
import org.eclipse.jdt.ls.core.internal.preferences.PreferenceManager;
import org.eclipse.m2e.core.MavenPlugin;
import org.eclipse.m2e.core.embedder.IMaven;
import org.eclipse.m2e.core.internal.IMavenConstants;
import org.osgi.service.component.annotations.Component;

/**
 * Imports a Maven project by using mvn eclipse:eclipse.
 *
 * @author David Thompson
 */
@Component
public class MavenEclipseEclipseProjectImporter extends EclipseProjectImporter {

	/* (non-Javadoc)
	 * @see org.eclipse.jdt.ls.core.internal.AbstractProjectImporter#applies(org.eclipse.core.runtime.IProgressMonitor)
	 */
	@Override
	public boolean applies(IProgressMonitor monitor) throws OperationCanceledException, CoreException {
		PreferenceManager preferencesManager = JavaLanguageServerPlugin.getPreferencesManager();
		if (preferencesManager != null && !preferencesManager.getPreferences().isImportMavenEnabled()) {
			return false;
		}
		if (directories == null) {
			//@formatter:off
			BasicFileDetector mavenDetector = new BasicFileDetector(rootFolder.toPath(), IMavenConstants.POM_FILE_NAME)
					.includeNested(false)
					.addExclusions("**/target"); //default maven build dir
			//@formatter:on
			for (IProject project : ProjectUtils.getAllProjects()) {
				if (!ProjectUtils.isMavenProject(project)) {
					String path = project.getLocation().toOSString();
					mavenDetector.addExclusions(path.replace("\\", "\\\\"));
				}
			}
			directories = mavenDetector.scan(monitor);
		}
		return !directories.isEmpty();
	}

	/* (non-Javadoc)
	 * @see org.eclipse.jdt.ls.core.internal.AbstractProjectImporter#applies(Collection<IPath>, org.eclipse.core.runtime.IProgressMonitor)
	 */
	@Override
	public boolean applies(Collection<IPath> buildFiles, IProgressMonitor monitor) {
		if (!getPreferences().isImportMavenEnabled()) {
			return false;
		}

		Collection<java.nio.file.Path> configurationDirs = findProjectPathByConfigurationName(buildFiles, Arrays.asList(IMavenConstants.POM_FILE_NAME), true /*includeNested*/);
		if (configurationDirs == null || configurationDirs.isEmpty()) {
			return false;
		}

		Set<java.nio.file.Path> noneMavenProjectPaths = new HashSet<>();
		for (IProject project : ProjectUtils.getAllProjects()) {
			if (!ProjectUtils.isMavenProject(project)) {
				noneMavenProjectPaths.add(project.getLocation().toFile().toPath());
			}
		}

		this.directories = configurationDirs.stream().filter(d -> {
			boolean folderIsImported = noneMavenProjectPaths.stream().anyMatch(path -> {
				return path.compareTo(d) == 0;
			});
			return !folderIsImported;
		}).collect(Collectors.toList());

		return !this.directories.isEmpty();
	}

	/* (non-Javadoc)
	 * @see org.eclipse.jdt.ls.core.internal.AbstractProjectImporter#importToWorkspace(org.eclipse.core.runtime.IProgressMonitor)
	 */
	@Override
	public void importToWorkspace(IProgressMonitor monitor) throws OperationCanceledException, CoreException {

		if (isAlreadyImported()) {
			return;
		}

		try {
			IMaven eyeMaven = MavenPlugin.getMaven();
			Maven maven = eyeMaven.lookup(Maven.class);
			for (Path directory : directories) {
				MavenProject project = eyeMaven.readProject(directory.resolve(IMavenConstants.POM_FILE_NAME).toFile(), monitor);
				DefaultMavenExecutionRequest req = new DefaultMavenExecutionRequest();
				eyeMaven.lookup(MavenExecutionRequestPopulator.class).populateDefaults(req);
				Properties properties = new Properties();
				// set the location where the projects will be generated
				properties.setProperty("eclipse.projectDir", ResourcesPlugin.getWorkspace().getRoot().getLocation().toOSString());
				req.setUserProperties(properties);
				req.setSystemProperties(System.getProperties());
				req.setGoals(List.of("eclipse:eclipse"));
				req.setPom(project.getFile());
				req.setBaseDirectory(directory.toFile());
				req.setExecutionListener(new AbstractExecutionListener() {
					/* (non-Javadoc)
					 * @see org.apache.maven.execution.AbstractExecutionListener#mojoStarted(org.apache.maven.execution.ExecutionEvent)
					 */
					@Override
					public void mojoStarted(ExecutionEvent event) {
						monitor.subTask(getExecutionEventString(event));
						super.mojoStarted(event);
					}
				});
				MavenExecutionResult res = maven.execute(req);
				if (res.hasExceptions()) {
					if (res.getExceptions().get(0) instanceof Exception) {
						throw (Exception) res.getExceptions().get(0);
					}
				}
				JavaLanguageServerPlugin.logInfo("Imported " + directory.toString() + "using mvn eclipse:eclipse");
			}
		} catch (Exception e) {
			JavaLanguageServerPlugin.logException(e);
		}

		// for the parent builder
		this.setDirectories(collectConvertedProjects());

		super.importToWorkspace(monitor);
	}

	private boolean isAlreadyImported() {
		Path workspacePath = ResourcesPlugin.getWorkspace().getRoot().getLocation().toPath();
		try {
			class LocatingFileVisitor extends SimpleFileVisitor<Path> {
				boolean found = false;

				/* (non-Javadoc)
				 * @see java.nio.file.SimpleFileVisitor#preVisitDirectory(java.lang.Object, java.nio.file.attribute.BasicFileAttributes)
				 */
				@Override
				public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
					if (dir.endsWith(".metadata")) {
						return FileVisitResult.SKIP_SUBTREE;
					}
					return super.preVisitDirectory(dir, attrs);
				}

				@Override
				public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {

					if (attrs.isRegularFile() && file.endsWith(".project")) {
						found = true;
						return FileVisitResult.TERMINATE;
					}
					return super.visitFile(file, attrs);
				}
			}
			LocatingFileVisitor visitor = new LocatingFileVisitor();
			Files.walkFileTree(workspacePath, visitor);
			return visitor.found;
		} catch (IOException ioe) {
			JavaLanguageServerPlugin.logException("Failed to collect converted projects", ioe);
		}
		return false;
	}

	private List<Path> collectConvertedProjects() {
		Path workspacePath = ResourcesPlugin.getWorkspace().getRoot().getLocation().toPath();
		try {
			List<Path> res = new ArrayList<>();
			Files.walkFileTree(workspacePath, new SimpleFileVisitor<Path>() {
				/* (non-Javadoc)
				 * @see java.nio.file.SimpleFileVisitor#preVisitDirectory(java.lang.Object, java.nio.file.attribute.BasicFileAttributes)
				 */
				@Override
				public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
					if (dir.endsWith(".metadata")) {
						return FileVisitResult.SKIP_SUBTREE;
					}
					return super.preVisitDirectory(dir, attrs);
				}

				/* (non-Javadoc)
				 * @see java.nio.file.SimpleFileVisitor#visitFile(java.lang.Object, java.nio.file.attribute.BasicFileAttributes)
				 */
				@Override
				public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
					if (attrs.isRegularFile() && file.endsWith(".project")) {
						res.add(file.getParent());
					}
					return super.visitFile(file, attrs);
				}
			});
			return res;
		} catch (IOException ioe) {
			JavaLanguageServerPlugin.logException("Failed to collect converted projects", ioe);
		}
		return Collections.emptyList();
	}

	/* (non-Javadoc)
	 * @see org.eclipse.jdt.ls.core.internal.AbstractProjectImporter#reset()
	 */
	@Override
	public void reset() {
		// TODO Auto-generated method stub
		super.reset();
	}

	private static String getExecutionEventString(ExecutionEvent event) {
		StringBuilder sb = new StringBuilder();
		sb.append("[");
		sb.append(event.getType().name());
		sb.append("] Project: ");
		sb.append(event.getProject().getGroupId());
		sb.append(":");
		sb.append(event.getProject().getArtifactId());
		sb.append(":");
		sb.append(event.getProject().getVersion());
		sb.append(" Plugin: ");
		sb.append(event.getMojoExecution().getPlugin().getGroupId());
		sb.append(":");
		sb.append(event.getMojoExecution().getPlugin().getArtifactId());
		sb.append(":");
		sb.append(event.getMojoExecution().getPlugin().getVersion());
		sb.append(" Goal: ");
		sb.append(event.getMojoExecution().getGoal());
		return sb.toString();
	}

}
