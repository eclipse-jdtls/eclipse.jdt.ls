/*******************************************************************************
 * Copyright (c) 2022 Microsoft Corporation and others.
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
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import org.apache.maven.shared.utils.StringUtils;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.jdt.core.IClasspathAttribute;
import org.eclipse.jdt.core.IClasspathEntry;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IPackageFragment;
import org.eclipse.jdt.core.IPackageFragmentRoot;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.formatter.CodeFormatter;
import org.eclipse.jdt.internal.core.JavaModelStatus;
import org.eclipse.jdt.internal.core.manipulation.StubUtility;
import org.eclipse.jdt.internal.corext.util.CodeFormatterUtil;
import org.eclipse.jdt.internal.corext.util.JavaConventionsUtil;
import org.eclipse.jdt.internal.corext.util.JavaModelUtil;
import org.eclipse.jdt.ls.core.internal.JavaLanguageServerPlugin;
import org.eclipse.jdt.ls.core.internal.ProjectUtils;
import org.eclipse.lsp4j.MessageType;

/**
 * The handler to create module-info.java
 * Also see: org.eclipse.jdt.internal.ui.actions.CreateModuleInfoAction
 *           org.eclipse.jdt.internal.ui.wizards.NewModuleInfoWizard
 */
public class CreateModuleInfoHandler {

	/**
	 * Create the module-info.java for given project.
	 * @param projectUri uri of the project.
	 * @param monitor progress monitor.
	 * @return the created module-info.java file uri.
	 */
	public static String createModuleInfo(String projectUri, IProgressMonitor monitor) {
		IProject project = ProjectUtils.getProjectFromUri(projectUri);
		if (!ProjectUtils.isJavaProject(project)) {
			JavaLanguageServerPlugin.getInstance().getClientConnection().showNotificationMessage(MessageType.Error, "The selected project is not a valid Java project.");
			return null;
		}

		IJavaProject javaProject = JavaCore.create(project);
		if (!JavaModelUtil.is9OrHigher(javaProject)) {
			JavaLanguageServerPlugin.getInstance().getClientConnection().showNotificationMessage(MessageType.Error, "The project source compliance must be 9 or higher to create module-info.java.");
			return null;
		}

		try {
			IPackageFragmentRoot[] packageFragmentRoots = javaProject.getPackageFragmentRoots();
			List<IPackageFragmentRoot> packageFragmentRootsAsList = new ArrayList<>(Arrays.asList(packageFragmentRoots));
			for (IPackageFragmentRoot packageFragmentRoot : packageFragmentRoots) {
				IResource res = packageFragmentRoot.getCorrespondingResource();
				if (res == null || res.getType() != IResource.FOLDER || packageFragmentRoot.getKind() != IPackageFragmentRoot.K_SOURCE) {
					packageFragmentRootsAsList.remove(packageFragmentRoot);
				}
			}

			packageFragmentRoots = packageFragmentRootsAsList.toArray(new IPackageFragmentRoot[packageFragmentRootsAsList.size()]);
			if (packageFragmentRoots.length == 0) {
				JavaLanguageServerPlugin.getInstance().getClientConnection().showNotificationMessage(MessageType.Error, "No source folder exists in the project.");
				return null;
			}

			IPackageFragmentRoot targetPkgFragmentRoot = null;
			for (IPackageFragmentRoot packageFragmentRoot : packageFragmentRoots) {
				if (packageFragmentRoot.getPackageFragment("").getCompilationUnit(JavaModelUtil.MODULE_INFO_JAVA).exists()) {
					JavaLanguageServerPlugin.getInstance().getClientConnection().showNotificationMessage(MessageType.Error,
						"The module-info.java file already exists in the source folder \"" + packageFragmentRoot.getElementName() + "\"");
					return null;
				}
				if (targetPkgFragmentRoot == null) {
					targetPkgFragmentRoot = packageFragmentRoot;
				}
			}

			if (targetPkgFragmentRoot == null) {
				return null;
			}

			String moduleInfoUri = createModuleInfoJava(javaProject, targetPkgFragmentRoot, packageFragmentRoots, monitor);
			if (moduleInfoUri != null) {
				new Job("Update project: " + javaProject.getElementName()) {
					@Override
					protected IStatus run(IProgressMonitor monitor) {
						try {
							convertClasspathToModulePath(javaProject, monitor);
						} catch (CoreException e) {
							return e.getStatus();
						}
						return Status.OK_STATUS;
					}
				}.schedule();
			}
			return moduleInfoUri;
		} catch (CoreException e) {
			JavaLanguageServerPlugin.log(e);
		}

		return null;
	}

	private static String createModuleInfoJava(IJavaProject javaProject, IPackageFragmentRoot targetPkgFragmentRoot,
			IPackageFragmentRoot[] packageFragmentRoots, IProgressMonitor monitor) throws CoreException {
		String fileContent = getModuleInfoFileContent(javaProject, packageFragmentRoots);
		IPackageFragment defaultPkg = targetPkgFragmentRoot.getPackageFragment("");
		ICompilationUnit moduleInfo = defaultPkg.createCompilationUnit(JavaModelUtil.MODULE_INFO_JAVA, fileContent, true, monitor);
		return moduleInfo.getResource().getLocationURI().toString();
	}

