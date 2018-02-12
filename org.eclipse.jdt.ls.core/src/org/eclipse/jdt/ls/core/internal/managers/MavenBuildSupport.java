/*******************************************************************************
 * Copyright (c) 2016-2017 Red Hat Inc. and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Red Hat Inc. - initial API and implementation
 *******************************************************************************/
package org.eclipse.jdt.ls.core.internal.managers;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.jdt.ls.core.internal.JavaLanguageServerPlugin;
import org.eclipse.jdt.ls.core.internal.ProjectUtils;
import org.eclipse.m2e.core.MavenPlugin;
import org.eclipse.m2e.core.project.IProjectConfigurationManager;
import org.eclipse.m2e.core.project.MavenUpdateRequest;

/**
 * @author Fred Bricon
 *
 */
public class MavenBuildSupport implements IBuildSupport {

	private static Map<Path, String> pomDigests = new ConcurrentHashMap<>();

	private IProjectConfigurationManager configurationManager;

	public MavenBuildSupport() {
		this.configurationManager = MavenPlugin.getProjectConfigurationManager();
	}

	public MavenBuildSupport(IProjectConfigurationManager configurationManager) {
		this.configurationManager = configurationManager;
	}

	@Override
	public boolean applies(IProject project) {
		return ProjectUtils.isMavenProject(project);
	}

	@Override
	public void update(IProject project, boolean force, IProgressMonitor monitor) throws CoreException {
		if (!applies(project) || (!needsMavenUpdate(project) && !force)) {
			return;
		}
		JavaLanguageServerPlugin.logInfo("Starting Maven update for "+project.getName());
		//TODO collect dependent projects and update them as well? i.e in case a parent project was modified
		MavenUpdateRequest request = new MavenUpdateRequest(project, MavenPlugin.getMavenConfiguration().isOffline(), true);
		this.configurationManager.updateProjectConfiguration(request, monitor);
	}

	private boolean needsMavenUpdate(IProject project) {
		try {
			Path path = project.getFile("pom.xml").getLocation().toFile().toPath();
			byte[] fileBytes = Files.readAllBytes(path);
			byte[] digest = MessageDigest.getInstance("MD5").digest(fileBytes);
			String prevDigest = pomDigests.putIfAbsent(path, Arrays.toString(digest));
			return prevDigest == null || !prevDigest.equals(Arrays.toString(digest));
		} catch (IOException | NoSuchAlgorithmException ioe) {
			return true;
		}
	}


	@Override
	public boolean isBuildFile(IResource resource) {
		return resource != null && resource.getProject() != null
				&& resource.getType()== IResource.FILE
				&& resource.getName().equals("pom.xml")
				//Check pom.xml is at the root of the project
				&& resource.getProject().equals(resource.getParent());
	}
}
