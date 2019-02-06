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

import java.util.Set;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.IPath;

import com.google.common.collect.Sets;

public class EclipseBuildSupport implements IBuildSupport {

	private Set<String> files = Sets.newHashSet(".classpath", ".project", ".factorypath");
	private Set<String> folders = Sets.newHashSet(".settings");

	@Override
	public boolean applies(IProject project) {
		return true; //all projects are Eclipse projects
	}

	@Override
	public boolean isBuildFile(IResource resource) {
		if (resource == null || resource.getProject() == null) {
			return false;
		}
		IProject project = resource.getProject();
		for (String file : files) {
			if (resource.equals(project.getFile(file))) {
				return true;
			}
		}
		IPath path = resource.getFullPath();
		for (String folder : folders) {
			IPath folderPath = project.getFolder(folder).getFullPath();
			if (folderPath.isPrefixOf(path)) {
				return true;
			}
		}
		return false;
	}

}
