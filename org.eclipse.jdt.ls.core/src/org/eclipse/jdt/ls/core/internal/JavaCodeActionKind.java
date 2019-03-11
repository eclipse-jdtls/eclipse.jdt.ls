/*******************************************************************************
 * Copyright (c) 2018 Red Hat Inc. and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Red Hat Inc. - initial API and implementation
 *******************************************************************************/
package org.eclipse.jdt.ls.core.internal;

import org.eclipse.lsp4j.CodeActionKind;

/**
 * jdt.ls specific Code Action kinds, extending {@link CodeActionKind}
 * hierarchies.
 *
 * @author Fred Bricon
 *
 */
public interface JavaCodeActionKind {

	/**
	 * Base kind for "generate" source actions
	 */
	public static final String SOURCE_GENERATE = CodeActionKind.Source + ".generate";

	/**
	 * Generate accessors kind
	 */
	public static final String SOURCE_GENERATE_ACCESSORS = SOURCE_GENERATE + ".accessors";

	/**
	 * Generate hashCode/equals kind
	 */
	public static final String SOURCE_GENERATE_HASHCODE_EQUALS = SOURCE_GENERATE + ".hashCodeEquals";

	/**
	 * Override/Implement methods kind
	 */
	public static final String SOURCE_OVERRIDE_METHODS = CodeActionKind.Source + ".overrideMethods";

	/**
	 * Extract to method kind
	 */
	public static final String REFACTOR_EXTRACT_METHOD = CodeActionKind.RefactorExtract + ".function";// using `.function` instead of `.method` to match existing keybindings

	/**
	 * Extract to constant kind
	 */
	public static final String REFACTOR_EXTRACT_CONSTANT = CodeActionKind.RefactorExtract + ".constant";

	/**
	 * Extract to variable kind
	 */
	public static final String REFACTOR_EXTRACT_VARIABLE = CodeActionKind.RefactorExtract + ".variable";

}
