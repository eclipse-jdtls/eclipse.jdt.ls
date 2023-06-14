/*******************************************************************************
 * Copyright (c) 2023 Red Hat Inc. and others.
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

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.jdt.core.IClassFile;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IMember;
import org.eclipse.jdt.core.IMethod;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.ITypeRoot;
import org.eclipse.jdt.internal.corext.util.MethodOverrideTester;
import org.eclipse.jdt.internal.corext.util.SuperTypeHierarchyCache;
import org.eclipse.jdt.ls.core.internal.JDTUtils;
import org.eclipse.jdt.ls.core.internal.JavaLanguageServerPlugin;
import org.eclipse.jdt.ls.core.internal.preferences.PreferenceManager;
import org.eclipse.lsp4j.Location;
import org.eclipse.lsp4j.TextDocumentPositionParams;

public class NavigateToDeclarationHandler {

	private final PreferenceManager preferenceManager;

	public NavigateToDeclarationHandler(PreferenceManager preferenceManager) {
		this.preferenceManager = preferenceManager;
	}

	public List<? extends Location> declaration(TextDocumentPositionParams position, IProgressMonitor monitor) {
		if (monitor.isCanceled()) {
			return Collections.emptyList();
		}
		ITypeRoot unit = null;
		try {
			boolean returnCompilationUnit = preferenceManager == null ? false : preferenceManager.isClientSupportsClassFileContent() && (preferenceManager.getPreferences().isIncludeDecompiledSources());
			unit = JDTUtils.resolveTypeRoot(position.getTextDocument().getUri(), returnCompilationUnit, monitor);
			Location location = null;
			if (unit != null && !monitor.isCanceled()) {
				location = computeDeclarationNavigation(unit, position.getPosition().getLine(), position.getPosition().getCharacter(), monitor);
			}
			return location == null || monitor.isCanceled() ? Collections.emptyList() : Arrays.asList(location);
		} finally {
			JDTUtils.discardClassFileWorkingCopy(unit);
		}

	}

	private Location computeDeclarationNavigation(ITypeRoot unit, int line, int column, IProgressMonitor monitor) {
		try {
			IJavaElement element = JDTUtils.findElementAtSelection(unit, line, column, this.preferenceManager, monitor);
			if (monitor.isCanceled() || element == null || element.getElementType() != IJavaElement.METHOD) {
				return null;
			}

			IMethod method = (IMethod) element;
			MethodOverrideTester tester = SuperTypeHierarchyCache.getMethodOverrideTester(method.getDeclaringType());

			IMethod methodDeclaration = tester.findDeclaringMethod(method, false);

			if (methodDeclaration == null) {
				return null;
			}

			ICompilationUnit compilationUnit = (ICompilationUnit) methodDeclaration.getAncestor(IJavaElement.COMPILATION_UNIT);
			IClassFile cf = (IClassFile) methodDeclaration.getAncestor(IJavaElement.CLASS_FILE);
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
				return JDTUtils.toLocation(methodDeclaration);
			}
			if (methodDeclaration instanceof IMember member && member.getClassFile() != null) {
				List<Location> locations = JDTUtils.searchDecompiledSources(element, cf, true, true, new NullProgressMonitor());
				if (!locations.isEmpty()) {
					return locations.get(0);
				}
				return JDTUtils.toLocation(member.getClassFile());
			}

		} catch (CoreException e) {
			JavaLanguageServerPlugin.logException("Problem computing declaration for" + unit.getElementName(), e);
		}

		return null;
	}
}