
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
import org.eclipse.jdt.core.IField;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.ILocalVariable;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.IMember;
import org.eclipse.jdt.core.IMethod;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.ITypeRoot;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.Signature;
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
import org.eclipse.jdt.ls.core.internal.SearchUtils;
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
			if (offset < 0) {
				return null;
			}
			if (ast == null) {
				return computeTypeDefinitionWithoutAST(unit, line, column, monitor);
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
							Location location = SearchUtils.searchOtherSources(member);
							if (location != null) {
								return location;
							}
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

	/**
	 * Fallback for non-Java compilation units where CoreASTProvider cannot
	 * produce a Java AST. Uses codeSelect to resolve the element, then
	 * navigates to its type definition.
	 */
	private Location computeTypeDefinitionWithoutAST(ITypeRoot unit, int line, int column, IProgressMonitor monitor) {
		try {
			PreferenceManager preferenceManager = JavaLanguageServerPlugin.getPreferencesManager();
			IJavaElement element = JDTUtils.findElementAtSelection(unit, line, column, preferenceManager, monitor);
			if (element == null || monitor.isCanceled()) {
				return null;
			}
			IType targetType = resolveElementType(element, unit.getJavaProject());
			if (targetType != null) {
				return NavigateToDefinitionHandler.computeDefinitionNavigation(targetType, unit.getJavaProject());
			}
			if (!JavaCore.isJavaLikeFileName(unit.getElementName())) {
				return NavigateToDefinitionHandler.computeDefinitionNavigation(element, unit.getJavaProject());
			}
		} catch (CoreException e) {
			JavaLanguageServerPlugin.logException("Problem computing typeDefinition for " + unit.getElementName(), e);
		}
		return null;
	}

	private static IType resolveElementType(IJavaElement element, IJavaProject project) throws JavaModelException {
		if (element instanceof IType type) {
			return type;
		}
		String typeSignature = null;
		IType declaringType = null;
		if (element instanceof IMethod method) {
			typeSignature = method.getReturnType();
			declaringType = method.getDeclaringType();
		} else if (element instanceof IField field) {
			typeSignature = field.getTypeSignature();
			declaringType = field.getDeclaringType();
		} else if (element instanceof ILocalVariable variable) {
			typeSignature = variable.getTypeSignature();
			IJavaElement parent = variable.getParent();
			if (parent instanceof IMember member) {
				declaringType = member.getDeclaringType();
			}
		}
		if (typeSignature == null) {
			return null;
		}
		String typeName = Signature.toString(typeSignature);
		if (declaringType != null) {
			String[][] resolved = declaringType.resolveType(typeName);
			if (resolved != null && resolved.length > 0) {
				String fqn = resolved[0][0].isEmpty() ? resolved[0][1] : resolved[0][0] + "." + resolved[0][1];
				return project.findType(fqn);
			}
		}
		return project.findType(typeName);
	}
}
