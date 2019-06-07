/*******************************************************************************
 * Copyright (c) 2019 Red Hat Inc. and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Red Hat Inc. - initial API and implementation
 *******************************************************************************/
package org.eclipse.jdt.ls.core.internal.managers;

import org.eclipse.core.resources.IProject;

public class DefaultProjectBuildSupport extends EclipseBuildSupport implements IBuildSupport {

	private ProjectsManager projectManager;

	public DefaultProjectBuildSupport(ProjectsManager projectManager) {
		this.projectManager = projectManager;
	}

	@Override
	public boolean applies(IProject project) {
		return projectManager.getDefaultProject().equals(project);
	}

}
