/*******************************************************************************
 * Copyright (c) 2019 Microsoft Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Microsoft Corporation - initial API and implementation
 *******************************************************************************/

package org.eclipse.jdt.ls.core.internal.handlers;

import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.eclipse.core.resources.IContainer;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.OperationCanceledException;
import org.eclipse.core.runtime.SubMonitor;
import org.eclipse.jdt.core.IClasspathEntry;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IPackageFragment;
import org.eclipse.jdt.core.IPackageFragmentRoot;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.ls.core.internal.ChangeUtil;
import org.eclipse.jdt.ls.core.internal.JDTUtils;
import org.eclipse.jdt.ls.core.internal.JavaLanguageServerPlugin;
import org.eclipse.jdt.ls.core.internal.ProjectUtils;
import org.eclipse.jdt.ls.core.internal.ResourceUtils;
import org.eclipse.jdt.ls.core.internal.corext.refactoring.reorg.IConfirmQuery;
import org.eclipse.jdt.ls.core.internal.corext.refactoring.reorg.IReorgDestination;
import org.eclipse.jdt.ls.core.internal.corext.refactoring.reorg.IReorgPolicy.IMovePolicy;
import org.eclipse.jdt.ls.core.internal.corext.refactoring.reorg.IReorgQueries;
import org.eclipse.jdt.ls.core.internal.corext.refactoring.reorg.JavaMoveProcessor;
import org.eclipse.jdt.ls.core.internal.corext.refactoring.reorg.ReorgDestinationFactory;
import org.eclipse.jdt.ls.core.internal.corext.refactoring.reorg.ReorgPolicyFactory;
import org.eclipse.jdt.ls.core.internal.corext.refactoring.reorg.ReorgUtils;
import org.eclipse.jdt.ls.core.internal.handlers.GetRefactorEditHandler.RefactorWorkspaceEdit;
import org.eclipse.jdt.ls.core.internal.managers.ProjectsManager;
import org.eclipse.jdt.ls.core.internal.preferences.PreferenceManager;
import org.eclipse.lsp4j.WorkspaceEdit;
import org.eclipse.ltk.core.refactoring.Change;
import org.eclipse.ltk.core.refactoring.CheckConditionsOperation;
import org.eclipse.ltk.core.refactoring.CreateChangeOperation;
import org.eclipse.ltk.core.refactoring.Refactoring;
import org.eclipse.ltk.core.refactoring.RefactoringStatus;
import org.eclipse.ltk.core.refactoring.participants.MoveRefactoring;

public class MoveHandler {
	public static final String DEFAULT_PACKAGE_DISPLAYNAME = "(default package)";

	public static PackageDestinationsResponse getPackageDestinations(String[] documentUris) {
		if (documentUris == null) {
			documentUris = new String[0];
		}

		Set<IJavaProject> targetProjects = new LinkedHashSet<>();
		Set<IPackageFragment> selectedPackages = new LinkedHashSet<>();
		for (String uri : documentUris) {
			final ICompilationUnit unit = JDTUtils.resolveCompilationUnit(uri);
			final IPath filePath = ResourceUtils.filePathFromURI(uri);
			if (unit == null || filePath == null) {
				continue;
			}

			selectedPackages.add((IPackageFragment) unit.getParent());
			IJavaProject currentProject = unit.getJavaProject();
			if (ProjectsManager.DEFAULT_PROJECT_NAME.equals(currentProject.getProject().getName())) {
				IProject belongedProject = findNearestProject(filePath);
				if (belongedProject != null) {
					targetProjects.add(JavaCore.create(belongedProject));
				}
			} else {
				targetProjects.add(currentProject);
			}
		}

		PreferenceManager manager = JavaLanguageServerPlugin.getPreferencesManager();
		Collection<IPath> workspaceRoots = manager.getPreferences().getRootPaths();
		for (IPath workspaceRoot : workspaceRoots) {
			ProjectUtils.getVisibleProjects(workspaceRoot).forEach(project -> {
				if (ProjectUtils.isJavaProject(project)) {
					targetProjects.add(JavaCore.create(project));
				}
			});

			String invisibleProjectName = ProjectUtils.getWorkspaceInvisibleProjectName(workspaceRoot);
			IProject invisibleProject = ResourcesPlugin.getWorkspace().getRoot().getProject(invisibleProjectName);
			if (invisibleProject.exists() && ProjectUtils.isJavaProject(invisibleProject)) {
				targetProjects.add(JavaCore.create(invisibleProject));
			}
		}

		Set<PackageNode> packageNodes = new LinkedHashSet<>();
		try {
			for (IJavaProject project : targetProjects) {
				for (IClasspathEntry entry : project.getRawClasspath()) {
					if (entry.getEntryKind() == IClasspathEntry.CPE_SOURCE) {
						IPackageFragmentRoot[] roots = project.findPackageFragmentRoots(entry);
						for (IPackageFragmentRoot root : roots) {
							if (root.isArchive() || root.isExternal()) {
								continue;
							}

							IJavaElement[] fragments = root.getChildren();
							for (IJavaElement fragment : fragments) {
								if (fragment instanceof IPackageFragment) {
									PackageNode packageNode = PackageNode.createPackageNode((IPackageFragment) fragment);
									packageNode.setParentOfSelectedFile(selectedPackages.contains(fragment));
									packageNodes.add(packageNode);
								}
							}
						}
					}
				}
			}
		} catch (JavaModelException e) {
			JavaLanguageServerPlugin.logException("Failed to list Java packages for source paths.", e);
		}

		return new PackageDestinationsResponse(packageNodes.toArray(new PackageNode[0]));
	}

