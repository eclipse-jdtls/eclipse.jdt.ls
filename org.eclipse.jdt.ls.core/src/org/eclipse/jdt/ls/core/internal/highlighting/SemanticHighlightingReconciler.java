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
 * Copied from https://github.com/eclipse/eclipse.jdt.ui/blob/d41fa3326c5b75a6419c81fcecb37d7d7fb3ac43/org.eclipse.jdt.ui/ui/org/eclipse/jdt/internal/ui/javaeditor/SemanticHighlightingReconciler.java
 *******************************************************************************/
package org.eclipse.jdt.ls.core.internal.highlighting;

import static java.util.Collections.emptyList;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.BooleanLiteral;
import org.eclipse.jdt.core.dom.CharacterLiteral;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.ConstructorInvocation;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.IMethodBinding;
import org.eclipse.jdt.core.dom.NumberLiteral;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.core.dom.SimpleType;
import org.eclipse.jdt.core.dom.SuperConstructorInvocation;
import org.eclipse.jdt.internal.corext.dom.GenericVisitor;
import org.eclipse.jdt.ls.core.internal.highlighting.SemanticHighlightings.DeprecatedMemberHighlighting;
import org.eclipse.jdt.ls.core.internal.highlighting.SemanticHighlightings.VarKeywordHighlighting;
import org.eclipse.jface.text.BadPositionCategoryException;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.Position;

import com.google.common.base.Preconditions;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableList;

/**
 * Semantic highlighting reconciler - Background thread implementation.
 *
 * @since 3.0
 */
@SuppressWarnings("restriction")
public class SemanticHighlightingReconciler {

	/**
	 * Collects positions from the AST.
	 */
	private class PositionCollector extends GenericVisitor {

		/** The semantic token */
		private SemanticToken fToken= new SemanticToken();

		/*
		 * @see org.eclipse.jdt.internal.corext.dom.GenericVisitor#visitNode(org.eclipse.jdt.core.dom.ASTNode)
		 */
		@Override
		protected boolean visitNode(ASTNode node) {
			if ((node.getFlags() & ASTNode.MALFORMED) == ASTNode.MALFORMED) {
				retainPositions(node.getStartPosition(), node.getLength());
				return false;
			}
			return true;
		}

		/*
		 * @see org.eclipse.jdt.core.dom.ASTVisitor#visit(org.eclipse.jdt.core.dom.BooleanLiteral)
		 */
		@Override
		public boolean visit(BooleanLiteral node) {
			return visitLiteral(node);
		}

		/*
		 * @see org.eclipse.jdt.core.dom.ASTVisitor#visit(org.eclipse.jdt.core.dom.CharacterLiteral)
		 */
		@Override
		public boolean visit(CharacterLiteral node) {
			return visitLiteral(node);
		}

		/*
		 * @see org.eclipse.jdt.core.dom.ASTVisitor#visit(org.eclipse.jdt.core.dom.NumberLiteral)
		 */
		@Override
		public boolean visit(NumberLiteral node) {
			return visitLiteral(node);
		}

		private boolean visitLiteral(Expression node) {
			fToken.update(node);
			for (int i= 0, n= fJobSemanticHighlightings.length; i < n; i++) {
				SemanticHighlighting semanticHighlighting= fJobSemanticHighlightings[i];
				if (semanticHighlighting.consumesLiteral(fToken)) {
					int offset= node.getStartPosition();
					int length= node.getLength();
					if (offset > -1 && length > 0) {
						addPosition(offset, length, fJobHighlightings.get(i));
					}
					break;
				}
			}
			fToken.clear();
			return false;
		}

		/*
		 * @see org.eclipse.jdt.internal.corext.dom.GenericVisitor#visit(org.eclipse.jdt.core.dom.ConstructorInvocation)
		 * @since 3.5
		 */
		@Override
		public boolean visit(ConstructorInvocation node) {
			// XXX Hack for performance reasons (should loop over fJobSemanticHighlightings can call consumes(*))
			if (fJobDeprecatedMemberHighlighting != null) {
				IMethodBinding constructorBinding= node.resolveConstructorBinding();
				if (constructorBinding != null && constructorBinding.isDeprecated()) {
					int offset= node.getStartPosition();
					int length= 4;
					if (offset > -1 && length > 0) {
						addPosition(offset, length, fJobDeprecatedMemberHighlighting);
					}
				}
			}
			return true;
		}

