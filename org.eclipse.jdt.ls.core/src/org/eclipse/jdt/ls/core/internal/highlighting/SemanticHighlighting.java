/*******************************************************************************
 * Copyright (c) 2000, 2008 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *     TypeFox - port to jdt.ls
 *
 * Copied from https://github.com/eclipse/eclipse.jdt.ui/blob/d41fa3326c5b75a6419c81fcecb37d7d7fb3ac43/org.eclipse.jdt.ui/ui/org/eclipse/jdt/internal/ui/javaeditor/SemanticHighlighting.java
 *******************************************************************************/

package org.eclipse.jdt.ls.core.internal.highlighting;

import java.util.List;

import com.google.common.collect.Iterables;

/**
 * Semantic highlighting
 */
public interface SemanticHighlighting {

	public abstract List<String> getScopes();

	/**
	 * @return the display name
	 */
	public default String getDisplayName() {
		final StringBuilder sb = new StringBuilder();
		sb.append(this.getClass().getSimpleName());
		sb.append("[");
		sb.append(Iterables.toString(getScopes()));
		sb.append("]");
		return sb.toString();
	}

	/**
	 * Returns <code>true</code> if the semantic highlighting consumes the semantic
	 * token.
	 * <p>
	 * NOTE: Implementors are not allowed to keep a reference on the token or on any
	 * object retrieved from the token.
	 * </p>
	 *
	 * @param token
	 *            the semantic token for a
	 *            {@link org.eclipse.jdt.core.dom.SimpleName}
	 * @return <code>true</code> iff the semantic highlighting consumes the semantic
	 *         token
	 */
	public boolean consumes(SemanticToken token);

	/**
	 * Returns <code>true</code> if the semantic highlighting consumes the semantic
	 * token.
	 * <p>
	 * NOTE: Implementors are not allowed to keep a reference on the token or on any
	 * object retrieved from the token.
	 * </p>
	 *
	 * @param token
	 *            the semantic token for a
	 *            {@link org.eclipse.jdt.core.dom.NumberLiteral},
	 *            {@link org.eclipse.jdt.core.dom.BooleanLiteral} or
	 *            {@link org.eclipse.jdt.core.dom.CharacterLiteral}
	 * @return <code>true</code> iff the semantic highlighting consumes the semantic
	 *         token
	 */
	public default boolean consumesLiteral(SemanticToken token) {
		return false;
	}

}