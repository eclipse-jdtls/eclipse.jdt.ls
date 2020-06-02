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

import java.util.concurrent.TimeUnit;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.jdt.core.IClassFile;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IPackageFragmentRoot;
import org.eclipse.jdt.ls.core.internal.JobHelpers;
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
			if (element instanceof IPackageFragmentRoot) {
				final IPackageFragmentRoot fragment = (IPackageFragmentRoot) element;
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
