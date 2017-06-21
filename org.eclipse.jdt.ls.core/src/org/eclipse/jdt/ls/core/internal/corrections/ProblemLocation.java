/*******************************************************************************
 * Copyright (c) 2000, 2011 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Copied from /org.eclipse.jdt.ui/src/org/eclipse/jdt/internal/ui/text/correction/ProblemLocation.java
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.jdt.ls.core.internal.corrections;

import org.eclipse.jdt.core.IJavaModelMarker;
import org.eclipse.jdt.core.compiler.IProblem;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.NodeFinder;

public class ProblemLocation implements IProblemLocation {

	private int offset;
	private int length;
	private int problemId;
	private boolean isError;

	public ProblemLocation(int offset, int length, int problemId, boolean isError) {
		this.offset = offset;
		this.length = length;
		this.problemId = problemId;
		this.isError = isError;
	}

	/* (non-Javadoc)
	 * @see org.eclipse.jdt.ls.core.internal.corrections.IProblemLocation#getOffset()
	 */
	@Override
	public int getOffset() {
		return this.offset;
	}

	/* (non-Javadoc)
	 * @see org.eclipse.jdt.ls.core.internal.corrections.IProblemLocation#getLength()
	 */
	@Override
	public int getLength() {
		return this.length;
	}

	/* (non-Javadoc)
	 * @see org.eclipse.jdt.ls.core.internal.corrections.IProblemLocation#getMarkerType()
	 */
	@Override
	public String getMarkerType() {
		return IJavaModelMarker.JAVA_MODEL_PROBLEM_MARKER;
	}

	/* (non-Javadoc)
	 * @see org.eclipse.jdt.ls.core.internal.corrections.IProblemLocation#getProblemId()
	 */
	@Override
	public int getProblemId() {
		return this.problemId;
	}

	/* (non-Javadoc)
	 * @see org.eclipse.jdt.ls.core.internal.corrections.IProblemLocation#getProblemArguments()
	 */
	@Override
	public String[] getProblemArguments() {
		return new String[0];
	}

	/* (non-Javadoc)
	 * @see org.eclipse.jdt.ls.core.internal.corrections.IProblemLocation#isError()
	 */
	@Override
	public boolean isError() {
		return this.isError;
	}

	@Override
	public ASTNode getCoveringNode(CompilationUnit astRoot) {
		NodeFinder finder = new NodeFinder(astRoot, this.offset, this.length);
		return finder.getCoveringNode();
	}

	@Override
	public ASTNode getCoveredNode(CompilationUnit astRoot) {
		NodeFinder finder = new NodeFinder(astRoot, this.offset, this.length);
		return finder.getCoveredNode();
	}

	@Override
	public String toString() {
		StringBuffer buf = new StringBuffer();
		buf.append("Id: ").append(getErrorCode(problemId)).append('\n'); //$NON-NLS-1$
		buf.append('[').append(offset).append(", ").append(length).append(']').append('\n'); //$NON-NLS-1$
		return buf.toString();
	}

	private String getErrorCode(int code) {
		StringBuffer buf = new StringBuffer();

		if ((code & IProblem.TypeRelated) != 0) {
			buf.append("TypeRelated + "); //$NON-NLS-1$
		}
		if ((code & IProblem.FieldRelated) != 0) {
			buf.append("FieldRelated + "); //$NON-NLS-1$
		}
		if ((code & IProblem.ConstructorRelated) != 0) {
			buf.append("ConstructorRelated + "); //$NON-NLS-1$
		}
		if ((code & IProblem.MethodRelated) != 0) {
			buf.append("MethodRelated + "); //$NON-NLS-1$
		}
		if ((code & IProblem.ImportRelated) != 0) {
			buf.append("ImportRelated + "); //$NON-NLS-1$
		}
		if ((code & IProblem.Internal) != 0) {
			buf.append("Internal + "); //$NON-NLS-1$
		}
		if ((code & IProblem.Syntax) != 0) {
			buf.append("Syntax + "); //$NON-NLS-1$
		}
		if ((code & IProblem.Javadoc) != 0) {
			buf.append("Javadoc + "); //$NON-NLS-1$
		}
		buf.append(code & IProblem.IgnoreCategoriesMask);

		return buf.toString();
	}

}
