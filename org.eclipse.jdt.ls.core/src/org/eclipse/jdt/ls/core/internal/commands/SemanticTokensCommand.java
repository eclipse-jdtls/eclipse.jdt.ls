/*******************************************************************************
 * Copyright (c) 2020 Microsoft Corporation and others.
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

package org.eclipse.jdt.ls.core.internal.commands;

import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.jdt.core.ITypeRoot;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.manipulation.CoreASTProvider;
import org.eclipse.jdt.ls.core.internal.JDTUtils;
import org.eclipse.jdt.ls.core.internal.JobHelpers;
import org.eclipse.jdt.ls.core.internal.handlers.DocumentLifeCycleHandler;
import org.eclipse.jdt.ls.core.internal.semantictokens.SemanticTokens;
import org.eclipse.jdt.ls.core.internal.semantictokens.SemanticTokensVisitor;

public class SemanticTokensCommand {
	public static SemanticTokens provide(String uri) {
		JobHelpers.waitForJobs(DocumentLifeCycleHandler.DOCUMENT_LIFE_CYCLE_JOBS, null);
		return doProvide(uri);
	}

	private static SemanticTokens doProvide(String uri) {
		ITypeRoot typeRoot = JDTUtils.resolveTypeRoot(uri);
		if (typeRoot == null) {
			return new SemanticTokens(new int[0]);
		}

		CompilationUnit root = CoreASTProvider.getInstance().getAST(typeRoot, CoreASTProvider.WAIT_YES, new NullProgressMonitor());
		if (root == null) {
			return new SemanticTokens(new int[0]);
		}

		SemanticTokensVisitor collector = new SemanticTokensVisitor(root);
		root.accept(collector);
		return collector.getSemanticTokens();
	}
}
