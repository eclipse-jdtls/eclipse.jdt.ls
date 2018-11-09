/*******************************************************************************
 * Copyright (c) 2018 Microsoft Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Microsoft Corporation - initial API and implementation
 *******************************************************************************/

package org.eclipse.jdt.ls.core.internal.handlers;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.manipulation.CoreASTProvider;
import org.eclipse.jdt.internal.core.manipulation.search.IOccurrencesFinder.OccurrenceLocation;
import org.eclipse.jdt.internal.core.manipulation.search.OccurrencesFinder;
import org.eclipse.jdt.ls.core.internal.JDTUtils;
import org.eclipse.jdt.ls.core.internal.JavaLanguageServerPlugin;
import org.eclipse.lsp4j.PrepareRenameResult;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.TextDocumentPositionParams;
import org.eclipse.lsp4j.jsonrpc.messages.Either;

public class PrepareRenameHandler {

	public PrepareRenameHandler() {
	}

	public Either<Range, PrepareRenameResult> prepareRename(TextDocumentPositionParams params, IProgressMonitor monitor) {

		final ICompilationUnit unit = JDTUtils.resolveCompilationUnit(params.getTextDocument().getUri());
		if (unit != null) {
			try {
				OccurrencesFinder finder = new OccurrencesFinder();
				CompilationUnit ast = CoreASTProvider.getInstance().getAST(unit, CoreASTProvider.WAIT_YES, monitor);

				if (ast != null) {
					int offset = JsonRpcHelpers.toOffset(unit.getBuffer(), params.getPosition().getLine(), params.getPosition().getCharacter());
					String error = finder.initialize(ast, offset, 0);
					if (error == null) {
						OccurrenceLocation[] occurrences = finder.getOccurrences();
						if (occurrences != null) {
							for (OccurrenceLocation loc : occurrences) {
								if (monitor.isCanceled()) {
									return Either.forLeft(new Range());
								}
								if (loc.getOffset() <= offset && loc.getOffset() + loc.getLength() >= offset) {
									return Either.forLeft(JDTUtils.toRange(unit, loc.getOffset(), loc.getLength()));
								}
							}
						}
					}
				}

			} catch (CoreException e) {
				JavaLanguageServerPlugin.logException("Problem with compute occurrences for" + unit.getElementName() + " in prepareRename", e);
			}
		}
		return Either.forLeft(new Range());
	}
}
