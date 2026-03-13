/*******************************************************************************
 * Copyright (c) Red Hat Inc. and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *     Red Hat Inc. - initial API and implementation
 *     Simeon Andreev - support completion handler extension
 *******************************************************************************/
package org.eclipse.jdt.ls.core.internal.handlers;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IConfigurationElement;
import org.eclipse.core.runtime.IExtension;
import org.eclipse.core.runtime.IExtensionPoint;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IRegistryEventListener;
import org.eclipse.core.runtime.Platform;
import org.eclipse.jdt.core.CompletionProposal;
import org.eclipse.jdt.ls.core.contentassist.CompletionRanking;
import org.eclipse.jdt.ls.core.internal.ExceptionFactory;
import org.eclipse.jdt.ls.core.internal.JavaLanguageServerPlugin;
import org.eclipse.jdt.ls.core.internal.preferences.PreferenceManager;
import org.eclipse.lsp4j.Command;
import org.eclipse.lsp4j.CompletionItem;
import org.eclipse.lsp4j.CompletionItemDefaults;
import org.eclipse.lsp4j.CompletionList;
import org.eclipse.lsp4j.CompletionParams;
import org.eclipse.lsp4j.jsonrpc.messages.Either;


public final class CompletionHandlers implements IRegistryEventListener {

	public static final String EXTENSION_POINT_ID = "org.eclipse.jdt.ls.core.completionHandler";

	private static final String DATA_FIELD_HANDLER_ID = "hid";

	private static final String CLASS = "class";

	private final PreferenceManager manager;
	private final Object handlersLock;
	private final CompletionHandler defaultHandler;
	private List<ICompletionHandler> handlers;

	// TODO: we can consider to cache more detailed context so that the information can also
	// be used by features like inlay hint.
	public static CompletionProposal selectedProposal;

	public CompletionHandlers(PreferenceManager manager) {
		this.manager = manager;
		handlersLock = new Object();
		defaultHandler = new CompletionHandler(manager);
		Platform.getExtensionRegistry().addListener(this, EXTENSION_POINT_ID);
	}

	public void dispose() {
		clear();
		Platform.getExtensionRegistry().removeListener(this);
	}

	public Either<List<CompletionItem>, CompletionList> completion(CompletionParams params, IProgressMonitor monitor) {
		boolean isIncomplete = false;
		List<CompletionItem> completions = new ArrayList<>();
		CompletionItemDefaults defaults = new CompletionItemDefaults();
		List<ICompletionHandler> handlers = getCompletionHandlers();
		for (int i = 0; i < handlers.size(); ++i) {
			if (monitor.isCanceled()) {
				break;
			}
			long startTime = System.currentTimeMillis();
			String handlerId = String.valueOf(i);
			ICompletionHandler handler = handlers.get(i);
			CompletionList c = handler.completion(params, monitor);
			if (c != null) {
				isIncomplete |= c.isIncomplete();
				if (i == 0) {
					// TODO: how to merge defaults?
					defaults = c.getItemDefaults();
				}
				long executionTime = System.currentTimeMillis() - startTime;
				String lastRequestId = null;
				for (CompletionItem item : c.getItems()) {
					String requestId = "";
					String proposalId = "";
					@SuppressWarnings("unchecked")
					Map<String, String> data = (Map<String, String>) item.getData();
					if (data != null) {
						requestId = data.getOrDefault(CompletionResolveHandler.DATA_FIELD_REQUEST_ID, "");
						proposalId = data.getOrDefault(CompletionResolveHandler.DATA_FIELD_PROPOSAL_ID, "");
					}
					if (requestId.isEmpty() || proposalId.isEmpty()) {
						continue;
					}
					item.setCommand(new Command("", "java.completion.onDidSelect", Arrays.asList(
							requestId,
							proposalId
					)));
					if (Objects.equals(requestId, lastRequestId)) {
						continue;
					}
					lastRequestId = requestId;
					int pId = Integer.parseInt(proposalId);
					long rId = Long.parseLong(requestId);
					CompletionResponse completionResponse = CompletionResponses.get(rId);
					if (completionResponse == null || completionResponse.getProposals().size() <= pId) {
						JavaLanguageServerPlugin.logError("Failed to save common data for completion items.");
						continue;
					}
					completionResponse.setCommonData(CompletionRanking.COMPLETION_EXECUTION_TIME, String.valueOf(executionTime));
					completionResponse.setCommonData(DATA_FIELD_HANDLER_ID, handlerId);
				}
				completions.addAll(c.getItems());
			}
		}
		return Either.forRight(new CompletionList(isIncomplete, completions, defaults));
	}

