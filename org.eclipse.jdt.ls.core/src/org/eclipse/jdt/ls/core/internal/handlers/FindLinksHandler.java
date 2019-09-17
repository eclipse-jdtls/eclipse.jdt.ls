/*******************************************************************************
 * Copyright (c) 2019 Microsoft Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Microsoft Corporation - initial API and implementation
 *******************************************************************************/

package org.eclipse.jdt.ls.core.internal.handlers;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IMethod;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.ITypeHierarchy;
import org.eclipse.jdt.core.ITypeRoot;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.internal.corext.util.MethodOverrideTester;
import org.eclipse.jdt.ls.core.internal.JDTUtils;
import org.eclipse.jdt.ls.core.internal.JavaLanguageServerPlugin;
import org.eclipse.jdt.ls.core.internal.preferences.PreferenceManager;
import org.eclipse.lsp4j.Location;
import org.eclipse.lsp4j.TextDocumentPositionParams;

public class FindLinksHandler {

	public static List<? extends Location> findLinks(String linkType, TextDocumentPositionParams position, IProgressMonitor monitor) {
		ITypeRoot unit = JDTUtils.resolveTypeRoot(position.getTextDocument().getUri());
		if (unit != null && !monitor.isCanceled()) {
			PreferenceManager preferenceManager = JavaLanguageServerPlugin.getInstance().getPreferencesManager();
			try {
				IJavaElement element = JDTUtils.findElementAtSelection(unit, position.getPosition().getLine(), position.getPosition().getCharacter(), preferenceManager, monitor);
				if (Objects.equals(linkType, "superImplementation")) {
					IMethod overriddenMethod = findOverriddenMethod(element, monitor);
					if (overriddenMethod != null) {
						Location location = NavigateToDefinitionHandler.computeDefinitionNavigation(overriddenMethod, element.getJavaProject());
						if (location != null) {
							String declaringTypeName = overriddenMethod.getDeclaringType().getFullyQualifiedName();
							String methodName = overriddenMethod.getElementName();
							String displayName = declaringTypeName + "." + methodName;
							return Collections.singletonList(new LinkLocation(displayName, "method", location));
						}
					}
				}
			} catch (JavaModelException e) {
				// do nothing
			}
		}

		return Collections.emptyList();
	}

	public static IMethod findOverriddenMethod(IJavaElement element, IProgressMonitor monitor) throws JavaModelException {
		if (!(element instanceof IMethod)) {
			return null;
		}

		IMethod method = (IMethod) element;
		IType type = method.getDeclaringType();
		if (type == null || type.isInterface() || method.isConstructor()) {
			return null;
		}

		ITypeHierarchy hierarchy = type.newSupertypeHierarchy(monitor);
		MethodOverrideTester tester = new MethodOverrideTester(type, hierarchy);
		IMethod found = tester.findOverriddenMethod(method, true);
		if (found != null && !found.equals(method)) {
			return found;
		}

		return null;
	}

	public static class LinkLocation extends Location {
		public String displayName;
		public String kind;

		public LinkLocation(String displayName, String kind, Location location) {
			super(location.getUri(), location.getRange());
			this.displayName = displayName;
			this.kind = kind;
		}
	}

	public static class FindLinksParams {
		// Supported link types: superImplementation
		public String type;
		public TextDocumentPositionParams position;
	}
}
