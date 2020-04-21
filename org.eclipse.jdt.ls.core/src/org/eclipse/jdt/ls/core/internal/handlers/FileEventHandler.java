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

package org.eclipse.jdt.ls.core.internal.handlers;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;

import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IWorkspaceRunnable;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.core.runtime.Path;
import org.eclipse.core.runtime.SubMonitor;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IPackageFragment;
import org.eclipse.jdt.core.IPackageFragmentRoot;
import org.eclipse.jdt.internal.core.JavaModelManager;
import org.eclipse.jdt.ls.core.internal.ChangeUtil;
import org.eclipse.jdt.ls.core.internal.JDTUtils;
import org.eclipse.jdt.ls.core.internal.JavaLanguageServerPlugin;
import org.eclipse.jdt.ls.core.internal.ProjectUtils;
import org.eclipse.jdt.ls.core.internal.ResourceUtils;
import org.eclipse.jdt.ls.core.internal.commands.BuildPathCommand;
import org.eclipse.jdt.ls.core.internal.commands.BuildPathCommand.ListCommandResult;
import org.eclipse.jdt.ls.core.internal.commands.BuildPathCommand.SourcePath;
import org.eclipse.jdt.ls.core.internal.corext.refactoring.rename.RenamePackageProcessor;
import org.eclipse.jdt.ls.core.internal.corext.refactoring.rename.RenameSupport;
import org.eclipse.lsp4j.WorkspaceEdit;
import org.eclipse.ltk.core.refactoring.Change;
import org.eclipse.ltk.core.refactoring.CheckConditionsOperation;
import org.eclipse.ltk.core.refactoring.RefactoringStatus;
import org.eclipse.ltk.core.refactoring.RefactoringTickProvider;
import org.eclipse.ltk.core.refactoring.participants.RenameRefactoring;
import org.eclipse.ltk.internal.core.refactoring.NotCancelableProgressMonitor;

public class FileEventHandler {

	public static WorkspaceEdit handleRenameFiles(FileRenameParams params, IProgressMonitor monitor) {
		if (params.files == null || params.files.isEmpty()) {
			return null;
		}

		FileRenameEvent[] files = params.files.stream().filter(event -> isFileNameRenameEvent(event)).toArray(FileRenameEvent[]::new);
		if (files.length == 0) {
			return null;
		}

		SubMonitor submonitor = SubMonitor.convert(monitor, "Computing rename updates...", 100 * files.length);
		WorkspaceEdit root = null;
		for (FileRenameEvent event : files) {
			String oldUri = event.oldUri;
			String newUri = event.newUri;
			ICompilationUnit unit = JDTUtils.resolveCompilationUnit(newUri);
			SubMonitor splitedMonitor = submonitor.split(100);
			try {
				if (unit != null && !unit.exists()) {
					final ICompilationUnit[] units = new ICompilationUnit[1];
					units[0] = unit;
					try {
						ResourcesPlugin.getWorkspace().run(new IWorkspaceRunnable() {
							@Override
							public void run(IProgressMonitor monitor) throws CoreException {
								units[0] = createCompilationUnit(units[0]);
							}
						}, new NullProgressMonitor());
					} catch (CoreException e) {
						JavaLanguageServerPlugin.logException(e.getMessage(), e);
					}
					unit = units[0];
				}

				if (unit != null) {
					String oldPrimaryType = getPrimaryTypeName(oldUri);
					String newPrimaryType = getPrimaryTypeName(newUri);
					if (!unit.getType(newPrimaryType).exists() && unit.getType(oldPrimaryType).exists()) {
						WorkspaceEdit edit = getRenameEdit(unit.getType(oldPrimaryType), newPrimaryType, splitedMonitor);
						root = ChangeUtil.mergeChanges(root, edit, true);
					}
				}
			} catch (CoreException e) {
				JavaLanguageServerPlugin.logException("Computing the rename edit: ", e);
			} finally {
				splitedMonitor.done();
			}
		}

		submonitor.done();
		return root;
	}

	public static WorkspaceEdit handleWillRenameFiles(FileRenameParams params, IProgressMonitor monitor) {
		if (params.files == null || params.files.isEmpty()) {
			return null;
		}

		FileRenameEvent[] renamefolders = params.files.stream().filter(event -> isFolderRenameEvent(event)).toArray(FileRenameEvent[]::new);
		if (renamefolders.length == 0) {
			return null;
		}

		SourcePath[] sourcePaths = getSourcePaths();
		if (sourcePaths == null || sourcePaths.length == 0) {
			return null;
		}

		return computePackageRenameEdit(renamefolders, sourcePaths, monitor);
	}

