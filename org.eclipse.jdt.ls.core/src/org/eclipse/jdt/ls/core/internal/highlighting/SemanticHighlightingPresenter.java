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
 * Copied from https://github.com/eclipse/eclipse.jdt.ui/blob/d41fa3326c5b75a6419c81fcecb37d7d7fb3ac43/org.eclipse.jdt.ui/ui/org/eclipse/jdt/internal/ui/javaeditor/SemanticHighlightingPresenter.java
 *******************************************************************************/
package org.eclipse.jdt.ls.core.internal.highlighting;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.eclipse.jdt.ls.core.internal.JavaLanguageServerPlugin;
import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.BadPositionCategoryException;
import org.eclipse.jface.text.DocumentEvent;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.IPositionUpdater;
import org.eclipse.jface.text.Position;


/**
 * Semantic highlighting presenter - Customized for the Java LS.
 *
 * @since 3.0
 */
public class SemanticHighlightingPresenter {

	/**
	 * Semantic highlighting position updater.
	 */
	public static class HighlightingPositionUpdater implements IPositionUpdater {

		/** The position category. */
		private final String fCategory;

		/**
		 * Creates a new updater for the given <code>category</code>.
		 *
		 * @param category the new category.
		 */
		public HighlightingPositionUpdater(String category) {
			fCategory= category;
		}

		/*
		 * @see org.eclipse.jface.text.IPositionUpdater#update(org.eclipse.jface.text.DocumentEvent)
		 */
		@Override
		public void update(DocumentEvent event) {

			int eventOffset= event.getOffset();
			int eventOldLength= event.getLength();
			int eventEnd= eventOffset + eventOldLength;

			try {
				Position[] positions= event.getDocument().getPositions(fCategory);

				for (int i= 0; i != positions.length; i++) {

					HighlightedPosition position= (HighlightedPosition) positions[i];

					// Also update deleted positions because they get deleted by the background thread and removed/invalidated only in the UI runnable
//					if (position.isDeleted())
//						continue;

					int offset= position.getOffset();
					int length= position.getLength();
					int end= offset + length;

					if (offset > eventEnd) {
						updateWithPrecedingEvent(position, event);
					} else if (end < eventOffset) {
						updateWithSucceedingEvent(position, event);
					} else if (offset <= eventOffset && end >= eventEnd) {
						updateWithIncludedEvent(position, event);
					} else if (offset <= eventOffset) {
						updateWithOverEndEvent(position, event);
					} else if (end >= eventEnd) {
						updateWithOverStartEvent(position, event);
					} else {
						updateWithIncludingEvent(position, event);
					}
				}
			} catch (BadPositionCategoryException e) {
				// ignore and return
			}
		}

		/**
		 * Update the given position with the given event. The event precedes the position.
		 *
		 * @param position The position
		 * @param event The event
		 */
		private void updateWithPrecedingEvent(HighlightedPosition position, DocumentEvent event) {
			String newText= event.getText();
			int eventNewLength= newText != null ? newText.length() : 0;
			int deltaLength= eventNewLength - event.getLength();

			position.setOffset(position.getOffset() + deltaLength);
		}

		/**
		 * Update the given position with the given event. The event succeeds the position.
		 *
		 * @param position The position
		 * @param event The event
		 */
		private void updateWithSucceedingEvent(HighlightedPosition position, DocumentEvent event) {
		}

		/**
		 * Update the given position with the given event. The event is included by the position.
		 *
		 * @param position The position
		 * @param event The event
		 */
		private void updateWithIncludedEvent(HighlightedPosition position, DocumentEvent event) {
			int eventOffset= event.getOffset();
			String newText= event.getText();
			if (newText == null)
			 {
				newText= ""; //$NON-NLS-1$
			}
			int eventNewLength= newText.length();

			int deltaLength= eventNewLength - event.getLength();

			int offset= position.getOffset();
			int length= position.getLength();
			int end= offset + length;

			int includedLength= 0;
			while (includedLength < eventNewLength && Character.isJavaIdentifierPart(newText.charAt(includedLength))) {
				includedLength++;
			}
			if (includedLength == eventNewLength) {
				position.setLength(length + deltaLength);
			} else {
				int newLeftLength= eventOffset - offset + includedLength;

				int excludedLength= eventNewLength;
				while (excludedLength > 0 && Character.isJavaIdentifierPart(newText.charAt(excludedLength - 1))) {
					excludedLength--;
				}
				int newRightOffset= eventOffset + excludedLength;
				int newRightLength= end + deltaLength - newRightOffset;

				if (newRightLength == 0) {
					position.setLength(newLeftLength);
				} else {
					if (newLeftLength == 0) {
						position.update(newRightOffset, newRightLength);
					} else {
						position.setLength(newLeftLength);
						// addPositionFromUI(newRightOffset, newRightLength, position.getHighlightingScopes());
						HighlightedPosition highlightedPosition = new HighlightedPosition(newRightOffset, newRightLength, position.getHighlightingScopes(), new Object());
						try {
							event.fDocument.addPosition(fCategory, highlightedPosition);
						} catch (BadLocationException | BadPositionCategoryException e) {
							JavaLanguageServerPlugin.logException("Error when adding new highlighting position to the document", e);
						}
					}
				}
			}
		}

