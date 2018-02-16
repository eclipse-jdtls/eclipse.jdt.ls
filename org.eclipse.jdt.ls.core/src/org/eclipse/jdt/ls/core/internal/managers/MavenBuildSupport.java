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

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.ResourcesPlugin;
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

	private static final String POM_SERLIZATION_FILE_NAME = ".pom-digests";

	private static final File SERIALIZATION_FILE;
	private static Map<String, String> pomDigests;

	static {
		File workspaceFile = ResourcesPlugin.getWorkspace().getRoot().getRawLocation().makeAbsolute().toFile();
		SERIALIZATION_FILE = new File(workspaceFile, POM_SERLIZATION_FILE_NAME);
		if (SERIALIZATION_FILE.exists()) {
			pomDigests = deserializePomDigests();
		} else {
			pomDigests = new HashMap<>();
		}
	}

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
		JavaLanguageServerPlugin.logInfo("Starting Maven update for " + project.getName());
		//TODO collect dependent projects and update them as well? i.e in case a parent project was modified
		MavenUpdateRequest request = new MavenUpdateRequest(project, MavenPlugin.getMavenConfiguration().isOffline(), true);
		this.configurationManager.updateProjectConfiguration(request, monitor);
	}

	private boolean needsMavenUpdate(IProject project) {
		try {
			Path path = project.getFile("pom.xml").getLocation().toFile().toPath();
			byte[] fileBytes = Files.readAllBytes(path);
			byte[] digest = MessageDigest.getInstance("MD5").digest(fileBytes);
			String newDigestStr = Arrays.toString(digest);
			synchronized (pomDigests) {
				String prevDigest = pomDigests.put(path.toString(), newDigestStr);
				if (prevDigest == null || !prevDigest.equals(newDigestStr)) {
					serializePomDigests();
					return true;
				}
				return false;
			}
		} catch (IOException | NoSuchAlgorithmException ioe) {
			return true;
		}
	}

	private void serializePomDigests() {
		try (ObjectOutputStream outStream = new ObjectOutputStream(new FileOutputStream(SERIALIZATION_FILE))) {
			outStream.writeObject(pomDigests);
		} catch (IOException e) {
			JavaLanguageServerPlugin.logException("Exception occured while serizalizion of pom digests", e);
		}
	}

	@SuppressWarnings("unchecked")
	private static Map<String, String> deserializePomDigests() {
		Map<String, String> resultObject = null;
		try (ObjectInputStream ois = new ObjectInputStream(new FileInputStream(SERIALIZATION_FILE))) {
			resultObject = (Map<String, String>) ois.readObject();
		} catch (IOException | ClassNotFoundException e) {
			resultObject = new HashMap<>();
			JavaLanguageServerPlugin.logException("Exception occured while deserizalizion of pom digests", e);
		}
		return resultObject;
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
