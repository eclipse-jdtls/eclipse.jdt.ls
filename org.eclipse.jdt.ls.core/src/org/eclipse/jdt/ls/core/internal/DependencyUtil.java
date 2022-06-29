/*******************************************************************************
 * Copyright (c) 2018 Red Hat Inc. and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *     Red Hat Inc. - initial API and implementation
 *******************************************************************************/
package org.eclipse.jdt.ls.core.internal;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.repository.ArtifactRepository;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.m2e.core.MavenPlugin;
import org.eclipse.m2e.core.embedder.ArtifactKey;
import org.eclipse.m2e.core.embedder.IMaven;

/**
 * Utility to locate jars from the local Maven repository.
 *
 * @author Snjeza
 * @author Fred Bricon
 *
 */
public class DependencyUtil {

	public static final String CLASSIFIER_JAVADOC = "javadoc";
	public static final String CLASSIFIER_SOURCES = "sources";

	public static File getSources(String groupId, String artifactId, String version) throws FileNotFoundException, CoreException {
		return getArtifact(groupId, artifactId, version, CLASSIFIER_SOURCES);
	}

	public static File getJavadoc(String groupId, String artifactId, String version) throws FileNotFoundException, CoreException {
		return getArtifact(groupId, artifactId, version, CLASSIFIER_JAVADOC);
	}

	public static File getArtifact(String groupId, String artifactId, String version, String classifier) throws FileNotFoundException, CoreException {
		ArtifactKey key = new ArtifactKey(groupId, artifactId, version, classifier);
		File archive = getLocalArtifactFile(key);
		if (archive == null) {
			Artifact artifact = MavenPlugin.getMaven().resolve(key.groupId(), key.artifactId(), key.version(), "jar", key.classifier(), null, new NullProgressMonitor());
			if (artifact == null) {
				throw new FileNotFoundException("Unable to find " + key);
			}
			archive = getLocalArtifactFile(key);
		}
		return archive;
	}

	//From org.eclipse.m2e.jdt.internal.BuildPathManager#getAttachedArtifactFile
	private static File getLocalArtifactFile(ArtifactKey a) {
		// can't use Maven resolve methods since they mark artifacts as not-found even if they could be resolved remotely
		IMaven maven = MavenPlugin.getMaven();
		try {
			ArtifactRepository localRepository = maven.getLocalRepository();
			String relPath = maven.getArtifactPath(localRepository, a.groupId(), a.artifactId(), a.version(), "jar", //$NON-NLS-1$
					a.classifier());
			File file = new File(localRepository.getBasedir(), relPath).getCanonicalFile();
			if (file.canRead() && file.isFile()) {
				return file;
			}
		} catch (CoreException | IOException ex) {
			// fall through
		}
		return null;
	}
}
