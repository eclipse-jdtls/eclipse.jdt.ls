/*******************************************************************************
 * Copyright (c) 2023 IBM Corporation and others.
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
 *     Microsoft Corporation - based this file on JavaTypeCompletionProcessor
 *******************************************************************************/
package org.eclipse.jdt.internal.ui.refactoring.contentassist;

import org.eclipse.jdt.internal.corext.util.JavaModelUtil;
/**
 * @since 1.18
 */
public class JavaTypeCompletionProcessorCore {
	public static final String DUMMY_CLASS_NAME= "$$__$$"; //$NON-NLS-1$
	/**
	 * The CU name to be used if no parent ICompilationUnit is available.
	 * The main type of this class will be filtered out from the proposals list.
	 */
	public static final String DUMMY_CU_NAME= DUMMY_CLASS_NAME + JavaModelUtil.DEFAULT_CU_SUFFIX;
}
