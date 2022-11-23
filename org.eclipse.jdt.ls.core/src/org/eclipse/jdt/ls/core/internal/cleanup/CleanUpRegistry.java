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

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.jdt.core.IBuffer;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.manipulation.CleanUpContextCore;
import org.eclipse.jdt.ls.core.internal.JDTUtils;
import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.eclipse.lsp4j.TextEdit;

/**
 * Represents a store of all the clean ups that can be performed on save in
 * eclipse.jdt.ls.
 */
public class CleanUpRegistry {

	private Map<String, ISimpleCleanUp> cleanUps;

	public CleanUpRegistry() {
		List<ISimpleCleanUp> cleanUpsList = new ArrayList<>();
		cleanUpsList.add(new MemberAccessUsesThisCleanUp());
		cleanUpsList.add(new StaticAccessUsesClassNameCleanUp());
		cleanUpsList.add(new AddOverrideAnnotationCleanUp());
		cleanUpsList.add(new AddDeprecatedAnnotationCleanUp());
		cleanUpsList.add(new StringConcatToTextBlockCleanUp());
		cleanUpsList.add(new InvertEqualsCleanUp());
		cleanUpsList.add(new VariableDeclarationFixCleanup());
		cleanUpsList.add(new SwitchExpressionCleanup());
		cleanUpsList.add(new InstanceofPatternMatch());
		cleanUpsList.add(new LambdaExpressionCleanup());

		// Store in a Map so that they can be accessed by ID quickly
		cleanUps = new HashMap<>();
		cleanUpsList.forEach(cleanUp -> {
			cleanUps.put(cleanUp.getIdentifier(), cleanUp);
		});
	}

	/**
	 * Returns a non-null list of text edits to clean up the given text document
	 * according to the clean ups that are enabled.
	 *
	 * @param textDocumentId
	 *            the text document to get the clean up edits for
	 * @param cleanUpEnabled
	 *            the list of enabled clean ups
	 * @param monitor
	 *            the progress monitor
	 * @return a non-null list of text edits to clean up the given text document
	 *         according to the clean ups that are enabled
	 */
	public List<TextEdit> getEditsForAllActiveCleanUps(TextDocumentIdentifier textDocumentId, List<String> cleanUpEnabled, IProgressMonitor monitor) {

		IJavaProject javaProject = JDTUtils.resolveCompilationUnit(textDocumentId.getUri()).getJavaProject();

		List<ISimpleCleanUp> cleanUpsToRun = cleanUpEnabled.stream() //
				.distinct() //
				.map(cleanUpId -> {
					return cleanUps.get(cleanUpId);
				}).filter(cleanUpId -> cleanUpId != null) //
				.toList();
		if (cleanUpsToRun.isEmpty()) {
			return Collections.emptyList();
		}

		List<String> compilerOptsToEnable = cleanUpsToRun.stream() //
				.flatMap(cleanUp -> {
					return cleanUp.getRequiredCompilerMarkers().stream();
				}) //
				.toList();

		// enable required compiler markers that are currently ignored
		Map<String, String> opts = javaProject.getOptions(true);
		for (String compilerOpt : compilerOptsToEnable) {
			String currentOptValue = opts.get(compilerOpt);
			if (currentOptValue == null || currentOptValue.equals(JavaCore.IGNORE)) {
				opts.put(compilerOpt, JavaCore.WARNING);
			}
		}

		// build the context after setting the compiler options so that the built AST has all the required markers
		CleanUpContextCore context = CleanUpUtils.getCleanUpContext(textDocumentId, opts, monitor);
		List<TextEdit> textEdits = new ArrayList<>();
		ICompilationUnit cu = context.getCompilationUnit();

		try {
			ICompilationUnit wc = cu.getWorkingCopy(monitor);
			for (ISimpleCleanUp cleanUp : cleanUpsToRun) {
				org.eclipse.text.edits.TextEdit jdtEdit = CleanUpUtils.getTextEditFromCleanUp(cleanUp, context, monitor);
				if (jdtEdit != null) {
					wc.applyTextEdit(jdtEdit, monitor);
					context = CleanUpUtils.getCleanUpContext(wc, opts, monitor);
				}
			}
			// https://microsoft.github.io/language-server-protocol/specifications/specification-3-16/#textEditArray
			// Cleanups may have overlapping text edits but LSP does not support this
			// Generate text edit as the entire document
			IBuffer wcBuff = wc.getBuffer();
			IBuffer cuBuff = cu.getBuffer();
			String newText = wcBuff.getContents();
			if (!newText.equals(cuBuff.getContents())) {
				TextEdit te = new TextEdit(JDTUtils.toRange(cu, 0, cuBuff.getLength()), newText);
				textEdits.add(te);
			}

		} catch (JavaModelException e) {
			// continue
		}

		return textEdits;
	}

}
