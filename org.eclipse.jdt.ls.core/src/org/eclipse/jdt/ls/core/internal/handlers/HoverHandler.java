/*******************************************************************************
 * Copyright (c) 2016-2017 Red Hat Inc. and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *     Red Hat Inc. - initial API and implementation
 *******************************************************************************/
package org.eclipse.jdt.ls.core.internal.handlers;

import java.util.Collections;
import java.util.List;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.jdt.core.ITypeRoot;
import org.eclipse.jdt.ls.core.internal.HoverInfoProvider;
import org.eclipse.jdt.ls.core.internal.JDTUtils;
import org.eclipse.jdt.ls.core.internal.preferences.PreferenceManager;
import org.eclipse.lsp4j.Hover;
import org.eclipse.lsp4j.MarkedString;
import org.eclipse.lsp4j.TextDocumentPositionParams;
import org.eclipse.lsp4j.jsonrpc.messages.Either;

public class HoverHandler {

	private final PreferenceManager preferenceManager;

	public HoverHandler(PreferenceManager preferenceManager) {
		this.preferenceManager = preferenceManager;
	}

	public Hover hover(TextDocumentPositionParams position, IProgressMonitor monitor) {
		ITypeRoot unit = null;
		try {
			boolean returnCompilationUnit = preferenceManager == null ? false : preferenceManager.isClientSupportsClassFileContent() && (preferenceManager.getPreferences().isIncludeDecompiledSources());
			unit = JDTUtils.resolveTypeRoot(position.getTextDocument().getUri(), returnCompilationUnit, monitor);
			List<Either<String, MarkedString>> content = null;
			if (unit != null && !monitor.isCanceled()) {
				content = computeHover(unit, position.getPosition().getLine(), position.getPosition().getCharacter(), monitor);
			} else {
				content = Collections.singletonList(Either.forLeft(""));
			}
			Hover $ = new Hover();
			$.setContents(content);
			return $;
		} finally {
			JDTUtils.discardClassFileWorkingCopy(unit);
		}
	}

	private List<Either<String, MarkedString>> computeHover(ITypeRoot unit, int line, int column, IProgressMonitor monitor) {
		HoverInfoProvider provider = new HoverInfoProvider(unit, this.preferenceManager);
		return provider.computeHover(line, column, monitor);
	}

}
