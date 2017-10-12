/*******************************************************************************
 * Copyright (c) 2016-2017 Red Hat Inc. and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Red Hat Inc. - initial API and implementation
 *******************************************************************************/
package org.eclipse.jdt.ls.core.internal.handlers;

import java.util.Arrays;
import java.util.List;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.jdt.core.IClassFile;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IMember;
import org.eclipse.jdt.core.ITypeRoot;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.ls.core.internal.JDTUtils;
import org.eclipse.jdt.ls.core.internal.JavaLanguageServerPlugin;
import org.eclipse.jdt.ls.core.internal.preferences.PreferenceManager;
import org.eclipse.lsp4j.Location;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.TextDocumentPositionParams;

public class NavigateToDefinitionHandler {

	private final PreferenceManager preferenceManager;

	public NavigateToDefinitionHandler(PreferenceManager preferenceManager) {
		this.preferenceManager = preferenceManager;
	}

	public List<? extends Location> definition(TextDocumentPositionParams position, IProgressMonitor monitor) {
		ITypeRoot unit = JDTUtils.resolveTypeRoot(position.getTextDocument().getUri());
		Location location = null;
		if (unit != null && !monitor.isCanceled()) {
			location = computeDefinitionNavigation(unit, position.getPosition().getLine(),
					position.getPosition().getCharacter(), monitor);
		}
		if (location == null) {
			location = new Location();
			location.setRange(new Range());
		}
		return Arrays.asList(location);
	}

	private Location computeDefinitionNavigation(ITypeRoot unit, int line, int column, IProgressMonitor monitor) {
		try {
			IJavaElement element = JDTUtils.findElementAtSelection(unit, line, column, this.preferenceManager, monitor);
			if (element == null) {
				return null;
			}
			ICompilationUnit compilationUnit = (ICompilationUnit) element.getAncestor(IJavaElement.COMPILATION_UNIT);
			IClassFile cf = (IClassFile) element.getAncestor(IJavaElement.CLASS_FILE);
			if (compilationUnit != null || (cf != null && cf.getSourceRange() != null)  ) {
				return JDTUtils.toLocation(element);
			}
			if (element instanceof IMember && ((IMember) element).getClassFile() != null) {
				return JDTUtils.toLocation(((IMember) element).getClassFile());
			}
		} catch (JavaModelException e) {
			JavaLanguageServerPlugin.logException("Problem computing definition for" +  unit.getElementName(), e);
		}
		return null;
	}


}
