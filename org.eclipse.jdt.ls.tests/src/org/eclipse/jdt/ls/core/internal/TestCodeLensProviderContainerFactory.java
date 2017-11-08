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

import java.util.ArrayList;
import java.util.List;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IExecutableExtensionFactory;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.ls.core.internal.handlers.CodeLensProvider;
import org.eclipse.jdt.ls.core.internal.handlers.CodeLensProviderContainer;
import org.eclipse.jdt.ls.core.internal.preferences.PreferenceManager;
import org.eclipse.lsp4j.CodeLens;
import org.eclipse.lsp4j.Command;

/**
 * Test CodeLensProviderContainer Factory.
 *
 */
public class TestCodeLensProviderContainerFactory implements IExecutableExtensionFactory {

	private static class TestCodeLensProviderContainer implements CodeLensProviderContainer {

		public static TestCodeLensProviderContainer getInstance() {
			return new TestCodeLensProviderContainer();
		}

		/* (non-Javadoc)
		 * @see org.eclipse.jdt.ls.core.internal.handlers.CodeLensProviderContainer#getCodeLensProvider(java.lang.String, org.eclipse.jdt.ls.core.internal.preferences.PreferenceManager)
		 */
		@Override
		public CodeLensProvider getCodeLensProvider(String providerId, PreferenceManager pm) {
			return new TestCodeLensProvider(providerId, pm);
		}
	}

	private static class TestCodeLensProvider implements CodeLensProvider {

		private String type;
		private PreferenceManager pm;

		public TestCodeLensProvider(String providerId, PreferenceManager pm) {
			type = providerId;
			this.pm = pm;
		}

		/* (non-Javadoc)
		 * @see org.eclipse.jdt.ls.core.internal.handlers.CodeLensProvider#resolveCodeLens(org.eclipse.lsp4j.CodeLens, org.eclipse.core.runtime.IProgressMonitor)
		 */
		@Override
		public CodeLens resolveCodeLens(CodeLens lens, IProgressMonitor monitor) {
			if (monitor.isCanceled()) {
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
			return type;
		}

		/* (non-Javadoc)
		 * @see org.eclipse.jdt.ls.core.internal.handlers.CodeLensProvider#collectCodeLenses(org.eclipse.jdt.core.ICompilationUnit, org.eclipse.jdt.core.IJavaElement[], org.eclipse.core.runtime.IProgressMonitor)
		 */
		@Override
		public List<CodeLens> collectCodeLenses(ICompilationUnit unit, IProgressMonitor monitor) throws JavaModelException {
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

	}

	/* (non-Javadoc)
	 * @see org.eclipse.core.runtime.IExecutableExtensionFactory#create()
	 */
	@Override
	public Object create() throws CoreException {
		return TestCodeLensProviderContainer.getInstance();
	}

}