		/**
		 * Update the given position with the given event. The event overlaps with the end of the position.
		 *
		 * @param position The position
		 * @param event The event
		 */
		private void updateWithOverEndEvent(HighlightedPosition position, DocumentEvent event) {
			String newText= event.getText();
			if (newText == null)
			 {
				newText= ""; //$NON-NLS-1$
			}
			int eventNewLength= newText.length();

			int includedLength= 0;
			while (includedLength < eventNewLength && Character.isJavaIdentifierPart(newText.charAt(includedLength))) {
				includedLength++;
			}
			position.setLength(event.getOffset() - position.getOffset() + includedLength);
		}

		/**
		 * Update the given position with the given event. The event overlaps with the start of the position.
		 *
		 * @param position The position
		 * @param event The event
		 */
		private void updateWithOverStartEvent(HighlightedPosition position, DocumentEvent event) {
			int eventOffset= event.getOffset();
			int eventEnd= eventOffset + event.getLength();

			String newText= event.getText();
			if (newText == null)
			 {
				newText= ""; //$NON-NLS-1$
			}
			int eventNewLength= newText.length();

			int excludedLength= eventNewLength;
			while (excludedLength > 0 && Character.isJavaIdentifierPart(newText.charAt(excludedLength - 1))) {
				excludedLength--;
			}
			int deleted= eventEnd - position.getOffset();
			int inserted= eventNewLength - excludedLength;
			position.update(eventOffset + excludedLength, position.getLength() - deleted + inserted);
		}

		/**
		 * Update the given position with the given event. The event includes the position.
		 *
		 * @param position The position
		 * @param event The event
		 */
		private void updateWithIncludingEvent(HighlightedPosition position, DocumentEvent event) {
			position.delete();
			position.update(event.getOffset(), 0);
		}
	}

	/** Position updater */
	public final IPositionUpdater fPositionUpdater = new HighlightingPositionUpdater(getPositionCategory());

	/** UI's current highlighted positions - can contain <code>null</code> elements */
	private List<Position> fPositions= new ArrayList<>();
	/** UI position lock */
	private Object fPositionLock= new Object();

	/** <code>true</code> iff the current reconcile is canceled. */
	private boolean fIsCanceled= false;

	/**
	 * Creates and returns a new highlighted position with the given offset, length and highlighting.
	 * <p>
	 * NOTE: Also called from background thread.
	 * </p>
	 *
	 * @param offset The offset
	 * @param length The length
	 * @param highlighting The highlighting
	 * @return The new highlighted position
	 */
	public HighlightedPosition createHighlightedPosition(int offset, int length, List<String> highlighting) {
		// TODO: reuse deleted positions
		return new HighlightedPosition(offset, length, highlighting, fPositionUpdater);
	}

	/**
	 * Adds all current positions to the given list.
	 * <p>
	 * NOTE: Called from background thread.
	 * </p>
	 *
	 * @param list The list
	 */
	public void addAllPositions(List<Position> list) {
		synchronized (fPositionLock) {
			list.addAll(fPositions);
		}
	}

	/**
	 * Create a runnable for updating the presentation.
	 * <p>
	 * NOTE: Called from background thread.
	 * </p>
	 * @param textPresentation the text presentation
	 * @param addedPositions the added positions
	 * @param removedPositions the removed positions
	 * @return the runnable or <code>null</code>, if reconciliation should be canceled
	 */
	public void createUpdateRunnable(IDocument document, List<Position> addedPositions, List<Position> removedPositions) {

		// TODO: do clustering of positions and post multiple fast runnables
		final HighlightedPosition[] added= new HighlightedPosition[addedPositions.size()];
		addedPositions.toArray(added);
		final HighlightedPosition[] removed= new HighlightedPosition[removedPositions.size()];
		removedPositions.toArray(removed);
		updatePresentation(document, added, removed);
	}

