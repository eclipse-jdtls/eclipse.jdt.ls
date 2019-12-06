/*******************************************************************************
 * Copyright (c) 2009 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Copied from org.eclipse.jdt.internal.corext.SourceRangeFactory
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.jdt.ls.core.internal.corext;

import org.eclipse.jdt.core.ISourceRange;
import org.eclipse.jdt.core.SourceRange;
import org.eclipse.jdt.core.compiler.IProblem;
import org.eclipse.jdt.core.dom.ASTNode;

public class SourceRangeFactory {

	public static ISourceRange create(ASTNode node) {
		return new SourceRange(node.getStartPosition(), node.getLength());
	}

	public static ISourceRange create(IProblem problem) {
		return new SourceRange(problem.getSourceStart(), problem.getSourceEnd() - problem.getSourceStart() + 1);
	}

}
