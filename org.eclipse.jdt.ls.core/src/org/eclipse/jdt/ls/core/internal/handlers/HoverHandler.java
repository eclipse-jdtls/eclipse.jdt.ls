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
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.ITypeRoot;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.ls.core.internal.HoverInfoProvider;
import org.eclipse.jdt.ls.core.internal.JDTUtils;
import org.eclipse.jdt.ls.core.internal.JavaLanguageServerPlugin;
import org.eclipse.jdt.ls.core.internal.jsh.JshDocumentManager;
import org.eclipse.jdt.ls.core.internal.preferences.PreferenceManager;
import org.eclipse.lsp4j.Hover;
import org.eclipse.lsp4j.MarkedString;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.TextDocumentPositionParams;
import org.eclipse.lsp4j.jsonrpc.messages.Either;

public class HoverHandler {

	private final PreferenceManager preferenceManager;

	public HoverHandler(PreferenceManager preferenceManager) {
		this.preferenceManager = preferenceManager;
	}

	public Hover hover(TextDocumentPositionParams position, IProgressMonitor monitor) {
		if (preferenceManager != null && !preferenceManager.getPreferences().isHoverJavadocEnabled()) {
			return null;
		}
		String uri = position.getTextDocument().getUri();
		if (JDTUtils.isJshFile(uri)) {
			return hoverJsh(uri, position.getPosition(), monitor);
		}
		ITypeRoot unit = null;
		try {
			boolean returnCompilationUnit = preferenceManager == null ? false : preferenceManager.isClientSupportsClassFileContent() && (preferenceManager.getPreferences().isIncludeDecompiledSources());
			unit = JDTUtils.resolveTypeRoot(uri, returnCompilationUnit, monitor);
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

	/**
	 * Hover for .jsh files: the client's position is in the original .jsh document's coordinates,
	 * which must be translated to a line/column in the synthetic wrapped source before calling
	 * {@link HoverInfoProvider#computeHover}.
	 * <p>
	 * Deliberately not calling {@link JDTUtils#discardClassFileWorkingCopy} here - unlike the
	 * normal path's unit, this working copy is the long-lived one owned by
	 * {@link JshDocumentManager}, discarded only on {@code didClose}, not per-request.
	 * <p>
	 * Known gap, tracked on https://github.com/eclipse-jdtls/eclipse.jdt.ls/issues/3973: the
	 * "Source: *link*" line {@link HoverInfoProvider#getSourceInfo} sometimes includes points at
	 * the synthetic document's line number (via {@code JDTUtils.toLocation}), not the original
	 * .jsh line - that translation was not adapted for this narrower path.
	 */
	private Hover hoverJsh(String uri, Position originalPosition, IProgressMonitor monitor) {
		JshDocumentManager.JshDocument document = JshDocumentManager.getInstance().get(uri);
		List<Either<String, MarkedString>> content = null;
		if (document != null && !monitor.isCanceled()) {
			ICompilationUnit unit = document.getWorkingCopy();
			int syntheticOffset = document.toSyntheticOffset(originalPosition);
			if (syntheticOffset > -1) {
				try {
					int[] line = JsonRpcHelpers.toLine(unit.getBuffer(), syntheticOffset);
					content = computeHover(unit, line[0], line[1], monitor);
				} catch (JavaModelException e) {
					JavaLanguageServerPlugin.logException("Failed to translate .jsh hover position", e);
				}
			}
		}
		if (content == null) {
			content = Collections.singletonList(Either.forLeft(""));
		}
		Hover $ = new Hover();
		$.setContents(content);
		return $;
	}

	private List<Either<String, MarkedString>> computeHover(ITypeRoot unit, int line, int column, IProgressMonitor monitor) {
		HoverInfoProvider provider = new HoverInfoProvider(unit, this.preferenceManager);
		return provider.computeHover(line, column, monitor);
	}

}
