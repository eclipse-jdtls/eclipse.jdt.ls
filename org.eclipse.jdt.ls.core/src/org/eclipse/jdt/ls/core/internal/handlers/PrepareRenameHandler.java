/*******************************************************************************
 * Copyright (c) 2018 Microsoft Corporation and others.
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

package org.eclipse.jdt.ls.core.internal.handlers;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.IBinding;
import org.eclipse.jdt.core.dom.Name;
import org.eclipse.jdt.core.manipulation.CoreASTProvider;
import org.eclipse.jdt.internal.core.manipulation.search.IOccurrencesFinder.OccurrenceLocation;
import org.eclipse.jdt.internal.core.manipulation.search.OccurrencesFinder;
import org.eclipse.jdt.ls.core.internal.JDTUtils;
import org.eclipse.jdt.ls.core.internal.JavaLanguageServerPlugin;
import org.eclipse.jdt.ls.core.internal.corext.refactoring.RefactoringAvailabilityTester;
import org.eclipse.jdt.ls.core.internal.corrections.InnovationContext;
import org.eclipse.lsp4j.PrepareRenameResult;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.TextDocumentPositionParams;
import org.eclipse.lsp4j.jsonrpc.ResponseErrorException;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.eclipse.lsp4j.jsonrpc.messages.ResponseError;
import org.eclipse.lsp4j.jsonrpc.messages.ResponseErrorCode;

public class PrepareRenameHandler {

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
									InnovationContext context = new InnovationContext(unit, loc.getOffset(), loc.getLength());
									context.setASTRoot(ast);
									ASTNode node = context.getCoveredNode();
									// Rename package is not fully supported yet.
									if (!isBinaryOrPackage(node)) {
										return Either.forLeft(JDTUtils.toRange(unit, loc.getOffset(), loc.getLength()));
									}
								}
							}
						}
					}
				}

			} catch (CoreException e) {
				JavaLanguageServerPlugin.logException("Problem computing occurrences for" + unit.getElementName() + " in prepareRename", e);
			}
		}
		throw new ResponseErrorException(new ResponseError(ResponseErrorCode.InvalidRequest, "Renaming this element is not supported.", null));
	}

	private boolean isBinaryOrPackage(ASTNode node) {
		if (node instanceof Name) {
			IBinding resolvedBinding = ((Name) node).resolveBinding();
			IJavaElement element = resolvedBinding != null ? resolvedBinding.getJavaElement() : null;
			try {
				if (element == null || element.getElementType() == IJavaElement.PACKAGE_FRAGMENT || !RefactoringAvailabilityTester.isRenameElementAvailable(element)) {
					return true;
				}
			} catch (CoreException e) {
				JavaLanguageServerPlugin.logException(e.getMessage(), e);
				return true;
			}
		}
		return false;
	}

}
