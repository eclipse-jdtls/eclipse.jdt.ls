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

package org.eclipse.jdt.ls.core.internal.syntaxserver;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IFolder;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.OperationCanceledException;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.ls.core.internal.JDTUtils;
import org.eclipse.jdt.ls.core.internal.JavaClientConnection;
import org.eclipse.jdt.ls.core.internal.JavaLanguageServerPlugin;
import org.eclipse.jdt.ls.core.internal.ProjectUtils;
import org.eclipse.jdt.ls.core.internal.ResourceUtils;
import org.eclipse.jdt.ls.core.internal.handlers.BaseDiagnosticsHandler;
import org.eclipse.jdt.ls.core.internal.handlers.BaseDocumentLifeCycleHandler;
import org.eclipse.jdt.ls.core.internal.managers.InvisibleProjectImporter;
import org.eclipse.jdt.ls.core.internal.managers.ProjectsManager;
import org.eclipse.jdt.ls.core.internal.preferences.PreferenceManager;

public class SyntaxDocumentLifeCycleHandler extends BaseDocumentLifeCycleHandler {
	public static final String[][] KNOWN_SRC_PREFIXES = new String[][] {
		{ "src", "main", "java" },
		{ "src", "test", "java" },
		{ "src" }
	};

	private JavaClientConnection connection;
	private ProjectsManager projectsManager;

	public SyntaxDocumentLifeCycleHandler(JavaClientConnection connection, ProjectsManager projectsManager, PreferenceManager preferenceManager, boolean delayValidation) {
		super(preferenceManager, delayValidation);
		this.connection = connection;
		this.projectsManager = projectsManager;
	}

	public void setClient(JavaClientConnection connection) {
		this.connection = connection;
	}

	@Override
	public BaseDiagnosticsHandler createDiagnosticsHandler(ICompilationUnit unit) {
		return new SyntaxDiagnosticsHandler(connection, unit);
	}

	@Override
	public boolean isSyntaxMode(ICompilationUnit unit) {
		return true;
	}

	@Override
	public ICompilationUnit resolveCompilationUnit(String uri) {
		IFile resource = JDTUtils.findFile(uri);
		ICompilationUnit unit = JDTUtils.resolveCompilationUnit(resource);
		if (JDTUtils.isOnClassPath(unit)) {
			return unit;
		}

		// Open file not on the classpath.
		IPath filePath = ResourceUtils.canonicalFilePathFromURI(uri);
		Collection<IPath> rootPaths = preferenceManager.getPreferences().getRootPaths();
		Optional<IPath> belongedRootPath = rootPaths.stream().filter(rootPath -> rootPath.isPrefixOf(filePath)).findFirst();
		if (belongedRootPath.isPresent()) {
			if (tryUpdateClasspath(filePath, belongedRootPath.get())) {
				unit = JDTUtils.resolveCompilationUnit(uri);
				projectsManager.registerWatchers(true);;
			}
		}

		if (unit == null) {
			unit = JDTUtils.getFakeCompilationUnit(uri);
		}

		return unit;
	}

	private boolean tryUpdateClasspath(IPath triggerFile, IPath rootPath) {
		IProject invisibleProject = null;
		try {
			invisibleProject = ProjectUtils.createInvisibleProjectIfNotExist(rootPath);
		} catch (OperationCanceledException | CoreException ex) {
			JavaLanguageServerPlugin.logException("Failed to create invisible project", ex);
		}

		if (invisibleProject != null && invisibleProject.exists()) {
			IPath[] sourcePaths = tryResolveKnownSourcePaths(triggerFile, rootPath);
			if (sourcePaths.length == 0) {
				IPath sourcePath = InvisibleProjectImporter.tryResolveSourceDirectory(triggerFile, rootPath, KNOWN_SRC_PREFIXES);
				if (sourcePath != null) {
					sourcePaths = new IPath[] { sourcePath };
				}
			}

			IJavaProject javaProject = JavaCore.create(invisibleProject);
			for (IPath sourcePath : sourcePaths) {
				IFolder workspaceLinkFolder = invisibleProject.getFolder(ProjectUtils.WORKSPACE_LINK);
				IPath relativeSourcePath = sourcePath.makeRelativeTo(rootPath);
				IPath fullSourcePath = relativeSourcePath.isEmpty() ? workspaceLinkFolder.getFullPath() : workspaceLinkFolder.getFolder(relativeSourcePath).getFullPath();
				try {
					ProjectUtils.addSourcePath(fullSourcePath, javaProject);
				} catch (CoreException e) {
					JavaLanguageServerPlugin.logException("Failed to update the source path.", e);
				}
			}

			return sourcePaths.length > 0;
		}

		return false;
	}

	private IPath[] tryResolveKnownSourcePaths(IPath triggerFile, IPath rootPath) {
		List<IPath> sourcePaths = new ArrayList<>();
		IPath relativePath = triggerFile.makeRelativeTo(rootPath).removeTrailingSeparator();
		List<String> segments = Arrays.asList(relativePath.segments());
		int index = segments.lastIndexOf("src");
		if (index >= 0) {
			IPath srcPath = relativePath.removeLastSegments(segments.size() -1 - index);
			IPath container = rootPath.append(srcPath.removeLastSegments(1));
			if (container.append("pom.xml").toFile().exists()
				|| container.append("build.gradle").toFile().exists()) {
				IPath mainJavaPath = container.append("src").append("main").append("java");
				IPath testJavaPath = container.append("src").append("test").append("java");
				if (mainJavaPath.toFile().exists()) {
					sourcePaths.add(mainJavaPath);
				}
				if (testJavaPath.toFile().exists()) {
					sourcePaths.add(testJavaPath);
				}
			}
		}

		return sourcePaths.toArray(new IPath[0]);
	}
}
