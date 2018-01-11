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
import java.util.function.BiFunction;
import java.util.stream.Collectors;

import org.eclipse.core.resources.IContainer;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IWorkspaceRoot;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.OperationCanceledException;
import org.eclipse.core.runtime.Path;
import org.eclipse.core.runtime.Status;
import org.eclipse.jdt.core.IClassFile;
import org.eclipse.jdt.core.IClasspathContainer;
import org.eclipse.jdt.core.IClasspathEntry;
import org.eclipse.jdt.core.IJarEntryResource;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IPackageFragment;
import org.eclipse.jdt.core.IPackageFragmentRoot;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.internal.core.JarEntryDirectory;
import org.eclipse.jdt.internal.core.JarEntryFile;
import org.eclipse.jdt.internal.core.JarEntryResource;
import org.eclipse.jdt.internal.core.JrtPackageFragmentRoot;
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

	private static final Map<ClasspathNodeKind, BiFunction<ClasspathQuery, IProgressMonitor, List<ClasspathNode>>> commands;

	static {
		commands = new HashMap<>();
		commands.put(ClasspathNodeKind.CONTAINER, ClasspathCommand::getContainers);
		commands.put(ClasspathNodeKind.JAR, ClasspathCommand::getJars);
		commands.put(ClasspathNodeKind.PACKAGE, ClasspathCommand::getPackages);
		commands.put(ClasspathNodeKind.CLASSFILE, ClasspathCommand::getClassfiles);
		commands.put(ClasspathNodeKind.Folder, ClasspathCommand::getFolderChildren);
	}

	/**
	 * Get the child list of ClasspathNode for the project dependency node.
	 *
	 * @param arguments
	 *            List of the arguments which contain two entries to get class path
	 *            children: the first entry is the query target node type
	 *            {@link ClasspathNodeKind} and the second one is the query instance
	 *            of type {@link ClasspathQuery}
	 * @return the found ClasspathNode list
	 * @throws CoreException
	 */
	public static List<ClasspathNode> getChildren(List<Object> arguments, IProgressMonitor pm) throws CoreException {
		if (arguments == null || arguments.size() < 2) {
			return Collections.emptyList();
		}
		ClasspathNodeKind classpathKind = gson.fromJson(gson.toJson(arguments.get(0)), ClasspathNodeKind.class);
		ClasspathQuery params = gson.fromJson(gson.toJson(arguments.get(1)), ClasspathQuery.class);

		BiFunction<ClasspathQuery, IProgressMonitor, List<ClasspathNode>> loader = commands.get(classpathKind);
		if (loader == null) {
			throw new CoreException(new Status(IStatus.ERROR, JavaLanguageServerPlugin.PLUGIN_ID, String.format("Unknown classpath item type: %s", classpathKind)));
		}
		List<ClasspathNode> result = loader.apply(params, pm);
		sortClasspathNode(result);
		return result;
	}

	/**
	 * Get the class path container list.
	 */
	private static List<ClasspathNode> getContainers(ClasspathQuery query, IProgressMonitor pm) {
		IJavaProject javaProject = getJavaProject(query.getProjectUri());

		if (javaProject != null) {
			try {
				IClasspathEntry[] references = javaProject.getRawClasspath();
				return Arrays.stream(references).filter(entry -> entry.getEntryKind() != IClasspathEntry.CPE_SOURCE).map(entry -> {
					try {
						IClasspathContainer container = JavaCore.getClasspathContainer(entry.getPath(), javaProject);
						if (container != null) {
							ClasspathNode containerNode = new ClasspathNode(container.getDescription(), container.getPath().toPortableString(), ClasspathNodeKind.CONTAINER);
							return containerNode;
						}
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

	private static List<ClasspathNode> getJars(ClasspathQuery query, IProgressMonitor pm) {
		IJavaProject javaProject = getJavaProject(query.getProjectUri());

		if (javaProject != null) {
			try {
				IClasspathEntry[] references = javaProject.getRawClasspath();
				IClasspathEntry containerEntry = null;
				for (IClasspathEntry reference : references) {
					if (reference.getPath().equals(Path.fromPortableString(query.getPath()))) {
						containerEntry = reference;
						break;
					}
				}
				if (containerEntry != null) {
					ArrayList<ClasspathNode> children = new ArrayList<>();
					IPackageFragmentRoot[] packageFragmentRoots = javaProject.findPackageFragmentRoots(containerEntry);
					for (IPackageFragmentRoot fragmentRoot : packageFragmentRoots) {
						ClasspathNode node = new ClasspathNode(fragmentRoot.getElementName(), fragmentRoot.getHandleIdentifier(), ClasspathNodeKind.JAR);
						children.add(node);
						if (fragmentRoot instanceof JrtPackageFragmentRoot) {
							node.setModuleName(fragmentRoot.getModuleDescription().getElementName());
						}
					}
					return children;
				}
			} catch (CoreException e) {
				JavaLanguageServerPlugin.logException("Problem load project JAR entries ", e);
			}
		}

		return Collections.emptyList();
	}

	private static List<ClasspathNode> getPackages(ClasspathQuery query, IProgressMonitor pm) {
		IJavaProject javaProject = getJavaProject(query.getProjectUri());

		if (javaProject != null) {
			try {

				IPackageFragmentRoot packageRoot = (IPackageFragmentRoot) JavaCore.create(query.getRootPath());
				if (packageRoot == null) {
					throw new CoreException(new Status(IStatus.ERROR, JavaLanguageServerPlugin.PLUGIN_ID, String.format("No package root found for %s", query.getPath())));
				}
				Object[] result = getPackageFragmentRootContent(packageRoot, pm);
				return convertToClasspathNode(result);
			} catch (CoreException e) {
				JavaLanguageServerPlugin.logException("Problem load project package ", e);
			}
		}
		return Collections.emptyList();
	}

	private static List<ClasspathNode> getClassfiles(ClasspathQuery query, IProgressMonitor pm) {
		IJavaProject javaProject = getJavaProject(query.getProjectUri());
		if (javaProject != null) {
			try {
				IPackageFragmentRoot packageRoot = (IPackageFragmentRoot) JavaCore.create(query.getRootPath());
				if (packageRoot == null) {
					throw new CoreException(new Status(IStatus.ERROR, JavaLanguageServerPlugin.PLUGIN_ID, String.format("No package root found for %s", query.getPath())));
				}
				IPackageFragment packageFragment = packageRoot.getPackageFragment(query.getPath());
				if (packageFragment != null) {
					IClassFile[] classFiles = packageFragment.getAllClassFiles();
					return Arrays.stream(classFiles).filter(classFile -> !classFile.getElementName().contains("$")).map(classFile -> {
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

	private static List<ClasspathNode> getFolderChildren(ClasspathQuery query, IProgressMonitor pm) {
		IJavaProject javaProject = getJavaProject(query.getProjectUri());
		if (javaProject != null) {
			try {
				IPackageFragmentRoot packageRoot = (IPackageFragmentRoot) JavaCore.create(query.getRootPath());
				if (packageRoot == null) {
					throw new CoreException(new Status(IStatus.ERROR, JavaLanguageServerPlugin.PLUGIN_ID, String.format("No package root found for %s", query.getPath())));
				}
				// jar file and folders
				Object[] resources = packageRoot.getNonJavaResources();
				for (Object resource : resources) {
					if (pm.isCanceled()) {
						throw new OperationCanceledException();
					}
					if (resource instanceof JarEntryDirectory) {
						JarEntryDirectory directory = (JarEntryDirectory) resource;
						Object[] children = findJarDirectoryChildren(directory, query.getPath());
						if (children != null) {
							return convertToClasspathNode(children);
						}
					}
				}

			} catch (CoreException e) {
				JavaLanguageServerPlugin.logException("Problem load project classfile list ", e);
			}
		}
		return Collections.emptyList();
	}

	private static Object[] getPackageFragmentRootContent(IPackageFragmentRoot root, IProgressMonitor pm) throws CoreException {
		ArrayList<Object> result = new ArrayList<>();
		for (IJavaElement child : root.getChildren()) {
			IPackageFragment fragment = (IPackageFragment) child;
			if (fragment.hasChildren()) {
				if (fragment.isDefaultPackage()) {
					result.addAll(Arrays.asList(fragment.getChildren()));
				} else {
					result.add(child);
				}
			}
		}
		Object[] nonJavaResources = root.getNonJavaResources();
		Collections.addAll(result, nonJavaResources);
		return result.toArray();
	}

	private static List<ClasspathNode> convertToClasspathNode(Object[] rootContent) throws JavaModelException {
		List<ClasspathNode> result = new ArrayList<>();
		for (Object root : rootContent) {
			if (root instanceof IPackageFragment) {
				IPackageFragment packageFragment = (IPackageFragment) root;
				ClasspathNode entry = new ClasspathNode(((IPackageFragment) root).getElementName(), packageFragment.getPath().toPortableString(), ClasspathNodeKind.PACKAGE);
				result.add(entry);
			} else if (root instanceof IClassFile) {
				IClassFile classFile = (IClassFile) root;
				ClasspathNode entry = new ClasspathNode(classFile.getElementName(), null, ClasspathNodeKind.CLASSFILE);
				entry.setUri(JDTUtils.toUri(classFile));
				result.add(entry);
			} else if (root instanceof JarEntryResource) {
				ClasspathNode jarNode = getJarEntryResource((JarEntryResource) root);
				if (jarNode != null) {
					result.add(jarNode);
				}
			}
		}

		return result;
	}


	private static ClasspathNode getJarEntryResource(JarEntryResource resource) {
		if (resource instanceof JarEntryDirectory) {
			return new ClasspathNode(resource.getName(), resource.getFullPath().toPortableString(), ClasspathNodeKind.Folder);
		} else if (resource instanceof JarEntryFile) {
			ClasspathNode entry = new ClasspathNode(resource.getName(), resource.getFullPath().toPortableString(), ClasspathNodeKind.FILE);
			entry.setUri(JDTUtils.toUri((JarEntryFile) resource));
			return entry;
		}
		return null;
	}

	private static Object[] findJarDirectoryChildren(JarEntryDirectory directory, String path) {
		String directoryPath = directory.getFullPath().toPortableString();
		if (directoryPath.equals(path)) {
			return directory.getChildren();
		}
		if (path.startsWith(directoryPath)) {
			for (IJarEntryResource resource : directory.getChildren()) {
				String childrenPath = resource.getFullPath().toPortableString();
				if (childrenPath.equals(path)) {
					return resource.getChildren();
				}
				if (path.startsWith(childrenPath) && resource instanceof JarEntryDirectory) {
					Object[] result = findJarDirectoryChildren((JarEntryDirectory) resource, path);
					if (result != null) {
						return result;
					}
				}
			}
		}
		return null;
	}

	private static void sortClasspathNode(List<ClasspathNode> classpathNodes) {
		classpathNodes.sort((ClasspathNode n1, ClasspathNode n2) -> {
			if (n1.getKind() != n2.getKind()) {
				return n1.getKind().getValue() - n2.getKind().getValue();
			} else {
				return n1.getName().compareTo(n2.getName());
			}
		});
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
