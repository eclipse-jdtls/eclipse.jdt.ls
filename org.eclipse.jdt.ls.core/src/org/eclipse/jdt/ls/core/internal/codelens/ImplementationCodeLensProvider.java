/*******************************************************************************
 * Copyright (c) 2017 Red Hat Inc. and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Red Hat Inc. - initial API and implementation
 *******************************************************************************/
package org.eclipse.jdt.ls.core.internal.codelens;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.jdt.core.Flags;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.ITypeRoot;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.ls.core.internal.JDTUtils;
import org.eclipse.jdt.ls.core.internal.JavaLanguageServerPlugin;
import org.eclipse.jdt.ls.core.internal.preferences.PreferenceManager;
import org.eclipse.lsp4j.CodeLens;
import org.eclipse.lsp4j.Command;
import org.eclipse.lsp4j.Location;
import org.eclipse.lsp4j.Position;

/**
 * Implementation CodeLens Provider
 *
 */
public class ImplementationCodeLensProvider implements CodeLensProvider {
	private static final String JAVA_SHOW_IMPLEMENTATIONS_COMMAND = "java.show.implementations";
	private static final String IMPLEMENTATION_TYPE = "implementations";

	private PreferenceManager preferenceManager;

	@Override
	public void setPreferencesManager(PreferenceManager pm) {
		this.preferenceManager = pm;
	}

	/* (non-Javadoc)
	 * @see org.eclipse.jdt.ls.core.internal.handlers.CodeLensProvider#resolveCodeLens(org.eclipse.lsp4j.CodeLens, org.eclipse.core.runtime.IProgressMonitor)
	 */
	@Override
	public CodeLens resolveCodeLens(CodeLens lens, IProgressMonitor monitor) {
		if (lens == null) {
			return null;
		}
		//Note that codelens resolution is honored if the request was emitted
		//before disabling codelenses in the preferences, else invalid codeLenses
		//(i.e. having no commands) would be returned.
		if (!couldHandle(lens)) {
			return lens;
		}
		Position position = getCodeLensStartPos(lens);
		String uri = getCodeLensUri(lens);
		String label = "implementation";
		String command = JAVA_SHOW_IMPLEMENTATIONS_COMMAND;
		List<Location> locations = null;
		try {
			ITypeRoot typeRoot = JDTUtils.resolveTypeRoot(uri);
			if (typeRoot != null) {
				IJavaElement element = JDTUtils.findElementAtSelection(typeRoot, position.getLine(), position.getCharacter(), this.preferenceManager, monitor);
				if (element instanceof IType) {
					try {
						locations = findImplementations((IType) element, monitor);
					} catch (CoreException e) {
						JavaLanguageServerPlugin.logException(e.getMessage(), e);
					}
				}
			}
		} catch (CoreException e) {
			JavaLanguageServerPlugin.logException("Problem resolving code lens", e);
		}
		if (locations == null) {
			locations = Collections.emptyList();
		}
		int size = locations.size();
		Command c = new Command(size + " " + label + ((size == 1) ? "" : "s"), command, Arrays.asList(uri, position, locations));
		lens.setCommand(c);
		return lens;
	}

	/* (non-Javadoc)
	 * @see org.eclipse.jdt.ls.core.internal.handlers.CodeLensProvider#getType()
	 */
	@Override
	public String getType() {
		return IMPLEMENTATION_TYPE;
	}

	/* (non-Javadoc)
	 * @see org.eclipse.jdt.ls.core.internal.codelens.CodeLensProvider#visit(org.eclipse.jdt.core.IType, org.eclipse.jdt.ls.core.internal.codelens.CodeLensProviderContext, org.eclipse.core.runtime.IProgressMonitor)
	 */
	@Override
	public int visit(IType type, CodeLensContext context, IProgressMonitor monitor) throws JavaModelException {
		if (!preferenceManager.getPreferences().isImplementationsCodeLensEnabled()) {
			return 0;
		}

		if (type.isInterface() || Flags.isAbstract(type.getFlags())) {
			CodeLens lens = createCodeLens(type, context.getRoot());
			context.addCodeLens(lens);
			return 1;
		}
		return 0;
	}

	private List<Location> findImplementations(IType type, IProgressMonitor monitor) throws JavaModelException {
		IType[] results = type.newTypeHierarchy(monitor).getAllSubtypes(type);
		final List<Location> result = new ArrayList<>();
		for (IType t : results) {
			ICompilationUnit compilationUnit = (ICompilationUnit) t.getAncestor(IJavaElement.COMPILATION_UNIT);
			if (compilationUnit == null) {
				continue;
			}
			Location location = JDTUtils.toLocation(t);
			result.add(location);
		}
		return result;
	}

}
