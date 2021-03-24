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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.Path;
import org.eclipse.core.runtime.SubMonitor;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IPackageFragment;
import org.eclipse.jdt.core.IPackageFragmentRoot;
import org.eclipse.jdt.core.JavaModelException;
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
import org.eclipse.jdt.ls.core.internal.corext.refactoring.reorg.IReorgDestination;
import org.eclipse.jdt.ls.core.internal.corext.refactoring.reorg.ReorgDestinationFactory;
import org.eclipse.lsp4j.FileRename;
import org.eclipse.lsp4j.RenameFilesParams;
import org.eclipse.lsp4j.WorkspaceEdit;
import org.eclipse.ltk.core.refactoring.Change;
import org.eclipse.ltk.core.refactoring.CheckConditionsOperation;
import org.eclipse.ltk.core.refactoring.RefactoringStatus;
import org.eclipse.ltk.core.refactoring.RefactoringTickProvider;
import org.eclipse.ltk.core.refactoring.participants.RenameRefactoring;
import org.eclipse.ltk.internal.core.refactoring.NotCancelableProgressMonitor;

public class FileEventHandler {

	public static WorkspaceEdit handleWillRenameFiles(RenameFilesParams params, IProgressMonitor monitor) {
		List<FileRename> files = params.getFiles();
		if (files == null || files.isEmpty()) {
			return null;
		}

		FileRename[] renameFiles = new FileRename[0];
		FileRename[] renameFolders = new FileRename[0];
		FileRename[] moveFiles = new FileRename[0];
		if (files.size() == 1) {
			FileRename renameEvent = files.get(0);
			if (isFileNameRenameEvent(renameEvent)) {
				renameFiles = new FileRename[] { renameEvent };
			} else if (isFolderRenameEvent(renameEvent)) {
				renameFolders = new FileRename[] { renameEvent };
			} else if (isMoveEvent(renameEvent)) {
				moveFiles = new FileRename[] { renameEvent };
			}
		} else {
			moveFiles = files.stream().filter(event -> isMoveEvent(event)).toArray(FileRename[]::new);
		}

		if (renameFiles.length == 0 && renameFolders.length == 0 && moveFiles.length == 0) {
			return null;
		}

		SourcePath[] sourcePaths = getSourcePaths();
		if (sourcePaths == null || sourcePaths.length == 0) {
			return null;
		}

		WorkspaceEdit root = null;
		SubMonitor submonitor = SubMonitor.convert(monitor, "Computing rename updates...", renameFiles.length + renameFolders.length + moveFiles.length);
		if (renameFiles.length > 0) {
			WorkspaceEdit edit = computeFileRenameEdit(renameFiles, submonitor.split(renameFiles.length));
			root = ChangeUtil.mergeChanges(root, edit, true);
		}

		if (renameFolders.length > 0) {
			WorkspaceEdit edit = computePackageRenameEdit(renameFolders, sourcePaths, submonitor.split(renameFolders.length));
			root = ChangeUtil.mergeChanges(root, edit, true);
		}

		if (moveFiles.length > 0) {
			WorkspaceEdit edit = computeMoveEdit(moveFiles, sourcePaths, submonitor.split(moveFiles.length));
			root = ChangeUtil.mergeChanges(root, edit, true);
		}

		submonitor.done();
		return ChangeUtil.hasChanges(root) ? root : null;
	}

