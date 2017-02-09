/*******************************************************************************
 * Copyright (c) 2017 Red Hat Inc. and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Red Hat Inc. - initial API and implementation
 *******************************************************************************/
package org.eclipse.jdt.ls.core.internal.handlers;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Cache of {@link CompletionResponse}s.
 *
 * @author Fred Bricon
 */
public class CompletionResponses {

	private CompletionResponses(){
		//Don't instantiate
	}

	private static Map<Long, CompletionResponse> COMPLETIONS = new ConcurrentHashMap<>();

	public static CompletionResponse get(Long id) {
		return COMPLETIONS.get(id);
	}

	public static void store(CompletionResponse response) {
		if (response != null) {
			COMPLETIONS.put(response.getId(), response);
		}
	}

	public static void delete(CompletionResponse response) {
		if (response != null) {
			COMPLETIONS.remove(response.getId());
		}
	}

	public static void clear() {
		COMPLETIONS.clear();
	}
}
