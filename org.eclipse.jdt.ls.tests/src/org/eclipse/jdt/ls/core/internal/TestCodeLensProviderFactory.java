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

import java.util.Map;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IExecutableExtensionFactory;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.ls.core.internal.codelens.CodeLensContext;
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
		 * @see org.eclipse.jdt.ls.core.internal.codelens.CodeLensProvider#visit(org.eclipse.jdt.core.IType, org.eclipse.jdt.ls.core.internal.codelens.CodeLensProviderContext, org.eclipse.core.runtime.IProgressMonitor)
		 */
		@Override
		public int visit(IType type, CodeLensContext context, IProgressMonitor monitor) throws JavaModelException {
			if (!isCodeLensEnabled() || !type.isClass()) {
				return 0;
			}

			CodeLens lens = createCodeLens(type, context.getRoot());
			context.addCodeLens(lens);
			return 1;
		}

		private boolean isCodeLensEnabled() {
			Preferences prefs = this.pm.getPreferences();
			if (!prefs.isCodeLensEnabled()) {
				return false;
			}
			Map<String, Object> config = prefs.asMap();
			if (config == null) {
				return true;
			}
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
