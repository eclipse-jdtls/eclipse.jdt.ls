/*******************************************************************************
 * Copyright (c) 2016-2017 Red Hat Inc. and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *     Red Hat Inc. - initial API and implementation
 *******************************************************************************/
package org.eclipse.jdt.ls.core.internal.handlers;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.apache.commons.lang3.StringUtils;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.jdt.core.IClassFile;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IMember;
import org.eclipse.jdt.core.ITypeRoot;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.BreakStatement;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.ContinueStatement;
import org.eclipse.jdt.core.dom.DoStatement;
import org.eclipse.jdt.core.dom.EnhancedForStatement;
import org.eclipse.jdt.core.dom.ForStatement;
import org.eclipse.jdt.core.dom.Initializer;
import org.eclipse.jdt.core.dom.LabeledStatement;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.NodeFinder;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.core.dom.SwitchExpression;
import org.eclipse.jdt.core.dom.SwitchStatement;
import org.eclipse.jdt.core.dom.WhileStatement;
import org.eclipse.jdt.core.manipulation.SharedASTProviderCore;
import org.eclipse.jdt.internal.corext.dom.TokenScanner;
import org.eclipse.jdt.ls.core.internal.JDTUtils;
import org.eclipse.jdt.ls.core.internal.JavaLanguageServerPlugin;
import org.eclipse.jdt.ls.core.internal.managers.ProjectsManager;
import org.eclipse.jdt.ls.core.internal.preferences.PreferenceManager;
import org.eclipse.lsp4j.Location;
import org.eclipse.lsp4j.TextDocumentPositionParams;

public class NavigateToDefinitionHandler {

	private final PreferenceManager preferenceManager;

	public NavigateToDefinitionHandler(PreferenceManager preferenceManager) {
		this.preferenceManager = preferenceManager;
	}

	public List<? extends Location> definition(TextDocumentPositionParams position, IProgressMonitor monitor) {
		if (monitor.isCanceled()) {
			return Collections.emptyList();
		}
		ITypeRoot unit = null;
		try {
			boolean returnCompilationUnit = preferenceManager == null ? false : preferenceManager.isClientSupportsClassFileContent() && (preferenceManager.getPreferences().isIncludeDecompiledSources());
			unit = JDTUtils.resolveTypeRoot(position.getTextDocument().getUri(), returnCompilationUnit, monitor);
			Location location = null;
			if (unit != null && !monitor.isCanceled()) {
				location = computeDefinitionNavigation(unit, position.getPosition().getLine(), position.getPosition().getCharacter(), monitor);
			}
			return location == null || monitor.isCanceled() ? Collections.emptyList() : Arrays.asList(location);
		} finally {
			JDTUtils.discardClassFileWorkingCopy(unit);
		}
	}

	private Location computeDefinitionNavigation(ITypeRoot unit, int line, int column, IProgressMonitor monitor) {
		try {
			IJavaElement element = JDTUtils.findElementAtSelection(unit, line, column, this.preferenceManager, monitor);
			if (monitor.isCanceled()) {
				return null;
			}
			if (element == null) {
				return computeBreakContinue(unit, line, column);
			}
			return computeDefinitionNavigation(element, unit.getJavaProject());
		} catch (CoreException e) {
			JavaLanguageServerPlugin.logException("Problem computing definition for" + unit.getElementName(), e);
		}

		return null;
	}

