
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
import org.eclipse.jdt.core.IClassFile;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IMember;
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
import org.eclipse.lsp4j.Location;
import org.eclipse.lsp4j.TextDocumentPositionParams;

public class NavigateToTypeDefinitionHandler {

	public NavigateToTypeDefinitionHandler() {
	}

	public List<? extends Location> typeDefinition(TextDocumentPositionParams position, IProgressMonitor monitor) {
		ITypeRoot unit = JDTUtils.resolveTypeRoot(position.getTextDocument().getUri());
		Location location = null;
		if (unit != null && !monitor.isCanceled()) {
			location = computeTypeDefinitionNavigation(unit, position.getPosition().getLine(), position.getPosition().getCharacter(), monitor);
		}
		return location == null ? null : Arrays.asList(location);
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
			if (coveringNode instanceof SimpleName) {
				IBinding resolvedBinding = ((SimpleName) coveringNode).resolveBinding();
				if (resolvedBinding != null) {
					ITypeBinding typeBinding = null;
					if (resolvedBinding instanceof IVariableBinding) {
						typeBinding = ((IVariableBinding) resolvedBinding).getType();
					} else if (resolvedBinding instanceof ITypeBinding) {
						typeBinding = (ITypeBinding) resolvedBinding;
					}
					if (typeBinding != null && typeBinding.getJavaElement() != null) {
						IJavaElement element = typeBinding.getJavaElement();
						ICompilationUnit compilationUnit = (ICompilationUnit) element.getAncestor(IJavaElement.COMPILATION_UNIT);
						IClassFile cf = (IClassFile) element.getAncestor(IJavaElement.CLASS_FILE);
						if (compilationUnit != null || (cf != null && cf.getSourceRange() != null)) {
							return JDTUtils.toLocation(element);
						}
						if (element instanceof IMember && ((IMember) element).getClassFile() != null) {
							return JDTUtils.toLocation(((IMember) element).getClassFile());
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
