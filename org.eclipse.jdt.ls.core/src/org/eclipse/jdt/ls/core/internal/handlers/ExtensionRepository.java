/*******************************************************************************
 * Copyright (c) Simeon Andreev and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *     Simeon Andreev - initial API and implementation
 *******************************************************************************/
package org.eclipse.jdt.ls.core.internal.handlers;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IConfigurationElement;
import org.eclipse.core.runtime.IExtension;
import org.eclipse.core.runtime.IExtensionPoint;
import org.eclipse.core.runtime.IRegistryEventListener;
import org.eclipse.core.runtime.Platform;
import org.eclipse.jdt.ls.core.internal.JavaLanguageServerPlugin;

/**
 * @author Simeon Andreev
 *
 */
public class ExtensionRepository<T> implements IRegistryEventListener {

	private static final String CLASS = "class";

	private final String extensionPointId;
	private final Class<T> interfaceType;
	private final Object lock;
	private List<T> defaultImplementers;
	private List<T> implementers;

	public ExtensionRepository(String extensionPointId, Class<T> interfaceType) {
		this.extensionPointId = extensionPointId;
		this.interfaceType = interfaceType;
		lock = new Object();
		defaultImplementers = Collections.emptyList();
		Platform.getExtensionRegistry().addListener(this, extensionPointId);
	}

	public void dispose() {
		clear();
		Platform.getExtensionRegistry().removeListener(this);
	}

	protected void setDefaultImplementers(List<T> defaultImplementers) {
		this.defaultImplementers = defaultImplementers;
	}

	protected List<T> getImplementers() {
		synchronized (lock) {
			if (implementers != null) {
				return new ArrayList<>(implementers);
			}
		}
		List<T> contributedHandlers = loadImplementers();
		synchronized (lock) {
			if (implementers == null) {
				implementers = new ArrayList<>(contributedHandlers.size() + defaultImplementers.size());
				implementers.addAll(defaultImplementers);
				implementers.addAll(contributedHandlers);
			}
			return new ArrayList<>(implementers);
		}
	}

	private List<T> loadImplementers() {
		IConfigurationElement[] elements = Platform.getExtensionRegistry().getConfigurationElementsFor(extensionPointId);
		List<T> implementers = new ArrayList<>(elements.length);
		for (IConfigurationElement element : elements) {
			try {
				Object extension = element.createExecutableExtension(CLASS);
				try {
					@SuppressWarnings("unchecked")
					T implementation = (T) extension;
					implementers.add(implementation);
				} catch (ClassCastException e) {
					JavaLanguageServerPlugin.logError("Invalid extension to " + extensionPointId + ". Must extend " + interfaceType.getName());
				}
			} catch (CoreException e) {
				JavaLanguageServerPlugin.logException("Unable to create completion handler", e);
			}
		}
		return implementers;
	}

	@Override
	public void added(IExtension[] extensions) {
		clear();
	}

	@Override
	public void removed(IExtension[] extensions) {
		clear();
	}

	@Override
	public void added(IExtensionPoint[] extensionPoints) {
	}

	@Override
	public void removed(IExtensionPoint[] extensionPoints) {
	}

	private void clear() {
		synchronized (lock) {
			implementers = null;
		}
	}
}