/*******************************************************************************
 * Copyright (c) 2020 Microsoft Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Microsoft Corporation - initial API and implementation
*******************************************************************************/

package org.eclipse.jdt.ls.core.internal.handlers;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.debug.core.sourcelookup.ISourceContainer;
import org.eclipse.jdt.core.IClassFile;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.launching.IRuntimeClasspathEntry;
import org.eclipse.jdt.launching.JavaRuntime;
import org.eclipse.jdt.launching.sourcelookup.containers.JavaProjectSourceContainer;
import org.eclipse.jdt.ls.core.internal.JDTUtils;
import org.eclipse.jdt.ls.core.internal.JavaLanguageServerPlugin;
import org.eclipse.jdt.ls.core.internal.ProjectUtils;

public class JdtSourceLookUpProvider {

    /**
     * Resolve the uri of the source file or class file by the class fully qualified name, the source path
     * and the interested projects.
     *
     * @param fullyQualifiedName
     *              the fully qualified name of the class.
     * @param sourcePath
     *              the source path of the class.
     * @param projectNames
     *              A list of the project names that needs to search in. If the given list is empty,
     *              All the projects in the workspace will be searched.
     *
     * @return the uri of the associated source file or class file.
     */
    public String getSourceFileURI(String fullyQualifiedName, String sourcePath, List<String> projectNames) {
        if (sourcePath == null) {
            return null;
        }

        Object sourceElement = findSourceElement(sourcePath, getSourceContainers(projectNames));
		if (sourceElement instanceof IResource resource) {
			return JDTUtils.getFileURI(resource);
		} else if (sourceElement instanceof IClassFile clazz) {
			return JDTUtils.toUri(clazz);
        }
        return null;
    }

    /**
     * Given a source name info, search the associated source file or class file from the source container list.
     *
     * @param sourcePath
     *                  the target source name (e.g. org\eclipse\jdt\ls\xxx.java).
     * @param containers
     *                  the source container list.
     * @return the associated source file or class file.
     */
    private Object findSourceElement(String sourcePath, ISourceContainer[] containers) {
        if (containers == null) {
            return null;
        }
        for (ISourceContainer container : containers) {
            try {
                Object[] objects = container.findSourceElements(sourcePath);
                if (objects.length > 0 && (objects[0] instanceof IResource || objects[0] instanceof IClassFile)) {
                    return objects[0];
                }
            } catch (CoreException e) {
                JavaLanguageServerPlugin.logException("Failed to find the source elements", e);
            }
        }
        return null;
    }

    private ISourceContainer[] getSourceContainers(List<String> projectNames) {
        List<IProject> projects = new ArrayList<>();
        if (projectNames == null || projectNames.size() == 0) {
            projects.addAll(Arrays.asList(ProjectUtils.getAllProjects()));
        } else {
            for (String projectName : projectNames) {
                projects.add(ProjectUtils.getProject(projectName));
            }
        }

        Set<ISourceContainer> containers = new LinkedHashSet<>();
        Set<IRuntimeClasspathEntry> calculated = new LinkedHashSet<>();
        projects.stream().map(project -> ProjectUtils.getJavaProject(project))
            .filter(javaProject -> javaProject != null && javaProject.exists())
            .forEach(javaProject -> {
                // Add source containers associated with the project's runtime classpath entries.
                containers.addAll(Arrays.asList(getSourceContainers(javaProject, calculated)));
                // Add source containers associated with the project's source folders.
                containers.add(new JavaProjectSourceContainer(javaProject));
            });

        return containers.toArray(new ISourceContainer[0]);
    }

    private ISourceContainer[] getSourceContainers(IJavaProject project, Set<IRuntimeClasspathEntry> calculated) {
        if (project == null || !project.exists()) {
            return new ISourceContainer[0];
        }

        try {
            IRuntimeClasspathEntry[] unresolved = JavaRuntime.computeUnresolvedRuntimeClasspath(project);
            List<IRuntimeClasspathEntry> resolved = new ArrayList<>();
            for (IRuntimeClasspathEntry entry : unresolved) {
                for (IRuntimeClasspathEntry resolvedEntry : JavaRuntime.resolveRuntimeClasspathEntry(entry, project)) {
                    if (!calculated.contains(resolvedEntry)) {
                        calculated.add(resolvedEntry);
                        resolved.add(resolvedEntry);
                    }
                }
            }
            Set<ISourceContainer> containers = new LinkedHashSet<>();
            containers.addAll(Arrays.asList(
                    JavaRuntime.getSourceContainers(resolved.toArray(new IRuntimeClasspathEntry[0]))));
            return containers.toArray(new ISourceContainer[0]);
        } catch (CoreException e) {
            JavaLanguageServerPlugin.logException("Failed to find the source elements for project: " + project.getElementName(), e);
        }

        return new ISourceContainer[0];
    }
}
