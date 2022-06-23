/*******************************************************************************
* Copyright (c) 2021 Microsoft Corporation and others.
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

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

public class ResponseStore<T> {
	private AtomicLong idSeed = new AtomicLong(0);
	private Map<Long, ResponseItem<T>> responseCache;

	/**
	 * Unlimited cache.
	 */
	public ResponseStore() {
		this.responseCache = new ConcurrentHashMap<>();
	}

	/**
	 * Deletes the eldest items if the size of the cache reaches the maximum.
	 */
	public ResponseStore(int maxSize) {
		this.responseCache = Collections.synchronizedMap(new LinkedHashMap<Long, ResponseItem<T>>() {
			@Override
			protected boolean removeEldestEntry(final Map.Entry eldest) {
				return maxSize > 0 && size() > maxSize;
			}
		});
	}

	public ResponseItem<T> createResponse() {
		return new ResponseItem<>(idSeed.getAndIncrement());
	}

	public ResponseItem<T> get(Long id) {
		return responseCache.get(id);
	}

	public void store(ResponseItem<T> response) {
		if (response != null) {
			responseCache.put(response.getId(), response);
		}
	}

	public void delete(ResponseItem<T> response) {
		if (response != null) {
			responseCache.remove(response.getId());
		}
	}

	public void clear() {
		responseCache.clear();
	}

	public boolean isEmpty() {
		return responseCache.isEmpty();
	}

	public static class ResponseItem<T> {
		private Long id;
		private List<T> proposals;

		public ResponseItem(Long id) {
			this.id = id;
		}

		/**
		 * @return the id
		 */
		public Long getId() {
			return id;
		}

		/**
		 * @return the proposals
		 */
		public List<T> getProposals() {
			return proposals;
		}

		/**
		 * @param proposals the proposals to set
		 */
		public void setProposals(List<T> proposals) {
			this.proposals = proposals;
		}
	}
}
