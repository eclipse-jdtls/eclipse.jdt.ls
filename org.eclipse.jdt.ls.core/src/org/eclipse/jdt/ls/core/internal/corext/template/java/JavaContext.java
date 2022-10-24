/*******************************************************************************
 * Copyright (c) 2000, 2019 IBM Corporation and others.
 *
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *     Lars Vogel  <lars.vogel@gmail.com> - [templates][content assist] Ctrl+Space without any starting letter shows to no templates - https://bugs.eclipse.org/406463
 *     Lukas Hanke <hanke@yatta.de> - [templates][content assist] Content assist for 'for' loop should suggest member variables - https://bugs.eclipse.org/117215
 *     Nicolaj Hoess <nicohoess@gmail.com> - Make some internal methods accessible to help Postfix Code Completion plug-in - https://bugs.eclipse.org/433500
 *     Microsoft Corporation - moved template related code to jdt.core.manipulation - https://bugs.eclipse.org/549989
 *******************************************************************************/

package org.eclipse.jdt.ls.core.internal.corext.template.java;

import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.internal.corext.template.java.JavaContextCore;
import org.eclipse.jdt.internal.ui.text.template.contentassist.MultiVariable;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.Position;
import org.eclipse.jface.text.templates.TemplateContextType;

/**
 * Copied from org.eclipse.jdt.internal.corext.template.java.JavaContext
 * UI related code is removed.
 */
public class JavaContext extends JavaContextCore {
	/** A global state for proposals that change if a master proposal changes. */
	protected MultiVariableGuess fMultiVariableGuess;

	/**
	 * Creates a java template context.
	 *
	 * @param type   the context type.
	 * @param document the document.
	 * @param completionOffset the completion offset within the document.
	 * @param completionLength the completion length.
	 * @param compilationUnit the compilation unit (may be <code>null</code>).
	 */
	public JavaContext(TemplateContextType type, IDocument document, int completionOffset, int completionLength, ICompilationUnit compilationUnit) {
		super(type, document, completionOffset, completionLength, compilationUnit);
	}

	/**
	 * Creates a java template context.
	 *
	 * @param type   the context type.
	 * @param document the document.
	 * @param completionPosition the position defining the completion offset and length
	 * @param compilationUnit the compilation unit (may be <code>null</code>).
	 * @since 3.2
	 */
	public JavaContext(TemplateContextType type, IDocument document, Position completionPosition, ICompilationUnit compilationUnit) {
		super(type, document, completionPosition, compilationUnit);
	}

	/**
	 * Adds a multi-variable guess dependency.
	 *
	 * @param master the master variable - <code>slave</code> needs to be updated when
	 *        <code>master</code> changes
	 * @param slave the dependent variable
	 * @since 3.3
	 */
	@Override
	public void addDependency(MultiVariable master, MultiVariable slave) {
		if (this.fMultiVariableGuess == null) {
			this.fMultiVariableGuess = new MultiVariableGuess();
		}

		this.fMultiVariableGuess.addDependency(master, slave);
	}

	public MultiVariableGuess getMultiVariableGuess() {
		return this.fMultiVariableGuess;
	}
}
