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

package org.eclipse.jdt.ls.core.internal.handlers;

import java.util.Collection;
import java.util.HashSet;
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
import org.eclipse.jdt.ls.core.internal.IDelegateCommandHandler;
import org.eclipse.jdt.ls.core.internal.JavaLanguageServerPlugin;
import org.eclipse.lsp4j.ExecuteCommandParams;
import org.eclipse.lsp4j.jsonrpc.ResponseErrorException;
import org.eclipse.lsp4j.jsonrpc.messages.ResponseError;
import org.eclipse.lsp4j.jsonrpc.messages.ResponseErrorCode;

public class WorkspaceExecuteCommandHandler {

	/**
	 * Extension point ID for the delegate command handler.
	 */
	private static final String EXTENSION_POINT_ID = "org.eclipse.jdt.ls.core.delegateCommandHandler";

	private static final String COMMAND = "command";

	private static final String CLASS = "class";

	private static final String ID = "id";

	private static class DelegateCommandHandlerDescriptor {

		private final IConfigurationElement fConfigurationElement;

		private Set<String> fCommandIds;

		private IDelegateCommandHandler fDelegateCommandHandlerInstance;

		public DelegateCommandHandlerDescriptor(IConfigurationElement element) {
			fConfigurationElement = element;

			IConfigurationElement[] children = fConfigurationElement.getChildren(COMMAND);
			fCommandIds = Stream.of(children).map(c -> c.getAttribute(ID)).collect(Collectors.toSet());
			fDelegateCommandHandlerInstance = null;
		}

		public Set<String> getCommands() {
			return fCommandIds;
		}

		public synchronized IDelegateCommandHandler getDelegateCommandHandler() {
			if (fDelegateCommandHandlerInstance == null) {
				try {
					Object extension = fConfigurationElement.createExecutableExtension(CLASS);
					if (extension instanceof IDelegateCommandHandler) {
						fDelegateCommandHandlerInstance = (IDelegateCommandHandler) extension;
					} else {
						String message = "Invalid extension to " + EXTENSION_POINT_ID + ". Must implements org.eclipse.jdt.ls.core.internal.IDelegateCommandHandler";
						JavaLanguageServerPlugin.logError(message);
						return null;
					}
				} catch (CoreException e) {
					JavaLanguageServerPlugin.logException("Unable to create delegate command handler ", e);
					return null;
				}
			}
			return fDelegateCommandHandlerInstance;
		}
	}

	private static Set<DelegateCommandHandlerDescriptor> fgContributedCommandHandlers;

	private static synchronized Set<DelegateCommandHandlerDescriptor> getDelegateCommandHandlerDescriptors() {
		if (fgContributedCommandHandlers == null) {
			IConfigurationElement[] elements = Platform.getExtensionRegistry().getConfigurationElementsFor(EXTENSION_POINT_ID);
			fgContributedCommandHandlers = Stream.of(elements).map(e -> new DelegateCommandHandlerDescriptor(e)).collect(Collectors.toSet());
		}
		return fgContributedCommandHandlers;
	}

	public static Set<String> getCommands() {
		Set<DelegateCommandHandlerDescriptor> handlers = getDelegateCommandHandlerDescriptors();
		Set<String> commands = new HashSet<>();
		for (DelegateCommandHandlerDescriptor handler : handlers) {
			commands.addAll(handler.getCommands());
		}
		return commands;
	}
	/**
	 * Execute workspace command and invoke language server delegate command
	 * handler for matching command
	 *
	 * @param params
	 *            parameter from the protocol
	 * @param monitor
	 * @return execution result
	 */
	public Object executeCommand(ExecuteCommandParams params, IProgressMonitor monitor) {
		if (params == null || params.getCommand() == null) {
			String errorMessage = "The workspace/executeCommand has empty params or command";
			JavaLanguageServerPlugin.logError(errorMessage);
			throw new ResponseErrorException(new ResponseError(ResponseErrorCode.InvalidParams, errorMessage, null));
		}

		Set<DelegateCommandHandlerDescriptor> handlers = getDelegateCommandHandlerDescriptors();

		Collection<DelegateCommandHandlerDescriptor> candidates = handlers.stream().filter(desc -> desc.getCommands().contains(params.getCommand())).collect(Collectors.toSet()); //no cancellation here but it's super fast so it's ok.

		if (candidates.size() > 1) {
			Exception ex = new IllegalStateException(String.format("Found multiple delegateCommandHandlers (%s) matching command %s", candidates, params.getCommand()));
			throw new ResponseErrorException(new ResponseError(ResponseErrorCode.InternalError, ex.getMessage(), ex));
		}
		if (monitor.isCanceled()) {
			return "";
		}
		if (candidates.isEmpty()) {
			throw new ResponseErrorException(new ResponseError(ResponseErrorCode.MethodNotFound, String.format("No delegateCommandHandler for %s", params.getCommand()), null));
		}
		final Object[] resultValues = new Object[1];
		SafeRunner.run(new ISafeRunnable() {
			@Override
			public void run() throws Exception {
				final IDelegateCommandHandler delegateCommandHandler = candidates.iterator().next().getDelegateCommandHandler();
				if (delegateCommandHandler != null) {
					resultValues[0] = delegateCommandHandler.executeCommand(params.getCommand(), params.getArguments(), monitor);
				}
			}

			@Override
			public void handleException(Throwable ex) {
				IStatus status = new Status(IStatus.ERROR, JavaLanguageServerPlugin.PLUGIN_ID, IStatus.OK, "Error in calling delegate command handler", ex);
				JavaLanguageServerPlugin.log(status);
				if (ex instanceof ResponseErrorException) {
					throw (ResponseErrorException) ex;
				}
				throw new ResponseErrorException(new ResponseError(ResponseErrorCode.UnknownErrorCode, ex.getMessage(), ex));
			}
		});
		return resultValues[0];
	}
}
