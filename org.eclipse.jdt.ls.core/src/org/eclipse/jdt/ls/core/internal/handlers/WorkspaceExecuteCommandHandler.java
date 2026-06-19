/*******************************************************************************
 * Copyright (c) 2017-2022 Microsoft Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *     Microsoft Corporation - initial API and implementation
 *******************************************************************************/

package org.eclipse.jdt.ls.core.internal.handlers;

import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IConfigurationElement;
import org.eclipse.core.runtime.IExtension;
import org.eclipse.core.runtime.IExtensionPoint;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IRegistryEventListener;
import org.eclipse.core.runtime.ISafeRunnable;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Platform;
import org.eclipse.core.runtime.SafeRunner;
import org.eclipse.core.runtime.Status;
import org.eclipse.jdt.ls.core.internal.IConstants;
import org.eclipse.jdt.ls.core.internal.IDelegateCommandHandler;
import org.eclipse.jdt.ls.core.internal.JSONUtility;
import org.eclipse.jdt.ls.core.internal.JavaLanguageServerPlugin;
import org.eclipse.lsp4j.ExecuteCommandParams;
import org.eclipse.lsp4j.jsonrpc.ResponseErrorException;
import org.eclipse.lsp4j.jsonrpc.messages.ResponseError;
import org.eclipse.lsp4j.jsonrpc.messages.ResponseErrorCode;

public class WorkspaceExecuteCommandHandler implements IRegistryEventListener {

	private WorkspaceExecuteCommandHandler() {}

	private static class CommandHandlerHolder {
		private static final WorkspaceExecuteCommandHandler instance = new WorkspaceExecuteCommandHandler();
	}

	public static WorkspaceExecuteCommandHandler getInstance() {
		return CommandHandlerHolder.instance;
	}

	/**
	 * Extension point ID for the delegate command handler.
	 */
	private static final String EXTENSION_POINT_ID = "org.eclipse.jdt.ls.core.delegateCommandHandler";

	private static final String COMMAND = "command";

	private static final String STATIC = "static";

	private static final String CLASS = "class";

	private static final String ID = "id";

	private static class DelegateCommandHandlerDescriptor {

		private final IConfigurationElement fConfigurationElement;

		private Set<String> fStaticCommandIds = new HashSet<>();;
		private Set<String> fNonStaticCommandIds = new HashSet<>();
		private Set<String> fAllCommands = new HashSet<>();

		private IDelegateCommandHandler fDelegateCommandHandlerInstance;

		public DelegateCommandHandlerDescriptor(IConfigurationElement element) {
			fConfigurationElement = element;

			IConfigurationElement[] children = fConfigurationElement.getChildren(COMMAND);
			Stream.of(children).forEach(c -> {
				String id = c.getAttribute(ID);
				if (Boolean.valueOf(c.getAttribute(STATIC))) {
					fStaticCommandIds.add(id);
				} else {
					fNonStaticCommandIds.add(id);
				}
				fAllCommands.add(id);
			});
			fDelegateCommandHandlerInstance = null;

			JavaLanguageServerPlugin.logInfo("Static Commands: " + fStaticCommandIds);
			JavaLanguageServerPlugin.logInfo("Non-Static Commands: " + fNonStaticCommandIds);
		}

		public Set<String> getStaticCommands() {
			return fStaticCommandIds;
		}

		public Set<String> getNonStaticCommands() {
			return fNonStaticCommandIds;
		}

		public Set<String> getAllCommands() {
			return fAllCommands;
		}