	private static WorkspaceEdit computeFileRenameEdit(FileRename[] renameEvents, IProgressMonitor monitor) {
		SubMonitor submonitor = SubMonitor.convert(monitor, "Computing file rename updates...", 100 * renameEvents.length);
		WorkspaceEdit root = null;
		for (FileRename event : renameEvents) {
			String oldUri = event.getOldUri();
			String newUri = event.getNewUri();
			ICompilationUnit unit = JDTUtils.resolveCompilationUnit(oldUri);
			SubMonitor splitedMonitor = submonitor.split(100);
			try {
				if (unit == null || !unit.exists()) {
					JavaLanguageServerPlugin.logError("Failed to compute the file rename edit because the file '" + oldUri + "' doesn't exist.");
					continue;
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
				JavaLanguageServerPlugin.logException("Computing the file rename edit: ", e);
			} finally {
				splitedMonitor.done();
			}
		}

		submonitor.done();
		return root;
	}

	private static WorkspaceEdit computePackageRenameEdit(FileRename[] renameEvents, SourcePath[] sourcePaths, IProgressMonitor monitor) {
		WorkspaceEdit[] root = new WorkspaceEdit[1];
		SubMonitor submonitor = SubMonitor.convert(monitor, "Computing package rename updates...", 100 * renameEvents.length);
		for (FileRename event : renameEvents) {
			IPath oldLocation = ResourceUtils.filePathFromURI(event.getOldUri());
			IPath newLocation = ResourceUtils.filePathFromURI(event.getNewUri());
			IPackageFragment oldPackageFragment = resolvePackageFragment(oldLocation, sourcePaths);
			SubMonitor renameMonitor = submonitor.split(100);
			try {
				if (oldPackageFragment != null && !oldPackageFragment.isDefaultPackage() && oldPackageFragment.getResource() != null) {
					String newPackageName = resolvePackageName(newLocation, sourcePaths);
					if (newPackageName == null) {
						continue;
					}

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
		}

		submonitor.done();
		return ChangeUtil.hasChanges(root[0]) ? root[0] : null;
	}

	private static WorkspaceEdit computeMoveEdit(FileRename[] moveEvents, SourcePath[] sourcePaths, IProgressMonitor monitor) {
		IPath[] newPaths = Stream.of(moveEvents).map(event -> ResourceUtils.filePathFromURI(event.getNewUri())).toArray(IPath[]::new);
		IPath destinationPath = ResourceUtils.getLongestCommonPath(newPaths);
		if (destinationPath == null) {
			return null;
		}

		// Verify all files are moving to the same destination.
		for (FileRename event : moveEvents) {
			IPath oldPath = ResourceUtils.filePathFromURI(event.getOldUri());
			IPath expectedNewPath = destinationPath.append(oldPath.lastSegment());
			IPath actualNewPath = ResourceUtils.filePathFromURI(event.getNewUri());
			if (!Objects.equals(expectedNewPath, actualNewPath)) {
				JavaLanguageServerPlugin.logError("Failed to compute move refactoring because the files are not moving to the same destination " + destinationPath.toOSString());
				return null;
			}
		}

		IPackageFragment destinationPackage = resolvePackageFragment(destinationPath, sourcePaths);
		if (destinationPackage == null) {
			return null;
		}

		// formatter:off
		ICompilationUnit[] cus = Stream.of(moveEvents)
			.filter(event -> {
				IPath oldPath = ResourceUtils.filePathFromURI(event.getOldUri());
				return oldPath != null && oldPath.toFile().isFile();
			}).map(event -> JDTUtils.resolveCompilationUnit(event.getOldUri()))
			.filter(cu -> cu != null && cu.getJavaProject() != null)
			.toArray(ICompilationUnit[]::new);
		// formatter:on
		List<ICompilationUnit> nonClasspathCus = new ArrayList<>();
		for (ICompilationUnit unit : cus) {
			if (!unit.getJavaProject().isOnClasspath(unit)) {
				nonClasspathCus.add(unit);
			}
		}

		WorkspaceEdit[] root = new WorkspaceEdit[1];
		if (cus.length > 0) {
			try {
				// For the cu that's not on the project's classpath, need to become workingcopy first,
				// otherwise invoking cu.getBuffer() will throw exception.
				for (ICompilationUnit cu : nonClasspathCus) {
					cu.becomeWorkingCopy(monitor);
				}
				IReorgDestination packageDestination = ReorgDestinationFactory.createDestination(destinationPackage);
				ResourcesPlugin.getWorkspace().run((pm) -> {
					root[0] = MoveHandler.move(new IResource[0], cus, packageDestination, true, pm);
				}, monitor);
			} catch (CoreException e) {
				JavaLanguageServerPlugin.logException("Failed to compute the move update", e);
			} finally {
				for (ICompilationUnit cu : nonClasspathCus) {
					try {
						cu.discardWorkingCopy();
					} catch (JavaModelException e) {
						// do nothing
					}
				}
			}
		}

		return ChangeUtil.hasChanges(root[0]) ? root[0] : null;
	}

	private static String resolvePackageName(IPath javaElementLocation, SourcePath[] sourcePaths) {
		return (String) resolvePackage(javaElementLocation, sourcePaths, false);
	}

	private static IPackageFragment resolvePackageFragment(IPath javaElementLocation, SourcePath[] sourcePaths) {
		return (IPackageFragment) resolvePackage(javaElementLocation, sourcePaths, true);
	}

	private static Object resolvePackage(IPath javaElementLocation, SourcePath[] sourcePaths, boolean returnModel) {
		for (SourcePath sourcePath : sourcePaths) {
			IPath sourceLocation = Path.fromOSString(sourcePath.path);
			IPath sourceEntry = Path.fromOSString(sourcePath.classpathEntry);
			if (sourceLocation.isPrefixOf(javaElementLocation)) {
				try {
					IJavaProject javaProject = ProjectUtils.getJavaProject(sourcePath.projectName);
					if (javaProject == null) {
						return null;
					}

					IPackageFragmentRoot packageRoot = javaProject.findPackageFragmentRoot(sourceEntry);
					if (packageRoot == null) {
						return null;
					}

					String packageName = String.join(".", javaElementLocation.makeRelativeTo(sourceLocation).segments());
					if (returnModel) {
						return packageRoot.getPackageFragment(packageName);
					}

					return packageName;
				} catch (CoreException e) {
					JavaLanguageServerPlugin.logException("Failed to resolve the package fragment", e);
				}

				return null;
			}
		}

		return null;
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

	private static boolean isFileNameRenameEvent(FileRename event) {
		IPath oldPath = ResourceUtils.filePathFromURI(event.getOldUri());
		IPath newPath = ResourceUtils.filePathFromURI(event.getNewUri());
		if (oldPath == null || newPath == null) {
			return false;
		}

		return (oldPath.toFile().isFile() || newPath.toFile().isFile())
			&& oldPath.lastSegment().endsWith(".java") && newPath.lastSegment().endsWith(".java")
			&& Objects.equals(oldPath.removeLastSegments(1), newPath.removeLastSegments(1));
	}

	private static boolean isFolderRenameEvent(FileRename event) {
		IPath oldPath = ResourceUtils.filePathFromURI(event.getOldUri());
		IPath newPath = ResourceUtils.filePathFromURI(event.getNewUri());
		if (oldPath == null || newPath == null) {
			return false;
		}

		return oldPath.toFile().isDirectory() || newPath.toFile().isDirectory();
	}

	/**
	 * The only move scenario that the upstream JDT supports is moving Java files to
	 * another package. It does not support moving a package to another package. So the
	 * language server only needs to handle the file move event, not the directory move event.
	 */
	private static boolean isMoveEvent(FileRename event) {
		IPath oldPath = ResourceUtils.filePathFromURI(event.getOldUri());
		IPath newPath = ResourceUtils.filePathFromURI(event.getNewUri());
		if (oldPath == null || newPath == null) {
			return false;
		}

		return oldPath.toFile().isFile() && oldPath.lastSegment().endsWith(".java")
			&& Objects.equals(oldPath.lastSegment(), newPath.lastSegment());
	}

	private static String getPrimaryTypeName(String uri) {
		String fileName = ResourceUtils.filePathFromURI(uri).lastSegment();
		int idx = fileName.lastIndexOf(".");
		if (idx >= 0) {
			return fileName.substring(0, idx);
		}

		return fileName;
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
}
