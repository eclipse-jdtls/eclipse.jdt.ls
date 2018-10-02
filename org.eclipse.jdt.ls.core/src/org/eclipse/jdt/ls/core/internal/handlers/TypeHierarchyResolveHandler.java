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
import org.eclipse.jdt.ls.core.internal.preferences.PreferenceManager;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.ResolveTypeHierarchyItemParams;
import org.eclipse.lsp4j.TypeHierarchyDirection;
import org.eclipse.lsp4j.TypeHierarchyItem;

/**
 * Handler for the {@code typeHierarchy/resolve} LS method.
 *
 * Resolves unresolved type hierarchy items.
 *
 */
public class TypeHierarchyResolveHandler extends TypeHierarchyHandler {

	public TypeHierarchyResolveHandler(PreferenceManager preferenceManager) {
		super(preferenceManager);
	}

	public TypeHierarchyItem resolve(ResolveTypeHierarchyItemParams params, IProgressMonitor monitor) {
		Assert.isNotNull(params, "params");
		Assert.isNotNull(params.getDirection(), "params.direction");
		Assert.isNotNull(params.getItem(), "params.item");

		String uri = params.getItem().getUri();
		Position position = params.getItem().getSelectionRange().getStart();
		TypeHierarchyDirection direction = params.getDirection();
		int resolve = params.getResolve();
		return getTypeHierarchy(uri, position, direction, resolve, monitor);
	}
}
