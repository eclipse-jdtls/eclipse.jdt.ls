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

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import org.apache.commons.lang3.StringUtils;
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
import org.eclipse.jdt.internal.core.JarPackageFragmentRoot;
import org.eclipse.jdt.internal.core.JavaModelManager;
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
		commands.put(ClasspathNodeKind.FILE, ClasspathCommand::getClassfiles);
		commands.put(ClasspathNodeKind.Folder, ClasspathCommand::getFolderChildren);
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
	public static List<ClasspathNode> getChildren(List<Object> arguments, IProgressMonitor pm) throws CoreException {
		if (arguments == null || arguments.size() < 2) {
			return Collections.emptyList();
		}
		ClasspathNodeKind classpathKind = gson.fromJson(gson.toJson(arguments.get(0)), ClasspathNodeKind.class);
		ClasspathQuery params = gson.fromJson(gson.toJson(arguments.get(1)), ClasspathQuery.class);

		BiFunction<ClasspathQuery, IProgressMonitor, List<ClasspathNode>> loader = commands.get(classpathKind);
		if (loader == null) {
			throw new CoreException(new Status(IStatus.ERROR, JavaLanguageServerPlugin.PLUGIN_ID,
					String.format("Unknown classpath item type: %s", classpathKind)));
		}
		return loader.apply(params, pm);
	}

	/**
	 * Get the source content from the .class file URI
	 *
	 * @param arguments
	 *            the .class file URI
	 * @return source content of the .class file. If the file content is not
	 *         available, return the empty string.
	 */
	public static String getSource(List<Object> arguments, IProgressMonitor pm) {
		ClasspathQuery query = gson.fromJson(gson.toJson(arguments.get(0)), ClasspathQuery.class);

		if (query.getRootPath() == null) {
			IClassFile classfile = JDTUtils.resolveClassFile(query.getPath());
			try {
				String content = classfile.getSource();
				return StringUtils.isBlank(content) ? "" : content;
			} catch (CoreException e) {
				JavaLanguageServerPlugin.logException("Failed to getSource from " + classfile.toString(), e);
			}
		} else {
			return getJarFileContent(query, pm);
		}
		return "";
	}

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
						children.add(new ClasspathNode(fragmentRoot.getElementName(), fragmentRoot.getPath().toPortableString(), ClasspathNodeKind.JAR));
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
				IPackageFragmentRoot packageRoot = javaProject.findPackageFragmentRoot(Path.fromPortableString(query.getPath()));


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
				IPackageFragmentRoot packageRoot = javaProject.findPackageFragmentRoot(Path.fromPortableString(query.getRootPath()));
				if (packageRoot == null) {
					throw new CoreException(new Status(IStatus.ERROR, JavaLanguageServerPlugin.PLUGIN_ID, String.format("No package root found for %s", query.getPath())));
				}
				IPackageFragment packageFragment = packageRoot.getPackageFragment(query.getPath());
				if (packageFragment != null) {
					IClassFile[] classFiles = packageFragment.getAllClassFiles();
					return Arrays.stream(classFiles)
							.filter(classFile -> !classFile.getElementName().contains("$"))
							.map(classFile -> {
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
				IPackageFragmentRoot packageRoot = javaProject.findPackageFragmentRoot(Path.fromPortableString(query.getRootPath()));
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
			if (((IPackageFragment) child).hasChildren()) {
				result.add(child);
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
			}

			if (root instanceof IClassFile) {
				IClassFile classFile = (IClassFile) root;
				ClasspathNode entry = new ClasspathNode(classFile.getElementName(), classFile.findPrimaryType().getFullyQualifiedName(), ClasspathNodeKind.CLASSFILE);
				entry.setUri(JDTUtils.toUri(classFile));
				result.add(entry);
			}

			if (root instanceof JarEntryResource) {
				result.add(getJarEntryResource((JarEntryResource) root));
			}
		}
		result.sort((ClasspathNode n1, ClasspathNode n2) -> n1.getKind().getValue() - n2.getKind().getValue());
		return result;
	}

	private static ClasspathNode getJarEntryResource(JarEntryResource resource) {
		ClasspathNode entry = new ClasspathNode();
		if (resource instanceof JarEntryDirectory) {
			return new ClasspathNode(resource.getName(), resource.getFullPath().toPortableString(), ClasspathNodeKind.Folder);
		}
		else if (resource instanceof JarEntryFile) {
			return new ClasspathNode(resource.getName(), resource.getFullPath().toPortableString(), ClasspathNodeKind.FILE);
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

	private static String getJarFileContent(ClasspathQuery query, IProgressMonitor pm) {
		IJavaProject javaProject = getJavaProject(query.getProjectUri());
		if (javaProject != null) {
			try {
				IPackageFragmentRoot packageRoot = javaProject.findPackageFragmentRoot(Path.fromPortableString(query.getRootPath()));
				if (packageRoot == null) {
					throw new CoreException(new Status(IStatus.ERROR, JavaLanguageServerPlugin.PLUGIN_ID, String.format("No package root found for %s", query.getPath())));
				}
				if (packageRoot instanceof JarPackageFragmentRoot) {
					JarPackageFragmentRoot jarPackageFragmentRoot = (JarPackageFragmentRoot) packageRoot;
					ZipFile jar = null;
					try {
						jar = jarPackageFragmentRoot.getJar();
						ZipEntry entry = jar.getEntry(query.getPath().substring(1));
						if (entry != null) {
							try (InputStream stream = jar.getInputStream(entry)) {
								return convertStreamToString(stream);
							} catch (IOException e) {
								JavaLanguageServerPlugin.logException("Can't read file content: " + entry.getName(), e);
							}
						}
					} finally {
						if (jar != null) {
							JavaModelManager.getJavaModelManager().closeZipFile(jar);
						}
					}
				}

			} catch (CoreException e) {
				JavaLanguageServerPlugin.logException("Problem load project classfile list ", e);
			}
		}
		return "";
	}

	private static String convertStreamToString(java.io.InputStream is) {
		java.util.Scanner s = new java.util.Scanner(is, "UTF-8").useDelimiter("\\A");
		return s.hasNext() ? s.next() : "";
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
