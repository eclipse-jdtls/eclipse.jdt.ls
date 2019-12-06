/*******************************************************************************
 * Copyright (c) 2017 Microsoft Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *     Microsoft Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.jdt.ls.core.internal.corrections;

import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.NodeFinder;
import org.eclipse.jdt.internal.core.manipulation.dom.ASTResolving;

public class InnovationContext implements IInvocationContext {

	private final ICompilationUnit fCompilationUnit;
	private CompilationUnit fASTRoot;
	private NodeFinder fNodeFinder;
	private int fSelectionLength;
	private int fSelectionOffset;

	public InnovationContext(ICompilationUnit compilationUnit, int selectionOffset, int selectionLength) {
		fCompilationUnit = compilationUnit;
		fSelectionLength = selectionLength;
		fSelectionOffset = selectionOffset;
	}

	/**
	 * Returns the compilation unit.
	 *
	 * @return an <code>ICompilationUnit</code>
	 */
	@Override
	public ICompilationUnit getCompilationUnit() {
		return fCompilationUnit;
	}

	/**
	 * Returns the length.
	 *
	 * @return int
	 */
	@Override
	public int getSelectionLength() {
		return fSelectionLength;
	}

	/**
	 * Returns the offset.
	 *
	 * @return int
	 */
	@Override
	public int getSelectionOffset() {
		return fSelectionOffset;
	}

	@Override
	public CompilationUnit getASTRoot() {
		if (fASTRoot == null) {
			fASTRoot = ASTResolving.createQuickFixAST(fCompilationUnit, null);
		}
		return fASTRoot;
	}

	/**
	 * @param root
	 *            The ASTRoot to set.
	 */
	public void setASTRoot(CompilationUnit root) {
		fASTRoot = root;
	}

	@Override
	public ASTNode getCoveringNode() {
		if (fNodeFinder == null) {
			fNodeFinder = new NodeFinder(getASTRoot(), fSelectionOffset, fSelectionLength);
		}
		return fNodeFinder.getCoveringNode();
	}

	@Override
	public ASTNode getCoveredNode() {
		if (fNodeFinder == null) {
			fNodeFinder = new NodeFinder(getASTRoot(), fSelectionOffset, fSelectionLength);
		}
		return fNodeFinder.getCoveredNode();
	}

}
