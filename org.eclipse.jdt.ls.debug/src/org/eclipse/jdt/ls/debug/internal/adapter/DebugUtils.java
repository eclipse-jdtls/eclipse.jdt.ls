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

package org.eclipse.jdt.ls.debug.internal.adapter;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IWorkspaceRoot;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IMember;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.search.IJavaSearchConstants;
import org.eclipse.jdt.core.search.IJavaSearchScope;
import org.eclipse.jdt.core.search.SearchEngine;
import org.eclipse.jdt.core.search.SearchMatch;
import org.eclipse.jdt.core.search.SearchParticipant;
import org.eclipse.jdt.core.search.SearchPattern;
import org.eclipse.jdt.core.search.SearchRequestor;
import org.eclipse.jdt.internal.core.SourceField;
import org.eclipse.jdt.internal.core.SourceMethod;
import org.eclipse.jdt.internal.core.SourceType;
import org.eclipse.jdt.launching.JavaRuntime;
import org.eclipse.jdt.ls.core.internal.JDTUtils;
import org.eclipse.jdt.ls.core.internal.handlers.JsonRpcHelpers;
import org.eclipse.jdt.ls.debug.internal.JavaDebuggerServerPlugin;
import org.eclipse.jdt.ls.debug.internal.core.IBreakpoint;
import org.eclipse.jdt.ls.debug.internal.core.IBreakpointManager;
import org.eclipse.jdt.ls.debug.internal.core.breakpoints.FakeJavaLineBreakpoint;
import org.eclipse.jdt.ls.debug.internal.core.breakpoints.JavaLineBreakpoint;
import org.eclipse.jdt.ls.debug.internal.core.log.Logger;

public class DebugUtils {

    /**
     * According to source breakpoint, set breakpoint info to debugee VM.
     */
    public static List<Types.Breakpoint> addBreakpoint(Types.Source source, Types.SourceBreakpoint[] lines,
            boolean sourceModified, IBreakpointManager manager) {
        List<Types.Breakpoint> resBreakpoints = new ArrayList<>();
        List<IBreakpoint> javaBreakpoints = new ArrayList<>();
        Path sourcePath = Paths.get(source.path);
        ICompilationUnit element = JDTUtils.resolveCompilationUnit(sourcePath.toUri());
        
        for (Types.SourceBreakpoint bp : lines) {
            boolean valid = false;
            try {
                String fqn = null;
                int offset = JsonRpcHelpers.toOffset(element.getBuffer(), bp.line, 0);
                IJavaElement javaElement = element.getElementAt(offset);
                if (javaElement instanceof SourceField || javaElement instanceof SourceMethod) {
                    IType type = ((IMember) javaElement).getDeclaringType();
                    fqn = type.getFullyQualifiedName();
                } else if (javaElement instanceof SourceType) {
                    fqn = ((SourceType) javaElement).getFullyQualifiedName();
                }

                if (fqn != null) {
                    javaBreakpoints.add(new JavaLineBreakpoint(fqn, bp.line, -1));
                    valid = true;
                }
            } catch (Exception e) {
                Logger.logException("Add breakpoint exception", e);
            }
            if (!valid) {
                javaBreakpoints.add(new FakeJavaLineBreakpoint(null, bp.line, -1));
            }
        }

        IBreakpoint[] added = manager.addBreakpoints(sourcePath.normalize().toString(), javaBreakpoints.toArray(new IBreakpoint[0]), sourceModified);
        for (IBreakpoint add : added) {
            resBreakpoints.add(new Types.Breakpoint(add.getId(), add.isVerified(), ((JavaLineBreakpoint) add).getLineNumber(), ""));
        }
        
        return resBreakpoints;
    }
    
    /**
     * Search the absolute path of the java file under the specified source path directory.
     * @param sourcePath
     *                  the project source path directories
     * @param sourceName
     *                  the java file path
     * @return the absolute file path
     */
    public static String sourceLookup(String[] sourcePath, String sourceName) {
        for (String path : sourcePath) {
            String fullpath = Paths.get(path, sourceName).toString();
            if (new File(fullpath).isFile()) {
                return fullpath;
            }
        }
        return null;
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
    public static IJavaProject getJavaProjectFromName(String projectName) throws CoreException {
        IWorkspaceRoot root = ResourcesPlugin.getWorkspace().getRoot();
        IProject project = root.getProject(projectName);
        if (!project.exists()) {
            throw new CoreException(new Status(IStatus.ERROR, JavaDebuggerServerPlugin.PLUGIN_ID, "Not an existed project."));
        }
        if (!project.isNatureEnabled("org.eclipse.jdt.core.javanature")) {
            throw new CoreException(new Status(IStatus.ERROR, JavaDebuggerServerPlugin.PLUGIN_ID, "Not a project with java nature."));
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
    public static List<IJavaProject> getJavaProjectFromType(String typeFullyQualifiedName) throws CoreException {
        SearchPattern pattern = SearchPattern.createPattern(
                typeFullyQualifiedName,
                IJavaSearchConstants.TYPE,
                IJavaSearchConstants.DECLARATIONS,
                SearchPattern.R_EXACT_MATCH);
        IJavaSearchScope scope = SearchEngine.createWorkspaceScope();
        ArrayList<IJavaProject> projects = new ArrayList<>();
        SearchRequestor requestor = new SearchRequestor() {
            @Override
            public void acceptSearchMatch(SearchMatch match) {
                Object element = match.getElement();
                if (element instanceof IJavaElement) {
                    projects.add(((IJavaElement)element).getJavaProject());
                }
            }
        };
        SearchEngine searchEngine = new SearchEngine();
        searchEngine.search(
                pattern,
                new SearchParticipant[] { SearchEngine.getDefaultSearchParticipant() },
                scope,
                requestor,
                null /* progress monitor */);

        return projects;
    }

    /**
     * Compute runtime classpath.
     * 
     * @param javaProject
     *            java project
     * @return class path
     * @throws CoreException
     *             CoreException
     */
    public static String computeClassPath(IJavaProject javaProject) throws CoreException {
        if (javaProject == null) {
            throw new IllegalArgumentException("javaProject is null");
        }
        String[] classPathArray = JavaRuntime.computeDefaultRuntimeClassPath(javaProject);
        String classPath = String.join(System.getProperty("path.separator"), classPathArray);
        return classPath;
    }
}
