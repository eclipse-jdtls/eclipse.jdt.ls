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
package org.eclipse.jdt.ls.core.internal.codelens;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.ISourceRange;
import org.eclipse.jdt.core.ISourceReference;
import org.eclipse.jdt.core.ITypeRoot;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.ls.core.internal.JDTUtils;
import org.eclipse.jdt.ls.core.internal.ResourceUtils;
import org.eclipse.jdt.ls.core.internal.preferences.PreferenceManager;
import org.eclipse.lsp4j.CodeLens;
import org.eclipse.lsp4j.Position;
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
	 * @param typeRoot
	 *            java type root.
	 * @param monitor
	 *            monitor.
	 * @return A list of codelens.
	 * @throws JavaModelException
	 */
	List<CodeLens> collectCodeLenses(ITypeRoot typeRoot, IProgressMonitor monitor) throws JavaModelException;

	/**
	 * Accept preferences manager which may contain configuration for this codelens
	 * provider.
	 *
	 * @param preferences
	 *            manager
	 */
	default void setPreferencesManager(PreferenceManager pm) {
	};

	/**
	 * Create a codelens.
	 *
	 * @param element
	 *            java element
	 * @param typeRoot
	 *            java type root
	 * @return codelens
	 * @throws JavaModelException
	 */
	default CodeLens createCodeLens(IJavaElement element, ITypeRoot typeRoot) throws JavaModelException {
		CodeLens lens = new CodeLens();
		ISourceRange r = ((ISourceReference) element).getNameRange();
		final Range range = JDTUtils.toRange(typeRoot, r.getOffset(), r.getLength());
		lens.setRange(range);
		String uri = ResourceUtils.toClientUri(JDTUtils.toUri(typeRoot));
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

	default String getCodeLensUri(CodeLens lens) {
		try {
			List<Object> data = (List<Object>) lens.getData();
			return (String) data.get(0);
		} catch (Exception ex) {
			return null;
		}
	}

	default Position getCodeLensStartPos(CodeLens lens) {
		try {
			List<Object> data = (List<Object>) lens.getData();
			Map<String, Object> position = (Map<String, Object>) data.get(1);
			int lineNumber = ((Double) position.get("line")).intValue();
			int character = ((Double) position.get("character")).intValue();
			return new Position(lineNumber, character);
		} catch (Exception ex) {
			return null;
		}
	}
}
