/*******************************************************************************
 * Copyright (c) 2020 Microsoft Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *     Microsoft Corporation - initial API and implementation
 *******************************************************************************/

package org.eclipse.jdt.ls.core.internal;

import static org.junit.Assert.assertEquals;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.core.runtime.OperationCanceledException;
import org.eclipse.jdt.ls.core.internal.managers.AbstractProjectsManagerBasedTest;
import org.eclipse.jdt.ls.core.internal.managers.ProjectsManager;
import org.junit.Test;

public class ProjectUtilsTest extends AbstractProjectsManagerBasedTest {
    @Test
    public void testGetRealFolderForDefaultProject() throws OperationCanceledException, CoreException {
        IProject project = ProjectsManager.createJavaProject(ProjectsManager.getDefaultProject(), new NullProgressMonitor());
        IPath path = ProjectUtils.getProjectRealFolder(project);
        assertEquals(project.getLocation(), path);
    }
}
