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
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.eclipse.core.resources.IContainer;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IWorkspaceRoot;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
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
import org.eclipse.jdt.ls.core.internal.JDTUtils;
import org.eclipse.jdt.ls.core.internal.JavaLanguageServerPlugin;
import org.eclipse.lsp4j.jsonrpc.json.adapters.CollectionTypeAdapterFactory;
import org.eclipse.lsp4j.jsonrpc.json.adapters.EitherTypeAdapterFactory;
import org.eclipse.lsp4j.jsonrpc.json.adapters.EnumTypeAdapterFactory;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

public class ClasspathCommand {

	private static final Gson gson = new GsonBuilder()
			.registerTypeAdapterFactory(new CollectionTypeAdapterFactory())
			.registerTypeAdapterFactory(new EitherTypeAdapterFactory())
			.registerTypeAdapterFactory(new EnumTypeAdapterFactory())
			.create();

	private static final Map<ClasspathNodeKind, Function<ClasspathQuery, List<ClasspathNode>>> commands;

	static {
		commands = new HashMap<>();
		commands.put(ClasspathNodeKind.CONTAINER, ClasspathCommand::getContainers);
		commands.put(ClasspathNodeKind.JAR, ClasspathCommand::getJars);
		commands.put(ClasspathNodeKind.PACKAGE, ClasspathCommand::getPackages);
		commands.put(ClasspathNodeKind.CLASSFILE, ClasspathCommand::getClassfiles);
	}

	/**
	 * Get the child list of ClasspathNode for the project dependency node of the
	 * type {@link ClasspathNode}
	 *
	 * @param arguments
	 *            List of the arguments which contain two entries to get class path
	 *            children: the first entry is the query type
	 *            {@link ClasspathNodeKind} and the second one is the query instance
	 *            of type {@link ClasspathQuery}
	 * @return the found ClasspathNode list
	 * @throws CoreException
	 */
	public static List<ClasspathNode> getChildren(List<Object> arguments) throws CoreException {
		if (arguments == null || arguments.size() < 2) {
			return Collections.emptyList();
		}
		ClasspathNodeKind classpathKind = gson.fromJson(gson.toJson(arguments.get(0)), ClasspathNodeKind.class);
		ClasspathQuery params = gson.fromJson(gson.toJson(arguments.get(1)), ClasspathQuery.class);

		Function<ClasspathQuery, List<ClasspathNode>> loader = commands.get(classpathKind);
		if (loader == null) {
			throw new CoreException(new Status(IStatus.ERROR, JavaLanguageServerPlugin.PLUGIN_ID,
					String.format("Unknown classpath item type: %s", classpathKind)));
		}
		return loader.apply(params);
	}

	/**
	 * Get the source content from the .class file URI
	 *
	 * @param query
	 *            the .class file URI
	 * @return source content of the .class file. If the file content is not
	 *         available, return the empty string.
	 */
	public static String getSource(List<Object> query) {
		String uri = (String) query.get(0);
		IClassFile classfile = JDTUtils.resolveClassFile(uri);
		try {
			String content = classfile.getSource();
			return StringUtils.isBlank(content) ? "" : content;
		} catch (CoreException e) {
			JavaLanguageServerPlugin.logException("Failed to getSource from " + classfile.toString(), e);
		}
		return "";
	}

	private static List<ClasspathNode> getContainers(ClasspathQuery query) {
		IJavaProject javaProject = getJavaProject(query.getProjectUri());

		if (javaProject != null) {
			try {
				IClasspathEntry[] references = javaProject.getRawClasspath();
				return Arrays.stream(references).filter(entry -> entry.getEntryKind() == IClasspathEntry.CPE_CONTAINER).map(entry -> {
					try {
						IClasspathContainer container = JavaCore.getClasspathContainer(entry.getPath(), javaProject);
						ClasspathNode containerNode = new ClasspathNode(container.getDescription(), container.getPath().toPortableString(), ClasspathNodeKind.CONTAINER);
						return containerNode;
					} catch (CoreException e) {
						// Ignore it
					}
					return null;
				}).filter(containerNode -> containerNode != null).collect(Collectors.toList());
			} catch (CoreException e) {
				JavaLanguageServerPlugin.logException("Problem load project library ", e);
			}
		}
		return Collections.emptyList();
	}

