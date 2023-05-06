/*******************************************************************************
 * Copyright (c) 2017 Red Hat Inc. and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *     Red Hat Inc. - initial API and implementation
 *******************************************************************************/
package org.eclipse.jdt.ls.core.internal.handlers;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import org.eclipse.jdt.core.CompletionContext;
import org.eclipse.jdt.core.CompletionProposal;
import org.eclipse.lsp4j.CompletionItem;

/**
 * Class representing {@link CompletionProposal} responses to for a given {@link CompletionContext}.
 *
 * @author Fred Bricon
 */
public class CompletionResponse {

	private static AtomicLong idSeed = new AtomicLong(0);
	private Long id;
	private int offset;
	private CompletionContext context;
	/**
	 * Stores the data that are common among the completion items.
	 */
	private Map<String, String> commonData = new HashMap<>();
	private List<CompletionProposal> proposals;
	private List<CompletionItem> items;
	/**
	 * Stores the data that are specific to each completion item.
	 * Those data are contributed by the ranking providers.
	 */
	private List<Map<String, String>> completionItemData;

	public CompletionResponse() {
		id = idSeed.getAndIncrement();
	}

	/**
	 * @return the id
	 */
	public Long getId() {
		return id;
	}

	/**
	 * @return the context
	 */
	public CompletionContext getContext() {
		return context;
	}
	/**
	 * @param context the context to set
	 */
	public void setContext(CompletionContext context) {
		this.context = context;
	}

	public String getCommonData(String key) {
		return this.commonData.get(key);
	}

	public void setCommonData(String key, String value) {
		this.commonData.put(key, value);
	}

	/**
	 * @return the proposals
	 */
	public List<CompletionProposal> getProposals() {
		return proposals;
	}
	/**
	 * @param proposals the proposals to set
	 */
	public void setProposals(List<CompletionProposal> proposals) {
		this.proposals = proposals;
	}
	/**
	 * @return the offset
	 */
	public int getOffset() {
		return offset;
	}
	/**
	 * @param offset the offset to set
	 */
	public void setOffset(int offset) {
		this.offset = offset;
	}

	/**
	 * @return the completion items.
	 */
	public List<CompletionItem> getItems() {
		return items;
	}

	/**
	 * @param items the completion items
	 */
	public void setItems(List<CompletionItem> items) {
		this.items = items;
	}

	public Map<String, String> getCompletionItemData(int index) {
		if (completionItemData == null || index >= completionItemData.size()) {
			return Collections.emptyMap();
		}
		return completionItemData.get(index);
	}

	public void setCompletionItemData(List<Map<String, String>> completionItemData) {
		this.completionItemData = completionItemData;
	}
}
