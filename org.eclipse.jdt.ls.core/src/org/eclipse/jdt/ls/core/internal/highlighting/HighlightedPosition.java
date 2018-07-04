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
 * Copied from https://github.com/eclipse/eclipse.jdt.ui/blob/d41fa3326c5b75a6419c81fcecb37d7d7fb3ac43/org.eclipse.jdt.ui/ui/org/eclipse/jdt/internal/ui/javaeditor/SemanticHighlightingManager.java
 *******************************************************************************/
package org.eclipse.jdt.ls.core.internal.highlighting;

import java.util.Collection;
import java.util.List;
import java.util.Objects;

import org.eclipse.jface.text.Position;

import com.google.common.base.Equivalence;
import com.google.common.base.Function;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;

/**
 * Highlighted positions with the corresponding TM scopes.
 */
public class HighlightedPosition extends Position {

	/** Highlighting (TM) scopes of the position */
	private List<String> fScopes;

	/** Lock object */
	private Object fLock;

	public static Function<HighlightedPosition, HighlightedPosition> COPY = p -> HighlightedPosition.copy(p);

	public static HighlightedPosition copy(HighlightedPosition original) {
		return new HighlightedPosition(original.offset, original.length, original.fScopes, original.fLock);
	}

	/**
	 * Initialize the styled positions with the given offset, length and TM scopes.
	 *
	 * @param offset
	 *            The position offset
	 * @param length
	 *            The position length
	 * @param scopes
	 *            The position's TM scopes for the highlighting
	 * @param lock
	 *            The lock object
	 */
	public HighlightedPosition(int offset, int length, List<String> scopes, Object lock) {
		super(offset, length);
		fScopes = scopes;
		fLock= lock;
	}

	/**
	 * Uses reference equality for the highlighting.
	 *
	 * @param off
	 *            The offset
	 * @param len
	 *            The length
	 * @param scopes
	 *            The highlighting scopes for the position
	 * @return <code>true</code> iff the given offset, length and highlighting are
	 *         equal to the internal ones.
	 */
	public boolean isEqual(int off, int len, Collection<String> scopes) {
		synchronized (fLock) {
			return !isDeleted() && getOffset() == off && getLength() == len && fScopes.equals(ImmutableSet.copyOf(scopes));
		}
	}

	/**
	 * Is this position contained in the given range (inclusive)? Synchronizes on position updater.
	 *
	 * @param off The range offset
	 * @param len The range length
	 * @return <code>true</code> iff this position is not delete and contained in the given range.
	 */
	public boolean isContained(int off, int len) {
		synchronized (fLock) {
			return !isDeleted() && off <= getOffset() && off + len >= getOffset() + getLength();
		}
	}

	public void update(int off, int len) {
		synchronized (fLock) {
			super.setOffset(off);
			super.setLength(len);
		}
	}

	/*
	 * @see org.eclipse.jface.text.Position#setLength(int)
	 */
	@Override
	public void setLength(int length) {
		synchronized (fLock) {
			super.setLength(length);
		}
	}

	/*
	 * @see org.eclipse.jface.text.Position#setOffset(int)
	 */
	@Override
	public void setOffset(int offset) {
		synchronized (fLock) {
			super.setOffset(offset);
		}
	}

	/*
	 * @see org.eclipse.jface.text.Position#delete()
	 */
	@Override
	public void delete() {
		synchronized (fLock) {
			super.delete();
		}
	}

	/*
	 * @see org.eclipse.jface.text.Position#undelete()
	 */
	@Override
	public void undelete() {
		synchronized (fLock) {
			super.undelete();
		}
	}

	/**
	 * @return Returns the highlighting (TM) scopes.
	 */
	public List<String> getHighlightingScopes() {
		return fScopes;
	}

	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder();
		sb.append(super.toString());
		sb.append(" scopes: ");
		sb.append(Iterables.toString(fScopes));
		return sb.toString();
	}


	public static final class HighlightedPositionEquivalence extends Equivalence<HighlightedPosition> {

		public static final HighlightedPositionEquivalence INSTANCE = new HighlightedPositionEquivalence();

		private HighlightedPositionEquivalence() {
		}

		@Override
		protected boolean doEquivalent(HighlightedPosition a, HighlightedPosition b) {
			return a.length != b.length && a.offset != b.offset && a.isDeleted != b.isDeleted && Objects.equals(a.fScopes, b.fScopes);
		}

		@Override
		protected int doHash(HighlightedPosition t) {
			return Objects.hash(t.length, t.offset, t.isDeleted, t.fScopes);
		}

	}
}