	/**
	 * Invalidate the presentation of the positions based on the given added positions and the existing deleted positions.
	 * Also unregisters the deleted positions from the document and patches the positions of this presenter.
	 * <p>
	 * NOTE: Indirectly called from background thread by UI runnable.
	 * </p>
	 * @param textPresentation the text presentation or <code>null</code>, if the presentation should computed in the UI thread
	 * @param addedPositions the added positions
	 * @param removedPositions the removed positions
	 */
	public void updatePresentation(IDocument document, HighlightedPosition[] addedPositions, HighlightedPosition[] removedPositions) {

//		checkOrdering("added positions: ", Arrays.asList(addedPositions)); //$NON-NLS-1$
//		checkOrdering("removed positions: ", Arrays.asList(removedPositions)); //$NON-NLS-1$
//		checkOrdering("old positions: ", fPositions); //$NON-NLS-1$

		// TODO: double-check consistency with document.getPositions(...)
		// TODO: reuse removed positions
		if (isCanceled()) {
			return;
		}

		String positionCategory= getPositionCategory();

		List<HighlightedPosition> removedPositionsList= Arrays.asList(removedPositions);

		try {
			synchronized (fPositionLock) {
				List<Position> oldPositions= fPositions;
				int newSize= Math.max(fPositions.size() + addedPositions.length - removedPositions.length, 10);

				/*
				 * The following loop is a kind of merge sort: it merges two List<Position>, each
				 * sorted by position.offset, into one new list. The first of the two is the
				 * previous list of positions (oldPositions), from which any deleted positions get
				 * removed on the fly. The second of two is the list of added positions. The result
				 * is stored in newPositions.
				 */
				List<Position> newPositions= new ArrayList<>(newSize);
				Position position= null;
				Position addedPosition= null;
				for (int i= 0, j= 0, n= oldPositions.size(), m= addedPositions.length; i < n || position != null || j < m || addedPosition != null;) {
					// loop variant: i + j < old(i + j)

					// a) find the next non-deleted Position from the old list
					while (position == null && i < n) {
						position= oldPositions.get(i++);
						if (position.isDeleted() || contain(removedPositionsList, position)) {
							document.removePosition(positionCategory, position);
							position= null;
						}
					}

					// b) find the next Position from the added list
					if (addedPosition == null && j < m) {
						addedPosition= addedPositions[j++];
						document.addPosition(positionCategory, addedPosition);
					}

					// c) merge: add the next of position/addedPosition with the lower offset
					if (position != null) {
						if (addedPosition != null) {
							if (position.getOffset() <= addedPosition.getOffset()) {
								newPositions.add(position);
								position= null;
							} else {
								newPositions.add(addedPosition);
								addedPosition= null;
							}
						} else {
							newPositions.add(position);
							position= null;
						}
					} else if (addedPosition != null) {
						newPositions.add(addedPosition);
						addedPosition= null;
					}
				}
				fPositions= newPositions;
			}
		} catch (BadPositionCategoryException e) {
			// Should not happen
			JavaLanguageServerPlugin.logException("Error occurred when updating the presentation.", e);
		} catch (BadLocationException e) {
			// Should not happen
			JavaLanguageServerPlugin.logException("Error occurred when updating the presentation.", e);
		}
//		checkOrdering("new positions: ", fPositions); //$NON-NLS-1$
	}

//	private void checkOrdering(String s, List positions) {
//		Position previous= null;
//		for (int i= 0, n= positions.size(); i < n; i++) {
//			Position current= (Position) positions.get(i);
//			if (previous != null && previous.getOffset() + previous.getLength() > current.getOffset())
//				return;
//		}
//	}

	/**
	 * Returns <code>true</code> iff the positions contain the position.
	 * @param positions the positions, must be ordered by offset but may overlap
	 * @param position the position
	 * @return <code>true</code> iff the positions contain the position
	 */
	private boolean contain(List<? extends Position> positions, Position position) {
		return indexOf(positions, position) != -1;
	}

	/**
	 * Returns index of the position in the positions, <code>-1</code> if not found.
	 * @param positions the positions, must be ordered by offset but may overlap
	 * @param position the position
	 * @return the index
	 */
	private int indexOf(List<? extends Position> positions, Position position) {
		int index= computeIndexAtOffset(positions, position.getOffset());
		int size= positions.size();
		while (index < size) {
			if (positions.get(index) == position) {
				return index;
			}
			index++;
		}
		return -1;
	}

	/**
	 * Returns the index of the first position with an offset equal or greater than the given offset.
	 *
	 * @param positions the positions, must be ordered by offset and must not overlap
	 * @param offset the offset
	 * @return the index of the last position with an offset equal or greater than the given offset
	 */
	private int computeIndexAtOffset(List<? extends Position> positions, int offset) {
		int i= -1;
		int j= positions.size();
		while (j - i > 1) {
			int k= (i + j) >> 1;
			Position position= positions.get(k);
			if (position.getOffset() >= offset) {
				j= k;
			} else {
				i= k;
			}
		}
		return j;
	}

	/**
	 * @return Returns <code>true</code> iff the current reconcile is canceled.
	 * <p>
	 * NOTE: Also called from background thread.
	 * </p>
	 */
	public boolean isCanceled() {
		return fIsCanceled;
	}

	/**
	 * Set whether or not the current reconcile is canceled.
	 *
	 * @param isCanceled <code>true</code> iff the current reconcile is canceled
	 */
	public void setCanceled(boolean isCanceled) {
		fIsCanceled = isCanceled;
	}

	/**
	 * @return The semantic reconciler position's category.
	 */
	public String getPositionCategory() {
		return toString();
	}

}