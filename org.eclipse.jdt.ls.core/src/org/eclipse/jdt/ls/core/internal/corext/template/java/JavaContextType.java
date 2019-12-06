/*******************************************************************************
 * Copyright (c) 2019 Microsoft Corporation and others.
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

package org.eclipse.jdt.ls.core.internal.corext.template.java;

import org.eclipse.jdt.internal.corext.template.java.AbstractJavaContextTypeCore;
import org.eclipse.jdt.internal.corext.template.java.IJavaContext;

/**
 * The context type for templates inside Java code. The same class is used for
 * several context types:
 * <dl>
 * <li>templates for all Java code locations</li>
 * <li>templates for member locations</li>
 * <li>templates for statement locations</li>
 * <li>templates for module-info.java files</li>
 * </dl>
 */
public class JavaContextType extends AbstractJavaContextTypeCore {

	/**
	 * The context type id for templates working on all Java code locations
	 */
	public static final String ID_ALL = "java"; //$NON-NLS-1$

	/**
	 * The context type id for templates working on member locations
	 */
	public static final String ID_MEMBERS = "java-members"; //$NON-NLS-1$

	/**
	 * The context type id for templates working on statement locations
	 */
	public static final String ID_STATEMENTS = "java-statements"; //$NON-NLS-1$

	/**
	 * The context type id for templates working on module-info.java files
	 */
	public static final String ID_MODULE = "module"; //$NON-NLS-1$

	@Override
	protected void initializeContext(IJavaContext context) {
		// Separate 'module' context type from 'java' context type
		if (getId().equals(ID_MODULE)) {
			return;
		}
		if (!getId().equals(JavaContextType.ID_ALL)) { // a specific context must also allow the templates that work everywhere
			context.addCompatibleContextType(JavaContextType.ID_ALL);
		}
	}
}
