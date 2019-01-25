/*******************************************************************************
 * Copyright (c) 2019 TypeFox and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     TypeFox - initial API and implementation
 *******************************************************************************/
package org.eclipse.jdt.ls.core.internal.handlers;

import org.eclipse.core.runtime.Assert;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.lsp4j.CallHierarchyItem;
import org.eclipse.lsp4j.CallHierarchyParams;
import org.eclipse.lsp4j.ResolveCallHierarchyItemParams;
import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.eclipse.lsp4j.services.TextDocumentService;

/**
 * Handler for the
 * {@link TextDocumentService#resolveCallHierarchy(org.eclipse.lsp4j.ResolveCallHierarchyItemParams)
 * <code>callHierarchy/resolve</code>} LS method.
 */
public class CallHierarchyResolveHandler extends CallHierarchyHandler {

	public CallHierarchyItem resolve(ResolveCallHierarchyItemParams params, IProgressMonitor monitor) {
		Assert.isNotNull(params, "params");

		return callHierarchy(toCallHierarchyParams(params), monitor);
	}

	private CallHierarchyParams toCallHierarchyParams(ResolveCallHierarchyItemParams params) {
		Assert.isNotNull(params.getItem(), "params.item");
		Assert.isNotNull(params.getDirection(), "params.direction");

		CallHierarchyParams result = new CallHierarchyParams();
		result.setDirection(params.getDirection());
		result.setResolve(params.getResolve());
		CallHierarchyItem item = params.getItem();
		result.setTextDocument(new TextDocumentIdentifier(item.getUri()));
		result.setPosition(item.getRange().getStart());
		return result;
	}

}
