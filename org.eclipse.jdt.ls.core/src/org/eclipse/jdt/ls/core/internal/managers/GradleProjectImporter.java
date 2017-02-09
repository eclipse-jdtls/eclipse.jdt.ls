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
import java.util.Collections;

import org.eclipse.buildship.core.CorePlugin;
import org.eclipse.buildship.core.util.gradle.GradleDistributionWrapper;
import org.eclipse.buildship.core.util.gradle.GradleDistributionWrapper.DistributionType;
import org.eclipse.buildship.core.workspace.NewProjectHandler;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.jdt.ls.core.internal.JavaLanguageServerPlugin;

import com.gradleware.tooling.toolingclient.GradleDistribution;
import com.gradleware.tooling.toolingmodel.repository.FixedRequestAttributes;

/**
 * @author Fred Bricon
 *
 */
public class GradleProjectImporter extends AbstractProjectImporter {

	protected static final GradleDistribution DEFAULT_DISTRIBUTION = GradleDistribution.fromBuild();

	/* (non-Javadoc)
	 * @see org.eclipse.jdt.ls.core.internal.managers.IProjectImporter#applies(org.eclipse.core.runtime.IProgressMonitor)
	 */
	@Override
	public boolean applies(IProgressMonitor monitor) throws InterruptedException, CoreException {
		return (rootFolder != null && new File(rootFolder, "build.gradle").exists());
	}

	/* (non-Javadoc)
	 * @see org.eclipse.jdt.ls.core.internal.managers.IProjectImporter#importToWorkspace(org.eclipse.core.runtime.IProgressMonitor)
	 */
	@Override
	public void importToWorkspace(IProgressMonitor monitor) throws InterruptedException, CoreException {
		if (!applies(monitor)) {
			return;
		}
		GradleDistribution distribution = DEFAULT_DISTRIBUTION;
		if (new File(rootFolder, "gradlew").exists()) {
			distribution = GradleDistributionWrapper.from(DistributionType.WRAPPER, null).toGradleDistribution();
		}
		JavaLanguageServerPlugin.logInfo("Importing Gradle project(s)");
		startSynchronization(rootFolder, distribution, NewProjectHandler.IMPORT_AND_MERGE);
	}

	protected void startSynchronization(File location, GradleDistribution distribution, NewProjectHandler newProjectHandler) {
		FixedRequestAttributes attributes = new FixedRequestAttributes(location, null, distribution, null, Collections.emptyList(), Collections.emptyList());
		CorePlugin.gradleWorkspaceManager().getGradleBuild(attributes).synchronize(newProjectHandler);
	}
}
