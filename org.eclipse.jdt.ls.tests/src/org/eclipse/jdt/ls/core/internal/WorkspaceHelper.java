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

import static org.junit.Assert.assertEquals;

import java.util.Arrays;
import java.util.List;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.jdt.ls.core.internal.JavaLanguageServerPlugin;

/**
 * @author Fred Bricon
 *
 */
public class WorkspaceHelper {

	private WorkspaceHelper() {
		//No instances allowed
	}

	public static void initWorkspace() {
		JavaLanguageServerPlugin.getProjectsManager().initializeProjects(null, new NullProgressMonitor());
		assertEquals(1, getAllProjects().size());
	}


	public static IProject getProject(String name) {
		IProject project = ResourcesPlugin.getWorkspace().getRoot().getProject(name);
		return project.exists()?project:null;
	}

	public static void deleteAllProjects() {
		getAllProjects().forEach(p -> delete(p));
	}

	public static List<IProject> getAllProjects() {
		IProject[] projects = ResourcesPlugin.getWorkspace().getRoot().getProjects();
		return Arrays.asList(projects);
	}

	public static void delete(IProject project) {
		try {
			project.delete(true, new NullProgressMonitor());
		} catch (CoreException e) {
			e.printStackTrace();
		}
	}

}