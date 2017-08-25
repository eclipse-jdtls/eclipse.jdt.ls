/*******************************************************************************
 * Copyright (c) 2017 Microsoft Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Microsoft Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.jdt.ls.core.internal.handlers;

import static org.eclipse.jdt.ls.core.internal.JavaLanguageServerPlugin.logException;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import org.eclipse.core.resources.IMarker;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IncrementalProjectBuilder;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.OperationCanceledException;
import org.eclipse.jdt.ls.core.internal.BuildWorkspaceResult;
import org.eclipse.jdt.ls.core.internal.BuildWorkspaceStatus;
import org.eclipse.jdt.ls.core.internal.ResourceUtils;

/**
 * @author xuzho
 *
 */
public class BuildWorkspaceHandler {

	public BuildWorkspaceResult buildWorkspace(IProgressMonitor monitor) {
		try {
			ResourcesPlugin.getWorkspace().build(IncrementalProjectBuilder.INCREMENTAL_BUILD, monitor);
			List<IMarker> errors = getBuildErrors(monitor);
			if (errors.isEmpty()) {
				return new BuildWorkspaceResult(BuildWorkspaceStatus.SUCCEED);
			} else {
				return new BuildWorkspaceResult(BuildWorkspaceStatus.FAILED, errors.stream().map(e -> e.toString()).collect(Collectors.joining(";")));
			}
		} catch (CoreException e) {
			logException("Failed to build workspace.", e);
			return new BuildWorkspaceResult(BuildWorkspaceStatus.FAILED, String.format("Exception: %s.", e.getMessage()));
		} catch (OperationCanceledException e) {
			return new BuildWorkspaceResult(BuildWorkspaceStatus.CANCELLED);
		}
	}

	private List<IMarker> getBuildErrors(IProgressMonitor monitor) throws CoreException {
		IProject[] projects = ResourcesPlugin.getWorkspace().getRoot().getProjects();
		List<IMarker> errors = new ArrayList<>();
		for (IProject project : projects) {
			errors.addAll(ResourceUtils.getErrorMarkers(project));
		}
		return errors;
	}
}
