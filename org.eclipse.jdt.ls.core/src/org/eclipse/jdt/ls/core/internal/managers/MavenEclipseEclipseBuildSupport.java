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

import org.eclipse.core.resources.IProject;
import org.eclipse.jdt.ls.core.internal.ProjectUtils;
import org.eclipse.m2e.core.internal.IMavenConstants;

/**
 * @author davthomp
 */
public class MavenEclipseEclipseBuildSupport extends EclipseBuildSupport {

	@Override
	public boolean applies(IProject project) {
		return project.getFile(IMavenConstants.POM_FILE_NAME).exists() && !ProjectUtils.isMavenProject(project);
	}

}
