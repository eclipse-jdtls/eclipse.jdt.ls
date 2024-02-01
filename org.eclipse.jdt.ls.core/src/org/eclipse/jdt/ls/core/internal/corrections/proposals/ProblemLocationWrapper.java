/*******************************************************************************
 * Copyright (c) 2024 Red Hat Inc. and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *     Red Hat Inc. - initial API and implementation
 *******************************************************************************/
package org.eclipse.jdt.ls.core.internal.corrections.proposals;

import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.internal.ui.text.correction.IProblemLocationCore;

public class ProblemLocationWrapper implements IProblemLocationCore {
	private IProblemLocationCore delegate;

	public ProblemLocationWrapper(IProblemLocationCore del) {
		this.delegate = del;
	}

	@Override
	public int getOffset() {
		return this.delegate.getOffset();
	}

	@Override
	public int getLength() {
		return this.delegate.getLength();
	}

	@Override
	public String getMarkerType() {
		return this.delegate.getMarkerType();
	}

	@Override
	public int getProblemId() {
		return this.delegate.getProblemId();
	}

	@Override
	public String[] getProblemArguments() {
		return this.delegate.getProblemArguments();
	}

	@Override
	public boolean isError() {
		return this.delegate.isError();
	}

	@Override
	public ASTNode getCoveringNode(CompilationUnit astRoot) {
		return this.delegate.getCoveringNode(astRoot);
	}
	@Override
	public ASTNode getCoveredNode(CompilationUnit astRoot) {
		return this.delegate.getCoveredNode(astRoot);
	}
}