	private static List<ClasspathNode> getJars(ClasspathQuery query) {
		IJavaProject javaProject = getJavaProject(query.getProjectUri());

		if (javaProject != null) {
			try {
				IClasspathEntry[] references = javaProject.getRawClasspath();
				IClasspathEntry containerEntry = null;
				for (IClasspathEntry reference : references) {
					if (reference.getPath().equals(Path.fromPortableString(query.getNodePath()))) {
						containerEntry = reference;
						break;
					}
				}
				ArrayList<ClasspathNode> children = new ArrayList<>();
				IPackageFragmentRoot[] packageFragmentRoots = javaProject.findPackageFragmentRoots(containerEntry);
				for (IPackageFragmentRoot fragmentRoot : packageFragmentRoots) {
					children.add(new ClasspathNode(fragmentRoot.getElementName(), fragmentRoot.getPath().toPortableString(), ClasspathNodeKind.JAR));
				}
				return children;
			} catch (CoreException e) {
				JavaLanguageServerPlugin.logException("Problem load project JAR entries ", e);
			}
		}

		return Collections.emptyList();
	}

	private static List<ClasspathNode> getPackages(ClasspathQuery query) {
		IJavaProject javaProject = getJavaProject(query.getProjectUri());

		if (javaProject != null) {
			try {
				IPackageFragmentRoot packageRoot = javaProject.findPackageFragmentRoot(Path.fromPortableString(query.getNodePath()));
				if (packageRoot == null) {
					throw new CoreException(new Status(IStatus.ERROR, JavaLanguageServerPlugin.PLUGIN_ID, String.format("No package root found for %s", query.getNodePath())));
				}
				IJavaElement[] elements = packageRoot.getChildren();
				return Arrays.stream(elements).filter(element -> {
					if (element instanceof PackageFragment) {
						try {
							return ((PackageFragment) element).hasChildren();
						} catch (JavaModelException e) {
							return false;
						}
					}
					return false;
				}).map(element -> new ClasspathNode(element.getElementName(), element.getPath().toPortableString(), ClasspathNodeKind.PACKAGE)).collect(Collectors.toList());
			} catch (CoreException e) {
				JavaLanguageServerPlugin.logException("Problem load project package ", e);
			}
		}
		return Collections.emptyList();
	}

	private static List<ClasspathNode> getClassfiles(ClasspathQuery query) {
		IJavaProject javaProject = getJavaProject(query.getProjectUri());
		if (javaProject != null) {
			try {
				IPackageFragmentRoot packageRoot = javaProject.findPackageFragmentRoot(Path.fromPortableString(query.getNodePath()));
				if (packageRoot == null) {
					throw new CoreException(new Status(IStatus.ERROR, JavaLanguageServerPlugin.PLUGIN_ID, String.format("No package root found for %s", query.getNodePath())));
				}
				IPackageFragment packageFragment = packageRoot.getPackageFragment(query.getNodeId());
				if (packageFragment != null) {
					IClassFile[] classFiles = packageFragment.getAllClassFiles();
					return Arrays.stream(classFiles).map(classFile -> {
						ClasspathNode item = new ClasspathNode(classFile.getElementName(), classFile.getPath().toPortableString(), ClasspathNodeKind.CLASSFILE);
						item.setUri(JDTUtils.toUri(classFile));
						return item;
					}).collect(Collectors.toList());
				}
			} catch (CoreException e) {
				JavaLanguageServerPlugin.logException("Problem load project classfile list ", e);
			}
		}
		return Collections.emptyList();
	}

	private static IJavaProject getJavaProject(String projectUri) {
		IWorkspaceRoot root = ResourcesPlugin.getWorkspace().getRoot();
		IContainer[] containers = root.findContainersForLocationURI(JDTUtils.toURI(projectUri));

		if (containers.length == 0) {
			return null;
		}

		IContainer container = containers[0];
		IProject project = container.getProject();
		if (!project.exists()) {
			return null;
		}

		return JavaCore.create(project);
	}
}
