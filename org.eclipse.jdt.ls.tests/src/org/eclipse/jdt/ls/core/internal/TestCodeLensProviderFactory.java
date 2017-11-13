/*******************************************************************************
 * Copyright (c) 2017 Microsoft Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Microsoft Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.jdt.ls.core.internal;

import static org.eclipse.jdt.ls.core.internal.handlers.MapFlattener.getBoolean;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IExecutableExtensionFactory;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.ITypeRoot;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.ls.core.internal.codelens.CodeLensProvider;
import org.eclipse.jdt.ls.core.internal.preferences.PreferenceManager;
import org.eclipse.jdt.ls.core.internal.preferences.Preferences;
import org.eclipse.lsp4j.CodeLens;
import org.eclipse.lsp4j.Command;

/**
 * Test CodeLensProvider Factory.
 *
 */
public class TestCodeLensProviderFactory implements IExecutableExtensionFactory {
	private static class TestCodeLensProvider implements CodeLensProvider {

		private String type;
		private PreferenceManager pm;

		public static final String CODE_LENS_ENABLED_KEY = "test.codelens.enabled";

		@Override
		public void setPreferencesManager(PreferenceManager pm) {
			this.pm = pm;
		}

		/* (non-Javadoc)
		 * @see org.eclipse.jdt.ls.core.internal.handlers.CodeLensProvider#resolveCodeLens(org.eclipse.lsp4j.CodeLens, org.eclipse.core.runtime.IProgressMonitor)
		 */
		@Override
		public CodeLens resolveCodeLens(CodeLens lens, IProgressMonitor monitor) {
			if (!isCodeLensEnabled() || monitor.isCanceled()) {
				return lens;
			}
			lens.setCommand(new Command(type, "ls.test." + type));
			return lens;
		}

		/* (non-Javadoc)
		 * @see org.eclipse.jdt.ls.core.internal.handlers.CodeLensProvider#getType()
		 */
		@Override
		public String getType() {
			return TestCodeLensProvider.class.getName();
		}

		/* (non-Javadoc)
		 * @see org.eclipse.jdt.ls.core.internal.handlers.CodeLensProvider#collectCodeLenses(org.eclipse.jdt.core.ICompilationUnit, org.eclipse.jdt.core.IJavaElement[], org.eclipse.core.runtime.IProgressMonitor)
		 */
		@Override
		public List<CodeLens> collectCodeLenses(ITypeRoot unit, IProgressMonitor monitor) throws JavaModelException {
			ArrayList<CodeLens> lenses = new ArrayList<>();
			IJavaElement[] elements = unit.getChildren();
			if (!this.pm.getPreferences().isCodeLensEnabled() || monitor.isCanceled()) {
				return lenses;
			}
			for (IJavaElement ele : elements) {
				if (ele.getElementType() == IJavaElement.TYPE && ((IType) ele).isClass()) {
					lenses.add(createCodeLens(ele, unit));
				}
			}
			return lenses;
		}

		private boolean isCodeLensEnabled() {
			Preferences prefs = this.pm.getPreferences();
			if (!prefs.isCodeLensEnabled()) {
				return false;
			}
			Map<String, Object> config = prefs.asMap();
			return getBoolean(config, CODE_LENS_ENABLED_KEY, true);

		}

	}

	/* (non-Javadoc)
	 * @see org.eclipse.core.runtime.IExecutableExtensionFactory#create()
	 */
	@Override
	public Object create() throws CoreException {
		return new TestCodeLensProvider();
	}

}
