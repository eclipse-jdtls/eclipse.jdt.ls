/*******************************************************************************
 * Copyright (c) 2017,2018 Microsoft Corporation and others.
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
package org.eclipse.jdt.ls.core.internal.commands;

import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;

import org.apache.commons.lang3.StringUtils;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IWorkspaceRoot;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IPackageFragment;
import org.eclipse.jdt.core.IPackageFragmentRoot;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.ls.core.internal.ChangeUtil;
import org.eclipse.jdt.ls.core.internal.IConstants;
import org.eclipse.jdt.ls.core.internal.JDTUtils;
import org.eclipse.jdt.ls.core.internal.JavaLanguageServerPlugin;
import org.eclipse.jdt.ls.core.internal.ResourceUtils;
import org.eclipse.jdt.ls.core.internal.TextEditConverter;
import org.eclipse.jdt.ls.core.internal.corrections.InnovationContext;
import org.eclipse.jdt.ls.core.internal.corrections.proposals.CUCorrectionProposal;
import org.eclipse.jdt.ls.core.internal.corrections.proposals.IProposalRelevance;
import org.eclipse.jdt.ls.core.internal.handlers.OrganizeImportsHandler;
import org.eclipse.lsp4j.CodeActionKind;
import org.eclipse.lsp4j.WorkspaceEdit;
import org.eclipse.ltk.core.refactoring.TextChange;
import org.eclipse.text.edits.TextEdit;

public class OrganizeImportsCommand {

	public Object organizeImports(List<Object> arguments) throws CoreException {
		WorkspaceEdit edit = new WorkspaceEdit();
		if (arguments != null && !arguments.isEmpty() && arguments.get(0) instanceof String fileUri) {
			final IPath rootPath = ResourceUtils.filePathFromURI(fileUri);
			if (rootPath == null) {
				throw new CoreException(new Status(IStatus.ERROR, IConstants.PLUGIN_ID, "URI is not found"));
			}
			final IWorkspaceRoot wsroot = ResourcesPlugin.getWorkspace().getRoot();
			IResource resource = wsroot.getFileForLocation(rootPath);
			if (resource == null) {
				resource = wsroot.getContainerForLocation(rootPath);
			}
			if (resource != null) {
				final OrganizeImportsCommand command = new OrganizeImportsCommand();
				int type = resource.getType();
				switch (type) {
					case IResource.PROJECT:
						edit = command.organizeImportsInProject(resource.getAdapter(IProject.class));
						break;
					case IResource.FOLDER:
						edit = command.organizeImportsInDirectory(fileUri, resource.getProject());
						break;
					case IResource.FILE:
						edit = command.organizeImportsInFile(fileUri);
						break;
					default://This can only be IResource.ROOT. Which is not relevant to jdt.ls
						// do nothing allow to return the empty WorkspaceEdit.
						break;
				}
			}
		}
		return edit;
	}

	/**
	 * Organize imports when select a project.
	 *
	 * @param proj
	 *            the target project
	 * @return
	 */
	public WorkspaceEdit organizeImportsInProject(IProject proj) {
		WorkspaceEdit rootEdit = new WorkspaceEdit();
		HashSet<IJavaElement> result = new HashSet<>();

		collectCompilationUnits(JavaCore.create(proj), result, null);
		for (IJavaElement elem : result) {
			if (elem.getElementType() == IJavaElement.COMPILATION_UNIT) {
				organizeImportsInCompilationUnit((ICompilationUnit) elem, rootEdit);
			}
		}
		return rootEdit;
	}

	/**
	 * Organize imports underlying a directory
	 *
	 * @param folderUri
	 *            Selected folder URI
	 * @param proj
	 *            the folder associated project
	 * @return
	 * @throws CoreException
	 */
	public WorkspaceEdit organizeImportsInDirectory(String folderUri, IProject proj) throws CoreException {
		WorkspaceEdit rootEdit = new WorkspaceEdit();
		IPackageFragment fragment = null;
		if (JDTUtils.toURI(folderUri) != null) {
			fragment = JDTUtils.resolvePackage(folderUri);
		}
		// Select an individual package
		if (fragment != null) {
			organizeImportsInPackageFragment(fragment, rootEdit);
		} else if (proj != null) {
			// Search the packages under the selected folder:
			IJavaProject javaProject = JavaCore.create(proj);
			IPath rootPath = ResourceUtils.filePathFromURI(folderUri);
			IPackageFragmentRoot[] roots = javaProject.getPackageFragmentRoots();
			HashSet<IJavaElement> result = new HashSet<>();
			for (IPackageFragmentRoot root : roots) {
				if (root.getKind() == IPackageFragmentRoot.K_SOURCE) {
					String packageRoot = root.getResource().getLocation().toString();
					if (packageRoot.toLowerCase().indexOf(rootPath.toString().toLowerCase()) >= 0) {
						collectCompilationUnits(javaProject, result, null);
					}
				}
			}
			for (IJavaElement elem : result) {
				if (elem.getElementType() == IJavaElement.COMPILATION_UNIT) {
					organizeImportsInCompilationUnit((ICompilationUnit) elem, rootEdit);
				}
			}
		}
		return rootEdit;
	}

	public WorkspaceEdit organizeImportsInFile(String fileUri) {
		WorkspaceEdit rootEdit = new WorkspaceEdit();
		ICompilationUnit unit = null;
		if (JDTUtils.toURI(fileUri) != null) {
			unit = JDTUtils.resolveCompilationUnit(fileUri);
		}
		if (unit == null) {
			return rootEdit;
		}
		organizeImportsInCompilationUnit(unit, rootEdit);
		return rootEdit;
	}

	public void organizeImportsInPackageFragment(IPackageFragment fragment, WorkspaceEdit rootEdit) throws CoreException {
		HashSet<IJavaElement> result = new HashSet<>();
		collectCompilationUnits(fragment.getParent(), result, fragment.getElementName());
		for (IJavaElement elem : result) {
			if (elem.getElementType() == IJavaElement.COMPILATION_UNIT) {
				organizeImportsInCompilationUnit((ICompilationUnit) elem, rootEdit);
			}
		}
	}

	public void organizeImportsInCompilationUnit(ICompilationUnit unit, WorkspaceEdit rootEdit) {
		try {
			InnovationContext context = new InnovationContext(unit, 0, unit.getBuffer().getLength() - 1);
			CUCorrectionProposal proposal = OrganizeImportsHandler.getOrganizeImportsProposal("OrganizeImports", CodeActionKind.SourceOrganizeImports, unit, IProposalRelevance.ORGANIZE_IMPORTS, context.getASTRoot(), false, false);
			if (proposal != null) {
				addWorkspaceEdit(unit, proposal, rootEdit);
			}
		} catch (CoreException e) {
			JavaLanguageServerPlugin.logException("Problem organize imports ", e);
		}
	}

	private void collectCompilationUnits(Object element, Collection<IJavaElement> result, String packagePrefix) {
		try {
			if (element instanceof IJavaElement elem) {
				if (elem.exists()) {
					switch (elem.getElementType()) {
						case IJavaElement.TYPE:
							if (elem.getParent().getElementType() == IJavaElement.COMPILATION_UNIT) {
								result.add(elem.getParent());
							}
							break;
						case IJavaElement.COMPILATION_UNIT:
							result.add(elem);
							break;
						case IJavaElement.IMPORT_CONTAINER:
							result.add(elem.getParent());
							break;
						case IJavaElement.PACKAGE_FRAGMENT:
							collectCompilationUnits((IPackageFragment) elem, result);
							break;
						case IJavaElement.PACKAGE_FRAGMENT_ROOT:
							collectCompilationUnits((IPackageFragmentRoot) elem, result, packagePrefix);
							break;
						case IJavaElement.JAVA_PROJECT:
							IPackageFragmentRoot[] roots = ((IJavaProject) elem).getPackageFragmentRoots();
							for (int k = 0; k < roots.length; k++) {
								collectCompilationUnits(roots[k], result, null);
							}
							break;
					}
				}
			}
		} catch (CoreException e) {
			JavaLanguageServerPlugin.logException("Problem collection compilation unit ", e);
		}
	}

	private void collectCompilationUnits(IPackageFragment pack, Collection<IJavaElement> result) throws JavaModelException {
		result.addAll(Arrays.asList(pack.getCompilationUnits()));
	}

	private void collectCompilationUnits(IPackageFragmentRoot root, Collection<IJavaElement> result, String prefix) throws JavaModelException {
		if (root.getKind() == IPackageFragmentRoot.K_SOURCE) {
			IJavaElement[] children = root.getChildren();
			for (int i = 0; i < children.length; i++) {
				IPackageFragment pack = (IPackageFragment) children[i];
				if (StringUtils.isBlank(prefix) || pack.getElementName().indexOf(prefix) >= 0) {
					collectCompilationUnits(pack, result);
				}
			}
		}
	}

	private void addWorkspaceEdit(ICompilationUnit cu, CUCorrectionProposal proposal, WorkspaceEdit rootEdit) throws CoreException {
		TextChange textChange = proposal.getTextChange();
		TextEdit edit = textChange.getEdit();
		TextEditConverter converter = new TextEditConverter(cu, edit);
		List<org.eclipse.lsp4j.TextEdit> edits = converter.convert();
		if (ChangeUtil.hasChanges(edits)) {
			rootEdit.getChanges().put(JDTUtils.toURI(cu), edits);
		}
	}
}