	@SuppressWarnings("unchecked")
	public void onDidCompletionItemSelect(String requestId, String proposalId) throws CoreException {
		triggerSignatureHelp();
		if (proposalId.isEmpty() || requestId.isEmpty()) {
			return;
		}
		int pId = Integer.parseInt(proposalId);
		long rId = Long.parseLong(requestId);
		CompletionResponse completionResponse = CompletionResponses.get(rId);
		if (completionResponse == null || completionResponse.getItems().size() <= pId || completionResponse.getProposals().size() <= pId) {
			throw ExceptionFactory.newException("Cannot get completion responses.");
		}

		CompletionProposal proposal = completionResponse.getProposals().get(pId);

		// clear the cache if failed to get the selected proposal.
		if (proposal == null) {
			selectedProposal = null;
		} else if (proposal.getKind() == CompletionProposal.METHOD_REF || proposal.getKind() == CompletionProposal.CONSTRUCTOR_INVOCATION || proposal.getKind() == CompletionProposal.METHOD_REF_WITH_CASTED_RECEIVER) {
			selectedProposal = proposal;
		}
		CompletionItem item = completionResponse.getItems().get(pId);
		if (item == null) {
			throw ExceptionFactory.newException("Cannot get the completion item.");
		}

		// get the cached completion execution time and set it to the selected item in case that providers need it.
		String executionTime = completionResponse.getCommonData(CompletionRanking.COMPLETION_EXECUTION_TIME);
		if (executionTime != null) {
			((Map<String, String>) item.getData()).put(CompletionRanking.COMPLETION_EXECUTION_TIME, executionTime);
		}

		Map<String, String> contributedData = completionResponse.getCompletionItemData(pId);
		if (contributedData != null) {
			((Map<String, String>) item.getData()).putAll(contributedData);
		}
		String index = completionResponse.getCommonData(DATA_FIELD_HANDLER_ID);
		if (index == null || index.isEmpty()) {
			index = "0";
		}
		int i = Integer.parseInt(index);
		List<ICompletionHandler> handlers = getCompletionHandlers();
		if (0 <= i && i < handlers.size()) {
			ICompletionHandler handler = handlers.get(i);
			handler.onDidCompletionItemSelect(item);
		}
	}

	private void triggerSignatureHelp() {
		if (manager.getPreferences().isSignatureHelpEnabled()) {
			String onSelectedCommand = manager.getClientPreferences().getCompletionItemCommand();
			if (!onSelectedCommand.isEmpty() && manager.getClientPreferences().isExecuteClientCommandSupport()) {
				JavaLanguageServerPlugin.getInstance().getClientConnection().executeClientCommand(onSelectedCommand);
			}
		}
	}

	private List<ICompletionHandler> getCompletionHandlers() {
		synchronized (handlersLock) {
			if (handlers != null) {
				return new ArrayList<>(handlers);
			}
		}
		List<ICompletionHandler> contributedHandlers = loadContributedHandlers();
		synchronized (handlersLock) {
			if (handlers == null) {
				handlers = new ArrayList<>(contributedHandlers.size() + 1);
				handlers.add(defaultHandler);
				handlers.addAll(contributedHandlers);
			}
			return new ArrayList<>(handlers);
		}
	}

	private List<ICompletionHandler> loadContributedHandlers() {
		IConfigurationElement[] elements = Platform.getExtensionRegistry().getConfigurationElementsFor(EXTENSION_POINT_ID);
		List<ICompletionHandler> handlers = new ArrayList<>(elements.length);
		for (IConfigurationElement element : elements) {
			try {
				Object extension = element.createExecutableExtension(CLASS);
				if (extension instanceof ICompletionHandler handler) {
					handlers.add(handler);
				} else {
					JavaLanguageServerPlugin.logError("Invalid extension to " + EXTENSION_POINT_ID
							+ ". Must extend " + ICompletionHandler.class.getName());
				}
			} catch (CoreException e) {
				JavaLanguageServerPlugin.logException("Unable to create completion handler", e);
			}
		}
		return handlers;
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
		synchronized (handlersLock) {
			handlers = null;
		}
	}
}