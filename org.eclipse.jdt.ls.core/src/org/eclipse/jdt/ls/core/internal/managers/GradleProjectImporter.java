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
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import org.eclipse.buildship.core.CorePlugin;
import org.eclipse.buildship.core.util.gradle.GradleDistributionWrapper;
import org.eclipse.buildship.core.util.gradle.GradleDistributionWrapper.DistributionType;
import org.eclipse.buildship.core.workspace.NewProjectHandler;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.SubMonitor;
import org.eclipse.jdt.ls.core.internal.JavaLanguageServerPlugin;

import com.gradleware.tooling.toolingclient.GradleDistribution;
import com.gradleware.tooling.toolingmodel.repository.FixedRequestAttributes;

/**
 * @author Fred Bricon
 *
 */
public class GradleProjectImporter extends AbstractProjectImporter {

	private static final String BUILD_GRADLE_DESCRIPTOR = "build.gradle";

	protected static final GradleDistribution DEFAULT_DISTRIBUTION = GradleDistribution.fromBuild();

	private Collection<Path> directories;

	/* (non-Javadoc)
	 * @see org.eclipse.jdt.ls.core.internal.managers.IProjectImporter#applies(org.eclipse.core.runtime.IProgressMonitor)
	 */
	@Override
	public boolean applies(IProgressMonitor monitor) throws CoreException {
		if (rootFolder == null) {
			return false;
		}
		if (directories == null) {
			BasicFileDetector gradleDetector = new BasicFileDetector(rootFolder.toPath(), BUILD_GRADLE_DESCRIPTOR)
					.includeNested(false)
					.addExclusions("build");//default gradle build dir
			directories = gradleDetector.scan(monitor);
		}
		return !directories.isEmpty();
	}

	/* (non-Javadoc)
	 * @see org.eclipse.jdt.ls.core.internal.managers.IProjectImporter#importToWorkspace(org.eclipse.core.runtime.IProgressMonitor)
	 */
	@Override
	public void importToWorkspace(IProgressMonitor monitor) throws CoreException {
		if (!applies(monitor)) {
			return;
		}
		SubMonitor subMonitor = SubMonitor.convert(monitor, 100);
		JavaLanguageServerPlugin.logInfo("Importing Gradle project(s)");
		int projectSize = directories.size();
		subMonitor.setWorkRemaining(projectSize);
		directories.forEach(d -> importDir(d, monitor));
		subMonitor.done();
	}

	private void importDir(Path rootFolder, IProgressMonitor monitor) {
		if (monitor.isCanceled()) {
			return;
		}
		GradleDistribution distribution = DEFAULT_DISTRIBUTION;
		if (Files.exists(rootFolder.resolve("gradlew"))) {
			distribution = GradleDistributionWrapper.from(DistributionType.WRAPPER, null).toGradleDistribution();
		}
		startSynchronization(rootFolder.toFile(), distribution, NewProjectHandler.IMPORT_AND_MERGE);
	}

	protected void startSynchronization(File location, GradleDistribution distribution, NewProjectHandler newProjectHandler) {
		List<String> jvmArgs = new ArrayList<>();
		addArgs(jvmArgs, JavaLanguageServerPlugin.HTTP_PROXY_HOST);
		addArgs(jvmArgs, JavaLanguageServerPlugin.HTTP_PROXY_PORT);
		addArgs(jvmArgs, JavaLanguageServerPlugin.HTTP_PROXY_USER);
		addArgs(jvmArgs, JavaLanguageServerPlugin.HTTP_PROXY_PASSWORD);
		addArgs(jvmArgs, JavaLanguageServerPlugin.HTTPS_PROXY_HOST);
		addArgs(jvmArgs, JavaLanguageServerPlugin.HTTPS_PROXY_PORT);
		addArgs(jvmArgs, JavaLanguageServerPlugin.HTTPS_PROXY_USER);
		addArgs(jvmArgs, JavaLanguageServerPlugin.HTTPS_PROXY_PASSWORD);
		addArgs(jvmArgs, JavaLanguageServerPlugin.HTTP_NON_PROXY_HOSTS);
		addArgs(jvmArgs, JavaLanguageServerPlugin.HTTPS_NON_PROXY_HOSTS);
		FixedRequestAttributes attributes = new FixedRequestAttributes(location, null, distribution, null, jvmArgs, Collections.emptyList());
		CorePlugin.gradleWorkspaceManager().getGradleBuild(attributes).synchronize(newProjectHandler);
	}

	private void addArgs(List<String> jvmArgs, String name) {
		String value = System.getProperty(name);
		if (value != null) {
			jvmArgs.add(String.format("-D%s=%s", name, value));

		}
	}
}
