/*******************************************************************************
 * Copyright (c) 2022 Microsoft Corporation and others.
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
package org.eclipse.jdt.ls.core.internal.corrections.proposals;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Arrays;
import java.util.Collection;

import org.eclipse.buildship.core.internal.CorePlugin;
import org.eclipse.buildship.core.internal.preferences.PersistentModel;
import org.eclipse.buildship.core.internal.util.gradle.GradleVersion;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.Path;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IModuleDescription;
import org.eclipse.jdt.core.IPackageFragmentRoot;
import org.eclipse.jdt.internal.core.JarPackageFragmentRoot;
import org.eclipse.jdt.internal.ui.text.correction.IProblemLocationCore;
import org.eclipse.jdt.ls.core.internal.ProjectUtils;
import org.eclipse.jdt.ls.core.internal.commands.ProjectCommand;
import org.eclipse.jdt.ls.core.internal.commands.ProjectCommand.ClasspathResult;
import org.eclipse.jdt.ls.core.internal.corrections.CorrectionMessages;
import org.eclipse.jdt.ls.core.internal.corrections.IInvocationContext;
import org.eclipse.jdt.ls.core.internal.managers.GradleUtils;
import org.eclipse.jdt.ls.core.internal.text.correction.CUCorrectionCommandProposal;
import org.eclipse.lsp4j.CodeActionKind;

public class GradleCompatibilityProcessor {
	public static void getGradleCompatibilityProposals(IInvocationContext context, IProblemLocationCore problem, Collection<ChangeCorrectionProposal> proposals) {
		IJavaProject javaProject = context.getCompilationUnit().getJavaProject();
		if (javaProject == null) {
			return;
		}
		IProject project = javaProject.getProject();
		if (!ProjectUtils.isGradleProject(project)) {
			return;
		}
		PersistentModel model = CorePlugin.modelPersistence().loadModel(project);
		if (!model.isPresent()) {
			return;
		}
		GradleVersion gradleVersion = model.getGradleVersion();
		if (gradleVersion != null && gradleVersion.compareTo(GradleVersion.version(GradleUtils.JPMS_SUPPORTED_VERSION)) < 0) {
			IResource resource = javaProject.getResource();
			if (resource == null) {
				return;
			}
			URI uri = resource.getLocationURI();
			if (uri == null) {
				return;
			}
			try {
				ClasspathResult result = ProjectCommand.getClasspathsFromJavaProject(javaProject, new ProjectCommand.ClasspathOptions());
				IModuleDescription moduleDescription = javaProject.getModuleDescription();
				if (moduleDescription == null) {
					addProposalForNonModulerProject(result, context, uri, proposals);
				} else {
					addProposalForModulerProject(javaProject, result, context, uri, proposals);
				}
			} catch (CoreException | URISyntaxException e) {
				return;
			}
		}
	}

	/**
	 * Add proposal for a non-modular project. For a project doesn't have a module
	 * description file (module-info.java), there should be nothing in the module
	 * path. See: https://github.com/gradle/gradle/issues/16922
	 * 
	 * @param result
	 *                      the classpath result
	 * @param context
	 *                      the invocation context
	 * @param uri
	 *                      the project uri
	 * @param proposals
	 *                      the current proposals
	 */
	private static void addProposalForNonModulerProject(ClasspathResult result, IInvocationContext context, URI uri, Collection<ChangeCorrectionProposal> proposals) {
		if (result.modulepaths.length > 0) {
			addProposal(context, uri, proposals);
		}
	}

	/**
	 * Add proposal for a modular project. For a project has a module description
	 * file (module-info.java), we should check that all the dependencies in
	 * classpath don't contain module description (either description or automatic
	 * description, the supported inferred modules in Gradle) See:
	 * https://docs.gradle.org/current/userguide/java_library_plugin.html#sec:java_library_modular)
	 * 
	 * @param javaProject
	 *                        the java project
	 * @param result
	 *                        the classpath result
	 * @param context
	 *                        the invocation context
	 * @param uri
	 *                        the project uri
	 * @param proposals
	 *                        the current proposals
	 */
	private static void addProposalForModulerProject(IJavaProject javaProject, ClasspathResult result, IInvocationContext context, URI uri, Collection<ChangeCorrectionProposal> proposals) {
		for (String classpath : result.classpaths) {
			try {
				IPackageFragmentRoot packageFragmentRoot = javaProject.findPackageFragmentRoot(new Path(classpath));
				if (packageFragmentRoot instanceof JarPackageFragmentRoot) {
					// try to get module description
					IModuleDescription jarModuleDescription = ((JarPackageFragmentRoot) packageFragmentRoot).getModuleDescription();
					if (jarModuleDescription == null) {
						// fall back to get automatic module description
						jarModuleDescription = ((JarPackageFragmentRoot) packageFragmentRoot).getAutomaticModuleDescription();
					}
					if (jarModuleDescription != null) {
						addProposal(context, uri, proposals);
						break;
					}
				}
			} catch (CoreException e) {
				continue;
			}
		}
	}

	private static void addProposal(IInvocationContext context, URI uri, Collection<ChangeCorrectionProposal> proposals) {
		proposals.add(new CUCorrectionCommandProposal(CorrectionMessages.NotAccessibleType_upgrade_Gradle_label, CodeActionKind.QuickFix, context.getCompilationUnit(), IProposalRelevance.CONFIGURE_BUILD_PATH, "java.project.upgradeGradle",
				Arrays.asList(uri.toString(), GradleUtils.JPMS_SUPPORTED_VERSION)));
	}
}
