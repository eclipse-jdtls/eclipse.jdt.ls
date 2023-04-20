/*******************************************************************************
 * Copyright (c) 2019 Microsoft Corporation and others.
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

package org.eclipse.jdt.ls.core.internal.handlers;

import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.commons.lang3.StringUtils;
import org.eclipse.core.resources.IContainer;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.core.runtime.OperationCanceledException;
import org.eclipse.core.runtime.SubMonitor;
import org.eclipse.jdt.core.IClasspathEntry;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IMember;
import org.eclipse.jdt.core.IMethod;
import org.eclipse.jdt.core.IPackageFragment;
import org.eclipse.jdt.core.IPackageFragmentRoot;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.AbstractTypeDeclaration;
import org.eclipse.jdt.core.dom.BodyDeclaration;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.FieldDeclaration;
import org.eclipse.jdt.core.dom.IMethodBinding;
import org.eclipse.jdt.core.dom.IVariableBinding;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.VariableDeclarationFragment;
import org.eclipse.jdt.internal.corext.codemanipulation.CodeGenerationSettings;
import org.eclipse.jdt.internal.corext.refactoring.RefactoringAvailabilityTesterCore;
import org.eclipse.jdt.internal.corext.refactoring.reorg.IConfirmQuery;
import org.eclipse.jdt.internal.corext.refactoring.reorg.IReorgDestination;
import org.eclipse.jdt.internal.corext.refactoring.reorg.IReorgPolicy.IMovePolicy;
import org.eclipse.jdt.internal.corext.refactoring.reorg.IReorgQueries;
import org.eclipse.jdt.internal.corext.refactoring.structure.MoveInnerToTopRefactoring;
import org.eclipse.jdt.internal.corext.refactoring.structure.MoveInstanceMethodProcessor;
import org.eclipse.jdt.internal.corext.refactoring.structure.MoveStaticMembersProcessor;
import org.eclipse.jdt.internal.corext.util.JdtFlags;
import org.eclipse.jdt.ls.core.internal.ChangeUtil;
import org.eclipse.jdt.ls.core.internal.JDTUtils;
import org.eclipse.jdt.ls.core.internal.JSONUtility;
import org.eclipse.jdt.ls.core.internal.JavaLanguageServerPlugin;
import org.eclipse.jdt.ls.core.internal.ProjectUtils;
import org.eclipse.jdt.ls.core.internal.ResourceUtils;
import org.eclipse.jdt.ls.core.internal.corext.refactoring.reorg.JavaMoveProcessor;
import org.eclipse.jdt.ls.core.internal.corext.refactoring.reorg.ReorgDestinationFactory;
import org.eclipse.jdt.ls.core.internal.corext.refactoring.reorg.ReorgPolicyFactory;
import org.eclipse.jdt.ls.core.internal.corext.refactoring.reorg.ReorgUtils;
import org.eclipse.jdt.ls.core.internal.corrections.DiagnosticsHelper;
import org.eclipse.jdt.ls.core.internal.corrections.InnovationContext;
import org.eclipse.jdt.ls.core.internal.handlers.GetRefactorEditHandler.RefactorWorkspaceEdit;
import org.eclipse.jdt.ls.core.internal.handlers.JdtDomModels.LspVariableBinding;
import org.eclipse.jdt.ls.core.internal.managers.ProjectsManager;
import org.eclipse.jdt.ls.core.internal.preferences.PreferenceManager;
import org.eclipse.lsp4j.CodeActionParams;
import org.eclipse.lsp4j.SymbolInformation;
import org.eclipse.lsp4j.WorkspaceEdit;
import org.eclipse.ltk.core.refactoring.Change;
import org.eclipse.ltk.core.refactoring.CheckConditionsOperation;
import org.eclipse.ltk.core.refactoring.CreateChangeOperation;
import org.eclipse.ltk.core.refactoring.Refactoring;
import org.eclipse.ltk.core.refactoring.RefactoringStatus;
import org.eclipse.ltk.core.refactoring.participants.MoveRefactoring;

import com.google.gson.Gson;

public class MoveHandler {
	public static final String DEFAULT_PACKAGE_DISPLAYNAME = "(default package)";

	public static MoveDestinationsResponse getMoveDestinations(MoveParams moveParams) {
		if (moveParams == null || StringUtils.isBlank(moveParams.moveKind)) {
			return null;
		}

		if ("moveResource".equalsIgnoreCase(moveParams.moveKind)) {
			return getPackageDestinations(moveParams.sourceUris);
		} else if ("moveInstanceMethod".equalsIgnoreCase(moveParams.moveKind)) {
			return getInstanceMethodDestinations(moveParams.params);
		}

		return null;
	}

	public static MoveDestinationsResponse getPackageDestinations(String[] documentUris) {
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
								if (fragment instanceof IPackageFragment pkg) {
									PackageNode packageNode = PackageNode.createPackageNode(pkg);
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

		return new MoveDestinationsResponse(packageNodes.toArray(new PackageNode[0]));
	}

	private static MethodDeclaration getSelectedMethodDeclaration(ICompilationUnit unit, CodeActionParams params) {
		CompilationUnit ast = CodeActionHandler.getASTRoot(unit);
		if (ast == null) {
			return null;
		}
		int start = DiagnosticsHelper.getStartOffset(unit, params.getRange());
		int end = DiagnosticsHelper.getEndOffset(unit, params.getRange());
		InnovationContext context = new InnovationContext(unit, start, end - start);
		context.setASTRoot(ast);

		ASTNode node = context.getCoveredNode();
		if (node == null) {
			node = context.getCoveringNode();
		}

		while (node != null && !(node instanceof BodyDeclaration)) {
			node = node.getParent();
		}

		if (node instanceof MethodDeclaration methodDeclaration) {
			return methodDeclaration;
		}

		return null;
	}

	private static MoveDestinationsResponse getInstanceMethodDestinations(CodeActionParams params) {
		final ICompilationUnit unit = JDTUtils.resolveCompilationUnit(params.getTextDocument().getUri());
		if (unit == null) {
			return new MoveDestinationsResponse("Cannot find the compilation unit associated with " + params.getTextDocument().getUri());
		}

		MethodDeclaration methodDeclaration = getSelectedMethodDeclaration(unit, params);
		if (methodDeclaration == null) {
			return new MoveDestinationsResponse("The selected element is not a method.");
		}

		IMethodBinding methodBinding = methodDeclaration.resolveBinding();
		if (methodBinding == null || !(methodBinding.getJavaElement() instanceof IMethod)) {
			return new MoveDestinationsResponse("The selected element is not a method.");
		}

		IMethod method = (IMethod) methodBinding.getJavaElement();
		MoveInstanceMethodProcessor processor = new MoveInstanceMethodProcessor(method, PreferenceManager.getCodeGenerationSettings(unit));
		Refactoring refactoring = new MoveRefactoring(processor);
		CheckConditionsOperation check = new CheckConditionsOperation(refactoring, CheckConditionsOperation.INITIAL_CONDITONS);
		try {
			check.run(new NullProgressMonitor());
			if (check.getStatus().hasFatalError()) {
				return new MoveDestinationsResponse(check.getStatus().getMessageMatchingSeverity(RefactoringStatus.FATAL));
			}

			IVariableBinding[] possibleTargets = processor.getPossibleTargets();
			LspVariableBinding[] targets = Stream.of(possibleTargets).map(target -> new LspVariableBinding(target)).toArray(LspVariableBinding[]::new);
			return new MoveDestinationsResponse(targets);
		} catch (CoreException e) {
			JavaLanguageServerPlugin.log(e);
		}

		return new MoveDestinationsResponse("Cannot find any target to move the method to.");
	}

	private static IProject findNearestProject(IPath filePath) {
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

	public static RefactorWorkspaceEdit move(MoveParams moveParams, IProgressMonitor monitor) {
		if (moveParams == null) {
			return new RefactorWorkspaceEdit("moveParams should not be empty.");
		}

		try {
			if ("moveResource".equalsIgnoreCase(moveParams.moveKind)) {
				String targetUri = null;
				if (moveParams.destination instanceof String dest) {
					targetUri = dest;
				} else {
					String json = (moveParams.destination == null ? null : new Gson().toJson(moveParams.destination));
					PackageNode packageNode = JSONUtility.toLsp4jModel(json, PackageNode.class);
					if (packageNode == null) {
						return new RefactorWorkspaceEdit("Invalid destination object: " + moveParams.destination);
					}

					targetUri = packageNode.uri;
				}

				return moveCU(moveParams.sourceUris, targetUri, moveParams.updateReferences, monitor);
			} else if ("moveInstanceMethod".equalsIgnoreCase(moveParams.moveKind)) {
				String json = (moveParams.destination == null ? null : new Gson().toJson(moveParams.destination));
				LspVariableBinding variableBinding = JSONUtility.toLsp4jModel(json, LspVariableBinding.class);
				if (variableBinding == null) {
					return new RefactorWorkspaceEdit("Invalid destination object: " + moveParams.destination);
				}

				return moveInstanceMethod(moveParams.params, variableBinding, monitor);
			} else if ("moveStaticMember".equalsIgnoreCase(moveParams.moveKind)) {
				String typeName = resolveTargetTypeName(moveParams.destination);
				return moveStaticMember(moveParams.params, typeName, monitor);
			} else if ("moveTypeToNewFile".equalsIgnoreCase(moveParams.moveKind)) {
				return moveTypeToNewFile(moveParams.params, monitor);
			} else if ("moveTypeToClass".equalsIgnoreCase(moveParams.moveKind)) {
				String typeName = resolveTargetTypeName(moveParams.destination);
				return moveTypeToClass(moveParams.params, typeName, monitor);
			}
		} catch (IllegalArgumentException e) {
			return new RefactorWorkspaceEdit(e.getMessage());
		}

		return new RefactorWorkspaceEdit("Unsupported move operation.");
	}

	private static String resolveTargetTypeName(Object destinationObj) throws IllegalArgumentException {
		if (destinationObj instanceof String dest) {
			return dest;
		}

		String json = (destinationObj== null ? null : new Gson().toJson(destinationObj));
		SymbolInformation destination = JSONUtility.toLsp4jModel(json, SymbolInformation.class);
		if (destination == null) {
			throw new IllegalArgumentException("Invalid destination object: " + destinationObj);
		}

		String typeName = destination.getName();
		if (StringUtils.isNotBlank(destination.getContainerName())) {
			typeName = destination.getContainerName() + "." + destination.getName();
		}

		return typeName;
	}

	private static RefactorWorkspaceEdit moveCU(String[] sourceUris, String targetUri, boolean updateReferences, IProgressMonitor monitor) {
		URI targetURI = JDTUtils.toURI(targetUri);
		if (targetURI == null) {
			return new RefactorWorkspaceEdit("Failed to move the files because of illegal uri '" + targetUri + "'.");
		}

		List<IJavaElement> elements = new ArrayList<>();
		for (String uri : sourceUris) {
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
			IContainer[] targetContainers = ResourcesPlugin.getWorkspace().getRoot().findContainersForLocationURI(targetURI);
			if (targetContainers == null || targetContainers.length == 0) {
				return new RefactorWorkspaceEdit("Failed to move the files because cannot find the target folder '" + targetUri + "' in the workspace.");
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
				if (targetElement instanceof IPackageFragmentRoot root) {
					targetElement = root.getPackageFragment("");
				}

				if (targetElement != null) {
					break;
				}
			}

			if (targetElement == null) {
				JavaLanguageServerPlugin.logError("Failed to move the files because cannot find the package associated with the path '" + targetUri + "'.");
				return new RefactorWorkspaceEdit("Failed to move the files because cannot find the package associated with the path '" + targetUri + "'.");
			}

			IReorgDestination packageDestination = ReorgDestinationFactory.createDestination(targetElement);
			WorkspaceEdit edit = move(resources, javaElements, packageDestination, updateReferences, submonitor);
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

	public static WorkspaceEdit move(IResource[] resources, IJavaElement[] javaElements, IReorgDestination destination, boolean updateReferences, IProgressMonitor monitor) throws CoreException {
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
			Change change = create.getChange();
			return ChangeUtil.convertToWorkspaceEdit(change);
		}

		return null;
	}

	private static RefactorWorkspaceEdit moveInstanceMethod(CodeActionParams params, LspVariableBinding destination, IProgressMonitor monitor) {
		final ICompilationUnit unit = JDTUtils.resolveCompilationUnit(params.getTextDocument().getUri());
		if (unit == null) {
			return new RefactorWorkspaceEdit("Failed to move instance method because cannot find the compilation unit associated with " + params.getTextDocument().getUri());
		}

		MethodDeclaration methodDeclaration = getSelectedMethodDeclaration(unit, params);
		if (methodDeclaration == null || destination == null) {
			return new RefactorWorkspaceEdit("Failed to move instance method because no method is selected or no destination is specified.");
		}

		IMethodBinding methodBinding = methodDeclaration.resolveBinding();
		if (methodBinding == null || !(methodBinding.getJavaElement() instanceof IMethod)) {
			return new RefactorWorkspaceEdit("Failed to move instance method because the selected element is not a method.");
		}

		SubMonitor subMonitor = SubMonitor.convert(monitor, "Moving instance method...", 100);
		IMethod method = (IMethod) methodBinding.getJavaElement();
		MoveInstanceMethodProcessor processor = new MoveInstanceMethodProcessor(method, PreferenceManager.getCodeGenerationSettings(unit));
		Refactoring refactoring = new MoveRefactoring(processor);
		CheckConditionsOperation check = new CheckConditionsOperation(refactoring, CheckConditionsOperation.INITIAL_CONDITONS);
		try {
			check.run(subMonitor.split(20));
			if (check.getStatus().getSeverity() >= RefactoringStatus.FATAL) {
				JavaLanguageServerPlugin.logError("Failed to execute the 'move' refactoring.");
				JavaLanguageServerPlugin.logError(check.getStatus().toString());
				return new RefactorWorkspaceEdit("Failed to move instance method. Reason: " + check.getStatus().toString());
			}

			IVariableBinding[] possibleTargets = processor.getPossibleTargets();
			Optional<IVariableBinding> target = Stream.of(possibleTargets).filter(possibleTarget -> Objects.equals(possibleTarget.getKey(), destination.bindingKey)).findFirst();
			if (target.isPresent()) {
				processor.setTarget(target.get());
				processor.setDeprecateDelegates(false);
				processor.setInlineDelegator(true);
				processor.setRemoveDelegator(true);
				check = new CheckConditionsOperation(refactoring, CheckConditionsOperation.FINAL_CONDITIONS);
				check.run(subMonitor.split(60));
				if (check.getStatus().getSeverity() >= RefactoringStatus.FATAL) {
					JavaLanguageServerPlugin.logError("Failed to execute the 'move' refactoring.");
					JavaLanguageServerPlugin.logError(check.getStatus().toString());
					return new RefactorWorkspaceEdit("Failed to move instance method. Reason: " + check.getStatus().toString());
				}

				Change change = processor.createChange(subMonitor.split(20));
				return new RefactorWorkspaceEdit(ChangeUtil.convertToWorkspaceEdit(change));
			} else {
				return new RefactorWorkspaceEdit("Failed to move instance method because cannot find the target " + destination.name);
			}
		} catch (CoreException e) {
			JavaLanguageServerPlugin.log(e);
			return new RefactorWorkspaceEdit("Failed to move instance method because of " + e.toString());
		} finally {
			subMonitor.done();
		}
	}

	private static RefactorWorkspaceEdit moveStaticMember(CodeActionParams params, String destinationTypeName, IProgressMonitor monitor) {
		final ICompilationUnit unit = JDTUtils.resolveCompilationUnit(params.getTextDocument().getUri());
		if (unit == null) {
			return new RefactorWorkspaceEdit("Failed to move static member because cannot find the compilation unit associated with " + params.getTextDocument().getUri());
		}

		BodyDeclaration bodyDeclaration = getSelectedMemberDeclaration(unit, params);
		List<IJavaElement> elements = new ArrayList<>();
		if (bodyDeclaration instanceof MethodDeclaration methodDecl) {
			elements.add(methodDecl.resolveBinding().getJavaElement());
		} else if (bodyDeclaration instanceof FieldDeclaration fieldDecl) {
			for (Object fragment : fieldDecl.fragments()) {
				elements.add(((VariableDeclarationFragment) fragment).resolveBinding().getJavaElement());
			}
		} else if (bodyDeclaration instanceof AbstractTypeDeclaration typeDecl) {
			elements.add(typeDecl.resolveBinding().getJavaElement());
		}

		IMember[] members = elements.stream().filter(IMember.class::isInstance).map(IMember.class::cast).toArray(IMember[]::new);
		return moveStaticMember(members, destinationTypeName, monitor);
	}

	private static RefactorWorkspaceEdit moveStaticMember(IMember[] members, String destinationTypeName, IProgressMonitor monitor) {
		if (members.length == 0 || destinationTypeName == null) {
			return new RefactorWorkspaceEdit("Failed to move static member because no members are selected or no destination is specified.");
		}

		CodeGenerationSettings settings = members[0].getTypeRoot() instanceof ICompilationUnit unit ? PreferenceManager.getCodeGenerationSettings(unit)
											: PreferenceManager.getCodeGenerationSettings(members[0].getJavaProject().getProject());
		MoveStaticMembersProcessor processor = new MoveStaticMembersProcessor(members, settings);
		Refactoring refactoring = new MoveRefactoring(processor);
		CheckConditionsOperation check = new CheckConditionsOperation(refactoring, CheckConditionsOperation.INITIAL_CONDITONS);
		SubMonitor subMonitor = SubMonitor.convert(monitor, "Moving static members...", 100);
		try {
			check.run(subMonitor.split(20));
			if (check.getStatus().getSeverity() >= RefactoringStatus.FATAL) {
				JavaLanguageServerPlugin.logError("Failed to execute the 'move' refactoring.");
				JavaLanguageServerPlugin.logError(check.getStatus().toString());
			}

			processor.setDestinationTypeFullyQualifiedName(destinationTypeName);
			processor.setDeprecateDelegates(false);
			check = new CheckConditionsOperation(refactoring, CheckConditionsOperation.FINAL_CONDITIONS);
			check.run(subMonitor.split(60));
			if (check.getStatus().getSeverity() >= RefactoringStatus.FATAL) {
				JavaLanguageServerPlugin.logError("Failed to execute the 'move' refactoring.");
				JavaLanguageServerPlugin.logError(check.getStatus().toString());
				return new RefactorWorkspaceEdit("Failed to move static member. Reason: " + check.getStatus().toString());
			}

			Change change = processor.createChange(subMonitor.split(20));
			return new RefactorWorkspaceEdit(ChangeUtil.convertToWorkspaceEdit(change));
		} catch (CoreException e) {
			JavaLanguageServerPlugin.log(e);
			return new RefactorWorkspaceEdit("Failed to move static member because of " + e.toString());
		} finally {
			subMonitor.done();
		}
	}

	private static RefactorWorkspaceEdit moveTypeToNewFile(CodeActionParams params, IProgressMonitor monitor) {
		final ICompilationUnit unit = JDTUtils.resolveCompilationUnit(params.getTextDocument().getUri());
		if (unit == null) {
			return new RefactorWorkspaceEdit("Failed to move type to new file because cannot find the compilation unit associated with " + params.getTextDocument().getUri());
		}

		IType type = getSelectedType(unit, params);
		if (type == null) {
			return new RefactorWorkspaceEdit("Failed to move type to new file because no type is selected.");
		}

		SubMonitor subMonitor = SubMonitor.convert(monitor, "Moving type to new file...", 100);
		try {
			MoveInnerToTopRefactoring refactoring = new MoveInnerToTopRefactoring(type, PreferenceManager.getCodeGenerationSettings(unit));
			CheckConditionsOperation check = new CheckConditionsOperation(refactoring, CheckConditionsOperation.ALL_CONDITIONS);
			check.run(subMonitor.split(50));
			if (check.getStatus().getSeverity() >= RefactoringStatus.FATAL) {
				JavaLanguageServerPlugin.logError("Failed to execute the 'move' refactoring.");
				JavaLanguageServerPlugin.logError(check.getStatus().toString());
				return new RefactorWorkspaceEdit("Failed to move type to new file. Reason: " + check.getStatus().toString());
			}

			Change change = refactoring.createChange(subMonitor.split(50));
			return new RefactorWorkspaceEdit(ChangeUtil.convertToWorkspaceEdit(change));
		} catch (CoreException e) {
			JavaLanguageServerPlugin.log(e);
			return new RefactorWorkspaceEdit("Failed to move type to new file because of " + e.toString());
		} catch (OperationCanceledException e) {
			return null;
		}
	}

	private static RefactorWorkspaceEdit moveTypeToClass(CodeActionParams params, String destinationTypeName, IProgressMonitor monitor) {
		final ICompilationUnit unit = JDTUtils.resolveCompilationUnit(params.getTextDocument().getUri());
		if (unit == null) {
			return new RefactorWorkspaceEdit("Failed to move type to another class because cannot find the compilation unit associated with " + params.getTextDocument().getUri());
		}

		IType type = getSelectedType(unit, params);
		if (type == null) {
			return new RefactorWorkspaceEdit("Failed to move type to another class because no type is selected.");
		}

		try {
			if (RefactoringAvailabilityTesterCore.isMoveStaticAvailable(type)) {
				return moveStaticMember(new IMember[] { type }, destinationTypeName, monitor);
			}

			return new RefactorWorkspaceEdit("Moving non-static type to another class is not supported.");
		} catch (JavaModelException e) {
			return new RefactorWorkspaceEdit("Failed to move type to another class. Reason: " + e.toString());
		}
	}

	private static IType getSelectedType(ICompilationUnit unit, CodeActionParams params) {
		AbstractTypeDeclaration typeDeclaration = getSelectedTypeDeclaration(unit, params);
		return typeDeclaration == null ? null : (IType) typeDeclaration.resolveBinding().getJavaElement();
	}

	private static BodyDeclaration getSelectedMemberDeclaration(ICompilationUnit unit, CodeActionParams params) {
		CompilationUnit ast = CodeActionHandler.getASTRoot(unit);
		if (ast == null) {
			return null;
		}
		int start = DiagnosticsHelper.getStartOffset(unit, params.getRange());
		int end = DiagnosticsHelper.getEndOffset(unit, params.getRange());
		InnovationContext context = new InnovationContext(unit, start, end - start);
		context.setASTRoot(ast);

		ASTNode node = context.getCoveredNode();
		if (node == null) {
			node = context.getCoveringNode();
		}

		while (node != null && !(node instanceof BodyDeclaration)) {
			node = node.getParent();
		}

		if (node != null && (node instanceof MethodDeclaration || node instanceof FieldDeclaration || node instanceof AbstractTypeDeclaration) && JdtFlags.isStatic((BodyDeclaration) node)) {
			return (BodyDeclaration) node;
		}

		return null;
	}

	private static AbstractTypeDeclaration getSelectedTypeDeclaration(ICompilationUnit unit, CodeActionParams params) {
		int start = DiagnosticsHelper.getStartOffset(unit, params.getRange());
		int end = DiagnosticsHelper.getEndOffset(unit, params.getRange());
		InnovationContext context = new InnovationContext(unit, start, end - start);
		context.setASTRoot(CodeActionHandler.getASTRoot(unit));

		ASTNode node = context.getCoveredNode();
		if (node == null) {
			node = context.getCoveringNode();
		}

		while (node != null && !(node instanceof AbstractTypeDeclaration)) {
			node = node.getParent();
		}

		return (AbstractTypeDeclaration) node;
	}

	public static class MoveDestinationsResponse {
		public String errorMessage;
		public Object[] destinations;

		public MoveDestinationsResponse(String errorMessage) {
			this.errorMessage = errorMessage;
		}

		public MoveDestinationsResponse(Object[] destinations) {
			this.destinations = destinations;
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

	public static class MoveParams {
		/**
		 * The supported move kind: moveResource, moveInstanceMethod, moveStaticMember,
		 * moveTypeToNewFile.
		 */
		String moveKind;
		/**
		 * The selected resource uris when the move operation is triggered.
		 */
		String[] sourceUris;
		/**
		 * The code action params when the move operation is triggered.
		 */
		CodeActionParams params;
		/**
		 * The possible destination: a folder/package, class, instanceDeclaration.
		 */
		Object destination;
		boolean updateReferences;

		public MoveParams(String moveKind, String[] sourceUris) {
			this(moveKind, sourceUris, null);
		}

		public MoveParams(String moveKind, String[] sourceUris, CodeActionParams params) {
			this(moveKind, sourceUris, params, null, true);
		}

		public MoveParams(String moveKind, String[] sourceUris, Object destination, boolean updateReferences) {
			this(moveKind, sourceUris, null, destination, updateReferences);
		}

		public MoveParams(String moveKind, CodeActionParams params, Object destination, boolean updateReferences) {
			this(moveKind, null, params, destination, updateReferences);
		}

		public MoveParams(String moveKind, String[] sourceUris, CodeActionParams params, Object destination, boolean updateReferences) {
			this.moveKind = moveKind;
			this.sourceUris = sourceUris;
			this.params = params;
			this.destination = destination;
			this.updateReferences = updateReferences;
		}
	}
}