		/*
		 * @see org.eclipse.jdt.internal.corext.dom.GenericVisitor#visit(org.eclipse.jdt.core.dom.ConstructorInvocation)
		 * @since 3.5
		 */
		@Override
		public boolean visit(SuperConstructorInvocation node) {
			// XXX Hack for performance reasons (should loop over fJobSemanticHighlightings can call consumes(*))
			if (fJobDeprecatedMemberHighlighting != null) {
				IMethodBinding constructorBinding= node.resolveConstructorBinding();
				if (constructorBinding != null && constructorBinding.isDeprecated()) {
					int offset= node.getStartPosition();
					int length= 5;
					if (offset > -1 && length > 0) {
						addPosition(offset, length, fJobDeprecatedMemberHighlighting);
					}
				}
			}
			return true;
		}

		@Override
		public boolean visit(SimpleType node) {
			if (node.getAST().apiLevel() >= AST.JLS10 && node.isVar()) {
				int offset= node.getStartPosition();
				int length= node.getLength();
				if (offset > -1 && length > 0) {
					for (int i= 0; i < fJobSemanticHighlightings.length; i++) {
						SemanticHighlighting semanticHighlighting= fJobSemanticHighlightings[i];
						if (semanticHighlighting instanceof VarKeywordHighlighting) {
							addPosition(offset, length, fJobHighlightings.get(i));
							return false;
						}
					}
				}
			}
			return true;
		}

		/*
		 * @see org.eclipse.jdt.core.dom.ASTVisitor#visit(org.eclipse.jdt.core.dom.SimpleName)
		 */
		@Override
		public boolean visit(SimpleName node) {
			fToken.update(node);
			for (int i= 0, n= fJobSemanticHighlightings.length; i < n; i++) {
				SemanticHighlighting semanticHighlighting= fJobSemanticHighlightings[i];
				if (semanticHighlighting.consumes(fToken)) {
					int offset= node.getStartPosition();
					int length= node.getLength();
					if (offset > -1 && length > 0) {
						addPosition(offset, length, fJobHighlightings.get(i));
					}
					break;
				}
			}
			fToken.clear();
			return false;
		}

		/**
		 * Add a position with the given range and highlighting iff it does not exist
		 * already.
		 *
		 * @param offset
		 *            The range offset
		 * @param length
		 *            The range length
		 * @param scopes
		 *            The highlighting
		 */
		private void addPosition(int offset, int length, List<String> scopes) {
			boolean isExisting= false;
			// TODO: use binary search
			for (int i= 0, n= fRemovedPositions.size(); i < n; i++) {
				HighlightedPosition position= (HighlightedPosition) fRemovedPositions.get(i);
				if (position == null) {
					continue;
				}
				if (position.isEqual(offset, length, scopes)) {
					isExisting= true;
					fRemovedPositions.set(i, null);
					fNOfRemovedPositions--;
					break;
				}
			}

			if (!isExisting) {
				Position position = fJobPresenter.createHighlightedPosition(offset, length, scopes);
				fAddedPositions.add(position);
			}
		}

		/**
		 * Retain the positions completely contained in the given range.
		 * @param offset The range offset
		 * @param length The range length
		 */
		private void retainPositions(int offset, int length) {
			// TODO: use binary search
			for (int i= 0, n= fRemovedPositions.size(); i < n; i++) {
				HighlightedPosition position= (HighlightedPosition) fRemovedPositions.get(i);
				if (position != null && position.isContained(offset, length)) {
					fRemovedPositions.set(i, null);
					fNOfRemovedPositions--;
				}
			}
		}
	}

	/** Position collector */
	private PositionCollector fCollector= new PositionCollector();

	/** The semantic highlighting presenter */
	private SemanticHighlightingPresenter fPresenter = new SemanticHighlightingPresenter();
	/** Semantic highlightings */
	private SemanticHighlighting[] fSemanticHighlightings = SemanticHighlightings.getSemanticHighlightings();
	/** Highlightings */
	private List<List<String>> fHighlightings = FluentIterable.from(Arrays.asList(fSemanticHighlightings)).transform(highlighting -> (List<String>) ImmutableList.copyOf(highlighting.getScopes())).toList();

	/** Background job's added highlighted positions */
	private List<Position> fAddedPositions= new ArrayList<>();
	/** Background job's removed highlighted positions */
	private List<Position> fRemovedPositions= new ArrayList<>();
	/** Number of removed positions */
	private int fNOfRemovedPositions;

	/**
	 * Reconcile operation lock.
	 * @since 3.2
	 */
	private final Object fReconcileLock= new Object();
	/**
	 * <code>true</code> if any thread is executing
	 * <code>reconcile</code>, <code>false</code> otherwise.
	 * @since 3.2
	 */
	private boolean fIsReconciling= false;

