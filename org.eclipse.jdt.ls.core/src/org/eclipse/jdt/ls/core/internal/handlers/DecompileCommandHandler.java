/*******************************************************************************
 * Copyright (c) 2017 Microsoft Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     David Gileadi - initial API and implementation
 *******************************************************************************/
package org.eclipse.jdt.ls.core.internal.handlers;

import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IConfigurationElement;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.ISafeRunnable;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Platform;
import org.eclipse.core.runtime.SafeRunner;
import org.eclipse.core.runtime.Status;
import org.eclipse.jdt.core.IClassFile;
import org.eclipse.jdt.ls.core.internal.IDecompilerCommandHandler;
import org.eclipse.jdt.ls.core.internal.JavaLanguageServerPlugin;
import org.eclipse.jdt.ls.core.internal.preferences.PreferenceManager;

public class DecompileCommandHandler {

	private static final String EXTENSION_POINT_ID = "org.eclipse.jdt.ls.core.decompilerCommandHandler";
	private static final String CLASS = "class";
	private static final String ID = "id";
	private static final String NAME = "name";

	private final PreferenceManager preferenceManager;
	private DecompilerCommandHandlerDescriptor decompilerDescriptor;
	private IDecompilerCommandHandler decompiler;

	public DecompileCommandHandler(PreferenceManager preferenceManager) {
		this.preferenceManager = preferenceManager;
	}

	private static class DecompilerCommandHandlerDescriptor {

		private final IConfigurationElement configurationElement;
		private IDecompilerCommandHandler decompilerCommandHandler;
		public final String id;
		public final String name;

		public DecompilerCommandHandlerDescriptor(IConfigurationElement element) {
			configurationElement = element;
			id = configurationElement.getAttribute(ID);
			name = configurationElement.getAttribute(NAME);
			decompilerCommandHandler = null;
		}

		public synchronized IDecompilerCommandHandler getDecompilerCommandHandler() {
			if (decompilerCommandHandler == null) {
				try {
					Object extension = configurationElement.createExecutableExtension(CLASS);
					if (extension instanceof IDecompilerCommandHandler) {
						decompilerCommandHandler = (IDecompilerCommandHandler) extension;
					} else {
						String message = "Invalid extension to " + EXTENSION_POINT_ID + ". Must implement org.eclipse.jdt.ls.core.internal.IDecompilerCommandHandler";
						JavaLanguageServerPlugin.logError(message);
						return null;
					}
				} catch (CoreException e) {
					JavaLanguageServerPlugin.logException("Unable to create decompiler command handler ", e);
					return null;
				}
			}
			return decompilerCommandHandler;
		}
	}

	private static Set<DecompilerCommandHandlerDescriptor> contributedCommandHandlers;

	private static synchronized Set<DecompilerCommandHandlerDescriptor> getDecompilerCommandHandlerDescriptors() {
		if (contributedCommandHandlers == null) {
			IConfigurationElement[] elements = Platform.getExtensionRegistry().getConfigurationElementsFor(EXTENSION_POINT_ID);
			contributedCommandHandlers = Stream.of(elements).map(e -> new DecompilerCommandHandlerDescriptor(e)).collect(Collectors.toSet());
		}
		return contributedCommandHandlers;
	}

	/**
	 * Decompile the given class file if possible
	 *
	 * @param classFile
	 *            the class file to decompile
	 * @param monitor
	 * @return the decompiled code if successful, or <code>null</code> if
	 *         unsuccessful, or an empty string if canceled
	 */
	public String decompile(IClassFile classFile, IProgressMonitor monitor) {
		IDecompilerCommandHandler decompiler = getDecompiler();
		if (decompiler == null) {
			JavaLanguageServerPlugin.logError("Unable to load decompiler class");
			return null;
		}

		if (monitor.isCanceled()) {
			return "";
		}

		String configuration = preferenceManager.getPreferences().getDecompilerConfiguration();
		final String[] resultValues = new String[1];
		SafeRunner.run(new ISafeRunnable() {
			@Override
			public void run() throws Exception {
				resultValues[0] = decompiler.decompile(classFile, configuration, monitor);
			}

			@Override
			public void handleException(Throwable ex) {
				IStatus status = new Status(IStatus.ERROR, JavaLanguageServerPlugin.PLUGIN_ID, IStatus.OK, "Error calling decompiler command handler", ex);
				JavaLanguageServerPlugin.log(status);
			}
		});
		return resultValues[0];
	}

	private IDecompilerCommandHandler getDecompiler() {
		if (this.decompiler != null) {
			return this.decompiler;
		}

		Set<DecompilerCommandHandlerDescriptor> handlers = getDecompilerCommandHandlerDescriptors();
		if (handlers.isEmpty()) {
			JavaLanguageServerPlugin.logError("No decompilers found");
			return null;
		}

		String decompilerId = preferenceManager.getPreferences().getDecompilerId();
		Optional<DecompilerCommandHandlerDescriptor> handler = handlers.stream().filter(d -> decompilerId == null || decompilerId.equals(d.id)).findFirst();
		if (!handler.isPresent()) {
			JavaLanguageServerPlugin.logError("Unable to find decompiler " + decompilerId);
			return null;
		}

		this.decompilerDescriptor = handler.get();
		this.decompiler = this.decompilerDescriptor.getDecompilerCommandHandler();
		return this.decompiler;
	}

	/**
	 * @return the name of the chosen decompiler, or its id if no name is provided
	 */
	public String getDecompilerName() {
		if (this.decompilerDescriptor == null) {
			getDecompiler();
			if (this.decompilerDescriptor == null) {
				return null;
			}
		}
		String name = this.decompilerDescriptor.name;
		if (name == null) {
			name = this.decompilerDescriptor.id;
		}
		return name;
	}
}
