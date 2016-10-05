/*******************************************************************************
 * Copyright (c) 2016 Red Hat Inc. and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Red Hat Inc. - initial API and implementation
 *******************************************************************************/
package org.jboss.tools.vscode.java.internal;

import static org.junit.Assert.assertEquals;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.NullProgressMonitor;

/**
 * @author Fred Bricon
 *
 */
public class WorkspaceHelper {

	private WorkspaceHelper() {
		//No instances allowed
	}

	public static void initWorkspace() {
		List<IProject> projects = new ArrayList<>(1);
		JavaLanguageServerPlugin.getProjectsManager().createProject(null, projects, new NullProgressMonitor());
		assertEquals(1, projects.size());
	}

	public static void deleteAllProjects() {
		IProject[] projects = ResourcesPlugin.getWorkspace().getRoot().getProjects();
		Stream.of(projects).forEach(p -> delete(p));
	}

	public static void delete(IProject project) {
		try {
			project.delete(true, new NullProgressMonitor());
		} catch (CoreException e) {
			e.printStackTrace();
		}
	}

}
