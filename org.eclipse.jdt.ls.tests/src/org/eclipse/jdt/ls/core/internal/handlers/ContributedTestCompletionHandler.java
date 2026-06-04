/*******************************************************************************
 * Copyright (c) 2026 Simeon Andreev and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *     Simeon Andreev - initial API and implementation
 *******************************************************************************/
package org.eclipse.jdt.ls.core.internal.handlers;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.lsp4j.CompletionItem;
import org.eclipse.lsp4j.CompletionList;
import org.eclipse.lsp4j.CompletionParams;

/**
 * @author Simeon Andreev
 *
 */
public class ContributedTestCompletionHandler implements ICompletionHandler {

	public static final String TEST_DETAIL = "test completion item";
	public static final String TEST_CONTENT = "test string";

	public static CompletionItem item = null;

	@Override
	public CompletionList completion(CompletionParams params, IProgressMonitor monitor) {
		List<CompletionItem> items = Collections.emptyList();
		if (item != null) {
			items = Arrays.asList(item);
		}
		return new CompletionList(items);
	}

	public static CompletionItem testItem() {
		CompletionItem item = new CompletionItem();
		item.setInsertText(TEST_CONTENT);
		item.setDetail(TEST_DETAIL);
		return item;
	}
}