		public synchronized IDelegateCommandHandler getDelegateCommandHandler() {
			if (fDelegateCommandHandlerInstance == null) {
				try {
					Object extension = fConfigurationElement.createExecutableExtension(CLASS);
					if (extension instanceof IDelegateCommandHandler handler) {
						fDelegateCommandHandlerInstance = handler;
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

		public static String createId(IConfigurationElement element) {
			return element.getNamespaceIdentifier() + "#" + element.getAttribute(CLASS);
		}
	}

	private Map<String, DelegateCommandHandlerDescriptor> fgContributedCommandHandlers;

	private synchronized Collection<DelegateCommandHandlerDescriptor> getDelegateCommandHandlerDescriptors() {
		return getDelegateCommandHandlerDescriptors(false);
	}

	private synchronized Collection<DelegateCommandHandlerDescriptor> getDelegateCommandHandlerDescriptors(boolean force) {
		if (fgContributedCommandHandlers == null || force) {

			IConfigurationElement[] elements = Platform.getExtensionRegistry().getConfigurationElementsFor(EXTENSION_POINT_ID);
			fgContributedCommandHandlers = Stream.of(elements).collect(Collectors.toMap(DelegateCommandHandlerDescriptor::createId, DelegateCommandHandlerDescriptor::new, (value1, value2) -> value2));

			Platform.getExtensionRegistry().removeListener(this);
			Platform.getExtensionRegistry().addListener(this);
		}

		return fgContributedCommandHandlers.values();
	}

	public Set<String> getStaticCommands() {
		Collection<DelegateCommandHandlerDescriptor> handlers = getDelegateCommandHandlerDescriptors();
		Set<String> commands = new HashSet<>();
		for (DelegateCommandHandlerDescriptor handler : handlers) {
			commands.addAll(handler.getStaticCommands());
		}
		return commands;
	}

	public Set<String> getNonStaticCommands() {
		Collection<DelegateCommandHandlerDescriptor> handlers = getDelegateCommandHandlerDescriptors();
		Set<String> commands = new HashSet<>();
		for (DelegateCommandHandlerDescriptor handler : handlers) {
			commands.addAll(handler.getNonStaticCommands());
		}
		return commands;
	}

	public Set<String> getAllCommands() {
		Collection<DelegateCommandHandlerDescriptor> handlers = getDelegateCommandHandlerDescriptors();
		Set<String> commands = new HashSet<>();
		for (DelegateCommandHandlerDescriptor handler : handlers) {
			commands.addAll(handler.getAllCommands());
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

		Collection<DelegateCommandHandlerDescriptor> handlers = getDelegateCommandHandlerDescriptors();

		Collection<DelegateCommandHandlerDescriptor> candidates = handlers.stream().filter(desc -> desc.getAllCommands().contains(params.getCommand())).collect(Collectors.toSet()); //no cancellation here but it's super fast so it's ok.

		if (candidates.isEmpty()) {
			// re-fetch from the extension registry again in case that the local cache is out-of-sync.
			handlers = getDelegateCommandHandlerDescriptors(true);
			candidates = handlers.stream().filter(desc -> desc.getAllCommands().contains(params.getCommand())).collect(Collectors.toSet());
		}

		if (monitor.isCanceled()) {
			return "";
		}
		if (candidates.size() > 1) {
			Exception ex = new IllegalStateException(String.format("Found multiple delegateCommandHandlers (%s) matching command %s", candidates, params.getCommand()));
			throw new ResponseErrorException(new ResponseError(ResponseErrorCode.InternalError, ex.getMessage(), ex));
		}
		if (candidates.isEmpty()) {
			throw new ResponseErrorException(new ResponseError(ResponseErrorCode.MethodNotFound, String.format("No delegateCommandHandler for %s", params.getCommand()), null));
		}

		final DelegateCommandHandlerDescriptor descriptor = candidates.iterator().next();
		final Object[] resultValues = new Object[1];
		SafeRunner.run(new ISafeRunnable() {
			@Override
			public void run() throws Exception {
				final IDelegateCommandHandler delegateCommandHandler = descriptor.getDelegateCommandHandler();
				if (delegateCommandHandler != null) {
					//Convert args to java objects before sending to extensions
					List<Object> args = Collections.emptyList();
					if(params.getArguments()!=null){
						args = params.getArguments().stream().map( (element)->{ return JSONUtility.toModel(element, Object.class);}).collect(Collectors.toList());
					}
					resultValues[0] = delegateCommandHandler.executeCommand(params.getCommand(), args, monitor);
				}
			}

			@Override
			public void handleException(Throwable ex) {
				IStatus status = new Status(IStatus.ERROR, IConstants.PLUGIN_ID, IStatus.OK, "Error in calling delegate command handler", ex);
				JavaLanguageServerPlugin.log(status);
				if (ex instanceof ResponseErrorException responseErrorEx) {
					throw responseErrorEx;
				}
				throw new ResponseErrorException(new ResponseError(ResponseErrorCode.UnknownErrorCode, ex.getMessage(), ex));
			}
		});
		return resultValues[0];
	}

	@Override
	public synchronized void added(IExtension[] extensions) {
		Map<String, DelegateCommandHandlerDescriptor> addedDescriptors = Stream.of(extensions)
				.filter(extension -> extension.getExtensionPointUniqueIdentifier().equals(EXTENSION_POINT_ID))
				.flatMap(extension -> Stream.of(extension.getConfigurationElements()))
				.collect(Collectors.toMap(DelegateCommandHandlerDescriptor::createId, DelegateCommandHandlerDescriptor::new, (value1, value2) -> value2));

		fgContributedCommandHandlers.putAll(addedDescriptors);
	}

	@Override
	public synchronized void removed(IExtension[] extensions) {
		Stream.of(extensions).filter(extension -> extension.getExtensionPointUniqueIdentifier().equals(EXTENSION_POINT_ID)).flatMap(extension -> Stream.of(extension.getConfigurationElements()))
				.map(DelegateCommandHandlerDescriptor::createId).forEach(fgContributedCommandHandlers::remove);
	}

	@Override
	public void added(IExtensionPoint[] extensionPoints) {

	}

	@Override
	public void removed(IExtensionPoint[] extensionPoints) {

	}
}
