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
package org.eclipse.jdt.ls.core.internal.commands;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

import org.apache.commons.lang3.StringUtils;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Path;
import org.eclipse.core.runtime.Status;
import org.eclipse.jdt.core.IClassFile;
import org.eclipse.jdt.core.IClasspathContainer;
import org.eclipse.jdt.core.IClasspathEntry;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IPackageFragment;
import org.eclipse.jdt.core.IPackageFragmentRoot;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.internal.core.PackageFragment;
import org.eclipse.jdt.ls.core.internal.JavaLanguageServerPlugin;
import org.eclipse.jdt.ls.core.internal.ResourceUtils;
import org.eclipse.lsp4j.jsonrpc.messages.Either;

public class ClasspathCommand {

	private static Map<String, Function<List<String>, Either<ClasspathItem[], String>>> commands = new HashMap<>();

	static {
		commands.put(ClasspathItem.CONTAINER, ClasspathCommand::getContainers);
		commands.put(ClasspathItem.JAR, ClasspathCommand::getJars);
		commands.put(ClasspathItem.PACKAGE, ClasspathCommand::getPackages);
		commands.put(ClasspathItem.CLASSFILE, ClasspathCommand::getClassfiles);
		commands.put(ClasspathItem.SOURCE, ClasspathCommand::getSource);
	}

	public static Either<ClasspathItem[], String> getClasspathItems(List<Object> arguments) throws CoreException {
		if (arguments == null || arguments.size() < 2) {
			return Either.forLeft(new ClasspathItem[0]);
		}
		if (arguments.get(0) instanceof String && arguments.get(1) instanceof ArrayList<?>) {
			String route = (String) arguments.get(0);
			ArrayList<String> query = (ArrayList<String>) arguments.get(1);
			Function<List<String>, Either<ClasspathItem[], String>> loader = commands.get(route);
			if (loader == null) {
				throw new CoreException(new Status(IStatus.ERROR, JavaLanguageServerPlugin.PLUGIN_ID, String.format("No loader for %s for ClasspathCommand", route)));
			}
			return loader.apply(query);
		} else {
			throw new CoreException(new Status(IStatus.ERROR, JavaLanguageServerPlugin.PLUGIN_ID, "Arguments are not correct for ClasspathItem.getClasspathItem"));
		}
	}

	private static Either<ClasspathItem[], String> getContainers(List<String> query) {
		IJavaProject javaProject = getJavaProject(query.get(0));

		if (javaProject != null) {
			try {
				IClasspathEntry[] references = javaProject.getRawClasspath();
				return Either.forLeft(Arrays.stream(references).filter(entry -> entry.getEntryKind() == IClasspathEntry.CPE_CONTAINER).map(entry -> {
					try {
						IClasspathContainer container = JavaCore.getClasspathContainer(entry.getPath(), javaProject);
						ClasspathItem containerItem = new ClasspathItem(container.getDescription(), container.getPath(), ClasspathItem.CONTAINER);
						return containerItem;
					} catch (CoreException e) {
						// Ignore it
					}
					return null;
				}).filter(containerItem -> containerItem != null).toArray(ClasspathItem[]::new));
			} catch (CoreException e) {
				JavaLanguageServerPlugin.logException("Problem load project library ", e);
			}
		}
		return Either.forLeft(new ClasspathItem[0]);
	}

	private static Either<ClasspathItem[], String> getJars(List<String> query) {
		IJavaProject javaProject = getJavaProject(query.get(0));

		if (javaProject != null) {
			try {
				IClasspathContainer container = JavaCore.getClasspathContainer(Path.fromPortableString(query.get(1)), javaProject);
				ArrayList<ClasspathItem> children = new ArrayList<>();

				for (IClasspathEntry entry : container.getClasspathEntries()) {
					IPackageFragmentRoot packageFragmentRoot = javaProject.findPackageFragmentRoot(entry.getPath());
					children.add(new ClasspathItem(packageFragmentRoot.getElementName(), entry.getPath(), ClasspathItem.JAR));
				}
				return Either.forLeft(children.toArray(new ClasspathItem[children.size()]));
			} catch (CoreException e) {
				JavaLanguageServerPlugin.logException("Problem load project JAR entries ", e);
			}
		}

		return Either.forLeft(new ClasspathItem[0]);
	}

