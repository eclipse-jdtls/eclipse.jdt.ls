/*******************************************************************************
 * Copyright (c) 2017 Red Hat Inc. and others.
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
import java.util.concurrent.CompletableFuture;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IWorkspaceRoot;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.search.IJavaSearchConstants;
import org.eclipse.jdt.core.search.IJavaSearchScope;
import org.eclipse.jdt.core.search.SearchEngine;
import org.eclipse.jdt.core.search.SearchMatch;
import org.eclipse.jdt.core.search.SearchParticipant;
import org.eclipse.jdt.core.search.SearchPattern;
import org.eclipse.jdt.core.search.SearchRequestor;
import org.eclipse.jdt.launching.JavaRuntime;
import org.eclipse.jdt.ls.core.internal.ClasspathResolveRequestParams;
import org.eclipse.jdt.ls.core.internal.JavaLanguageServerPlugin;
import org.eclipse.lsp4j.jsonrpc.CompletableFutures;
import org.eclipse.lsp4j.jsonrpc.ResponseErrorException;
import org.eclipse.lsp4j.jsonrpc.messages.ResponseError;

/**
 * @author xuzho
 *
 */
public class ResolveClasspathsHandler {

	public CompletableFuture<String> resolveClasspaths(ClasspathResolveRequestParams param) {
		return CompletableFutures.computeAsync(cm -> {
			try {
				return computeClassPath(param.getProjectName(), param.getStartupClass());
			} catch (CoreException e) {
				logException("Failed to resolve classpath.", e);
				throw new ResponseErrorException(new ResponseError(-32602, "Failed to resolve classpath: " + e.getMessage(), e));
			}
		});
	}

	/**
	 * Get java project from name.
	 *
	 * @param projectName
	 *            project name
	 * @return java project
	 * @throws CoreException
	 *             CoreException
	 */
	private static IJavaProject getJavaProjectFromName(String projectName) throws CoreException {
		IWorkspaceRoot root = ResourcesPlugin.getWorkspace().getRoot();
		IProject project = root.getProject(projectName);
		if (!project.exists()) {
			throw new CoreException(new Status(IStatus.ERROR, JavaLanguageServerPlugin.PLUGIN_ID, String.format("Cannot find the project with name '%s'.", projectName)));
		}
		if (!project.isNatureEnabled("org.eclipse.jdt.core.javanature")) {
			throw new CoreException(new Status(IStatus.ERROR, JavaLanguageServerPlugin.PLUGIN_ID, String.format("The project '%s' does not have java nature enabled.", projectName)));
		}
		IJavaProject javaProject = JavaCore.create(project);
		return javaProject;
	}

	/**
	 * Get java project from type.
	 *
	 * @param typeFullyQualifiedName
	 *            fully qualified name of type
	 * @return java project
	 * @throws CoreException
	 *             CoreException
	 */
	private static List<IJavaProject> getJavaProjectFromType(String typeFullyQualifiedName) throws CoreException {
		SearchPattern pattern = SearchPattern.createPattern(typeFullyQualifiedName, IJavaSearchConstants.TYPE, IJavaSearchConstants.DECLARATIONS, SearchPattern.R_EXACT_MATCH);
		IJavaSearchScope scope = SearchEngine.createWorkspaceScope();
		ArrayList<IJavaProject> projects = new ArrayList<>();
		SearchRequestor requestor = new SearchRequestor() {
			@Override
			public void acceptSearchMatch(SearchMatch match) {
				Object element = match.getElement();
				if (element instanceof IJavaElement) {
					projects.add(((IJavaElement) element).getJavaProject());
				}
			}
		};
		SearchEngine searchEngine = new SearchEngine();
		searchEngine.search(pattern, new SearchParticipant[] { SearchEngine.getDefaultSearchParticipant() }, scope, requestor, null /* progress monitor */);

		return projects;
	}

	/**
	 * Accord to the project name and the main class, compute runtime classpath.
	 *
	 * @param projectName
	 *            project name
	 * @param mainClass
	 *            full qualified class name
	 * @return class path
	 * @throws CoreException
	 *             CoreException
	 */
	private static String computeClassPath(String projectName, String mainClass) throws CoreException {
		IJavaProject project = null;
		// if type exists in multiple projects, debug configuration need provide project name.
		if (projectName != null) {
			project = getJavaProjectFromName(projectName);
		} else {
			List<IJavaProject> projects = getJavaProjectFromType(mainClass);
			if (projects.size() == 0) {
				throw new CoreException(new Status(IStatus.ERROR, JavaLanguageServerPlugin.PLUGIN_ID, String.format("Main class '%s' doesn't exist in the workspace.", mainClass)));
			}
			if (projects.size() > 1) {
				throw new CoreException(new Status(IStatus.ERROR, JavaLanguageServerPlugin.PLUGIN_ID, String.format("Main class '%s' isn't unique in the workspace, please pass in specified projectname.", mainClass)));
			}
			project = projects.get(0);
		}
		return computeClassPath(project);
	}

	/**
	 * Compute runtime classpath of a java project.
	 *
	 * @param javaProject
	 *            java project
	 * @return class path
	 * @throws CoreException
	 *             CoreException
	 */
	private static String computeClassPath(IJavaProject javaProject) throws CoreException {
		if (javaProject == null) {
			throw new IllegalArgumentException("javaProject is null");
		}
		String[] classPathArray = JavaRuntime.computeDefaultRuntimeClassPath(javaProject);
		String classPath = String.join(System.getProperty("path.separator"), classPathArray);
		return classPath;
	}
}
