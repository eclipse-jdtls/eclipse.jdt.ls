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
package org.eclipse.jdt.ls.core.internal;

import java.util.Map;

import org.eclipse.buildship.core.configuration.GradleProjectNature;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.m2e.core.internal.IMavenConstants;

/**
 * @author Fred Bricon
 */
@SuppressWarnings("restriction")
public class ProjectUtils {

	private ProjectUtils() {
		//No instanciation
	}

	public static boolean hasNature(IProject project, String natureId) {
		try {
			return project != null && project.hasNature(natureId);
		} catch (CoreException e) {
			return false;
		}
	}

	public static boolean isJavaProject(IProject project) {
		return hasNature(project, JavaCore.NATURE_ID);
	}

	public static boolean isMavenProject(IProject project) {
		return hasNature(project, IMavenConstants.NATURE_ID);
	}

	public static boolean isGradleProject(IProject project) {
		return hasNature(project, GradleProjectNature.ID);
	}

	public static String getJavaSourceLevel(IProject project) {
		if (!isJavaProject(project)) {
			return null;
		}
		IJavaProject javaProject = JavaCore.create(project);
		Map<String, String> options = javaProject.getOptions(true);
		return options.get(JavaCore.COMPILER_SOURCE);
	}

}