	/** The semantic highlighting presenter - cache for background thread, only valid during {@link #reconciled(CompilationUnit, boolean, IProgressMonitor)} */
	private SemanticHighlightingPresenter fJobPresenter = new SemanticHighlightingPresenter();
	/** Semantic highlightings - cache for background thread, only valid during {@link #reconciled(CompilationUnit, boolean, IProgressMonitor)} */
	private SemanticHighlighting[] fJobSemanticHighlightings;
	/** Highlightings - cache for background thread, only valid during {@link #reconciled(CompilationUnit, boolean, IProgressMonitor)} */
	private List<List<String>> fJobHighlightings;

	/**
	 * XXX Hack for performance reasons (should loop over fJobSemanticHighlightings can call consumes(*))
	 * @since 3.5
	 */
	private List<String> fJobDeprecatedMemberHighlighting;

	public List<HighlightedPosition> reconciled(IDocument document, ASTNode ast, boolean forced, IProgressMonitor progressMonitor) throws BadPositionCategoryException {
		// ensure at most one thread can be reconciling at any time
		synchronized (fReconcileLock) {
			if (fIsReconciling) {
				return emptyList();
			} else {
				fIsReconciling= true;
			}
		}
		fJobPresenter= fPresenter;
		fJobSemanticHighlightings= fSemanticHighlightings;
		fJobHighlightings= fHighlightings;

		try {
			if (ast == null) {
				return emptyList();
			}

			ASTNode[] subtrees= getAffectedSubtrees(ast);
			if (subtrees.length == 0) {
				return emptyList();
			}

			startReconcilingPositions();

			if (!fJobPresenter.isCanceled()) {
				fJobDeprecatedMemberHighlighting= null;
				for (int i= 0, n= fJobSemanticHighlightings.length; i < n; i++) {
					SemanticHighlighting semanticHighlighting= fJobSemanticHighlightings[i];
					if (semanticHighlighting instanceof DeprecatedMemberHighlighting) {
						fJobDeprecatedMemberHighlighting = fJobHighlightings.get(i);
						break;
					}
				}
				reconcilePositions(subtrees);
			}

			// We need to manually install the position category
			if (!document.containsPositionCategory(fPresenter.getPositionCategory())) {
				document.addPositionCategory(fPresenter.getPositionCategory());
			}

			updatePresentation(document, fAddedPositions, fRemovedPositions);
			stopReconcilingPositions();
			Position[] positions = document.getPositions(fPresenter.getPositionCategory());
			for (Position position : positions) {
				Preconditions.checkState(position instanceof HighlightedPosition);
			}
			return FluentIterable.from(Arrays.asList(positions)).filter(HighlightedPosition.class).toList();
		} finally {
			fJobPresenter= null;
			fJobSemanticHighlightings= null;
			fJobHighlightings= null;
			fJobDeprecatedMemberHighlighting= null;
			synchronized (fReconcileLock) {
				fIsReconciling= false;
			}
		}
	}

	/**
	 * @param node Root node
	 * @return Array of subtrees that may be affected by past document changes
	 */
	private ASTNode[] getAffectedSubtrees(ASTNode node) {
		// TODO: only return nodes which are affected by document changes - would require an 'anchor' concept for taking distant effects into account
		return new ASTNode[] { node };
	}

	/**
	 * Start reconciling positions.
	 */
	private void startReconcilingPositions() {
		fJobPresenter.addAllPositions(fRemovedPositions);
		fNOfRemovedPositions= fRemovedPositions.size();
	}

	/**
	 * Reconcile positions based on the AST subtrees
	 *
	 * @param subtrees the AST subtrees
	 */
	private void reconcilePositions(ASTNode[] subtrees) {
		// FIXME: remove positions not covered by subtrees



		for (int i= 0, n= subtrees.length; i < n; i++) {
			subtrees[i].accept(fCollector);
		}
		List<Position> oldPositions= fRemovedPositions;
		List<Position> newPositions= new ArrayList<>(fNOfRemovedPositions);
		for (int i= 0, n= oldPositions.size(); i < n; i ++) {
			Position current= oldPositions.get(i);
			if (current != null) {
				newPositions.add(current);
			}
		}
		fRemovedPositions= newPositions;
	}

	/**
	 * Update the presentation.
	 *
	 * @param textPresentation the text presentation
	 * @param addedPositions the added positions
	 * @param removedPositions the removed positions
	 */
	private void updatePresentation(IDocument document, List<Position> addedPositions, List<Position> removedPositions) {
		fJobPresenter.createUpdateRunnable(document, addedPositions, removedPositions);
	}

	/**
	 * Stop reconciling positions.
	 */
	private void stopReconcilingPositions() {
		fRemovedPositions.clear();
		fNOfRemovedPositions= 0;
		fAddedPositions.clear();
	}

}