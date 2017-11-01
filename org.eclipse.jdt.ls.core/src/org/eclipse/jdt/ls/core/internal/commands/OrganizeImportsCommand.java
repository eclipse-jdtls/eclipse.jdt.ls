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

import java.io.File;
import java.net.URI;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;

import org.apache.commons.lang3.StringUtils;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IPackageFragment;
import org.eclipse.jdt.core.IPackageFragmentRoot;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.ls.core.internal.JDTUtils;
import org.eclipse.jdt.ls.core.internal.JavaLanguageServerPlugin;
import org.eclipse.jdt.ls.core.internal.ResourceUtils;
import org.eclipse.jdt.ls.core.internal.TextEditConverter;
import org.eclipse.jdt.ls.core.internal.corext.codemanipulation.OrganizeImportsOperation;
import org.eclipse.jdt.ls.core.internal.corrections.InnovationContext;
import org.eclipse.jdt.ls.core.internal.corrections.proposals.CUCorrectionProposal;
import org.eclipse.jdt.ls.core.internal.corrections.proposals.IProposalRelevance;
import org.eclipse.jface.text.IDocument;
import org.eclipse.lsp4j.WorkspaceEdit;
import org.eclipse.ltk.core.refactoring.TextChange;
import org.eclipse.text.edits.TextEdit;

public class OrganizeImportsCommand {

	public static Object organizeImports(List<Object> arguments) throws CoreException {
		if (arguments == null || arguments.isEmpty()) {
			return new WorkspaceEdit();
		}

		Object arg1 = arguments.get(0);
		if (arg1 instanceof String) {
			String fileUri = (String) arg1;
			OrganizeImportsCommand command = new OrganizeImportsCommand();

			IPath rootPath = ResourceUtils.filePathFromURI(fileUri);
			IProject[] projects = ResourcesPlugin.getWorkspace().getRoot().getProjects();
			boolean selectProjectRoot = false;
			IProject targetProj = null;
			if (rootPath != null && projects.length > 0) {
				for (IProject proj : projects) {
					String projectLocation = proj.getLocation().toString().toLowerCase();
					String selectedPath = rootPath.toString().toLowerCase();
					if (!StringUtils.isBlank(selectedPath) && !StringUtils.isBlank(projectLocation)) {
						if (selectedPath.startsWith(projectLocation)) {
							targetProj = proj;
							if (projectLocation.equals(selectedPath)) {
								selectProjectRoot = true;
							}
							break;
						}

					}
				}
			}
			// Project root URI:
			if (targetProj != null && selectProjectRoot) {
				return command.organizeImportsInProject(targetProj);
			}

			URI uri = URI.create(fileUri);
			File file = new File(uri.getPath());
			boolean exists = file.exists();
			if (!exists) {
				return new WorkspaceEdit();
			}
			if (file.isDirectory()) {
				return command.organizeImportsInDirectory(fileUri, targetProj);
			} else {
				return command.organizeImportsInFile(fileUri);
			}
		}
		return new WorkspaceEdit();
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
			CUCorrectionProposal proposal = new CUCorrectionProposal("OrganizeImports", unit, IProposalRelevance.ORGANIZE_IMPORTS) {
				@Override
				protected void addEdits(IDocument document, TextEdit editRoot) throws CoreException {
					CompilationUnit astRoot = context.getASTRoot();
					OrganizeImportsOperation op = new OrganizeImportsOperation(unit, astRoot, true, false, true, null);
					editRoot.addChild(op.createTextEdit(null));
				}
			};

			addWorkspaceEdit(unit, proposal, rootEdit);
		} catch (CoreException e) {
			JavaLanguageServerPlugin.logException("Problem organize imports ", e);
		}
	}

	private void collectCompilationUnits(Object element, Collection<IJavaElement> result, String packagePrefix) {
		try {
			if (element instanceof IJavaElement) {
				IJavaElement elem = (IJavaElement) element;
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
		rootEdit.getChanges().put(JDTUtils.toURI(cu), converter.convert());
	}
}
