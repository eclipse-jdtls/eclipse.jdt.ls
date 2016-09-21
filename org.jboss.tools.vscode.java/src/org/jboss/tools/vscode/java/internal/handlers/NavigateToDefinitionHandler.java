/*******************************************************************************
 * Copyright (c) 2016 Red Hat Inc. and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Red Hat Inc. - initial API and implementation
 *******************************************************************************/
package org.jboss.tools.vscode.java.internal.handlers;

import org.eclipse.jdt.core.IClassFile;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.ITypeRoot;
import org.eclipse.jdt.core.JavaModelException;
import org.jboss.tools.langs.Location;
import org.jboss.tools.langs.TextDocumentPositionParams;
import org.jboss.tools.langs.base.LSPMethods;
import org.jboss.tools.vscode.internal.ipc.CancelMonitor;
import org.jboss.tools.vscode.internal.ipc.RequestHandler;
import org.jboss.tools.vscode.java.internal.JDTUtils;
import org.jboss.tools.vscode.java.internal.JavaLanguageServerPlugin;

public class NavigateToDefinitionHandler implements RequestHandler<TextDocumentPositionParams, org.jboss.tools.langs.Location>{

	public NavigateToDefinitionHandler() {
	}

	@Override
	public boolean canHandle(String request) {
		return LSPMethods.DOCUMENT_DEFINITION.getMethod().equals(request);
	}

	private Location computeDefinitonNavigation(ITypeRoot unit, int line, int column) {
		try {
			IJavaElement[] elements = unit.codeSelect(JsonRpcHelpers.toOffset(unit.getBuffer(), line, column), 0);

			if (elements == null || elements.length != 1)
				return null;
			IJavaElement element = elements[0];
			ICompilationUnit compilationUnit = (ICompilationUnit) element
					.getAncestor(IJavaElement.COMPILATION_UNIT);
			IClassFile cf = (IClassFile) element.getAncestor(IJavaElement.CLASS_FILE);
			if (compilationUnit != null || (cf != null && cf.getSourceRange() != null)  ) {
				return JDTUtils.toLocation(element);
			}
			return null;

		} catch (JavaModelException e) {
			JavaLanguageServerPlugin.logException("Problem with codeSelect for" +  unit.getElementName(), e);
		}
		return null;
	}

	@Override
	public org.jboss.tools.langs.Location handle(TextDocumentPositionParams param, CancelMonitor cm) {
		ITypeRoot unit = JDTUtils.resolveTypeRoot(param.getTextDocument().getUri());

		return computeDefinitonNavigation(unit, param.getPosition().getLine().intValue(),
				param.getPosition().getCharacter().intValue());
	}

}
