/*******************************************************************************
 * Copyright (c) 2018-2019 Red Hat Inc. and others.
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
	 * Generate toString kind
	 */
	public static final String SOURCE_GENERATE_TO_STRING = SOURCE_GENERATE + ".toString";

	/**
	 * Generate constructors kind
	 */
	public static final String SOURCE_GENERATE_CONSTRUCTORS = SOURCE_GENERATE + ".constructors";

	/**
	 * Generate delegate methods
	 */
	public static final String SOURCE_GENERATE_DELEGATE_METHODS = SOURCE_GENERATE + ".delegateMethods";

	/**
	 * Generate final modifiers where possible
	 */
	public static final String SOURCE_GENERATE_FINAL_MODIFIERS = SOURCE_GENERATE + ".finalModifiers";

	/**
	 * Override/Implement methods kind
	 */
	public static final String SOURCE_OVERRIDE_METHODS = CodeActionKind.Source + ".overrideMethods";

	/**
	 * Sort members kind
	 */
	public static final String SOURCE_SORT_MEMBERS = CodeActionKind.Source + ".sortMembers";

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

	/**
	 * Extract to field kind
	 */
	public static final String REFACTOR_EXTRACT_FIELD = CodeActionKind.RefactorExtract + ".field";

	/**
	 * Extract interface kind
	 */
	public static final String REFACTOR_EXTRACT_INTERFACE = CodeActionKind.RefactorExtract + ".interface";

	/**
	 * Move kind
	 */
	public static final String REFACTOR_MOVE = CodeActionKind.Refactor + ".move";

	/**
	 * Assign statement to new local variable
	 */
	public static final String REFACTOR_ASSIGN_VARIABLE = CodeActionKind.Refactor + ".assign.variable";

	/**
	 * Assign statement to new field
	 */
	public static final String REFACTOR_ASSIGN_FIELD = CodeActionKind.Refactor + ".assign.field";

	/**
	 * Introduce parameter
	 */
	public static final String REFACTOR_INTRODUCE_PARAMETER = CodeActionKind.Refactor + ".introduce.parameter";

	/**
	 * Base kind for "quickassist" code actions
	 */
	public static final String QUICK_ASSIST = "quickassist";
}
