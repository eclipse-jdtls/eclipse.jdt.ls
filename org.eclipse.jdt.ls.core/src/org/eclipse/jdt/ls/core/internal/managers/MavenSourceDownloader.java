/*******************************************************************************
 * Copyright (c) 2020 Red Hat Inc. and others.
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

import java.io.File;
import java.io.FileNotFoundException;
import java.util.concurrent.TimeUnit;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.jdt.core.IClassFile;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IPackageFragmentRoot;
import org.eclipse.jdt.ls.core.internal.DependencyUtil;
import org.eclipse.jdt.ls.core.internal.JobHelpers;
import org.eclipse.jdt.ls.core.internal.ProjectUtils;
import org.eclipse.m2e.core.embedder.ArtifactKey;
import org.eclipse.m2e.jdt.IClasspathManager;
import org.eclipse.m2e.jdt.MavenJdtPlugin;
import org.eclipse.m2e.jdt.internal.BuildPathManager;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;

/**
 * {@link ISourceDownloader} implementation based on m2e's
 * {@link IClasspathManager}' source download facilities.
 *
 * @author Fred Bricon
 *
 */
public class MavenSourceDownloader implements ISourceDownloader {

	private static Cache<String, Boolean> downloadRequestsCache = CacheBuilder.newBuilder().maximumSize(100).expireAfterWrite(1, TimeUnit.HOURS).build();
	private static final int MAX_TIME_MILLIS = 3000;

	@Override
	public void discoverSource(IClassFile classFile, IProgressMonitor monitor) throws CoreException {
		if (classFile == null) {
			return;
		}
		IJavaElement element = classFile;
		while (element.getParent() != null) {
			element = element.getParent();
			if (element instanceof IPackageFragmentRoot fragment) {
				IPath attachmentPath = fragment.getSourceAttachmentPath();
				if (attachmentPath != null && !attachmentPath.isEmpty() && attachmentPath.toFile().exists()) {
					break;
				}
				if (fragment.isArchive()) {
					IFile file = ResourcesPlugin.getWorkspace().getRoot().getFile(fragment.getPath());
					IPath path = file.getFullPath();
					if (path == null || !path.toFile().exists()) {
						path = file.getLocation();
						if (path == null) {
							return;
						}
					}
					Boolean downloaded = downloadRequestsCache.getIfPresent(path.toString());
					if (downloaded == null) {
						downloadRequestsCache.put(path.toString(), true);
						ArtifactKey artifact = new MavenPropertiesIdentifier().identify(path, monitor);
						if (artifact == null) {
							artifact = new MavenCentralIdentifier().identify(path, monitor);
						}
						if (artifact != null) {
							if (!ProjectUtils.isMavenProject(element.getJavaProject().getProject())) {
								// see https://github.com/eclipse-m2e/m2e-core/commit/b547ecc358c990e182a5eaf8d36f121e43f4a8c9#diff-3967743078be6a24ba1e3ec28bfc22bdf2c88a740695411f6d20e2444fef042fR943
								long lastModified;
								try {
									File artifactFile = DependencyUtil.getArtifact(artifact.groupId(), artifact.artifactId(), artifact.version(), artifact.classifier());
									lastModified = artifactFile.lastModified();
								} catch (FileNotFoundException | CoreException e1) {
									lastModified = -1;
								}
								if (lastModified > -1) {
									try {
										File sources = DependencyUtil.getSources(artifact.groupId(), artifact.artifactId(), artifact.version());
										sources.setLastModified(lastModified - 1);
									} catch (FileNotFoundException | CoreException e) {
										// ignore
									}
									try {
										File javadoc = DependencyUtil.getJavadoc(artifact.groupId(), artifact.artifactId(), artifact.version());
										javadoc.setLastModified(lastModified - 1);
									} catch (FileNotFoundException | CoreException e) {
										// ignore
									}
								}
							}
							BuildPathManager buildpathManager = (BuildPathManager) MavenJdtPlugin.getDefault().getBuildpathManager();
							buildpathManager.scheduleDownload(fragment, artifact, true, true);
							JobHelpers.waitForDownloadSourcesJobs(MAX_TIME_MILLIS);
						}
					}
					break;
				}
			}
		}
	}

}
