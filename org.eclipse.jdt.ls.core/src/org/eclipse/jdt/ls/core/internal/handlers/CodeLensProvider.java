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

import java.util.Arrays;
import java.util.List;

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
 * CodeLens Provider interface. Extensions could extend codelens collection and
 * resolution by implementing the interface
 */
public interface CodeLensProvider {
	/**
	 * Resolve codelens.
	 *
	 * @param lens
	 * @param monitor
	 * @return resolved codelens
	 */
	CodeLens resolveCodeLens(CodeLens lens, IProgressMonitor monitor);

	/**
	 * get type of the provider.
	 *
	 * @return type
	 */
	String getType();

	/**
	 * Collect codelens.
	 *
	 * @param unit
	 *            The compilation unit of a source file.
	 * @param monitor
	 *            monitor.
	 * @return A list of codelens.
	 * @throws JavaModelException
	 */
	List<CodeLens> collectCodeLenses(ICompilationUnit unit, IProgressMonitor monitor) throws JavaModelException;

	/**
	 * Create a codelens.
	 *
	 * @param element
	 *            java element
	 * @param unit
	 *            compilation unit of the source file
	 * @return codelens
	 * @throws JavaModelException
	 */
	default CodeLens createCodeLens(IJavaElement element, ICompilationUnit unit) throws JavaModelException {
		CodeLens lens = new CodeLens();
		ISourceRange r = ((ISourceReference) element).getNameRange();
		final Range range = JDTUtils.toRange(unit, r.getOffset(), r.getLength());
		lens.setRange(range);
		String uri = ResourceUtils.toClientUri(JDTUtils.getFileURI(unit));
		lens.setData(Arrays.asList(uri, range.getStart(), this.getType()));
		return lens;
	}

	/**
	 * Whether the provider could handle a codelens.
	 *
	 * @param lens
	 *            codelens
	 * @return boolean, true means that the codelens could be handled by the
	 *         provider
	 */
	default Boolean couldHandle(CodeLens lens) {
		try {
			List<Object> data = (List<Object>) lens.getData();
			String type = (String) data.get(2);
			return type.equals(this.getType());
		} catch (Exception ex) {
			return false;
		}
	}
}