	private static Either<ClasspathItem[], String> getPackages(List<String> query) {
		IJavaProject javaProject = getJavaProject(query.get(0));

		if (javaProject != null) {
			try {
				IPackageFragmentRoot packageRoot = javaProject.findPackageFragmentRoot(ResourceUtils.filePathFromURI(query.get(1)));
				if (packageRoot == null) {
					throw new CoreException(new Status(IStatus.ERROR, JavaLanguageServerPlugin.PLUGIN_ID, String.format("No package root found for %s", query.get(1))));
				}
				IJavaElement[] elements = packageRoot.getChildren();
				return Either.forLeft(Arrays.stream(elements).filter(element -> {
					if (element instanceof PackageFragment) {
						try {
							return ((PackageFragment) element).hasChildren();
						} catch (JavaModelException e) {
							return false;
						}
					}
					return false;
				}).map(element -> new ClasspathItem(element.getElementName(), element.getPath(), ClasspathItem.PACKAGE)).toArray(ClasspathItem[]::new));
			} catch (CoreException e) {
				JavaLanguageServerPlugin.logException("Problem load project package ", e);
			}
		}
		return Either.forLeft(new ClasspathItem[0]);
	}

	private static Either<ClasspathItem[], String> getClassfiles(List<String> query) {
		IJavaProject javaProject = getJavaProject(query.get(0));
		if (javaProject != null) {
			try {
				IPackageFragmentRoot packageRoot = javaProject.findPackageFragmentRoot(ResourceUtils.filePathFromURI(query.get(1)));
				if (packageRoot == null) {
					throw new CoreException(new Status(IStatus.ERROR, JavaLanguageServerPlugin.PLUGIN_ID, String.format("No package root found for %s", query.get(1))));
				}
				IPackageFragment packageFragment = packageRoot.getPackageFragment(query.get(2));
				if (packageFragment != null) {
					IClassFile[] classFiles = packageFragment.getAllClassFiles();
					return Either.forLeft(Arrays.stream(classFiles).map(classFile -> new ClasspathItem(classFile.getElementName(), classFile.getPath(), ClasspathItem.CLASSFILE)).toArray(ClasspathItem[]::new));
				}
			} catch (CoreException e) {
				JavaLanguageServerPlugin.logException("Problem load project classfile list ", e);
			}
		}
		return Either.forLeft(new ClasspathItem[0]);
	}

	private static Either<ClasspathItem[], String> getSource(List<String> query) {
		IJavaProject javaProject = getJavaProject(query.get(0));
		if (javaProject != null) {
			try {
				IPackageFragmentRoot packageRoot = javaProject.findPackageFragmentRoot(ResourceUtils.filePathFromURI(query.get(1)));
				if (packageRoot == null) {
					throw new CoreException(new Status(IStatus.ERROR, JavaLanguageServerPlugin.PLUGIN_ID, String.format("No package root found for %s", query.get(1))));
				}
				IPackageFragment packageFragment = packageRoot.getPackageFragment(query.get(2));
				IClassFile[] classFiles = packageFragment.getAllClassFiles();
				Optional<IClassFile> result = Arrays.stream(classFiles).filter(classFile -> classFile.getElementName().equals(query.get(3))).findAny();
				if (result.isPresent()) {
					String content = result.get().getSource();
					return Either.forRight(StringUtils.isBlank(content) ? "" : content);
				}
			} catch (CoreException e) {
				JavaLanguageServerPlugin.logException("Problem load classfile source ", e);
			}
		}
		return Either.forLeft(new ClasspathItem[0]);
	}

	private static IJavaProject getJavaProject(String projectUri) {
		IPath rootPath = ResourceUtils.filePathFromURI(projectUri);
		IProject[] projects = ResourcesPlugin.getWorkspace().getRoot().getProjects();
		IProject targetProj = null;
		if (rootPath != null && projects.length > 0) {
			for (IProject proj : projects) {
				String projectLocation = proj.getLocation().toString().toLowerCase();
				String selectedPath = rootPath.toString().toLowerCase();
				if (!StringUtils.isBlank(selectedPath) && !StringUtils.isBlank(projectLocation)) {
					if (selectedPath.startsWith(projectLocation)) {
						targetProj = proj;
						break;
					}

				}
			}
		}
		return JavaCore.create(targetProj);
	}
}
