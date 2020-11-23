/*******************************************************************************
 * Copyright (c) 2017-2021 Microsoft Corporation and others.
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

package org.eclipse.jdt.ls.core.internal.correction;

import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.compiler.IProblem;
import org.eclipse.jdt.ls.core.internal.CodeActionUtil;
import org.eclipse.lsp4j.Range;

public class AbstractSelectionTest extends AbstractQuickFixTest {

	protected int[] getSelection(String source) {
		return CodeActionUtil.getSelection(source);
	}

	@Override
	protected Range getRange(ICompilationUnit cu, IProblem[] problems) throws JavaModelException {
		return CodeActionUtil.getRange(cu);
	}
}