	public static IProject findNearestProject(IPath filePath) {
		List<IProject> projects = Stream.of(ProjectUtils.getAllProjects()).filter(ProjectUtils::isJavaProject).sorted(new Comparator<IProject>() {
			@Override
			public int compare(IProject p1, IProject p2) {
				return p2.getLocation().toOSString().length() - p1.getLocation().toOSString().length();
			}
		}).collect(Collectors.toList());

		for (IProject project : projects) {
			if (project.getLocation().isPrefixOf(filePath)) {
				return project;
			}
		}

		return null;
	}

	public static RefactorWorkspaceEdit moveFile(MoveFileParams moveFileParams, IProgressMonitor monitor) {
		URI targetUri = JDTUtils.toURI(moveFileParams.targetUri);
		if (targetUri == null) {
			return new RefactorWorkspaceEdit("Failed to move the files because of illegal uri '" + moveFileParams.targetUri + "'.");
		}

		List<IJavaElement> elements = new ArrayList<>();
		for (String uri : moveFileParams.documentUris) {
			ICompilationUnit unit = JDTUtils.resolveCompilationUnit(uri);
			if (unit == null) {
				continue;
			}

			elements.add(unit);
		}

		SubMonitor submonitor = SubMonitor.convert(monitor, "Moving File...", 100);
		try {
			IResource[] resources = ReorgUtils.getResources(elements);
			IJavaElement[] javaElements = ReorgUtils.getJavaElements(elements);
			IContainer[] targetContainers = ResourcesPlugin.getWorkspace().getRoot().findContainersForLocationURI(targetUri);
			if (targetContainers == null || targetContainers.length == 0) {
				return new RefactorWorkspaceEdit("Failed to move the files because cannot find the target folder '" + moveFileParams.targetUri + "' in the workspace.");
			} else if ((resources == null || resources.length == 0) && (javaElements == null || javaElements.length == 0)) {
				return new RefactorWorkspaceEdit("Failed to move the files because cannot find any resources or Java elements associated with the files.");
			}

			// For multi-module scenario, findContainersForLocationURI API may return a container array, need put the result from the nearest project in front.
			Arrays.sort(targetContainers, (Comparator<IContainer>) (IContainer a, IContainer b) -> {
				return a.getFullPath().toPortableString().length() - b.getFullPath().toPortableString().length();
			});
			IJavaElement targetElement = null;
			for (IContainer container : targetContainers) {
				targetElement = JavaCore.create(container);
				if (targetElement instanceof IPackageFragmentRoot) {
					targetElement = ((IPackageFragmentRoot) targetElement).getPackageFragment("");
				}

				if (targetElement != null) {
					break;
				}
			}

			if (targetElement == null) {
				JavaLanguageServerPlugin.logError("Failed to move the files because cannot find the package associated with the path '" + moveFileParams.targetUri + "'.");
				return new RefactorWorkspaceEdit("Failed to move the files because cannot find the package associated with the path '" + moveFileParams.targetUri + "'.");
			}

			IReorgDestination packageDestination = ReorgDestinationFactory.createDestination(targetElement);
			WorkspaceEdit edit = move(resources, javaElements, packageDestination, moveFileParams.updateReferences, submonitor);
			if (edit == null) {
				return new RefactorWorkspaceEdit("Cannot enable move operation.");
			}

			return new RefactorWorkspaceEdit(edit);
		} catch (CoreException e) {
			JavaLanguageServerPlugin.logException("Failed to move the files.", e);
			return new RefactorWorkspaceEdit("Failed to move the files because of " + e.toString());
		} finally {
			submonitor.done();
		}
	}

