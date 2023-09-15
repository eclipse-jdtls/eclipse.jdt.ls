/*******************************************************************************
 * Copyright (c) 2023 Red Hat Inc. and others.
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
package org.eclipse.jdt.ls.core.internal.corext.refactoring;

import org.eclipse.osgi.util.NLS;

public class RefactoringCoreMessages extends NLS {

	private static final String BUNDLE_NAME = "org.eclipse.jdt.ls.core.internal.corext.refactoring.refactoring";//$NON-NLS-1$

	public static String ChangeSignatureRefactoring_change_signature_for;
	public static String ExtractFieldRefactoring_name;
	public static String ExtractFieldRefactoring_cannot_extract;
	public static String ExtractFieldRefactoring_interface_methods;
	public static String ExtractFieldRefactoring_creating_change;
	public static String ExtractFieldRefactoring_uses_type_declared_locally;
	public static String ExtractFieldRefactoring_initialize_field;

	static {
		NLS.initializeMessages(BUNDLE_NAME, RefactoringCoreMessages.class);
	}

	private RefactoringCoreMessages() {
		// Do not instantiate
	}

}
