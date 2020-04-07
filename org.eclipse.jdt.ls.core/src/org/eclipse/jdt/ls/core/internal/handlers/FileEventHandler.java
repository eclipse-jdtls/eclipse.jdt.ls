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

import java.util.List;
import java.util.Objects;

import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IWorkspaceRunnable;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.core.runtime.SubMonitor;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IPackageFragment;
import org.eclipse.jdt.internal.core.JavaModelManager;
import org.eclipse.jdt.ls.core.internal.ChangeUtil;
import org.eclipse.jdt.ls.core.internal.JDTUtils;
import org.eclipse.jdt.ls.core.internal.JavaLanguageServerPlugin;
import org.eclipse.jdt.ls.core.internal.ResourceUtils;
import org.eclipse.jdt.ls.core.internal.corext.refactoring.rename.RenameSupport;
import org.eclipse.lsp4j.WorkspaceEdit;
import org.eclipse.ltk.core.refactoring.Change;
import org.eclipse.ltk.core.refactoring.CheckConditionsOperation;
import org.eclipse.ltk.core.refactoring.CreateChangeOperation;
import org.eclipse.ltk.core.refactoring.RefactoringStatus;
import org.eclipse.ltk.core.refactoring.participants.RenameRefactoring;

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

	private static boolean isFileNameRenameEvent(FileRenameEvent event) {
		IPath oldPath = ResourceUtils.filePathFromURI(event.oldUri);
		IPath newPath = ResourceUtils.filePathFromURI(event.newUri);
		return oldPath.lastSegment().endsWith(".java")
			&& newPath.lastSegment().endsWith(".java")
			&& Objects.equals(oldPath.removeLastSegments(1), newPath.removeLastSegments(1));
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

		RenameRefactoring renameRefactoring = renameSupport.getRenameRefactoring();

		CheckConditionsOperation check = new CheckConditionsOperation(renameRefactoring, CheckConditionsOperation.ALL_CONDITIONS);
		CreateChangeOperation create = new CreateChangeOperation(check, RefactoringStatus.FATAL);
		create.run(monitor);
		if (check.getStatus().getSeverity() >= RefactoringStatus.FATAL) {
			JavaLanguageServerPlugin.logError(check.getStatus().getMessageMatchingSeverity(RefactoringStatus.ERROR));
		}

		Change change = create.getChange();
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
