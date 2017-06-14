/*******************************************************************************
 * Copyright (c) 2000, 2016 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Copied from /org.eclipse.jdt.ui/src/org/eclipse/jdt/internal/corext/codemanipulation/StubUtility.java
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *     John Kaplan, johnkaplantech@gmail.com - 108071 [code templates] template for body of newly created class
 *******************************************************************************/
package org.eclipse.jdt.ls.core.internal.corext.codemanipulation;

import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.rewrite.ImportRewrite;

/**
 * Implementations for {@link CodeGeneration} APIs, and other helper methods
 * to create source code stubs based on {@link IJavaElement}s.
 *
 * @see StubUtility2
 */
public class StubUtility {


	public static ImportRewrite createImportRewrite(ICompilationUnit cu, boolean restoreExistingImports) throws JavaModelException {
		return ImportRewrite.create(cu, restoreExistingImports);
	}

	/**
	 * Returns a {@link ImportRewrite} using {@link ImportRewrite#create(CompilationUnit, boolean)} and
	 * configures the rewriter with the settings as specified in the JDT UI preferences.
	 * <p>
	 * This method sets {@link ImportRewrite#setUseContextToFilterImplicitImports(boolean)} to <code>true</code>
	 * iff the given AST has been resolved with bindings. Clients should always supply a context
	 * when they call one of the <code>addImport(...)</code> methods.
	 * </p>
	 *
	 * @param astRoot the AST root to create the rewriter on
	 * @param restoreExistingImports specifies if the existing imports should be kept or removed.
	 * @return the new rewriter configured with the settings as specified in the JDT UI preferences.
	 *
	 * @see ImportRewrite#create(CompilationUnit, boolean)
	 */
	public static ImportRewrite createImportRewrite(CompilationUnit astRoot, boolean restoreExistingImports) {
		ImportRewrite rewrite = ImportRewrite.create(astRoot, restoreExistingImports);
		if (astRoot.getAST().hasResolvedBindings()) {
			rewrite.setUseContextToFilterImplicitImports(true);
		}
		return rewrite;
	}

}
