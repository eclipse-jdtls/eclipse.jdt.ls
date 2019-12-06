/*******************************************************************************
 * Copyright (c) 2000, 2011 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 *  Originally copied from org.eclipse.jdt.internal.corext.refactoring.util.NoCommentSourceRangeComputer
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.jdt.ls.core.internal.corext.refactoring.util;

import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.rewrite.TargetSourceRangeComputer;

public class NoCommentSourceRangeComputer extends TargetSourceRangeComputer {
	@Override
	public SourceRange computeSourceRange(ASTNode node) {
		return new SourceRange(node.getStartPosition(), node.getLength());
	}
}
