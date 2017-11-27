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
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IConfigurationElement;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.Platform;
import org.eclipse.jdt.core.IClassFile;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IMethod;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.ITypeRoot;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.ls.core.internal.JDTUtils;
import org.eclipse.jdt.ls.core.internal.JavaLanguageServerPlugin;
import org.eclipse.jdt.ls.core.internal.codelens.CodeLensContext;
import org.eclipse.jdt.ls.core.internal.codelens.CodeLensProvider;
import org.eclipse.jdt.ls.core.internal.preferences.PreferenceManager;
import org.eclipse.lsp4j.CodeLens;

public class CodeLensHandler {
	private final int maxCodeLensCount = 4;
	private final PreferenceManager preferenceManager;
	private List<CodeLensProvider> codeLensProviders;

	public CodeLensHandler(PreferenceManager preferenceManager) {
		this.preferenceManager = preferenceManager;
		codeLensProviders = new ArrayList<>();
		codeLensProviders.addAll(getCodeLensProviders());
		for (CodeLensProvider p : codeLensProviders) {
			p.setPreferencesManager(this.preferenceManager);
		}
	}

	@SuppressWarnings("unchecked")
	public CodeLens resolve(CodeLens lens, IProgressMonitor monitor) {
		if (lens == null) {
			return null;
		}
		for (CodeLensProvider provider : codeLensProviders) {
			if (monitor.isCanceled()) {
				return lens;
			}
			if (provider.couldHandle(lens)) {
				lens = provider.resolveCodeLens(lens, monitor);
			}
		}
		return lens;
	}

	public List<CodeLens> getCodeLensSymbols(String uri, IProgressMonitor monitor) {
		if (!preferenceManager.getPreferences().isCodeLensEnabled()) {
			return Collections.emptyList();
		}
		final ICompilationUnit unit = JDTUtils.resolveCompilationUnit(uri);
		IClassFile classFile = null;
		if (unit == null) {
			classFile = JDTUtils.resolveClassFile(uri);
			if (classFile == null) {
				return Collections.emptyList();
			}
		} else {
			if (!unit.getResource().exists() || monitor.isCanceled()) {
				return Collections.emptyList();
			}
		}
		try {
			ITypeRoot typeRoot = unit != null ? unit : classFile;
			IJavaElement[] elements = typeRoot.getChildren();
			CodeLensContext context = new CodeLensContext(typeRoot);
			collectCodeLenses(context, elements, monitor);
			if (monitor.isCanceled()) {
				context.clearCodeLenses();
			}
			return context.getCodeLenses();
		} catch (JavaModelException e) {
			JavaLanguageServerPlugin.logException("Problem getting code lenses for" + unit.getElementName(), e);
		}
		return Collections.emptyList();
	}

	private void collectCodeLenses(CodeLensContext context, IJavaElement[] elements, IProgressMonitor monitor) throws JavaModelException {
		for (IJavaElement element : elements) {
			if (monitor.isCanceled()) {
				return;
			}
			if (element.getElementType() == IJavaElement.TYPE) {
				collectCodeLenses(context, ((IType) element).getChildren(), monitor);
			} else if (element.getElementType() != IJavaElement.METHOD || JDTUtils.isHiddenGeneratedElement(element)) {
				continue;
			}

			int count = 0;
			for (CodeLensProvider provider : codeLensProviders) {
				if (element instanceof IType) {
					count += provider.visit((IType) element, context, monitor);
				} else {
					count += provider.visit((IMethod) element, context, monitor);
				}

				if (count >= maxCodeLensCount) {
					break;
				}
				if (monitor.isCanceled()) {
					return;
				}
			}
		}
	}

	/**
	 * Extension point ID for the codelens provider.
	 */
	private static final String EXTENSION_POINT_ID = "org.eclipse.jdt.ls.core.internal.codelens.codeLensProvider";

	private static final String CLASS = "class";

	private static final String ID = "id";

	private static final String ORDER = "order";

	private static Set<CodeLensProviderDescriptor> fgContributedCodeLensProviderDescriptors;

	public List<CodeLensProvider> getCodeLensProviders() {
		Set<CodeLensProviderDescriptor> deps = getCodeLensProviderDescriptors();
		return deps.stream().sorted((d1, d2) -> d1.order - d2.order).map(d -> d.getCodeLensProvider()).filter(d -> d != null).collect(Collectors.toList());
	}

	private static synchronized Set<CodeLensProviderDescriptor> getCodeLensProviderDescriptors() {
		if (fgContributedCodeLensProviderDescriptors == null) {
			IConfigurationElement[] elements = Platform.getExtensionRegistry().getConfigurationElementsFor(EXTENSION_POINT_ID);
			fgContributedCodeLensProviderDescriptors = Stream.of(elements).map(e -> new CodeLensProviderDescriptor(e)).collect(Collectors.toSet());
		}
		return fgContributedCodeLensProviderDescriptors;
	}

	private static class CodeLensProviderDescriptor {

		private final IConfigurationElement fConfigurationElement;

		private final String id;

		private final int order;

		public CodeLensProviderDescriptor(IConfigurationElement element) {
			fConfigurationElement = element;
			id = fConfigurationElement.getAttribute(ID);
			order = Integer.valueOf(fConfigurationElement.getAttribute(ORDER));
		}

		public synchronized CodeLensProvider getCodeLensProvider() {
			try {
				Object extension = fConfigurationElement.createExecutableExtension(CLASS);
				if (extension instanceof CodeLensProvider) {
					return (CodeLensProvider) extension;
				} else {
					String message = "Invalid extension to " + EXTENSION_POINT_ID + ". Must implements " + CodeLensProvider.class.getName();
					JavaLanguageServerPlugin.logError(message);
					return null;
				}
			} catch (CoreException e) {
				JavaLanguageServerPlugin.logException("Unable to create code lens provider ", e);
				return null;
			}
		}
	}
}
