/*******************************************************************************
 * Copyright (c) 2022 Red Hat Inc. and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *     Red Hat Inc. - initial API and implementation
 *******************************************************************************/
package org.eclipse.jdt.ls.core.internal.cleanup;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.manipulation.CleanUpContextCore;
import org.eclipse.jdt.core.manipulation.ICleanUpFixCore;
import org.eclipse.jdt.core.refactoring.CompilationUnitChange;
import org.eclipse.jdt.internal.corext.dom.IASTSharedValues;
import org.eclipse.jdt.ls.core.internal.JDTUtils;
import org.eclipse.jdt.ls.core.internal.JavaLanguageServerPlugin;
import org.eclipse.jdt.ls.core.internal.TextEditConverter;
import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.eclipse.lsp4j.TextEdit;

/**
 * Functions for working with JDT ICleanUpCore and ISimpleCleanUp.
 */
public class CleanUpUtils {

	private CleanUpUtils() {
	}

	/**
	 * Returns the clean up context for the given text document.
	 *
	 * @param textDocumentId
	 *            the text document to get the clean up context for
	 * @param compilerOpts
	 *            the compiler options to use for the AST
	 * @param monitor
	 *            the progress monitor
	 * @return the clean up context for the given text document
	 */
	public static CleanUpContextCore getCleanUpContext(TextDocumentIdentifier textDocumentId, Map<String, String> compilerOpts, IProgressMonitor monitor) {
		ICompilationUnit unit = JDTUtils.resolveCompilationUnit(textDocumentId.getUri());
		CompilationUnit ast = createASTWithOpts(unit, compilerOpts, monitor);
		return new CleanUpContextCore(unit, ast);
	}

	/**
	 * Returns a non-null list of text edits for the given clean up.
	 *
	 * @param cleanUp
	 *            the clean up to get the edits for
	 * @param context
	 *            the context to perform the clean up on
	 * @param monitor
	 *            the progress monitor
	 * @return a non-null list of text edits for the given clean up
	 */
	public static List<TextEdit> getTextEditFromCleanUp(ISimpleCleanUp cleanUp, CleanUpContextCore context, IProgressMonitor monitor) {

		try {
			ICleanUpFixCore fix = cleanUp.createFix(context);
			if (fix == null) {
				return Collections.emptyList();
			}
			CompilationUnitChange cleanUpChange = fix.createChange(monitor);
			org.eclipse.text.edits.TextEdit jdtEdit = cleanUpChange.getEdit();
			if (jdtEdit == null) {
				return Collections.emptyList();
			}
			TextEditConverter converter = new TextEditConverter(context.getCompilationUnit(), jdtEdit);
			List<TextEdit> lspEdit = converter.convert();
			return lspEdit != null ? lspEdit : Collections.emptyList();
		} catch (CoreException e) {
			JavaLanguageServerPlugin.logError(String.format("Failed to create text edit for clean up %s", cleanUp.getIdentifier()));
		}
		return Collections.emptyList();
	}

	private static CompilationUnit createASTWithOpts(ICompilationUnit cu, Map<String, String> opts, IProgressMonitor monitor) {
		ASTParser astParser = ASTParser.newParser(IASTSharedValues.SHARED_AST_LEVEL);
		astParser.setSource(cu);
		astParser.setResolveBindings(true);
		astParser.setStatementsRecovery(IASTSharedValues.SHARED_AST_STATEMENT_RECOVERY);
		astParser.setBindingsRecovery(IASTSharedValues.SHARED_BINDING_RECOVERY);
		astParser.setCompilerOptions(opts);
		return (CompilationUnit) astParser.createAST(monitor);
	}

}
