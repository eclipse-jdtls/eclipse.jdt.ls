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

import java.util.List;
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
	private List<CompletionProposal> proposals;
	private List<CompletionItem> items;

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
}
