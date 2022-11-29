
/*******************************************************************************
* Copyright (c) 2018 Microsoft Corporation and others.
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

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.jdt.core.IClassFile;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IMember;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.ITypeRoot;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.IBinding;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.IVariableBinding;
import org.eclipse.jdt.core.dom.NodeFinder;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.core.manipulation.CoreASTProvider;
import org.eclipse.jdt.ls.core.internal.JDTUtils;
import org.eclipse.jdt.ls.core.internal.JavaLanguageServerPlugin;
import org.eclipse.jdt.ls.core.internal.preferences.PreferenceManager;
import org.eclipse.lsp4j.Location;
import org.eclipse.lsp4j.TextDocumentPositionParams;

public class NavigateToTypeDefinitionHandler {

	public NavigateToTypeDefinitionHandler() {
	}

	public List<? extends Location> typeDefinition(TextDocumentPositionParams position, IProgressMonitor monitor) {
		ITypeRoot unit = null;
		try {
			PreferenceManager preferenceManager = JavaLanguageServerPlugin.getPreferencesManager();
			boolean returnCompilationUnit = preferenceManager == null ? false : preferenceManager.isClientSupportsClassFileContent() && (preferenceManager.getPreferences().isIncludeDecompiledSources());
			unit = JDTUtils.resolveTypeRoot(position.getTextDocument().getUri(), returnCompilationUnit, monitor);
			Location location = null;
			if (unit != null && !monitor.isCanceled()) {
				location = computeTypeDefinitionNavigation(unit, position.getPosition().getLine(), position.getPosition().getCharacter(), monitor);
			}
			return location == null ? null : Arrays.asList(location);
		} finally {
			JDTUtils.discardClassFileWorkingCopy(unit);
		}
	}

	private Location computeTypeDefinitionNavigation(ITypeRoot unit, int line, int column, IProgressMonitor monitor) {
		try {
			CompilationUnit ast = CoreASTProvider.getInstance().getAST(unit, CoreASTProvider.WAIT_YES, monitor);
			int offset = JsonRpcHelpers.toOffset(unit.getBuffer(), line, column);
			if (ast == null || offset < 0) {
				return null;
			}
			NodeFinder finder = new NodeFinder(ast, offset, 0);
			ASTNode coveringNode = finder.getCoveringNode();
			if (coveringNode instanceof SimpleName name) {
				IBinding resolvedBinding = name.resolveBinding();
				if (resolvedBinding != null) {
					ITypeBinding typeBinding = null;
					if (resolvedBinding instanceof IVariableBinding variableBinding) {
						typeBinding = variableBinding.getType();
					} else if (resolvedBinding instanceof ITypeBinding resolvedTypeBinding) {
						typeBinding = resolvedTypeBinding;
					}
					if (typeBinding != null && typeBinding.getJavaElement() != null) {
						IJavaElement element = typeBinding.getJavaElement();
						ICompilationUnit compilationUnit = (ICompilationUnit) element.getAncestor(IJavaElement.COMPILATION_UNIT);
						IClassFile cf = (IClassFile) element.getAncestor(IJavaElement.CLASS_FILE);
						if (compilationUnit != null || (cf != null && cf.getSourceRange() != null)) {
							if (compilationUnit != null && compilationUnit.getResource() != null && !compilationUnit.getResource().exists()) {
								String fqn = compilationUnit.findPrimaryType().getFullyQualifiedName();
								IType type = compilationUnit.getJavaProject().findType(fqn);
								if (type.getClassFile() != null) {
									String uriString = JDTUtils.toUri(type.getClassFile());
									Location location = JDTUtils.toLocation(element);
									location.setUri(uriString);
									return location;
								}
								return null;
							}
							return JDTUtils.toLocation(element);
						}
						if (element instanceof IMember member && member.getClassFile() != null) {
							List<Location> locations = JDTUtils.searchDecompiledSources(element, cf, true, true, new NullProgressMonitor());
							if (!locations.isEmpty()) {
								return locations.get(0);
							}
							return JDTUtils.toLocation(member.getClassFile());
						}
					}
				}
			}
		} catch (CoreException e) {
			JavaLanguageServerPlugin.logException("Problem computing typeDefinition for" + unit.getElementName(), e);
		}
		return null;
	}
}