	private static WorkspaceEdit computePackageRenameEdit(FileRenameEvent[] renameEvents, SourcePath[] sourcePaths, IProgressMonitor monitor) {
		WorkspaceEdit[] root = new WorkspaceEdit[1];
		SubMonitor submonitor = SubMonitor.convert(monitor, "Computing package rename updates...", 100 * renameEvents.length);
		for (FileRenameEvent event : renameEvents) {
			IPath oldLocation = ResourceUtils.filePathFromURI(event.oldUri);
			IPath newLocation = ResourceUtils.filePathFromURI(event.newUri);
			for (SourcePath sourcePath : sourcePaths) {
				IPath sourceLocation = Path.fromOSString(sourcePath.path);
				IPath sourceEntry = Path.fromOSString(sourcePath.classpathEntry);
				if (sourceLocation.isPrefixOf(oldLocation)) {
					SubMonitor renameMonitor = submonitor.split(100);
					try {
						IJavaProject javaProject = ProjectUtils.getJavaProject(sourcePath.projectName);
						if (javaProject == null) {
							break;
						}

						IPackageFragmentRoot packageRoot = javaProject.findPackageFragmentRoot(sourceEntry);
						if (packageRoot == null) {
							break;
						}

						String oldPackageName = String.join(".", oldLocation.makeRelativeTo(sourceLocation).segments());
						String newPackageName = String.join(".", newLocation.makeRelativeTo(sourceLocation).segments());
						IPackageFragment oldPackageFragment = packageRoot.getPackageFragment(oldPackageName);
						if (oldPackageFragment != null && oldPackageFragment.getResource() != null) {
							oldPackageFragment.getResource().refreshLocal(IResource.DEPTH_INFINITE, null);
							if (oldPackageFragment.exists()) {
								ResourcesPlugin.getWorkspace().run((pm) -> {
									WorkspaceEdit edit = getRenameEdit(oldPackageFragment, newPackageName, pm);
									root[0] = ChangeUtil.mergeChanges(root[0], edit, true);
								}, oldPackageFragment.getSchedulingRule(), IResource.NONE, renameMonitor);
							}
						}
					} catch (CoreException e) {
						JavaLanguageServerPlugin.logException("Failed to compute the package rename update", e);
					} finally {
						renameMonitor.done();
					}

					break;
				}
			}
		}

		submonitor.done();
		return ChangeUtil.hasChanges(root[0]) ? root[0] : null;
	}

	private static SourcePath[] getSourcePaths() {
		SourcePath[] sourcePaths = new SourcePath[0];
		ListCommandResult result = (ListCommandResult) BuildPathCommand.listSourcePaths();
		if (result.status && result.data != null && result.data.length > 0) {
			sourcePaths = result.data;
		}

		Arrays.sort(sourcePaths, (a, b) -> {
			return b.path.length() - a.path.length();
		});

		return sourcePaths;
	}

	private static boolean isFileNameRenameEvent(FileRenameEvent event) {
		IPath oldPath = ResourceUtils.filePathFromURI(event.oldUri);
		IPath newPath = ResourceUtils.filePathFromURI(event.newUri);
		return newPath.toFile().isFile() && oldPath.lastSegment().endsWith(".java")
			&& newPath.lastSegment().endsWith(".java")
			&& Objects.equals(oldPath.removeLastSegments(1), newPath.removeLastSegments(1));
	}

	private static boolean isFolderRenameEvent(FileRenameEvent event) {
		IPath oldPath = ResourceUtils.filePathFromURI(event.oldUri);
		IPath newPath = ResourceUtils.filePathFromURI(event.newUri);
		return (oldPath.toFile().isDirectory() || newPath.toFile().isDirectory()) && Objects.equals(oldPath.removeLastSegments(1), newPath.removeLastSegments(1));
	}

	private static String getPrimaryTypeName(String uri) {
		String fileName = ResourceUtils.filePathFromURI(uri).lastSegment();
		int idx = fileName.lastIndexOf(".");
		if (idx >= 0) {
			return fileName.substring(0, idx);
		}

		return fileName;
	}

	private static ICompilationUnit createCompilationUnit(ICompilationUnit unit) {
		try {
			unit.getResource().refreshLocal(IResource.DEPTH_ONE, new NullProgressMonitor());
			if (unit.getResource().exists()) {
				IJavaElement parent = unit.getParent();
				if (parent instanceof IPackageFragment) {
					IPackageFragment pkg = (IPackageFragment) parent;
					if (JavaModelManager.determineIfOnClasspath(unit.getResource(), unit.getJavaProject()) != null) {
						unit = pkg.createCompilationUnit(unit.getElementName(), unit.getSource(), true, new NullProgressMonitor());
					}
				}
			}
		} catch (CoreException e) {
			JavaLanguageServerPlugin.logException(e.getMessage(), e);
		}
		return unit;
	}

	private static WorkspaceEdit getRenameEdit(IJavaElement targetElement, String newName, IProgressMonitor monitor) throws CoreException {
		RenameSupport renameSupport = RenameSupport.create(targetElement, newName, RenameSupport.UPDATE_REFERENCES);
		if (renameSupport == null) {
			return null;
		}

		if (targetElement instanceof IPackageFragment) {
			((RenamePackageProcessor) renameSupport.getJavaRenameProcessor()).setRenameSubpackages(true);
		}

		RenameRefactoring renameRefactoring = renameSupport.getRenameRefactoring();
		RefactoringTickProvider rtp = renameRefactoring.getRefactoringTickProvider();
		SubMonitor submonitor = SubMonitor.convert(monitor, "Creating rename changes...", rtp.getAllTicks());
		CheckConditionsOperation checkConditionOperation = new CheckConditionsOperation(renameRefactoring, CheckConditionsOperation.ALL_CONDITIONS);
		checkConditionOperation.run(submonitor.split(rtp.getCheckAllConditionsTicks()));
		if (checkConditionOperation.getStatus().getSeverity() >= RefactoringStatus.FATAL) {
			JavaLanguageServerPlugin.logError(checkConditionOperation.getStatus().getMessageMatchingSeverity(RefactoringStatus.ERROR));
		}

		Change change = renameRefactoring.createChange(submonitor.split(rtp.getCreateChangeTicks()));
		change.initializeValidationData(new NotCancelableProgressMonitor(submonitor.split(rtp.getInitializeChangeTicks())));
		return ChangeUtil.convertToWorkspaceEdit(change);
	}

	public static class FileRenameEvent {
		public String oldUri;
		public String newUri;

		public FileRenameEvent() {
		}

		public FileRenameEvent(String oldUri, String newUri) {
			this.oldUri = oldUri;
			this.newUri = newUri;
		}
	}

	public static class FileRenameParams {
		public List<FileRenameEvent> files;

		public FileRenameParams() {
		}

		public FileRenameParams(List<FileRenameEvent> files) {
			this.files = files;
		}
	}
}