	public static WorkspaceEdit move(IResource[] resources, IJavaElement[] javaElements, IReorgDestination destination, boolean updateReferences, IProgressMonitor monitor) throws JavaModelException, CoreException {
		IMovePolicy policy = ReorgPolicyFactory.createMovePolicy(resources, javaElements);
		if (policy.canEnable()) {
			JavaMoveProcessor processor = new JavaMoveProcessor(policy);
			Refactoring refactoring = new MoveRefactoring(processor);
			processor.setDestination(destination);
			processor.setUpdateReferences(updateReferences);
			processor.setReorgQueries(new IReorgQueries() {
				@Override
				public IConfirmQuery createSkipQuery(String queryTitle, int queryID) {
					return yesQuery;
				}

				@Override
				public IConfirmQuery createYesNoQuery(String queryTitle, boolean allowCancel, int queryID) {
					return yesQuery;
				}

				@Override
				public IConfirmQuery createYesYesToAllNoNoToAllQuery(String queryTitle, boolean allowCancel, int queryID) {
					return yesQuery;
				}

				private final IConfirmQuery yesQuery = new IConfirmQuery() {
					@Override
					public boolean confirm(String question) throws OperationCanceledException {
						return true;
					}

					@Override
					public boolean confirm(String question, Object[] elements) throws OperationCanceledException {
						return true;
					}
				};
			});

			CheckConditionsOperation check = new CheckConditionsOperation(refactoring, CheckConditionsOperation.ALL_CONDITIONS);
			final CreateChangeOperation create = new CreateChangeOperation(check, RefactoringStatus.FATAL);
			create.run(monitor);
			if (check.getStatus().getSeverity() >= RefactoringStatus.FATAL) {
				JavaLanguageServerPlugin.logError("Failed to execute the 'move' refactoring.");
				JavaLanguageServerPlugin.logError(check.getStatus().toString());
			}

			Change change = create.getChange();
			return ChangeUtil.convertToWorkspaceEdit(change);
		}

		return null;
	}

	public static class PackageDestinationsResponse {
		public PackageNode[] packageNodes;

		public PackageDestinationsResponse(PackageNode[] packageNodes) {
			this.packageNodes = packageNodes;
		}
	}

	public static class PackageNode {
		public String displayName;
		public String uri;
		public String path;
		public String project;
		public boolean isDefaultPackage;
		public boolean isParentOfSelectedFile = false;

		public PackageNode(String displayName, String uri, String path, String project, boolean isDefaultPackage) {
			this.displayName = displayName;
			this.uri = uri;
			this.path = path;
			this.project = project;
			this.isDefaultPackage = isDefaultPackage;
		}

		public void setParentOfSelectedFile(boolean isParentOfSelectedFile) {
			this.isParentOfSelectedFile = isParentOfSelectedFile;
		}

		public static PackageNode createPackageNode(IPackageFragment fragment) {
			if (fragment == null) {
				return null;
			}

			String projectName = fragment.getJavaProject().getProject().getName();
			String uri = null;
			if (fragment.getResource() != null) {
				uri = JDTUtils.getFileURI(fragment.getResource());
			}

			if (fragment.isDefaultPackage()) {
				return new PackageNode(DEFAULT_PACKAGE_DISPLAYNAME, uri, fragment.getPath().toPortableString(), projectName, true);
			}

			return new PackageNode(fragment.getElementName(), uri, fragment.getPath().toPortableString(), projectName, false);
		}
	}

	public static class MoveFileParams {
		String[] documentUris;
		String targetUri;
		boolean updateReferences;

		public MoveFileParams(String[] documentUris, String targetUri, boolean updateReferences) {
			this.documentUris = documentUris;
			this.targetUri = targetUri;
			this.updateReferences = updateReferences;
		}
	}
}
