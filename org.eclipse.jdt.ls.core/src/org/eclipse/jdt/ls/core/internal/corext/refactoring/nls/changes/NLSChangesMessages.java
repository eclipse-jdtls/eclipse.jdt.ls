/*******************************************************************************
 * Copyright (c) 2000, 2009 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Originally copied from org.eclipse.jdt.internal.corext.refactoring.nls.changes.NLSChangesMessages
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.jdt.ls.core.internal.corext.refactoring.nls.changes;

import org.eclipse.osgi.util.NLS;

public final class NLSChangesMessages extends NLS {

	private static final String BUNDLE_NAME = "org.eclipse.jdt.ls.core.internal.corext.refactoring.nls.changes.NLSChangesMessages";//$NON-NLS-1$

	private NLSChangesMessages() {
		// Do not instantiate
	}

	public static String createFile_creating_resource;
	public static String createFile_Create_file;

	public static String CreateFileChange_error_exists;
	public static String CreateFileChange_error_unknownLocation;

	static {
		NLS.initializeMessages(BUNDLE_NAME, NLSChangesMessages.class);
	}
}