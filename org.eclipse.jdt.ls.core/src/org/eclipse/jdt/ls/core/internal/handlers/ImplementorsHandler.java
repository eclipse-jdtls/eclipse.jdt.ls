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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.ITypeRoot;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.ls.core.internal.JDTUtils;
import org.eclipse.jdt.ls.core.internal.JavaLanguageServerPlugin;
import org.eclipse.jdt.ls.core.internal.ResourceUtils;
import org.eclipse.jdt.ls.core.internal.hover.JavaElementLabels;
import org.eclipse.lsp4j.Location;
import org.eclipse.lsp4j.SymbolInformation;
import org.eclipse.lsp4j.SymbolKind;
import org.eclipse.lsp4j.TextDocumentPositionParams;

public class ImplementorsHandler {

	public List<? extends SymbolInformation> implementors(TextDocumentPositionParams params, IProgressMonitor monitor) {
		ITypeRoot unit = JDTUtils.resolveTypeRoot(params.getTextDocument().getUri());
		if (unit != null) {
			try {
				IJavaElement element = JDTUtils.findElementAtSelection(unit, params.getPosition().getLine(), params.getPosition().getCharacter());
				if (element instanceof IType) {
					return findImplementations((IType) element, monitor);
				}
			} catch (JavaModelException e) {
				JavaLanguageServerPlugin.logException("Problem resolving implementors", e);
			}
		}
		return Collections.emptyList();
	}

	private List<SymbolInformation> findImplementations(IType type, IProgressMonitor monitor) throws JavaModelException {
		IType[] results = type.newTypeHierarchy(monitor).getAllSubtypes(type);
		final List<SymbolInformation> symbols = new ArrayList<>();
		for (IType t : results) {
			ICompilationUnit compilationUnit = (ICompilationUnit) t.getAncestor(IJavaElement.COMPILATION_UNIT);
			if (compilationUnit == null) {
				continue;
			}
			Location location = JDTUtils.toLocation(t);
			if (location != null) {
				SymbolInformation si = new SymbolInformation();
				String name = JavaElementLabels.getElementLabel(t, JavaElementLabels.ALL_DEFAULT);
				si.setName(name == null ? t.getElementName() : name);
				si.setKind(t.isInterface() ? SymbolKind.Interface : SymbolKind.Class);
				if (t.getParent() != null) {
					si.setContainerName(t.getParent().getElementName());
				}
				location.setUri(ResourceUtils.toClientUri(location.getUri()));
				si.setLocation(location);
				if (!symbols.contains(si)) {
					symbols.add(si);
				}
			}
		}
		return symbols;
	}

}