/******************************************************************************
 * Copyright (c) 2019 TypeFox and others.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v. 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0,
 * or the Eclipse Distribution License v. 1.0 which is available at
 * http://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: EPL-2.0 OR BSD-3-Clause
 ******************************************************************************/
package org.eclipse.lsp4j.legacy.typeHierarchy;

import org.eclipse.lsp4j.services.TextDocumentService;

/**
 * Direction specific to the
 * {@link TextDocumentService#typeHierarchy(TypeHierarchyParams)
 * textDocument/typeHierarchy} and
 * {@link TextDocumentService#resolveTypeHierarchy(ResolveTypeHierarchyItemParams)
 * typeHierarchy/resolve} LS methods.
 *
 * <p>
 * Valid values are:
 * <ul>
 * <li>{@link TypeHierarchyDirection#Children Children},</li>
 * <li>{@link TypeHierarchyDirection#Parents Parents},</li>
 * <li>{@link TypeHierarchyDirection#Both Both}.</li>
 * </ul>
 *
 */
public enum TypeHierarchyDirection {

	/**
	 * Flag for retrieving/resolving the subtypes. Value: {@code 0}.
	 */
	Children,

	/**
	 * Flag to use when retrieving/resolving the supertypes. Value: {@code 1}.
	 */
	Parents,

	/**
	 * Flag for resolving both the super- and subtypes. Value: {@code 2}.
	 */
	Both;

	public static TypeHierarchyDirection forValue(int value) {
		TypeHierarchyDirection[] values = TypeHierarchyDirection.values();
		if (value < 0 || value >= values.length) {
			throw new IllegalArgumentException("Illegal enum value: " + value);
		}
		return values[value];
	}

}
