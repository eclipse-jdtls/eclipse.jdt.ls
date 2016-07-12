/*******************************************************************************
 * Copyright (c) 2000, 2011 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package copied.org.eclipse.jdt.internal.ui.search;

import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.CompilationUnit;

public interface IOccurrencesFinder {

	public static final int K_OCCURRENCE = 5;

	public static final int K_EXCEPTION_OCCURRENCE = 6;
	public static final int K_EXIT_POINT_OCCURRENCE = 7;
	public static final int K_IMPLEMENTS_OCCURRENCE = 8;
	public static final int K_BREAK_TARGET_OCCURRENCE = 9;

	public static final int F_WRITE_OCCURRENCE = 1;
	public static final int F_READ_OCCURRENCE = 2;
	public static final int F_EXCEPTION_DECLARATION = 8;

	/**
	 * Element representing a occurrence
	 */
	public static class OccurrenceLocation {
		private final int fOffset;
		private final int fLength;
		private final int fFlags;

		public OccurrenceLocation(int offset, int length, int flags) {
			fOffset = offset;
			fLength = length;
			fFlags = flags;
		}

		public int getOffset() {
			return fOffset;
		}

		public int getLength() {
			return fLength;
		}

		public int getFlags() {
			return fFlags;
		}

		@Override
		public String toString() {
			return "[" + fOffset + " / " + fLength + "] "; //$NON-NLS-1$//$NON-NLS-2$
		}

	}

	public String initialize(CompilationUnit root, int offset, int length);

	public String initialize(CompilationUnit root, ASTNode node);

	/**
	 * Returns the name of the element to look for or <code>null</code> if the
	 * finder hasn't been initialized yet.
	 * 
	 * @return the name of the element
	 */
	public String getElementName();

	/**
	 * Returns the AST root.
	 *
	 * @return the AST root
	 */
	public CompilationUnit getASTRoot();

	/**
	 * Returns the occurrences
	 *
	 * @return the occurrences
	 */
	public OccurrenceLocation[] getOccurrences();

	public int getSearchKind();

	/**
	 * Returns the id of this finder.
	 * 
	 * @return returns the id of this finder.
	 */
	public String getID();

}