	private static String getModuleInfoFileContent(IJavaProject javaProject, IPackageFragmentRoot[] packageFragmentRoots) throws CoreException {
		HashSet<String> exportedPackages = new HashSet<>();
		for (IPackageFragmentRoot packageFragmentRoot : packageFragmentRoots) {
			for (IJavaElement child : packageFragmentRoot.getChildren()) {
				if (child instanceof IPackageFragment pkgFragment) {
					if (!pkgFragment.isDefaultPackage() && pkgFragment.getCompilationUnits().length != 0) {
						exportedPackages.add(pkgFragment.getElementName());
					}
				}
			}
		}

		String[] requiredModules= JavaCore.getReferencedModules(javaProject);
		String moduleName = getModuleName(javaProject);
		String lineDelimiter = StubUtility.getLineDelimiterUsed(javaProject);
		StringBuilder fileContentBuilder = new StringBuilder();
		fileContentBuilder.append("module ");
		fileContentBuilder.append(moduleName);
		fileContentBuilder.append(" {");
		fileContentBuilder.append(lineDelimiter);

		for (String exportedPkg : exportedPackages) {
			fileContentBuilder.append("\t");
			fileContentBuilder.append("exports ");
			fileContentBuilder.append(exportedPkg);
			fileContentBuilder.append(";");
			fileContentBuilder.append(lineDelimiter);
		}

		for (String requiredModule : requiredModules) {
			fileContentBuilder.append("\t");
			fileContentBuilder.append("requires ");
			fileContentBuilder.append(requiredModule);
			fileContentBuilder.append(";");
			fileContentBuilder.append(lineDelimiter);
		}

		fileContentBuilder.append("}");
		fileContentBuilder.append(lineDelimiter);
		String fileContent = fileContentBuilder.toString();

		Map<String, String> options = javaProject.getOptions(true);
		String formattedContent = CodeFormatterUtil.format(CodeFormatter.K_MODULE_INFO, fileContent, 0, lineDelimiter, options);

		return formattedContent;
	}

	private static String getModuleName(IJavaProject javaProject) {
		String moduleName = convertToModuleName(javaProject.getElementName());
		IStatus status = JavaConventionsUtil.validateModuleName(moduleName, javaProject);
		if (status == JavaModelStatus.VERIFIED_OK) {
			return moduleName;
		}
		return "module.name";
	}

	/**
	 * public only for testing purpose
	 */
	public static String convertToModuleName(String name) {
		// replace all invalid chars to '.'
		Pattern invalidChars = Pattern.compile("[^A-Za-z0-9]");
		name = invalidChars.matcher(name).replaceAll(".");

		// replace all continuous dots to single dot
		Pattern continuousDots = Pattern.compile("\\.{2,}");
		name = continuousDots.matcher(name).replaceAll(".");

		// remove leading and tailing '.'
		name = StringUtils.stripStart(name, ".");
		name = StringUtils.stripEnd(name, ".");

		return name;
	}

	private static void convertClasspathToModulePath(IJavaProject javaProject, IProgressMonitor monitor) throws JavaModelException {
		boolean changed = false;
		IClasspathEntry[] rawClasspath = javaProject.getRawClasspath();
		for (int i = 0; i < rawClasspath.length; i++) {
			IClasspathEntry entry = rawClasspath[i];
			switch (entry.getEntryKind()) {
				case IClasspathEntry.CPE_CONTAINER:
				case IClasspathEntry.CPE_LIBRARY:
				case IClasspathEntry.CPE_PROJECT:
					IClasspathAttribute[] newAttributes = addModuleAttributeIfNeeded(entry.getExtraAttributes());
					if (newAttributes != null) {
						rawClasspath[i] = addAttributes(entry, newAttributes);
						changed= true;
					}
					break;
				default:
					// other kinds are not handled
			}
		}
		if (changed) {
			javaProject.setRawClasspath(rawClasspath, monitor);
		}
	}

	private static IClasspathAttribute[] addModuleAttributeIfNeeded(IClasspathAttribute[] extraAttributes) {
		for (int j= 0; j < extraAttributes.length; j++) {
			IClasspathAttribute classpathAttribute = extraAttributes[j];
			if (IClasspathAttribute.MODULE.equals(classpathAttribute.getName())) {
				if ("true".equals(classpathAttribute.getValue())) {
					return null; // no change required
				}
				extraAttributes[j] = JavaCore.newClasspathAttribute(IClasspathAttribute.MODULE, "true");
				return extraAttributes;
			}
		}
		extraAttributes= Arrays.copyOf(extraAttributes, extraAttributes.length+1);
		extraAttributes[extraAttributes.length-1] = JavaCore.newClasspathAttribute(IClasspathAttribute.MODULE, "true");
		return extraAttributes;
	}

	private static IClasspathEntry addAttributes(IClasspathEntry entry, IClasspathAttribute[] extraAttributes) {
		switch (entry.getEntryKind()) {
			case IClasspathEntry.CPE_CONTAINER:
				return JavaCore.newContainerEntry(entry.getPath(), entry.getAccessRules(), extraAttributes, entry.isExported());
			case IClasspathEntry.CPE_LIBRARY:
				return JavaCore.newLibraryEntry(entry.getPath(), entry.getSourceAttachmentPath(),
						entry.getSourceAttachmentRootPath(), entry.getAccessRules(), extraAttributes, entry.isExported());
			case IClasspathEntry.CPE_PROJECT:
				return JavaCore.newProjectEntry(entry.getPath(), entry.getAccessRules(), entry.combineAccessRules(),
						extraAttributes, entry.isExported());
			default:
				return entry; // other kinds are not handled
		}
	}
}
