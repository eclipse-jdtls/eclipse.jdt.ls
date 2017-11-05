/*******************************************************************************
 * Copyright (c) 2017 Microsoft Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Microsoft Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.jdt.ls.core.internal.handlers;

import java.util.ArrayList;
import java.util.Arrays;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.ISourceRange;
import org.eclipse.jdt.core.ISourceReference;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.ls.core.internal.JDTUtils;
import org.eclipse.jdt.ls.core.internal.ResourceUtils;
import org.eclipse.lsp4j.CodeLens;
import org.eclipse.lsp4j.Range;

/**
 * @author xuzho
 *
 */
public interface CodeLensProvider {
	CodeLens resolveCodeLens(CodeLens lens, IProgressMonitor monitor);

	String getType();

	ArrayList<CodeLens> collectCodeLenses(ICompilationUnit unit, IJavaElement[] elements, IProgressMonitor monitor) throws JavaModelException;

	default CodeLens getCodeLens(String type, IJavaElement element, ICompilationUnit unit) throws JavaModelException {
		CodeLens lens = new CodeLens();
		ISourceRange r = ((ISourceReference) element).getNameRange();
		final Range range = JDTUtils.toRange(unit, r.getOffset(), r.getLength());
		lens.setRange(range);
		String uri = ResourceUtils.toClientUri(JDTUtils.getFileURI(unit));
		lens.setData(Arrays.asList(uri, range.getStart(), type));
		return lens;
	}
}
