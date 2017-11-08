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
import org.eclipse.jdt.core.ITypeRoot;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.ls.core.internal.JDTUtils;
import org.eclipse.jdt.ls.core.internal.JavaLanguageServerPlugin;
import org.eclipse.jdt.ls.core.internal.preferences.PreferenceManager;
import org.eclipse.lsp4j.CodeLens;

public class CodeLensHandler {

	private final PreferenceManager preferenceManager;
	private List<CodeLensProvider> codeLensProviders;

	public CodeLensHandler(PreferenceManager preferenceManager) {
		this.preferenceManager = preferenceManager;
		codeLensProviders = new ArrayList<>();
		codeLensProviders.add(new ReferencesCodeLensProvider(preferenceManager));
		codeLensProviders.add(new ImplementationCodeLensProvider(preferenceManager));
		codeLensProviders.addAll(getExtendedCodeLensProviders());
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
			ArrayList<CodeLens> lenses = new ArrayList<>(elements.length);
			for (CodeLensProvider provider : codeLensProviders) {
				lenses.addAll(provider.collectCodeLenses(unit, monitor));
				if (monitor.isCanceled()) {
					lenses.clear();
					break;
				}
			}
			return lenses;
		} catch (JavaModelException e) {
			JavaLanguageServerPlugin.logException("Problem getting code lenses for" + unit.getElementName(), e);
		}
		return Collections.emptyList();
	}

	/**
	 * Extension point ID for the codelens provider container.
	 */
	private static final String EXTENSION_POINT_ID = "org.eclipse.jdt.ls.core.codeLensProviderContainer";

	private static final String PROVIDER = "provider";

	private static final String CLASS = "class";

	private static final String ID = "id";

	private static Set<CodeLensProviderContainerDescriptor> fgContributedCodeLensProviderContainers;

	public List<CodeLensProvider> getExtendedCodeLensProviders() {
		Set<CodeLensProviderContainerDescriptor> deps = getCodeLensProviderContainerDescriptors();
		List<CodeLensProvider> res = new ArrayList<>(deps.size());
		for (CodeLensProviderContainerDescriptor dep : deps) {
			CodeLensProviderContainer container = dep.getCodeLensProviderContainer();
			res.addAll(dep.getProviderIds().stream().map(id -> container.getCodeLensProvider(id, preferenceManager)).collect(Collectors.toSet()));
		}

		return res;
	}

	private static synchronized Set<CodeLensProviderContainerDescriptor> getCodeLensProviderContainerDescriptors() {
		if (fgContributedCodeLensProviderContainers == null) {
			IConfigurationElement[] elements = Platform.getExtensionRegistry().getConfigurationElementsFor(EXTENSION_POINT_ID);
			fgContributedCodeLensProviderContainers = Stream.of(elements).map(e -> new CodeLensProviderContainerDescriptor(e)).collect(Collectors.toSet());
		}
		return fgContributedCodeLensProviderContainers;
	}

	private static class CodeLensProviderContainerDescriptor {

		private final IConfigurationElement fConfigurationElement;

		private Set<String> fProviderIds;

		private CodeLensProviderContainer fCodeLensProviderContainerInstance;

		public CodeLensProviderContainerDescriptor(IConfigurationElement element) {
			fConfigurationElement = element;

			IConfigurationElement[] children = fConfigurationElement.getChildren(PROVIDER);
			fProviderIds = Stream.of(children).map(c -> c.getAttribute(ID)).collect(Collectors.toSet());
			fCodeLensProviderContainerInstance = null;
		}

		public Set<String> getProviderIds() {
			return fProviderIds;
		}

		public synchronized CodeLensProviderContainer getCodeLensProviderContainer() {
			if (fCodeLensProviderContainerInstance == null) {
				try {
					Object extension = fConfigurationElement.createExecutableExtension(CLASS);
					if (extension instanceof CodeLensProviderContainer) {
						fCodeLensProviderContainerInstance = (CodeLensProviderContainer) extension;
					} else {
						String message = "Invalid extension to " + EXTENSION_POINT_ID + ". Must implements org.eclipse.jdt.ls.core.internal.codeLensProviderContainer";
						JavaLanguageServerPlugin.logError(message);
						return null;
					}
				} catch (CoreException e) {
					JavaLanguageServerPlugin.logException("Unable to create code lens provider container ", e);
					return null;
				}
			}
			return fCodeLensProviderContainerInstance;
		}
	}
}