	private Location computeBreakContinue(ITypeRoot typeRoot, int line, int column) throws CoreException {
		int offset = JsonRpcHelpers.toOffset(typeRoot.getBuffer(), line, column);
		if (offset >= 0) {
			CompilationUnit unit = SharedASTProviderCore.getAST(typeRoot, SharedASTProviderCore.WAIT_YES, null);
			if (unit == null) {
				return null;
			}
			ASTNode selectedNode = NodeFinder.perform(unit, offset, 0);
			ASTNode node = null;
			SimpleName label = null;
			if (selectedNode instanceof BreakStatement breakStatement) {
				node = selectedNode;
				label = breakStatement.getLabel();
			} else if (selectedNode instanceof ContinueStatement continueStatement) {
				node = selectedNode;
				label = continueStatement.getLabel();
			} else if (selectedNode instanceof SimpleName && selectedNode.getParent() instanceof BreakStatement breakStatement) {
				node = selectedNode.getParent();
				label = breakStatement.getLabel();
			} else if (selectedNode instanceof SimpleName && selectedNode.getParent() instanceof ContinueStatement continueStatement) {
				node = selectedNode.getParent();
				label = continueStatement.getLabel();
			}
			if (node != null) {
				ASTNode parent = node.getParent();
				ASTNode target = null;
				while (parent != null) {
					if (parent instanceof MethodDeclaration || parent instanceof Initializer) {
						break;
					}
					if (label == null) {
						if (parent instanceof ForStatement || parent instanceof EnhancedForStatement || parent instanceof WhileStatement || parent instanceof DoStatement) {
							target = parent;
							break;
						}
						if (node instanceof BreakStatement) {
							if (parent instanceof SwitchStatement || parent instanceof SwitchExpression) {
								target = parent;
								break;
							}
						}
						if (node instanceof LabeledStatement) {
							target = parent;
							break;
						}
					} else if (LabeledStatement.class.isInstance(parent)) {
						LabeledStatement ls = (LabeledStatement) parent;
						if (ls.getLabel().getIdentifier().equals(label.getIdentifier())) {
							target = ls;
							break;
						}
					}
					parent = parent.getParent();
				}
				if (target != null) {
					int start = target.getStartPosition();
					int end = new TokenScanner(unit.getTypeRoot()).getNextEndOffset(node.getStartPosition(), true) - start;
					if (start >= 0 && end >= 0) {
						return JDTUtils.toLocation((ICompilationUnit) typeRoot, start, end);
					}
				}
			}
		}
		return null;
	}

	public static Location computeDefinitionNavigation(IJavaElement element, IJavaProject javaProject) throws JavaModelException {
		if (element == null) {
			return null;
		}

		ICompilationUnit compilationUnit = (ICompilationUnit) element.getAncestor(IJavaElement.COMPILATION_UNIT);
		IClassFile cf = (IClassFile) element.getAncestor(IJavaElement.CLASS_FILE);
		if (compilationUnit != null || (cf != null && cf.getSourceRange() != null)) {
			if (compilationUnit != null && compilationUnit.getResource() != null && !compilationUnit.getResource().exists()) {
				IClassFile classFile = JDTUtils.getClassFile(compilationUnit);
				if (classFile != null) {
					String uriString = JDTUtils.toUri(classFile);
					Location location = fixLocation(element, JDTUtils.toLocation(element), compilationUnit.getJavaProject());
					location.setUri(uriString);
					return location;
				}
				return null;
			}
			return fixLocation(element, JDTUtils.toLocation(element), javaProject);
		}

		if (element instanceof IMember member && member.getClassFile() != null) {
			List<Location> locations = JDTUtils.searchDecompiledSources(element, cf, true, true, new NullProgressMonitor());
			if (!locations.isEmpty()) {
				return fixLocation(element, locations.get(0), javaProject);
			}
			return fixLocation(element, JDTUtils.toLocation(member.getClassFile()), javaProject);
		}

		return null;
	}

	private static Location fixLocation(IJavaElement element, Location location, IJavaProject javaProject) {
		if (location == null) {
			return null;
		}
		if (!javaProject.equals(element.getJavaProject()) && element.getJavaProject().getProject().getName().equals(ProjectsManager.DEFAULT_PROJECT_NAME)) {
			// see issue at: https://github.com/eclipse/eclipse.jdt.ls/issues/842 and https://bugs.eclipse.org/bugs/show_bug.cgi?id=541573
			// for jdk classes, jdt will reuse the java model by altering project to share the model between projects
			// so that sometimes the project for `element` is default project and the project is different from the project for `unit`
			// this fix is to replace the project name with non-default ones since default project should be transparent to users.
			if (location.getUri().contains(ProjectsManager.DEFAULT_PROJECT_NAME)) {
				String patched = StringUtils.replaceOnce(location.getUri(), ProjectsManager.DEFAULT_PROJECT_NAME, javaProject.getProject().getName());
				try {
					IClassFile cf = (IClassFile) JavaCore.create(JDTUtils.toURI(patched).getQuery());
					if (cf != null && cf.exists()) {
						location.setUri(patched);
					}
				} catch (Exception ex) {

				}
			}
		}
		return location;
	}


}
